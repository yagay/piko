package dev.piko.ui.screens.instant

import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import dev.piko.ui.components.PikoDialog
import dev.piko.ui.components.PikoDialogConfirm
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.piko.shared.i18n.tr
import dev.piko.shared.state.InstantDraft

/** Standalone Android dialog host: no drive screen, navigation drawer or main scaffold. */
@Composable
fun ExternalMagnetImportHost(
    draft: InstantDraft,
    onDismiss: () -> Unit,
    onPreview: (String, String) -> Unit,
) {
    var confirmDiscard by remember(draft) { mutableStateOf(false) }
    val requestClose = {
        if (draft.endNeedsConfirm) confirmDiscard = true else onDismiss()
    }
    BackHandler { requestClose() }
    if (confirmDiscard) {
        PikoDialog(
            onDismissRequest = { confirmDiscard = false },
            title = { Text(tr("放弃选择？", "Discard selection?", "選択を破棄しますか？", "선택을 취소할까요?", "放棄選取？")) },
            text = { Text(tr("尚未保存的文件勾选将丢失。", "Unsaved file selections will be lost.", "未保存の選択は失われます。", "저장하지 않은 선택이 사라집니다.", "未儲存的勾選將遺失。")) },
            confirmButton = {
                PikoDialogConfirm(
                    tr("关闭", "Close", "閉じる", "닫기", "關閉"),
                    destructive = true,
                    onClick = { confirmDiscard = false; onDismiss() },
                )
            },
            dismissButton = {
                TextButton(onClick = { confirmDiscard = false }) {
                    Text(tr("继续选择", "Keep selecting", "選択を続ける", "선택 계속", "繼續選擇"))
                }
            },
        )
    }
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                tr("保存磁力", "Save magnet", "マグネットを保存", "마그넷 저장", "儲存磁力"),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = requestClose) {
                Icon(Icons.Outlined.Close, contentDescription = tr("关闭", "Close", "閉じる", "닫기", "關閉"))
            }
        }
        InstantSheetContent(
            state = draft.state,
            inSideSheet = true,
            headerShown = true,
            fillHeight = true,
            onPreview = onPreview,
        )
    }
}
