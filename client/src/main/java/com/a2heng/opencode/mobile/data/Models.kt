package com.a2heng.opencode.mobile.data

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

// ---------------------------------------------------------------------------
// 解析层：opencode 的 HTTP API 返回的是松散 JSON，而且版本之间字段会变。
// 这里全部用 JsonElement 手工取值，缺字段就用默认值，保证旧/新服务端都不崩。
// ---------------------------------------------------------------------------

fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content

fun JsonObject.long(key: String): Long =
    (this[key] as? JsonPrimitive)?.longOrNull ?: 0L

fun JsonObject.dbl(key: String): Double =
    (this[key] as? JsonPrimitive)?.doubleOrNull ?: 0.0

fun JsonObject.bool(key: String): Boolean =
    (this[key] as? JsonPrimitive)?.booleanOrNull ?: false

fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

fun JsonObject.arr(key: String): List<JsonElement> = (this[key] as? JsonArray)?.toList() ?: emptyList()

fun JsonObject.pretty(): String = toString()

data class Tokens(
    val input: Long = 0,
    val output: Long = 0,
    val reasoning: Long = 0,
    val cacheRead: Long = 0,
    val cacheWrite: Long = 0,
) {
    /** 命中率口径与 42003 看板一致：cache.read / (input + cache.read) */
    val hitRate: Double
        get() {
            val d = input + cacheRead
            return if (d <= 0) 0.0 else cacheRead * 100.0 / d
        }

    val outputAll: Long get() = output + reasoning

    fun plus(other: Tokens) = Tokens(
        input = input + other.input,
        output = output + other.output,
        reasoning = reasoning + other.reasoning,
        cacheRead = cacheRead + other.cacheRead,
        cacheWrite = cacheWrite + other.cacheWrite,
    )

    companion object {
        fun from(o: JsonObject?): Tokens? {
            if (o == null) return null
            val cache = o.obj("cache")
            return Tokens(
                input = o.long("input"),
                output = o.long("output"),
                reasoning = o.long("reasoning"),
                cacheRead = cache?.long("read") ?: 0,
                cacheWrite = cache?.long("write") ?: 0,
            )
        }
    }
}

data class ModelRef(
    val id: String = "",
    val providerID: String = "",
    val variant: String? = null,
) {
    val label: String get() = id.ifBlank { providerID }.ifBlank { "?" }

    fun toJson(): JsonObject = kotlinx.serialization.json.buildJsonObject {
        put("id", JsonPrimitive(id))
        put("providerID", JsonPrimitive(providerID))
        if (variant != null) put("variant", JsonPrimitive(variant))
    }

    companion object {
        fun from(e: JsonElement?): ModelRef? {
            val o = e as? JsonObject ?: return null
            val id = o.str("id") ?: o.str("modelID") ?: return null
            // 服务端会把「默认档」写成 variant: "default"，统一归一成 null
            val variant = o.str("variant")?.takeIf { it.isNotBlank() && it != "default" }
            return ModelRef(id, o.str("providerID") ?: "", variant)
        }
    }
}

/** 一页消息 + 继续往更早翻的游标。 */
data class MessagesPage(
    val messages: List<Msg>,
    val next: String?,
)

data class SessionInfo(
    val id: String = "",
    val title: String = "",
    val projectID: String = "",
    val directory: String = "",
    val agent: String = "",
    val model: ModelRef? = null,
    val cost: Double = 0.0,
    val tokens: Tokens? = null,
    val created: Long = 0,
    val updated: Long = 0,
    val busySince: Long = 0,
    val outcome: String? = null,
) {
    val busy: Boolean get() = busySince > 0
    val shortTitle: String get() = title.ifBlank { "未命名会话" }

    companion object {
        fun from(o: JsonObject): SessionInfo {
            val time = o.obj("time")
            return SessionInfo(
                id = o.str("id") ?: "",
                title = o.str("title") ?: "",
                projectID = o.str("projectID") ?: "",
                directory = o.obj("location")?.str("directory") ?: "",
                agent = o.str("agent") ?: "",
                model = ModelRef.from(o["model"]),
                cost = o.dbl("cost"),
                tokens = Tokens.from(o.obj("tokens")),
                created = time?.long("created") ?: 0,
                updated = time?.long("updated") ?: 0,
                busySince = time?.long("busy") ?: 0,
                outcome = o.str("outcome"),
            )
        }
    }
}

// ---------------------------------------------------------------------------
// 消息内容
// ---------------------------------------------------------------------------

sealed interface Part {
    data class Text(val text: String) : Part
    data class Reasoning(val text: String) : Part
    data class Tool(
        val toolID: String,
        val name: String,
        val status: String,
        val input: String,
        val output: String,
        val error: String,
        val diff: String = "",
        val images: List<String> = emptyList(),
        val payload: String = "",
        val exitCode: Long? = null,
        val truncated: Boolean = false,
    ) : Part
}

