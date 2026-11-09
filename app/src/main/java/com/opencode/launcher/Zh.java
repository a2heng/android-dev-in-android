package com.opencode.launcher;

import android.content.Context;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Traducciones al chino para la UI nativa (la WebView ya usa el idioma del servidor). */
public final class Zh {
    private static final Map<String, String> M = new HashMap<>();

    static {
        // --- opciones ---
        put("Options", "选项");
        put("Close", "关闭");
        put("Start on boot", "开机启动");
        put("Boot start enabled", "已开启开机启动");
        put("Boot start disabled", "已关闭开机启动");
        put("Server watchdog", "服务端看门狗");
        put("Watchdog interval", "看门狗间隔");
        put("Watchdog: ", "看门狗：");
        put("Interval: ", "间隔：");
        put("Root (su)", "Root 权限（su）");
        put("No su", "无 su");
        put("Grant", "授权");
        put("Ask", "请求");
        put("Root disabled", "已关闭 Root");
        put("Root granted", "已授予 Root");
        put("Root denied", "Root 被拒绝");
        put("Asking for root...", "正在请求 Root…");
        put("This phone has no su", "这台手机没有 su");
        put("Server engine", "服务端引擎");
        put("Native", "原生");
        put("proot", "proot");
        put("proot only", "仅 proot");
        put("Native engine: restarting", "原生引擎：正在重启");
        put("proot engine: restarting", "proot 引擎：正在重启");
        put("App lock", "应用锁");
        put("Lock enabled", "已开启应用锁");
        put("Lock disabled", "已关闭应用锁");
        put("Your phone has no lock: create one first", "手机还没有锁屏，请先设置");
        put("Notify", "通知");
        put("Restart", "重启");
        put("OFF", "关");
        put("ON", "开");

        // --- opciones: descripciones ---
        put("On phone boot, starts the server alone without opening the app. Off costs nothing; on costs the same as having the chat open.",
                "开机后自动启动服务端，不需要打开应用。关闭不耗电；开启的耗电与一直开着聊天差不多。");
        put("Checks every X minutes whether the server answers. Notify: tells you if it died. Restart: brings it back up alone. Off does nothing.",
                "每隔 X 分钟检查服务端是否响应。通知：挂了会提醒你；重启：自动拉起；关闭：不做任何事。");
        put("How often the watchdog checks. Uses the system's inexact alarm (cheap); in deep sleep it may lag. Stopping the server by hand pauses it until the next start.",
                "看门狗检查的频率。使用系统非精确闹钟（省电）；深度睡眠时可能延迟。手动停止服务端会暂停看门狗，直到下次启动。");
        put("If the phone is rooted, the agent can run commands via su (Magisk/KernelSU ask). Off never prompts. If no prompt appears, tap Grant.",
                "如果手机有 Root，agent 可以通过 su 执行命令（Magisk/KernelSU 会询问）。关闭后不会弹窗。没有弹窗时点“授权”。");
        put("Native: the server runs without proot, faster. Commands that need pkg/apt run inside proot automatically. If native does not start, it falls back to proot.",
                "原生：服务端不经过 proot，速度更快；需要 pkg/apt 的命令会自动进入 proot。原生启动失败会自动回退到 proot。");
        put("Asks for the phone unlock (PIN, pattern, fingerprint) once when opening the chat. Off by default; if your phone has no lock, it sends you to create one.",
                "进入聊天时要求一次手机解锁（PIN、图案、指纹）。默认关闭；手机没有锁屏时会引导你去设置。");

        // --- principal ---
        put("Controls", "控制面板");
        put("Controls panel", "控制面板");
        put("Start and open", "启动并打开");
        put("Open chat", "打开聊天");
        put("Stop", "停止");
        put("Copy", "复制");
        put("Log", "日志");
        put("Clear", "清空");
        put("Tools", "工具");
        put("Usage", "用量");
        put("Share with opencode", "分享给 opencode");
        put("Send", "发送");
        put("Cancel", "取消");
        put("New session", "新建会话");
        put("Existing session", "已有会话");
        put("Selected session", "已选会话");
        put("No sessions yet", "还没有会话");
        put("No sessions.", "没有会话。");
        put("Checking server", "检查服务端");
        put("Checking server...", "正在检查服务端…");
        put("Server open", "服务端已启动");
        put("Server open (still alive)", "服务端已启动（仍在运行）");
        put("Server closed", "服务端已停止");
        put("Server stopped", "服务端已停止");
        put("Server stopped: starting…", "服务端已停止：正在启动…");
        put("Server not answering", "服务端无响应");
        put("Server not answering: retrying", "服务端无响应：正在重试");
        put("Server unavailable: will be created on send", "服务端不可用：发送时会创建");
        put("Ready. Press Start and open.", "就绪。点击“启动并打开”。");
        put("OpenCode did not start", "OpenCode 未启动");
        put("Local runtime failed", "本地运行时启动失败");
        put("Local runtime failed, check the log", "本地运行时失败，请查看日志");
        put("Restart server", "重启服务端");
        put("Stop server", "停止服务端");
        put("OpenCode local", "OpenCode 本地");
        put("Local server", "本地服务端");
        put("OpenCode update", "OpenCode 更新");
        put("Check app update", "检查应用更新");
        put("Report problem", "报告问题");
        put("Update opencode", "更新 opencode");
        put("Updating OpenCode", "正在更新 OpenCode");
        put("OpenCode is up to date", "OpenCode 已是最新");
        put("No updates available", "没有可用更新");
        put("New app version", "发现新版本");
        put("Update available: ", "有可用更新：");
        put("Downloading new version...", "正在下载新版本…");
        put("Preparing update", "准备更新");
        put("Opening the chat...", "正在打开聊天…");
        put("Share: preparing...", "分享：准备中…");
        put("Choose where to send it", "选择发送位置");
        put("Create a new conversation", "新建会话");
        put("Choose a new or existing conversation", "选择新建或已有会话");
        put("Optional instruction", "可选说明");
        put("Tap to open the app and start it", "点击打开应用并启动");
        put("This may take a few seconds", "可能需要几秒钟");
        put("All files access is needed to start the agent", "需要“所有文件访问”权限才能启动 agent");
        put("Enable 'All files' and retry", "请开启“所有文件”权限后重试");
        put("Enable 'All files' to share files", "请开启“所有文件”权限以分享文件");
        put("Enable 'Install unknown apps' and come back", "请允许“安装未知应用”后返回");
        put("Files are copied to Downloads/opencode-shares", "文件会复制到 Downloads/opencode-shares");
        put("Files are copied to the app's private folder (Android/data)", "文件会复制到应用私有目录（Android/data）");
        put("No Downloads access: using the private folder", "无法访问 Downloads：使用私有目录");
        put("Delete", "删除");
        put("Delete chat", "删除会话");
        put("Deleting…", "正在删除…");
        put("Retry", "重试");
        put("Retrying...", "正在重试…");
        put("Progress timeout", "进度超时");
        put("Could not copy", "复制失败");
        put("Log copied", "日志已复制");
        put("Log empty, nothing to copy", "日志为空，没有可复制内容");
        put("Could not load sessions", "加载会话失败");
        put("No connection to the agent", "无法连接 agent");
        put("No connection to the agent. Retrying...", "无法连接 agent，正在重试…");
        put("The agent did not answer. Still trying...", "agent 没有响应，仍在尝试…");
        put("No server: start it first", "服务端未启动：请先启动");
        put("Server ?", "服务端？");
        put("Server down", "服务端离线");
        put("Hung server, restarting…", "服务端卡住，正在重启…");
        put("Hung server: restarting alone…", "服务端卡住：自动重启中…");
        put("Still open, check the log", "仍然开着，请查看日志");
        put("Did not start: check the log", "没有启动，请查看日志");
        put("Did not start. Retrying...", "没有启动，正在重试…");
        put("Local runtime not available", "本地运行时不可用");
        put("This install has no native runtime", "此安装没有原生运行时");
        put("Update finished, restarting…", "更新完成，正在重启…");
        put("Restarting to apply the change...", "正在重启以应用更改…");
        put("Will apply on server start.", "下次启动服务端时生效。");
        put("Watchdog paused until next start.", "看门狗已暂停，直到下次启动。");
    }

    private static void put(String en, String zh) {
        M.put(en, zh);
    }

    public static String tr(String en) {
        if (en == null) return "";
        String s = M.get(en);
        return s != null ? s : en;
    }

    public static boolean isZh(Context c) {
        try {
            String l = c.getSharedPreferences("app", Context.MODE_PRIVATE)
                    .getString("lang", "auto");
            if ("zh".equals(l)) return true;
            if ("en".equals(l) || "es".equals(l)) return false;
        } catch (Exception ignored) {
        }
        String dl = Locale.getDefault().getLanguage();
        return dl != null && dl.startsWith("zh");
    }
}
