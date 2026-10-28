package com.artem.myarchive.ui.viewers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.artem.myarchive.data.Attachment
import com.artem.myarchive.data.AttachmentKind
import com.artem.myarchive.repository
import com.artem.myarchive.ui.formatSize
import com.artem.myarchive.ui.icon

/** Открывает вложение подходящим встроенным просмотрщиком в зависимости от типа файла. */
@Composable
fun FileViewerScreen(attachmentId: Long, onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val result by produceState<Result<Attachment?>?>(initialValue = null, attachmentId) {
        value = runCatching { repo.getAttachment(attachmentId) }
    }
    val loaded = result ?: return
    val attachment = loaded.getOrNull()
    if (attachment == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val file = repo.files.file(attachment)
    val isMedia = attachment.kind == AttachmentKind.VIDEO || attachment.kind == AttachmentKind.AUDIO

    ViewerScaffold(title = attachment.displayName, attachment = attachment, onBack = onBack, dark = isMedia) { modifier ->
        when (attachment.kind) {
            AttachmentKind.VIDEO, AttachmentKind.AUDIO ->
                MediaPlayer(file, isAudio = attachment.kind == AttachmentKind.AUDIO, modifier = modifier)
            AttachmentKind.PDF -> PdfViewer(file, attachment, modifier)
            AttachmentKind.DOCX -> DocxViewer(file, attachment, modifier)
            AttachmentKind.TEXT -> TextViewer(file, modifier)
            AttachmentKind.IMAGE, AttachmentKind.OTHER -> UnsupportedFile(attachment, modifier)
        }
    }
}

@Composable
fun Loading(modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Заглушка для форматов, которые нельзя показать внутри приложения (например, старый .doc, .xlsx, .pptx). */
@Composable
fun UnsupportedFile(attachment: Attachment, modifier: Modifier, reason: String? = null) {
    val repo = LocalContext.current.repository
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(attachment.kind.icon(), null, modifier = Modifier.size(72.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(attachment.displayName, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(formatSize(attachment.size), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(16.dp))
        Text(
            reason ?: "Этот формат нельзя показать прямо в приложении. Его можно открыть в другом приложении на телефоне.",
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = { repo.files.openExternally(attachment) }) { Text("Открыть в другом приложении") }
    }
}

