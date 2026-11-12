package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ---------------------------------------------------------------------------
// 一个够用的 Markdown 渲染器（不引第三方库）：
//   · 围栏代码块 ```lang ... ``` 单独成块，等宽 + 可横向滚动
//   · 行内 `code` / **粗体** / *斜体* / [链接](url) / # 标题
//   · 其余按纯文本渲染，保留换行
// ---------------------------------------------------------------------------

private sealed interface Block {
    data class Text(val text: String) : Block
    data class Code(val lang: String, val code: String) : Block
}

private sealed interface Line {
    data class Heading(val level: Int, val text: String) : Line
    data class Quote(val text: String) : Line
    data class ListItem(val indent: Int, val marker: String, val text: String) : Line
    data class Para(val text: String) : Line
    data class Table(
        val headers: List<String>,
        val rows: List<List<String>>,
        val aligns: List<Int>,
    ) : Line
    data object Rule : Line
    data object Gap : Line
}

private val headingRe = Regex("^(#{1,6})\\s+(.*)$")
private val quoteRe = Regex("^\\s*>\\s?(.*)$")
private val listRe = Regex("^(\\s*)([-*+]|\\d+[.)])\\s+(.*)$")
private val ruleRe = Regex("^\\s*(---+|\\*\\*\\*+|___+)\\s*$")

private fun splitRow(line: String): List<String> =
    line.trim().trim('|').split('|').map { it.trim() }

private fun isTableSeparator(line: String): Boolean {
    val t = line.trim()
    if (!t.contains('-')) return false
    val cells = t.trim('|').split('|')
    return cells.isNotEmpty() && cells.all { Regex("^:?-{2,}:?$").matches(it.trim()) }
}

private fun tableAligns(line: String): List<Int> =
    splitRow(line).map { cell ->
        when {
            cell.startsWith(":") && cell.endsWith(":") -> 0
            cell.endsWith(":") -> 1
            else -> -1
        }
    }

private fun parseLines(text: String): List<Line> {
    val lines = text.split('\n')
    val out = mutableListOf<Line>()
    val para = mutableListOf<String>()
    fun flush() {
        if (para.isNotEmpty()) {
            out += Line.Para(para.joinToString("\n"))
            para.clear()
        }
    }
    var i = 0
    while (i < lines.size) {
        val line = lines[i].trimEnd()
        // 竖线表格：| a | b | + | --- | --- |
        if (line.trimStart().startsWith("|") && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
            flush()
            val headers = splitRow(line)
            val aligns = tableAligns(lines[i + 1])
            i += 2
            val rows = mutableListOf<List<String>>()
            while (i < lines.size && lines[i].trimStart().startsWith("|")) {
                rows += splitRow(lines[i])
                i++
            }
            out += Line.Table(headers, rows, aligns)
            continue
        }
        val h = headingRe.find(line)
        if (h != null) {
            flush()
            out += Line.Heading(h.groupValues[1].length, h.groupValues[2].trim())
            i++
            continue
        }
        if (line.isBlank()) {
            flush()
            out += Line.Gap
            i++
            continue
        }
        if (ruleRe.matches(line)) {
            flush()
            out += Line.Rule
            i++
            continue
        }
        val q = quoteRe.find(line)
        if (q != null) {
            flush()
            out += Line.Quote(q.groupValues[1])
            i++
            continue
        }
        val l = listRe.find(line)
        if (l != null) {
            flush()
            val indent = l.groupValues[1].length / 2
            out += Line.ListItem(indent, l.groupValues[2], l.groupValues[3])
            i++
            continue
        }
        para += line
        i++
    }
    flush()
    return out
}

private fun splitBlocks(source: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val buffer = StringBuilder()
    var code: StringBuilder? = null
    var lang = ""

    fun flushText() {
        val t = buffer.toString().trim('\n')
        if (t.isNotBlank()) blocks += Block.Text(t)
        buffer.clear()
    }

    for (line in source.split('\n')) {
        val fence = line.trimStart()
        if (fence.startsWith("```")) {
            if (code == null) {
                flushText()
                code = StringBuilder()
                lang = fence.removePrefix("```").trim()
            } else {
                blocks += Block.Code(lang, code.toString().trimEnd('\n'))
                code = null
                lang = ""
            }
            continue
        }
        if (code != null) {
            code.append(line).append('\n')
        } else {
            buffer.append(line).append('\n')
        }
    }
    if (code != null) {
        // 没闭合的代码块也按代码显示，总比吞掉好
        blocks += Block.Code(lang, code.toString().trimEnd('\n'))
    } else {
        flushText()
    }
    return blocks
}

