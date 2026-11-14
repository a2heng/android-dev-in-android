package com.a2heng.opencode.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.a2heng.opencode.mobile.data.AppModel

@Composable
fun ConnectScreen(model: AppModel) {
    var url by rememberSaveable { mutableStateOf(model.serverUrl) }
    var password by rememberSaveable { mutableStateOf(model.password) }
    var showPassword by remember { mutableStateOf(false) }
    var savedMenuOpen by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Terminal,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text("Adia", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                "连接局域网里的 opencode 服务：会话、流式回答、缓存命中率、文件与终端。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(26.dp))

            OutlinedTextField(
                value = url,
                onValueChange = {
                    // 正在连旧地址 / 显示旧错误时改配置：立刻停下，停在当前页面
                    if (model.connecting || model.connectionError != null) model.cancelConnect()
                    url = it
                    // 输入到已保存的地址时，直接把密码带出来
                    model.savedConnectionFor(it)?.let { saved -> password = saved.password }
                },
                label = { Text("服务器地址") },
                placeholder = { Text("http://192.168.1.100:42000") },
                leadingIcon = { Icon(Icons.Rounded.Dns, contentDescription = null) },
                trailingIcon = if (model.savedConnections.isEmpty()) {
                    null
                } else {
                    {
                        Box {
                            IconButton(onClick = { savedMenuOpen = true }) {
                                Icon(
                                    Icons.Rounded.ArrowDropDown,
                                    contentDescription = "已保存的服务器",
                                    tint = MaterialTheme.colorScheme.primary,
                                )
                            }
                            DropdownMenu(
                                expanded = savedMenuOpen,
                                onDismissRequest = { savedMenuOpen = false },
                            ) {
                                model.savedConnections.forEach { saved ->
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    prettyServer(saved.url),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.widthIn(max = 210.dp),
                                                )
                                                Text(
                                                    if (saved.password.isBlank()) "无密码" else "已保存密码",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        trailingIcon = {
                                            IconButton(
                                                onClick = { model.forgetConnection(saved.url) },
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteOutline,
                                                    contentDescription = "删除",
                                                    modifier = Modifier.size(17.dp),
                                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                )
                                            }
                                        },
                                        onClick = {
                                            url = saved.url
                                            password = saved.password
                                            savedMenuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )
            if (model.savedConnections.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "点地址栏右侧箭头可从 ${model.savedConnections.size} 个已保存的连接里选择",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    if (model.connecting || model.connectionError != null) model.cancelConnect()
                    password = it
                },
                label = { Text("密码") },
                leadingIcon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
                trailingIcon = {
                    Text(
                        if (showPassword) "隐藏" else "显示",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .clickableNoRipple { showPassword = !showPassword },
                    )
                },
                singleLine = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
            )

            if (model.connectionError != null) {
                Spacer(Modifier.height(12.dp))
                Text(
                    model.connectionError ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            Spacer(Modifier.height(18.dp))
            Button(
                onClick = {
                    if (model.connecting) model.cancelConnect() else model.connect(url, password)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                if (model.connecting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(Modifier.size(10.dp))
                    Icon(Icons.Rounded.Close, contentDescription = null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("取消连接")
                } else {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("连接")
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "提示：手机与电脑需在同一局域网；服务端启动参数 --hostname 0.0.0.0。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** 保存列表里去掉协议前缀，只显示 host:port，省地方。 */
private fun prettyServer(url: String): String =
    url.removePrefix("http://").removePrefix("https://").trimEnd('/')
