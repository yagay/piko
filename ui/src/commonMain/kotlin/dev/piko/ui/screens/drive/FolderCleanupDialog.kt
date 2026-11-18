package dev.piko.ui.screens.drive

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.piko.ui.FolderAutoCleanup
import dev.piko.ui.FolderCleanupRule
import io.github.nihildigit.pikpak.FileStat
import kotlinx.coroutines.launch

/**
 * First use requires an explicit preview. Automated checks happen only when opening
 * the configured folder, and all deletions go to PikPak's recycle bin.
 */
@Composable
internal fun FolderCleanupDialog(
    account: String,
    folderId: String,
    folderName: String,
    cleanup: FolderAutoCleanup,
    onDismiss: () -> Unit,
    onMessage: (String) -> Unit,
) {
    val existing = remember(account, folderId) { cleanup.getRule(account, folderId) }
    var sizeText by remember(account, folderId) { mutableStateOf(existing.maxSizeMb.toString()) }
    var enabled by remember(account, folderId) { mutableStateOf(existing.enabled) }
    var candidates by remember(account, folderId) { mutableStateOf<List<FileStat>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var errorText by remember { mutableStateOf<String?>(null) }
    var confirmNow by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val maxMb = sizeText.toIntOrNull()?.takeIf { it in 1..102400 }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("文件夹自动清理 · $folderName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("只检查这个云端文件夹的直属文件；大小为 0、大小未知、子文件夹及内部文件跳过。匹配文件移入 PikPak 回收站，不永久删除。")
                OutlinedTextField(
                    value = sizeText,
                    onValueChange = { sizeText = it.filter(Char::isDigit); candidates = null; confirmNow = false },
                    label = { Text("文件大小上限 (MB)，含等于") },
                    singleLine = true,
                    isError = maxMb == null,
                )
                androidx.compose.foundation.layout.Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("进入此文件夹时自动检查", modifier = Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                Text("默认 500 MB = 500,000,000 字节。更改阈值后必须重新预览才能保存。")
                Button(onClick = {
                    val value = maxMb ?: return@Button
                    busy = true
                    errorText = null
                    scope.launch {
                        cleanup.preview(account, folderId, value)
                            .onSuccess { candidates = it }
                            .onFailure { errorText = it.message ?: "扫描失败" }
                        busy = false
                    }
                }, enabled = !busy && maxMb != null) { Text(if (busy) "正在扫描…" else "扫描并预览") }
                candidates?.let { files ->
                    Text("匹配 ${files.size} 个文件，合计 ${files.sumOf { it.sizeBytes } / 1_000_000L} MB")
                    LazyColumn(modifier = Modifier.heightIn(max = 180.dp)) {
                        items(files.take(30), key = { it.id }) { file ->
                            Text("${file.name} · ${file.sizeBytes / 1_000_000L} MB")
                        }
                        if (files.size > 30) item { Text("还有 ${files.size - 30} 个文件…") }
                    }
                    if (files.isNotEmpty()) {
                        if (confirmNow) {
                            Text("请再次确认：现在将重新扫描并把所有匹配文件移入回收站。")
                            TextButton(enabled = !busy, onClick = {
                                val value = maxMb ?: return@TextButton
                                busy = true
                                scope.launch {
                                    cleanup.trashMatching(account, folderId, value)
                                        .onSuccess { count -> onMessage("已移入回收站 $count 个文件"); onDismiss() }
                                        .onFailure { errorText = it.message ?: "清理失败" }
                                    busy = false
                                }
                            }) { Text("确认立即清理") }
                        } else {
                            TextButton(enabled = !busy, onClick = { confirmNow = true }) { Text("立即清理预览中的匹配文件…") }
                        }
                    }
                }
                errorText?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !busy && maxMb != null && candidates != null,
                onClick = {
                    val value = maxMb ?: return@TextButton
                    cleanup.saveRule(account, folderId, FolderCleanupRule(value, enabled))
                    onMessage(if (enabled) "已开启自动清理：下次进入此文件夹检查" else "已保存规则（自动清理关闭）")
                    onDismiss()
                },
            ) { Text("保存规则") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
}