private val inlinePattern = Regex(
    "(`[^`\\n]+`)|(\\*\\*[^*\\n]+\\*\\*)|(~~[^~\\n]+~~)|(\\*[^*\\n]+\\*)|(!?\\[[^\\]\\n]*\\]\\([^)\\n]+\\))",
)

private val HTTP_LINK_RE = Regex("""^https?://""", RegexOption.IGNORE_CASE)

private fun isHttpLink(href: String): Boolean = HTTP_LINK_RE.containsMatchIn(href)

private fun isLikelyFileHref(href: String): Boolean {
    if (href.isBlank()) return false
    if (href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) return false
    if (isHttpLink(href)) return false
    if (href.startsWith("file://")) return true
    if (href.startsWith("~/") || href.startsWith("/") || href.startsWith("./") || href.startsWith("../")) return true
    // 形如 foo/bar、foo.md、README —— 常见相对文件引用
    return href.contains('/') || href.contains('.') && !href.startsWith("?")
}

private fun inline(
    text: String,
    code: Color,
    link: Color,
    onOpenPath: ((String) -> Unit)? = null,
): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    val linkStyle = SpanStyle(color = link, textDecoration = TextDecoration.Underline)
    for (match in inlinePattern.findAll(text)) {
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val token = match.value
        when {
            token.startsWith("`") -> {
                withStyle(
                    SpanStyle(
                        fontFamily = FontFamily.Monospace,
                        color = code,
                        background = code.copy(alpha = 0.12f),
                    ),
                ) { append(token.substring(1, token.length - 1)) }
            }
            token.startsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(inlinePlain(token.substring(2, token.length - 2)))
                }
            }
            token.startsWith("~~") -> {
                withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                    append(inlinePlain(token.substring(2, token.length - 2)))
                }
            }
            token.startsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(inlinePlain(token.substring(1, token.length - 1)))
                }
            }
            token.startsWith("![") -> {
                // 图片：显示 [图片: alt]，href 若是文件可点击
                val alt = token.substringAfter("![").substringBefore("]")
                val href = token.substringAfter("](").substringBeforeLast(")")
                val label = if (alt.isBlank()) "[图片]" else "[图片: $alt]"
                appendLinked(label, href, linkStyle, onOpenPath)
            }
            token.startsWith("[") -> {
                val label = token.substringAfter('[').substringBefore(']')
                val href = token.substringAfter("](").substringBeforeLast(")")
                appendLinked(label, href, linkStyle, onOpenPath)
            }
            else -> append(token)
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

private fun AnnotatedString.Builder.appendLinked(
    label: String,
    href: String,
    linkStyle: SpanStyle,
    onOpenPath: ((String) -> Unit)?,
) {
    if (href.isBlank()) {
        append(label)
        return
    }
    when {
        isHttpLink(href) -> {
            val ann = LinkAnnotation.Url(
                href,
                TextLinkStyles(style = linkStyle),
            )
            withLink(ann) { append(label) }
        }
        isLikelyFileHref(href) && onOpenPath != null -> {
            pushStringAnnotation("FILE_PATH", href)
            withStyle(linkStyle) { append(label) }
            pop()
        }
        isLikelyFileHref(href) -> {
            // 没有回调时也保留样式，至少可见
            withStyle(linkStyle) { append(label) }
        }
        else -> withStyle(linkStyle) { append(label) }
    }
}

private fun inlinePlain(text: String): String = text
    .replace("`", "")
    .replace("**", "")
    .replace("*", "")

private fun estimateColWidth(texts: List<String>, compact: Boolean): androidx.compose.ui.unit.Dp {
    // 粗略估算：ASCII 按 1，CJK/全角按 2，避免中文表格被压成细条
    val maxUnits = texts.maxOfOrNull { s ->
        s.sumOf { ch ->
            when {
                ch.code in 0x2E80..0x9FFF || ch.code in 0xFF00..0xFFEF || ch.code in 0x3000..0x303F -> 2
                else -> 1
            } as Int
        }
    } ?: 0
    val unit = if (compact) 5 else 6
    val min = if (compact) 56 else 72
    val max = if (compact) 320 else 420
    return (maxUnits * unit).coerceIn(min, max).dp
}

