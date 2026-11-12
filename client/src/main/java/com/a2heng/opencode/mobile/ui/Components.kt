package com.a2heng.opencode.mobile.ui

import android.os.Build
import android.view.RoundedCorner
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import android.net.Uri
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Audiotrack
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Icon
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 全局唯一的卡片动画。
 *
 * 所有卡片（会话/消息条目、工具卡、思考卡、详情面板/遮罩）都从这里取，不要在别处另写一份：
 * 只有淡入淡出 + 很小的缩放（0.985 → 1），没有任何位移，幅度小、干净利落，
 * 出现和消失都有。
 */
object CardMotion {
    const val SCALE_FROM = 0.985f
    val FadeIn: FiniteAnimationSpec<Float> =
        tween(durationMillis = 110, easing = FastOutSlowInEasing)
    val FadeOut: FiniteAnimationSpec<Float> =
        tween(durationMillis = 90, easing = FastOutSlowInEasing)
    val enter: EnterTransition =
        fadeIn(FadeIn) + scaleIn(
            animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing),
            initialScale = SCALE_FROM,
        )
    val exit: ExitTransition =
        fadeOut(FadeOut) + scaleOut(
            animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing),
            targetScale = SCALE_FROM,
        )
}

/**
 * 宽屏判定：宽度 ≥ 600dp（横屏手机 / 平板）时用「左侧 rail + 双栏」布局。
 * 竖屏手机保持底部导航 + 全屏子页。
 */
@Composable
fun rememberIsWideLayout(): Boolean = LocalConfiguration.current.screenWidthDp >= 600

@Composable
fun RateDot(rate: Double, modifier: Modifier = Modifier, size: Int = 9) {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    Box(
        modifier
            .size(size.dp)
            .background(rateColor(rate, dark), CircleShape),
    )
}

fun Color.luminanceIsDark(): Boolean = (0.299 * red + 0.587 * green + 0.114 * blue) < 0.5

/**
 * 读取屏幕物理 R 角（px -> dp），用于让卡片/子页的圆角与手机屏幕一致。
 * Android 12+ 才有 Display.getRoundedCorner；没有圆角的设备回退到 24dp。
 */
@Composable
fun rememberDeviceCornerRadius(): Dp {
    val view = LocalView.current
    val density = LocalDensity.current
    return remember(view, density) {
        val px = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val display = view.display
            if (display == null) 0 else {
                listOf(RoundedCorner.POSITION_TOP_LEFT, RoundedCorner.POSITION_TOP_RIGHT)
                    .mapNotNull { display.getRoundedCorner(it)?.radius }
                    .maxOrNull() ?: 0
            }
        } else 0
        if (px > 0) with(density) { px.toDp() } else 24.dp
    }
}

/**
 * 展开卡片内容区的统一下限/上限：上限避开手机 R 角的横向区域，
 * 所有详情卡（工具/思考）共用这一组高度，避免有的太矮、有的顶出圆角。
 */
@Composable
fun rememberDialogContentHeight(): Pair<Dp, Dp> {
    val screen = LocalConfiguration.current.screenHeightDp.dp
    val corner = rememberDeviceCornerRadius()
    val maxHeight = (screen - corner * 2 - 120.dp).coerceAtLeast(300.dp)
    val minHeight = (maxHeight * 0.62f).coerceAtLeast(240.dp)
    return minHeight to maxHeight
}

@Composable
fun StatCard(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = valueColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (hint != null) {
                Text(
                    hint,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun MonoText(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 12,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text,
        modifier = modifier,
        fontFamily = FontFamily.Monospace,
        fontSize = size.sp,
        color = color,
    )
}

@Composable
fun EmptyState(title: String, hint: String? = null, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (hint != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
            )
        }
    }
}

@Composable
fun LabelValue(label: String, value: String, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())
private val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())

fun formatTime(ms: Long): String {
    if (ms <= 0) return "—"
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val sameDay = now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
    val yesterday = (now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)) == 1 &&
        now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    return when {
        sameDay -> timeFmt.format(Date(ms))
        yesterday -> "昨天 " + timeFmt.format(Date(ms))
        else -> dateFmt.format(Date(ms))
    }
}

fun formatRelative(ms: Long): String {
    if (ms <= 0) return "—"
    val diff = System.currentTimeMillis() - ms
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3_600_000 -> "${diff / 60_000} 分钟前"
        diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000} 天前"
        else -> dateFmt.format(Date(ms))
    }
}

@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val source = remember { MutableInteractionSource() }
    return clickable(interactionSource = source, indication = null, onClick = onClick)
}

/**
 * 内嵌媒体预览（视频/音频），基于系统 VideoView + MediaController，无额外依赖。
 * 文件由 AppModel 写入 cacheDir 并以 file:// Uri 传入。
 */
@Composable
fun MediaPreview(
    uri: Uri,
    kind: String, // "video" | "audio"
    title: String,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var error by remember(uri) { mutableStateOf<String?>(null) }
    val isVideo = kind == "video"

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!isVideo) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Audiotrack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("音频预览", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isVideo) Modifier.heightIn(min = 180.dp, max = 420.dp) else Modifier.height(72.dp))
                .background(Color.Black.copy(alpha = if (isVideo) 1f else 0.04f), RoundedCornerShape(12.dp)),
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    FrameLayout(ctx).apply {
                        val vv = VideoView(ctx)
                        val mc = MediaController(ctx)
                        mc.setAnchorView(this)
                        vv.setMediaController(mc)
                        vv.setOnPreparedListener { mp ->
                            mp.isLooping = false
                            if (!isVideo) mc.show(0)
                        }
                        vv.setOnErrorListener { _, _, _ ->
                            error = "无法播放此${if (isVideo) "视频" else "音频"}（格式可能不受支持）"
                            true
                        }
                        addView(
                            vv,
                            FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT,
                            ),
                        )
                        tag = vv
                        vv.setVideoURI(uri)
                        vv.start()
                    }
                },
                update = { frame ->
                    val vv = frame.tag as? VideoView ?: return@AndroidView
                    val current = vv.tag as? Uri
                    if (current != uri) {
                        vv.tag = uri
                        vv.setVideoURI(uri)
                        vv.start()
                    }
                },
                onRelease = { frame ->
                    (frame.tag as? VideoView)?.stopPlayback()
                },
            )
            if (isVideo) {
                // 角标提示
                Text(
                    "视频",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        if (error != null) {
            Text(
                error!!,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}
