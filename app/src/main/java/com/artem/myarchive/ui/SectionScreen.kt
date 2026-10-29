package com.artem.myarchive.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artem.myarchive.data.EntryWithAttachments
import com.artem.myarchive.data.Section
import com.artem.myarchive.data.SectionItem
import com.artem.myarchive.data.images
import com.artem.myarchive.repository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

private sealed interface SectionDialog {
    data object Add : SectionDialog
    data class Rename(val section: Section) : SectionDialog
    data class Delete(val section: Section) : SectionDialog
    data class DeleteSelected(val sections: Set<Long>, val entries: Set<Long>) : SectionDialog
    data class Move(val sections: Set<Long>, val entries: Set<Long>) : SectionDialog
}

/**
 * Экран раздела. При [sectionId] == null — главный экран со списком разделов верхнего уровня.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SectionScreen(
    sectionId: Long?,
    onBack: () -> Unit,
    onOpenSection: (Long) -> Unit,
    onOpenEntry: (Long) -> Unit,
    onAddEntry: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val repo = context.repository
    val scope = rememberCoroutineScope()
    val isRoot = sectionId == null

    val section: Section? by remember(sectionId) {
        if (sectionId == null) flowOf(null) else repo.section(sectionId)
    }.collectAsState(initial = null)
    val subsections: List<SectionItem>? by remember(sectionId) { repo.sections(sectionId) }.collectAsState(initial = null)
    val entries: List<EntryWithAttachments>? by remember(sectionId) {
        if (sectionId == null) flowOf(emptyList<EntryWithAttachments>()) else repo.entries(sectionId)
    }.collectAsState(initial = null)

    var dialog by remember { mutableStateOf<SectionDialog?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var addMenuOpen by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }

    // Если текущий раздел удалили — возвращаемся назад.
    LaunchedEffect(deleted) { if (deleted) onBack() }

    // Режим выбора: долгое нажатие на раздел/запись или кнопка «Отправить» в шапке.
    var selecting by remember(sectionId) { mutableStateOf(false) }
    var selectedSections by remember(sectionId) { mutableStateOf(emptySet<Long>()) }
    var selectedEntries by remember(sectionId) { mutableStateOf(emptySet<Long>()) }
    val selectedCount = selectedSections.size + selectedEntries.size
    fun clearSelection() {
        selecting = false
        selectedSections = emptySet()
        selectedEntries = emptySet()
    }
    fun toggleSection(id: Long) {
        selecting = true
        selectedSections = if (id in selectedSections) selectedSections - id else selectedSections + id
    }
    fun toggleEntry(id: Long) {
        selecting = true
        selectedEntries = if (id in selectedEntries) selectedEntries - id else selectedEntries + id
    }
    BackHandler(enabled = selecting) { clearSelection() }

    val shareAction = rememberShareAction()
    val importAction = rememberImportAction()
    val pickZip = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) importAction.import(uri, sectionId)
    }
    val rootTitle = "Инженерный справочник"
    fun shareSelected() {
        val oneSection = selectedSections.singleOrNull()?.takeIf { selectedEntries.isEmpty() }
        val oneEntry = selectedEntries.singleOrNull()?.takeIf { selectedSections.isEmpty() }
        val name = oneSection?.let { id -> subsections?.find { it.section.id == id }?.section?.title }
            ?: oneEntry?.let { id -> entries?.find { it.entry.id == id }?.entry?.title }
            ?: section?.title
            ?: rootTitle
        shareAction.share(name, selectedSections.toList(), selectedEntries.toList()) { clearSelection() }
    }

    Scaffold(
        topBar = {
            if (selecting) {
                TopAppBar(
                    title = { Text(if (selectedCount == 0) "Выберите, что отправить" else "Выбрано: $selectedCount") },
                    navigationIcon = {
                        IconButton(onClick = { clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Отменить выбор")
                        }
                    },
                    actions = {
                        if (selectedSections.size == 1 && selectedEntries.isEmpty()) {
                            val id = selectedSections.first()
                            IconButton(onClick = {
                                subsections?.find { it.section.id == id }?.let { dialog = SectionDialog.Rename(it.section) }
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Переименовать")
                            }
                        }
                        IconButton(
                            onClick = { dialog = SectionDialog.Move(selectedSections, selectedEntries) },
                            enabled = selectedCount > 0,
                        ) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "Переместить")
                        }
                        IconButton(
                            onClick = { dialog = SectionDialog.DeleteSelected(selectedSections, selectedEntries) },
                            enabled = selectedCount > 0,
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить")
                        }
                        IconButton(onClick = { shareSelected() }, enabled = selectedCount > 0) {
                            Icon(Icons.Default.Share, contentDescription = "Отправить")
                        }
                    },
                )
            } else TopAppBar(
                title = {
                    Text(
                        if (isRoot) rootTitle else section?.title.orEmpty(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (!isRoot) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    }
                },
                actions = {
                    if (isRoot) {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Default.Palette, contentDescription = "Оформление")
                        }
                    }
                    IconButton(onClick = { pickZip.launch(arrayOf("*/*")) }) {
                        Icon(Icons.Default.Unarchive, contentDescription = "Импорт из zip")
                    }
                    if (!subsections.isNullOrEmpty() || !entries.isNullOrEmpty()) {
                        IconButton(onClick = { selecting = true }) {
                            Icon(Icons.Default.Share, contentDescription = "Выбрать и отправить")
                        }
                    }
                    val current = section
                    if (current != null) {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Переименовать") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { menuOpen = false; dialog = SectionDialog.Rename(current) },
                            )
                            DropdownMenuItem(
                                text = { Text("Отправить весь раздел") },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    shareAction.share(current.title, listOf(current.id), emptyList())
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Удалить раздел") },
                                leadingIcon = { Icon(Icons.Default.Delete, null) },
                                onClick = { menuOpen = false; dialog = SectionDialog.Delete(current) },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            val isEmpty = subsections.isNullOrEmpty() && entries.isNullOrEmpty()
            if (!isEmpty && !selecting) {
                if (isRoot) {
                    ExtendedFloatingActionButton(
                        onClick = { dialog = SectionDialog.Add },
                        icon = { Icon(Icons.Default.Add, null) },
                        text = { Text("Добавить раздел") },
                    )
                } else {
                    Box {
                        ExtendedFloatingActionButton(
                            onClick = { addMenuOpen = true },
                            icon = { Icon(Icons.Default.Add, null) },
                            text = { Text("Добавить") },
                        )
                        DropdownMenu(expanded = addMenuOpen, onDismissRequest = { addMenuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Подраздел") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, null) },
                                onClick = { addMenuOpen = false; dialog = SectionDialog.Add },
                            )
                            DropdownMenuItem(
                                text = { Text("Запись") },
                                leadingIcon = { Icon(Icons.Default.NoteAdd, null) },
                                onClick = { addMenuOpen = false; onAddEntry(sectionId!!) },
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        val subs = subsections
        val list = entries
        when {
            subs == null || list == null -> Unit // загрузка
            subs.isEmpty() && list.isEmpty() -> EmptyState(
                isRoot = isRoot,
                modifier = Modifier.padding(padding),
                onAddSection = { dialog = SectionDialog.Add },
                onAddEntry = { sectionId?.let(onAddEntry) },
            )
            else -> LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding() + 8.dp,
                    bottom = padding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (subs.isNotEmpty()) {
                    item { Header(if (isRoot) "Разделы" else "Подразделы") }
                    items(subs, key = { "s" + it.section.id }) { item ->
                        val id = item.section.id
                        SectionCard(
                            item = item,
                            selected = id in selectedSections,
                            onClick = { if (selecting) toggleSection(id) else onOpenSection(id) },
                            onLongClick = { toggleSection(id) },
                        )
                    }
                }
                if (list.isNotEmpty()) {
                    item { Header("Записи") }
                    items(list, key = { "e" + it.entry.id }) { entry ->
                        val id = entry.entry.id
                        EntryCard(
                            item = entry,
                            selected = id in selectedEntries,
                            onClick = { if (selecting) toggleEntry(id) else onOpenEntry(id) },
                            onLongClick = { toggleEntry(id) },
                        )
                    }
                }
            }
        }
    }

    ShareProgress(shareAction)
    ImportProgress(importAction)

    when (val d = dialog) {
        null -> Unit
        SectionDialog.Add -> NameDialog(
            title = if (isRoot) "Новый раздел" else "Новый подраздел",
            confirmText = "Создать",
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                scope.launch { repo.addSection(sectionId, name) }
            },
        )
        is SectionDialog.Rename -> NameDialog(
            title = "Переименовать раздел",
            initial = d.section.title,
            onDismiss = { dialog = null },
            onConfirm = { name ->
                dialog = null
                scope.launch { repo.renameSection(d.section, name) }
                clearSelection()
            },
        )
        is SectionDialog.Move -> MoveDialog(
            movingSections = d.sections,
            hasEntries = d.entries.isNotEmpty(),
            startId = sectionId,
            onDismiss = { dialog = null },
            onMove = { target ->
                dialog = null
                clearSelection()
                scope.launch {
                    val skipped = repo.move(d.sections, d.entries, target)
                    if (skipped > 0) {
                        Toast.makeText(context, "Раздел нельзя переместить внутрь самого себя", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(context, "Перемещено", Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
        is SectionDialog.DeleteSelected -> ConfirmDialog(
            title = "Удалить выбранное?",
            text = buildString {
                append("Будет удалено: ")
                append(
                    listOfNotNull(
                        d.sections.size.takeIf { it > 0 }?.let { plural(it, "раздел", "раздела", "разделов") },
                        d.entries.size.takeIf { it > 0 }?.let { plural(it, "запись", "записи", "записей") },
                    ).joinToString(" и ")
                )
                append(". Разделы удаляются вместе со всеми подразделами, записями и файлами.")
            },
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                clearSelection()
                scope.launch { repo.deleteAll(d.sections, d.entries) }
            },
        )
        is SectionDialog.Delete -> ConfirmDialog(
            title = "Удалить раздел?",
            text = "Раздел «${d.section.title}» будет удалён вместе со всеми подразделами, записями и файлами.",
            onDismiss = { dialog = null },
            onConfirm = {
                dialog = null
                val isCurrent = d.section.id == sectionId
                scope.launch {
                    repo.deleteSection(d.section.id)
                    if (isCurrent) deleted = true
                }
            },
        )
    }
}

@Composable
private fun Header(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
    )
}

@Composable
private fun EmptyState(
    isRoot: Boolean,
    modifier: Modifier,
    onAddSection: () -> Unit,
    onAddEntry: () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            if (isRoot) "Здесь пока пусто.\nСоздайте первый раздел для своей информации."
            else "В этом разделе пока ничего нет.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onAddSection, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.CreateNewFolder, null)
            Spacer(Modifier.width(8.dp))
            Text(if (isRoot) "Добавить раздел" else "Добавить подраздел")
        }
        if (!isRoot) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onAddEntry, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Icon(Icons.Default.NoteAdd, null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить запись")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SectionCard(
    item: SectionItem,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = CardShape,
        colors = cardColors(selected),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (selected) Icons.Default.CheckCircle else Icons.Default.Folder,
                null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    item.section.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val parts = buildList {
                    if (item.subCount > 0) add(plural(item.subCount, "подраздел", "подраздела", "подразделов"))
                    if (item.entryCount > 0) add(plural(item.entryCount, "запись", "записи", "записей"))
                }
                Text(
                    if (parts.isEmpty()) "Пусто" else parts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.Default.ChevronRight, null)
        }
    }
}

@Composable
private fun cardColors(selected: Boolean) =
    if (selected) CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    else CardDefaults.cardColors()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EntryCard(
    item: EntryWithAttachments,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        shape = CardShape,
        colors = cardColors(selected),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
            val cover = item.images.firstOrNull()
            if (cover != null) {
                val context = LocalContext.current
                AsyncImage(
                    model = context.repository.files.file(cover),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(64.dp).clip(RoundedCornerShape(10.dp)),
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    item.entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (item.entry.description.isNotBlank()) {
                    Text(
                        item.entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.attachments.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Icon(Icons.Default.AttachFile, null, modifier = Modifier.size(16.dp))
                        Text(
                            plural(item.attachments.size, "вложение", "вложения", "вложений"),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

private val CardShape = RoundedCornerShape(16.dp)
