package com.artem.myarchive.data

import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Repository(private val db: AppDatabase, val files: FileStorage) {

    private val dao = db.dao()

    fun sections(parentId: Long?) = dao.observeSections(parentId)
    fun section(id: Long) = dao.observeSection(id)
    fun entries(sectionId: Long) = dao.observeEntries(sectionId)
    fun entry(id: Long) = dao.observeEntry(id)

    suspend fun getEntry(id: Long) = dao.getEntry(id)
    suspend fun getAttachment(id: Long) = dao.getAttachment(id)

    suspend fun addSection(parentId: Long?, title: String) =
        dao.insertSection(Section(parentId = parentId, title = title.trim()))

    suspend fun renameSection(section: Section, title: String) =
        dao.updateSection(section.copy(title = title.trim()))

    /** Удаляет раздел со всеми подразделами, записями и файлами вложений. */
    suspend fun deleteSection(id: Long) {
        val ids = mutableListOf(id)
        var i = 0
        while (i < ids.size) {
            ids += dao.childSectionIds(ids[i])
            i++
        }
        val attachments = ids.chunked(500).flatMap { dao.attachmentsInSections(it) }
        dao.deleteSection(id) // записи и вложения удалятся каскадно
        files.delete(attachments)
    }

    /**
     * Сохраняет запись. [attachments] — итоговый список вложений в нужном порядке
     * (новые имеют id == 0), [removed] — вложения, которые пользователь убрал.
     */
    suspend fun saveEntry(
        entryId: Long?,
        sectionId: Long,
        title: String,
        description: String,
        attachments: List<Attachment>,
        removed: List<Attachment>,
    ): Long {
        val savedId = db.withTransaction {
            val id = if (entryId == null) {
                dao.insertEntry(Entry(sectionId = sectionId, title = title.trim(), description = description.trim()))
            } else {
                val old = dao.getEntry(entryId)!!.entry
                dao.updateEntry(
                    old.copy(title = title.trim(), description = description.trim(), updatedAt = System.currentTimeMillis())
                )
                entryId
            }
            val ordered = attachments.mapIndexed { index, a -> a.copy(entryId = id, position = index) }
            dao.insertAttachments(ordered.filter { it.id == 0L })
            dao.updateAttachments(ordered.filter { it.id != 0L })
            dao.deleteAttachments(removed.filter { it.id != 0L })
            id
        }
        files.delete(removed)
        return savedId
    }

    /** Удаляет несколько разделов и записей сразу. */
    suspend fun deleteAll(sectionIds: Collection<Long>, entryIds: Collection<Long>) {
        entryIds.forEach { deleteEntry(it) }
        sectionIds.forEach { deleteSection(it) }
    }

    /**
     * Упаковывает выбранные разделы (со всеми подразделами) и записи в один zip-архив.
     * Структура папок повторяет разделы, у каждой записи — своя папка с файлом «Описание.txt» и вложениями.
     */
    suspend fun exportZip(name: String, sectionIds: List<Long>, entryIds: List<Long>): File =
        withContext(Dispatchers.IO) {
            val dir = files.shareDir()
            dir.listFiles()?.forEach { it.deleteRecursively() } // старые архивы больше не нужны
            val out = File(dir, safeName(name).ifEmpty { "Архив" } + ".zip")
            ZipOutputStream(out.outputStream().buffered()).use { zip ->
                val used = HashSet<String>()
                sectionIds.forEach { id -> dao.getSection(id)?.let { addSection(zip, it, "", used) } }
                entryIds.forEach { id -> dao.getEntry(id)?.let { addEntry(zip, it, "", used) } }
            }
            out
        }

    private suspend fun addSection(zip: ZipOutputStream, section: Section, parent: String, used: MutableSet<String>) {
        val path = unique(parent + safeName(section.title).ifEmpty { "Раздел" }, used) + "/"
        zip.putNextEntry(ZipEntry(path))
        zip.closeEntry()
        dao.entriesIn(section.id).forEach { addEntry(zip, it, path, used) }
        dao.childSections(section.id).forEach { addSection(zip, it, path, used) }
    }

    private fun addEntry(zip: ZipOutputStream, item: EntryWithAttachments, parent: String, used: MutableSet<String>) {
        val path = unique(parent + safeName(item.entry.title).ifEmpty { "Запись" }, used) + "/"
        val text = buildString {
            appendLine(item.entry.title)
            if (item.entry.description.isNotBlank()) {
                appendLine()
                appendLine(item.entry.description)
            }
        }
        zip.putNextEntry(ZipEntry(unique(path + "Описание.txt", used)))
        zip.write(text.toByteArray())
        zip.closeEntry()
        item.attachments.sortedBy { it.position }.forEach { a ->
            val source = files.file(a)
            if (!source.exists()) return@forEach
            zip.putNextEntry(ZipEntry(unique(path + safeName(a.displayName).ifEmpty { a.fileName }, used)))
            source.inputStream().use { it.copyTo(zip) }
            zip.closeEntry()
        }
    }

    /** Если такое имя в архиве уже есть, добавляет « (2)», « (3)»… перед расширением. */
    private fun unique(path: String, used: MutableSet<String>): String {
        if (used.add(path)) return path
        val slash = path.lastIndexOf('/')
        val dot = path.lastIndexOf('.').takeIf { it > slash + 1 } ?: path.length
        var n = 2
        while (true) {
            val candidate = path.substring(0, dot) + " ($n)" + path.substring(dot)
            if (used.add(candidate)) return candidate
            n++
        }
    }

    private fun safeName(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_").trim().trimEnd('.').take(100)

    suspend fun deleteEntry(id: Long) {
        val entry = dao.getEntry(id) ?: return
        dao.deleteEntry(id)
        files.delete(entry.attachments)
    }
}
