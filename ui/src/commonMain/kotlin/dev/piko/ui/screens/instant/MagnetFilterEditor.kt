package dev.piko.ui.screens.instant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.piko.data.repository.FileCategory
import dev.piko.data.repository.label
import dev.piko.shared.i18n.tr
import dev.piko.shared.state.InstantSheetState
import dev.piko.shared.state.MagnetFileSelectionFilter
import dev.piko.ui.components.PikoDialog
import dev.piko.ui.components.PikoDialogConfirm

/** Changes remain local until confirmed; closing the dialog never edits the selection. */
@Composable
internal fun MagnetFilterEditor(state: InstantSheetState, onDismiss: () -> Unit) {
    val initial = state.magnetFilter
    var enabled by remember(state) { mutableStateOf(initial.enabled) }
    var categories by remember(state) { mutableStateOf(initial.categories) }
    var minText by remember(state) { mutableStateOf(initial.minMb?.toString().orEmpty()) }
    var maxText by remember(state) { mutableStateOf(initial.maxMb?.toString().orEmpty()) }
    val min = minText.takeUnless(String::isBlank)?.toIntOrNull()
    val max = maxText.takeUnless(String::isBlank)?.toIntOrNull()
    val valid = categories.isNotEmpty() &&
        (minText.isBlank() || min != null) && (maxText.isBlank() || max != null) &&
        (min == null || min in 0..MagnetFileSelectionFilter.MAX_MB) &&
        (max == null || max in 0..MagnetFileSelectionFilter.MAX_MB) &&
        (min == null || max == null || min <= max)

    PikoDialog(
        onDismissRequest = onDismiss,
        title = { Text(tr("磁力默认筛选", "Magnet filter", "マグネットのフィルター", "마그넷 필터", "磁力預設篩選")) },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        tr("自动选择符合规则的文件", "Auto-select matching files", "一致するファイルを自動選択", "일치하는 파일 자동 선택", "自動選取符合規則的檔案"),
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Checkbox(enabled, onCheckedChange = { enabled = it }, enabled = state.canEdit)
                }
                Text(tr("文件类型（可多选）", "File types (multi-select)", "ファイル種類（複数可）", "파일 유형 (복수 선택)", "檔案類型（可多選）"),
                    style = MaterialTheme.typography.labelMedium)
                FileCategory.entries.chunked(3).forEach { group ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        group.forEach { category ->
                            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = category in categories,
                                    onCheckedChange = { checked ->
                                        categories = if (checked) categories + category else categories - category
                                    },
                                    modifier = Modifier.size(40.dp),
                                    enabled = state.canEdit,
                                )
                                Text(category.label, maxLines = 1, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = minText,
                        onValueChange = { minText = it.filter(Char::isDigit).take(7) },
                        label = { Text(tr("最小 MB", "Min MB", "最小 MB", "최소 MB", "最小 MB")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = state.canEdit,
                        isError = !valid,
                    )
                    OutlinedTextField(
                        value = maxText,
                        onValueChange = { maxText = it.filter(Char::isDigit).take(7) },
                        label = { Text(tr("最大 MB", "Max MB", "最大 MB", "최대 MB", "最大 MB")) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = state.canEdit,
                        isError = !valid,
                    )
                }
                Text(
                    if (valid) tr("大小留空表示不限；原始文件列表仍可手动勾选。", "Blank size = unlimited. You can still pick files manually.", "空欄は無制限。手動選択も可能です。", "빈칸은 무제한이며 수동 선택도 가능합니다.", "大小留空表示不限，仍可手動勾選。")
                    else tr("请至少选择一种分类，并输入有效的大小范围。", "Select a category and enter valid size limits.", "種類を選択し、有効なサイズを入力してください。", "유형과 올바른 크기를 입력하세요.", "請至少選一種分類並輸入有效大小。"),
                    color = if (valid) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
                TextButton(onClick = {
                    val defaults = MagnetFileSelectionFilter(enabled = enabled)
                    categories = defaults.categories
                    minText = defaults.minMb?.toString().orEmpty()
                    maxText = defaults.maxMb?.toString().orEmpty()
                }) { Text(tr("恢复默认规则", "Reset defaults", "初期設定に戻す", "기본값 복원", "還原預設規則")) }
            }
        },
        confirmButton = {
            PikoDialogConfirm(
                label = tr("保存并应用", "Save & apply", "保存して適用", "저장 및 적용", "儲存並套用"),
                enabled = state.canEdit && valid,
                onClick = {
                    if (state.updateMagnetFilter(min, max, categories)) {
                        state.setMagnetFilterEnabled(enabled)
                        onDismiss()
                    }
                },
            )
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(tr("取消", "Cancel", "キャンセル", "취소", "取消")) } },
    )
}