data class Msg(
    val id: String,
    val role: String, // user / assistant / system / ...
    val created: Long,
    val text: String = "",
    val parts: List<Part> = emptyList(),
    val images: List<String> = emptyList(),
    val tokens: Tokens? = null,
    val cost: Double = 0.0,
    val model: ModelRef? = null,
    val agent: String = "",
    val finish: String? = null,
    val error: String? = null,
) {
    val isUser get() = role == "user"
    val isAssistant get() = role == "assistant"
}

private fun toolContentText(state: JsonObject): String {
    val sb = StringBuilder()
    for (item in state.arr("content")) {
        val o = item as? JsonObject ?: continue
        val t = o.str("text")
        if (t != null) {
            if (sb.isNotEmpty()) sb.append('\n')
            // 单个工具输出可能很大（read 一个文件），截断避免手机内存被吃光
            sb.append(t.take(8000))
        } else {
            val uri = o.str("uri")
            if (uri != null && uri.startsWith("data:")) {
                // 图片/附件由 ChatScreen 单独渲染，这里不要重复成文字占位
                continue
            }
            val path = o.str("path") ?: o.str("filename")
            if (path != null) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(path)
            }
        }
        if (sb.length > 12000) break
    }
    return sb.toString()
}

private fun toolInputText(state: JsonObject): String {
    val input = state["input"] ?: return ""
    return when (input) {
        is JsonPrimitive -> input.content
        is JsonObject -> {
            // 常见工具优先显示最有信息量的一行
            val keys = listOf("command", "filePath", "path", "pattern", "url", "description", "prompt")
            val best = keys.firstNotNullOfOrNull { k -> input.str(k) }
            best ?: input.toString()
        }
        else -> input.toString()
    }
}

private fun parseToolImages(state: JsonObject): List<String> =
    state.arr("content").mapNotNull { item ->
        val o = item as? JsonObject ?: return@mapNotNull null
        val uri = o.str("uri") ?: return@mapNotNull null
        val mime = o.str("mime") ?: ""
        if (o.str("type") == "file" && (mime.startsWith("image/") || uri.startsWith("data:image"))) uri else null
    }.take(2)

private fun parseToolDiff(state: JsonObject): String {
    val files = state.obj("metadata")?.arr("files") ?: return ""
    return files.mapNotNull { (it as? JsonObject)?.str("patch") }.joinToString("\n")
}

private fun parsePart(e: JsonElement): Part? {
    val o = e as? JsonObject ?: return null
    return when (o.str("type")) {
        "text" -> Part.Text(o.str("text") ?: "")
        "reasoning" -> Part.Reasoning(o.str("text") ?: "")
        "tool" -> {
            val state = o.obj("state") ?: JsonObject(emptyMap())
            val meta = state.obj("metadata")
            val name = o.str("name") ?: "tool"
            val input = state.obj("input")
            Part.Tool(
                toolID = o.str("id") ?: "",
                name = name,
                status = state.str("status") ?: "unknown",
                input = toolInputText(state),
                output = toolContentText(state),
                error = state.obj("error")?.str("message") ?: state.str("error") ?: "",
                diff = parseToolDiff(state),
                images = parseToolImages(state),
                payload = if (name == "write") input?.str("content") ?: "" else "",
                exitCode = meta?.let { if ("exit" in it) it.long("exit") else null },
                truncated = meta?.bool("truncated") ?: false,
            )
        }
        else -> null
    }
}

fun parseMessage(o: JsonObject): Msg {
    val type = o.str("type") ?: "unknown"
    val time = o.obj("time")
    val created = time?.long("created") ?: 0
    return when (type) {
        "user" -> Msg(
            id = o.str("id") ?: "",
            role = "user",
            created = created,
            text = o.str("text") ?: "",
            images = o.arr("files").mapNotNull { item ->
                val f = item as? JsonObject ?: return@mapNotNull null
                val mime = f.str("mime") ?: ""
                val data = f.str("data") ?: return@mapNotNull null
                if (mime.startsWith("image/")) "data:$mime;base64,$data" else null
            },
        )
        "assistant" -> Msg(
            id = o.str("id") ?: "",
            role = "assistant",
            created = created,
            parts = o.arr("content").mapNotNull(::parsePart),
            tokens = Tokens.from(o.obj("tokens")),
            cost = o.dbl("cost"),
            model = ModelRef.from(o["model"]),
            agent = o.str("agent") ?: "",
            finish = o.str("finish"),
            error = o.obj("error")?.str("message"),
        )
        else -> Msg(
            id = o.str("id") ?: "",
            role = type,
            created = created,
            text = o.str("text") ?: o.str("summary") ?: "",
        )
    }
}

