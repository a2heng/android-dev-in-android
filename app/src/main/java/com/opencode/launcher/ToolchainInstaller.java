package com.opencode.launcher;

import android.content.Context;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Extrae el toolchain offline que viaja en assets/toolchain/toolchain.zip.
 *
 * Contenido:
 *   android-sdk/platforms/android-36/...
 *   android-sdk/build-tools/35.0.2/aapt2
 *   gradle/gradle-9.9.0/...
 *   gradle-home/caches/modules-2/...
 *
 * El JDK no va aqui: se instala desde los .deb de assets/toolbox con dpkg.
 * La extraccion es idempotente y se marca con .installed-<version>.
 */
public final class ToolchainInstaller {
    private static final String TAG = "OpenCodeToolchain";
    public static final String VERSION = "7";
    private static final String ASSET = "toolchain/toolchain.zip";
    /** Revision empaquetada de build-tools (ver scripts/make-toolchain.py). */
    private static final String BT_VERSION = "36.0.0";

    private ToolchainInstaller() {
    }

    public static File root(Context ctx) {
        return new File(LocalRuntime.homeDir(ctx), "toolchain");
    }

    public static boolean installed(Context ctx) {
        File root = root(ctx);
        File marker = new File(root, ".installed");
        File version = new File(root, "version");
        if (!marker.isFile() || !version.isFile()) return false;
        try {
            String v = new String(java.nio.file.Files.readAllBytes(version.toPath()),
                    java.nio.charset.StandardCharsets.UTF_8).trim();
            return VERSION.equals(v);
        } catch (Exception e) {
            return false;
        }
    }

    public static void ensure(Context ctx) {
        synchronized (ToolchainInstaller.class) {
            if (installed(ctx)) return;
            File root = root(ctx);
            File tmp = new File(root.getParentFile(), "toolchain.tmp");
            Log.i(TAG, "extrayendo toolchain offline...");
            try {
                deleteTree(tmp);
                if (!tmp.mkdirs() && !tmp.isDirectory()) {
                    throw new IOException("no se pudo crear " + tmp);
                }
                String tmpCanonical = tmp.getCanonicalPath() + File.separator;
                int count = 0;
                // ZipInputStream perdia una entrada (build-tools/.../aapt) al
                // leer el zip de 500 MB desde los assets. Se copia a un fichero
                // y se usa ZipFile, que accede al indice central: 13427
                // entradas, todas.
                File copy = new File(root.getParentFile(), "toolchain.zip.tmp");
                deleteTree(copy);
                try (InputStream in = ctx.getAssets().open(ASSET);
                        OutputStream out = new FileOutputStream(copy)) {
                    byte[] buf = new byte[1 << 16];
                    int n;
                    while ((n = in.read(buf)) > 0) {
                        out.write(buf, 0, n);
                    }
                }
                try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(copy)) {
                    java.util.Enumeration<? extends ZipEntry> entries = zip.entries();
                    while (entries.hasMoreElements()) {
                        ZipEntry entry = entries.nextElement();
                        String name = entry.getName();
                        if (name.isEmpty()) continue;
                        File out = new File(tmp, name);
                        String canonical = out.getCanonicalPath();
                        if (!canonical.startsWith(tmpCanonical)) {
                            throw new IOException("entrada fuera del destino: " + name);
                        }
                        if (entry.isDirectory()) {
                            if (!out.isDirectory() && !out.mkdirs() && !out.isDirectory()) {
                                throw new IOException("no se pudo crear " + out);
                            }
                            continue;
                        }
                        File parent = out.getParentFile();
                        if (parent != null && !parent.isDirectory()
                                && !parent.mkdirs() && !parent.isDirectory()) {
                            throw new IOException("no se pudo crear " + parent);
                        }
                        try (InputStream src = zip.getInputStream(entry);
                                OutputStream os = new FileOutputStream(out)) {
                            byte[] buf = new byte[1 << 16];
                            int n;
                            while ((n = src.read(buf)) > 0) {
                                os.write(buf, 0, n);
                            }
                        }
                        if (isExecutable(name)) {
                            //noinspection ResultOfMethodCallIgnored
                            out.setExecutable(true, true);
                        }
                        count++;
                    }
                }
                deleteTree(copy);
                deleteTree(root);
                if (!tmp.renameTo(root)) {
                    throw new IOException("no se pudo mover " + tmp + " a " + root);
                }
                //noinspection ResultOfMethodCallIgnored
                new File(root, ".installed").createNewFile();
                try (FileOutputStream ver = new FileOutputStream(new File(root, "version"))) {
                    ver.write(VERSION.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                }
                for (String tool : new String[]{"aapt", "aapt2", "aidl", "zipalign"}) {
                    File f = new File(root, "android-sdk/build-tools/" + BT_VERSION + "/" + tool);
                    if (!f.isFile()) Log.w(TAG, "herramienta ausente: " + f);
                }
                Log.i(TAG, "toolchain listo: " + count + " ficheros en " + root);
            } catch (Throwable t) {
                Log.e(TAG, "no se pudo extraer el toolchain offline", t);
                deleteTree(tmp);
            }
        }
    }

    private static boolean isExecutable(String name) {
        return name.endsWith(".sh")
                || name.contains("/bin/")
                || name.contains("build-tools/")
                || name.contains("platform-tools/")
                || name.endsWith("/gradle")
                || name.endsWith("/gradle.bat");
    }

    private static void deleteTree(File file) {
        if (file == null || !file.exists()) return;
        File[] kids = file.listFiles();
        if (kids != null) {
            for (File kid : kids) deleteTree(kid);
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }
}
