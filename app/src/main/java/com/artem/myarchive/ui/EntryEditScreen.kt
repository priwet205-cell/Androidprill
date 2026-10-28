package com.artem.myarchive.ui

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.artem.myarchive.App
import com.artem.myarchive.data.Attachment
import com.artem.myarchive.data.AttachmentKind
import com.artem.myarchive.repository
import kotlinx.coroutines.launch

class EntryEditViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as App).repository

    var title by mutableStateOf("")
    var description by mutableStateOf("")
    val attachments = mutableStateListOf<Attachment>()
    private val removed = mutableListOf<Attachment>()
    /** Файлы, скопированные в этой сессии редактирования, — удаляются, если запись не сохранили. */
    private val imported = mutableListOf<Attachment>()

    var importing by mutableIntStateOf(0)
        private set
    var saved by mutableStateOf(false)
        private set
    var showTitleError by mutableStateOf(false)

    private var initialized = false
    private var entryId: Long? = null
    private var sectionId: Long = 0

    fun init(sectionId: Long, entryId: Long?) {
        if (initialized) return
        initialized = true
        this.sectionId = sectionId
        this.entryId = entryId
        if (entryId != null) {
            viewModelScope.launch {
                val e = repo.getEntry(entryId) ?: return@launch
                title = e.entry.title
                description = e.entry.description
                attachments.addAll(e.attachments.sortedBy { it.position })
            }
        }
    }

    fun import(uris: List<Uri>, onError: (String) -> Unit) {
        uris.forEach { uri ->
            importing++
            viewModelScope.launch {
                try {
                    val a = repo.files.import(uri)
                    imported += a
                    attachments += a
                } catch (e: Exception) {
                    onError("Не удалось добавить файл: ${e.message}")
                } finally {
                    importing--
                }
            }
        }
    }

    fun remove(a: Attachment) {
        attachments.remove(a)
        if (a.id != 0L) removed += a else {
            imported.remove(a)
            repo.files.file(a).delete()
        }
    }

    fun save() {
        if (title.isBlank()) {
            showTitleError = true
            return
        }
        viewModelScope.launch {
            repo.saveEntry(entryId, sectionId, title, description, attachments.toList(), removed.toList())
            imported.clear()
            saved = true
        }
    }

    override fun onCleared() {
        // Пользователь вышел без сохранения — убираем скопированные файлы.
        imported.forEach { repo.files.file(it).delete() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryEditScreen(sectionId: Long, entryId: Long?, onDone: () -> Unit) {
    val vm: EntryEditViewModel = viewModel()
    LaunchedEffect(Unit) { vm.init(sectionId, entryId) }
    LaunchedEffect(vm.saved) { if (vm.saved) onDone() }

    val context = LocalContext.current
    val onError: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    val pickMedia = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) {
        vm.import(it, onError)
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) {
        vm.import(it, onError)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (entryId == null) "Новая запись" else "Редактирование") },
                navigationIcon = {
                    IconButton(onClick = onDone) { Icon(Icons.Default.Close, contentDescription = "Отмена") }
                },
                actions = {
                    IconButton(onClick = vm::save, enabled = vm.importing == 0) {
                        Icon(Icons.Default.Check, contentDescription = "Сохранить")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = vm.title,
                onValueChange = { vm.title = it; vm.showTitleError = false },
                label = { Text("Название") },
                isError = vm.showTitleError,
                supportingText = { if (vm.showTitleError) Text("Введите название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = vm.description,
                onValueChange = { vm.description = it },
                label = { Text("Описание") },
                minLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Default.AddPhotoAlternate, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Фото / видео")
                }
                OutlinedButton(onClick = { pickFiles.launch(arrayOf("*/*")) }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.AttachFile, null)
                    Spacer(Modifier.width(6.dp))
                    Text("Документ")
                }
            }

            if (vm.importing > 0) {
                Text("Копирование файлов…", style = MaterialTheme.typography.bodySmall)
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }

            val images = vm.attachments.filter { it.kind == AttachmentKind.IMAGE }
            val others = vm.attachments.filter { it.kind != AttachmentKind.IMAGE }

            if (images.isNotEmpty()) {
                Text("Фотографии", style = MaterialTheme.typography.titleSmall)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(images, key = { it.fileName }) { a ->
                        Box {
                            AsyncImage(
                                model = context.repository.files.file(a),
                                contentDescription = a.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(110.dp).clip(RoundedCornerShape(12.dp)),
                            )
                            RemoveButton(Modifier.align(Alignment.TopEnd)) { vm.remove(a) }
                        }
                    }
                }
            }

            if (others.isNotEmpty()) {
                Text("Файлы", style = MaterialTheme.typography.titleSmall)
                others.forEach { a ->
                    ListItem(
                        headlineContent = { Text(a.displayName, maxLines = 2) },
                        supportingContent = { Text(formatSize(a.size)) },
                        leadingContent = { Icon(a.kind.icon(), null) },
                        trailingContent = {
                            IconButton(onClick = { vm.remove(a) }) { Icon(Icons.Default.Close, "Убрать") }
                        },
                    )
                }
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = vm::save,
                enabled = vm.importing == 0,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (vm.importing > 0) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun RemoveButton(modifier: Modifier, onClick: () -> Unit) {
    FilledTonalIconButton(
        onClick = onClick,
        modifier = modifier.padding(4.dp).size(28.dp),
        shape = CircleShape,
        colors = IconButtonDefaults.filledTonalIconButtonColors(),
    ) {
        Icon(Icons.Default.Close, contentDescription = "Убрать", modifier = Modifier.size(16.dp))
    }
}
