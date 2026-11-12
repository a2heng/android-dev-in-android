package com.opencode.launcher;

import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Punto unico que decide como se ejecuta un comando en el runtime nativo:
 *
 *  - directo sobre Android para binarios del sistema,
 *  - con /system/bin/linker64 para los ELF del prefijo (git, python, node...),
 *    que Android no deja ejecutar con execve desde el directorio de la app,
 *  - o dentro del sandbox proot para paquetes (pkg/apt/dpkg), scripts del
 *    prefijo y operaciones de git que lanzan ayudantes externos.
 *
 * Genera en el prefijo de la caja de herramientas:
 *   oc-native-env.sh -> funciones que lanzan los ELF con el linker
 *   oc-shell         -> shell que usa el servidor (via trampolin del APK)
 *   oc-proot         -> ejecuta un comando dentro de proot
 *
 * Asi no hay "if command == git" repartidos por el codigo: la politica vive
 * aqui y los scripts se regeneran en cada arranque.
 */
public final class CommandResolver {
    private static final String TAG = "OpenCodeLocal";

    public static final String SHELL_NAME = "oc-shell";
    public static final String PROOT_NAME = "oc-proot";
    public static final String NATIVE_ENV_NAME = "oc-native-env.sh";
    /** Comando que lee/cambia el motor: oc-engine [auto|proot|native|status]. */
    public static final String ENGINE_NAME = "oc-engine";

    private static final String LINKER = "/system/bin/linker64";
    private static final String[] SYSTEM_DIRS = {
            "/system/bin", "/system/xbin", "/vendor/bin"
    };

    /** Comandos que necesitan el prefijo completo de Termux (proot). */
    private static final String[] PROOT_COMMANDS = {
            "pkg", "apt", "apt-get", "apt-cache", "apt-key", "dpkg", "dpkg-deb",
            "dpkg-query", "dpkg-trigger", "termux-change-repo", "termux-setup-storage",
            "termux-reload-settings", "termux-services", "proot", "proot-distro",
            "sshd", "service",
            // Lanza a otros comandos: en nativo el hijo seria el toybox del
            // sistema (o fallaria con EACCES), en proot es el del prefijo.
            "xargs"
    };

    /**
     * Binarios del prefijo que deben ganar a la version de /system/bin.
     *
     * Regla: solo herramientas "hoja" (no lanzan otros binarios del prefijo).
     * Asi el agente ve las mismas GNU que dentro de proot sin pagar ptrace.
     * Lo que si lanza ayudantes del prefijo (git, ssh, los scripts de build)
     * sigue yendo a proot por las otras reglas.
     *
     * Medido: GNU find tarda 94 ms en 10.059 entradas frente a 158 ms del
     * toybox del sistema; GNU grep ademas soporta mas expresiones.
     */
    private static final Set<String> PREFER_PREFIX = new HashSet<>(Arrays.asList(
            // Busqueda y texto (las que mas usa el agente).
            "find", "grep", "sed", "awk", "diff", "less", "cmp",
            // Compresion suelta (son el programa, no lanzan a nadie).
            "gzip", "bzip2", "xz", "zstd",
            // Coreutils: mismas opciones que en proot. Los nombres son
            // symlinks a coreutils; la funcion pasa el symlink (no el destino)
            // para que argv[0] siga siendo el applet correcto.
            "sort", "uniq", "cut", "tr", "head", "tail", "wc", "stat", "du",
            "md5sum", "sha1sum", "sha256sum", "base64"
    ));

    /** Nombres que no se pueden definir como funcion (builtins o palabras raras). */
    private static final Set<String> SHELL_BUILTINS = new HashSet<>(Arrays.asList(
            "alias", "bg", "break", "cd", "command", "continue", "echo", "eval",
            "exec", "exit", "export", "false", "fc", "fg", "getopts", "hash",
            "jobs", "kill", "let", "local", "printf", "pwd", "read", "readonly",
            "return", "set", "shift", "source", "test", "times", "trap", "true",
            "type", "ulimit", "umask", "unalias", "unset", "wait"
    ));

    private static final Pattern VALID_NAME =
            Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    /** Nombres que se pueden enrutar a proot, incluidos los guiones de termux-*. */
    private static final Pattern SAFE_COMMAND =
            Pattern.compile("[A-Za-z0-9_+.-]+");

    private static final String TERMUX_PREFIX = LocalToolbox.SANDBOX_PREFIX;

    private CommandResolver() {
    }

