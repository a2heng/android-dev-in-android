package com.a2heng.opencode.mobile.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.graphics.graphicsLayer
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AttachFile
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Psychology
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.UTurnLeft
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.Attachment
import com.a2heng.opencode.mobile.data.CommandInfo
import com.a2heng.opencode.mobile.data.FormField
import com.a2heng.opencode.mobile.data.FormInfo
import com.a2heng.opencode.mobile.data.Msg
import com.a2heng.opencode.mobile.data.Part
import com.a2heng.opencode.mobile.data.PermissionReq
import com.a2heng.opencode.mobile.data.SessionInfo
import com.a2heng.opencode.mobile.data.formatCost
import com.a2heng.opencode.mobile.data.formatTokens
import com.a2heng.opencode.mobile.data.rateLabel
import com.a2heng.opencode.mobile.data.str
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(model: AppModel, session: SessionInfo, wide: Boolean = false) {
    var input by rememberSaveable { mutableStateOf("") }
    var pickModel by remember { mutableStateOf(false) }
    var pickAgent by remember { mutableStateOf(false) }
    var revertTarget by remember { mutableStateOf<Msg?>(null) }
    var detailToolID by remember { mutableStateOf<String?>(null) }
    // 思考详情：live 表示点的是底部流式区，内容要继续跟着 model.liveReasoning 更新
    var reasoningView by remember { mutableStateOf<ReasoningView?>(null) }
    var fileTarget by remember { mutableStateOf<String?>(null) }
    // 文本里的路径 → 文件预览卡。支持 file://、~/、绝对路径，以及相对当前会话目录的路径。
    val openPath: (String) -> Unit = { raw ->
        val abs = resolveFileRef(raw, session.directory, model.homeDir)
        fileTarget = abs
        model.openFile(abs)
    }

    var showCommands by remember { mutableStateOf(false) }
    var showFilePicker by remember { mutableStateOf(false) }
    var openMenu by remember { mutableStateOf(false) }
    var confirmStop by remember { mutableStateOf(false) }
    var backProgress by remember { mutableFloatStateOf(0f) }
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterProgress by animateFloatAsState(
        targetValue = if (entered) 0f else 1f,
        animationSpec = tween(durationMillis = 260, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "chat-enter",
    )
    val motion = maxOf(backProgress, enterProgress)
    val deviceCorner = rememberDeviceCornerRadius()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val attachments = remember { mutableStateListOf<Attachment>() }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val att = withContext(Dispatchers.IO) { loadImageAttachment(context, uri) }
                if (att != null) attachments.add(att)
            }
        }
    }

    // 其它 App 分享进来的文本 / 图片
    LaunchedEffect(model.pendingDraft, model.pendingAttachment) {
        if (model.pendingDraft != null || model.pendingAttachment != null) {
            val (text, image) = model.consumeDraft()
            if (!text.isNullOrBlank()) input = text
            if (image != null) attachments.add(image)
        }
    }

    LaunchedEffect(session.id) { model.loadMeta(session.directory) }

    LaunchedEffect(session.id) {
        while (true) {
            model.refreshShells()
            kotlinx.coroutines.delay(3000)
        }
    }

    // 发出 pending / 新消息后回到最底部（reverseLayout 的 0 就是底部）
    // 只在队列变多时滚到底，队列被取走/撤回时不要来回跳
    var lastQueueSize by remember(session.id) { mutableStateOf(0) }
    LaunchedEffect(model.queue.size) {
        if (model.queue.size > lastQueueSize && model.queue.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
        lastQueueSize = model.queue.size
    }

    // 滚动到最上面（reverseLayout 的末尾）时，继续往更早翻一页
    LaunchedEffect(listState) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: 0) to info.totalItemsCount
        }.collect { (last, total) ->
            if (total > 0 && last >= total - 4) model.loadOlderMessages()
        }
    }

    val pendingPermissions = model.permissions.filter { it.sessionID == session.id }
    val pendingForms = model.forms.filter { it.sessionID == session.id }
    // 通过 API 新建的会话在服务端没有 model 字段，从最后一条 assistant 消息里补
    val effectiveModel = session.model ?: model.messages.lastOrNull { it.model != null }?.model
    val lastTokens = model.messages.lastOrNull { it.isAssistant && it.tokens != null }?.tokens
    // 命中率用整个会话的累计口径（服务端 session.tokens 就是全会话汇总），
    // 上下文占用仍然只看最近一步（context 是单次请求的窗口占用）。
    val aggregateTokens = session.tokens
        ?: model.messages.mapNotNull { it.tokens }.reduceOrNull { a, b -> a.plus(b) }
    val contextLimit = effectiveModel?.let { model.contextLimits[it.id] } ?: 0L
    val contextUsed = (lastTokens?.input ?: 0L) + (lastTokens?.cacheRead ?: 0L) + (lastTokens?.cacheWrite ?: 0L)
    val contextPct = if (contextLimit > 0 && contextUsed > 0) {
        (contextUsed * 100.0 / contextLimit).coerceIn(0.0, 100.0)
    } else {
        null
    }
    val contextColor = contextPct?.let {
        contextUsageColor(it, MaterialTheme.colorScheme.background.luminanceIsDark())
    } ?: MaterialTheme.colorScheme.onSurfaceVariant
    val agentDisplay = session.agent.ifBlank {
        model.agents.firstOrNull { it.str("mode") == "primary" }?.str("name")
            ?: model.agents.firstOrNull()?.str("name")
            ?: "build"
    }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    val backOwner = androidx.activity.compose.LocalOnBackPressedDispatcherOwner.current
    if (backOwner != null) {
        PredictiveBackHandler { events ->
            try {
                events.collect { event -> backProgress = event.progress }
                model.closeSession()
                model.refreshSessions()
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                backProgress = 0f
                throw cancel
            }
        }
    } else {
        BackHandler {
            model.closeSession()
            model.refreshSessions()
        }
    }

    Scaffold(
        modifier = Modifier
            .clip(RoundedCornerShape(deviceCorner * motion))
            .graphicsLayer {
                // 子页返回：不移动，只淡出；干净利落
                alpha = 1f - motion
            },
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 3.dp,
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .then(
                            if (wide) Modifier
                            else Modifier.windowInsetsPadding(WindowInsets.statusBars),
                        )
                        .height(50.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalIconButton(
                        onClick = {
                            model.closeSession()
                            model.refreshSessions()
                        },
                        modifier = Modifier.size(38.dp),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Icon(
                            Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                            modifier = Modifier.size(19.dp),
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    // 顶栏：名字一行，下面一行是没放进底栏的信息（上下文 / tokens / 显示 / Git / 会话）。
                    Column(Modifier.weight(1f)) {
                        Text(
                            session.shortTitle,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MetaText(
                                "ctx " + (contextPct?.let { "${it.roundToInt()}%" } ?: "—"),
                                color = contextColor,
                            )
                            MetaDot()
                            MetaText(
                                aggregateTokens?.let {
                                    "↑${formatTokens(it.input)} ↓${formatTokens(it.output)}"
                                } ?: "↑— ↓—",
                            )
                            MetaDot()
                            MetaText(
                                detailLevelLabel(model.detailLevel),
                                onClick = {
                                    model.updateDetailLevel(nextDetailLevel(model.detailLevel))
                                },
                            )
                            MetaDot()
                            MetaText("Git", onClick = { model.openGit(session.directory) })
                            MetaDot()
                            MetaText(
                                "会话 ${model.openSessions.size}",
                                onClick = { openMenu = true },
                            )
                        }
                    }
                }
            }
        },
        bottomBar = {
            ChatComposer(
                model = model,
                busy = model.sending,
                value = input,
                onChange = { input = it },
                attachments = attachments,
                onPickImage = { imagePicker.launch("image/*") },
                onPickFile = { showFilePicker = true },
                onPickCommand = { showCommands = true; model.loadMeta(session.directory) },
                onRemoveAttachment = { attachments.removeAt(it) },
                onStop = { confirmStop = true },
                onSend = {
                    val text = input.trim()
                    if (text.isNotEmpty() || attachments.isNotEmpty()) {
                        input = ""
                        // 用「服务端命令 + 客户端内建命令」合并后的列表判断：
                        // compact 这类命令不在 /api/command 里，只看 model.commands 会被当成普通文本发出去。
                        // 命令必须带斜杠：裸写的 compact / interrupt 等一律当普通文本发出去，避免误触发。
                        val cmd = allCommands(model).firstOrNull {
                            text == "/" + it.name || text.startsWith("/" + it.name + " ")
                        }
                        if (cmd != null) {
                            when (cmd.name) {
                                "compact" -> model.compactSession()
                                "interrupt" -> model.interrupt()
                                "model" -> pickModel = true
                                "agent" -> pickAgent = true
                                "reload" -> model.reloadConfig()
                                else -> model.runCommand(
                                    cmd.name,
                                    text.removePrefix("/" + cmd.name).trim(),
                                    attachments.toList(),
                                )
                            }
                        } else {
                            model.sendPrompt(text, attachments.toList())
                        }
                        attachments.clear()
                        scope.launch { listState.animateScrollToItem(0) }
                    }
                },
            ) {
                ChatStatusBar(
                    model = model,
                    modelLabel = effectiveModel?.let { modelDisplay(it, model.modelGroupById) }
                        ?.takeIf { it.isNotBlank() } ?: "服务端默认",
                    agentLabel = agentDisplay,
                    hitLabel = aggregateTokens?.let { rateLabel(it.hitRate) } ?: "—",
                    hitColor = aggregateTokens?.let {
                        rateColor(it.hitRate, MaterialTheme.colorScheme.background.luminanceIsDark())
                    } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    costLabel = session.cost.takeIf { it > 0 }?.let { formatCost(it) },
                    onPickModel = {
                        model.loadMeta(session.directory)
                        pickModel = true
                    },
                    onPickAgent = {
                        model.loadMeta(session.directory)
                        pickAgent = true
                    },
                    onCache = { model.openCache(session) },
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            ErrorBanner(model)

            if (pendingPermissions.isNotEmpty()) {
                // 权限请求用弹窗，避免被长对话挤到屏幕外
                val req = pendingPermissions.first()
                AppDialog(
                    onDismissRequest = { },
                    title = { Text("需要你的确认") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(req.action, fontWeight = FontWeight.SemiBold)
                            if (req.resources.isNotEmpty()) {
                                Text(
                                    req.resources.joinToString("\n").take(1200),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (req.message.isNotBlank()) {
                                Text(req.message, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { model.replyPermission(req, "once") }) { Text("允许一次") }
                    },
                    dismissButton = {
                        Row {
                            TextButton(onClick = { model.replyPermission(req, "reject") }) {
                                Text("拒绝", color = MaterialTheme.colorScheme.error)
                            }
                            TextButton(onClick = { model.replyPermission(req, "always") }) { Text("总是允许") }
                        }
                    },
                )
            }

            if (model.messagesLoading && model.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            } else {
                // 瀑布流和 Pending 分开：Pending 固定在输入框上方，不参与消息列表滚动
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxWidth().weight(1f)) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            reverseLayout = true,
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                if (model.detailLevel == "compact") 8.dp else 12.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(
                                if (model.detailLevel == "compact") 4.dp else 10.dp,
                            ),
                        ) {
                            if (model.liveReasoning.isNotBlank() || model.liveText.isNotBlank() || model.sending) {
                                item(key = "live") {
                                    LiveBubble(
                                        model,
                                        model.detailLevel,
                                        onOpenReasoning = { reasoningView = ReasoningView(it, live = true) },
                                        onOpenPath = openPath,
                                    )
                                }
                            }
                            items(model.messages.asReversed(), key = { it.id }) { msg ->
                                // 卡片出现/消失统一用 CardMotion（淡入淡出，不位移）
                                Box(
                                    Modifier
                                        .animateItem(
                                            fadeInSpec = CardMotion.FadeIn,
                                            placementSpec = null,
                                            fadeOutSpec = CardMotion.FadeOut,
                                        )
                                        .clipToBounds(),
                                ) {
                                    MessageItem(
                                        msg = msg,
                                        detail = model.detailLevel,
                                        baseDir = session.directory,
                                        onBackground = { model.backgroundCurrent() },
                                        onOpenReasoning = { reasoningView = ReasoningView(it, live = false) },
                                        onLongPressUser = { revertTarget = it },
                                        onOpenTool = { detailToolID = it.toolID },
                                        onOpenPath = openPath,
                                    )
                                }
                            }
                            if (model.loadingOlderMessages) {
                                item(key = "older-loading") {
                                    Box(
                                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                        )
                                    }
                                }
                            }
                        }
                        if (listState.firstVisibleItemIndex > 2) {
                            SmallScrollToBottom(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(bottom = 12.dp),
                            ) {
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        }
                        if (model.shells.any { it.sessionID == session.id }) {
                            // 悬浮球放到整屏层（Scaffold 外面），展开的卡片才能和
                            // 工具/思考/文件详情一样居中、背景渐暗。
                        }
                    }
                    if (model.queue.isNotEmpty()) {
                        PendingRows(model) { restored -> input = restored }
                    }
                }
            }
        }

        // 打开的会话：卷帘卡片，占满可视宽度，向下展开；点外面或返回键收起，背景渐暗
        if (openMenu) {
            BackHandler { openMenu = false }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickableNoRipple { openMenu = false },
            )
            androidx.compose.animation.AnimatedVisibility(
                visible = openMenu,
                modifier = Modifier.align(Alignment.TopStart),
                enter = androidx.compose.animation.expandVertically(
                    animationSpec = tween(220),
                    expandFrom = Alignment.Top,
                ) + androidx.compose.animation.fadeIn(tween(140)),
                exit = androidx.compose.animation.shrinkVertically(
                    animationSpec = tween(180),
                    shrinkTowards = Alignment.Top,
                ) + androidx.compose.animation.fadeOut(tween(120)),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 4.dp,
                    shape = RoundedCornerShape(0.dp, 0.dp, 18.dp, 18.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp),
                ) {
                    Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "打开的会话",
                                style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                "${model.openSessions.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            IconButton(
                                onClick = {
                                    openMenu = false
                                    model.closeAllOpenSessions()
                                },
                                modifier = Modifier.size(30.dp),
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "退出多会话",
                                    modifier = Modifier.size(17.dp),
                                )
                            }
                        }
                        HorizontalDivider(Modifier.padding(bottom = 4.dp))
                        if (model.openSessions.isEmpty()) {
                            Text(
                                "还没有打开的会话",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 300.dp)) {
                            items(model.openSessions, key = { it.id }) { item ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickableNoRipple {
                                            openMenu = false
                                            if (item.id != session.id) model.openSession(item)
                                        }
                                        .padding(start = 8.dp, top = 7.dp, end = 2.dp, bottom = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        Modifier
                                            .size(7.dp)
                                            .background(
                                                if (item.id == session.id) MaterialTheme.colorScheme.primary
                                                else if (model.isActive(item.id)) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
                                                else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                CircleShape,
                                            ),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            item.shortTitle,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (item.id == session.id) FontWeight.SemiBold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            item.directory.substringAfterLast('/').ifBlank { item.directory },
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    if (item.id == session.id) {
                                        Text(
                                            "当前",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { model.closeOpenSession(item.id) },
                                        modifier = Modifier.size(30.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.Close,
                                            contentDescription = "关闭这个会话",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }
    }

    // 后台 shell 悬浮球 + 展开卡片：放在 Scaffold 外面，和详情卡共用 DetailOverlay。
    if (model.shells.any { it.sessionID == session.id }) {
        FloatingShells(model = model, sessionID = session.id)
    }

    PickerPageHost(visible = pickModel, onBack = { pickModel = false }) {
        ModelPickerPage(
            model = model,
            title = "选择模型",
            selected = effectiveModel,
            onPick = { ref -> if (ref != null) model.setModel(ref) },
            onDismiss = { pickModel = false },
        )
    }

    PickerPageHost(visible = pickAgent, onBack = { pickAgent = false }) {
        PickerPage(title = "选择 Agent", onBack = { pickAgent = false }) {
            val agents = model.agents.mapNotNull { a ->
                val id = a.str("id") ?: return@mapNotNull null
                (a.str("name") ?: id) to (a.str("description") ?: id) to id
            }
            if (agents.isEmpty()) {
                EmptyState("没有可用的 Agent")
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(agents, key = { it.second }) { (labels, id) ->
                        val (name, desc) = labels
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple {
                                    model.setAgent(id)
                                    pickAgent = false
                                }
                                .padding(vertical = 11.dp),
                        ) {
                            Column {
                                Text(name, style = MaterialTheme.typography.bodyLarge)
                                if (desc.isNotBlank() && desc != id) {
                                    Text(
                                        desc,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingForms.firstOrNull()?.let { form ->
        FormDialog(
            form = form,
            onDismiss = { model.dismissForm(form.id) },
            onSubmit = { answer -> model.replyForm(form, answer) },
        )
    }

    revertTarget?.let { msg ->
        AppDialog(
            onDismissRequest = { revertTarget = null },
            title = { Text("回退到这条消息？") },
            text = {
                Text(
                    "会用 opencode 的 revert 撤销这条消息及其之后的内容。" +
                        "「回退并还原文件」会把这一段的文件改动一起还原。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    model.revertTo(msg.id, false)
                    revertTarget = null
                }) { Text("回退对话") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            copyToClipboard(context, msg.text)
                            revertTarget = null
                        },
                        enabled = msg.text.isNotBlank(),
                    ) { Text("复制") }
                    TextButton(onClick = { revertTarget = null }) { Text("取消") }
                    TextButton(onClick = {
                        model.revertTo(msg.id, true)
                        revertTarget = null
                    }) {
                        Text("回退并还原文件", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
        )
    }

    if (confirmStop) {
        AppDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text("停止当前任务？") },
            text = {
                Text(
                    "会中断这个会话正在进行的回答（等同于把运行中的任务停下来）。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmStop = false
                    model.interrupt()
                }) { Text("停止", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) { Text("继续") }
            },
        )
    }

    fileTarget?.let { path ->
        DetailOverlay(onDismiss = { fileTarget = null; model.closeFile() }) { close ->
            FilePreviewCard(model, path, close)
        }
    }

    // 详情卡每次重组都从最新消息里取，运行中的工具/思考在弹窗里也能继续更新
    val detailTool = detailToolID?.let { id ->
        model.messages.asSequence()
            .flatMap { it.parts.asSequence() }
            .filterIsInstance<Part.Tool>()
            .firstOrNull { it.toolID == id }
    }
    // 流式思考：把最新内容持续写进状态，否则流式结束后会退回“点开那一刻”的旧内容
    LaunchedEffect(model.liveReasoning, reasoningView?.live) {
        val view = reasoningView ?: return@LaunchedEffect
        if (view.live && model.liveReasoning.isNotBlank()) {
            reasoningView = view.copy(text = model.liveReasoning)
        }
    }
    val reasoningShown = reasoningView

    if (wide) {
        // 宽屏：详情做成右侧面板（聊天仍然可见），点另一张卡直接换内容
        detailTool?.let { tool ->
            SideDetailPane(onDismiss = { detailToolID = null }) { close ->
                ToolDetailCard(tool, session.directory, close, openPath)
            }
        }
        reasoningShown?.let { view ->
            SideDetailPane(onDismiss = { reasoningView = null }) { close ->
                ReasoningDetailCard(view.text, view.live, close)
            }
        }
    } else {
        detailTool?.let { tool ->
            DetailOverlay(onDismiss = { detailToolID = null }) { close ->
                ToolDetailCard(tool, session.directory, close, openPath)
            }
        }
        reasoningShown?.let { view ->
            DetailOverlay(onDismiss = { reasoningView = null }) { close ->
                ReasoningDetailCard(view.text, view.live, close)
            }
        }
    }

    PickerPageHost(visible = showFilePicker, onBack = { showFilePicker = false }) {
        FileMentionPage(
            model = model,
            startDir = session.directory.ifBlank { model.homeDir },
            onDismiss = { showFilePicker = false },
        ) { absolute ->
            val base = session.directory.ifBlank { model.homeDir }
            val rel = if (base.isNotBlank() && absolute.startsWith("$base/")) {
                absolute.removePrefix("$base/")
            } else {
                absolute
            }
            input = (input.trimEnd() + " @" + rel + " ").trimStart()
        }
    }

    PickerPageHost(visible = showCommands, onBack = { showCommands = false }) {
        PickerPage(title = "命令", onBack = { showCommands = false }) {
            val commandList = allCommands(model)
            if (commandList.isEmpty()) {
                EmptyState("没有可用命令")
            } else {
                LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    items(commandList, key = { it.name }) { cmd ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickableNoRipple {
                                    showCommands = false
                                    input = "/" + cmd.name + " " + input.trim()
                                }
                                .padding(vertical = 10.dp),
                        ) {
                            Column {
                                Text("/${cmd.name}", style = MaterialTheme.typography.bodyLarge)
                                if (cmd.description.isNotBlank()) {
                                    Text(
                                        cmd.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

}

@Composable
private fun ChatComposer(
    model: AppModel,
    busy: Boolean,
    value: String,
    onChange: (String) -> Unit,
    attachments: List<Attachment>,
    onPickImage: () -> Unit,
    onPickFile: () -> Unit,
    onPickCommand: () -> Unit,
    onRemoveAttachment: (Int) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    var actionsOpen by rememberSaveable { mutableStateOf(false) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    val rotation by animateFloatAsState(if (actionsOpen) 45f else 0f, label = "plus-rotate")
    val slashQuery = if (value.startsWith("/") && !value.contains(' ')) value.substring(1) else null
    val suggestions = if (slashQuery == null) {
        emptyList()
    } else {
        allCommands(model)
            .filter { it.name.contains(slashQuery, ignoreCase = true) }
            .take(6)
    }
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime)),
        ) {
            if (suggestions.isNotEmpty()) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Column {
                        suggestions.forEach { cmd ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickableNoRipple { onChange("/" + cmd.name + " ") }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "/" + cmd.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    cmd.description,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
            if (attachments.isNotEmpty()) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    attachments.forEachIndexed { index, att ->
                        Box {
                            val bmp = remember(att.base64) {
                                decodeDataUri("data:${att.mime};base64,${att.base64}", 240)
                            }
                            if (bmp != null) {
                                Image(
                                    bitmap = bmp.asImageBitmap(),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                )
                            }
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(18.dp)
                                    .background(MaterialTheme.colorScheme.error, CircleShape)
                                    .clickableNoRipple { onRemoveAttachment(index) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "移除",
                                    tint = Color.White,
                                    modifier = Modifier.size(12.dp),
                                )
                            }
                        }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .clickableNoRipple { actionsOpen = !actionsOpen },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = if (actionsOpen) "收起" else "添加",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp).rotate(rotation),
                    )
                }
                AnimatedVisibility(
                    visible = actionsOpen,
                    enter = androidx.compose.animation.fadeIn(
                        animationSpec = tween(durationMillis = 110),
                    ) + androidx.compose.animation.scaleIn(
                        initialScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = 140,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    ),
                    exit = androidx.compose.animation.fadeOut(
                        animationSpec = tween(durationMillis = 90),
                    ) + androidx.compose.animation.scaleOut(
                        targetScale = 0.92f,
                        animationSpec = tween(
                            durationMillis = 100,
                            easing = androidx.compose.animation.core.FastOutSlowInEasing,
                        ),
                    ),
                ) {
                    Row(
                        Modifier.padding(start = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ComposerAction(Icons.Rounded.AddPhotoAlternate, "图片", 26, MaterialTheme.colorScheme.primary, onPickImage)
                        ComposerAction(Icons.Rounded.NoteAdd, "文件", 24, MaterialTheme.colorScheme.primary, onPickFile)
                        ComposerAction(Icons.Rounded.Terminal, "命令", 23, MaterialTheme.colorScheme.primary, onPickCommand)
                    }
                }
                Spacer(Modifier.width(6.dp))
                val hint = when {
                    !busy -> "发消息给 opencode…"
                    model.sendMode == "steer" -> "运行中 · 引导当前回答"
                    model.sendMode == "interrupt" -> "运行中 · 中断后发送"
                    else -> "运行中 · 结束后再发"
                }
                // 自绘输入框：比 OutlinedTextField 的默认内边距紧凑，清空按钮能贴右上角
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    ),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(Modifier.padding(start = 10.dp, end = 2.dp, top = 4.dp, bottom = 6.dp)) {
                        BasicTextField(
                            value = value,
                            onValueChange = onChange,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            maxLines = 5,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Default),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(end = if (value.isEmpty()) 0.dp else 24.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (value.isEmpty() && !actionsOpen) {
                                        Text(
                                            hint,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                        // 清空按钮贴在输入框右上角；点了先弹确认，避免误删刚打的字
                        if (value.isNotEmpty()) {
                            Box(
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .clickableNoRipple { confirmClear = true },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "清空输入",
                                    modifier = Modifier.size(14.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(6.dp))
                val hasContent = value.isNotBlank() || attachments.isNotEmpty()
                val stopMode = !hasContent && busy
                FloatingActionButton(
                    onClick = {
                        when {
                            hasContent -> onSend()
                            stopMode -> onStop()
                        }
                    },
                    modifier = Modifier
                        .size(42.dp)
                        .alpha(if (hasContent || stopMode) 1f else 0.45f),
                    containerColor = when {
                        hasContent -> MaterialTheme.colorScheme.primary
                        stopMode -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    contentColor = when {
                        hasContent -> MaterialTheme.colorScheme.onPrimary
                        stopMode -> MaterialTheme.colorScheme.onError
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    shape = CircleShape,
                ) {
                    Icon(
                        if (stopMode) Icons.Rounded.Stop else Icons.Rounded.ArrowUpward,
                        contentDescription = if (stopMode) "停止" else "发送",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            footer()
        }
    }

    if (confirmClear) {
        AppDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空输入？") },
            text = {
                Text(
                    "只会清空输入框里还没发出去的文字，已附加的图片/文件不受影响。",
                    style = MaterialTheme.typography.bodySmall,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    onChange("")
                }) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun DialogBackButton(onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = Modifier.size(32.dp),
        colors = IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Icon(
            Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun InfoChip(
    label: String,
    value: String,
    valueColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .height(34.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), shape)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 输入区下方的一排状态底栏：模型 / 模式 / 命中 / 花费。
 * 不横向滚动；其余信息（上下文、tokens、显示细节、Git、打开的会话）在顶栏「⋮」里。
 */
@Composable
private fun ChatStatusBar(
    model: AppModel,
    modelLabel: String,
    agentLabel: String,
    hitLabel: String,
    hitColor: Color,
    costLabel: String?,
    onPickModel: () -> Unit,
    onPickAgent: () -> Unit,
    onCache: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, end = 6.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusChip(
            "模型",
            modelLabel,
            onClick = onPickModel,
            modifier = Modifier.weight(1f),
        )
        StatusChip("模式", agentLabel, onClick = onPickAgent)
        StatusChip("命中", hitLabel, valueColor = hitColor, onClick = onCache)
        StatusChip("花费", costLabel ?: "—")
    }
}

@Composable
private fun StatusChip(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(8.dp)
    Row(
        modifier
            .height(26.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), shape)
            .then(if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier)
            .padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            label,
            fontSize = 9.sp,
            lineHeight = 10.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Text(
            value,
            fontSize = 10.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MetaText(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Text(
        text,
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = if (onClick != null) Modifier.clickableNoRipple(onClick) else Modifier,
    )
}

@Composable
private fun MetaDot() {
    Text(
        "·",
        fontSize = 9.5.sp,
        lineHeight = 11.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
        modifier = Modifier.padding(horizontal = 3.dp),
    )
}

private fun detailLevelLabel(level: String): String = when (level) {
    "compact" -> "精简"
    "full" -> "适中"
    else -> "完整"
}

private fun nextDetailLevel(level: String): String = when (level) {
    "compact" -> "full"
    "full" -> "expanded"
    else -> "compact"
}

@Composable
private fun ComposerAction(
    icon: ImageVector,
    label: String,
    size: Int,
    tint: Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(size.dp))
    }
}

/** 思考详情打开时记住来源：live 表示底部流式区，内容要继续跟着 model.liveReasoning 长。 */
private data class ReasoningView(val text: String, val live: Boolean)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageItem(
    msg: Msg,
    detail: String,
    baseDir: String = "",
    onBackground: () -> Unit = {},
    onOpenReasoning: (String) -> Unit = {},
    onLongPressUser: (Msg) -> Unit = {},
    onOpenTool: (Part.Tool) -> Unit = {},
    onOpenPath: ((String) -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    if (msg.isUser) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp),
                modifier = Modifier
                    .widthIn(max = 330.dp)
                    .combinedClickable(
                        onClick = {},
                        onLongClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onLongPressUser(msg)
                        },
                    ),
            ) {
                Column(Modifier.padding(horizontal = if (detail == "compact") 10.dp else 13.dp, vertical = if (detail == "compact") 6.dp else 9.dp)) {
                    msg.images.forEach { DataUriImage(it) }
                    Text(
                        msg.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
        return
    }

    Column(Modifier.fillMaxWidth()) {
        msg.parts.forEach { part ->
            when (part) {
                is Part.Reasoning -> ReasoningBlock(part.text, detail, onOpenReasoning = onOpenReasoning)
                is Part.Tool -> ToolBlock(part, detail, onOpenTool, msg.tokens?.hitRate, baseDir, onBackground, onOpenPath)
                is Part.Text -> AssistantText(part.text, onOpenPath)
            }
        }
        if (msg.parts.isEmpty() && msg.text.isNotBlank()) AssistantText(msg.text, onOpenPath)
        msg.error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        if (detail != "compact") Footer(msg)
    }
}

@Composable
private fun AssistantText(text: String, onOpenPath: ((String) -> Unit)? = null) {
    if (text.isBlank()) return
    MarkdownText(
        text,
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        onOpenPath = onOpenPath,
    )
}

/** 工具卡片正文最多预览的行数：超过就显示「… 展开」，所有工具共用这一个阈值。 */
private const val TOOL_PREVIEW_LINES = 20

@Composable
private fun LeftAccent(
    accent: Color,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent, RoundedCornerShape(2.dp)),
        )
        Box(Modifier.weight(1f).padding(start = 6.dp)) { content() }
    }
}

/**
 * 多行文本的「省略 + 展开」：到固定行数后加省略号，末尾给一个可点的高亮「展开」，
 * 完整内容交给工具详情/思考详情弹窗去看，不在正文里塞滚动条。
 */
@Composable
private fun ClampedText(
    text: String,
    maxLines: Int,
    onExpand: () -> Unit,
    maskColor: Color,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    fontSize: androidx.compose.ui.unit.TextUnit = 10.5.sp,
) {
    // 注意：不要用 text 作为 remember 的 key。流式文本每来一段都会把状态重置成 false，
    // 按钮就会「出现→消失→出现」地闪；只从 false 变 true，绝不回退。
    var overflowed by remember { mutableStateOf(false) }
    Box(modifier) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            color = color,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            onTextLayout = {
                if (it.hasVisualOverflow) overflowed = true
            },
        )
        if (overflowed) {
            // 嵌在右下角的小按钮：按钮自己的右下圆角就是卡片圆角，左上做成切角
            Surface(
                color = maskColor,
                shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 6.dp),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .clickableNoRipple(onExpand),
            ) {
                Text(
                    "展开",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ReasoningBlock(
    text: String,
    detail: String,
    live: Boolean = false,
    onOpenReasoning: (String) -> Unit = {},
) {
    if (text.isBlank()) return
    if (detail == "compact") {
        // 精简模式也给一行思考，点开看完整内容
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { onOpenReasoning(text) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Rounded.Psychology,
                contentDescription = null,
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(5.dp))
            Text(
                "思考",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "${text.length} 字",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
            )
            Spacer(Modifier.weight(1f))
            Text(
                "展开",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    } else {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.60f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickableNoRipple { onOpenReasoning(text) },
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Rounded.Psychology,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "思考",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    if (live) "进行中" else "完成",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (live) Sky else Accent,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${text.length} 字",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // 卡片本身就是唯一容器：不再套一层深色背景 + padding
            LeftAccent(
                accent = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (detail == "expanded") {
                    MarkdownText(
                        text,
                        Modifier.fillMaxWidth(),
                        compact = true,
                    )
                } else {
                    ClampedText(
                        text,
                        maxLines = 16,
                        onExpand = { onOpenReasoning(text) },
                        maskColor = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun ReasoningDetailCard(text: String, live: Boolean, onClose: () -> Unit) {
    val (contentMin, contentMax) = rememberDialogContentHeight()
    val scroll = rememberScrollState()
    // 流式思考：内容还在长，跟着滚到底部（用户往上翻过就不要再抢）
    LaunchedEffect(text.length, live) {
        if (live && scroll.maxValue - scroll.value < 120) scroll.scrollTo(scroll.maxValue)
    }
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .widthIn(max = 520.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogBackButton(onClick = onClose)
                Spacer(Modifier.width(4.dp))
                Text(
                    "思考过程 · ${text.length} 字",
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(
                Modifier
                    .padding(top = 10.dp)
                    .heightIn(min = contentMin, max = contentMax)
                    .verticalScroll(scroll)
                    .padding(vertical = 5.dp),
            ) {
                MarkdownText(text, Modifier.fillMaxWidth(), compact = true)
            }
        }
    }
}

@Composable
private fun ShellBlock(text: String, command: Boolean, maxLines: Int) {
    val accent = if (command) Sky else MaterialTheme.colorScheme.outline
    val bg = if (command) Sky.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(bg, RoundedCornerShape(6.dp)),
    ) {
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
            fontSize = 10.5.sp,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun ToolBlock(
    part: Part.Tool,
    detail: String,
    onOpen: (Part.Tool) -> Unit,
    rate: Double? = null,
    baseDir: String = "",
    onBackground: () -> Unit = {},
    onOpenFile: ((String) -> Unit)? = null,
) {
    val hasDiff = part.diff.isNotBlank()
    val cleanPatch = if (hasDiff) cleanDiff(part.diff) else ""
    val hasOutput = part.output.isNotBlank()
    val hasImages = part.images.isNotEmpty()
    val hasError = part.error.isNotBlank()
    val statusColor = when (part.status) {
        "completed", "success" -> Accent
        "error", "failed" -> MaterialTheme.colorScheme.error
        "running" -> Sky
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val runningShell = part.name.lowercase() in setOf("shell", "bash", "sh") && part.status == "running"
    var backgrounded by remember(part.toolID) { mutableStateOf(false) }

    if (detail == "compact") {
        val summary = part.input.lineSequence().firstOrNull()?.take(140).orEmpty()
        Row(
            Modifier
                .fillMaxWidth()
                .clickableNoRipple { onOpen(part) }
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(toolIcon(part.name), contentDescription = null, modifier = Modifier.size(12.dp), tint = statusColor)
            Spacer(Modifier.width(5.dp))
            Text(
                part.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (summary.isNotBlank()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    summary,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            } else {
                Spacer(Modifier.weight(1f))
            }
            val extra = when {
                hasImages -> "图片"
                hasDiff -> "diff"
                part.payload.isNotBlank() -> "内容"
                hasOutput -> "输出"
                hasError -> "错误"
                else -> ""
            }
            if (runningShell && !backgrounded) {
                Spacer(Modifier.width(6.dp))
                Text(
                    "后台",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clickableNoRipple {
                            backgrounded = true
                            onBackground()
                        }
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
            if (rate != null) {
                Spacer(Modifier.width(6.dp))
                val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
                Text(
                    rateLabel(rate),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = rateColor(rate, dark),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(48.dp),
                )
            } else if (extra.isNotEmpty()) {
                Spacer(Modifier.width(6.dp))
                Text(
                    extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor.copy(alpha = 0.9f),
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.width(48.dp),
                )
            }
        }
        return
    }

    val isShellTool = part.name.lowercase() in setOf("shell", "bash", "sh")
    val isEditTool = part.name in setOf("edit", "patch", "multiedit")
    val isWriteTool = part.name == "write"
    val isReadTool = part.name.lowercase() == "read"
    val isExpanded = detail == "expanded"
    val displayName = toolDisplayName(part.name)
    val inputPath = if (isReadTool || isEditTool || isWriteTool) firstLineText(part.input) else ""
    val relPath = shortToolPath(inputPath, baseDir)
    val readRange = if (isReadTool) {
        Regex("""lines? (\d+)[-–](\d+)""").find(part.output)?.let { "L${it.groupValues[1]}–${it.groupValues[2]}" }
    } else {
        null
    }
    val writeLineCount = if (isWriteTool) part.payload.lineSequence().count() else 0
    val addLines = if (isEditTool) cleanPatch.lineSequence().count { it.startsWith("+") } else 0
    val delLines = if (isEditTool) cleanPatch.lineSequence().count { it.startsWith("-") } else 0
    val subtitle = when {
        isReadTool -> listOfNotNull(relPath.ifBlank { null }, readRange).joinToString(" · ")
        isWriteTool -> listOfNotNull(relPath.ifBlank { null }, "${writeLineCount} 行").joinToString(" · ")
        isEditTool -> listOfNotNull(
            relPath.ifBlank { null },
            if (addLines + delLines > 0) "+$addLines −$delLines" else null,
        ).joinToString(" · ")
        else -> ""
    }
    val readBody = if (isReadTool) part.output.substringAfter('\n', part.output) else ""
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .clickableNoRipple { onOpen(part) },
    ) {
        Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(toolIcon(part.name), contentDescription = null, modifier = Modifier.size(15.dp), tint = statusColor)
                Spacer(Modifier.width(6.dp))
                Text(displayName, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
                Spacer(Modifier.width(6.dp))
                Text(
                    toolStatusLabel(part),
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                )
                Spacer(Modifier.weight(1f))
                if (runningShell && !backgrounded) {
                    Text(
                        "后台",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickableNoRipple {
                                backgrounded = true
                                onBackground()
                            }
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                }
                if (part.truncated) {
                    Text("已截断", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (subtitle.isNotBlank()) {
                val canOpenFile = (isReadTool || isEditTool || isWriteTool) &&
                    inputPath.isNotBlank() && onOpenFile != null
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    color = if (canOpenFile) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .then(
                            if (canOpenFile) Modifier.clickableNoRipple { onOpenFile!!(inputPath) }
                            else Modifier,
                        ),
                )
            }

            if (isShellTool) {
                if (part.input.isNotBlank()) {
                    ShellBlock(part.input.take(4000), command = true, maxLines = 6)
                }
                if (hasOutput) {
                    LeftAccent(
                        accent = Sky,
                        modifier = Modifier.padding(top = 4.dp),
                    ) {
                        if (isExpanded) {
                            Text(
                                part.output.takeLast(30000),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            ClampedText(
                                part.output.takeLast(12000),
                                maxLines = TOOL_PREVIEW_LINES,
                                onExpand = { onOpen(part) },
                                maskColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                if (!isReadTool && !isEditTool && !isWriteTool && part.input.isNotBlank()) {
                    Text(
                        part.input.lineSequence().firstOrNull()?.take(220) ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                if (isReadTool && readBody.isNotBlank()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.35f),
                                RoundedCornerShape(6.dp),
                            )
                            .padding(6.dp),
                    ) {
                        if (isExpanded) {
                            Text(
                                readBody.take(30000),
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.5.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        } else {
                            ClampedText(
                                readBody.take(20000),
                                maxLines = TOOL_PREVIEW_LINES,
                                onExpand = { onOpen(part) },
                                maskColor = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
                if (hasImages) {
                    part.images.forEach { DataUriImage(it) }
                }
                if (hasDiff) {
                    if (isExpanded) {
                        DiffView(cleanPatch)
                    } else {
                        val longDiff = cleanPatch.lineSequence().count() > TOOL_PREVIEW_LINES
                        Box(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp)
                                    .clip(RoundedCornerShape(6.dp)),
                            ) {
                                DiffView(cleanPatch)
                            }
                            if (longDiff) {
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = RoundedCornerShape(topStart = 12.dp, bottomEnd = 6.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .clickableNoRipple { onOpen(part) },
                                ) {
                                    Text(
                                        "展开",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                if (isWriteTool && part.payload.isNotBlank()) {
                    if (isExpanded) {
                        Text(
                            part.payload.take(30000),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(6.dp),
                        )
                    } else {
                        ClampedText(
                            part.payload.take(12000),
                            maxLines = TOOL_PREVIEW_LINES,
                            onExpand = { onOpen(part) },
                            maskColor = MaterialTheme.colorScheme.surface,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 5.dp)
                                .background(
                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(8.dp),
                        )
                    }
                }
                if (hasOutput && !isWriteTool && !isReadTool && (!isEditTool || !hasDiff)) {
                    if (isExpanded) {
                        Text(
                            part.output.takeLast(30000),
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        )
                    } else {
                        ClampedText(
                            part.output.takeLast(12000),
                            maxLines = TOOL_PREVIEW_LINES,
                            onExpand = { onOpen(part) },
                            maskColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.fillMaxWidth().padding(top = 5.dp),
                        )
                    }
                }
            }
            if (hasError) {
                Text(
                    part.error,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 5.dp),
                )
            }
        }
    }
}

@Composable
private fun ToolDetailCard(
    part: Part.Tool,
    baseDir: String = "",
    onClose: () -> Unit,
    onOpenFile: ((String) -> Unit)? = null,
) {
    val (contentMin, contentMax) = rememberDialogContentHeight()
    val isShellTool = part.name.lowercase() in setOf("shell", "bash", "sh")
    val isWriteTool = part.name == "write"
    val isEditTool = part.name in setOf("edit", "patch", "multiedit")
    val isReadTool = part.name.lowercase() == "read"
    val displayName = toolDisplayName(part.name)
    val inputPath = if (isReadTool || isEditTool || isWriteTool) firstLineText(part.input) else ""
    val relPath = shortToolPath(inputPath, baseDir)
    val readRange = if (isReadTool) {
        Regex("""lines? (\d+)[-–](\d+)""").find(part.output)?.let { "L${it.groupValues[1]}–${it.groupValues[2]}" }
    } else {
        null
    }
    val subtitle = when {
        isReadTool -> listOfNotNull(relPath.ifBlank { null }, readRange).joinToString(" · ")
        isWriteTool -> relPath
        isEditTool -> relPath
        else -> ""
    }
    val readBody = if (isReadTool) part.output.substringAfter('\n', part.output) else ""
    val statusColor = when (part.status) {
        "completed", "success" -> Accent
        "error", "failed" -> MaterialTheme.colorScheme.error
        "running" -> Sky
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 6.dp,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .widthIn(max = 720.dp),
            ) {
                Column(Modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DialogBackButton(onClick = onClose)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            toolIcon(part.name),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = statusColor,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            displayName,
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            toolStatusLabel(part),
                            style = MaterialTheme.typography.labelSmall,
                            color = statusColor,
                        )
                    }
                    if (subtitle.isNotBlank()) {
                        val canOpenFile = (isReadTool || isEditTool || isWriteTool) &&
                            inputPath.isNotBlank() && onOpenFile != null
                        Text(
                            subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = if (canOpenFile) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .then(
                                    if (canOpenFile) Modifier.clickableNoRipple {
                                        onClose()
                                        onOpenFile!!(inputPath)
                                    } else Modifier,
                                ),
                        )
                    }
                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                    Column(
                        Modifier
                            .heightIn(min = contentMin, max = contentMax)
                            .verticalScroll(rememberScrollState())
                            .padding(vertical = 5.dp),
                    ) {
                        if (isReadTool) {
                            if (readBody.isNotBlank()) {
                                Text(
                                    readBody.take(30000),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        } else if (isWriteTool) {
                            if (part.payload.isNotBlank()) {
                                Text(
                                    part.payload.take(20000),
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            } else {
                                Text(
                                    "（没有写入内容）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        } else {
                            if (part.input.isNotBlank()) {
                                if (isShellTool) {
                                    ShellBlock(part.input, command = true, maxLines = Int.MAX_VALUE)
                                } else {
                                    Text(
                                        part.input,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (part.images.isNotEmpty()) {
                                part.images.forEach { DataUriImage(it) }
                            }
                            if (part.diff.isNotBlank()) {
                                DiffView(cleanDiff(part.diff))
                            }
                            if (part.output.isNotBlank() && (isShellTool || !isEditTool)) {
                                Spacer(Modifier.height(6.dp))
                                if (isShellTool) {
                                    ShellBlock(
                                        part.output.takeLast(30000),
                                        command = false,
                                        maxLines = Int.MAX_VALUE,
                                    )
                                } else {
                                    Text(
                                        part.output,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.5.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                        if (part.error.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                part.error,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
}

@Composable
private fun FilePreviewCard(model: AppModel, path: String, onClose: () -> Unit) {
    val (contentMin, contentMax) = rememberDialogContentHeight()
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 6.dp,
        modifier = Modifier
            .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)
            .fillMaxWidth()
            .widthIn(max = 720.dp),
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DialogBackButton(onClick = onClose)
                Spacer(Modifier.width(4.dp))
                Text(
                    path.substringAfterLast('/'),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                path.substringBeforeLast('/'),
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Box(
                Modifier
                    .heightIn(min = contentMin, max = contentMax)
                    .padding(vertical = 4.dp),
            ) {
                val mediaUri = model.openFileMediaUri
                val mediaKind = model.openFileMediaKind
                when {
                    model.openFileLoading -> CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(20.dp),
                        strokeWidth = 2.dp,
                    )
                    mediaUri != null && mediaKind != null -> Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        MediaPreview(
                            uri = mediaUri,
                            kind = mediaKind,
                            title = path.substringAfterLast('/'),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (model.openFileBytesSize > 0L) {
                            Text(
                                "${model.openFileMime.ifBlank { mediaKind }} · ${model.openFileBytesSize / 1024} KB",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    model.openFileImage != null -> Image(
                        bitmap = model.openFileImage!!.asImageBitmap(),
                        contentDescription = path.substringAfterLast('/'),
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    model.openFileBinary -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            model.openFileText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    model.openFileMarkdown -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        val baseDir = path.substringBeforeLast('/', "")
                        MarkdownText(
                            model.openFileText,
                            compact = false,
                            onOpenPath = { ref ->
                                model.openFile(resolveFileRef(ref, baseDir, model.homeDir))
                            },
                        )
                    }
                    else -> Box(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    ) {
                        val baseDir = path.substringBeforeLast('/', "")
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (model.openFileMime.isNotBlank()) {
                                Text(
                                    model.openFileMime,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Text(
                                model.openFileText.take(120_000),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun toolStatusLabel(part: Part.Tool): String = when {
    part.exitCode != null && part.exitCode != 0L -> "exit ${part.exitCode}"
    part.status == "completed" || part.status == "success" -> "完成"
    part.status == "running" -> "运行中"
    part.status == "error" || part.status == "failed" -> "失败"
    else -> part.status.ifBlank { "—" }
}

/** 复制用户消息到系统剪贴板。 */
private fun copyToClipboard(context: Context, text: String) {
    if (text.isBlank()) return
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("OpenCode message", text))
    Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
}

private fun toolDisplayName(name: String): String = when (name.lowercase()) {
    "read" -> "读取"
    "write" -> "写入"
    "edit", "patch", "multiedit" -> "编辑"
    "shell", "bash", "sh" -> "命令"
    "grep", "search" -> "搜索"
    "glob" -> "查找文件"
    "webfetch", "fetch" -> "抓取网页"
    "websearch" -> "网页搜索"
    "task", "subagent" -> "子任务"
    else -> name
}

/** 去掉 patch 自带的文件头（Index/====/---/+++），路径已经在卡片副标题里显示一次了。 */
private fun cleanDiff(diff: String): String =
    diff.lineSequence()
        .filterNot { line ->
            line.startsWith("Index: ") ||
                line.startsWith("diff --git ") ||
                line.startsWith("--- ") ||
                line.startsWith("+++ ") ||
                (line.isNotEmpty() && line.all { it == '=' })
        }
        .joinToString("\n")
        .trim()

private fun firstLineText(text: String): String =
    text.lineSequence().firstOrNull()?.trim().orEmpty()

private fun shortToolPath(path: String, baseDir: String): String {
    val p = path.trim()
    if (p.isEmpty()) return ""
    val base = baseDir.trimEnd('/')
    if (base.isNotEmpty() && p.startsWith("$base/")) return p.removePrefix("$base/")
    val parts = p.trim('/').split('/').filter { it.isNotEmpty() }
    return when {
        parts.size <= 2 -> p
        else -> "…/" + parts.takeLast(2).joinToString("/")
    }
}

@Composable
private fun Footer(msg: Msg) {
    val tokens = msg.tokens ?: return
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val color = rateColor(tokens.hitRate, dark)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 2.dp, bottom = 2.dp),
    ) {
        Box(
            Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(4.dp)),
        )
        Text(
            "命中 ${rateLabel(tokens.hitRate)}",
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            "in ${formatTokens(tokens.input)} · cache ${formatTokens(tokens.cacheRead)} · out ${formatTokens(tokens.outputAll)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (msg.cost > 0) {
            Text(
                formatCost(msg.cost),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun LiveBubble(
    model: AppModel,
    detail: String,
    onOpenReasoning: (String) -> Unit = {},
    onOpenPath: ((String) -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        if (model.liveReasoning.isNotBlank()) {
            ReasoningBlock(model.liveReasoning, detail, live = true, onOpenReasoning = onOpenReasoning)
        }
        if (model.liveText.isNotBlank()) {
            AssistantText(model.liveText, onOpenPath)
        } else if (model.sending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 1.5.dp)
                Spacer(Modifier.width(8.dp))
                Text(
                    model.statusLine.ifBlank { "正在生成…" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PendingRows(model: AppModel, onWithdraw: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 150.dp)
            .animateContentSize(tween(durationMillis = 100))
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        model.queue.forEach { item ->
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { model.withdrawQueued(item.id) { restored -> onWithdraw(restored) } },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            androidx.compose.ui.res.painterResource(com.a2heng.opencode.mobile.R.drawable.ic_return_horizontal),
                            contentDescription = "撤回编辑",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Text(
                        item.text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { model.cancelQueued(item.id) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "移除",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallScrollToBottom(modifier: Modifier = Modifier, onClick: () -> Unit) {
    FloatingActionButton(
        onClick = onClick,
        modifier = modifier.size(40.dp),
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.primary,
        shape = CircleShape,
    ) {
        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "回到底部", modifier = Modifier.size(22.dp))
    }
}

/** 选一张图 -> 最长边压到 1600px -> JPEG 85 -> inline base64（opencode 的 files[].uri）。 */
fun loadImageAttachment(context: Context, uri: Uri): Attachment? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: return@runCatching null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
    var sample = 1
    while (bounds.outWidth / sample > 1600 || bounds.outHeight / sample > 1600) sample *= 2
    val bitmap = BitmapFactory.decodeByteArray(
        bytes, 0, bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return@runCatching null
    val out = ByteArrayOutputStream()
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
    Attachment(
        name = "image_${System.currentTimeMillis()}.jpg",
        mime = "image/jpeg",
        base64 = Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP),
    )
}.getOrNull()

/** opencode 原生命令 + 本 App 的客户端命令（去重）。 */
private fun builtinClientCommands(): List<CommandInfo> = listOf(
    CommandInfo("compact", "压缩当前会话的上下文"),
    CommandInfo("interrupt", "中断当前回答"),
    CommandInfo("model", "切换模型"),
    CommandInfo("agent", "切换 Agent"),
    CommandInfo("reload", "重新加载服务端配置"),
)

private fun allCommands(model: AppModel): List<CommandInfo> =
    model.commands + builtinClientCommands().filter { extra ->
        model.commands.none { it.name == extra.name }
    }

@Composable
private fun SendModeItem(label: String, selected: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label) },
        trailingIcon = {
            if (selected) {
                Icon(
                    Icons.Rounded.Check,
                    contentDescription = "当前",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        onClick = onClick,
    )
}

// ---------------------------------------------------------------------------
// 表单（opencode 的 question / 结构化输入）
// ---------------------------------------------------------------------------

@Composable
private fun FormDialog(form: FormInfo, onDismiss: () -> Unit, onSubmit: (JsonObject) -> Unit) {
    val values = remember(form.id) { mutableStateMapOf<String, String>() }
    val bools = remember(form.id) { mutableStateMapOf<String, Boolean>() }
    val multis = remember(form.id) { mutableStateMapOf<String, Set<String>>() }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text(form.title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                form.fields.forEach { field ->
                    FieldEditor(field, values, bools, multis)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val answer = buildJsonObject {
                    form.fields.forEach { f ->
                        when (f.type) {
                            "boolean" -> put(f.key, JsonPrimitive(bools[f.key] ?: f.defaultBool ?: false))
                            "multiselect" -> put(
                                f.key,
                                buildJsonArray {
                                    (multis[f.key] ?: emptySet()).forEach { add(JsonPrimitive(it)) }
                                },
                            )
                            "integer" -> values[f.key]?.toLongOrNull()?.let { put(f.key, JsonPrimitive(it)) }
                            "number" -> values[f.key]?.toDoubleOrNull()?.let { put(f.key, JsonPrimitive(it)) }
                            else -> {
                                // 选了选项就用选项，否则用自定义输入
                                val chosen = multis[f.key]?.firstOrNull()
                                put(f.key, JsonPrimitive(chosen ?: values[f.key] ?: ""))
                            }
                        }
                    }
                }
                onSubmit(answer)
            }) { Text("提交") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun FieldEditor(
    field: FormField,
    values: MutableMap<String, String>,
    bools: MutableMap<String, Boolean>,
    multis: MutableMap<String, Set<String>>,
) {
    Column {
        Text(
            field.title + if (field.required) " *" else "",
            style = MaterialTheme.typography.labelLarge,
        )
        if (field.description.isNotBlank()) {
            Text(
                field.description,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (field.type) {
            "boolean" -> Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = {
                    bools[field.key] = !(bools[field.key] ?: field.defaultBool ?: false)
                }) {
                    Text(if (bools[field.key] ?: field.defaultBool ?: false) "是" else "否")
                }
            }
            "string" -> {
                val focusManager = LocalFocusManager.current
                // 点击输入框就用 press 事件取消单选：光靠 onFocusChanged 时，输入框已经聚焦就不会再触发
                val interaction = remember(field.key) { MutableInteractionSource() }
                LaunchedEffect(interaction, field.key) {
                    interaction.interactions.collect { event ->
                        if (event is PressInteraction.Press && field.type != "multiselect") {
                            if (!multis[field.key].isNullOrEmpty()) multis[field.key] = emptySet()
                        }
                    }
                }
                // 选项在上面，自定义输入在最下面
                if (field.options.isNotEmpty()) {
                    OptionChips(field, multis, values) {
                        // 选选项 = 关掉自定义回答：清空文字并收起焦点，两块互相排斥
                        values[field.key] = ""
                        focusManager.clearFocus()
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "或自定义回答",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                val singleChosen = field.options.isNotEmpty() &&
                    field.type != "multiselect" &&
                    !multis[field.key].isNullOrEmpty()
                OutlinedTextField(
                    value = values[field.key] ?: field.defaultText ?: "",
                    onValueChange = {
                        values[field.key] = it
                        // 自己输入时，上面的单选选项自动取消
                        if (field.type != "multiselect") multis[field.key] = emptySet()
                    },
                    placeholder = {
                        Text(
                            field.placeholder.ifBlank {
                                when {
                                    singleChosen -> "点这里输入自定义回答（会取消上面的选择）"
                                    field.options.isNotEmpty() -> "或者直接输入…"
                                    else -> ""
                                }
                            },
                        )
                    },
                    interactionSource = interaction,
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 4,
                )
            }
            "multiselect" -> OptionChips(field, multis, values)
            else -> OutlinedTextField(
                value = values[field.key] ?: field.defaultText ?: "",
                onValueChange = { values[field.key] = it },
                keyboardOptions = KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
        }
    }
}

@Composable
private fun OptionChips(
    field: FormField,
    multis: MutableMap<String, Set<String>>,
    values: MutableMap<String, String>,
    onPicked: () -> Unit = {},
) {
    // 用不可变 Set 放在 state map 里：每次点击整体替换，避免 list 变异不触发重组 / 状态错乱
    val selected = multis[field.key] ?: emptySet()
    val primary = MaterialTheme.colorScheme.primary
    val isMulti = field.type == "multiselect"
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 4.dp)) {
        field.options.forEach { option ->
            val isSelected = option.value in selected
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (isSelected) primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickableNoRipple {
                        // 与官方前端一致：单选再点同一项不会取消；多选是切换；
                        // 选选项 = 关掉自定义回答（清空文字并收键盘），两块互斥。
                        if (isMulti) {
                            multis[field.key] =
                                if (isSelected) selected - option.value else selected + option.value
                        } else {
                            multis[field.key] = setOf(option.value)
                            onPicked()
                        }
                    },
            ) {
                Row(
                    Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // 选中状态一定要看得出来：单选是圆点，多选是打勾方块
                    val markShape = if (isMulti) RoundedCornerShape(4.dp) else CircleShape
                    Box(
                        Modifier
                            .size(16.dp)
                            .border(1.5.dp, if (isSelected) primary else MaterialTheme.colorScheme.outline, markShape)
                            .background(if (isSelected) primary else Color.Transparent, markShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isSelected) {
                            Box(
                                Modifier
                                    .size(6.dp)
                                    .background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                            )
                        }
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            option.label,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (option.description.isNotBlank()) {
                            Text(
                                option.description,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
