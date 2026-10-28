package com.artem.myarchive.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.artem.myarchive.data.Section
import com.artem.myarchive.data.SectionItem
import com.artem.myarchive.repository
import kotlinx.coroutines.flow.flowOf

/**
 * Окно выбора папки для переноса. Можно заходить в подразделы и возвращаться назад.
 * [movingSections] не показываются (нельзя перенести раздел внутрь самого себя).
 * Записи нельзя положить на главный экран — только в раздел.
 */
@Composable
fun MoveDialog(
    movingSections: Set<Long>,
    hasEntries: Boolean,
    startId: Long?,
    onDismiss: () -> Unit,
    onMove: (targetId: Long?) -> Unit,
) {
    val repo = LocalContext.current.repository
    var currentId by remember { mutableStateOf(startId) }
    val current: Section? by remember(currentId) {
        currentId?.let { repo.section(it) } ?: flowOf(null)
    }.collectAsState(initial = null)
    val children: List<SectionItem> by remember(currentId) { repo.sections(currentId) }
        .collectAsState(initial = emptyList())

    val atRoot = currentId == null
    val canMoveHere = !(hasEntries && atRoot) && currentId != startId

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Переместить в…") },
        text = {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = !atRoot) { current?.let { currentId = it.parentId } }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(if (atRoot) Icons.Default.Home else Icons.AutoMirrored.Filled.ArrowBack, null)
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (atRoot) "Главный экран" else current?.title.orEmpty(),
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                HorizontalDivider()
                val visible = children.filter { it.section.id !in movingSections }
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    if (visible.isEmpty()) {
                        item {
                            Text(
                                "Подразделов нет",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        }
                    }
                    items(visible, key = { it.section.id }) { item ->
                        ListItem(
                            headlineContent = { Text(item.section.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            leadingContent = { Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary) },
                            trailingContent = { Icon(Icons.Default.ChevronRight, null) },
                            modifier = Modifier.clickable { currentId = item.section.id },
                        )
                    }
                }
                if (hasEntries && atRoot) {
                    Text(
                        "Записи можно перенести только в раздел — откройте нужный.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onMove(currentId) }, enabled = canMoveHere) { Text("Переместить сюда") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
