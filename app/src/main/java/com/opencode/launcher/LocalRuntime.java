package com.opencode.launcher;

import android.content.Context;
import android.content.SharedPreferences;
import android.system.Os;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;

import org.json.JSONObject;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public final class LocalRuntime {
    private static final String TAG = "OpenCodeLocal";
    private static final Object LOCK = new Object();
    private static final String PREF = "app";
    private static final Object GIT_LOCK = new Object();
    private static final Object SDCARD_LOCK = new Object();

    private static Process process;
    private static String activeBinPath;

    private LocalRuntime() {
    }

    /**
     * Directorio HOME del runtime. Se resuelve a su forma canonica
     * (/data/data/...) para que coincida con las rutas que devuelve el
     * servidor; /data/user/0 y /data/data son el mismo sitio, pero la UI
     * compara cadenas y descartaria las sesiones si no coinciden.
     */
    public static File homeDir(Context ctx) {
        File home = new File(ctx.getFilesDir(), "home");
        try {
            return home.getCanonicalFile();
        } catch (IOException e) {
            return home;
        }
    }

    /** Raiz de proyectos; el servidor arranca con este cwd. */
    public static File workspaceDir(Context ctx) {
        File w = new File(homeDir(ctx), "workspace");
        try {
            mkdirs(w);
        } catch (IOException ignored) {
        }
        return w;
    }

    /**
     * Retira repos git accidentales en carpetas que no son proyectos.
     * El caso real: opencode abre su proyecto global
     * (~/toolchain) y se acaba versionando 366 MB del SDK/Gradle.
     */
    private static void cleanManagedGit(Context ctx) {
        File home = homeDir(ctx);
        deleteGit(new File(home, "toolchain"));
        deleteGit(new File(home.getParentFile(), "usr"));
        cleanAutoGit(new File(home, "workspace"));
        cleanAutoGit(home);
        cleanAutoGit(new File(home, "projects"));
        cleanAutoGit(new File(home, ".config"));
        cleanAutoGit(new File(home, ".local"));
    }

    /**
     * Limpieza segura al arrancar: temporales viejos, logs de Gradle y
     * .deb descargados. No toca caches compartidas ni builds de proyectos.
     */
    private static void pruneTransientCaches(Context ctx) {
        File home = homeDir(ctx);
        long cutoff = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        pruneOlder(new File(home, ".cache/tmp"), cutoff);
        pruneOlder(new File(home, ".local/share/opencode/log"), cutoff);
        pruneOlder(new File(home, "toolchain/gradle-home/daemon"), cutoff);
        File archives = new File(home.getParentFile(), "usr/var/cache/apt/archives");
        File[] debs = archives.listFiles();
        if (debs != null) {
            for (File f : debs) {
                if (f.isFile() && f.getName().endsWith(".deb")) f.delete();
            }
        }
        deleteTree(new File(home, "toolchain.tmp"));
    }

    private static void pruneOlder(File dir, long cutoff) {
        File[] kids = dir.listFiles();
        if (kids == null) return;
        for (File f : kids) {
            if (f.isDirectory()) {
                pruneOlder(f, cutoff);
                String[] left = f.list();
                if (left != null && left.length == 0) f.delete();
            } else if (f.lastModified() < cutoff) {
                f.delete();
            }
        }
    }

    /**
     * Retira el rootfs de enlaces que creaba la version anterior. El servidor
     * opencode v2 no lista symlinks, asi que esa carpeta solo aparecia como
     * una raiz vacia. Solo se borra si todos sus hijos son enlaces: si el
     * usuario metio algo real ahi, se conserva.
     */
    private static void pruneGeneratedRootfs(Context ctx) {
        File rf = new File(homeDir(ctx), "rootfs");
        File[] kids = rf.listFiles();
        if (kids == null || kids.length == 0) return;
        for (File kid : kids) {
            try {
                if (!android.system.OsConstants.S_ISLNK(
                        Os.lstat(kid.getAbsolutePath()).st_mode)) {
                    return;
                }
            } catch (Exception e) {
                return;
            }
        }
        deleteTree(rf);
        Log.i(TAG, "rootfs de enlaces retirado: " + rf);
    }

    private static void deleteGit(File dir) {
        File git = new File(dir, ".git");
        if (!git.isDirectory()) return;
        deleteTree(git);
        Log.i(TAG, "repo accidental retirado: " + git);
    }

    /** Borra .git vacio o creado por nuestro commit automatico. */
    private static void cleanAutoGit(File dir) {
        File git = new File(dir, ".git");
        if (!git.isDirectory()) return;
        boolean auto = !hasGitCommit(dir);
        if (!auto) {
            try {
                String msg = new String(Files.readAllBytes(
                        new File(git, "COMMIT_EDITMSG").toPath()), StandardCharsets.UTF_8);
                auto = msg.startsWith("Adia project:");
            } catch (Exception ignored) {
            }
        }
        if (auto) {
            deleteTree(git);
            Log.i(TAG, "repo automatico retirado: " + git);
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) for (File kid : kids) deleteTree(kid);
        //noinspection ResultOfMethodCallIgnored
        f.delete();
    }

    private static boolean hasGitCommit(File dir) {
        File heads = new File(dir, ".git/refs/heads");
        File[] refs = heads.listFiles();
        if (refs != null && refs.length > 0) return true;
        File packed = new File(dir, ".git/packed-refs");
        return packed.isFile() && packed.length() > 0;
    }

    /**
     * Monta /storage/emulated/0 dentro de HOME como "sdcard" para que el
     * selector de proyectos muestre rutas limpias (~/sdcard/...) en vez de
     * rutas con "..". Es un bind mount, no un symlink: el servidor de
     * opencode no lista symlinks. Sin root no hace nada; oc-early.js tiene
     * un fallback con rutas relativas.
     */
    public static void ensureSdcardMount(Context ctx) {
        synchronized (SDCARD_LOCK) {
            ensureSdcardMountLocked(ctx);
        }
    }

    private static void ensureSdcardMountLocked(Context ctx) {
        File dir = new File(homeDir(ctx), "sdcard");
        String path = dir.getAbsolutePath();
        String q = "'" + path.replace("'", "'\\''") + "'";
        String cmd = "p=" + q + "; "
                + "if grep -qF \" $p \" /proc/mounts; then echo ok; exit 0; fi; "
                + "umount \"$p\" 2>/dev/null; "
                + "if [ -L \"$p\" ]; then rm -f \"$p\"; fi; "
                + "if [ -d \"$p\" ] && [ -n \"$(ls -A \"$p\" 2>/dev/null | head -n 1)\" ]; then "
                + "echo not-empty; exit 0; fi; "
                + "mkdir -p \"$p\"; "
                + "mount --bind /storage/emulated/0 \"$p\" && echo ok";
        String out = RootShell.run(ctx, cmd, 15000);
        if (out == null) {
            Log.i(TAG, "sdcard bind: sin root");
        } else {
            Log.i(TAG, "sdcard bind: " + (out.isEmpty() ? "ok" : out));
        }
    }

    public static String activeVersion(Context ctx) {
        try {
            SharedPreferences sp = ctx.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String version = sp.getString("opencode_runtime_version", "");
            String path = sp.getString("opencode_runtime_path", "");
            if (version != null && !version.isEmpty() && path != null
                    && new File(path).isFile()
                    && UpdateCheck.cmpVer(version, BuildConfig.OPENCODE_VERSION) > 0) {
                return version;
            }
        } catch (Exception ignored) {
        }
        return BuildConfig.OPENCODE_VERSION;
    }

    public static File runtimeBinary(Context ctx) {
        try {
            SharedPreferences sp = ctx.getApplicationContext()
                    .getSharedPreferences(PREF, Context.MODE_PRIVATE);
            String version = sp.getString("opencode_runtime_version", "");
            String path = sp.getString("opencode_runtime_path", "");
            if (version != null && !version.isEmpty() && path != null
                    && UpdateCheck.cmpVer(version, BuildConfig.OPENCODE_VERSION) > 0) {
                File f = new File(path);
                if (f.isFile() && f.length() > 10L * 1024 * 1024) return f;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /** Binarios necesarios para el unico runtime: proot. */
    public static boolean prootAvailable(Context ctx) {
        File nd = new File(ctx.getApplicationInfo().nativeLibraryDir);
        return new File(nd, "libopencode2_bin.so").isFile()
                && new File(nd, "libld_musl.so").isFile()
                && new File(nd, "libstdcxx.so").isFile()
                && new File(nd, "libgcc_s.so").isFile()
                && new File(nd, "libproot.so").isFile()
                && new File(nd, "libproot_loader.so").isFile()
                && new File(nd, "libtalloc.so").isFile()
                && new File(nd, "libandroid-shmem.so").isFile();
    }

    /** Compatibilidad: solo existe el runtime proot. */
    public static boolean available(Context ctx) {
        return prootAvailable(ctx);
    }

    private static String readAll(HttpURLConnection c) throws IOException {
        try (InputStream in = c.getInputStream()) {
            byte[] buf = new byte[8192];
            StringBuilder out = new StringBuilder();
            int n;
            while ((n = in.read(buf)) > 0 && out.length() < 65536) {
                out.append(new String(buf, 0, n, StandardCharsets.UTF_8));
            }
            return out.toString();
        }
    }

    /** Contraseña Basic estable por instalación para el servidor local. */
    public static String serverPassword(Context ctx) {
        SharedPreferences sp = ctx.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        String p = sp.getString("local_server_password", null);
        if (p == null || p.length() < 24) {
            byte[] raw = new byte[32];
            new SecureRandom().nextBytes(raw);
            p = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
            sp.edit().putString("local_server_password", p).apply();
        }
        return p;
    }

    /**
     * v2: crea un codigo de emparejamiento de un solo uso y devuelve la URL
     * /auth/connect/<code>. Al cargarla en el WebView, el servidor deja una
     * cookie de sesion y redirige a la UI. v1 devuelve null.
     */
    public static String pairingUrl(Context ctx) {
        HttpURLConnection c = null;
        try {
            URL url = new URL("http://127.0.0.1:" + BuildConfig.SERVER_PORT + "/api/pair");
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(2000);
            c.setReadTimeout(3000);
            c.setUseCaches(false);
            c.setRequestMethod("POST");
            c.setDoOutput(true);
            c.setFixedLengthStreamingMode(0);
            String auth = Base64.getEncoder().encodeToString(
                    ("opencode:" + serverPassword(ctx)).getBytes(StandardCharsets.UTF_8));
            c.setRequestProperty("Authorization", "Basic " + auth);
            c.setRequestProperty("Accept", "application/json");
            c.getOutputStream().close();
            if (c.getResponseCode() != 200) return null;
            String body = readAll(c);
            JSONObject o = new JSONObject(body);
            String link = o.optString("url", "");
            if (link.startsWith("http://") || link.startsWith("https://")) return link;
            String code = o.optString("code", "");
            if (!code.isEmpty()) {
                return "http://127.0.0.1:" + BuildConfig.SERVER_PORT + "/auth/connect/" + code;
            }
        } catch (Exception e) {
            Log.w(TAG, "pairing v2 no disponible: " + e);
        } finally {
            if (c != null) c.disconnect();
        }
        return null;
    }

    public static boolean running() {
        synchronized (LOCK) {
            return process != null && process.isAlive();
        }
    }

    public static boolean start(Context ctx, int port) throws IOException {
        synchronized (LOCK) {
            if (process != null && process.isAlive()) {
                return true;
            }
            Context app = ctx.getApplicationContext();
            AppLog.init(app);
            cleanManagedGit(app);
            pruneTransientCaches(app);
            pruneGeneratedRootfs(app);
            // La raiz de proyectos debe existir siempre, tambien en modo proot:
            // el selector de sesiones la ofrece como "/" canonico.
            workspaceDir(app);
            // Un solo camino: TODO corre dentro de proot. No hay motor
            // nativo, ni fallback, ni modos que el usuario pueda romper.
            prepareToolbox(app);
            if (!prootAvailable(app)) {
                throw new IOException("faltan binarios del runtime proot");
            }
            boolean ok = startProot(app, port);
            if (ok) Log.i(TAG, "runtime proot iniciado en 127.0.0.1:" + port);
            return ok;
        }
    }

    /**
     * Deja el prefijo y los comandos de diagnostico listos antes de arrancar.
     * Si falta libreadline (instalacion a medias), se extrae del APK.
     */
    private static void prepareToolbox(Context app) {
        File home = homeDir(app);
        try {
            // Si la primera instalacion sigue en curso (sin marcador), no se
            // toca el prefijo: dpkg esta escribiendo y reparar en paralelo
            // dejaria ficheros mezclados.
            if (LocalToolbox.complete(app)) {
                LocalToolbox.repairEssentials(app);
            }
        } catch (Throwable t) {
            Log.w(TAG, "no se pudo reparar el prefijo: " + t);
        }
        try {
            File binDir = LocalToolbox.installed(app)
                    ? new File(LocalToolbox.prefix(app), "bin")
                    : new File(home, "bin");
            CommandResolver.install(home, binDir);
            // Primer arranque: los envoltorios se generaron en $HOME/bin
            // porque el prefijo aun no existia. Al instalarse el toolbox se
            // generan en $PREFIX/bin; los viejos de $HOME/bin quedan muertos
            // y hacen pensar que hay dos juegos de comandos.
            if (LocalToolbox.installed(app)) cleanLegacyHomeBin(home);
        } catch (Throwable t) {
            Log.w(TAG, "no se pudieron regenerar los envoltorios: " + t);
        }
    }

    private static void cleanLegacyHomeBin(File home) {
        File dir = new File(home, "bin");
        String[] names = {
                CommandResolver.SHELL_NAME, CommandResolver.PROOT_NAME,
                CommandResolver.ENGINE_NAME, CommandResolver.NATIVE_ENV_NAME,
                CommandResolver.DOCTOR_NAME, CommandResolver.REPAIR_NAME
        };
        for (String name : names) {
            File f = new File(dir, name);
            if (f.isFile() || java.nio.file.Files.isSymbolicLink(f.toPath())) {
                //noinspection ResultOfMethodCallIgnored
                f.delete();
            }
        }
    }

    private static boolean startProot(Context app, int port) throws IOException {
        File nd = new File(app.getApplicationInfo().nativeLibraryDir);
        File home = homeDir(app);
        File config = new File(home, ".config/opencode");
        File tmp = new File(app.getCacheDir(), "tmp");
        File prootTmp = new File(tmp, "proot");
        File lib = new File(home, "lib");
        File etc = new File(home, ".oc-etc");
        mkdirs(home);
        mkdirs(config);
        mkdirs(tmp);
        mkdirs(prootTmp);
        mkdirs(lib);
        mkdirs(etc);
        writeConfig(config);
        writeGitConfig(home);
        installTools(app, config);
        // El servidor tambien corre dentro de proot. Su shell de agente debe
        // ser el bash del prefijo; no hay oc-shell ni reparto por comando.
        File nativeConfigDir = new File(home, ".config/opencode-android");
        mkdirs(nativeConfigDir);
        installSkills(app, nativeConfigDir);
        String agentShell = LocalToolbox.installed(app)
                ? LocalToolbox.SANDBOX_PREFIX + "/bin/bash"
                : "/system/bin/sh";
        NativeRuntimeConfig.write(nativeConfigDir, config, agentShell);
        prepareNetworkOverrides(home, etc);

        File bin = runtimeBinary(app);
        if (bin == null) bin = new File(nd, "libopencode2_bin.so");
        File musl = new File(nd, "libld_musl.so");
        File proot = new File(nd, "libproot.so");
        File loader = new File(nd, "libproot_loader.so");
        copyIfNeeded(new File(nd, "libstdcxx.so"), new File(lib, "libstdc++.so.6"));
        copyIfNeeded(new File(nd, "libgcc_s.so"), new File(lib, "libgcc_s.so.1"));

        Map<String, String> env = buildEnv(app, home, tmp, etc, lib, nd, loader, prootTmp);
        env.put("PWD", home.getAbsolutePath());
        env.put("SHELL", agentShell);
        env.put("OC_ENGINE", "proot");
        env.put("OPENCODE_CONFIG_DIR", nativeConfigDir.getAbsolutePath());
        List<String> cmd = prootCmd(app, home, tmp, etc, musl, proot, env,
                home.getAbsolutePath());
        cmd.add(bin.getAbsolutePath());
        cmd.add("serve");
        cmd.add("--hostname");
        cmd.add("127.0.0.1");
        cmd.add("--port");
        cmd.add(Integer.toString(port));

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(home);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().putAll(env);
        spawn(pb, bin.getAbsolutePath());
        return true;
    }

    /**
     * Shell local para el terminal del frontend embebido: proot + prefix/bin/script,
     * que abre un PTY real y ejecuta bash. Asi el terminal de Adia no depende del
     * PTY de opencode (roto en Android por bun-pty).
     *
     * <p>Si la instalacion del toolbox es anterior a util-linux, se extrae el
     * paquete del propio APK antes de lanzar el shell; si aun asi no hay
     * "script", se arranca bash sin PTY (echo local desactivado, pero usable)
     * en vez de devolver un proceso que muere al instante.</p>
     */
    public static Process localShell(Context ctx) throws IOException {
        Context app = ctx.getApplicationContext();
        if (!prootAvailable(app)) throw new IOException("runtime proot no disponible");
        if (!LocalToolbox.installed(app)) throw new IOException("toolbox no instalado");
        // No abras un bash a medias: si la primera instalacion sigue en curso,
        // el terminal mostraria libreadline.so.8. Mejor un error claro que se
        // puede reintentar cuando termine.
        if (!LocalToolbox.complete(app)) {
            throw new IOException("toolbox instalándose; espera y reintenta");
        }
        // Antes de abrir el PTY: si falta readline/bash, se rellena desde el
        // APK para que el terminal no arranque con "libreadline.so.8 not found".
        LocalToolbox.repairEssentials(app);
        File nd = new File(app.getApplicationInfo().nativeLibraryDir);
        File home = homeDir(app);
        File tmp = new File(app.getCacheDir(), "tmp");
        File etc = new File(home, ".oc-etc");
        File lib = new File(home, "lib");
        File prootTmp = new File(tmp, "proot");
        mkdirs(home);
        mkdirs(tmp);
        mkdirs(etc);
        mkdirs(lib);
        mkdirs(prootTmp);
        prepareNetworkOverrides(home, etc);
        copyIfNeeded(new File(nd, "libstdcxx.so"), new File(lib, "libstdc++.so.6"));
        copyIfNeeded(new File(nd, "libgcc_s.so"), new File(lib, "libgcc_s.so.1"));
        File musl = new File(nd, "libld_musl.so");
        File proot = new File(nd, "libproot.so");
        File loader = new File(nd, "libproot_loader.so");
        Map<String, String> env = buildEnv(app, home, tmp, etc, lib, nd, loader, prootTmp);
        // En el terminal interactivo el shell por defecto es el bash del prefijo.
        env.put("SHELL", LocalToolbox.SANDBOX_PREFIX + "/bin/bash");
        env.put("COLORTERM", "truecolor");
        List<String> base = prootCmd(app, home, tmp, etc, musl, proot, env,
                home.getAbsolutePath());
        boolean hasScript = LocalToolbox.ensureTerminalTools(app);
        if (hasScript) {
            List<String> scriptCmd = new ArrayList<>(base);
            scriptCmd.add(LocalToolbox.SANDBOX_PREFIX + "/bin/script");
            scriptCmd.add("-q");
            scriptCmd.add("-c");
            scriptCmd.add("stty rows 40 cols 100 2>/dev/null; exec bash -l");
            scriptCmd.add("/dev/null");
            Process p = startShell(scriptCmd, home, env);
            // Si script muere enseguida (falta una lib, no puede abrir /dev/ptmx,
            // etc.) el terminal se quedaria en negro. Se cae a bash sin PTY.
            if (aliveAfter(p, 350)) return p;
            Log.w(TAG, "script termino al arrancar; terminal local sin PTY");
            p.destroy();
        } else {
            Log.w(TAG, "script no disponible; terminal local sin PTY");
        }
        List<String> bashCmd = new ArrayList<>(base);
        bashCmd.add(LocalToolbox.SANDBOX_PREFIX + "/bin/bash");
        bashCmd.add("-i");
        bashCmd.add("-l");
        return startShell(bashCmd, home, env);
    }

    private static Process startShell(List<String> cmd, File home, Map<String, String> env)
            throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(home);
        pb.redirectErrorStream(true);
        pb.environment().clear();
        pb.environment().putAll(env);
        return pb.start();
    }

    private static boolean aliveAfter(Process p, long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return p.isAlive();
    }

    /**
     * Ejecuta un comando dentro del mismo sandbox (proot) en el que vive el
     * agente, y devuelve su salida. Lo usa el servidor de herramientas para
     * comprobar el entorno y para el shim "pkg".
     */
    public static String runInSandbox(Context ctx, String command, int timeoutMs) {
        try {
            Context app = ctx.getApplicationContext();
            File nd = new File(app.getApplicationInfo().nativeLibraryDir);
            if (!prootAvailable(app)) return "no hay runtime proot";
            File home = homeDir(app);
            File tmp = new File(app.getCacheDir(), "tmp");
            File prootTmp = new File(tmp, "proot");
            File lib = new File(home, "lib");
            File etc = new File(home, ".oc-etc");
            mkdirs(home);
            mkdirs(tmp);
            mkdirs(prootTmp);
            mkdirs(lib);
            mkdirs(etc);
            prepareNetworkOverrides(home, etc);
            File musl = new File(nd, "libld_musl.so");
            File proot = new File(nd, "libproot.so");
            File loader = new File(nd, "libproot_loader.so");
            Map<String, String> env = buildEnv(app, home, tmp, etc, lib, nd, loader, prootTmp);
            env.put("PWD", home.getAbsolutePath());
            List<String> cmd = prootCmd(app, home, tmp, etc, musl, proot, env,
                    home.getAbsolutePath());
            cmd.add("/system/bin/sh");
            cmd.add("-c");
            cmd.add(command);
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(home);
            pb.redirectErrorStream(true);
            pb.environment().clear();
            pb.environment().putAll(env);
            Process p = pb.start();
            StringBuilder out = new StringBuilder();
            Thread t = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(
                        new InputStreamReader(p.getInputStream()))) {
                    String line;
                    while ((line = r.readLine()) != null && out.length() < 20000) {
                        out.append(line).append('\n');
                    }
                } catch (IOException ignored) {
                }
            }, "sandbox-cmd");
            t.setDaemon(true);
            t.start();
            boolean done = p.waitFor(timeoutMs, TimeUnit.MILLISECONDS);
            if (!done) {
                p.destroyForcibly();
                out.append("[timeout]\n");
            }
            return out.toString();
        } catch (Exception e) {
            return "error: " + e;
        }
    }

    private static Process spawn(ProcessBuilder pb, String binPath) throws IOException {
        Process p = pb.start();
        process = p;
        activeBinPath = binPath;
        Thread log = new Thread(() -> {
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    Log.i(TAG, line);
                    AppLog.line("opencode", line);
                }
            } catch (IOException ignored) {
            }
        }, "local-runtime-log");
        log.setDaemon(true);
        log.start();
        return p;
    }

    private static Map<String, String> buildEnv(Context app, File home, File tmp, File etc,
            File lib, File nd, File loader, File prootTmp) throws IOException {
        Map<String, String> env = new LinkedHashMap<>();
            // HOME real de la app: una sola ruta, sin alias.
            env.put("HOME", home.getAbsolutePath());
            env.put("TMPDIR", tmp.getAbsolutePath());
            env.put("XDG_CONFIG_HOME", new File(home, ".config").getAbsolutePath());
            env.put("XDG_DATA_HOME", new File(home, ".local/share").getAbsolutePath());
            env.put("XDG_CACHE_HOME", new File(home, ".cache").getAbsolutePath());
            env.put("XDG_STATE_HOME", new File(home, ".local/state").getAbsolutePath());
            env.put("XDG_RUNTIME_DIR", new File(home, ".run").getAbsolutePath());
            env.put("ANDROID_ROOT", "/system");
            env.put("ANDROID_DATA", "/data");
            env.put("TERMUX_VERSION", "0.118.0");
            // Caja de herramientas: si esta instalada, su prefijo va delante
            // del PATH y sus librerias delante del resto.
            File toolbox = LocalToolbox.prefix(app);
            boolean hasToolbox = LocalToolbox.installed(app);
            String path = (hasToolbox ? LocalToolbox.SANDBOX_PREFIX + "/bin:" : "")
                    + nd + ":/system/bin:/system/xbin:/vendor/bin";
            env.put("PATH", path);
            env.put("SHELL", "/system/bin/sh");
            env.put("LANG", "C.UTF-8");
            env.put("LC_ALL", "C.UTF-8");
            env.put("TERM", "xterm-256color");
            String appUser = "u0_a" + (android.os.Process.myUid() % 100000);
            env.put("USER", appUser);
            env.put("LOGNAME", appUser);
            env.put("NO_COLOR", "1");
            env.put("CI", "1");
            env.put("OPENCODE_DISABLE_AUTOUPDATE", "1");
            env.put("OPENCODE_RUNTIME", "proot");
            env.put("OC_ENGINE", "proot");
            env.put("OPENCODE_NATIVE_LIB_DIR", nd.getAbsolutePath());
            env.put("OPENCODE_SERVER_PASSWORD", serverPassword(app));
            env.put("OPENCODE_SERVER_USERNAME", "opencode");
            env.put("OPENCODE_PASSWORD", serverPassword(app));
            String libPath = (hasToolbox ? LocalToolbox.SANDBOX_PREFIX + "/lib:" : "")
                    + lib.getAbsolutePath() + ":" + nd;
            env.put("LD_LIBRARY_PATH", libPath);
            if (hasToolbox) {
                env.put("PREFIX", LocalToolbox.SANDBOX_PREFIX);
                env.put("TERMUX_HOME", LocalToolbox.SANDBOX_PREFIX);
                env.put("TMPDIR", tmp.getAbsolutePath());
                // El pkg de Termux re-selecciona mirror (cosa de su app); aqui
                // el mirror ya esta en sources.list, asi que se le dice que no.
                env.put("TERMUX_PKG_NO_MIRROR_SELECT", "1");
                env.put("OPENCODE_TOOLBOX_TOKEN", serverPassword(app));
                env.put("OPENCODE_TOOLBOX_PORT", Integer.toString(ToolboxServer.PORT));
            }
            env.put("PROOT_LOADER", loader.getAbsolutePath());
            env.put("PROOT_TMP_DIR", prootTmp.getAbsolutePath());
            mkdirs(new File(home, ".local/share"));
            mkdirs(new File(home, ".cache"));
            mkdirs(new File(home, ".local/state"));
            mkdirs(new File(home, ".run"));
            return env;
    }

    private static List<String> prootCmd(Context app, File home, File tmp, File etc,
            File musl, File proot, Map<String, String> env, String sandboxCwd)
            throws IOException {
        File toolbox = LocalToolbox.prefix(app);
        boolean hasToolbox = LocalToolbox.installed(app);
        StringBuilder script = new StringBuilder();
        for (Map.Entry<String, String> e : env.entrySet()) {
            script.append("export ").append(e.getKey()).append('=')
                    .append(quote(e.getValue())).append("; ");
        }
        script.append("exec \"$@\"");
        List<String> cmd = new ArrayList<>();
        cmd.add("/system/bin/sh");
        cmd.add("-c");
        cmd.add(script.toString());
        cmd.add("opencode");
        cmd.add(proot.getAbsolutePath());
        cmd.add("--kill-on-exit");
        cmd.add("-b");
        cmd.add(tmp.getAbsolutePath() + ":/tmp");
        cmd.add("-b");
        cmd.add(musl.getAbsolutePath() + ":/lib/ld-musl-aarch64.so.1");
        if (hasToolbox) {
            // Prefijo estilo Termux: las herramientas llevan dentro rutas
            // absolutas (/data/data/com.termux/files/usr) y solo funcionan
            // si ese directorio existe con ese nombre dentro del sandbox.
            cmd.add("-b");
            cmd.add(toolbox.getAbsolutePath() + ":" + LocalToolbox.SANDBOX_PREFIX);
        }
        cmd.add("-b");
        cmd.add(new File(etc, "hosts").getAbsolutePath() + ":/etc/hosts");
        cmd.add("-b");
        cmd.add(new File(etc, "resolv.conf").getAbsolutePath() + ":/etc/resolv.conf");
        cmd.add("-w");
        cmd.add(sandboxCwd == null ? home.getAbsolutePath() : sandboxCwd);
        return cmd;
    }

    public static void stop() {
        stopCurrent();
    }

    private static void stopCurrent() {
        Process p;
        String binPath;
        synchronized (LOCK) {
            p = process;
            process = null;
            binPath = activeBinPath;
            activeBinPath = null;
        }
        if (p != null) {
            p.destroy();
        }
        killMatching("-TERM", "libopencode2_bin.so serve --hostname 127.0.0.1 --port "
                + BuildConfig.SERVER_PORT);
        if (binPath != null && !binPath.endsWith("libopencode2_bin.so")) {
            killMatching("-TERM", binPath + " serve --hostname 127.0.0.1 --port "
                    + BuildConfig.SERVER_PORT);
        }
        if (p != null) {
            try {
                if (!p.waitFor(4, TimeUnit.SECONDS)) {
                    p.destroyForcibly();
                }
            } catch (InterruptedException e) {
                p.destroyForcibly();
                Thread.currentThread().interrupt();
            }
        }
        killMatching("-KILL", "libopencode2_bin.so serve --hostname 127.0.0.1 --port "
                + BuildConfig.SERVER_PORT);
        if (binPath != null && !binPath.endsWith("libopencode2_bin.so")) {
            killMatching("-KILL", binPath + " serve --hostname 127.0.0.1 --port "
                    + BuildConfig.SERVER_PORT);
        }
        LocalNetworkProxy.stop();
    }

    private static void killMatching(String signal, String pattern) {
        try {
            Process k = new ProcessBuilder("/system/bin/pkill", signal, "-f", pattern)
                    .redirectErrorStream(true).start();
            k.waitFor(2, TimeUnit.SECONDS);
        } catch (Exception ignored) {
        }
    }

    private static void copyIfNeeded(File src, File dst) throws IOException {
        if (dst.isFile() && dst.length() == src.length()) {
            return;
        }
        try (InputStream in = new FileInputStream(src);
                OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private static void mkdirs(File dir) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs() && !dir.isDirectory()) {
            throw new IOException("no se pudo crear " + dir);
        }
    }

    private static void prepareNetworkOverrides(File home, File etc) throws IOException {
        migrateNetworkOverrides(home);
        writeNetworkOverrides(etc);
    }

    /**
     * Instalaciones anteriores guardaban hosts/resolv.conf dentro de home/etc,
     * que es una carpeta del proyecto del usuario. Solo se borran si el
     * contenido es exactamente el que generaba el launcher; cualquier edicion
     * del usuario se conserva.
     */
    private static void migrateNetworkOverrides(File home) {
        File old = new File(home, "etc");
        File hosts = new File(old, "hosts");
        if (hosts.isFile() && isOurHosts(hosts)) {
            if (hosts.delete()) Log.i(TAG, "hosts antiguo retirado de etc");
        }
        File resolv = new File(old, "resolv.conf");
        if (resolv.isFile() && isOurResolv(resolv)) {
            if (resolv.delete()) Log.i(TAG, "resolv.conf antiguo retirado de etc");
        }
    }

    private static boolean isOurHosts(File file) {
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) lines.add(line.trim());
                }
            }
            if (lines.size() != 3) return false;
            if (!lines.get(0).equals("127.0.0.1 localhost")) return false;
            boolean opencode = false;
            boolean models = false;
            for (int i = 1; i < lines.size(); i++) {
                String[] parts = lines.get(i).split("\\s+");
                if (parts.length != 2) return false;
                if (parts[1].equals("opencode.ai")) opencode = true;
                else if (parts[1].equals("models.opencode.ai")) models = true;
                else return false;
            }
            return opencode && models;
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isOurResolv(File file) {
        try {
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(
                    new FileInputStream(file), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!line.trim().isEmpty()) lines.add(line.trim());
                }
            }
            return lines.size() == 2
                    && lines.get(0).equals("nameserver 1.1.1.1")
                    && lines.get(1).equals("nameserver 8.8.8.8");
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeNetworkOverrides(File etc) throws IOException {
        String[][] hosts = {
                {"opencode.ai", "172.65.90.20"},
                {"models.opencode.ai", "172.66.173.149"}
        };
        StringBuilder body = new StringBuilder("127.0.0.1 localhost\n");
        for (String[] host : hosts) {
            String ip = null;
            try {
                for (InetAddress address : InetAddress.getAllByName(host[0])) {
                    if (address instanceof Inet4Address) {
                        ip = address.getHostAddress();
                        break;
                    }
                }
            } catch (Exception ignored) {
            }
            body.append(ip == null ? host[1] : ip).append(' ').append(host[0]).append('\n');
        }
        try (FileWriter w = new FileWriter(new File(etc, "hosts"), false)) {
            w.write(body.toString());
        }
        try (FileWriter w = new FileWriter(new File(etc, "resolv.conf"), false)) {
            w.write("nameserver 1.1.1.1\nnameserver 8.8.8.8\n");
        }
    }

    /**
     * Instala los tools que sustituyen a grep/glob. Viven en el directorio
     * global de configuracion y llaman al rg Bionic empaquetado con linker64,
     * asi que funcionan sin root y sin descargar el rg glibc del servidor.
     * Si el usuario ya tiene un fichero con ese nombre, no se pisa.
     */
    private static void installTools(Context app, File config) {
        try {
            File dir = new File(config, "tools");
            mkdirs(dir);
            installTool(app, dir, "grep.ts");
            installTool(app, dir, "glob.ts");
            // Skills propias (construccion de APK en el dispositivo). Se
            // copian en cada arranque para que las actualizaciones del APK
            // lleguen tambien a la configuracion del agente.
            installSkills(app, config);
        } catch (Exception e) {
            Log.w(TAG, "no se pudieron instalar los tools grep/glob: " + e);
        }
    }

    /**
     * Copia recursivamente assets/skills a &lt;config&gt;/skills. OpenCode
     * descubre cada SKILL.md que encuentra bajo esa ruta.
     */
    private static void installSkills(Context app, File config) {
        try {
            copyAssetDir(app, "skills", new File(config, "skills"));
        } catch (Exception e) {
            Log.w(TAG, "no se pudieron instalar las skills: " + e);
        }
    }

    private static void copyAssetDir(Context app, String assetPath, File dest) throws IOException {
        String[] children = app.getAssets().list(assetPath);
        if (children == null || children.length == 0) {
            copyAssetFile(app, assetPath, dest);
            return;
        }
        if (!dest.isDirectory() && !dest.mkdirs() && !dest.isDirectory()) {
            throw new IOException("no se pudo crear " + dest);
        }
        for (String child : children) {
            copyAssetDir(app, assetPath + "/" + child, new File(dest, child));
        }
    }

    private static void copyAssetFile(Context app, String assetPath, File dest) throws IOException {
        File parent = dest.getParentFile();
        if (parent != null) mkdirs(parent);
        File tmp = new File(dest.getAbsolutePath() + ".tmp");
        try (InputStream in = app.getAssets().open(assetPath);
                OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        if (!tmp.renameTo(dest)) {
            LocalToolbox.copyFile(tmp, dest);
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        //noinspection ResultOfMethodCallIgnored
        dest.setReadable(true, false);
        if (dest.getName().endsWith(".sh")) {
            //noinspection ResultOfMethodCallIgnored
            dest.setExecutable(true, true);
        }
    }

    private static void installTool(Context app, File dir, String name) throws IOException {
        File dest = new File(dir, name);
        if (dest.isFile() && !isOurTool(dest)) {
            Log.i(TAG, "se conserva el tool de usuario " + dest);
            return;
        }
        File tmp = new File(dir, name + ".tmp");
        try (InputStream in = app.getAssets().open("tools/" + name);
                OutputStream out = new FileOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
        if (!tmp.renameTo(dest)) {
            try (InputStream in = new FileInputStream(tmp);
                    OutputStream out = new FileOutputStream(dest)) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
            }
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
        }
        Log.i(TAG, "tool instalado " + dest);
    }

    private static boolean isOurTool(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] buf = new byte[2048];
            int n = in.read(buf);
            if (n <= 0) return false;
            return new String(buf, 0, n, StandardCharsets.UTF_8)
                    .contains("OPENCODE_ANDROID_TOOL");
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeConfig(File dir) throws IOException {
        File f = new File(dir, "opencode.jsonc");
        if (f.isFile()) {
            return;
        }
        try (FileWriter w = new FileWriter(f, false)) {
            w.write("{\n  \"$schema\": \"https://opencode.ai/config.json\",\n");
            w.write("  \"autoupdate\": false\n}\n");
        }
    }

    /**
     * El FUSE de /sdcard presenta los archivos como de media_rw, no del uid
     * de la app. Git lo interpreta como "dubious ownership" y se niega a
     * trabajar, con lo que un proyecto externo acababa con worktree "/".
     * Se marca safe.directory para todos los repos: el sandbox de la app ya
     * esta aislado y el usuario elige que proyectos abre.
     */
    private static void writeGitConfig(File home) {
        File f = new File(home, ".gitconfig");
        try {
            String body = f.isFile()
                    ? new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8)
                    : "";
            if (body.contains("[safe]")) return;
            StringBuilder sb = new StringBuilder(body);
            if (sb.length() > 0 && sb.charAt(sb.length() - 1) != '\n') sb.append('\n');
            sb.append("[safe]\n\tdirectory = *\n");
            try (FileOutputStream fos = new FileOutputStream(f, false)) {
                fos.write(sb.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (Exception e) {
            Log.w(TAG, "no se pudo escribir .gitconfig: " + e.getMessage());
        }
    }

    private static String quote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
