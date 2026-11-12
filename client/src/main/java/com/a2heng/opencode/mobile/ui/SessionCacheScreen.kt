package com.a2heng.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.CacheStep
import com.a2heng.opencode.mobile.data.SessionInfo
import com.a2heng.opencode.mobile.data.Tokens
import com.a2heng.opencode.mobile.data.formatCost
import com.a2heng.opencode.mobile.data.formatTokens
import com.a2heng.opencode.mobile.data.rateColorHint
import com.a2heng.opencode.mobile.data.rateLabel

/**
 * 单个会话的「最近缓存状态」。从会话列表点命中率进入，不占底部标签。
 * 重新设计：大号命中率 + 关键指标 + 趋势 + 逐步明细。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionCacheScreen(
    model: AppModel,
    session: SessionInfo,
    onBack: () -> Unit,
    onOpenChat: (SessionInfo) -> Unit,
) {
    LaunchedEffect(session.id) {
        if (model.cacheDetail?.session?.id != session.id) model.loadCacheDetail(session)
    }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "cache-enter",
    )
    val motion = maxOf(backProgress, enterProgress)
    val deviceCorner = rememberDeviceCornerRadius()
    val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backOwner != null) {
        PredictiveBackHandler { events ->
            try {
                events.collect { event -> backProgress = event.progress }
                onBack()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                backProgress = 0f
                throw cancel
            }
        }
    } else {
        BackHandler { onBack() }
    }

    val detail = model.cacheDetail?.takeIf { it.session.id == session.id }
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()

    Scaffold(
        modifier = Modifier
            .clip(RoundedCornerShape(deviceCorner * motion))
            .graphicsLayer {
                // 子页返回：不移动，只淡出；干净利落
                alpha = 1f - motion
            },
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                    }
                },
                title = {
                    Column {
                        Text(session.shortTitle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "最近缓存状态",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onOpenChat(session) }) {
                        Icon(Icons.Rounded.ChatBubbleOutline, contentDescription = "打开会话")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        when {
            detail == null && model.cacheDetailLoading -> Box(
                Modifier.padding(padding).fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(strokeWidth = 2.dp) }

            detail == null -> Column(
                Modifier.padding(padding).fillMaxSize().padding(24.dp),
            ) {
                Text(
                    model.cacheDetailError ?: "还没有这个会话的缓存数据",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(12.dp))
                Text("重新打开这个页面会自动刷新", style = MaterialTheme.typography.labelSmall)
            }

            else -> CacheDetailBody(detail, dark, padding, model.modelGroupById)
        }
    }
}

@Composable
private fun CacheDetailBody(
    detail: com.a2heng.opencode.mobile.data.SessionCache,
    dark: Boolean,
    padding: androidx.compose.foundation.layout.PaddingValues,
    groups: Map<String, Int>,
) {
    val totals = detail.totals
    val color = rateColor(detail.rate, dark)

    LazyColumn(
        modifier = Modifier.padding(padding).fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            rateLabel(detail.rate),
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = color,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            rateColorHint(detail.rate),
                            style = MaterialTheme.typography.labelMedium,
                            color = color,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                        Spacer(Modifier.weight(1f))
                        Column(horizontalAlignment = Alignment.End) {
                            Text("最近一步", style = MaterialTheme.typography.labelSmall)
                            Text(
                                rateLabel(detail.lastRate),
                                style = MaterialTheme.typography.titleMedium,
                                color = rateColor(detail.lastRate, dark),
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (detail.rate / 100.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        color = color,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "${detail.session.model?.let { modelDisplay(it, groups) } ?: "—"} · ${detail.steps.size} 步 · ${formatCost(detail.cost)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("缓存读取", formatTokens(totals.cacheRead), Modifier.weight(1f))
                StatCard("未命中输入", formatTokens(totals.input), Modifier.weight(1f))
                StatCard("输出", formatTokens(totals.outputAll), Modifier.weight(1f))
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("缓存写入", formatTokens(totals.cacheWrite), Modifier.weight(1f))
                StatCard("花费", formatCost(detail.cost), Modifier.weight(1f))
                StatCard("消息时间", formatTime(detail.session.updated), Modifier.weight(1f))
            }
        }

        item {
            Text("最近 ${minOf(40, detail.steps.size)} 步", style = MaterialTheme.typography.titleSmall)
        }
        item {
            CacheTrend(detail.steps.takeLast(40), dark)
        }

        item {
            Text("逐步明细", style = MaterialTheme.typography.titleSmall)
        }
        items(detail.steps.asReversed(), key = { it.id }) { step ->
            StepRow(step, dark)
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun CacheTrend(steps: List<CacheStep>, dark: Boolean) {
    if (steps.isEmpty()) return
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(120.dp)
                .padding(10.dp),
        ) {
            val gap = 2.dp.toPx()
            val barWidth = ((size.width - gap * (steps.size - 1)) / steps.size).coerceAtLeast(1f)
            val h = size.height
            steps.forEachIndexed { index, step ->
                val barH = (h * (step.rate / 100.0)).toFloat().coerceIn(2f, h)
                drawRect(
                    color = rateColor(step.rate, dark),
                    topLeft = Offset(index * (barWidth + gap), h - barH),
                    size = Size(barWidth, barH),
                )
            }
        }
    }
}

@Composable
private fun StepRow(step: CacheStep, dark: Boolean) {
    Column(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(7.dp)
                    .background(rateColor(step.rate, dark), RoundedCornerShape(4.dp)),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                formatTime(step.time),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(62.dp),
            )
            Text(
                rateLabel(step.rate),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = rateColor(step.rate, dark),
                modifier = Modifier.width(56.dp),
            )
            Text(
                "in ${formatTokens(step.tokens.input)} · cache ${formatTokens(step.tokens.cacheRead)} · out ${formatTokens(step.tokens.outputAll)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    }
}