    /** true si el comando debe correr dentro de proot. */
    public static boolean needsProot(String commandLine) {
        if (commandLine == null || commandLine.isEmpty()) return false;
        String normalized = commandLine.replace('\n', ' ')
                .replace(';', ' ').replace('|', ' ').replace('&', ' ');
        for (String raw : normalized.split("\\s+")) {
            String word = raw.replace("\"", "").replace("'", "");
            if (word.isEmpty()) continue;
            String base = word.substring(word.lastIndexOf('/') + 1);
            for (String cmd : PROOT_COMMANDS) {
                if (base.equals(cmd)) return true;
            }
            if (word.contains(TERMUX_PREFIX)) return true;
        }
        return false;
    }

    /**
     * Escribe los envoltorios y devuelve la ruta de oc-shell.
     */
    public static File install(File home, File nd, File tmp, File etc, File binDir)
            throws IOException {
        if (!binDir.isDirectory() && !binDir.mkdirs() && !binDir.isDirectory()) {
            throw new IOException("no se pudo crear " + binDir);
        }
        File prefixDir = binDir.getParentFile();
        File prefixLib = prefixDir == null ? new File(TERMUX_PREFIX, "lib")
                : new File(prefixDir, "lib");
        LinkedHashSet<String> libs = new LinkedHashSet<>();
        libs.add(prefixLib.getAbsolutePath());
        libs.add(new File(home, "lib").getAbsolutePath());
        libs.add(nd.getAbsolutePath());
        String libPath = String.join(":", libs);
        Scan scan = scan(binDir, libPath);
        write(new File(binDir, NATIVE_ENV_NAME), nativeEnv(scan.functions, libPath));
        File proot = new File(binDir, PROOT_NAME);
        write(proot, prootScript(home, nd, tmp, etc));
        File modeFile = new File(new File(home, ".config/opencode-android"), "engine-mode");
        write(new File(binDir, ENGINE_NAME), engineScript(modeFile));
        File shell = new File(binDir, SHELL_NAME);
        write(shell, shellScript(scan.prootNames, modeFile));
        Log.i(TAG, "resolvedor listo: " + scan.functions.size() + " nativos, "
                + scan.prootNames.size() + " a proot; " + shell);
        return shell;
    }

    /** Motores soportados. "auto" es el reparto por comando; "proot" manda todo al sandbox. */
    public static final class Mode {
        public static final String AUTO = "auto";
        public static final String PROOT = "proot";
        public static final String NATIVE = "native";

        private Mode() {
        }

        public static boolean valid(String mode) {
            return AUTO.equals(mode) || PROOT.equals(mode) || NATIVE.equals(mode);
        }
    }

    /** Fichero de una linea (auto|proot|native) que lee oc-shell en cada comando. */
    public static File modeFile(File home) {
        return new File(new File(home, ".config/opencode-android"), "engine-mode");
    }

