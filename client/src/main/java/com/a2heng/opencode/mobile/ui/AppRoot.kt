package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a2heng.opencode.mobile.data.AppModel

@Composable
fun AppRoot(model: AppModel) {
    if (!model.connected) {
        ConnectScreen(model)
        return
    }
    // 宽屏（横屏手机 / 平板）：左侧 rail + 会话页双栏，聊天不再整页覆盖
    val wide = rememberIsWideLayout()
    // 会话页、缓存页都盖在列表上：预测式返回时整页滑走，露出下面的上一页（否则背景是黑的）
    androidx.compose.foundation.layout.Box(Modifier.fillMaxSize()) {
        MainScreen(model, wide)
        val current = model.current
        if (current != null && !wide) {
            ChatScreen(model, current)
        }
        val cache = model.cacheTarget
        if (cache != null) {
            SessionCacheScreen(
                model = model,
                session = cache,
                onBack = { model.closeCache() },
                onOpenChat = { session ->
                    model.closeCache()
                    model.openSession(session)
                },
            )
        }
        val gitDir = model.gitDirectory
        if (gitDir != null) {
            DetailOverlay(onDismiss = { model.closeGit() }) { close ->
                GitScreen(
                    model = model,
                    directory = gitDir,
                    onBack = close,
                )
            }
        }
    }
}

private data class Tab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

private val tabs = listOf(
    Tab("会话", Icons.Rounded.ChatBubbleOutline),
    Tab("文件", Icons.Rounded.FolderOpen),
    Tab("终端", Icons.Rounded.Terminal),
    Tab("设置", Icons.Rounded.Settings),
)

private fun tabTitle(tab: Int): String = when (tab) {
    0 -> "会话"
    1 -> "文件"
    2 -> "终端"
    else -> "设置"
}

@Composable
fun MainScreen(model: AppModel, wide: Boolean = false) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showNewSession by rememberSaveable { mutableStateOf(false) }

    // 不再放刷新按钮：进入页面就自动刷新一次（回到前台的刷新在 refreshOnResume）
    LaunchedEffect(tab, wide) {
        when (tab) {
            0 -> model.refreshSessions()
            1 -> model.loadDir(model.fsDir)
            2 -> model.loadPtys()
        }
    }

    val content: @Composable () -> Unit = {
        when (tab) {
            0 -> SessionsScreen(
                model = model,
                onNewSession = { showNewSession = true },
                onOpenCache = { model.openCache(it) },
            )
            1 -> FilesScreen(model, wide)
            2 -> TerminalScreen(model)
            else -> SettingsScreen(model)
        }
    }

    if (wide) {
        // 横屏 / 平板：左侧 rail 只吃宽度、不吃高度，主区域完整保留；
        // 会话页变成「列表 + 聊天」双栏（master-detail）。
        // 注意用 Surface 提供 onBackground 内容色，否则各页标题会是默认的黑色。
        Surface(
            color = MaterialTheme.colorScheme.background,
            contentColor = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(Modifier.fillMaxSize()) {
                RailNav(model, tab) { tab = it }
                Column(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .statusBarsPadding(),
                ) {
                    ErrorBanner(model)
                if (tab == 0) {
                    Row(Modifier.weight(1f).fillMaxWidth()) {
                        Box(
                            Modifier
                                .width(300.dp)
                                .fillMaxHeight(),
                        ) { content() }
                        androidx.compose.material3.VerticalDivider(
                            Modifier.fillMaxHeight(),
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                        )
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            val current = model.current
                            if (current != null) {
                                ChatScreen(model, current, wide = true)
                            } else {
                                EmptyChatHint()
                            }
                        }
                    }
                } else {
                    Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                }
            }
            }
        }
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { index, item ->
                        NavigationBarItem(
                            selected = tab == index,
                            onClick = {
                                if (index == 0 && tab == 0 && model.current != null) {
                                    model.closeSession()
                                    model.refreshSessions()
                                } else {
                                    tab = index
                                }
                            },
                            icon = {
                                if (index == 0 && model.activeIds.isNotEmpty()) {
                                    BadgedBox(
                                        badge = {
                                            Badge(containerColor = MaterialTheme.colorScheme.primary)
                                        },
                                    ) {
                                        Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp))
                                    }
                                } else {
                                    Icon(item.icon, contentDescription = item.label, modifier = Modifier.size(22.dp))
                                }
                            },
                            label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        )
                    }
                }
            },
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().statusBarsPadding()) {
                ErrorBanner(model)
                content()
            }
        }
    }

    if (showNewSession) {
        NewSessionDialog(
            model = model,
            onDismiss = { showNewSession = false },
            onCreate = { dir ->
                showNewSession = false
                model.createSession(dir)
            },
        )
    }
}

/** 宽屏左侧导航：64dp，图标 + 小标签，替代底部导航（不占高度）。 */
@Composable
private fun RailNav(model: AppModel, tab: Int, onTab: (Int) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 2.dp) {
        Column(
            Modifier
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .width(64.dp)
                .padding(vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            tabs.forEachIndexed { index, item ->
                val selected = tab == index
                val tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickableNoRipple { onTab(index) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (index == 0 && model.activeIds.isNotEmpty()) {
                            BadgedBox(
                                badge = { Badge(containerColor = MaterialTheme.colorScheme.primary) },
                            ) {
                                Icon(
                                    item.icon,
                                    contentDescription = item.label,
                                    modifier = Modifier.size(21.dp),
                                    tint = tint,
                                )
                            }
                        } else {
                            Icon(
                                item.icon,
                                contentDescription = item.label,
                                modifier = Modifier.size(21.dp),
                                tint = tint,
                            )
                        }
                    }
                    Text(
                        item.label,
                        fontSize = 9.sp,
                        color = tint,
                    )
                }
            }
        }
    }
}

/** 宽屏但还没有打开会话时，右栏的占位。 */
@Composable
private fun EmptyChatHint() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            "从左边选一个会话",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun ErrorBanner(model: AppModel) {
    val error = model.lastError ?: model.connectionError
    if (error != null) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { model.clearError() }) { Text("关闭") }
        }
    }
}