@Composable
private fun MarkdownTable(
    line: Line.Table,
    codeColor: Color,
    linkColor: Color,
    compact: Boolean,
    onOpenPath: ((String) -> Unit)?,
) {
    val colCount = line.headers.size.coerceAtLeast(1)
    val widths = (0 until colCount).map { c ->
        estimateColWidth(
            listOf(line.headers.getOrElse(c) { "" }) + line.rows.map { it.getOrElse(c) { "" } },
            compact,
        )
    }
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.40f)
    val headerBg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.70f)
    val padH = if (compact) 6.dp else 8.dp
    val padV = if (compact) 4.dp else 6.dp
    val fontSize = if (compact) 10.5.sp else 12.sp
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .border(1.dp, grid, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp)),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                Row(Modifier.background(headerBg)) {
                    line.headers.forEachIndexed { c, cell ->
                        LinkableText(
                            inline(cell, codeColor, linkColor, onOpenPath),
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = fontSize,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            ),
                            modifier = Modifier
                                .width(widths[c])
                                .border(0.5.dp, grid)
                                .padding(horizontal = padH, vertical = padV),
                            onOpenPath = onOpenPath,
                        )
                    }
                }
                if (line.rows.isEmpty()) {
                    Row {
                        (0 until colCount).forEach { c ->
                            Box(
                                Modifier
                                    .width(widths[c])
                                    .border(0.5.dp, grid)
                                    .padding(horizontal = padH, vertical = padV),
                            )
                        }
                    }
                }
                line.rows.forEachIndexed { r, row ->
                    Row(
                        Modifier.background(
                            if (r % 2 == 0) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f),
                        ),
                    ) {
                        (0 until colCount).forEach { c ->
                            val cell = row.getOrElse(c) { "" }
                            LinkableText(
                                inline(cell, codeColor, linkColor, onOpenPath),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontSize = fontSize,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = when (line.aligns.getOrElse(c) { -1 }) {
                                        0 -> TextAlign.Center
                                        1 -> TextAlign.End
                                        else -> TextAlign.Start
                                    },
                                ),
                                modifier = Modifier
                                    .width(widths[c])
                                    .border(0.5.dp, grid)
                                    .padding(horizontal = padH, vertical = padV),
                                onOpenPath = onOpenPath,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLines(
    text: String,
    codeColor: Color,
    linkColor: Color,
    compact: Boolean,
    onOpenPath: ((String) -> Unit)?,
) {
    val lines = remember(text) { parseLines(text) }
    val base = if (compact) {
        MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp, lineHeight = 15.sp)
    } else {
        MaterialTheme.typography.bodyMedium
    }
    Column(Modifier.fillMaxWidth()) {
        lines.forEach { line ->
            when (line) {
                is Line.Heading -> LinkableText(
                    inline(line.text, codeColor, linkColor, onOpenPath),
                    style = if (compact) {
                        when (line.level) {
                            1 -> MaterialTheme.typography.titleSmall
                            2 -> MaterialTheme.typography.labelLarge
                            else -> MaterialTheme.typography.labelMedium
                        }
                    } else {
                        when (line.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        }
                    },
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 6.dp, bottom = 2.dp),
                    onOpenPath = onOpenPath,
                )
                is Line.Quote -> Row(
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min)
                        .padding(vertical = 2.dp),
                ) {
                    Box(
                        Modifier
                            .width(3.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.55f), RoundedCornerShape(2.dp)),
                    )
                    LinkableText(
                        inline(line.text, codeColor, linkColor, onOpenPath),
                        style = base,
                        fontStyle = FontStyle.Italic,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                        onOpenPath = onOpenPath,
                    )
                }
                is Line.ListItem -> Row(
                    Modifier.padding(start = (line.indent * 12).dp, top = 1.dp, bottom = 1.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        if (line.marker in listOf("-", "*", "+")) "•" else line.marker,
                        style = base,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(if (compact) 16.dp else 18.dp),
                    )
                    LinkableText(
                        inline(line.text, codeColor, linkColor, onOpenPath),
                        style = base,
                        modifier = Modifier.weight(1f),
                        onOpenPath = onOpenPath,
                    )
                }
                is Line.Table -> MarkdownTable(line, codeColor, linkColor, compact, onOpenPath)
                Line.Rule -> HorizontalDivider(Modifier.padding(vertical = if (compact) 4.dp else 6.dp))
                Line.Gap -> Spacer(Modifier.height(if (compact) 4.dp else 6.dp))
                is Line.Para -> SelectionContainer {
                    LinkableText(
                        inline(line.text, codeColor, linkColor, onOpenPath),
                        style = base,
                        modifier = Modifier.padding(vertical = 1.dp),
                        onOpenPath = onOpenPath,
                    )
                }
            }
        }
    }
}

