package com.opencode.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

/**
 * Descarga el APK de actualizacion con reanudacion por HTTP Range.
 *
 * <p>El .part y el .apk viven en getExternalFilesDir("updates"), asi que
 * sobreviven a: matar la app, apagar la pantalla, cancelar el instalador y
 * volver a pulsar "actualizar". Si el .apk ya esta completo y es valido, no se
 * vuelve a descargar.</p>
 */
public class AppUpdateService extends Service {
    public static final String ACTION_DOWNLOAD = "com.opencode.launcher.APP_UPDATE_DOWNLOAD";
    public static final String ACTION_CANCEL = "com.opencode.launcher.APP_UPDATE_CANCEL";
    public static final String EXTRA_URL = "url";
    public static final String EXTRA_TAG = "tag";
    private static final String TAG = "AdiaAppUpdate";
    private static final String CHANNEL = "app_update";
    private static final int NID = 2210;
    private static final int MAX_ATTEMPTS = 4;

    private volatile boolean running;
    private volatile boolean cancelled;
    private volatile HttpURLConnection active;
    private volatile File currentPart;
    private volatile String lastUrl;
    private volatile String lastTag;

    public static void cancel(Context ctx) {
        Intent i = new Intent(ctx, AppUpdateService.class).setAction(ACTION_CANCEL);
        try {
            ctx.startService(i);
        } catch (Exception e) {
            Log.w(TAG, "no se pudo cancelar: " + e);
        }
    }

