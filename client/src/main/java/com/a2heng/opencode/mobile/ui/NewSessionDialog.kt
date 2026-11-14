package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.a2heng.opencode.mobile.data.isAbsolutePath
import com.a2heng.opencode.mobile.data.joinPath
import com.a2heng.opencode.mobile.data.normPath
import com.a2heng.opencode.mobile.data.FsEntry

/**
 * 新建会话时选择（或新建）一个文件夹。
 *
 * 目录来自 /api/fs/list：entry.path 是相对**当前 location** 的，所以拼绝对
 * 路径时要用当前 path（不是固定的根）。
 *
 * `HOME` 和 `项目区` 指向我们自己的规范位置：
 * - `HOME`  = 服务端 HOME（GET /api/location）；
 * - `项目区` = HOME/workspace。真正的 Android 根目录既不能当项目，
 *   还会让整个选择器卡住，所以不在这里暴露。
 *
 * 根节点固定为真实 `/`，不随服务端返回的 location 变化；两个胶囊负责跳到
 * 规范位置，「上一级」不会被重置。
 */
@Composable
fun NewSessionDialog(
    model: AppModel,
    onDismiss: () -> Unit,
    onCreate: (String?) -> Unit,
) {
    val home = model.homeDir.trimEnd('/')
    val initial = model.expandSessionDir(model.current?.directory)
        ?: model.expandSessionDir(model.sessions.firstOrNull()?.directory)
        ?: home
    var path by remember { mutableStateOf(initial) }
    var dirs by remember { mutableStateOf<List<FsEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }

    var creatingFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    LaunchedEffect(path, reload) {
        val requested = path
        loading = true
        error = null
        try {
            val (location, list) = model.browseDirs(requested.ifBlank { null })
            // 快速点按/切目录时，旧请求可能晚到：只认最后一次。
            if (path != requested) return@LaunchedEffect
            if (path.isBlank()) path = home.ifBlank { location }
            dirs = list.filter { it.isDir && !it.name.startsWith(".") }
                .sortedBy { it.name.lowercase() }
        } catch (e: Exception) {
            if (path == requested) {
                error = e.message
                dirs = emptyList()
            }
        } finally {
            if (path == requested) loading = false
        }
    }

    // 根固定为真根：只靠服务端返回的 location 当根会让「上一级」点一下就被
    // 重置。`~` / `/` 胶囊负责跳到规范位置，向上仍可自由走到任意祖先。
    val baseRoot = "/"
    val upTarget = sessionParentDir(path, baseRoot)
    val recents = remember(model.sessions, home) {
        model.sessions.mapNotNull { model.expandSessionDir(it.directory) }
            .filter { it.isNotBlank() }
            .distinct()
            .take(6)
    }

    AppDialog(
        onDismissRequest = onDismiss,
        title = { Text("新建会话") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { upTarget?.let { path = it } },
                        enabled = upTarget != null,
                    ) {
                        Icon(Icons.Rounded.ArrowUpward, contentDescription = "上一级", modifier = Modifier.size(20.dp))
                    }
                    Text(
                        sessionPathLabel(path, home),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                }
                HorizontalDivider()
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    sessionChip(
                        label = "HOME",
                        selected = path.trimEnd('/') == home,
                    ) { if (home.isNotBlank()) path = home }
                    val workspace = model.sessionRoot()
                    sessionChip(
                        label = "项目区",
                        selected = workspace.isNotBlank() && path.trimEnd('/') == workspace,
                    ) { path = workspace.ifBlank { home } }
                }
                if (recents.isNotEmpty()) {
                    Text(
                        "最近使用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        recents.forEach { dir ->
                            sessionChip(
                                label = shortDir(dir, model.homeDir),
                                selected = path.trimEnd('/') == dir.trimEnd('/'),
                            ) { path = dir }
                        }
                    }
                }
                HorizontalDivider()
                Box(Modifier.fillMaxWidth().height(260.dp)) {
                    when {
                        loading -> Box(Modifier.fillMaxWidth().height(260.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(strokeWidth = 2.dp)
                        }
                        error != null -> Text(
                            error ?: "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp),
                        )
                        dirs.isEmpty() -> EmptyState("这个目录下没有子文件夹")
                        else -> LazyColumn {
                            items(dirs, key = { it.path }) { entry ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .clickableNoRipple {
                                            path = fullPath(path.ifBlank { baseRoot }, entry.path)
                                        }
                                        .padding(vertical = 9.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Rounded.Folder,
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                        tint = MaterialTheme.colorScheme.tertiary,
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
                HorizontalDivider()
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(onClick = { creatingFolder = true }) {
                        Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("新建文件夹")
                    }
                    Text(
                        "会话将在此目录运行",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCreate(model.expandSessionDir(path)) }) {
                Text("在这里创建")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )

    if (creatingFolder) {
        AppDialog(
            onDismissRequest = { creatingFolder = false },
            title = { Text("新建文件夹") },
            text = {
                Column {
                    Text(
                        "在 ${sessionPathLabel(path, home)} 下创建",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = folderName,
                        onValueChange = { folderName = it },
                        placeholder = { Text("文件夹名，例如 PureVox") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = folderName.isNotBlank(),
                    onClick = {
                        val name = folderName.trim()
                        creatingFolder = false
                        folderName = ""
                        if (name.isNotEmpty()) {
                            val parent = path.ifBlank { baseRoot }
                            model.createFolder(parent, name) {
                                path = fullPath(parent, name)
                                reload++
                            }
                        }
                    },
                ) { Text("创建") }
            },
            dismissButton = {
                TextButton(onClick = {
                    creatingFolder = false
                    folderName = ""
                }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun sessionChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        },
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.clickableNoRipple(onClick),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
}

private fun fullPath(root: String, relative: String): String = joinPath(root, relative)

private fun isUnder(base: String, path: String): Boolean {
    val b = normPath(base).trimEnd('/')
    val p = normPath(path).trimEnd('/')
    if (b.isEmpty()) return true
    return p == b || p.startsWith("$b/")
}

/** 已经是根（"/" 或 Windows 的 "D:/"）就没有上一级。 */
private fun sessionIsRootPath(p: String): Boolean =
    p.isEmpty() || p == "/" || Regex("^[A-Za-z]:$").matches(p)

/** 上一级，不越过选择器固定的根。 */
private fun sessionParentDir(current: String, base: String): String? {
    val b = normPath(base).trimEnd('/')
    var cur = normPath(current).trimEnd('/')
    // 不以 / 或盘符开头的相对路径先补到固定根，避免「上一级」只截掉一段。
    if (cur.isNotEmpty() && !isAbsolutePath(cur)) {
        cur = if (b.isNotEmpty()) joinPath(b, cur) else "/" + cur.trimStart('/')
        cur = cur.trimEnd('/')
    }
    if (sessionIsRootPath(cur) || cur == b) return null
    if (b.isNotEmpty() && !isUnder(b, cur)) return null
    val rawParent = cur.substringBeforeLast('/', "")
    val parent = when {
        rawParent.isEmpty() -> "/"
        Regex("^[A-Za-z]:$").matches(rawParent) -> "$rawParent/"
        else -> rawParent
    }
    return if (b.isNotEmpty() && b != "/" && !isUnder(b, parent)) b else parent
}

private fun sessionPathLabel(path: String, home: String): String {
    val h = home.trimEnd('/')
    val p = path.trimEnd('/')
    return when {
        p.isBlank() -> if (h.isNotBlank()) "~" else "/"
        h.isNotBlank() && p == h -> "~"
        h.isNotBlank() && p.startsWith("$h/") -> "~/" + p.removePrefix("$h/")
        else -> p
    }
}

private fun shortDir(dir: String, home: String): String {
    val h = home.trimEnd('/')
    return when {
        h.isNotEmpty() && dir.trimEnd('/') == h -> "~"
        h.isNotEmpty() && dir.startsWith("$h/") -> dir.removePrefix("$h/").trimEnd('/')
        else -> dir.trimEnd('/').ifBlank { "/" }
    }
}
