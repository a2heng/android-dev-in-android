package com.a2heng.opencode.mobile.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.NoteAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** 不同工具用不同图标，一眼能分清 shell / 读文件 / 编辑 / 搜索 / 网络。 */
fun toolIcon(name: String): ImageVector = when (name.lowercase()) {
    "shell", "bash", "sh", "terminal" -> Icons.Rounded.Terminal
    "read", "cat" -> Icons.Rounded.Description
    "write", "create" -> Icons.Rounded.NoteAdd
    "edit", "patch", "multiedit" -> Icons.Rounded.Edit
    "glob", "grep", "search", "find" -> Icons.Rounded.Search
    "webfetch", "fetch", "websearch" -> Icons.Rounded.Language
    "task", "agent", "subagent" -> Icons.Rounded.AccountTree
    "question", "ask" -> Icons.Rounded.HelpOutline
    "todowrite", "todo", "todos" -> Icons.Rounded.Checklist
    else -> Icons.Rounded.Build
}

/** 把 data:image/...;base64,... 解码成 Bitmap，限制最大边避免 OOM。 */
fun decodeDataUri(uri: String, maxSide: Int = 1600): Bitmap? {
    val comma = uri.indexOf(',')
    if (!uri.startsWith("data:") || comma < 0) return null
    return try {
        val bytes = Base64.decode(uri.substring(comma + 1), Base64.DEFAULT)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) sample *= 2
        BitmapFactory.decodeByteArray(
            bytes, 0, bytes.size,
            BitmapFactory.Options().apply { inSampleSize = sample },
        )
    } catch (_: Throwable) {
        null
    }
}

@Composable
fun DataUriImage(uri: String) {
    val bitmap = remember(uri) { decodeDataUri(uri) }
    if (bitmap == null) {
        Text(
            "[图片无法解码]",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    Image(
        bitmap = bitmap.asImageBitmap(),
        contentDescription = "工具生成的图片",
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
            .padding(top = 4.dp),
    )
}

/** 统一 diff：+ 绿、- 红、@@ 蓝；跳过 Index/---/+++ 这些噪声头。 */
@Composable
fun DiffView(diff: String) {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    val plus = if (dark) Color(0xFF4ADE80) else Color(0xFF17864A)
    val minus = if (dark) Color(0xFFF87171) else Color(0xFFC0323D)
    val hunk = if (dark) Color(0xFF7DD3FC) else Color(0xFF1D7FA6)
    val lines = remember(diff) { meaningfulDiffLines(diff) }
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 6.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        lines.take(400).forEach { line ->
            val color = when {
                line.startsWith("@@") -> hunk
                line.startsWith("+") -> plus
                line.startsWith("-") -> minus
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            val bg = when {
                line.startsWith("+") -> plus.copy(alpha = 0.10f)
                line.startsWith("-") -> minus.copy(alpha = 0.10f)
                line.startsWith("@@") -> hunk.copy(alpha = 0.10f)
                else -> Color.Transparent
            }
            Text(
                line.ifEmpty { " " },
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = color,
                softWrap = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(bg)
                    .padding(horizontal = 8.dp, vertical = 0.5.dp),
            )
        }
    }
}

fun meaningfulDiffLines(diff: String): List<String> =
    diff.lineSequence().filterNot { line ->
        line.startsWith("Index: ") || line.startsWith("===") ||
            line.startsWith("--- ") || line.startsWith("+++ ")
    }.toList()

/** 折叠状态只显示真正的改动行，避免前 10 行全是 patch 头。 */
fun diffPreview(diff: String, maxLines: Int): String =
    meaningfulDiffLines(diff).take(maxLines).joinToString("\n")
