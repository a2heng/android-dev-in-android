package com.a2heng.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import android.content.Context
import android.graphics.Typeface
import android.os.Build
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.io.File
import androidx.compose.ui.unit.sp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.PtyInfo
import com.a2heng.opencode.mobile.LocalShellHost
import org.connectbot.terminal.Terminal
import org.connectbot.terminal.TerminalEmulator

@Composable
fun TerminalScreen(model: AppModel) {
    LaunchedEffect(Unit) {
        if (model.ptys.isEmpty()) model.loadPtys()
    }

    val active = model.ptyActive
    if (active != null) {
        BackHandler { model.detachPty() }
        TerminalView(model, active)
    } else if (model.localTerminalOpen) {
        BackHandler { model.closeLocalTerminalView() }
        LocalTerminalView(model)
    } else {
        // Ojo: antes había un lambda dentro de otro lambda y el toque no hacía
        // nada (el warning "Lambda expression is never invoked").
        val openLocal: (() -> Unit)? = if (LocalShellHost.available) {
            { model.openLocalTerminal() }
        } else {
            null
        }
        PtyList(model, onOpenLocal = openLocal)
    }
}

@Composable
private fun PtyList(model: AppModel, onOpenLocal: (() -> Unit)? = null) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("终端会话", style = MaterialTheme.typography.titleSmall)
                Text(
                    "电脑上开一个 PTY，手机直接敲命令",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = { model.createPty() }) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("新建")
            }
        }

        if (onOpenLocal != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clickableNoRipple(onOpenLocal),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("本机 Termux", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "内置 proot + bash，直接在这台手机上跑",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        "打开",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        model.ptyError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }

        if (model.ptys.isEmpty()) {
            EmptyState("还没有终端", "点「新建」会在服务端当前目录启动 bash")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(model.ptys, key = { it.id }) { pty ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple { model.attachPty(pty) }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = if (pty.status == "running") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(pty.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                            Text(
                                "${pty.command} · pid ${pty.pid} · ${pty.cwd}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Text(
                            pty.status,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (pty.status == "running") MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        IconButton(onClick = { model.removePty(pty.id) }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = "结束",
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }
}

private val controlKeys = listOf(
    "Ctrl+C" to "\u0003",
    "Tab" to "\t",
    "Esc" to "\u001b",
    "↑" to "\u001b[A",
    "↓" to "\u001b[B",
    "←" to "\u001b[D",
    "→" to "\u001b[C",
)

@Composable
private fun TerminalView(model: AppModel, pty: PtyInfo) {
    TerminalPane(
        emulator = model.ptyEmulator,
        fontSize = model.ptyFontSize,
        onFontSize = model::updatePtyFontSize,
        onInput = model::sendPty,
        placeholder = "正在连接终端…",
    )
}

/**
 * 终端外壳：服务端 PTY 和本机 Termux 共用。
 *
 * 占位区固定（竖屏底部 46% 高 / 横屏右侧 37% 宽），软键盘不改变它；
 * 按键区始终在占位区顶部。旋转屏幕时 emulator/进程由 ViewModel 持有，
 * 这里只负责重新绑定视图。
 */
@Composable
internal fun TerminalPane(
    emulator: TerminalEmulator?,
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    onInput: (String) -> Unit,
    placeholder: String,
) {
    val focus = remember { FocusRequester() }
    val appContext = LocalContext.current.applicationContext
    val typeface = remember { terminalTypeface(appContext) }
    var ctrl by remember { mutableStateOf(false) }
    var shift by remember { mutableStateOf(false) }
    var alt by remember { mutableStateOf(false) }

    val cfg = LocalConfiguration.current
    val wide = cfg.screenWidthDp >= 600
    // 固定键盘占位（不随键盘高度变化）：竖屏底部 46% 高，横屏右侧 37% 宽
    val reserved = if (wide) (cfg.screenWidthDp * 0.37f).dp else (cfg.screenHeightDp * 0.46f).dp
    val controls: @Composable (Boolean) -> Unit = { compact ->
        TerminalControls(
            fontSize = fontSize,
            onFontSize = onFontSize,
            onInput = onInput,
            ctrl = ctrl,
            shift = shift,
            alt = alt,
            compact = compact,
        ) { c, s, a -> ctrl = c; shift = s; alt = a }
    }

    Box(Modifier.fillMaxSize()) {
        TerminalBox(
            emulator = emulator,
            fontSize = fontSize,
            typeface = typeface,
            focus = focus,
            placeholder = placeholder,
            modifier = Modifier
                .fillMaxSize()
                .then(if (wide) Modifier.padding(end = reserved) else Modifier.padding(bottom = reserved)),
        )
        if (wide) {
            // 横屏：按钮在右侧留白区的顶部
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(bottomStart = 10.dp),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .width(reserved),
            ) {
                Column(
                    Modifier.padding(4.dp).fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                    )
                    Box(Modifier.padding(top = 6.dp)) { controls(true) }
                }
            }
        } else {
            // 竖屏：按钮在底部留白区的上沿（分隔线下面）
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(reserved),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)),
                )
                Box(Modifier.padding(top = 4.dp), contentAlignment = Alignment.Center) { controls(false) }
            }
        }
    }
}

