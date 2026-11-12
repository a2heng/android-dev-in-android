package com.a2heng.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * 带预测式返回动画的对话框：返回手势拖动时整体轻微缩小/淡出，
 * 松手取消会回弹，完成才真正 dismiss（Android 14+ 才有完整手势，低版本仍是普通返回）。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun AppDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: (@Composable () -> Unit)? = null,
    dismissButton: (@Composable () -> Unit)? = null,
    contentPadding: androidx.compose.ui.unit.Dp = 24.dp,
    outerHorizontalPadding: androidx.compose.ui.unit.Dp = 24.dp,
    outerTopPadding: androidx.compose.ui.unit.Dp = 32.dp,
    outerBottomPadding: androidx.compose.ui.unit.Dp = 32.dp,
    maxContentHeight: androidx.compose.ui.unit.Dp = 500.dp,
    titleSpacing: androidx.compose.ui.unit.Dp = 16.dp,
) {
    var backProgress by remember { mutableFloatStateOf(0f) }
    val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (backOwner != null) {
            PredictiveBackHandler { back ->
                try {
                    back.collect { event -> backProgress = event.progress }
                    onDismissRequest()
                } catch (cancel: kotlinx.coroutines.CancellationException) {
                    backProgress = 0f
                    throw cancel
                }
            }
        }
        Surface(
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier = Modifier
                .padding(
                    start = outerHorizontalPadding,
                    end = outerHorizontalPadding,
                    top = outerTopPadding,
                    bottom = outerBottomPadding,
                )
                .imePadding()
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .graphicsLayer {
                    val scale = 1f - 0.07f * backProgress
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.35f * backProgress
                },
        ) {
            Column(Modifier.padding(contentPadding)) {
                title()
                Column(
                    Modifier
                        .padding(top = 16.dp)
                        .heightIn(max = maxContentHeight),
                ) { text() }
                if (confirmButton != null || dismissButton != null) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        dismissButton?.invoke()
                        confirmButton?.invoke()
                    }
                }
            }
        }
    }
}


/**
 * 详情层统一的打开/关闭驱动：进度 0→1（淡入），关闭时先回到 0 再真正移除，
 * 遮罩和卡片用同一个进度，所以不会出现「遮罩先闪一下、卡片再淡入」。
 */
@Composable
private fun rememberOverlayProgress(onDismiss: () -> Unit): Pair<Float, () -> Unit> {
    val progress = remember { androidx.compose.animation.core.Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val close: () -> Unit = { if (!closing) closing = true }
    keyboardHide()
    LaunchedEffect(Unit) {
        progress.animateTo(
            1f,
            androidx.compose.animation.core.tween(
                durationMillis = 150,
                easing = androidx.compose.animation.core.FastOutSlowInEasing,
            ),
        )
    }
    LaunchedEffect(closing) {
        if (closing) {
            progress.animateTo(
                0f,
                androidx.compose.animation.core.tween(
                    durationMillis = 110,
                    easing = androidx.compose.animation.core.FastOutSlowInEasing,
                ),
            )
            onDismiss()
        }
    }
    BackHandler(enabled = true) { close() }
    return progress.value to close
}

@Composable
private fun keyboardHide() {
    val keyboard = androidx.compose.ui.platform.LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { keyboard?.hide() }
}

/**
 * 工具/思考详情卡的自绘遮罩层。
 *
 * 刻意不用系统 `Dialog`：Dialog 会新建窗口，厂商 ROM（MagicOS 等）会给弹窗窗口套
 * 「从角落飘出来」的窗口动画，Compose 里没法关掉。这里直接画在当前组合里：
 * 遮罩和卡片一起淡入淡出、卡片带一点点缩放，点遮罩或按返回键关闭。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DetailOverlay(
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val (progress, close) = rememberOverlayProgress(onDismiss)
    Box(
        Modifier
            .fillMaxSize()
            .clickableNoRipple { close() },
    ) {
        // 遮罩跟着进度一起变，不会有突然变黑的闪烁
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * progress)),
        )
        Box(
            Modifier
                .align(Alignment.Center)
                .graphicsLayer {
                    alpha = progress
                    val s = CardMotion.SCALE_FROM + (1f - CardMotion.SCALE_FROM) * progress
                    scaleX = s
                    scaleY = s
                }
                // 卡片自己吃掉点击，避免点到卡片也被当成点遮罩
                .clickableNoRipple { },
        ) {
            content(close)
        }
    }
}

/**
 * 宽屏的右侧详情面板（Material 3 supporting pane）：聊天保持可见，
 * 详情原地淡入淡出（同一个进度驱动），点空白或返回键收起。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SideDetailPane(
    onDismiss: () -> Unit,
    content: @Composable (close: () -> Unit) -> Unit,
) {
    val (progress, close) = rememberOverlayProgress(onDismiss)
    Box(Modifier.fillMaxSize()) {
        // 空白区域点一下收起
        Box(
            Modifier
                .fillMaxSize()
                .clickableNoRipple { close() },
        )
        Box(
            Modifier
                .align(Alignment.CenterEnd)
                .graphicsLayer {
                    alpha = progress
                    val s = CardMotion.SCALE_FROM + (1f - CardMotion.SCALE_FROM) * progress
                    scaleX = s
                    scaleY = s
                }
                .clickableNoRipple { },
        ) {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp),
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier
                    .width(380.dp)
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                Box(Modifier.padding(6.dp)) { content(close) }
            }
        }
    }
}
