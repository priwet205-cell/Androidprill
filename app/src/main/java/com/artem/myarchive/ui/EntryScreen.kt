package com.artem.myarchive.ui

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artem.myarchive.data.AttachmentKind
import com.artem.myarchive.data.Entry
import com.artem.myarchive.data.EntryWithAttachments
import com.artem.myarchive.data.files
import com.artem.myarchive.data.images
import com.artem.myarchive.repository
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EntryScreen(
    entryId: Long,
    onBack: () -> Unit,
    onEdit: (sectionId: Long) -> Unit,
    onOpenImage: (index: Int) -> Unit,
    onOpenFile: (attachmentId: Long) -> Unit,
) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()

    // LOADING — запись ещё загружается, null — запись удалена.
    val state: EntryWithAttachments? by remember(entryId) { repo.entry(entryId) }.collectAsState(initial = LOADING)
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(state) { if (state == null) onBack() }
    val item = state ?: return
    if (item === LOADING) return

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(item.entry.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    IconButton(onClick = { onEdit(item.entry.sectionId) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Редактировать")
                    }
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Удалить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(item.entry.title, style = MaterialTheme.typography.headlineSmall)
            Text(
                "Изменено: " + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                    .format(Date(item.entry.updatedAt)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.entry.description.isNotBlank()) {
                SelectionContainer {
                    Text(item.entry.description, style = MaterialTheme.typography.bodyLarge)
                }
            }

            val images = item.images
            if (images.isNotEmpty()) {
                Text("Фотографии", style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    images.forEachIndexed { index, a ->
                        AsyncImage(
                            model = repo.files.file(a),
                            contentDescription = a.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(104.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onOpenImage(index) },
                        )
                    }
                }
            }

            val files = item.files
            if (files.isNotEmpty()) {
                Text("Файлы", style = MaterialTheme.typography.titleMedium)
                files.forEach { a ->
                    Card(onClick = { onOpenFile(a.id) }, modifier = Modifier.fillMaxWidth()) {
                        if (a.kind == AttachmentKind.VIDEO) {
                            // Превью видео (первый кадр) с кнопкой воспроизведения.
                            Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                                AsyncImage(
                                    model = repo.files.file(a),
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                                Icon(
                                    Icons.Default.PlayCircle,
                                    contentDescription = "Воспроизвести",
                                    tint = Color.White,
                                    modifier = Modifier.size(56.dp),
                                )
                            }
                        }
                        ListItem(
                            headlineContent = { Text(a.displayName, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text(formatSize(a.size)) },
                            leadingContent = { Icon(a.kind.icon(), null) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = "Удалить запись?",
            text = "Запись «${item.entry.title}» и все её файлы будут удалены.",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                scope.launch { repo.deleteEntry(entryId) } // экран закроется сам, когда запись исчезнет
            },
        )
    }
}

private val LOADING = EntryWithAttachments(
    entry = Entry(id = -1, sectionId = -1, title = "", description = ""),
    attachments = emptyList(),
)
