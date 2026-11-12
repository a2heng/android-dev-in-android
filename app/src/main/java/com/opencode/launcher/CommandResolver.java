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
    /**
     * fast/native ejecuta binarios del prefijo fuera del sandbox. Solo se
     * permite en builds debug para medir; en release el motor siempre es
     * auto/proot, aunque quede un engine-mode=fast de una instalacion vieja.
     */
    private static final boolean ALLOW_FAST = BuildConfig.DEBUG;

    public static final String SHELL_NAME = "oc-shell";
    public static final String PROOT_NAME = "oc-proot";
    public static final String NATIVE_ENV_NAME = "oc-native-env.sh";
    /** Comando que lee/cambia el motor: oc-engine [auto|proot|fast|status]. */
    public static final String ENGINE_NAME = "oc-engine";
    /** Diagnostico de rutas y motor: oc-doctor. */
    public static final String DOCTOR_NAME = "oc-doctor";
    /** Reparacion offline del prefijo: oc-repair. */
    public static final String REPAIR_NAME = "oc-repair";

    private static final String LINKER = "/system/bin/linker64";

    /*
     * No hay listas de "comandos que van a proot" ni de "comandos seguros":
     * en auto TODO el comando entra en proot. La lista de herramientas hoja
     * queda solo como documentacion/medicion en docs/commands.md y en el
     * modo fast de debug. Asi el agente no necesita conocer la clasificacion:
     * escribe el comando y el motor decide por el.
     */

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
        Scan scan = scan(binDir);
        write(new File(binDir, NATIVE_ENV_NAME), nativeEnv(scan.functions, libPath));
        File proot = new File(binDir, PROOT_NAME);
        write(proot, prootScript(home, nd, tmp, etc));
        File modeFile = modeFile(home);
        // Las instalaciones que midieron con "native" vuelven a auto: native
        // era justo el modo que dejaba binarios del prefijo sin sus librerias.
        if (Mode.NATIVE.equals(readModeRaw(home))) writeMode(home, Mode.AUTO);
        write(new File(binDir, ENGINE_NAME), engineScript(modeFile));
        File shell = new File(binDir, SHELL_NAME);
        write(shell, shellScript(modeFile));
        write(new File(binDir, DOCTOR_NAME), doctorScript(home));
        write(new File(binDir, REPAIR_NAME), repairScript(home));
        Log.i(TAG, "resolvedor listo: " + scan.functions.size()
                + " funciones fast; auto/proot = todo al sandbox; " + shell);
        return shell;
    }

    /**
     * Motores soportados:
     * <ul>
     *   <li>{@code auto}: transparente. TODO el comando se ejecuta dentro del
     *       sandbox proot; el agente no clasifica nada ni necesita saber que
     *       hay detras. Es el valor por defecto.</li>
     *   <li>{@code proot}: igual que auto, explicito.</li>
     *   <li>{@code fast}: solo debug, para medir; ejecuta nativo y puede
     *       reproducir el error de libreadline. Release lo ignora.</li>
     *   <li>{@code native}: alias historico de {@code fast}; se migra solo.</li>
     * </ul>
     */
    public static final class Mode {
        public static final String AUTO = "auto";
        public static final String PROOT = "proot";
        public static final String FAST = "fast";
        public static final String NATIVE = "native";

        private Mode() {
        }

        public static boolean valid(String mode) {
            return AUTO.equals(mode) || PROOT.equals(mode)
                    || FAST.equals(mode) || NATIVE.equals(mode);
        }

        /** "native" era el nombre viejo de fast; no debe quedar en disco. */
        public static String migrate(String mode) {
            return NATIVE.equals(mode) ? FAST : mode;
        }
    }

    /** Fichero de una linea (auto|proot|fast) que lee oc-shell en cada comando. */
    public static File modeFile(File home) {
        return new File(new File(home, ".config/opencode-android"), "engine-mode");
    }

    public static String readMode(File home) {
        File file = modeFile(home);
        if (!file.isFile()) return Mode.AUTO;
        try {
            String text = new String(java.nio.file.Files.readAllBytes(file.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return Mode.valid(text) ? Mode.migrate(text) : Mode.AUTO;
        } catch (IOException e) {
            return Mode.AUTO;
        }
    }

    /** Texto tal cual del fichero, sin migrar; para detectar "native" viejo. */
    static String readModeRaw(File home) {
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
    }

    /**
     * Solo para el modo fast de debug: genera funciones que lanzan los ELF
     * con linker64. El modo auto no usa esto; todo va a proot.
     */
    private static Scan scan(File binDir) {
        Scan scan = new Scan();
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
                if (!isElf(file)) continue;
                if (SHELL_BUILTINS.contains(name)) continue;
                if (!VALID_NAME.matcher(name).matches()) continue;
                if (!linker) continue;
                File target;
                try {
                    target = file.getCanonicalFile();
                } catch (IOException e) {
                    continue;
                }
                if (!target.isFile() || !isElf(target)) continue;
                if (!seen.add(name)) continue;
                // Se lanza el SYMLINK, no el destino canonico: los coreutils
                // de Termux son un solo binario que elige el applet por
                // argv[0] (head -> coreutils --head). Con el destino se
                // perderia ese nombre.
                scan.functions.add(name + "() { LD_LIBRARY_PATH=\"$OC_NATIVE_LIB_PATH\" "
                        + LINKER + " " + q(file.getAbsolutePath()) + " \"$@\"; }");
            }
        }
        return scan;
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

    private static String shellScript(File modeFile) {
        String modePath = q(modeFile.getAbsolutePath());
        // fast/native solo se permite en debug; en release se ignora.
        String fastBlock = ALLOW_FAST
                ? "if [ \"$OC_MODE\" = \"fast\" ] || [ \"$OC_MODE\" = \"native\" ]; then\n"
                + "  if [ -f \"$OWN/" + NATIVE_ENV_NAME + "\" ]; then . \"$OWN/" + NATIVE_ENV_NAME + "\"; fi\n"
                + "  OC_ENGINE=native; export OC_ENGINE\n"
                + "  eval \"$CMD\"; exit $?\n"
                + "fi\n"
                : "if [ \"$OC_MODE\" = \"fast\" ] || [ \"$OC_MODE\" = \"native\" ]; then\n"
                + "  OC_MODE=auto\n"
                + "fi\n";
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. No editar a mano.\n"
                + "# Ver docs/commands.md y docs/paths.md en el repositorio.\n"
                + "#\n"
                + "# auto/proot: TODO el comando entra en proot. No hay heuristicas\n"
                + "# ni listas que el agente deba conocer; el motor decide por el.\n"
                + "# fast: solo debug, nativo, para medir.\n"
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
                + "OC_MODE=auto\n"
                + "if [ -r " + modePath + " ]; then read OC_MODE < " + modePath + "; fi\n"
                + fastBlock
                + "exec /system/bin/sh \"$OWN/" + PROOT_NAME + "\" \"$CMD\"\n";
    }

    private static String engineScript(File modeFile) {
        String path = q(modeFile.getAbsolutePath());
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver: motor de los comandos del agente.\n"
                + "# Uso: oc-engine auto|proot|fast|status|help\n"
                + "#\n"
                + "#   auto   (por defecto) seguro: los binarios Termux van a proot;\n"
                + "#          solo grep/sed/find... y los binarios Android corren nativos.\n"
                + "#   proot  todo el comando dentro del sandbox (mas lento, maximo\n"
                + "#          aislamiento y compatibilidad).\n"
                + "#   fast   solo en builds debug, para medir. PUEDE reproducir el\n"
                + "#          error 'library libreadline.so.8 not found'; en release\n"
                + "#          se ignora y se sigue con auto.\n"
                + "#   status imprime el modo actual (una palabra).\n"
                + "#   help   imprime esta ayuda.\n"
                + "CURRENT=auto\n"
                + "if [ -r " + path + " ]; then read CURRENT < " + path + "; fi\n"
                + "case \"${1:-status}\" in\n"
                + "  status|get|\"\") echo \"$CURRENT\" ;;\n"
                + "  auto)\n"
                + "    echo auto > " + path + " || exit 1\n"
                + "    echo \"engine=auto (seguro: binarios Termux -> proot)\" ;;\n"
                + "  proot)\n"
                + "    echo proot > " + path + " || exit 1\n"
                + "    echo \"engine=proot (todo el comando dentro del sandbox)\" ;;\n"
                + "  fast|native)\n"
                + (ALLOW_FAST
                    ? "    echo fast > " + path + " || exit 1\n"
                    + "    echo \"engine=fast (solo debug; puede fallar libreadline)\" >&2 ;;\n"
                    : "    echo \"oc-engine: fast solo existe en builds debug; el motor sigue en auto\" >&2\n"
                    + "    exit 2 ;;\n")
                + "  help|-h|--help)\n"
                + "    echo \"oc-engine: motor de ejecucion de comandos de Adia\"\n"
                + "    echo \"  oc-engine status  -> auto|proot|fast\"\n"
                + "    echo \"  oc-engine auto    -> seguro (recomendado)\"\n"
                + "    echo \"  oc-engine proot   -> todo en el sandbox Termux\"\n"
                + (ALLOW_FAST
                    ? "    echo \"  oc-engine fast    -> reparto antiguo, solo debug/medicion\"\n"
                    : "    echo \"  oc-engine fast    -> deshabilitado en release\"\n")
                + "    echo \"  oc-engine help    -> esta ayuda\"\n"
                + "    echo \"Documentacion: docs/commands.md y docs/paths.md\" ;;\n"
                + "  *)\n"
                + "    echo \"uso: oc-engine auto|proot|fast|status|help\" >&2\n"
                + "    exit 2 ;;\n"
                + "esac\n";
    }

    /**
     * oc-doctor: comprueba las rutas que siempre deben existir, muestra el
     * modo y pide al servidor Java el estado real (paquetes esenciales,
     * proot, motor). Solo usa builtins y toybox: si bash esta roto, esto
     * sigue funcionando.
     */
    private static String doctorScript(File home) {
        StringBuilder files = new StringBuilder();
        for (String path : LocalToolbox.essentialPaths()) {
            files.append("check ").append(q(path)).append('\n');
        }
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. Uso: oc-doctor\n"
                + "# Diagnostico de rutas Termux/proot. No requiere bash ni curl.\n"
                + "case \"$0\" in */*) OWN=${0%/*} ;; *) OWN=${PREFIX:-" + TERMUX_PREFIX + "}/bin ;; esac\n"
                + "PORT=${OPENCODE_TOOLBOX_PORT:-" + ToolboxServer.PORT + "}\n"
                + "TOK=${OPENCODE_TOOLBOX_TOKEN:-}\n"
                + "MODE=$(cat " + q(modeFile(home).getAbsolutePath()) + " 2>/dev/null)\n"
                + "[ -z \"$MODE\" ] && MODE=auto\n"
                + "echo \"== oc-doctor ==\"\n"
                + "echo \"engine=$MODE\"\n"
                + "echo \"OC_ENGINE=${OC_ENGINE:-native}\"\n"
                + "echo \"HOME=$HOME\"\n"
                + "echo \"PREFIX=${PREFIX:-<no definido>}\"\n"
                + "echo \"PWD=$PWD\"\n"
                + "echo \"PATH=$PATH\"\n"
                + "echo \"-- ficheros esenciales (prefijo visible) --\"\n"
                + "check() { if [ -e \"${PREFIX:-/nonexistent}/$1\" ]; then echo \"OK      $1\"; else echo \"MISSING $1\"; fi; }\n"
                + files
                + "echo \"-- comprobacion en proot --\"\n"
                + "/system/bin/sh \"$OWN/oc-proot\" 'echo proot-ok; echo PWD=$PWD; echo PREFIX=$PREFIX; bash -c \"echo bash-ok\"; git --version | head -1' 2>&1\n"
                + "post() {\n"
                + "  if [ -x \"${PREFIX:-/nonexistent}/bin/curl\" ]; then\n"
                + "    \"${PREFIX}/bin/curl\" -s -X POST --data \"token=$TOK\" \"$1\"\n"
                + "  else\n"
                + "    /system/bin/toybox wget -q -O - --post-data=\"token=$TOK\" \"$1\"\n"
                + "  fi\n"
                + "}\n"
                + "echo \"-- estado del servidor (Java) --\"\n"
                + "if [ -n \"$TOK\" ]; then\n"
                + "  post \"http://127.0.0.1:$PORT/doctor\" 2>/dev/null || echo \"doctor-http-failed\"\n"
                + "else\n"
                + "  echo \"sin OPENCODE_TOOLBOX_TOKEN (abre el terminal desde la app)\"\n"
                + "fi\n"
                + "echo\n";
    }

    /**
     * oc-repair: pide al servidor Java que rellene los esenciales que falten
     * desde los .deb del APK (offline) y vuelve a pasar el diagnostico.
     */
    private static String repairScript(File home) {
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. Uso: oc-repair\n"
                + "case \"$0\" in */*) OWN=${0%/*} ;; *) OWN=${PREFIX:-" + TERMUX_PREFIX + "}/bin ;; esac\n"
                + "PORT=${OPENCODE_TOOLBOX_PORT:-" + ToolboxServer.PORT + "}\n"
                + "TOK=${OPENCODE_TOOLBOX_TOKEN:-}\n"
                + "if [ -z \"$TOK\" ]; then\n"
                + "  echo \"oc-repair: falta OPENCODE_TOOLBOX_TOKEN; ejecutalo desde el terminal de Adia\" >&2\n"
                + "  exit 2\n"
                + "fi\n"
                + "post() {\n"
                + "  if [ -x \"${PREFIX:-/nonexistent}/bin/curl\" ]; then\n"
                + "    \"${PREFIX}/bin/curl\" -s -X POST --data \"token=$TOK\" \"$1\"\n"
                + "  else\n"
                + "    /system/bin/toybox wget -q -O - --post-data=\"token=$TOK\" \"$1\"\n"
                + "  fi\n"
                + "}\n"
                + "echo \"oc-repair: reparando el prefijo desde el APK (sin red)...\"\n"
                + "post \"http://127.0.0.1:$PORT/repair\" 2>/dev/null || {\n"
                + "  echo \"oc-repair: no se pudo hablar con el servidor local\" >&2\n"
                + "  exit 1\n"
                + "}\n"
                + "echo\n"
                + "echo \"oc-repair: hecho; comprobando...\"\n"
                + "/system/bin/sh \"$OWN/oc-doctor\"\n";
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
                // El cwd del agente debe sobrevivir al salto a proot. Antes se
                // forzaba HOME: por eso "pwd" dentro de un comando con bash
                // devolvia HOME y build.sh buscaba el proyecto en el sitio
                // equivocado cuando se usaba "$PWD/...".
                + "WORKDIR=\"${PWD:-$HOME}\"\n"
                + "if [ ! -d \"$WORKDIR\" ]; then WORKDIR=\"$HOME\"; fi\n"
                + "exec " + q(new File(nd, "libproot.so").getAbsolutePath()) + " --kill-on-exit \\\n"
                + "  -b " + q(tmp.getAbsolutePath() + ":/tmp") + " \\\n"
                + "  -b " + q(new File(nd, "libld_musl.so").getAbsolutePath()
                        + ":/lib/ld-musl-aarch64.so.1") + " \\\n"
                + "  -b " + q(prefix.getAbsolutePath() + ":" + TERMUX_PREFIX) + " \\\n"
                + "  -b " + q(new File(etc, "hosts").getAbsolutePath() + ":/etc/hosts") + " \\\n"
                + "  -b " + q(new File(etc, "resolv.conf").getAbsolutePath() + ":/etc/resolv.conf") + " \\\n"
                + "  -w \"$WORKDIR\" \\\n"
                + "  /system/bin/sh -c \"$CMD\"\n";
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
