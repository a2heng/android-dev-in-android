package com.a2heng.opencode.mobile

import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.SharedPayload
import com.a2heng.opencode.mobile.ui.AppRoot
import com.a2heng.opencode.mobile.ui.OcTheme
import java.lang.Process

/** 宿主（Adia）提供的本机 shell 进程（Termux/proot 里的 bash）。 */
fun interface LocalShellSpawner {
    fun spawn(): Process?
}

/** 本机终端能力：由宿主注入；独立 APK 里为 null，终端页只有服务端 PTY。 */
object LocalShellHost {
    @Volatile
    var spawner: LocalShellSpawner? = null
    val available: Boolean get() = spawner != null
}

/**
 * 整个 Compose 客户端的根组合。
 *
 * - 独立 APK：`serverUrl = null`，走保存的服务器 / 登录页。
 * - Adia（单 APK）：传入本机 opencode 的 URL、随机密码，以及可选的
 *   本机 shell 与文件根目录。
 */
@Composable
fun OpenCodeMobileRoot(
    serverUrl: String? = null,
    password: String? = null,
    shared: SharedPayload? = null,
    fsRoot: String? = null,
    onSharedConsumed: () -> Unit = {},
) {
    val model: AppModel = viewModel()
    model.overrideFsRoot(fsRoot)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // 启动时的 connect 可能早于应用可见，保活服务会启动失败；回到前台补一次。
        model.ensureKeepAlive()
        model.refreshOnResume()
    }
    OcTheme(themeMode = model.themeMode, appearance = model.appearance) {
        LaunchedEffect(shared) {
            shared?.let {
                model.applyShare(it.text, it.image)
                onSharedConsumed()
            }
        }
        LaunchedEffect(serverUrl, password) {
            if (!serverUrl.isNullOrBlank()) {
                // 同进程服务：不写入 SharedPreferences，避免把独立客户端的配置覆盖掉。
                model.connect(serverUrl, password.orEmpty(), remember = false)
            } else {
                model.autoConnect()
            }
        }
        AppRoot(model)
    }
}

/**
 * 给宿主（Adia 的 Java Activity）用的入口：
 * 启动本地 opencode 之后直接调用它，整个界面就是这套 Compose 客户端。
 */
object OpenCodeMobileHost {
    @JvmStatic
    fun show(activity: ComponentActivity, url: String, password: String) {
        activity.setContent {
            OpenCodeMobileRoot(serverUrl = url, password = password)
        }
    }

    /**
     * @param shell  本机 Termux shell（null = 终端页只用服务端 PTY）
     * @param fsRoot 文件页的“/”；null = 真机根目录（Adia 用真实文件系统）
     */
    @JvmStatic
    @JvmOverloads
    fun show(
        activity: ComponentActivity,
        url: String,
        password: String,
        shell: LocalShellSpawner?,
        fsRoot: String?,
    ) {
        LocalShellHost.spawner = shell
        activity.setContent {
            OpenCodeMobileRoot(serverUrl = url, password = password, fsRoot = fsRoot)
        }
    }
}
