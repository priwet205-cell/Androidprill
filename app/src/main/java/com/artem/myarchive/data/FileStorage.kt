package com.artem.myarchive.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/**
 * Хранит копии выбранных пользователем файлов во внутренней памяти приложения,
 * чтобы они оставались доступны, даже если исходник удалят или перенесут.
 */
class FileStorage(private val context: Context) {

    private val dir: File = File(context.filesDir, "attachments").apply { mkdirs() }

    fun file(attachment: Attachment): File = File(dir, attachment.fileName)

    /** Копирует файл по [uri] во внутреннее хранилище. entryId заполняется при сохранении записи. */
    suspend fun import(uri: Uri): Attachment = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        var name: String? = null
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) name = c.getString(0)
        }
        val displayName = name ?: uri.lastPathSegment ?: "file"
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val mime = resolver.getType(uri)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: "application/octet-stream"

        val fileName = UUID.randomUUID().toString() + if (ext.isNotEmpty()) ".$ext" else ""
        val target = File(dir, fileName)
        val input = resolver.openInputStream(uri) ?: error("Не удалось открыть файл")
        input.use { src -> target.outputStream().use { dst -> src.copyTo(dst) } }

        Attachment(
            entryId = 0,
            displayName = displayName,
            mimeType = mime,
            fileName = fileName,
            kind = detectKind(mime, ext),
            size = target.length(),
        )
    }

    suspend fun delete(attachments: List<Attachment>) = withContext(Dispatchers.IO) {
        attachments.forEach { file(it).delete() }
    }

    /** Открыть файл во внешнем приложении (для форматов, которые нельзя показать внутри). */
    fun openExternally(attachment: Attachment) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file(attachment))
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(view, "Открыть с помощью")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    companion object {
        private val textExtensions = setOf("txt", "md", "csv", "json", "xml", "log", "html", "htm")

        fun detectKind(mime: String, ext: String): AttachmentKind = when {
            mime.startsWith("image/") -> AttachmentKind.IMAGE
            mime.startsWith("video/") -> AttachmentKind.VIDEO
            mime.startsWith("audio/") -> AttachmentKind.AUDIO
            mime == "application/pdf" || ext == "pdf" -> AttachmentKind.PDF
            ext == "docx" || mime == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                AttachmentKind.DOCX
            mime.startsWith("text/") || ext in textExtensions -> AttachmentKind.TEXT
            else -> AttachmentKind.OTHER
        }
    }
}