    public static String readMode(File home) {
        File file = modeFile(home);
        if (!file.isFile()) return Mode.AUTO;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return Mode.valid(text) ? text : Mode.AUTO;
        } catch (IOException e) {
            return Mode.AUTO;
        }
    }

    public static void writeMode(File home, String mode) throws IOException {
        if (!Mode.valid(mode)) throw new IOException("motor desconocido: " + mode);
        File file = modeFile(home);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()
                && !parent.isDirectory()) {
            throw new IOException("no se pudo crear " + parent);
        }
        try (FileWriter w = new FileWriter(file, false)) {
            w.write(mode + "\n");
        }
    }

    private static void write(File file, String body) throws IOException {
        try (FileWriter w = new FileWriter(file, false)) {
            w.write(body);
        }
        //noinspection ResultOfMethodCallIgnored
        file.setExecutable(true, true);
        //noinspection ResultOfMethodCallIgnored
        file.setReadable(true, false);
    }

    /**
     * Un ELF del prefijo no se puede execve desde untrusted_app, pero el
     * linker del sistema si puede leerlo y mapearlo como datos. Se expone una
     * funcion por comando; los scripts (npm, pip...) y lo que necesita el
     * prefijo completo siguen yendo a proot.
     */
    private static final class Scan {
        final List<String> functions = new ArrayList<>();
        final Set<String> prootNames = new LinkedHashSet<>();
    }

    private static Scan scan(File binDir, String libPath) {
        Scan scan = new Scan();
        Set<String> proot = new LinkedHashSet<>(Arrays.asList(PROOT_COMMANDS));
        boolean linker = new File(LINKER).isFile();
        File[] files = binDir.listFiles();
        if (files != null) {
            Arrays.sort(files);
            Set<String> seen = new HashSet<>();
            for (File file : files) {
                String name = file.getName();
                if (name.isEmpty() || name.startsWith(".") || name.startsWith("oc-")) {
                    continue;
                }
                if (!SAFE_COMMAND.matcher(name).matches()) continue;
                if (!file.isFile() || !file.canExecute()) continue;
                if (proot.contains(name)) continue;
                if (systemHas(name) && !PREFER_PREFIX.contains(name)) continue;
                if (!isElf(file)) {
                    // Script de verdad (npm, pip, pkg, termux-*...): necesita su prefijo.
                    proot.add(name);
                    continue;
                }
                if (SHELL_BUILTINS.contains(name)) continue;
                if (!VALID_NAME.matcher(name).matches()) {
                    // Un ELF con guion no puede ser funcion POSIX: a proot.
                    proot.add(name);
                    continue;
                }
                if (!linker) {
                    proot.add(name);
                    continue;
                }
                File target;
                try {
                    target = file.getCanonicalFile();
                } catch (IOException e) {
                    proot.add(name);
                    continue;
                }
                if (!target.isFile() || !isElf(target)) {
                    proot.add(name);
                    continue;
                }
                if (!seen.add(name)) continue;
                // Se lanza el SYMLINK, no el destino canonico: los coreutils
                // de Termux son un solo binario que elige el applet por
                // argv[0] (head -> coreutils --head). Con el destino se
                // perderia ese nombre.
                scan.functions.add(name + "() { LD_LIBRARY_PATH=\"$OC_NATIVE_LIB_PATH\" "
                        + LINKER + " " + q(file.getAbsolutePath()) + " \"$@\"; }");
            }
        }
        scan.prootNames.addAll(proot);
        return scan;
    }

    private static boolean systemHas(String name) {
        for (String dir : SYSTEM_DIRS) {
            if (new File(dir, name).isFile()) return true;
        }
        return false;
    }

    private static boolean isElf(File file) {
        try (InputStream in = new FileInputStream(file)) {
            byte[] head = new byte[4];
            int off = 0;
            while (off < head.length) {
                int n = in.read(head, off, head.length - off);
                if (n < 0) return false;
                off += n;
            }
            return (head[0] & 0xff) == 0x7f && head[1] == 'E'
                    && head[2] == 'L' && head[3] == 'F';
        } catch (IOException e) {
            return false;
        }
    }

    private static String nativeEnv(List<String> functions, String libPath) {
        StringBuilder out = new StringBuilder();
        out.append("#!/system/bin/sh\n")
                .append("# Generado por CommandResolver. No editar a mano.\n")
                .append("# Lanza los ELF del prefijo con el linker del sistema:\n")
                .append("# Android bloquea execve en el directorio de datos de la app.\n")
                .append("# LD_LIBRARY_PATH solo se aplica al linker y al ELF del prefijo;\n")
                .append("# los binarios del sistema no deben heredar las libs de Termux.\n")
                .append("OC_NATIVE_LIB_PATH=").append(q(libPath)).append('\n');
        for (String function : functions) {
            out.append(function).append('\n');
        }
        return out.toString();
    }

    private static String shellScript(Set<String> prootNames, File modeFile) {
        String modePath = q(modeFile.getAbsolutePath());
        StringBuilder cases = new StringBuilder();
        for (String cmd : prootNames) {
            if (cases.length() > 0) cases.append('|');
            cases.append(cmd);
        }
        // El envoltorio se ejecuta con /system/bin/sh. No usa utilidades del
        // prefijo (dirname, printf, tr...) porque Android no las deja
        // ejecutar: solo builtins y ordenes del sistema.
        //
        // Se revisan todas las palabras, no solo la primera: asi un
        // "id; git status" tambien detecta git. Un falso positivo (p. ej.
        // "echo git") solo cuesta arrancar proot; un falso negativo rompe el
        // comando con Permission denied.
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. No editar a mano.\n"
                + "# El shell del agente no hereda las librerias de Termux: los\n"
                + "# binarios del sistema (dumpsys, pm, am...) cargan las suyas.\n"
                + "# Las funciones de oc-native-env.sh ponen LD_LIBRARY_PATH solo\n"
                + "# cuando lanzan un ELF del prefijo con linker64.\n"
                + "unset LD_LIBRARY_PATH LD_PRELOAD\n"
                + "CMD=\"\"\n"
                + "while [ $# -gt 0 ]; do\n"
                + "  case \"$1\" in\n"
                + "    -c|-lc) CMD=\"$2\"; shift 2 ;;\n"
                + "    -l|--login) shift ;;\n"
                + "    *) if [ -z \"$CMD\" ]; then CMD=\"$1\"; fi; shift ;;\n"
                + "  esac\n"
                + "done\n"
                + "if [ -z \"$CMD\" ]; then exec /system/bin/sh; fi\n"
                + "case \"$0\" in */*) OWN=${0%/*} ;; *) OWN=. ;; esac\n"
                + "APP_FILES=${OWN%/usr/bin}\n"
                // Motor elegido por el usuario: auto (reparto por comando),
                // proot (todo al sandbox) o native (fuerza nativo, para medir).
                + "OC_MODE=auto\n"
                + "if [ -r " + modePath + " ]; then read OC_MODE < " + modePath + "; fi\n"
                + "if [ \"$OC_MODE\" = \"proot\" ]; then\n"
                + "  exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\"\n"
                + "fi\n"
                + "if [ \"$OC_MODE\" = \"native\" ]; then\n"
                + "  if [ -f \"$OWN/" + NATIVE_ENV_NAME + "\" ]; then . \"$OWN/" + NATIVE_ENV_NAME + "\"; fi\n"
                + "  OC_ENGINE=native\n"
                + "  export OC_ENGINE\n"
                + "  eval \"$CMD\"\n"
                + "  exit $?\n"
                + "fi\n"
                + "HAS_GIT=0\n"
                + "GIT_HELPER=0\n"
                + "for RAW in $CMD; do\n"
                + "  W=${RAW#\\\"}; W=${W%\\\"}; W=${W#\\'}; W=${W%\\'}\n"
                + "  W=${W#\\(}; W=${W%\\)}; W=${W%;}; W=${W%|}; W=${W%&}\n"
                + "  case \"$W\" in\n"
                + "    " + cases + ")\n"
                + "      exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\" ;;\n"
                + "    " + TERMUX_PREFIX + "/*|*" + TERMUX_PREFIX + "/*|*\"$APP_FILES\"*)\n"
                + "      exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\" ;;\n"
                + "    *android-build/scripts/*)\n"
                + "      # La cadena de build (JDK, Gradle, aapt2) vive en el\n"
                + "      # prefijo y necesita proot para ejecutar sus ELF.\n"
                + "      exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\" ;;\n"
                + "  esac\n"
                + "  case \"$W\" in git) HAS_GIT=1 ;; esac\n"
                + "  case \"$W\" in\n"
                + "    clone|fetch|pull|push|ls-remote|submodule|commit|merge|rebase)\n"
                + "      GIT_HELPER=1 ;;\n"
                + "    cherry-pick|revert|am|send-email|difftool|mergetool|credential)\n"
                + "      GIT_HELPER=1 ;;\n"
                + "    maintenance|gc|repack|prune)\n"
                + "      GIT_HELPER=1 ;;\n"
                + "  esac\n"
                + "done\n"
                + "if [ $HAS_GIT -eq 1 ] && [ $GIT_HELPER -eq 1 ]; then\n"
                + "  exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\"\n"
                + "fi\n"
                + "if [ -f \"$OWN/" + NATIVE_ENV_NAME + "\" ]; then\n"
                + "  . \"$OWN/" + NATIVE_ENV_NAME + "\"\n"
                + "fi\n"
                + "OC_ENGINE=native\n"
                + "export OC_ENGINE\n"
                + "eval \"$CMD\"\n"
                + "RC=$?\n"
                // 127: no encontrado; 126: no ejecutable (execve de app data
                // rechazado por Android). Los dos casos se reintentan en proot.
                + "if [ $RC -eq 127 ] || [ $RC -eq 126 ]; then\n"
                + "  exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\"\n"
                + "fi\n"
                + "exit $RC\n";
    }

    /**
     * oc-engine: consulta o cambia el motor desde el propio agente.
     * El cambio aplica al siguiente comando, sin reiniciar el servidor.
     */
    private static String engineScript(File modeFile) {
        String path = q(modeFile.getAbsolutePath());
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver: motor de los comandos del agente.\n"
                + "#   oc-engine status -> auto|proot|native\n"
                + "#   oc-engine proot  -> todo dentro del sandbox\n"
                + "#   oc-engine auto   -> reparto por comando (por defecto)\n"
                + "#   oc-engine native -> fuerza nativo; solo para medir\n"
                + "CURRENT=auto\n"
                + "if [ -r " + path + " ]; then read CURRENT < " + path + "; fi\n"
                + "case \"${1:-status}\" in\n"
                + "  status|get|\"\") echo \"$CURRENT\" ;;\n"
                + "  auto|proot|native)\n"
                + "    echo \"$1\" > " + path + " || exit 1\n"
                + "    echo \"engine=$1\" ;;\n"
                + "  *)\n"
                + "    echo \"uso: oc-engine [auto|proot|native|status]\" >&2\n"
                + "    exit 2 ;;\n"
                + "esac\n";
    }

    private static String prootScript(File home, File nd, File tmp, File etc) {        File prefix = new File(home.getParentFile(), "usr");
        File prootTmp = new File(tmp, "proot");
        //noinspection ResultOfMethodCallIgnored
        prootTmp.mkdirs();
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver: comando dentro del sandbox proot.\n"
                + "CMD=\"${1:-exec /system/bin/sh}\"\n"
                // Si ya estamos dentro de proot (OC_ENGINE=proot), no anidar otro
                // proot: oc-proot es idempotente.
                + "if [ \"${OC_ENGINE:-}\" = \"proot\" ]; then\n"
                + "  exec /system/bin/sh -c \"$CMD\"\n"
                + "fi\n"
                + "unset BASH_ENV ENV LD_PRELOAD\n"
                + "if [ ! -d " + q(prefix.getAbsolutePath()) + " ]; then\n"
                + "  echo \"proot: la caja de herramientas aun se esta instalando\" >&2\n"
                + "  exit 127\n"
                + "fi\n"
                + "export HOME=" + q(home.getAbsolutePath()) + "\n"
                + "export TMPDIR=" + q(tmp.getAbsolutePath()) + "\n"
                + "export XDG_CONFIG_HOME=" + q(new File(home, ".config").getAbsolutePath()) + "\n"
                + "export XDG_DATA_HOME=" + q(new File(home, ".local/share").getAbsolutePath()) + "\n"
                + "export XDG_CACHE_HOME=" + q(new File(home, ".cache").getAbsolutePath()) + "\n"
                + "export XDG_STATE_HOME=" + q(new File(home, ".local/state").getAbsolutePath()) + "\n"
                + "export XDG_RUNTIME_DIR=" + q(new File(home, ".run").getAbsolutePath()) + "\n"
                + "export PREFIX=" + q(TERMUX_PREFIX) + "\n"
                + "export TERMUX_HOME=" + q(TERMUX_PREFIX) + "\n"
                + "export TERMUX_VERSION=0.118.0\n"
                + "export TERMUX_PKG_NO_MIRROR_SELECT=1\n"
                + "export PATH=" + q(TERMUX_PREFIX + "/bin:/system/bin:/system/xbin:/vendor/bin") + "\n"
                + "export LD_LIBRARY_PATH=" + q(TERMUX_PREFIX + "/lib:" + new File(home, "lib").getAbsolutePath()
                        + ":" + nd.getAbsolutePath()) + "\n"
                + "export SHELL=/system/bin/sh\n"
                + "export OC_ENGINE=proot\n"
                + "export LANG=C.UTF-8\n"
                + "export LC_ALL=C.UTF-8\n"
                + "export TERM=xterm-256color\n"
                + "export NO_COLOR=1\n"
                + "export PROOT_LOADER=" + q(new File(nd, "libproot_loader.so").getAbsolutePath()) + "\n"
                + "export PROOT_TMP_DIR=" + q(prootTmp.getAbsolutePath()) + "\n"
                + "exec " + q(new File(nd, "libproot.so").getAbsolutePath()) + " --kill-on-exit \\\n"
                + "  -b " + q(tmp.getAbsolutePath() + ":/tmp") + " \\\n"
                + "  -b " + q(new File(nd, "libld_musl.so").getAbsolutePath()
                        + ":/lib/ld-musl-aarch64.so.1") + " \\\n"
                + "  -b " + q(prefix.getAbsolutePath() + ":" + TERMUX_PREFIX) + " \\\n"
                + "  -b " + q(new File(etc, "hosts").getAbsolutePath() + ":/etc/hosts") + " \\\n"
                + "  -b " + q(new File(etc, "resolv.conf").getAbsolutePath() + ":/etc/resolv.conf") + " \\\n"
                + "  -w " + q(home.getAbsolutePath()) + " \\\n"
                + "  /system/bin/sh -c \"$CMD\"\n";
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
