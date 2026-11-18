package dev.piko.ui.screens.instant

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
            IconButton(onClick = onDismiss) {
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
