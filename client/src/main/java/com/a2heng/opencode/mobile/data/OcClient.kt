package com.a2heng.opencode.mobile.data

import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.channels.trySendBlocking
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : Exception(message)

/**
 * opencode HTTP API 的极薄封装。
 *
 * 认证是 Basic：用户名固定 `opencode`，密码就是服务端 `OPENCODE_PASSWORD`。
 * 服务端没设密码时不会带 Authorization 头。
 */
class OcClient(rawBase: String, private val password: String) {

    val base: String = normalize(rawBase)

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val authHeader: String? =
        if (password.isNotEmpty()) Credentials.basic("opencode", password) else null

    private fun request(path: String): Request.Builder {
        val b = Request.Builder().url(base + path)
        authHeader?.let { b.header("Authorization", it) }
        return b
    }

    suspend fun get(path: String): JsonElement {
        val resp = http.newCall(request(path).get().build()).execute()
        return resp.use { readJson(it) }
    }

    suspend fun post(path: String, body: JsonElement? = null): JsonElement? {
        val payload = (body ?: JsonObject(emptyMap())).toString()
        val req = request(path)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        return resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) null else json.parseToJsonElement(text)
        }
    }

    suspend fun put(path: String, body: JsonElement): JsonElement? {
        val req = request(path)
            .put(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        return resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) null else json.parseToJsonElement(text)
        }
    }

    suspend fun patch(path: String, body: JsonElement): JsonElement? {
        val req = request(path)
            .patch(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        return resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val text = r.body?.string().orEmpty()
            if (text.isBlank()) null else json.parseToJsonElement(text)
        }
    }

    suspend fun delete(path: String) {
        val resp = http.newCall(request(path).delete().build()).execute()
        resp.use { r -> if (!r.isSuccessful) throw errorOf(r) }
    }

    private fun readJson(resp: Response): JsonElement {
        if (!resp.isSuccessful) throw errorOf(resp)
        val text = resp.body?.string().orEmpty()
        if (text.isBlank()) return JsonObject(emptyMap())
        return json.parseToJsonElement(text)
    }

    private fun errorOf(resp: Response): ApiException {
        val body = try {
            resp.body?.string().orEmpty()
        } catch (_: Exception) {
            ""
        }
        val detail = try {
            val o = json.parseToJsonElement(body) as? JsonObject
            o?.str("message") ?: o?.str("_tag") ?: body.take(200)
        } catch (_: Exception) {
            body.take(200)
        }
        return ApiException(resp.code, "HTTP ${resp.code}: $detail")
    }

    // ---- 便捷接口 -----------------------------------------------------------

    suspend fun sessionList(): List<SessionInfo> =
        unwrapArray(get("/api/session")).mapNotNull { (it as? JsonObject)?.let(SessionInfo::from) }

    /** 服务端的根目录（/api/location 的 directory），用来显示 ~ 相对路径。 */
    suspend fun locationRoot(): String =
        normPath((get("/api/location") as? JsonObject)?.str("directory") ?: "")

    /** 真正在跑的会话：/api/session/active 返回 { data: { ses_x: {type:"running"} } } */
    suspend fun activeSessionIds(): Set<String> {
        val body = get("/api/session/active") as? JsonObject ?: return emptySet()
        val data = body["data"] as? JsonObject ?: return emptySet()
        return data.keys
    }

    suspend fun session(id: String): SessionInfo? =
        unwrapObject(get("/api/session/$id"))?.let(SessionInfo::from)

    suspend fun messages(sessionID: String): List<Msg> =
        messagesPage(sessionID, limit = 200).messages

    /**
     * 分页读取消息（对齐官方前端的做法）：
     * - 首屏 `order=desc, limit=20/40` 拿最新一页，响应里的 cursor.next 用来继续往更早翻；
     * - 翻旧消息只带 cursor，不带 order。
     */
    suspend fun messagesPage(
        sessionID: String,
        limit: Int = 40,
        order: String = "desc",
        cursor: String? = null,
    ): MessagesPage {
        val query = buildString {
            append("?limit=").append(limit)
            if (cursor != null) {
                append("&cursor=").append(java.net.URLEncoder.encode(cursor, "UTF-8"))
            } else {
                append("&order=").append(order)
            }
        }
        val body = get("/api/session/$sessionID/message$query") as? JsonObject
            ?: return MessagesPage(emptyList(), null)
        val data = body.arr("data").mapNotNull { (it as? JsonObject)?.let(::parseMessage) }
        val cursorObj = body.obj("cursor")
        return MessagesPage(data, cursorObj?.str("next"))
    }

    /** 完整历史：从最新一页开始，按 cursor.next 一页页往前翻（上限 20 页，防死循环）。 */
    suspend fun allMessages(sessionID: String, pageSize: Int = 200): List<Msg> {
        val out = mutableListOf<Msg>()
        var cursor: String? = null
        var pages = 0
        while (pages < 20) {
            val page = messagesPage(sessionID, limit = pageSize, cursor = cursor)
            out += page.messages
            cursor = page.next ?: break
            pages++
        }
        return out
    }

    suspend fun prompt(
        sessionID: String,
        text: String,
        delivery: String? = null,
        attachments: List<Attachment> = emptyList(),
    ) {
        val body = buildJsonObject {
            put("text", JsonPrimitive(text))
            if (delivery != null) put("delivery", JsonPrimitive(delivery))
            if (attachments.isNotEmpty()) {
                put(
                    "files",
                    buildJsonArray {
                        attachments.forEach { a ->
                            add(
                                buildJsonObject {
                                    // v2.2.5 实际校验的是 files[].uri（data URI），
                                    // openapi.json 里的 data/source 反而会报 Missing key uri
                                    put("uri", JsonPrimitive("data:${a.mime};base64,${a.base64}"))
                                    put("mime", JsonPrimitive(a.mime))
                                    put("name", JsonPrimitive(a.name))
                                },
                            )
                        }
                    },
                )
            }
        }
        post("/api/session/$sessionID/prompt", body)
    }

    suspend fun inbox(sessionID: String): List<JsonObject> =
        unwrapArray(get("/api/session/$sessionID/inbox")).mapNotNull { it as? JsonObject }

    suspend fun commands(directory: String? = null): List<CommandInfo> {
        val query = if (directory.isNullOrBlank()) {
            ""
        } else {
            "?location[directory]=" + urlEncode(directory)
        }
        return unwrapArray(get("/api/command$query")).mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            val name = o.str("name") ?: return@mapNotNull null
            CommandInfo(name, o.str("description") ?: "")
        }
    }

    suspend fun compact(sessionID: String) {
        post("/api/session/$sessionID/compact")
    }

    /** 等价于原版 Ctrl+B：把当前阻塞执行的 shell 移到后台观察。 */
    suspend fun background(sessionID: String) {
        post("/api/session/$sessionID/background")
    }

    suspend fun reloadConfig() {
        post("/api/location/reload")
    }

    private fun locationQuery(directory: String?): String =
        if (directory.isNullOrBlank()) "" else
            "?location%5Bdirectory%5D=" + java.net.URLEncoder.encode(directory, "UTF-8")

    // ---- VCS / Git（location 作用域）----------------------------------------
    suspend fun vcsInfo(directory: String): VcsInfo =
        parseVcsInfo(get("/api/vcs" + locationQuery(directory)) as? JsonObject)

    suspend fun vcsStatus(directory: String): List<VcsFileStatus> =
        unwrapArray(get("/api/vcs/status" + locationQuery(directory))).mapNotNull { item ->
            (item as? JsonObject)?.let { parseVcsFileStatus(it) }
        }

    suspend fun vcsBase(directory: String): JsonObject? =
        unwrapObject(get("/api/vcs/base" + locationQuery(directory)))

    suspend fun vcsDiff(directory: String, mode: String = "working", base: String? = null): List<VcsDiffFile> {
        val params = buildList {
            add("mode=" + urlEncode(mode))
            if (!base.isNullOrBlank()) add("base=" + urlEncode(base))
        }.joinToString("&")
        val q = locationQuery(directory).let { if (it.isBlank()) "?$params" else "$it&$params" }
        return unwrapArray(get("/api/vcs/diff$q")).mapNotNull { item ->
            (item as? JsonObject)?.let { parseVcsDiffFile(it) }
        }
    }

    suspend fun shells(directory: String? = null): List<ShellInfo> =
        unwrapArray(get("/api/shell" + locationQuery(directory))).mapNotNull { item ->
            val o = item as? JsonObject ?: return@mapNotNull null
            ShellInfo(
                id = o.str("id") ?: return@mapNotNull null,
                command = o.str("command") ?: "",
                status = o.str("status") ?: "",
                // ShellTool 会把来源会话放在 metadata.sessionID，用它在客户端按会话过滤悬浮球
                sessionID = o.obj("metadata")?.str("sessionID") ?: "",
                // 有的版本给 number，有的给 double，统一取 long
                started = o.obj("time")?.long("started") ?: 0L,
            )
        }

    suspend fun shellOutput(id: String, directory: String? = null): String {
        val e = get("/api/shell/$id/output" + locationQuery(directory))
        if (e !is JsonObject) return if (e is JsonPrimitive) e.content else e.toString()
        // 真实结构：{ location, data: { output, cursor, size, truncated } }
        // 兼容 data 直接是字符串 / 数组的旧版本，否则会把整个 JSON 当成输出显示。
        return when (val data = e["data"]) {
            is JsonPrimitive -> data.content
            is JsonObject -> data.str("output") ?: ""
            is JsonArray -> data.mapNotNull { (it as? JsonObject)?.str("output") }.joinToString("")
            else -> e.str("output") ?: ""
        }
    }

    suspend fun stopShell(id: String, directory: String? = null) {
        delete("/api/shell/$id" + locationQuery(directory))
    }

    suspend fun runCommand(
        sessionID: String,
        name: String,
        text: String,
        delivery: String? = null,
        attachments: List<Attachment> = emptyList(),
    ) {
        val body = buildJsonObject {
            put("name", JsonPrimitive(name))
            put("text", JsonPrimitive(text))
            if (delivery != null) put("delivery", JsonPrimitive(delivery))
            if (attachments.isNotEmpty()) {
                put(
                    "files",
                    buildJsonArray {
                        attachments.forEach { a ->
                            add(
                                buildJsonObject {
                                    put("uri", JsonPrimitive("data:${a.mime};base64,${a.base64}"))
                                    put("mime", JsonPrimitive(a.mime))
                                    put("name", JsonPrimitive(a.name))
                                },
                            )
                        }
                    },
                )
            }
        }
        post("/api/session/$sessionID/command", body)
    }

    suspend fun cancelInbox(sessionID: String, inboxID: String) {
        delete("/api/session/$sessionID/inbox/$inboxID")
    }

    suspend fun runShell(command: String, cwd: String? = null) {
        val body = buildJsonObject {
            put("command", JsonPrimitive(command))
            if (!cwd.isNullOrBlank()) put("cwd", JsonPrimitive(cwd))
        }
        post("/api/shell", body)
    }

    suspend fun interrupt(sessionID: String) {
        post("/api/session/$sessionID/interrupt")
    }

    suspend fun stageRevert(sessionID: String, messageID: String, files: Boolean) {
        post(
            "/api/session/$sessionID/revert/stage",
            buildJsonObject {
                put("messageID", JsonPrimitive(messageID))
                put("files", JsonPrimitive(files))
            },
        )
    }

    suspend fun commitRevert(sessionID: String) {
        post("/api/session/$sessionID/revert/commit")
    }

    suspend fun clearRevert(sessionID: String) {
        delete("/api/session/$sessionID/revert")
    }

    suspend fun createSession(directory: String? = null): SessionInfo? {
        val body = buildJsonObject {
            if (!directory.isNullOrBlank()) {
                put("location", buildJsonObject { put("directory", JsonPrimitive(directory)) })
            }
        }
        return unwrapObject(post("/api/session", body))?.let(SessionInfo::from)
    }

    suspend fun deleteSession(id: String) = delete("/api/session/$id")

    suspend fun renameSession(id: String, title: String) {
        patch("/api/session/$id", buildJsonObject { put("title", JsonPrimitive(title)) })
    }

    suspend fun setModel(sessionID: String, model: ModelRef) {
        post(
            "/api/session/$sessionID/model",
            buildJsonObject { put("model", model.toJson()) },
        )
    }

    suspend fun setAgent(sessionID: String, agent: String) {
        post(
            "/api/session/$sessionID/agent",
            buildJsonObject { put("agent", JsonPrimitive(agent)) },
        )
    }

    suspend fun agents(): List<JsonObject> =
        unwrapArray(get("/api/agent")).mapNotNull { it as? JsonObject }

    suspend fun models(): List<JsonObject> =
        unwrapArray(get("/api/model")).mapNotNull { it as? JsonObject }

    suspend fun defaultModel(): ModelRef? {
        val body = get("/api/model/default") as? JsonObject ?: return null
        val data = body["data"] as? JsonObject ?: return null
        val id = data.str("id") ?: return null
        return ModelRef(id, data.str("providerID") ?: "")
    }

    suspend fun permissions(): List<PermissionReq> =
        unwrapArray(get("/api/permission/request")).mapNotNull {
            (it as? JsonObject)?.let(::parsePermission)
        }

    suspend fun replyPermission(sessionID: String, requestID: String, decision: String) {
        post(
            "/api/session/$sessionID/permission/$requestID/reply",
            buildJsonObject { put("decision", JsonPrimitive(decision)) },
        )
    }

    suspend fun forms(sessionID: String): List<FormInfo> =
        unwrapArray(get("/api/session/$sessionID/form")).mapNotNull {
            (it as? JsonObject)?.let(::parseForm)
        }

    suspend fun replyForm(sessionID: String, formID: String, answer: JsonElement) {
        post(
            "/api/session/$sessionID/form/$formID/reply",
            buildJsonObject { put("answer", answer) },
        )
    }

    /**
     * 列目录。传绝对路径时把它当成 location（`location[directory]`），
     * 这样 `/tmp/opencode` 这种项目外的目录也能列，而且返回的 path 是相对该目录的。
     */
    suspend fun listDir(dir: String?): Pair<String, List<FsEntry>> {
        val q = if (dir.isNullOrBlank()) "" else "?location%5Bdirectory%5D=" + urlEncode(dir)
        val o = get("/api/fs/list$q") as? JsonObject ?: JsonObject(emptyMap())
        val location = normPath(o.obj("location")?.str("directory") ?: dir.orEmpty())
        val entries = o.arr("data").mapNotNull { e ->
            val ob = e as? JsonObject ?: return@mapNotNull null
            FsEntry(normPath(ob.str("path") ?: return@mapNotNull null), ob.str("type") ?: "file")
        }
        return location to entries
    }

    suspend fun readFile(path: String): String = decodeText(readFileRaw(path).bytes)

    /**
     * 原始字节 + MIME：文本自己按 UTF-8/GB18030 解码，图片直接交给 BitmapFactory。
     * `/api/fs/read` 是 location 作用域的：把文件父目录当 location、文件名当相对路径，
     * 否则项目外的文件会 404（也避免 URL 里的 `..` 被规范化掉）。
     */
    suspend fun readFileRaw(path: String): FsFile {
        val dir = if (path.startsWith("/")) {
            path.substringBeforeLast('/', "").ifBlank { "/" }
        } else {
            path.substringBeforeLast('/', "")
        }
        val name = path.substringAfterLast('/')
        val q = if (dir.isBlank()) "" else "?location%5Bdirectory%5D=" + urlEncode(dir)
        val req = request("/api/fs/read/" + urlEncode(name) + q).get().build()
        val resp = http.newCall(req).execute()
        return resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val mime = r.header("Content-Type")?.substringBefore(';')?.trim().orEmpty()
            FsFile(mime, r.body?.bytes() ?: ByteArray(0))
        }
    }

    suspend fun ptyList(): List<PtyInfo> =
        unwrapArray(get("/api/pty")).mapNotNull { (it as? JsonObject)?.let(::parsePty) }

    /**
     * 创建 PTY。`command = null` 时不传 command，让服务端用**自己的默认 shell**。
     *
     * 不要写死 "bash"：Windows 服务端的 PATH 里通常没有 bash，
     * bun-pty 会直接 500；而服务端默认 shell 会解析成 Git Bash / cmd.exe。
     * Adia 本机终端不走这里（宿主注入 Termux shell），这里只服务远端 PTY。
     */
    suspend fun ptyCreate(command: String? = null, cwd: String? = null): PtyInfo? {
        val body = buildJsonObject {
            if (!command.isNullOrBlank()) put("command", JsonPrimitive(command))
            put("title", JsonPrimitive("手机终端"))
            if (!cwd.isNullOrBlank()) put("cwd", JsonPrimitive(cwd))
        }
        return unwrapObject(post("/api/pty", body))?.let(::parsePty)
    }

    suspend fun ptyRemove(id: String) = delete("/api/pty/$id")

    suspend fun ptyResize(id: String, rows: Int, cols: Int) {
        val body = buildJsonObject {
            put(
                "size",
                buildJsonObject {
                    put("rows", JsonPrimitive(rows))
                    put("cols", JsonPrimitive(cols))
                },
            )
        }
        // 这个接口只认 PUT；PATCH 会 404（之前 PTY 宽度一直没同步）
        put("/api/pty/$id", body)
    }

    /** 申请 PTY WebSocket 票据（服务端强制要求 x-opencode-ticket: 1）。 */
    suspend fun ptyTicket(id: String): String? {
        val req = request("/api/pty/$id/connect-token")
            .header("x-opencode-ticket", "1")
            .post(JsonObject(emptyMap()).toString().toRequestBody("application/json".toMediaType()))
            .build()
        val resp = http.newCall(req).execute()
        return resp.use { r ->
            if (!r.isSuccessful) throw errorOf(r)
            val o = json.parseToJsonElement(r.body?.string().orEmpty()) as? JsonObject
            o?.obj("data")?.str("ticket")
        }
    }

    fun ptySocket(id: String, ticket: String, listener: WebSocketListener): WebSocket {
        val wsUrl = base.replaceFirst("http://", "ws://").replaceFirst("https://", "wss://") +
            "/api/pty/$id/connect?ticket=" + urlEncode(ticket)
        return http.newWebSocket(Request.Builder().url(wsUrl).build(), listener)
    }

    // ---- 事件流（SSE） ------------------------------------------------------

    /**
     * 订阅 /api/event。上游断开或网络抖动时由调用方重新收集（这里只抛结束）。
     * 每行形如 `data: {...}`，另外有心跳注释行 `: heartbeat`。
     */
    fun events(): Flow<JsonObject> = callbackFlow {
        val req = request("/api/event").header("Accept", "text/event-stream").get().build()
        val call = http.newCall(req)
        val thread = Thread {
            try {
                call.execute().use { resp ->
                    if (!resp.isSuccessful) {
                        trySendBlocking(JsonObject(mapOf("type" to JsonPrimitive("__error"))))
                        close()
                        return@use
                    }
                    val source = resp.body?.source() ?: return@use
                    while (!source.exhausted()) {
                        val line = source.readUtf8Line() ?: break
                        if (!line.startsWith("data:")) continue
                        val raw = line.removePrefix("data:").trim()
                        if (raw.isEmpty()) continue
                        try {
                            val o = json.parseToJsonElement(raw) as? JsonObject ?: continue
                            if (trySendBlocking(o).isFailure) break
                        } catch (_: Exception) {
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                close()
            }
        }
        thread.isDaemon = true
        thread.name = "opencode-events"
        thread.start()
        awaitClose {
            try {
                call.cancel()
            } catch (_: Exception) {
            }
            thread.interrupt()
        }
    }

    suspend fun ping(): JsonElement = get("/api/info")

    companion object {
        fun normalize(input: String): String {
            var s = input.trim()
            if (s.isEmpty()) return "http://127.0.0.1:42000"
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            return s.trimEnd('/')
        }

        fun urlEncode(s: String): String =
            java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20")

        fun unwrapArray(e: JsonElement): List<JsonElement> = when (e) {
            is JsonArray -> e.toList()
            is JsonObject -> (e["data"] as? JsonArray)?.toList() ?: emptyList()
            else -> emptyList()
        }

        fun unwrapObject(e: JsonElement?): JsonObject? = when (e) {
            is JsonObject -> (e["data"] as? JsonObject) ?: e
            else -> null
        }
    }
}
