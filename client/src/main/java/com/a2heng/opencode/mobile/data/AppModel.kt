package com.a2heng.opencode.mobile.data

import android.app.Application
import android.os.Looper
import android.widget.Toast
import androidx.compose.ui.graphics.Color
import org.connectbot.terminal.TerminalDimensions
import org.connectbot.terminal.TerminalEmulator
import org.connectbot.terminal.TerminalEmulatorFactory
import com.a2heng.opencode.mobile.ConnectionService
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

class AppModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("opencode-mobile", Application.MODE_PRIVATE)

    // 打开的会话（卷帘里的标签）：只在用户手动 × 或删除会话时才移除，
    // 并且持久化，重启 App 后仍然在。运行中/有后台 shell 不会自动塞进来。
    private fun persistOpenSessions() {
        prefs.edit()
            .putString("open_sessions", openSessions.joinToString("\n") { it.id })
            .apply()
    }

    private fun restoreOpenSessions() {
        if (openSessions.isNotEmpty()) return
        val ids = prefs.getString("open_sessions", "").orEmpty()
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (ids.isEmpty()) return
        val restored = ids.mapNotNull { id -> sessions.firstOrNull { it.id == id } }
        if (restored.isNotEmpty()) openSessions = restored.take(20)
    }

    // ---- 连接 ---------------------------------------------------------------
    var serverUrl by mutableStateOf(prefs.getString("url", "") ?: "")
        private set
    var password by mutableStateOf(prefs.getString("password", "") ?: "")
        private set
    var connected by mutableStateOf(false)
        private set
    var connecting by mutableStateOf(false)
        private set
    var connectionError by mutableStateOf<String?>(null)
        private set
    var serverVersion by mutableStateOf("")
        private set
    var homeDir by mutableStateOf("")
        private set

    /**
     * 运行中发送方式。opencode 的 delivery 只有 steer / queue，且**默认是 steer**
     * （见 core/src/session/session.ts：input.delivery ?? "steer"）。
     * 「中断后发送」是本 App 的客户端行为，不是 opencode 的 delivery。
     */
    var sendMode by mutableStateOf(
        run {
            if (!prefs.getBoolean("send_mode_v2", false)) {
                prefs.edit()
                    .putString("send_mode", "steer")
                    .putBoolean("send_mode_v2", true)
                    .apply()
            }
            prefs.getString("send_mode", "steer") ?: "steer"
        },
    )
        private set

    /** 聊天显示：compact（精简，工具一行 + 点击气泡看详情）/ full（完整，全部展开） */
    var detailLevel by mutableStateOf(
        (prefs.getString("detail_level", "full") ?: "full").let {
            if (it == "normal" || it == "verbose") "full" else it
        },
    )
        private set

    /** 会话列表排序：true=按文件夹分组，false=按时间 */
    var sessionsGroupByFolder by mutableStateOf(prefs.getBoolean("sessions_group_by_folder", true))
        private set

    /** 主题色：mono（默认）/ dynamic（Material You）/ blue / purple / orange / cyan */
    var themeMode by mutableStateOf(prefs.getString("theme_mode", "mono") ?: "mono")
        private set

    /** 外观：system（默认）/ light / dark */
    var appearance by mutableStateOf(prefs.getString("appearance", "system") ?: "system")
        private set

    /** 全局默认模型（新会话使用）；null 表示跟随服务端默认 */
    var defaultModel by mutableStateOf<ModelRef?>(
        run {
            val id = prefs.getString("default_model_id", "") ?: ""
            val provider = prefs.getString("default_model_provider", "") ?: ""
            val variant = prefs.getString("default_model_variant", "")?.takeIf { it.isNotBlank() }
            if (id.isBlank()) null else ModelRef(id, provider, variant)
        },
    )
        private set

    /** 从其它应用分享进来的内容，等聊天页消费 */
    var pendingDraft by mutableStateOf<String?>(null)
        private set
    var pendingAttachment by mutableStateOf<Attachment?>(null)
        private set
    var shells by mutableStateOf<List<ShellInfo>>(emptyList())
        private set

    fun refreshShells(directory: String? = current?.directory) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                // 只保留正在跑的；跑完的在服务端队列里自然消失，不需要本地留档
                shells = withContext(Dispatchers.IO) { c.shells(directory) }
                    .filter { it.status.isEmpty() || it.status == "running" }
            } catch (_: Exception) {
            }
        }
    }

    suspend fun loadShellOutput(id: String): String {
        val c = client ?: return ""
        return withContext(Dispatchers.IO) {
            runCatching { c.shellOutput(id, current?.directory) }.getOrDefault("")
        }
    }

    fun stopShell(id: String) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.stopShell(id, current?.directory) }
                refreshShells()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    /** 版本号来自包管理器（library 模块没有 BuildConfig.VERSION_NAME）。 */
    val appVersion: String = runCatching {
        app.packageManager.getPackageInfo(app.packageName, 0).versionName.orEmpty()
    }.getOrDefault("")
    var checkingUpdate by mutableStateOf(false)
        private set
    var updateMessage by mutableStateOf<String?>(null)
        private set
    var updateInfo by mutableStateOf<UpdateInfo?>(null)
        private set
    var downloadingUpdate by mutableStateOf(false)
        private set
    var updateProgress by mutableStateOf(0)
        private set

    var client: OcClient? = null
        private set

    // ---- 会话 ---------------------------------------------------------------
    var sessions by mutableStateOf<List<SessionInfo>>(emptyList())
        private set
    var activeIds by mutableStateOf<Set<String>>(emptySet())
        private set
    var sessionsLoading by mutableStateOf(false)
        private set
    var current by mutableStateOf<SessionInfo?>(null)
        private set
    /** 最近打开过的会话（顶部下拉里展示的“活着的标签页”） */
    var openSessions by mutableStateOf<List<SessionInfo>>(emptyList())
        private set
    var messages by mutableStateOf<List<Msg>>(emptyList())
        private set
    var messagesLoading by mutableStateOf(false)
        private set
    var sending by mutableStateOf(false)
        private set
    var liveText by mutableStateOf("")
        private set
    var liveReasoning by mutableStateOf("")
        private set
    var statusLine by mutableStateOf("")
        private set
    var queue by mutableStateOf<List<Queued>>(emptyList())
        private set
    /** 是否还能往更早翻页（服务端 cursor.next 非空）。 */
    var hasOlderMessages by mutableStateOf(false)
        private set
    var loadingOlderMessages by mutableStateOf(false)
        private set
    private var olderCursor: String? = null
    private data class CachedMessages(val messages: List<Msg>, val cursor: String?)
    /** 每个打开过的会话的已加载消息，切回来不重新拉；最多 8 个。 */
    private val sessionMessageCache = mutableMapOf<String, CachedMessages>()
    val queuedCount: Int get() = queue.size
    var lastError by mutableStateOf<String?>(null)
        private set

    // ---- 权限 / 表单 --------------------------------------------------------
    var permissions by mutableStateOf<List<PermissionReq>>(emptyList())
        private set
    var forms by mutableStateOf<List<FormInfo>>(emptyList())
        private set

    // ---- 模型 / Agent -------------------------------------------------------
    var agents by mutableStateOf<List<JsonObject>>(emptyList())
        private set
    var models by mutableStateOf<List<JsonObject>>(emptyList())
        private set
    var commands by mutableStateOf<List<CommandInfo>>(emptyList())
        private set
    /** modelID -> context window */
    var contextLimits by mutableStateOf<Map<String, Long>>(emptyMap())
        private set
    /** modelID -> 来源分组：0 免费 / 1 opencode go / 2 opencode console / 3 其它 */
    var modelGroupById by mutableStateOf<Map<String, Int>>(emptyMap())
        private set
    /** 服务端当前默认模型（只读，用于展示） */
    var serverDefaultModel by mutableStateOf<ModelRef?>(null)
        private set

    // ---- 缓存看板 -----------------------------------------------------------
    var caches by mutableStateOf<List<SessionCache>>(emptyList())
        private set
    var cacheLoading by mutableStateOf(false)
        private set
    var cacheError by mutableStateOf<String?>(null)
        private set
    var cacheFetchedAt by mutableStateOf(0L)
        private set

    /** 单个会话的缓存详情（从会话列表点命中率进入） */
    var cacheDetail by mutableStateOf<SessionCache?>(null)
        private set
    var cacheDetailLoading by mutableStateOf(false)
        private set
    var cacheDetailError by mutableStateOf<String?>(null)
        private set
    /** 内存缓存：同一个会话第二次打开直接秒开，后台再刷新。 */
    private val cacheDetailCache = mutableMapOf<String, SessionCache>()
    private var cacheDetailJob: Job? = null

    fun loadCacheDetail(session: SessionInfo) {
        val c = client ?: return
        val cached = cacheDetailCache[session.id]
        if (cached != null) {
            cacheDetail = cached
            cacheDetailLoading = false
            cacheDetailError = null
        } else {
            cacheDetailLoading = true
            cacheDetailError = null
        }
        cacheDetailJob?.cancel()
        cacheDetailJob = viewModelScope.launch {
            try {
                // 只取最新一页（200 条）算步骤；总量用服务端的 session.tokens，不再拉全历史
                val page = withContext(Dispatchers.IO) {
                    c.messagesPage(session.id, limit = 100, order = "desc")
                }
                val detail = withContext(Dispatchers.Default) {
                    SessionCache(session, stepsOf(page.messages), session.tokens)
                }
                if (cacheDetailCache.size >= 12) {
                    cacheDetailCache.keys.firstOrNull { it != session.id }?.let { cacheDetailCache.remove(it) }
                }
                cacheDetailCache[session.id] = detail
                if (cacheTarget?.id == session.id) cacheDetail = detail
            } catch (e: Exception) {
                if (cacheTarget?.id == session.id && cached == null) cacheDetailError = e.message
            } finally {
                if (cacheTarget?.id == session.id) cacheDetailLoading = false
            }
        }
    }

    fun clearCacheDetail() {
        cacheDetail = null
        cacheDetailError = null
    }

    /** 当前打开的缓存详情页（会话列表或对话标题里的命中率胶囊共用） */
    var cacheTarget by mutableStateOf<SessionInfo?>(null)
        private set

    fun openCache(session: SessionInfo) {
        cacheTarget = session
        loadCacheDetail(session)
    }

    fun closeCache() {
        cacheTarget = null
        clearCacheDetail()
    }

    // ---- 文件 ---------------------------------------------------------------
    /** 宿主指定的“/”；null = 真机根目录（Adia 现在用真实文件系统）。 */
    var fsRootOverride by mutableStateOf<String?>(null)
        private set
    var fsDir by mutableStateOf("")
        private set
    var fsRoot by mutableStateOf("")
        private set
    var fsEntries by mutableStateOf<List<FsEntry>>(emptyList())
        private set
    var fsLoading by mutableStateOf(false)
        private set
    var fsError by mutableStateOf<String?>(null)
        private set
    /** Última petición de listado; las respuestas viejas se descartan. */
    private var fsLoadSeq = 0
    var openFile by mutableStateOf<String?>(null)
        private set
    var openFileText by mutableStateOf("")
        private set
    var openFileLoading by mutableStateOf(false)
        private set
    var openFileImage by mutableStateOf<android.graphics.Bitmap?>(null)
        private set
    var openFileMime by mutableStateOf("")
        private set
    var openFileBinary by mutableStateOf(false)
        private set
    var openFileMarkdown by mutableStateOf(false)
        private set
    /** 音视频预览：写到缓存文件后的本地 URI */
    var openFileMediaUri by mutableStateOf<android.net.Uri?>(null)
        private set
    /** "audio" / "video" */
    var openFileMediaKind by mutableStateOf<String?>(null)
        private set
    var openFileBytesSize by mutableStateOf(0L)
        private set

    // ---- Git / VCS（上下文按钮打开）-----------------------------------------
    var gitDirectory by mutableStateOf<String?>(null)
        private set
    var gitInfo by mutableStateOf(VcsInfo())
        private set
    var gitStatus by mutableStateOf<List<VcsFileStatus>>(emptyList())
        private set
    var gitDiffFiles by mutableStateOf<List<VcsDiffFile>>(emptyList())
        private set
    var gitLoading by mutableStateOf(false)
        private set
    var gitError by mutableStateOf<String?>(null)
        private set
    var gitSelectedFile by mutableStateOf<String?>(null)
        private set
    var gitMode by mutableStateOf("working") // working | branch
        private set
    var gitBaseLabel by mutableStateOf<String?>(null)
        private set

    fun openGit(directory: String) {
        val dir = directory.ifBlank { current?.directory.orEmpty() }.ifBlank { homeDir }
        if (dir.isBlank()) return
        gitDirectory = dir
        gitSelectedFile = null
        gitError = null
        gitInfo = VcsInfo()
        gitStatus = emptyList()
        gitDiffFiles = emptyList()
        gitBaseLabel = null
        gitMode = "working"
        gitLoading = true
        loadGit("working")
    }

    fun closeGit() {
        gitDirectory = null
        gitSelectedFile = null
        gitError = null
        gitLoading = false
    }

    fun loadGit(mode: String = gitMode) {
        val c = client ?: return
        val dir = gitDirectory ?: return
        viewModelScope.launch {
            gitLoading = true
            gitError = null
            gitMode = mode
            if (mode == "working") gitStatus = emptyList()
            gitDiffFiles = emptyList()
            try {
                val (info, base, status, diffs) = withContext(Dispatchers.IO) {
                    val i = runCatching { c.vcsInfo(dir) }.getOrDefault(VcsInfo())
                    val b = runCatching { c.vcsBase(dir) }.getOrNull()
                    val s = if (mode == "working") runCatching { c.vcsStatus(dir) }.getOrDefault(emptyList()) else emptyList()
                    val d = runCatching { c.vcsDiff(dir, mode) }.getOrDefault(emptyList())
                    Quad(i, b, s, d)
                }
                gitInfo = info
                gitBaseLabel = base?.str("name") ?: base?.str("ref") ?: base?.str("base")
                gitStatus = status
                gitDiffFiles = diffs
                if (gitSelectedFile != null && diffs.none { it.file == gitSelectedFile }) {
                    gitSelectedFile = diffs.firstOrNull()?.file
                }
            } catch (e: Exception) {
                gitError = e.message ?: "加载 Git 信息失败"
            } finally {
                gitLoading = false
            }
        }
    }

    private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

    fun selectGitFile(file: String?) {
        gitSelectedFile = file
    }

    // ---- 终端 ---------------------------------------------------------------
    var ptys by mutableStateOf<List<PtyInfo>>(emptyList())
        private set
    var ptyActive by mutableStateOf<PtyInfo?>(null)
        private set
    var ptyOutput by mutableStateOf("")
        private set
    var ptyConnected by mutableStateOf(false)
        private set
    var ptyConnecting by mutableStateOf(false)
        private set
    var ptyError by mutableStateOf<String?>(null)
        private set
    var ptyEmulator by mutableStateOf<TerminalEmulator?>(null)
        private set
    // 终端字号（记住缩放）
    var ptyFontSize by mutableStateOf(prefs.getFloat("pty_font_size", 11f))
        private set
    private var ptySocket: WebSocket? = null
    private var ptyDecoding = false

    // 本机 Termux 终端：进程和 emulator 放在 ViewModel，旋转屏幕/切页不丢。
    var localTerminalOpen by mutableStateOf(false)
        private set
    var localTerminalEmulator by mutableStateOf<TerminalEmulator?>(null)
        private set
    var localTerminalStarting by mutableStateOf(false)
        private set
    var localTerminalError by mutableStateOf<String?>(null)
        private set
    private var localTerminalProcess: Process? = null
    private var localTerminalReader: Job? = null

    private var eventJob: Job? = null
    private var reloadJob: Job? = null
    private var sessionsJob: Job? = null

    // =========================================================================
    // 连接
    // =========================================================================

    fun connect(url: String, pwd: String, remember: Boolean = true) {
        val normalized = OcClient.normalize(url)
        connecting = true
        connectionError = null
        viewModelScope.launch {
            try {
                val c = OcClient(normalized, pwd)
                val info = withContext(Dispatchers.IO) { c.ping() } as? JsonObject
                serverUrl = normalized
                password = pwd
                client = c
                serverVersion = info?.str("version") ?: ""
                runCatching { withContext(Dispatchers.IO) { c.locationRoot() } }
                    .onSuccess { homeDir = it }
                connected = true
                connecting = false
                if (remember) {
                    prefs.edit().putString("url", normalized).putString("password", pwd).apply()
                }
                retryJob?.cancel()
                retryDelay = 5_000L
                ConnectionService.start(getApplication())
                refreshSessions()
                loadPermissions()
                loadMeta()
                startEvents()
            } catch (e: Exception) {
                connecting = false
                connected = false
                connectionError = e.message ?: e.javaClass.simpleName
                scheduleReconnect()
            }
        }
    }

    fun disconnect() {
        retryJob?.cancel()
        ConnectionService.stop(getApplication())
        eventJob?.cancel()
        reloadJob?.cancel()
        sessionsJob?.cancel()
        detachPty()
        client = null
        connected = false
        current = null
        messages = emptyList()
        sessions = emptyList()
        caches = emptyList()
        permissions = emptyList()
        forms = emptyList()
        serverVersion = ""
    }

    fun autoConnect() {
        if (serverUrl.isNotBlank() && !connected && !connecting) connect(serverUrl, password)
    }

    fun clearError() {
        lastError = null
    }

    fun updateSendMode(mode: String) {
        sendMode = mode
        prefs.edit().putString("send_mode", mode).apply()
    }

    fun updateDetailLevel(level: String) {
        detailLevel = level
        prefs.edit().putString("detail_level", level).apply()
    }

    fun updateSessionsGroupByFolder(grouped: Boolean) {
        sessionsGroupByFolder = grouped
        prefs.edit().putBoolean("sessions_group_by_folder", grouped).apply()
    }

    fun updateThemeMode(mode: String) {
        themeMode = mode
        prefs.edit().putString("theme_mode", mode).apply()
    }

    fun updateAppearance(mode: String) {
        appearance = mode
        prefs.edit().putString("appearance", mode).apply()
    }

    fun updateDefaultModel(model: ModelRef?) {
        defaultModel = model
        prefs.edit()
            .putString("default_model_id", model?.id ?: "")
            .putString("default_model_provider", model?.providerID ?: "")
            .putString("default_model_variant", model?.variant ?: "")
            .apply()
    }

    fun applyShare(text: String?, image: Attachment?) {
        if (!text.isNullOrBlank()) pendingDraft = text
        if (image != null) pendingAttachment = image
    }
    fun consumeDraft(): Pair<String?, Attachment?> {
        val result = pendingDraft to pendingAttachment
        pendingDraft = null
        pendingAttachment = null
        return result
    }

    fun checkUpdate() {
        if (checkingUpdate) return
        checkingUpdate = true
        updateMessage = "检查中…"
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { UpdateClient.latest() }
                if (info == null) {
                    updateMessage = "没有找到 Release"
                } else {
                    updateInfo = info
                    updateMessage = if (UpdateClient.isNewer(appVersion, info.tag)) {
                        "发现新版本 ${info.tag}"
                    } else {
                        "已是最新版本 v$appVersion"
                    }
                }
            } catch (e: Exception) {
                updateMessage = "检查失败：" + (e.message ?: "")
            } finally {
                checkingUpdate = false
            }
        }
    }

    fun downloadUpdate() {
        val info = updateInfo ?: return
        val url = info.apkUrl
        if (url == null) {
            updateMessage = "这个版本没有 APK 附件"
            return
        }
        if (downloadingUpdate) return
        downloadingUpdate = true
        updateProgress = 0
        viewModelScope.launch {
            try {
                val dir = java.io.File(getApplication<Application>().cacheDir, "update").apply { mkdirs() }
                val dest = java.io.File(dir, "adia-${info.tag.removePrefix("v")}.apk")
                withContext(Dispatchers.IO) {
                    UpdateClient.download(url, dest) { p -> updateProgress = p }
                }
                installApk(dest)
                updateMessage = "已下载，交给系统安装"
            } catch (e: Exception) {
                updateMessage = "下载失败：" + (e.message ?: "")
            } finally {
                downloadingUpdate = false
            }
        }
    }

    private fun installApk(file: java.io.File) {
        val ctx = getApplication<Application>()
        val uri = androidx.core.content.FileProvider.getUriForFile(
            ctx,
            ctx.packageName + ".fileprovider",
            file,
        )
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        ctx.startActivity(intent)
    }

    // ---- 自动重连（退避，避免频繁打服务端） --------------------------------
    private var retryJob: Job? = null
    private var retryDelay = 5_000L

    private fun scheduleReconnect() {
        if (retryJob?.isActive == true) return
        if (serverUrl.isBlank()) return
        val delayMs = retryDelay
        retryDelay = (retryDelay * 2).coerceAtMost(60_000L)
        retryJob = viewModelScope.launch {
            delay(delayMs)
            if (!connected && !connecting && serverUrl.isNotBlank()) {
                connect(serverUrl, password)
            }
        }
    }

    /** 手动重连：立即尝试，不等待退避。 */
    fun retryNow() {
        retryJob?.cancel()
        retryDelay = 5_000L
        if (serverUrl.isNotBlank()) connect(serverUrl, password)
    }

    /** 回到前台时调用：只在断开时补一次，连上就不动。 */
    fun retryIfNeeded() {
        if (!connected && !connecting && serverUrl.isNotBlank()) retryNow()
    }

    /**
     * 从后台 / 锁屏回到前台：SSE 可能已经断了一段时间，界面上的「正在思考 / 第 N 步」
     * 是旧事件攒出来的，会和服务端实际状态对不上。这里强制同步一次运行状态和最新消息。
     */
    fun refreshOnResume() {
        if (!connected) {
            retryIfNeeded()
            return
        }
        val c = client ?: return
        refreshSessions()
        viewModelScope.launch {
            val active = withContext(Dispatchers.IO) {
                runCatching { c.activeSessionIds() }.getOrNull()
            }
            if (active != null) {
                activeIds = active
                val cur = current
                if (cur != null && cur.id !in active) {
                    sending = false
                    liveText = ""
                    liveReasoning = ""
                    statusLine = ""
                }
            }
            loadMessages(silent = true)
            loadInbox()
            refreshShells(current?.directory)
        }
    }

    // =========================================================================
    // 会话
    // =========================================================================

    fun refreshSessions() {
        val c = client ?: return
        if (sessionsJob?.isActive == true) return
        sessionsJob = viewModelScope.launch {
            sessionsLoading = true
            try {
                val (list, active) = withContext(Dispatchers.IO) {
                    c.sessionList() to c.activeSessionIds()
                }
                sessions = list.sortedByDescending { it.updated }.map { fresh ->
                    val old = sessions.firstOrNull { it.id == fresh.id }
                    if (fresh.model == null && old?.model != null) fresh.copy(model = old.model) else fresh
                }
                activeIds = active
                current?.let { cur ->
                    sessions.firstOrNull { it.id == cur.id }?.let { fresh ->
                        current = if (fresh.model == null && cur.model != null) fresh.copy(model = cur.model) else fresh
                    }
                }
                openSessions = openSessions.map { old ->
                    sessions.firstOrNull { it.id == old.id } ?: old
                }
                restoreOpenSessions()
            } catch (e: Exception) {
                connectionError = e.message
            } finally {
                sessionsLoading = false
            }
        }
    }

    fun openSession(session: SessionInfo) {
        current?.let { prev -> if (prev.id != session.id) saveMessageCache(prev.id) }
        current = session
        openSessions = (listOf(session) + openSessions.filterNot { it.id == session.id }).take(20)
        persistOpenSessions()
        val cached = sessionMessageCache[session.id]
        messages = cached?.messages ?: emptyList()
        olderCursor = cached?.cursor
        hasOlderMessages = olderCursor != null
        loadingOlderMessages = false
        liveText = ""
        liveReasoning = ""
        sending = isActive(session.id)
        queue = emptyList()
        lastError = null
        // 有缓存就先显示，再静默刷新最新一页（只拉最新 40 条）
        loadMessages(silent = cached != null)
        loadForms(session.id)
        loadInbox()
    }

    fun closeSession() {
        saveMessageCache(current?.id)
        current = null
        messages = emptyList()
        olderCursor = null
        hasOlderMessages = false
        loadingOlderMessages = false
        liveText = ""
        liveReasoning = ""
        forms = emptyList()
    }

    private fun saveMessageCache(id: String?) {
        if (id.isNullOrBlank()) return
        sessionMessageCache[id] = CachedMessages(messages, olderCursor)
        if (sessionMessageCache.size > 8) {
            val victim = sessionMessageCache.keys.firstOrNull { it != current?.id } ?: return
            sessionMessageCache.remove(victim)
        }
    }

    /** 关闭「打开的会话」里的一个：如果是当前会话，就切到下一个，没有就回到列表。 */
    fun closeOpenSession(id: String) {
        openSessions = openSessions.filterNot { it.id == id }
        persistOpenSessions()
        if (current?.id == id) {
            val next = openSessions.firstOrNull()
            if (next != null) openSession(next) else closeSession()
        }
    }

    /** 退出多会话切换状态：全部关掉，回到会话列表。 */
    fun closeAllOpenSessions() {
        openSessions = emptyList()
        persistOpenSessions()
        closeSession()
    }

    fun loadMessages(silent: Boolean = false) {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            if (!silent) messagesLoading = true
            try {
                // 只拉最新一页（官方前端也是 20~40 条一页），再按 id 合并，
                // 不会因为一次工具事件就重拉整个会话。
                val page = withContext(Dispatchers.IO) {
                    c.messagesPage(s.id, limit = 40, order = "desc")
                }
                if (current?.id != s.id) return@launch
                val fresh = collapseMessages(page.messages).asReversed()
                val freshIds = fresh.map { it.id }.toHashSet()
                // 服务端顺序是权威的：老的在前，最新一页接在后面；不要按 created 重排
                val merged = messages.filterNot { it.id in freshIds } + fresh
                messages = merged
                autoBackgroundIfPending()
                if (olderCursor == null || merged.size == fresh.size) {
                    olderCursor = page.next
                }
                hasOlderMessages = olderCursor != null
                liveText = ""
                liveReasoning = ""
                // 服务端没给会话存 model 时，从最后一条 assistant 消息里回填，
                // 这样标题胶囊和会话列表的模型前缀都能显示出来。
                if (current?.model == null) {
                    merged.lastOrNull { it.isAssistant && it.model != null }?.model?.let { m ->
                        current?.let { cur ->
                            current = cur.copy(model = m)
                            sessions = sessions.map { if (it.id == cur.id) it.copy(model = m) else it }
                            openSessions = openSessions.map { if (it.id == cur.id) it.copy(model = m) else it }
                        }
                    }
                }
            } catch (e: Exception) {
                if (!silent) lastError = e.message
            } finally {
                messagesLoading = false
            }
        }
    }

    /** 往更早翻一页（滚动到消息列表顶部时调用）。 */
    fun loadOlderMessages() {
        if (loadingOlderMessages || !hasOlderMessages) return
        val c = client ?: return
        val s = current ?: return
        val cursor = olderCursor ?: return
        loadingOlderMessages = true
        viewModelScope.launch {
            try {
                val page = withContext(Dispatchers.IO) {
                    c.messagesPage(s.id, limit = 40, cursor = cursor)
                }
                if (current?.id != s.id) return@launch
                val incoming = collapseMessages(page.messages).asReversed()
                if (incoming.isEmpty()) {
                    olderCursor = null
                    hasOlderMessages = false
                    return@launch
                }
                val ids = messages.map { it.id }.toHashSet()
                messages = incoming.filterNot { it.id in ids } + messages
                olderCursor = page.next
                hasOlderMessages = page.next != null
            } catch (e: Exception) {
                lastError = e.message
            } finally {
                loadingOlderMessages = false
            }
        }
    }

    private fun collapseMessages(list: List<Msg>): List<Msg> {
        // 服务端返回的列表本身是权威顺序（desc），这里只做过滤，不再按 created 重排：
        // 队列消息的 created 可能是入队时间，按时间排会把上一轮的工具挤到它后面，造成闪烁。
        return list.filter {
            when {
                it.isUser -> true
                it.isAssistant -> it.parts.isNotEmpty() || it.text.isNotBlank()
                else -> false
            }
        }
    }

    fun sendPrompt(text: String, attachments: List<Attachment> = emptyList()) {
        val c = client ?: return
        val s = current ?: return
        if (text.isBlank() && attachments.isEmpty()) return
        val busy = isActive(s.id)
        val mode = if (!busy) "direct" else sendMode
        lastError = null
        if (mode != "queue" && mode != "steer") {
            sending = true
            liveText = ""
            liveReasoning = ""
        }
        val bodyText = text.ifBlank { if (attachments.isNotEmpty()) "请看这张图片" else "" }
        viewModelScope.launch {
            try {
                when (mode) {
                    "queue" -> {
                        // 排队 / 引导都不改动 statusLine：用户已经知道消息进了队列，
                        // 覆盖掉正在思考 / 正在使用工具的真实状态只会更乱。
                        withContext(Dispatchers.IO) { c.prompt(s.id, bodyText, "queue", attachments) }
                        delay(300)
                        loadInbox()
                    }
                    "steer" -> {
                        withContext(Dispatchers.IO) { c.prompt(s.id, bodyText, "steer", attachments) }
                    }
                    "interrupt" -> {
                        withContext(Dispatchers.IO) {
                            c.interrupt(s.id)
                            delay(300)
                            c.prompt(s.id, bodyText, null, attachments)
                        }
                    }
                    else -> {
                        withContext(Dispatchers.IO) { c.prompt(s.id, bodyText, null, attachments) }
                        delay(350)
                        loadMessages(silent = true)
                    }
                }
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
                if (mode != "queue" && mode != "steer") sending = false
            }
        }
    }

    fun runCommand(name: String, text: String, attachments: List<Attachment> = emptyList()) {
        val c = client ?: return
        val s = current ?: return
        val busy = isActive(s.id)
        val mode = if (!busy) "direct" else sendMode
        lastError = null
        if (mode != "queue" && mode != "steer") {
            sending = true
            liveText = ""
            liveReasoning = ""
        }
        viewModelScope.launch {
            try {
                when (mode) {
                    "queue" -> {
                        withContext(Dispatchers.IO) { c.runCommand(s.id, name, text, "queue", attachments) }
                        delay(300)
                        loadInbox()
                    }
                    "steer" -> {
                        withContext(Dispatchers.IO) { c.runCommand(s.id, name, text, "steer", attachments) }
                    }
                    "interrupt" -> {
                        withContext(Dispatchers.IO) {
                            c.interrupt(s.id)
                            delay(300)
                            c.runCommand(s.id, name, text, null, attachments)
                        }
                    }
                    else -> {
                        withContext(Dispatchers.IO) { c.runCommand(s.id, name, text, null, attachments) }
                        delay(350)
                        loadMessages(silent = true)
                    }
                }
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
                if (mode != "queue" && mode != "steer") sending = false
            }
        }
    }

    fun loadInbox() {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { c.inbox(s.id) }
                queue = list.filter { it.str("type") == "user" }.map {
                    Queued(
                        id = it.str("id") ?: "",
                        text = it.obj("payload")?.str("text") ?: "",
                        delivery = it.str("delivery") ?: "queue",
                    )
                }
                autoBackgroundIfPending()
            } catch (_: Exception) {
            }
        }
    }

    fun cancelQueued(id: String) {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.cancelInbox(s.id, id) }
                queue = queue.filterNot { it.id == id }
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    /** 撤回排队消息：取消服务端队列项，成功后再把原文还给输入框。 */
    fun withdrawQueued(id: String, onDone: (String) -> Unit) {
        val c = client ?: return
        val s = current ?: return
        val item = queue.firstOrNull { it.id == id } ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.cancelInbox(s.id, id) }
                queue = queue.filterNot { it.id == id }
                onDone(item.text)
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    /**
     * opencode 的 revert：长按已发出的用户消息时使用。
     * files=true 连文件改动一起还原；false 只回退对话。
     */
    fun revertTo(messageID: String, files: Boolean) {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    c.stageRevert(s.id, messageID, files)
                    c.commitRevert(s.id)
                }
                queue = emptyList()
                loadMessages(silent = true)
                refreshSessions()
            } catch (e: Exception) {
                lastError = "回退失败：" + (e.message ?: "")
            }
        }
    }

    fun interrupt() {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.interrupt(s.id) }
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun createSession(directory: String? = null) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                val dir = expandSessionDir(directory)
                    ?: expandSessionDir(current?.directory)
                    ?: expandSessionDir(sessions.firstOrNull()?.directory)
                var s = withContext(Dispatchers.IO) { c.createSession(dir) }
                // 默认模型必须落到服务端会话上：只改本地 copy 的话，
                // 别的客户端或重连后会退回服务端默认模型。
                val chosen = defaultModel
                if (s != null && chosen != null && !sameModel(s.model, chosen)) {
                    val applied = runCatching {
                        withContext(Dispatchers.IO) { c.setModel(s.id, chosen) }
                    }.isSuccess
                    if (!applied) {
                        lastError = "默认模型 ${chosen.label} 没能应用到新会话"
                    }
                    // 以服务端返回为准（含 variant 归一化），读不到才本地补。
                    val fresh: SessionInfo? = withContext(Dispatchers.IO) {
                        runCatching { c.session(s.id) }.getOrNull()
                    }
                    s = fresh ?: s.copy(model = chosen)
                }
                refreshSessions()
                if (s != null) openSession(s)
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    private fun sameModel(a: ModelRef?, b: ModelRef?): Boolean =
        a != null && b != null && a.id == b.id && a.providerID == b.providerID &&
            a.variant == b.variant

    /** 新建会话对话框里浏览目录用，不影响「文件」页签的状态。 */
    suspend fun browseDirs(path: String?): Pair<String, List<FsEntry>> {
        val c = client ?: return "" to emptyList()
        return withContext(Dispatchers.IO) { c.listDir(expandUserPath(path) ?: "") }
    }

    /** 在指定目录下新建文件夹（走服务端的 shell，随后让对话框自己刷新）。 */
    fun createFolder(parent: String, name: String, onDone: () -> Unit) {
        val c = client ?: return
        val base = expandUserPath(parent) ?: parent
        val target = base.trimEnd('/') + "/" + name.trim().trim('/')
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    c.runShell("mkdir -p -- " + shellQuote(target), base)
                }
                delay(400)
                onDone()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    private fun shellQuote(s: String) = "'" + s.replace("'", "'\\''") + "'"

    fun renameSession(id: String, title: String) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.renameSession(id, title) }
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun deleteSession(id: String) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.deleteSession(id) }
                openSessions = openSessions.filterNot { it.id == id }
                persistOpenSessions()
                if (current?.id == id) closeSession()
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun setModel(model: ModelRef) {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.setModel(s.id, model) }
                current = s.copy(model = model)
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun setAgent(agent: String) {
        val c = client ?: return
        val s = current ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.setAgent(s.id, agent) }
                current = s.copy(agent = agent)
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    // =========================================================================
    // 事件流
    // =========================================================================

    private fun startEvents() {
        eventJob?.cancel()
        eventJob = viewModelScope.launch {
            var backoff = 1000L
            while (connected) {
                val c = client ?: break
                try {
                    c.events().collect { event -> handleEvent(event) }
                } catch (_: Exception) {
                }
                if (!connected) break
                delay(backoff)
                backoff = (backoff * 2).coerceAtMost(10_000)
                refreshSessions()
            }
        }
    }

    private fun handleEvent(event: JsonObject) {
        val type = event.str("type") ?: return
        if (type == "__error") return
        val data = event.obj("data") ?: JsonObject(emptyMap())
        var sessionID = data.str("sessionID")
        if (sessionID == null) {
            sessionID = data.obj("info")?.str("sessionID") ?: data.str("id")
        }

        if (type == "server.connected") {
            refreshSessions()
            loadPermissions()
            return
        }

        if (type.startsWith("permission.")) {
            loadPermissions()
            return
        }

        if (type.startsWith("session.inbox.")) {
            loadInbox()
            return
        }

        if (type.startsWith("form.")) {
            sessionID?.let { loadForms(it) }
            if (current != null) loadForms(current!!.id)
            return
        }

        if (type == "session.updated" || type == "session.created" || type == "session.deleted") {
            refreshSessions()
            return
        }

        val sid = sessionID ?: return

        if (current?.id != sid) return

        when (type) {
            "session.text.started" -> {
                if (liveText.isNotEmpty() && !liveText.endsWith("\n")) liveText += "\n"
            }
            "session.text.delta" -> {
                liveText += data.str("delta") ?: ""
            }
            "session.text.ended" -> {
                data.str("text")?.let { full ->
                    if (liveText.length < full.length) liveText = full
                }
            }
            "session.reasoning.started" -> {
                if (liveReasoning.isNotEmpty()) liveReasoning += "\n\n"
            }
            "session.reasoning.delta" -> {
                liveReasoning += data.str("delta") ?: ""
            }
            "session.reasoning.ended" -> {
                data.str("text")?.let { full ->
                    if (liveReasoning.length < full.length) liveReasoning = full
                }
            }
            "session.execution.started" -> {
                sending = true
                activeIds = activeIds + sid
                statusLine = "正在思考…"
            }
            "session.step.started" -> {
                val n = data.str("ordinal") ?: data.str("step") ?: data.str("index")
                statusLine = if (n.isNullOrBlank()) "思考中…" else "第 $n 步"
            }
            "session.step.ended", "session.step.streamed" -> {
                sending = true
                scheduleReload()
            }
            "session.tool.input.started" -> statusLine = "准备工具 ${data.str("name") ?: ""}"
            "session.tool.input.ended", "session.tool.called", "session.tool.progress" -> {
                statusLine = "执行工具 ${data.str("name") ?: ""}"
                scheduleReload()
            }
            "session.tool.success", "session.tool.failed" -> {
                scheduleReload()
            }
            "session.execution.succeeded", "session.execution.failed",
            "session.execution.interrupted", "session.idle" -> {
                sending = false
                activeIds = activeIds - sid
                statusLine = ""
                scheduleReload(immediate = true)
                refreshSessions()
            }
            "session.error" -> {
                sending = false
                lastError = data.str("message")
                scheduleReload(immediate = true)
            }
            "session.message.content.updated", "session.message" -> scheduleReload()
            "session.compaction.started" -> {
                statusLine = "正在压缩上下文…"
                sending = true
            }
            "session.compaction.ended" -> {
                statusLine = ""
                if (!isActive(sid)) sending = false
                scheduleReload(immediate = true)
            }
            "session.usage.recorded", "session.usage.updated" -> scheduleReload()
            else -> {
                if (type.startsWith("session.tool.")) scheduleReload()
            }
        }
    }

    private fun scheduleReload(immediate: Boolean = false) {
        reloadJob?.cancel()
        reloadJob = viewModelScope.launch {
            delay(if (immediate) 80 else 450)
            loadMessages(silent = true)
        }
    }

    // =========================================================================
    // 权限 / 表单
    // =========================================================================

    fun loadPermissions() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                permissions = withContext(Dispatchers.IO) { c.permissions() }
            } catch (_: Exception) {
            }
        }
    }

    fun replyPermission(req: PermissionReq, decision: String) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.replyPermission(req.sessionID, req.id, decision) }
                permissions = permissions.filterNot { it.id == req.id }
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun loadForms(sessionID: String) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { c.forms(sessionID) }
                forms = forms.filterNot { it.sessionID == sessionID } + list
            } catch (_: Exception) {
            }
        }
    }

    fun replyForm(form: FormInfo, answer: kotlinx.serialization.json.JsonElement) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.replyForm(form.sessionID, form.id, answer) }
                forms = forms.filterNot { it.id == form.id }
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    fun dismissForm(formID: String) {
        forms = forms.filterNot { it.id == formID }
    }

    // =========================================================================
    // 模型 / Agent
    // =========================================================================

    fun loadMeta(directory: String? = null) {
        val c = client ?: return
        viewModelScope.launch {
            try {
                val (a, m) = withContext(Dispatchers.IO) { c.agents() to c.models() }
                agents = a.filter { it.str("mode") != "subagent" }
                contextLimits = m.mapNotNull { item ->
                    val id = item.str("id") ?: return@mapNotNull null
                    val limit = item.obj("limit")?.long("context") ?: 0L
                    if (limit > 0) id to limit else null
                }.toMap()
                modelGroupById = m.mapNotNull { item ->
                    val id = item.str("id") ?: return@mapNotNull null
                    val provider = item.str("providerID") ?: ""
                    val cost = (item["cost"] as? kotlinx.serialization.json.JsonArray)
                        ?.firstOrNull() as? kotlinx.serialization.json.JsonObject
                    val free = (cost?.dbl("input") ?: 0.0) == 0.0 &&
                        (cost?.dbl("output") ?: 0.0) == 0.0
                    val group = when {
                        provider == "opencode-go" -> 1
                        provider == "opencode" && free -> 0
                        provider == "opencode" -> 2
                        else -> 3
                    }
                    "$provider/$id" to group
                }.toMap()
                models = m.filter {
                    val enabled = (it["enabled"] as? JsonPrimitive)?.booleanOrNull
                    val status = it.str("status")
                    (enabled != false) && (status == null || status == "active")
                }
                serverDefaultModel = withContext(Dispatchers.IO) {
                    runCatching { c.defaultModel() }.getOrNull()
                }
                val cmds = withContext(Dispatchers.IO) {
                    val local = if (directory.isNullOrBlank()) emptyList() else {
                        runCatching { c.commands(directory) }.getOrDefault(emptyList())
                    }
                    val global = runCatching { c.commands() }.getOrDefault(emptyList())
                    (local + global).distinctBy { it.name }
                }
                commands = cmds
            } catch (_: Exception) {
            }
        }
    }

    fun compactSession() {
        val c = client ?: return
        val s = current ?: return
        Toast.makeText(getApplication(), "正在压缩上下文…", Toast.LENGTH_SHORT).show()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.compact(s.id) }
                loadMessages(silent = true)
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    private var lastBackgroundAt = 0L

    /** 当前助手消息里是否还有正在运行的 shell（等价原版 Ctrl+B 的阻塞工作）。 */
    fun hasRunningShell(): Boolean =
        messages.lastOrNull { it.isAssistant }?.parts?.any {
            it is Part.Tool && it.name.lowercase() in setOf("shell", "bash", "sh") && it.status == "running"
        } == true

    /** 原版 Ctrl+B：把当前阻塞的 shell 移到后台观察。 */
    fun backgroundCurrent() {
        val c = client ?: return
        val s = current ?: return
        lastBackgroundAt = System.currentTimeMillis()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.background(s.id) }
                refreshShells()
                scheduleReload(immediate = true)
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    /** 有 Pending 等待时，自动把正在跑的 shell 移到后台，让队列能继续。 */
    fun autoBackgroundIfPending() {
        if (queue.isEmpty() || !hasRunningShell()) return
        if (System.currentTimeMillis() - lastBackgroundAt < 2500) return
        backgroundCurrent()
    }

    fun reloadConfig() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.reloadConfig() }
                loadMeta(current?.directory)
                refreshSessions()
            } catch (e: Exception) {
                lastError = e.message
            }
        }
    }

    // =========================================================================
    // 缓存看板
    // =========================================================================

    fun refreshCache() {
        val c = client ?: return
        if (cacheLoading) return
        cacheLoading = true
        cacheError = null
        viewModelScope.launch {
            try {
                val list = withContext(Dispatchers.IO) { c.sessionList() }
                    .sortedByDescending { it.updated }
                    .take(20)
                val out = mutableListOf<SessionCache>()
                // 并发 4 个，避免把服务端和手机网络打死
                for (chunk in list.chunked(4)) {
                    val rows = chunk.map { s ->
                        async(Dispatchers.IO) {
                            try {
                                SessionCache(s, stepsOf(c.messages(s.id)), s.tokens)
                            } catch (_: Exception) {
                                null
                            }
                        }
                    }.awaitAll().filterNotNull()
                    out += rows
                }
                caches = out.sortedByDescending { it.session.updated }
                cacheFetchedAt = System.currentTimeMillis()
            } catch (e: Exception) {
                cacheError = e.message
            } finally {
                cacheLoading = false
            }
        }
    }

    fun cacheFor(sessionID: String): SessionCache? = caches.firstOrNull { it.session.id == sessionID }

    fun isActive(sessionID: String): Boolean = sessionID in activeIds

    // =========================================================================
    // 文件
    // =========================================================================

    fun overrideFsRoot(path: String?) {
        val next = path?.trimEnd('/')?.takeIf { it.isNotBlank() }
        if (next != fsRootOverride) {
            fsRootOverride = next
            fsRoot = fsBase()
            // La raiz cambio (p. ej. una version anterior usaba rootfs):
            // invalida el listado para que FilesScreen vuelva a cargar.
            fsDir = ""
            fsEntries = emptyList()
            fsError = null
        }
    }

    /**
     * HOME del servidor opencode (el "~" real), sin barra final.
     * Sale de GET /api/location al conectar.
     */
    fun homePath(): String = homeDir.trimEnd('/')

    /** Raiz canonica de proyectos dentro del HOME: "<home>/workspace". */
    fun sessionRoot(): String {
        val h = homePath()
        return if (h.isNotBlank()) "$h/workspace" else ""
    }

    /**
     * Expande las dos rutas especiales del selector de sesiones:
     * - "~" / "~/..." -> HOME del servidor;
     * - "/" -> HOME/workspace (un proyecto en la raiz Android es lo que hacia
     *   que el selector quedara inservible).
     */
    fun expandSessionDir(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        val home = homePath()
        if (home.isBlank()) return s
        return when {
            s == "~" || s == "~/" -> home
            s.startsWith("~/") -> home + s.drop(1)
            s == "/" -> sessionRoot().ifBlank { home }
            else -> s
        }
    }

    /** Solo expande "~"; deja "/" y las rutas absolutas tal cual. */
    fun expandUserPath(raw: String?): String? {
        val s = raw?.trim().orEmpty()
        if (s.isBlank()) return null
        val home = homePath()
        if (home.isBlank()) return s
        return when {
            s == "~" || s == "~/" -> home
            s.startsWith("~/") -> home + s.drop(1)
            else -> s
        }
    }

    /** "/" del explorador de archivos: override del host o la raiz real. */
    fun fsBase(): String =
        fsRootOverride?.trimEnd('/')?.takeIf { it.isNotBlank() } ?: "/"

    /** "~" del explorador: HOME real del servidor. */
    fun fsHome(): String = homePath()

    fun loadDir(path: String?) {
        val c = client ?: return
        val requested = expandUserPath(path)
        val target = requested ?: fsRootOverride
        val seq = ++fsLoadSeq
        fsLoading = true
        fsError = null
        viewModelScope.launch {
            try {
                val (dir, entries) = withContext(Dispatchers.IO) { c.listDir(target) }
                if (seq != fsLoadSeq) return@launch
                fsRoot = fsBase()
                fsDir = dir.ifBlank { target.orEmpty() }.trimEnd('/').ifBlank { "/" }
                fsEntries = entries.sortedWith(compareByDescending<FsEntry> { it.isDir }.thenBy { it.name.lowercase() })
            } catch (e: Exception) {
                if (seq == fsLoadSeq) {
                    fsError = e.message
                    fsEntries = emptyList()
                }
            } finally {
                if (seq == fsLoadSeq) fsLoading = false
            }
        }
    }

    fun openFile(path: String) {
        val c = client ?: return
        openFile = path
        openFileText = ""
        openFileImage = null
        openFileBinary = false
        openFileMarkdown = false
        openFileMime = ""
        openFileMediaUri = null
        openFileMediaKind = null
        openFileBytesSize = 0L
        openFileLoading = true
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) { c.readFileRaw(path) }
                openFileMime = file.mime
                openFileBytesSize = file.bytes.size.toLong()
                val lower = path.substringAfterLast('.', "").lowercase()
                val mediaKind = when {
                    file.mime.startsWith("video/") || lower in VIDEO_EXTS -> "video"
                    file.mime.startsWith("audio/") || lower in AUDIO_EXTS -> "audio"
                    else -> null
                }
                when {
                    mediaKind != null -> {
                        val uri = withContext(Dispatchers.IO) { writeMediaCache(path, file.bytes, lower) }
                        if (uri != null) {
                            openFileMediaKind = mediaKind
                            openFileMediaUri = uri
                        } else {
                            openFileBinary = true
                            openFileText = "无法准备${if (mediaKind == "video") "视频" else "音频"}预览（${file.bytes.size / 1024} KB）"
                        }
                    }
                    file.mime.startsWith("image/") || lower in IMAGE_EXTS -> {
                        val bmp = withContext(Dispatchers.Default) { decodeBitmapCapped(file.bytes, 4096) }
                        if (bmp != null) {
                            openFileImage = bmp
                        } else {
                            openFileBinary = true
                            openFileText = "图片无法解码（${file.bytes.size / 1024} KB）"
                        }
                    }
                    looksBinary(file.bytes) -> {
                        openFileBinary = true
                        openFileText = "二进制文件（${file.bytes.size / 1024} KB）\nMIME: ${file.mime.ifBlank { "unknown" }}\n\n暂不支持预览，可在上级目录查看文件信息。"
                    }
                    else -> {
                        val text = withContext(Dispatchers.Default) { decodeText(file.bytes) }
                        openFileMarkdown = lower in setOf("md", "markdown", "mdx")
                        openFileText = if (text.length > 400_000) {
                            text.take(400_000) + "\n\n…（文件过大，已截断）"
                        } else {
                            text
                        }
                    }
                }
            } catch (e: Exception) {
                openFileText = "读取失败：" + (e.message ?: "")
            } finally {
                openFileLoading = false
            }
        }
    }

    fun closeFile() {
        openFile = null
        openFileText = ""
        openFileImage = null
        openFileBinary = false
        openFileMarkdown = false
        openFileMime = ""
        openFileMediaUri = null
        openFileMediaKind = null
        openFileBytesSize = 0L
    }

    /** 按 max 边长采样解码，避免 4K 图片直接把内存打爆。 */
    private fun decodeBitmapCapped(bytes: ByteArray, max: Int): android.graphics.Bitmap? {
        val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > max || bounds.outHeight / sample > max) sample *= 2
        val opts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
        return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun writeMediaCache(path: String, bytes: ByteArray, ext: String): android.net.Uri? {
        return runCatching {
            val dir = java.io.File(getApplication<Application>().cacheDir, "media-preview")
            if (!dir.exists()) dir.mkdirs()
            // 清理旧预览，避免缓存膨胀
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(6)?.forEach { it.delete() }
            val safeExt = ext.ifBlank { "bin" }.take(10).filter { it.isLetterOrDigit() }
            val name = "preview_${path.hashCode()}_${System.currentTimeMillis()}.$safeExt"
            val f = java.io.File(dir, name)
            f.writeBytes(bytes)
            android.net.Uri.fromFile(f)
        }.getOrNull()
    }

    private companion object {
        val VIDEO_EXTS = setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "3gp", "ts")
        val AUDIO_EXTS = setOf("mp3", "wav", "m4a", "aac", "ogg", "flac", "opus", "oga", "wma")
        val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "heic", "heif", "svg")
    }

    // =========================================================================
    // 终端 PTY
    // =========================================================================

    fun loadPtys() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                ptys = withContext(Dispatchers.IO) { c.ptyList() }
            } catch (e: Exception) {
                ptyError = e.message
            }
        }
    }

    // ---- 本机 Termux 终端 -----------------------------------------------
    /**
     * Abre la vista del terminal local. El proceso y el emulator viven en el
     * ViewModel: rotar la pantalla o cambiar de pestaña no los destruye.
     */
    fun openLocalTerminal() {
        localTerminalOpen = true
        ensureLocalTerminal()
    }

    fun closeLocalTerminalView() {
        localTerminalOpen = false
    }

    fun ensureLocalTerminal() {
        localTerminalError = null
        if (localTerminalEmulator != null && localTerminalProcess?.isAlive == true) return
        if (localTerminalStarting) return
        val spawner = com.a2heng.opencode.mobile.LocalShellHost.spawner
        if (spawner == null) {
            localTerminalError = "本机终端不可用"
            return
        }
        localTerminalStarting = true
        viewModelScope.launch {
            val p = withContext(Dispatchers.IO) {
                runCatching { spawner.spawn() }.getOrNull()
            }
            if (p == null) {
                localTerminalStarting = false
                localTerminalError = "无法启动本机 shell（toolbox 可能还在安装）"
                return@launch
            }
            runCatching { localTerminalProcess?.destroy() }
            localTerminalProcess = p
            val emu = TerminalEmulatorFactory.create(
                looper = Looper.getMainLooper(),
                initialRows = 40,
                initialCols = 100,
                defaultForeground = Color(0xFFE6EDF3),
                defaultBackground = Color(0xFF0E1116),
                onKeyboardInput = { bytes -> writeLocalTerminal(bytes) },
                onResize = { },
            )
            localTerminalEmulator = emu
            localTerminalStarting = false
            localTerminalReader?.cancel()
            localTerminalReader = viewModelScope.launch(Dispatchers.IO) {
                val buf = ByteArray(8192)
                try {
                    while (true) {
                        val n = p.inputStream.read(buf)
                        if (n <= 0) break
                        val chunk = buf.copyOf(n)
                        withContext(Dispatchers.Main) { emu.writeInput(chunk) }
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun writeLocalTerminal(bytes: ByteArray) {
        runCatching {
            localTerminalProcess?.outputStream?.write(bytes)
            localTerminalProcess?.outputStream?.flush()
        }
    }

    fun sendLocalTerminal(text: String) = writeLocalTerminal(text.toByteArray())

    fun endLocalTerminal() {
        localTerminalReader?.cancel()
        localTerminalReader = null
        runCatching { localTerminalProcess?.destroy() }
        localTerminalProcess = null
        localTerminalEmulator = null
        localTerminalStarting = false
        localTerminalOpen = false
    }

    fun createPty() {
        val c = client ?: return
        viewModelScope.launch {
            try {
                val cwd = expandSessionDir(current?.directory)
                val p = withContext(Dispatchers.IO) { c.ptyCreate("bash", cwd) }
                if (p != null) {
                    loadPtys()
                    attachPty(p)
                }
            } catch (e: Exception) {
                ptyError = e.message
            }
        }
    }

    fun attachPty(pty: PtyInfo) {
        val c = client ?: return
        detachPty()
        ptyActive = pty
        ptyOutput = ""
        ptyError = null
        ptyConnecting = true
        // termlib（libvterm）负责真正的 VT100/xterm 解析：watch / vim / htop 才能正常显示
        val emu = TerminalEmulatorFactory.create(
            looper = Looper.getMainLooper(),
            initialRows = 40,
            initialCols = 100,
            defaultForeground = Color(0xFFE6EDF3),
            defaultBackground = Color(0xFF0E1116),
            onKeyboardInput = { bytes: ByteArray -> ptySocket?.send(ByteString.of(*bytes)) },
            onResize = { dims: TerminalDimensions ->
                android.util.Log.d("PtySize", "view rows=${dims.rows} cols=${dims.columns} w=${dims.widthPixels} h=${dims.heightPixels}")
                viewModelScope.launch(Dispatchers.IO) {
                    runCatching {
                        c.ptyResize(pty.id, dims.rows, dims.columns)
                        android.util.Log.d("PtySize", "sent resize rows=${dims.rows} cols=${dims.columns}")
                    }
                }
            },
        )
        ptyEmulator = emu
        viewModelScope.launch {
            try {
                val ticket = withContext(Dispatchers.IO) { c.ptyTicket(pty.id) }
                if (ticket == null) {
                    ptyError = "服务端没有返回终端票据"
                    ptyConnecting = false
                    return@launch
                }
                val socket = c.ptySocket(pty.id, ticket, object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) {
                        ptyConnected = true
                        ptyConnecting = false
                        // 尺寸交给 termlib：视图布局后通过 onResize 回调同步给服务端
                    }

                    override fun onMessage(webSocket: WebSocket, text: String) {
                        ptyEmulator?.writeInput(text.toByteArray())
                    }

                    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                        // 控制帧：0x00 + JSON {cursor}
                        if (bytes.size > 1 && bytes[0] == 0.toByte()) return
                        ptyEmulator?.writeInput(bytes.toByteArray())
                    }

                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        ptyConnected = false
                        ptyConnecting = false
                        ptyError = t.message ?: "终端连接失败"
                    }

                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        ptyConnected = false
                        ptyConnecting = false
                    }
                })
                ptySocket = socket
            } catch (e: Exception) {
                ptyConnecting = false
                ptyError = e.message
            }
        }
    }

    fun sendPty(text: String) {
        ptySocket?.send(text)
    }

    fun updatePtyFontSize(size: Float) {
        val next = size.coerceIn(5f, 26f)
        ptyFontSize = next
        prefs.edit().putFloat("pty_font_size", next).apply()
    }

    fun detachPty() {
        try {
            ptySocket?.close(1000, "leave")
        } catch (_: Exception) {
        }
        ptySocket = null
        ptyEmulator?.close()
        ptyEmulator = null
        ptyActive = null
        ptyConnected = false
        ptyConnecting = false
        ptyOutput = ""
    }

    fun removePty(id: String) {
        val c = client ?: return
        if (ptyActive?.id == id) detachPty()
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { c.ptyRemove(id) }
                loadPtys()
            } catch (e: Exception) {
                ptyError = e.message
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        localTerminalReader?.cancel()
        localTerminalReader = null
        runCatching { localTerminalProcess?.destroy() }
        localTerminalProcess = null
    }
}
