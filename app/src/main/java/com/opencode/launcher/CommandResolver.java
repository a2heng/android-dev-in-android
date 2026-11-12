package com.opencode.launcher;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * Genera los pocos comandos de diagnostico que viven en el prefijo.
 *
 * <p>No hay resolvedor de comandos: el servidor opencode, el terminal y todas
 * las herramientas corren ya dentro del mismo proot. Por eso no existen
 * oc-shell, oc-native-env.sh ni una lista de comandos que decidir. Solo quedan
 * oc-doctor / oc-repair / oc-engine (informativo).</p>
 */
public final class CommandResolver {

    /** Nombres historicos: se borran de $HOME/bin al arrancar. */
    public static final String SHELL_NAME = "oc-shell";
    public static final String PROOT_NAME = "oc-proot";
    public static final String NATIVE_ENV_NAME = "oc-native-env.sh";
    public static final String ENGINE_NAME = "oc-engine";
    public static final String DOCTOR_NAME = "oc-doctor";
    public static final String REPAIR_NAME = "oc-repair";

    private static final String TERMUX_PREFIX = LocalToolbox.SANDBOX_PREFIX;

    private CommandResolver() {
    }

    /** Escribe oc-doctor, oc-repair y oc-engine en el prefijo. */
    public static void install(File home, File binDir) throws IOException {
        if (!binDir.isDirectory() && !binDir.mkdirs() && !binDir.isDirectory()) {
            throw new IOException("no se pudo crear " + binDir);
        }
        // Restos de versiones anteriores que ya no se usan.
        for (String old : new String[]{SHELL_NAME, PROOT_NAME, NATIVE_ENV_NAME}) {
            //noinspection ResultOfMethodCallIgnored
            new File(binDir, old).delete();
        }
        write(new File(binDir, DOCTOR_NAME), doctorScript(home));
        write(new File(binDir, REPAIR_NAME), repairScript(home));
        write(new File(binDir, ENGINE_NAME), engineScript());
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
     * oc-doctor: comprueba las rutas que siempre deben existir, muestra el
     * modo (siempre proot) y pide al servidor Java el estado real.
     */
    private static String doctorScript(File home) {
        StringBuilder files = new StringBuilder();
        for (String path : LocalToolbox.essentialPaths()) {
            files.append("check ").append(q(path)).append('\n');
        }
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. Uso: oc-doctor\n"
                + "# Diagnostico de rutas. Todo corre ya dentro de proot.\n"
                + "case \"$0\" in */*) OWN=${0%/*} ;; *) OWN=${PREFIX:-" + TERMUX_PREFIX + "}/bin ;; esac\n"
                + "PORT=${OPENCODE_TOOLBOX_PORT:-" + ToolboxServer.PORT + "}\n"
                + "TOK=${OPENCODE_TOOLBOX_TOKEN:-}\n"
                + "echo \"== oc-doctor ==\"\n"
                + "echo \"engine=proot (fijo)\"\n"
                + "echo \"OC_ENGINE=${OC_ENGINE:-proot}\"\n"
                + "echo \"HOME=$HOME\"\n"
                + "echo \"PREFIX=${PREFIX:-<no definido>}\"\n"
                + "echo \"PWD=$PWD\"\n"
                + "echo \"PATH=$PATH\"\n"
                + "echo \"-- ficheros esenciales (prefijo visible) --\"\n"
                + "check() { if [ -e \"${PREFIX:-/nonexistent}/$1\" ]; then echo \"OK      $1\"; else echo \"MISSING $1\"; fi; }\n"
                + files
                + "echo \"-- comprobacion directa (ya estamos en proot) --\"\n"
                + "echo proot-ok\n"
                + "echo PWD=$PWD\n"
                + "echo PREFIX=$PREFIX\n"
                + "bash -c 'echo bash-ok' 2>&1\n"
                + "git --version 2>&1 | head -1\n"
                + "python3 --version 2>&1 | head -1\n"
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

    /** oc-repair: pide al servidor Java que rellene los esenciales que falten. */
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

    /**
     * oc-engine: ya no hay motor que elegir. Se conserva como comando
     * informativo para scripts/agents antiguos.
     */
    private static String engineScript() {
        return "#!/system/bin/sh\n"
                + "# Generado por CommandResolver. Todo corre en proot.\n"
                + "case \"${1:-status}\" in\n"
                + "  status|get|\"\") echo proot ;;\n"
                + "  auto|proot) echo \"engine=proot (fijo; todo corre en proot)\" ;;\n"
                + "  fast|native)\n"
                + "    echo \"oc-engine: fast/native ya no existe; todo es proot\" >&2\n"
                + "    exit 2 ;;\n"
                + "  help|-h|--help)\n"
                + "    echo \"oc-engine: Adia corre todo dentro de proot; no hay modos.\"\n"
                + "    echo \"  oc-engine status -> proot\"\n"
                + "    echo \"  oc-doctor       -> diagnostico de rutas\"\n"
                + "    echo \"  oc-repair       -> reparacion offline\" ;;\n"
                + "  *)\n"
                + "    echo \"uso: oc-engine [status|help]\" >&2\n"
                + "    exit 2 ;;\n"
                + "esac\n";
    }

    private static String q(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }
}
