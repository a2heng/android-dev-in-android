package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.FsEntry
import com.a2heng.opencode.mobile.data.isAbsolutePath
import com.a2heng.opencode.mobile.data.joinPath
import com.a2heng.opencode.mobile.data.normPath

/**
 * 从工作目录里选一个文件，插入 `@相对路径` 引用（opencode 的 mention 写法）。
 * 全屏子页（PickerPage），不是弹窗；目录优先，文件点了就插入。
 */
@Composable
fun FileMentionPage(
    model: AppModel,
    startDir: String,
    onDismiss: () -> Unit,
    onPick: (absolutePath: String) -> Unit,
) {
    var path by remember { mutableStateOf(startDir) }
    var root by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    LaunchedEffect(path, reload) {
        loading = true
        error = null
        try {
            val (r, list) = model.browseDirs(path.ifBlank { null })
            root = r
            if (path.isBlank()) path = r
            entries = list.filter { !it.name.startsWith(".") }
                .sortedWith(compareByDescending<FsEntry> { it.isDir }.thenBy { it.name.lowercase() })
        } catch (e: Exception) {
            error = e.message
        } finally {
            loading = false
        }
    }

    PickerPage(title = "插入文件引用", onBack = onDismiss) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    val r = normPath(root).trimEnd('/')
                    var cur = normPath(path).trimEnd('/')
                    if (cur.isNotEmpty() && !isAbsolutePath(cur)) {
                        cur = if (r.isNotEmpty()) joinPath(r, cur) else "/" + cur.trimStart('/')
                        cur = cur.trimEnd('/')
                    }
                    val rawParent = cur.substringBeforeLast('/', "")
                    val parent = when {
                        rawParent.isEmpty() -> "/"
                        Regex("^[A-Za-z]:$").matches(rawParent) -> "$rawParent/"
                        else -> rawParent
                    }
                    val atRoot = cur.isEmpty() || cur == r ||
                        cur == "/" || Regex("^[A-Za-z]:$").matches(cur)
                    if (!atRoot && parent.length >= r.length) {
                        path = parent
                    }
                },
                enabled = path.trimEnd('/').isNotEmpty() && path.trimEnd('/') != root.trimEnd('/'),
            ) {
                Icon(
                    Icons.Rounded.ArrowUpward,
                    contentDescription = "上一级",
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                path,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider()
        when {
            loading -> Box(
                Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            }
            error != null -> Text(
                error ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(12.dp),
            )
            entries.isEmpty() -> EmptyState("目录是空的")
            else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(entries, key = { it.path }) { entry ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickableNoRipple {
                                val full = fullPath(root, entry.path)
                                if (entry.isDir) {
                                    path = full
                                } else {
                                    onPick(full)
                                    onDismiss()
                                }
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                            tint = if (entry.isDir) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private fun fullPath(root: String, relative: String): String = joinPath(root, relative)
