package com.opencode.launcher;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * Registro persistente de Adia y del runtime opencode.
 *
 * Antes el "Registro" solo mostraba un TextView que se vaciaba al cerrar la
 * app (y que en la linea Next ni siquiera se ve). Ahora todo se escribe en
 * {@code files/logs/adia.log}, se rota al llegar a 512 KB y el dialogo de
 * registro lo puede copiar o compartir.
 */
public final class AppLog {
    private static final String TAG = "Adia";
    private static final long MAX_BYTES = 512L * 1024;
    private static final long READ_TAIL_BYTES = 200L * 1024;
    private static final Object LOCK = new Object();

    private static File logFile;
    private static BufferedWriter writer;

    private AppLog() {
    }

    /** Idempotente: se llama desde la actividad y desde el runtime. */
    public static void init(Context ctx) {
        if (ctx == null) return;
        synchronized (LOCK) {
            if (logFile != null && writer != null) return;
            File dir = new File(ctx.getApplicationContext().getFilesDir(), "logs");
            if (!dir.exists() && !dir.mkdirs()) {
                Log.w(TAG, "no se pudo crear " + dir);
                return;
            }
            logFile = new File(dir, "adia.log");
            openWriter();
        }
    }

    private static void openWriter() {
        try {
            writer = new BufferedWriter(new OutputStreamWriter(
                    new FileOutputStream(logFile, true), StandardCharsets.UTF_8));
        } catch (IOException e) {
            writer = null;
            Log.w(TAG, "no se pudo abrir el registro: " + e);
        }
    }

    private static void rotateIfNeeded() {
        if (logFile == null || !logFile.exists()) return;
        if (logFile.length() < MAX_BYTES) return;
        try {
            if (writer != null) writer.close();
        } catch (IOException ignored) {
        }
        writer = null;
        File old = new File(logFile.getParentFile(), "adia.log.1");
        if (old.exists()) old.delete();
        logFile.renameTo(old);
        openWriter();
    }

    /** Escribe una linea con marca de tiempo. Nunca lanza. */
    public static void line(String source, String message) {
        if (message == null) return;
        String clean = message.replace("\r", "");
        synchronized (LOCK) {
            if (logFile == null) return;
            rotateIfNeeded();
            if (writer == null) openWriter();
            if (writer == null) return;
            String stamp = new SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US)
                    .format(new Date());
            try {
                writer.write(stamp);
                writer.write(' ');
                writer.write(source == null ? "app" : source);
                writer.write(' ');
                writer.write(clean);
                writer.write('\n');
                writer.flush();
            } catch (IOException e) {
                Log.w(TAG, "fallo al escribir el registro: " + e);
            }
        }
    }

    public static File file(Context ctx) {
        init(ctx);
        return logFile;
    }

    /** Ultimas ~200 KB del registro, o "" si aun no hay nada. */
    public static String read(Context ctx) {
        init(ctx);
        File f;
        synchronized (LOCK) {
            f = logFile;
        }
        if (f == null || !f.exists()) return "";
        long size = f.length();
        long from = Math.max(0, size - READ_TAIL_BYTES);
        StringBuilder sb = new StringBuilder();
        try (FileInputStream in = new FileInputStream(f)) {
            long skipped = 0;
            while (skipped < from) {
                long n = in.skip(from - skipped);
                if (n <= 0) break;
                skipped += n;
            }
            try (BufferedReader r = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                boolean first = true;
                while ((line = r.readLine()) != null) {
                    // Al cortar por bytes puede quedar media linea al principio.
                    if (first && from > 0) {
                        first = false;
                        continue;
                    }
                    first = false;
                    sb.append(line).append('\n');
                }
            }
        } catch (IOException e) {
            return "No se pudo leer el registro: " + e.getMessage();
        }
        if (from > 0) sb.insert(0, "…(solo las últimas líneas)…\n");
        return sb.toString();
    }

    public static void clear(Context ctx) {
        init(ctx);
        synchronized (LOCK) {
            try {
                if (writer != null) writer.close();
            } catch (IOException ignored) {
            }
            writer = null;
            if (logFile != null && logFile.exists()) logFile.delete();
            openWriter();
        }
    }
}