@Composable
private fun TerminalBox(
    emulator: TerminalEmulator?,
    fontSize: Float,
    typeface: Typeface,
    focus: FocusRequester,
    placeholder: String,
    modifier: Modifier,
) {
    Surface(color = MaterialTheme.colorScheme.surface, modifier = modifier) {
        if (emulator == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    placeholder,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            key(fontSize) {
                Terminal(
                    terminalEmulator = emulator,
                    modifier = Modifier.fillMaxSize(),
                    initialFontSize = fontSize.sp,
                    minFontSize = 5.sp,
                    maxFontSize = 28.sp,
                    typeface = typeface,
                    backgroundColor = MaterialTheme.colorScheme.surface,
                    foregroundColor = MaterialTheme.colorScheme.onSurface,
                    keyboardEnabled = true,
                    showSoftKeyboard = true,
                    focusRequester = focus,
                )
            }
        }
    }
}

private fun arrowSeq(dir: Char, ctrl: Boolean, shift: Boolean, alt: Boolean): String {
    val mod = 1 + (if (shift) 1 else 0) + (if (alt) 2 else 0) + (if (ctrl) 4 else 0)
    return if (mod == 1) "\u001b[$dir" else "\u001b[1;${mod}$dir"
}

/**
 * 按键区（固定两行）：功能键 5 列 + 方向键 3 列，共 8 列。
 * 整体按可用宽度自适应缩放，横屏窄条里也不会溢出。
 */
@Composable
private fun TerminalControls(
    fontSize: Float,
    onFontSize: (Float) -> Unit,
    onInput: (String) -> Unit,
    ctrl: Boolean,
    shift: Boolean,
    alt: Boolean,
    compact: Boolean,
    onMods: (Boolean, Boolean, Boolean) -> Unit,
) {
    androidx.compose.foundation.layout.BoxWithConstraints {
        val gap = 2.dp
        // 8 列 + 7 个列间距 + 两组之间的 8dp
        val chipW = ((maxWidth - gap * 7 - 8.dp) / 8).coerceIn(24.dp, 36.dp)
        val chipH = (chipW * 0.72f).coerceIn(20.dp, 26.dp)
        val chipFont = (chipW.value * 0.28f).coerceIn(7f, 9f).sp

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeyChip("A−", chipW, chipH, chipFont) { onFontSize(fontSize - 1f) }
                    KeyChip("Esc", chipW, chipH, chipFont) { onInput("\u001b") }
                    KeyChip("^C", chipW, chipH, chipFont) { onInput("\u0003") }
                    ToggleChip("Ctrl", ctrl, chipW, chipH, chipFont) { onMods(!ctrl, shift, alt) }
                    ToggleChip("Alt", alt, chipW, chipH, chipFont) { onMods(ctrl, shift, !alt) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeyChip("A+", chipW, chipH, chipFont) { onFontSize(fontSize + 1f) }
                    KeyChip("Tab", chipW, chipH, chipFont) { onInput(if (shift) "\u001b[Z" else "\t") }
                    EnterChip(chipW, chipH, chipFont) { cr -> onInput(if (cr) "\r" else "\n") }
                    ToggleChip("Shift", shift, chipW, chipH, chipFont) { onMods(ctrl, !shift, alt) }
                    KeyChip("Del", chipW, chipH, chipFont) { onInput("\u007f") }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeyChip("Home", chipW, chipH, chipFont) { onInput("\u001b[H") }
                    KeyChip("↑", chipW, chipH, chipFont) { onInput(arrowSeq('A', ctrl, shift, alt)) }
                    KeyChip("End", chipW, chipH, chipFont) { onInput("\u001b[F") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    KeyChip("←", chipW, chipH, chipFont) { onInput(arrowSeq('D', ctrl, shift, alt)) }
                    KeyChip("↓", chipW, chipH, chipFont) { onInput(arrowSeq('B', ctrl, shift, alt)) }
                    KeyChip("→", chipW, chipH, chipFont) { onInput(arrowSeq('C', ctrl, shift, alt)) }
                }
            }
        }
    }
}

@Composable
private fun KeyChip(
    label: String,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier
            .size(w, h)
            .clickableNoRipple(onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ToggleChip(
    label: String,
    on: Boolean,
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onClick: () -> Unit,
) {
    Surface(
        color = if (on) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant,
        contentColor = if (on) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant,
        border = BorderStroke(
            1.dp,
            if (on) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outline.copy(alpha = 0.30f),
        ),
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier
            .size(w, h)
            .clickableNoRipple(onClick),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                fontSize = fontSize,
                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                softWrap = false,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** 回车：点=CR，长按=LF。 */
@Composable
private fun EnterChip(
    w: androidx.compose.ui.unit.Dp,
    h: androidx.compose.ui.unit.Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    onSend: (Boolean) -> Unit,
) {
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    Surface(
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shape = RoundedCornerShape(7.dp),
        modifier = Modifier
            .size(w, h)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onSend(true) },
                    onLongPress = {
                        haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                        onSend(false)
                    },
                )
            },
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("回车", fontSize = fontSize, maxLines = 1, softWrap = false, textAlign = TextAlign.Center)
        }
    }
}

/**
 * 终端字体：优先用打包的 Sarasa Fixed SC（中英文等宽、拉丁 0.5em），
 * 取不到再回退系统窄等宽字体。
 */
private fun terminalTypeface(context: Context): Typeface {
    runCatching {
        return Typeface.createFromAsset(context.assets, "fonts/SarasaFixedSC-Regular.ttf")
    }
    val candidates = listOf(
        "/system/fonts/DroidSansMono.ttf",
        "/system/fonts/RobotoMono-Regular.ttf",
        "/system/fonts/CutiveMono.ttf",
    )
    for (path in candidates) {
        if (java.io.File(path).exists()) runCatching { return Typeface.createFromFile(path) }
    }
    return Typeface.MONOSPACE
}