@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onOpenPath: ((String) -> Unit)? = null,
) {
    val blocks = remember(text) { splitBlocks(text) }
    val codeColor = MaterialTheme.colorScheme.primary
    val linkColor = MaterialTheme.colorScheme.secondary
    val mono = MaterialTheme.colorScheme.onSurface

    Column(modifier.fillMaxWidth()) {
        blocks.forEach { block ->
            when (block) {
                is Block.Code -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .background(
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                RoundedCornerShape(8.dp),
                            ),
                    ) {
                        if (block.lang.isNotBlank()) {
                            Text(
                                block.lang,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 10.dp, top = 5.dp),
                            )
                        }
                        Text(
                            block.code,
                            fontFamily = FontFamily.Monospace,
                            fontSize = if (compact) 10.5.sp else 11.sp,
                            color = mono,
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = 10.dp, vertical = 7.dp),
                        )
                    }
                }
                is Block.Text -> MarkdownLines(block.text, codeColor, linkColor, compact, onOpenPath)
            }
        }
    }
}


// ---------------------------------------------------------------------------
// 文本里的路径：点一下就打开文件卡片。
// 支持：file:// URI、~/…、/…（绝对）、以及常见的相对路径（含 / 或带扩展名），
// 不要求以 / 开头。解析时按当前会话目录补全相对路径。
// ---------------------------------------------------------------------------

private val FILE_URI_RE = Regex("""file://[^\s"'`<>)\]]+""")

// 绝对/家目录路径：~ 或 / 开头，至少一段
private val ABS_PATH_RE = Regex("""(?:~|/)[\w.@%+\-]+(?:/[\w.@%+\-]+)*""")

// 相对路径：形如 src/foo.kt、./foo、../foo、a/b/c.ts、README.md（带扩展名的单段）
// 规则：由 [\w.@%+-] 和 / 组成，包含 / 或包含“看起来像扩展名”的 .xxx 结尾，
// 且不以 http(s):// 开头（避免误伤链接），前面不是 URL/协议的一部分。
private val REL_PATH_RE = Regex(
    """(?<![A-Za-z0-9_:/])(?:\./|\.\./)?[\w.@%+\-]+(?:/[\w.@%+\-]+)+(?:[?#][^\s"'`<>)\]]*)?""" +
        """|(?<![A-Za-z0-9_:/])(?:\./|\.\./)?[\w@%+\-]+(?:\.[\w@%+\-]+)*\.[A-Za-z0-9]{1,10}(?::\d+(?::\d+)?)?(?![A-Za-z0-9_])""",
)

private val TRAILING_JUNK = charArrayOf('.', ',', ';', ':', ')', ']', '}', '"', '\'', '>', '，', '。', '：', '）', '】', '」')

private fun cleanMatch(raw: String): String {
    var s = raw
    while (s.isNotEmpty() && s.last() in TRAILING_JUNK) s = s.dropLast(1)
    // 去掉 :line 或 :line:col 后缀（编辑器跳转格式），保留路径本体用于打开
    return s.replace(Regex(""":(\d+)(?::\d+)?$"""), "")
}

