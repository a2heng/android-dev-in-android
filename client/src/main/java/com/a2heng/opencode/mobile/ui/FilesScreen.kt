package com.a2heng.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.FsEntry
import com.a2heng.opencode.mobile.data.isAbsolutePath
import com.a2heng.opencode.mobile.data.joinPath
import com.a2heng.opencode.mobile.data.normPath

@Composable
fun FilesScreen(model: AppModel, wide: Boolean = false) {
    LaunchedEffect(Unit) {
        if (model.fsDir.isBlank()) model.loadDir(null)
    }

    val rootBase = model.fsBase()
    BackHandler(enabled = model.openFile != null || parentDir(model.fsDir, rootBase) != null) {
        if (model.openFile != null) {
            model.closeFile()
        } else {
            parentDir(model.fsDir, rootBase)?.let { model.loadDir(it) }
        }
    }

    if (wide) {
        // 横屏：左边目录/文件列表，右边预览，选文件不再把列表顶掉
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.width(320.dp).fillMaxHeight()) { FsListPane(model) }
            androidx.compose.material3.VerticalDivider(
                Modifier.fillMaxHeight(),
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                val open = model.openFile
                if (open != null) {
                    FileViewer(model, open)
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "选一个文件在这里查看",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        return
    }

    val open = model.openFile
    if (open != null) {
        FileViewer(model, open)
        return
    }

    FsListPane(model)
}

@Composable
private fun FsListPane(model: AppModel) {
    val rootBase = model.fsBase()
    var creatingFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        // 预设文件夹胶囊：~、当前会话目录、最近项目（自动换行，最多约 5 行后滚动）。
        // 不提供 "/" 和 "/tmp/opencode"：Adia 的应用沙箱读不到真实根目录，
        // 点开只会是空的，反而让人以为文件系统坏了。
        val presets = remember(model.homeDir, model.current?.id, model.sessions, model.fsDir, model.fsRootOverride) {
            buildList {
                if (model.homeDir.isNotBlank()) add("~" to model.homeDir)
                model.expandSessionDir(model.current?.directory)?.let { add("当前会话" to it) }
                model.sessions.forEach { s ->
                    val dir = model.expandSessionDir(s.directory) ?: return@forEach
                    if (none { it.second == dir }) {
                        add(dir.trimEnd('/').substringAfterLast('/').ifBlank { dir } to dir)
                    }
                }
            }.take(24)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 168.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 6.dp),
        ) {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                presets.forEach { (label, dir) ->
                    val selected = model.fsDir.trimEnd('/') == dir.trimEnd('/')
                    Surface(
                        color = if (selected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        shape = RoundedCornerShape(50),
                        border = BorderStroke(
                            1.dp,
                            if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.22f),
                        ),
                        modifier = Modifier.clickableNoRipple {
                            model.closeFile()
                            model.loadDir(dir)
                        },
                    ) {
                        Text(
                            label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = { parentDir(model.fsDir, rootBase)?.let { model.loadDir(it) } },
                enabled = parentDir(model.fsDir, rootBase) != null,
            ) {
                Icon(Icons.Rounded.ArrowUpward, contentDescription = "上级目录")
            }
            Text(
                pathLabel(rootBase, model.fsDir),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (model.fsLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            IconButton(onClick = { folderName = ""; creatingFolder = true }) {
                Icon(
                    Icons.Rounded.CreateNewFolder,
                    contentDescription = "新建文件夹",
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        model.fsError?.let {
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 14.dp),
            )
        }

        if (model.fsEntries.isEmpty() && !model.fsLoading) {
            EmptyState("目录是空的")
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(model.fsEntries, key = { it.path }) { entry ->
                    FsRow(entry) {
                        val full = fullPath(model.fsDir.ifBlank { rootBase }, entry.path)
                        if (entry.isDir) model.loadDir(full) else model.openFile(full)
                    }
                }
                item { Spacer(Modifier.height(20.dp)) }
            }
        }
    }

    if (creatingFolder) {
        AppDialog(
            onDismissRequest = { creatingFolder = false },
            title = { Text("新建文件夹") },
            text = {
                Column {
                    Text(
                        pathLabel(rootBase, model.fsDir),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                        val name = folderName.trim().trim('/')
                        creatingFolder = false
                        folderName = ""
                        if (name.isNotEmpty()) {
                            model.createFolder(model.fsDir, name) {
                                model.loadDir(model.fsDir)
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

private fun fullPath(root: String, relative: String): String = joinPath(root, relative)

private fun isUnder(base: String, path: String): Boolean {
    val b = normPath(base).trimEnd('/')
    val p = normPath(path).trimEnd('/')
    if (b.isEmpty()) return true
    return p == b || p.startsWith("$b/")
}

/** 已经是根（"/" 或 Windows 的 "D:/"）就没有上一级。 */
private fun isRootPath(p: String): Boolean =
    p.isEmpty() || p == "/" || Regex("^[A-Za-z]:$").matches(p)

/** Directorio padre, sin salir de la raiz del explorador. */
private fun parentDir(current: String, base: String): String? {
    val b = normPath(base).trimEnd('/')
    var cur = normPath(current).trimEnd('/')
    // 相对路径（不以 / 或盘符开头）先补到基准根：否则 substringBeforeLast('/')
    // 只会截掉一段，表现为「上一级」点了没反应或路径显示不全。
    if (cur.isNotEmpty() && !isAbsolutePath(cur)) {
        cur = if (b.isNotEmpty()) joinPath(b, cur) else "/" + cur.trimStart('/')
        cur = cur.trimEnd('/')
    }
    if (isRootPath(cur) || cur == b) return null
    if (b.isNotEmpty() && !isUnder(b, cur)) return null
    val rawParent = cur.substringBeforeLast('/', "")
    val parent = when {
        rawParent.isEmpty() -> "/"
        Regex("^[A-Za-z]:$").matches(rawParent) -> "$rawParent/"
        else -> rawParent
    }
    return if (b.isNotEmpty() && b != "/" && !isUnder(b, parent)) b else parent
}

/** Ruta visible: relativa a HOME; la propia raiz se muestra como "~". */
private fun pathLabel(base: String, dir: String): String {
    val b = normPath(base).trimEnd('/')
    val d = normPath(dir).trimEnd('/')
    return when {
        d.isBlank() -> "~"
        b.isNotEmpty() && d == b -> "~"
        b.isNotEmpty() && b != "/" && d.startsWith("$b/") -> "~" + d.removePrefix(b)
        else -> d
    }
}

@Composable
private fun FsRow(entry: FsEntry, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (entry.isDir) Icons.Rounded.Folder else Icons.Rounded.Description,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = if (entry.isDir) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.size(12.dp))
        Text(
            entry.path.trimEnd('/').substringAfterLast('/'),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun FileViewer(model: AppModel, path: String) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { model.closeFile() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Column(Modifier.weight(1f)) {
                Text(
                    path.substringAfterLast('/'),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    path.substringBeforeLast('/', ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (model.openFileLoading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp).padding(end = 4.dp), strokeWidth = 2.dp)
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            val mediaUri = model.openFileMediaUri
            val mediaKind = model.openFileMediaKind
            when {
                model.openFileLoading -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center).size(22.dp),
                    strokeWidth = 2.dp,
                )
                mediaUri != null && mediaKind != null -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    MediaPreview(
                        uri = mediaUri,
                        kind = mediaKind,
                        title = path.substringAfterLast('/'),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (model.openFileMime.isNotBlank() || model.openFileBytesSize > 0L) {
                        Text(
                            listOfNotNull(
                                model.openFileMime.takeIf { it.isNotBlank() },
                                if (model.openFileBytesSize > 0L) "${model.openFileBytesSize / 1024} KB" else null,
                            ).joinToString(" · "),
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
                        Modifier.fillMaxWidth(),
                        compact = false,
                        onOpenPath = { ref ->
                            model.openFile(resolveFileRef(ref, baseDir, model.homeDir))
                        },
                    )
                }
                else -> Box(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                ) {
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
