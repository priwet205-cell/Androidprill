package com.artem.myarchive.data

import androidx.room.withTransaction

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

    suspend fun deleteEntry(id: Long) {
        val entry = dao.getEntry(id) ?: return
        dao.deleteEntry(id)
        files.delete(entry.attachments)
    }
}