/** 返回 offset 落在哪个路径上；优先 file://，其次绝对/家目录，其次相对路径。 */
fun pathAt(text: String, offset: Int): String? {
    for (re in listOf(FILE_URI_RE, ABS_PATH_RE, REL_PATH_RE)) {
        for (m in re.findAll(text)) {
            if (offset in m.range) {
                val cleaned = cleanMatch(m.value)
                // 过滤掉明显不是文件路径的东西（纯数字/版本号等）
                if (cleaned == "." || cleaned == ".." || cleaned.all { it == '/' }) continue
                if (re === REL_PATH_RE && !looksLikeFilePath(cleaned)) continue
                return cleaned
            }
        }
    }
    return null
}

private fun looksLikeFilePath(s: String): Boolean {
    if (s.startsWith("./") || s.startsWith("../") || s.contains('/')) return true
    // 单段带扩展名：README.md、build.gradle.kts 等
    val dot = s.lastIndexOf('.')
    if (dot > 0 && dot < s.length - 1) {
        val ext = s.substring(dot + 1).lowercase()
        // 常见扩展名/后缀白名单倾向：避免把 "v1.2" 当路径
        if (ext.any { !it.isLetterOrDigit() }) return false
        if (ext.length in 1..10 && ext.any { it.isLetter() }) return true
    }
    return false
}

/**
 * 把 Markdown/工具里的原始路径解析成可交给文件 API 的绝对路径。
 * - file:// → 去协议并解码
 * - ~/ → 展开 home
 * - / 开头 → 原样
 * - 其余 → 相对 baseDir（会话目录）
 */
fun resolveFileRef(raw: String, baseDir: String, homeDir: String): String {
    var s = raw.trim()
    if (s.startsWith("file://")) {
        s = s.removePrefix("file://")
        // file:///C:/... (Windows 风格) 在 Linux 上很少见，但顺手处理
        if (Regex("""^/[A-Za-z]:""").containsMatchIn(s)) s = s.removePrefix("/")
        s = runCatching { java.net.URLDecoder.decode(s, "UTF-8") }.getOrElse { s }
        s = s.substringBefore('?').substringBefore('#')
    }
    s = cleanMatch(s)
    return when {
        s.startsWith("~/") -> {
            val home = homeDir.trimEnd('/')
            if (home.isNotBlank()) home + s.removePrefix("~") else s
        }
        s.startsWith("/") -> s
        else -> {
            val base = baseDir.trimEnd('/')
            if (base.isNotBlank()) "$base/$s" else s
        }
    }
}

@Composable
private fun LinkableText(
    annotated: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    onOpenPath: ((String) -> Unit)? = null,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    val uriHandler = LocalUriHandler.current
    val tapModifier = if (onOpenPath == null) {
        modifier.pointerInput(annotated) {
            detectTapGestures { pos ->
                val lr = layout ?: return@detectTapGestures
                if (annotated.isEmpty()) return@detectTapGestures
                val offset = lr.getOffsetForPosition(pos).coerceIn(0, annotated.length - 1)
                val urlAnn = annotated.getLinkAnnotations(offset, offset + 1).firstOrNull()?.item
                if (urlAnn is LinkAnnotation.Url) runCatching { uriHandler.openUri(urlAnn.url) }
            }
        }
    } else {
        modifier.pointerInput(annotated, onOpenPath, uriHandler) {
            detectTapGestures { pos ->
                val lr = layout ?: return@detectTapGestures
                if (annotated.isEmpty()) return@detectTapGestures
                val offset = lr.getOffsetForPosition(pos).coerceIn(0, annotated.length - 1)
                // 1) Markdown 链接显式标注的文件目标
                val fileAnn = annotated.getStringAnnotations("FILE_PATH", offset, offset + 1)
                    .firstOrNull()?.item
                if (fileAnn != null) {
                    onOpenPath(fileAnn)
                    return@detectTapGestures
                }
                // 2) http(s) 链接
                val urlAnn = annotated.getLinkAnnotations(offset, offset + 1).firstOrNull()?.item
                if (urlAnn is LinkAnnotation.Url) {
                    runCatching { uriHandler.openUri(urlAnn.url) }
                    return@detectTapGestures
                }
                // 3) 文本里裸露的路径（绝对/相对/file://）
                pathAt(annotated.text, offset)?.let(onOpenPath)
            }
        }
    }
    Text(
        annotated,
        style = style,
        modifier = tapModifier,
        color = color,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        onTextLayout = { layout = it },
    )
}
