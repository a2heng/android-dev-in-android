package com.a2heng.opencode.mobile.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.a2heng.opencode.mobile.data.AppModel
import com.a2heng.opencode.mobile.data.VcsDiffFile
import com.a2heng.opencode.mobile.data.VcsFileStatus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GitScreen(
    model: AppModel,
    directory: String,
    onBack: () -> Unit,
) {
    BackHandler(onBack = onBack)
    val info = model.gitInfo
    val selected = model.gitSelectedFile
    val files = mergeGitFiles(model.gitStatus, model.gitDiffFiles)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(0.92f)
            .padding(horizontal = 10.dp, vertical = 18.dp),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.background,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
    ) {
        Column(Modifier.fillMaxSize()) {
            GitHeader(
                directory = directory,
                branch = info.label,
                provider = info.provider,
                baseLabel = model.gitBaseLabel,
                mode = model.gitMode,
                loading = model.gitLoading,
                files = files,
                onBack = onBack,
                onRefresh = { model.loadGit(model.gitMode) },
                onMode = { model.loadGit(it) },
            )

            model.gitError?.let { err ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                        .padding(12.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        TextButton(onClick = { model.loadGit(model.gitMode) }) { Text("刷新") }
                    }
                }
            }

            val hasVcs = info.provider.isNotBlank() || model.gitStatus.isNotEmpty() || model.gitDiffFiles.isNotEmpty()
            if (model.gitLoading && !hasVcs && model.gitError == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                }
                return@Column
            }

            if (!hasVcs && !model.gitLoading && model.gitError == null) {
                EmptyState(
                    "未检测到 Git 信息",
                    "当前目录：${directory.ifBlank { "—" }}\n如果这是仓库子目录、服务端 location 不同或 /api/vcs 不可用，点刷新重试。",
                )
                return@Column
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

            if (files.isEmpty() && !model.gitLoading) {
                val msg = when (model.gitMode) {
                    "committed" -> "上一次提交没有可显示的差异。"
                    "branch" -> "与基线相比没有差异。"
                    else -> "没有检测到未提交的变更。"
                }
                EmptyState(
                    msg,
                    "「工作区」显示未提交的改动；「上一次提交」显示最近一次提交（HEAD~1 → HEAD）的改动。" +
                        "逐条 commit 历史记录需要服务端提供 git log 接口（当前 /api/vcs 未提供）。",
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 16.dp),
                ) {
                    items(files, key = { "${model.gitMode}:${it.path}" }) { item ->
                                        val fileDiff = model.gitDiffFiles.firstOrNull { it.file == item.path }
                        val isSelected = selected == item.path
                        GitFileRow(
                            item = item,
                            selected = isSelected,
                            onClick = { model.selectGitFile(if (selected == item.path) null else item.path) },
                        )
                        if (isSelected && fileDiff != null) {
                            GitDiffPanel(fileDiff)
                        } else if (isSelected && fileDiff == null) {
                            Text(
                                "该文件没有可展开的补丁内容。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 32.dp, end = 14.dp, bottom = 8.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GitHeader(
    directory: String,
    branch: String,
    provider: String,
    baseLabel: String?,
    mode: String,
    loading: Boolean,
    files: List<GitRowItem>,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onMode: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
            }
            Icon(
                Icons.Rounded.AccountTree,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Git", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    if (branch.isNotBlank() && branch != "—") {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            branch,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (provider.isNotBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text(provider, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                val sub = directory.ifBlank { "—" }
                Text(
                    sub,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (loading) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(4.dp))
            }
            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "刷新")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == "working",
                onClick = { if (!loading) onMode("working") },
                label = { Text("工作区", maxLines = 1) },
            )
            FilterChip(
                selected = mode == "committed",
                onClick = { if (!loading) onMode("committed") },
                label = { Text("上一次提交", maxLines = 1) },
            )
            Spacer(Modifier.weight(1f))
            GitSummary(files)
        }
    }
}

private data class GitRowItem(val path: String, val status: String, val additions: Int, val deletions: Int)

// 与 ToolViews.DiffView 保持一致的主题感知配色
private data class DiffColors(val plus: Color, val minus: Color, val hunk: Color)

@Composable
private fun rememberDiffColors(): DiffColors {
    val dark = MaterialTheme.colorScheme.background.luminanceIsDark()
    return DiffColors(
        plus = if (dark) Color(0xFF4ADE80) else Color(0xFF17864A),
        minus = if (dark) Color(0xFFF87171) else Color(0xFFC0323D),
        hunk = if (dark) Color(0xFF7DD3FC) else Color(0xFF1D7FA6),
    )
}

