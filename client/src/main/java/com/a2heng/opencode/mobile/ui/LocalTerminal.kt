package com.a2heng.opencode.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.a2heng.opencode.mobile.data.AppModel

/**
 * 本机 Termux 终端（Adia 内置）。
 *
 * 进程、PTY 输出和 libvterm emulator 都在 [AppModel] 里，所以旋转屏幕、
 * 切到别的页签再回来都不会丢；这里只把共用的 [TerminalPane] 绑到本机会话。
 * 按键区、字号、固定键盘占位与服务端 PTY 完全一致。
 */
@Composable
fun LocalTerminalView(model: AppModel) {
    LaunchedEffect(Unit) { model.ensureLocalTerminal() }
    TerminalPane(
        emulator = model.localTerminalEmulator,
        fontSize = model.ptyFontSize,
        onFontSize = model::updatePtyFontSize,
        onInput = model::sendLocalTerminal,
        placeholder = when {
            model.localTerminalStarting -> "正在打开 Termux…"
            model.localTerminalError != null -> model.localTerminalError ?: ""
            else -> "正在连接 shell…"
        },
    )
}