// ---------------------------------------------------------------------------
// 权限 / 表单 / 文件 / PTY
// ---------------------------------------------------------------------------

data class PermissionReq(
    val id: String,
    val sessionID: String,
    val action: String,
    val resources: List<String>,
    val message: String,
    val source: String,
)

fun parsePermission(o: JsonObject) = PermissionReq(
    id = o.str("id") ?: "",
    sessionID = o.str("sessionID") ?: "",
    action = o.str("action") ?: "",
    resources = o.arr("resources").mapNotNull { (it as? JsonPrimitive)?.content },
    message = o.str("message") ?: "",
    source = o.obj("source")?.str("type") ?: o.str("source") ?: "",
)

data class FormOption(val value: String, val label: String, val description: String)

data class FormField(
    val key: String,
    val type: String,
    val title: String,
    val description: String,
    val required: Boolean,
    val options: List<FormOption>,
    val defaultText: String?,
    val defaultBool: Boolean?,
    val placeholder: String,
)

data class FormInfo(
    val id: String,
    val sessionID: String,
    val title: String,
    val fields: List<FormField>,
)

fun parseForm(o: JsonObject) = FormInfo(
    id = o.str("id") ?: "",
    sessionID = o.str("sessionID") ?: "",
    title = o.str("title") ?: "需要你的选择",
    fields = o.arr("fields").mapNotNull { item ->
        val f = item as? JsonObject ?: return@mapNotNull null
        FormField(
            key = f.str("key") ?: return@mapNotNull null,
            type = f.str("type") ?: "string",
            title = f.str("title") ?: f.str("key") ?: "",
            description = f.str("description") ?: "",
            required = f.bool("required"),
            options = f.arr("options").mapNotNull { opt ->
                val obj = opt as? JsonObject ?: return@mapNotNull null
                FormOption(
                    value = obj.str("value") ?: return@mapNotNull null,
                    label = obj.str("label") ?: obj.str("value") ?: "",
                    description = obj.str("description") ?: "",
                )
            },
            defaultText = (f["default"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content,
            defaultBool = (f["default"] as? JsonPrimitive)?.booleanOrNull,
            placeholder = f.str("placeholder") ?: "",
        )
    },
)

data class FsEntry(val path: String, val type: String) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/').ifBlank { path }
    val isDir: Boolean get() = type == "directory"
}

/** `/api/fs/read` 返回的原始内容（服务端是流式字节 + MIME，不是 JSON）。 */
data class FsFile(val mime: String, val bytes: ByteArray)

data class Queued(val id: String, val text: String, val delivery: String = "queue")

data class CommandInfo(val name: String, val description: String)

data class UpdateInfo(val tag: String, val pageUrl: String, val apkUrl: String?)

data class ShellInfo(
    val id: String,
    val command: String,
    val status: String,
    val sessionID: String = "",
    // 服务端 time.started（毫秒时间戳），用来在悬浮球里显示已经跑了多久
    val started: Long = 0L,
)

/** 通过 prompt 的 files 字段发送的图片（inline base64）。 */
data class Attachment(val name: String, val mime: String, val base64: String)

/** 从系统分享面板进来的一次性内容。 */
data class SharedPayload(val text: String?, val image: Attachment?)

data class PtyInfo(
    val id: String,
    val title: String,
    val command: String,
    val status: String,
    val pid: Long,
    val cwd: String,
    val exitCode: Long,
)

fun parsePty(o: JsonObject) = PtyInfo(
    id = o.str("id") ?: "",
    title = o.str("title") ?: o.str("command") ?: "terminal",
    command = o.str("command") ?: "bash",
    status = o.str("status") ?: "running",
    pid = o.long("pid"),
    cwd = o.str("cwd") ?: "",
    exitCode = o.long("exitCode"),
)

// ---------------------------------------------------------------------------
// 缓存统计（App 内直连计算，规则与 42003 看板一致）
// ---------------------------------------------------------------------------

data class CacheStep(
    val id: String,
    val time: Long,
    val tokens: Tokens,
    val cost: Double,
    val model: String,
) {
    val rate: Double get() = tokens.hitRate
}

data class SessionCache(
    val session: SessionInfo,
    val steps: List<CacheStep>,
    /** 会话级聚合（服务端 session.tokens）；为空时才退回用 steps 求和。 */
    val aggregate: Tokens? = null,
) {
    val totals: Tokens
        get() = aggregate ?: steps.fold(Tokens()) { acc, s -> acc.plus(s.tokens) }
    val cost: Double get() = if (session.cost > 0) session.cost else steps.sumOf { it.cost }
    val rate: Double get() = totals.hitRate
    val lastRate: Double get() = steps.lastOrNull()?.rate ?: 0.0
}

fun stepsOf(messages: List<Msg>): List<CacheStep> =
    messages.filter { it.isAssistant && it.tokens != null }
        .map {
            CacheStep(
                id = it.id,
                time = it.created,
                tokens = it.tokens!!,
                cost = it.cost,
                model = it.model?.label ?: "",
            )
        }
        .sortedBy { it.time }

fun formatTokens(n: Long): String = when {
    n >= 1_000_000 -> String.format("%.2fM", n / 1_000_000.0)
    n >= 1_000 -> String.format("%.1fk", n / 1_000.0)
    else -> n.toString()
}

fun formatCost(c: Double): String = when {
    c <= 0.0 -> "$0"
    c < 0.01 -> String.format("$%.4f", c)
    else -> String.format("$%.2f", c)
}

fun rateLabel(rate: Double): String = String.format("%.1f%%", rate)

fun rateColorHint(rate: Double): String = when {
    rate >= 95 -> "很好"
    rate >= 90 -> "较好"
    rate >= 80 -> "一般"
    rate >= 70 -> "偏低"
    rate >= 50 -> "差"
    else -> "很差"
}

private val ANSI_CSI = Regex("\u001B\\[[0-9;?]*[ -/]*[@-~]")
private val ANSI_OSC = Regex("\u001B\\][^\u0007]*\u0007")

/** 终端输出里的 ANSI 转义序列在普通 Text 里会变成乱码，显示前清掉。 */
fun stripAnsi(input: String): String =
    ANSI_CSI.replace(ANSI_OSC.replace(input, ""), "").replace("\r\n", "\n").replace('\r', '\n')

// ---------------------------------------------------------------------------
// VCS / Git（/api/vcs*，location 作用域）
// ---------------------------------------------------------------------------

data class VcsBranch(val current: String = "", val default: String = "")

data class VcsInfo(
    val provider: String = "",
    val branch: VcsBranch = VcsBranch(),
) {
    val isGit: Boolean get() = provider.equals("git", ignoreCase = true)
    val label: String get() = branch.current.ifBlank { branch.default }.ifBlank { "—" }
}

data class VcsFileStatus(
    val file: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: String = "", // added|deleted|modified
)

data class VcsDiffFile(
    val file: String = "",
    val patch: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val status: String = "",
    val more: Boolean = false,
)

private fun JsonObject.vcsDataObj(): JsonObject =
    // opencode server 常返回 {location, data:{...}}；也兼容直接返回对象
    (this["data"] as? JsonObject) ?: this

fun parseVcsInfo(o: JsonObject?): VcsInfo {
    if (o == null) return VcsInfo()
    val d = o.vcsDataObj()
    val b = d.obj("branch")
    return VcsInfo(
        provider = d.str("provider") ?: o.str("provider") ?: "",
        branch = VcsBranch(
            current = b?.str("current") ?: d.str("current") ?: "",
            default = b?.str("default") ?: d.str("default") ?: "",
        ),
    )
}

fun parseVcsFileStatus(o: JsonObject): VcsFileStatus = VcsFileStatus(
    file = o.str("file") ?: "",
    additions = (o.long("additions")).toInt(),
    deletions = (o.long("deletions")).toInt(),
    status = o.str("status") ?: "",
)

fun parseVcsDiffFile(o: JsonObject): VcsDiffFile = VcsDiffFile(
    file = o.str("file") ?: "",
    patch = o.str("patch") ?: "",
    additions = (o.long("additions")).toInt(),
    deletions = (o.long("deletions")).toInt(),
    status = o.str("status") ?: "",
    more = o.bool("more"),
)

/**
 * 文本解码：先严格 UTF-8，失败再按国标 GB18030（兼容 GBK/GB2312），最后宽松 UTF-8 兜底。
 * 服务端 `/api/fs/read` 给的是原始字节，所以这里能自己判断编码。
 */
fun decodeText(bytes: ByteArray): String {
    val clean = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
        bytes.copyOfRange(3, bytes.size)
    } else {
        bytes
    }
    val strict = Charsets.UTF_8.newDecoder()
        .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
        .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
    return try {
        strict.decode(java.nio.ByteBuffer.wrap(clean)).toString()
    } catch (_: Exception) {
        try {
            String(clean, java.nio.charset.Charset.forName("GB18030"))
        } catch (_: Exception) {
            String(clean, Charsets.UTF_8)
        }
    }
}

/** 前 8KB 里出现 NUL 就当成二进制，不硬当文本渲染。 */
fun looksBinary(bytes: ByteArray): Boolean {
    val n = minOf(bytes.size, 8192)
    for (i in 0 until n) if (bytes[i] == 0.toByte()) return true
    return false
}