@Composable
private fun GitSummary(files: List<GitRowItem>) {
    val add = files.sumOf { it.additions }
    val del = files.sumOf { it.deletions }
    if (files.isEmpty() && add == 0 && del == 0) return
    val colors = rememberDiffColors()
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${files.size}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            softWrap = false,
        )
        if (add > 0) Text(
            "+$add",
            style = MaterialTheme.typography.labelSmall,
            color = colors.plus,
            maxLines = 1,
            softWrap = false,
        )
        if (del > 0) Text(
            "-$del",
            style = MaterialTheme.typography.labelSmall,
            color = colors.minus,
            maxLines = 1,
            softWrap = false,
        )
    }
}

private fun mergeGitFiles(status: List<VcsFileStatus>, diffs: List<VcsDiffFile>): List<GitRowItem> {
    val byPath = linkedMapOf<String, GitRowItem>()
    status.forEach {
        byPath[it.file] = GitRowItem(it.file, it.status, it.additions, it.deletions)
    }
    diffs.forEach { d ->
        val prev = byPath[d.file]
        byPath[d.file] = GitRowItem(
            d.file,
            d.status.ifBlank { prev?.status.orEmpty() },
            if (d.additions > 0 || prev == null) d.additions else prev.additions,
            if (d.deletions > 0 || prev == null) d.deletions else prev.deletions,
        )
    }
    return byPath.values.sortedBy { it.path.lowercase() }
}

@Composable
private fun GitFileRow(item: GitRowItem, selected: Boolean, onClick: () -> Unit) {
    val colors = rememberDiffColors()
    val (statusLabel, statusColor) = when (item.status) {
        "added" -> "A" to colors.plus
        "deleted" -> "D" to colors.minus
        "modified" -> "M" to MaterialTheme.colorScheme.primary
        else -> "•" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                statusLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                modifier = Modifier.width(18.dp),
            )
            Text(
                item.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (item.additions > 0) {
                Text(
                    "+${item.additions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.plus,
                    maxLines = 1,
                    softWrap = false,
                )
                Spacer(Modifier.width(6.dp))
            }
            if (item.deletions > 0) {
                Text(
                    "-${item.deletions}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.minus,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
        val dir = item.path.substringBeforeLast('/', "")
        if (dir.isNotBlank()) {
            Text(
                dir,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 18.dp, top = 1.dp),
            )
        }
    }
}

@Composable
private fun GitDiffPanel(diff: VcsDiffFile) {
    val patch = diff.patch.ifBlank { "（无可显示的补丁内容）" }
    val colors = rememberDiffColors()
    val metaFg = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, bottom = 8.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(vertical = 4.dp),
    ) {
        if (diff.more) {
            Text(
                "内容较多，已截断部分显示",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        // 只显示变更行与 hunk/文件头，隐藏未修改的上下文行（避免太长）
        val filtered = patch.lineSequence().filter { line ->
            line.startsWith('+') || line.startsWith('-') || line.startsWith('@') ||
                line.startsWith("diff ") || line.startsWith("index ") ||
                line.startsWith('\\') || line.startsWith("Binary ")
        }
        val lines = filtered.take(220).toList()
        val truncated = filtered.drop(220).any()
        Box(Modifier.horizontalScroll(rememberScrollState())) {
            Column {
                lines.forEach { line ->
                    val (fg, bg) = when {
                        line.startsWith("+++") || line.startsWith("---") ->
                            metaFg to Color.Transparent
                        line.startsWith("@@") ->
                            colors.hunk to colors.hunk.copy(alpha = 0.10f)
                        line.startsWith("+") ->
                            colors.plus to colors.plus.copy(alpha = 0.10f)
                        line.startsWith("-") ->
                            colors.minus to colors.minus.copy(alpha = 0.10f)
                        line.startsWith("diff ") || line.startsWith("index ") ->
                            metaFg to Color.Transparent
                        else -> MaterialTheme.colorScheme.onSurface to Color.Transparent
                    }
                    Text(
                        line.ifEmpty { " " },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = fg,
                        softWrap = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(bg)
                            .padding(horizontal = 8.dp, vertical = 0.5.dp),
                    )
                }
                if (truncated) {
                    Text(
                        "…（已截断显示）",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}
