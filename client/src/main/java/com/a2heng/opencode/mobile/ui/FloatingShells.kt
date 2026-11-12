package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.ShellInfo
import kotlin.math.roundToInt

/**
 * 后台 shell 的悬浮球。
 *
 * - 收起时：可拖动的小圆球，位置和卡片无关。
 * - 点开：和工具/思考/文件详情共用 `DetailOverlay`（卡片居中、背景渐暗、
 *   点外面/返回键关闭），并且**全部 shell 直接全展开**，不再有“查看/收起”；
 *   每条前面的亮色竖条用来区分不同的 shell。
 */
@Composable
fun FloatingShells(model: AppModel, sessionID: String, modifier: Modifier = Modifier) {
    // 只显示当前会话自己的后台 shell；别的会话在跑命令不在这里弹球
    val shells = model.shells.filter { it.sessionID == sessionID }
    if (shells.isEmpty()) return

    var expanded by remember { mutableStateOf(false) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var outputs by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var confirmStopId by remember { mutableStateOf<String?>(null) }

    val screenH = LocalConfiguration.current.screenHeightDp.dp
    val cardMaxH = screenH * 0.72f

    // 每秒刷新一次已运行时长
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }
    // 打开就一直拉所有 shell 的最新输出（全部展开，不需要手动选）
    LaunchedEffect(expanded, shells.map { it.id }) {
        if (!expanded) return@LaunchedEffect
        while (true) {
            val fresh = shells.associate { it.id to model.loadShellOutput(it.id) }
            outputs = fresh
            kotlinx.coroutines.delay(1500)
        }
    }

    confirmStopId?.let { stopId ->
        val cmd = shells.firstOrNull { it.id == stopId }?.command.orEmpty().trim()
        val short = when {
            cmd.isBlank() -> ""
            cmd.length <= 28 -> cmd
            else -> cmd.take(28).trimEnd() + "…"
        }
        AlertDialog(
            onDismissRequest = { confirmStopId = null },
            text = {
                Text(
                    if (short.isNotBlank()) "关闭「$short」这个 shell？"
                    else "关闭这个 shell？",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    model.stopShell(stopId)
                    confirmStopId = null
                }) { Text("关闭", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStopId = null }) { Text("取消") }
            },
        )
    }

    if (!expanded) {
        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 90.dp)
                    .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
                    .size(52.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, drag ->
                            change.consume()
                            offset += drag
                        }
                    }
                    .clickableNoRipple { expanded = true },
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = CircleShape,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(46.dp)
                        .align(Alignment.BottomStart),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.PlaylistPlay,
                            contentDescription = "后台 shell",
                            tint = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                if (shells.size > 1) {
                    Surface(
                        color = MaterialTheme.colorScheme.error,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(19.dp)
                            .align(Alignment.TopEnd),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                shells.size.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.onError,
                            )
                        }
                    }
                }
            }
        }
        return
    }

    DetailOverlay(onDismiss = { expanded = false }) { close ->
        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(18.dp),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .fillMaxWidth()
                .widthIn(max = 560.dp),
        ) {
            Column(Modifier.padding(10.dp).heightIn(max = cardMaxH)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Rounded.PlaylistPlay,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "后台 shell · ${shells.size}",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .weight(1f),
                    )
                    IconButton(onClick = close, modifier = Modifier.size(30.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "关闭窗口（不会停止命令）",
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                // 全部展开：卡片整体滚动，每条 shell 自己的输出不再折叠。
                Column(
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    shells.forEachIndexed { index, shell ->
                        ShellBlock(
                            shell = shell,
                            accent = shellAccent(index),
                            elapsed = elapsedLabel(shell.started, nowMs),
                            output = outputs[shell.id].orEmpty(),
                            onStop = { confirmStopId = shell.id },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ShellBlock(
    shell: ShellInfo,
    accent: Color,
    elapsed: String,
    output: String,
    onStop: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        // 亮色竖条：用来区分不同的 shell（多条同时全展开时靠它认）
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    elapsed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    lineHeight = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    shell.command,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    lineHeight = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = accent,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onStop, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Rounded.Stop,
                        contentDescription = "停止这个命令",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.height(3.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 50.dp, end = 2.dp),
            ) {
                Text(
                    output.ifBlank { "（还没有输出）" }.takeLast(8000),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** 每条 shell 的亮色竖条：按顺序循环，主题色里挑对比度高的几个。 */
@Composable
private fun shellAccent(index: Int): Color {
    val cs = MaterialTheme.colorScheme
    val palette = listOf(cs.primary, cs.tertiary, cs.secondary, cs.error)
    return palette[index % palette.size]
}

/** 已运行时长：<1 分钟显示 12s，<1 小时显示 3m05s，再长显示 1h02m。 */
private fun elapsedLabel(started: Long, now: Long): String {
    if (started <= 0L) return ""
    val s = ((now - started) / 1000L).coerceAtLeast(0L)
    return when {
        s < 60 -> "${s}s"
        s < 3600 -> "%dm%02ds".format(s / 60, s % 60)
        else -> "%dh%02dm".format(s / 3600, (s % 3600) / 60)
    }
}
