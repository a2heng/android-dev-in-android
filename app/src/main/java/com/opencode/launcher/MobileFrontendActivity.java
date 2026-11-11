package com.opencode.launcher;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Host ligero del frontend Compose embebido (:client de opencode-mobile).
 *
 * <p>No carga WebView ni el layout clasico: solo arranca el runtime local,
 * espera a que el servicio responda y monta la UI Compose. Es el modo
 * "un APK = backend + frontend" de Adia.</p>
 */
public class MobileFrontendActivity extends AppCompatActivity {
    private static final String TAG = "OpenCodeLocal";
    private static final int PORT = BuildConfig.SERVER_PORT;
    private static final String URL = "http://127.0.0.1:" + PORT;
    private static final long HEALTH_TIMEOUT_MS = 120_000L;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private boolean mounted;
    private boolean starting;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!"mobile".equals(BuildConfig.UI_MODE)) {
            // Respaldo: el frontend WebView clasico.
            startActivity(new android.content.Intent(this, MainActivity.class));
            finish();
            return;
        }
        // Igual que el APK independiente de OpenCode Mobile: edge-to-edge +
        // adjustNothing; el IME se gestiona con imePadding() en Compose.
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(getWindow(), false);
        androidx.core.view.WindowInsetsControllerCompat c =
                androidx.core.view.WindowCompat.getInsetsController(getWindow(), getWindow().getDecorView());
        if (c != null) {
            c.setSystemBarsBehavior(
                    androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            boolean dark = (getResources().getConfiguration().uiMode
                    & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                    == android.content.res.Configuration.UI_MODE_NIGHT_YES;
            c.setAppearanceLightStatusBars(!dark);
            c.setAppearanceLightNavigationBars(!dark);
        }
        setContentView(buildLoading());
        startServerAndShow();
    }

    private LinearLayout buildLoading() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0E1116"));
        ProgressBar bar = new ProgressBar(this);
        root.addView(bar, new LinearLayout.LayoutParams(dp(42), dp(42)));
        status = new TextView(this);
        status.setText("正在启动 opencode…");
        status.setTextColor(Color.parseColor("#C7CDD6"));
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(14);
        root.addView(status, lp);
        return root;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void startServerAndShow() {
        if (starting || mounted) return;
        starting = true;
        LocalServerService.start(this);
        new Thread(() -> {
            long deadline = System.currentTimeMillis() + HEALTH_TIMEOUT_MS;
            boolean ok = false;
            while (System.currentTimeMillis() < deadline) {
                if (probe()) {
                    ok = true;
                    break;
                }
                try {
                    Thread.sleep(600);
                } catch (InterruptedException ignored) {
                    return;
                }
            }
            final boolean healthy = ok;
            ui.post(() -> {
                starting = false;
                if (healthy) {
                    showFrontend();
                } else if (status != null) {
                    status.setText("服务没有启动起来，退出重进一次（或查看日志 OpenCodeLocal）");
                }
            });
        }, "mobile-health").start();
    }

    private boolean probe() {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(URL + "/api/info").openConnection();
            c.setConnectTimeout(1200);
            c.setReadTimeout(2000);
            c.setUseCaches(false);
            String auth = Base64.encodeToString(
                    ("opencode:" + LocalRuntime.serverPassword(this)).getBytes(StandardCharsets.UTF_8),
                    Base64.NO_WRAP);
            c.setRequestProperty("Authorization", "Basic " + auth);
            return c.getResponseCode() == 200;
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private void showFrontend() {
        if (mounted) return;
        mounted = true;
        String pwd = LocalRuntime.serverPassword(this);
        try {
            LocalRuntime.ensureRootfs(this);
            String fsRoot = LocalRuntime.rootfsDir(this).getAbsolutePath();
            com.a2heng.opencode.mobile.LocalShellSpawner shell = () -> {
                try {
                    return LocalRuntime.localShell(this);
                } catch (Throwable t) {
                    Log.w(TAG, "no se pudo abrir el shell local: " + t);
                    return null;
                }
            };
            com.a2heng.opencode.mobile.OpenCodeMobileHost.show(this, URL, pwd, shell, fsRoot);
        } catch (Throwable t) {
            Log.w(TAG, "frontend Compose fallo; se abre el WebView", t);
            mounted = false;
            startActivity(new android.content.Intent(this, MainActivity.class));
            finish();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Si el proceso se recreo o el servicio aun no estaba listo, reintenta solo.
        if (!mounted) startServerAndShow();
    }
}