    public static void start(Context ctx, String url, String tag) {
        Intent i = new Intent(ctx, AppUpdateService.class)
                .setAction(ACTION_DOWNLOAD)
                .putExtra(EXTRA_URL, url)
                .putExtra(EXTRA_TAG, tag);
        try {
            ctx.startForegroundService(i);
        } catch (Exception e) {
            Log.w(TAG, "no se pudo arrancar AppUpdateService: " + e);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_CANCEL.equals(intent.getAction())) {
            cancelDownload();
            return START_NOT_STICKY;
        }
        if (!ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        if (running) return START_NOT_STICKY;
        String url = intent.getStringExtra(EXTRA_URL);
        String tag = intent.getStringExtra(EXTRA_TAG);
        if (url == null || tag == null || url.isEmpty() || tag.isEmpty()) {
            stopSelf();
            return START_NOT_STICKY;
        }
        cancelled = false;
        lastUrl = url;
        lastTag = tag;
        running = true;
        startForeground(NID, notify(ServerWatch.t(this,
                "Preparando actualización", "Preparing update"), 0, 0, true, false));
        new Thread(() -> {
            try {
                download(url, tag);
            } catch (Throwable t) {
                if (!cancelled) {
                    Log.w(TAG, "descarga fallida", t);
                    fail(String.valueOf(t.getMessage()));
                }
            } finally {
                running = false;
                stopForeground(!cancelled);
                stopSelf();
            }
        }, "app-update").start();
        return START_NOT_STICKY;
    }

    /**
     * Cancela la descarga en curso y borra el .part. El .apk completo (si
     * existe) no se toca; si no existe, el siguiente toque empieza de cero.
     */
    private void cancelDownload() {
        cancelled = true;
        HttpURLConnection c = active;
        if (c != null) {
            try {
                c.disconnect();
            } catch (Exception ignored) {
            }
        }
        File part = currentPart;
        if (part != null && part.isFile()) {
            //noinspection ResultOfMethodCallIgnored
            part.delete();
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(NID);
        stopForeground(true);
        stopSelf();
    }

    private void download(String url, String tag) throws Exception {
        File dir = getExternalFilesDir("updates");
        if (dir == null) throw new IOException("sin directorio de updates");
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("no se pudo crear updates");
        File apk = new File(dir, "launcher-" + tag + ".apk");
        if (isValidApk(apk)) {
            ready(apk, tag);
            return;
        }
        File part = new File(dir, apk.getName() + ".part");
        currentPart = part;
        IOException last = null;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            if (cancelled) return;
            try {
                if (fetch(url, part)) {
                    if (apk.exists() && !apk.delete()) throw new IOException("no se pudo reemplazar el APK");
                    if (!part.renameTo(apk)) throw new IOException("no se pudo cerrar el APK");
                    if (!isValidApk(apk)) {
                        // Un paquete equivocado/corrupto no debe quedar como listo.
                        //noinspection ResultOfMethodCallIgnored
                        apk.delete();
                        throw new IOException("APK inválido");
                    }
                    ready(apk, tag);
                    return;
                }
            } catch (IOException e) {
                last = e;
                Log.w(TAG, "intento " + (attempt + 1) + " falló: " + e.getMessage());
            }
            if (attempt < MAX_ATTEMPTS - 1) {
                try {
                    Thread.sleep(1500L * (attempt + 1));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
        throw last != null ? last : new IOException("descarga incompleta");
    }

    /**
     * Descarga/reanuda. Devuelve true solo cuando el .part tiene el tamaño
     * total anunciado por el servidor.
     */
    private boolean fetch(String url, File part) throws IOException {
        if (cancelled) return false;
        long have = part.isFile() ? part.length() : 0;
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(url).openConnection();
            active = c;
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(30000);
            c.setRequestProperty("User-Agent", "adia");
            c.setRequestProperty("Accept", "application/octet-stream");
            c.setRequestProperty("Accept-Encoding", "identity");
            if (have > 0) c.setRequestProperty("Range", "bytes=" + have + "-");
            int code = c.getResponseCode();
            if (code == 416) {
                // El servidor dice que no hay nada despues de `have`: si el
                // tamaño coincide, ya esta completo; si no, reiniciar.
                String range = c.getHeaderField("Content-Range");
                long total = totalFromRange(range);
                if (total > 0 && have >= total) return true;
                if (!part.delete()) throw new IOException("no se pudo reiniciar");
                have = 0;
                return false;
            }
            if (code == 200) {
                // El servidor no acepto Range: se empieza de cero.
                have = 0;
            } else if (code != 206) {
                throw new IOException("HTTP " + code);
            }
            long length = c.getContentLengthLong();
            long total = code == 206 ? totalFromRange(c.getHeaderField("Content-Range")) : length;
            if (total <= 0 && length > 0) total = have + length;
            try (InputStream in = c.getInputStream();
                 OutputStream out = new FileOutputStream(part, have > 0)) {
                byte[] buf = new byte[64 * 1024];
                long done = have;
                long lastTick = 0;
                int n;
                while ((n = in.read(buf)) > 0) {
                    if (cancelled) return false;
                    out.write(buf, 0, n);
                    done += n;
                    long now = SystemClock.elapsedRealtime();
                    if (now - lastTick > 800) {
                        updateProgress(done, total);
                        lastTick = now;
                    }
                }
            }
            if (total > 0) return part.length() == total;
            return part.length() > 0 && length < 0;
        } finally {
            active = null;
            if (c != null) c.disconnect();
        }
    }

    private static long totalFromRange(String value) {
        if (value == null) return 0;
        int slash = value.lastIndexOf('/');
        if (slash < 0 || slash + 1 >= value.length()) return 0;
        try {
            return Long.parseLong(value.substring(slash + 1).trim());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private boolean isValidApk(File apk) {
        if (!apk.isFile() || apk.length() < 1024 * 1024) return false;
        try {
            PackageInfo info = getPackageManager()
                    .getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            return info != null && getPackageName().equals(info.packageName);
        } catch (Exception e) {
            return false;
        }
    }

    private void ready(File apk, String tag) {
        getSharedPreferences("app", MODE_PRIVATE).edit()
                .putString("app_apk", apk.getName())
                .putString("app_apk_tag", tag)
                .putLong("app_apk_ready_at", System.currentTimeMillis())
                .apply();
        updateProgress(apk.length(), apk.length());
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            Intent open = new Intent(this, MainActivity.class)
                    .setAction(MainActivity.ACTION_INSTALL_APK)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            int flags = PendingIntent.FLAG_UPDATE_CURRENT;
            if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_IMMUTABLE;
            PendingIntent pi = PendingIntent.getActivity(this, NID, open, flags);
            Notification n = new NotificationCompat.Builder(this, channel())
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle(ServerWatch.t(this, "Actualización lista", "Update ready"))
                    .setContentText(ServerWatch.t(this, "Toca para instalar ", "Tap to install ") + tag)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build();
            nm.notify(NID, n);
        }
    }

    private void fail(String message) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channel())
                .setSmallIcon(android.R.drawable.stat_notify_error)
                .setContentTitle(ServerWatch.t(this, "Descarga interrumpida",
                        "Download interrupted"))
                .setContentText(message == null ? "" : message)
                .setAutoCancel(true);
        if (lastUrl != null && lastTag != null) {
            Intent retry = new Intent(this, AppUpdateService.class)
                    .setAction(ACTION_DOWNLOAD)
                    .putExtra(EXTRA_URL, lastUrl)
                    .putExtra(EXTRA_TAG, lastTag);
            b.addAction(android.R.drawable.stat_notify_sync,
                    ServerWatch.t(this, "Reintentar", "Retry"), pending(retry, 2211));
        }
        nm.notify(NID, b.build());
    }

    private void updateProgress(long done, long total) {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.notify(NID, notify(ServerWatch.t(this,
                "Descargando actualización", "Downloading update"), done, total, false, true));
    }

    private PendingIntent pending(Intent intent, int request) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (Build.VERSION.SDK_INT >= 31) flags |= PendingIntent.FLAG_IMMUTABLE;
        return PendingIntent.getService(this, request, intent, flags);
    }

    private String channel() {
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null && Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    ServerWatch.t(this, "Actualizaciones", "Updates"),
                    NotificationManager.IMPORTANCE_LOW);
            nm.createNotificationChannel(ch);
        }
        return CHANNEL;
    }

    private Notification notify(String title, long done, long total, boolean indeterminate,
            boolean ongoing) {
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, channel())
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle(title)
                .setOnlyAlertOnce(true)
                .setOngoing(ongoing)
                .setAutoCancel(!ongoing);
        if (ongoing) {
            Intent cancel = new Intent(this, AppUpdateService.class)
                    .setAction(ACTION_CANCEL);
            b.addAction(android.R.drawable.ic_delete,
                    ServerWatch.t(this, "Cancelar", "Cancel"), pending(cancel, 2212));
        }
        if (total > 0) {
            b.setProgress(1000, (int) Math.min(1000, done * 1000 / total), false);
            b.setContentText(String.format(Locale.US, "%.1f / %.1f MB",
                    done / 1048576.0, total / 1048576.0));
        } else {
            b.setProgress(0, 0, indeterminate);
        }
        return b.build();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
