package com.artem.myarchive.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ArchiveDao {

    // ---- Разделы ----

    @Query(
        """
        SELECT s.*,
            (SELECT COUNT(*) FROM sections c WHERE c.parentId = s.id) AS subCount,
            (SELECT COUNT(*) FROM entries e WHERE e.sectionId = s.id) AS entryCount
        FROM sections s
        WHERE s.parentId IS :parentId
        ORDER BY s.createdAt
        """
    )
    fun observeSections(parentId: Long?): Flow<List<SectionItem>>

    @Query("SELECT * FROM sections WHERE id = :id")
    fun observeSection(id: Long): Flow<Section?>

    @Query("SELECT * FROM sections WHERE id = :id")
    suspend fun getSection(id: Long): Section?

    @Query("SELECT * FROM sections WHERE parentId = :parentId ORDER BY createdAt")
    suspend fun childSections(parentId: Long): List<Section>

    @Query("SELECT id FROM sections WHERE parentId = :parentId")
    suspend fun childSectionIds(parentId: Long): List<Long>

    @Query("UPDATE sections SET parentId = :parentId WHERE id IN (:ids)")
    suspend fun moveSections(ids: List<Long>, parentId: Long?)

    @Query("UPDATE entries SET sectionId = :sectionId, updatedAt = :now WHERE id IN (:ids)")
    suspend fun moveEntries(ids: List<Long>, sectionId: Long, now: Long)

    @Insert
    suspend fun insertSection(section: Section): Long

    @Update
    suspend fun updateSection(section: Section)

    @Query("DELETE FROM sections WHERE id = :id")
    suspend fun deleteSection(id: Long)

    // ---- Записи ----

    @Transaction
    @Query("SELECT * FROM entries WHERE sectionId = :sectionId ORDER BY createdAt DESC")
    fun observeEntries(sectionId: Long): Flow<List<EntryWithAttachments>>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    fun observeEntry(id: Long): Flow<EntryWithAttachments?>

    @Transaction
    @Query("SELECT * FROM entries WHERE id = :id")
    suspend fun getEntry(id: Long): EntryWithAttachments?

    @Transaction
    @Query("SELECT * FROM entries WHERE sectionId = :sectionId ORDER BY createdAt")
    suspend fun entriesIn(sectionId: Long): List<EntryWithAttachments>

    @Insert
    suspend fun insertEntry(entry: Entry): Long

    @Update
    suspend fun updateEntry(entry: Entry)

    @Query("DELETE FROM entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    // ---- Вложения ----

    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getAttachment(id: Long): Attachment?

    @Query(
        """
        SELECT a.* FROM attachments a
        INNER JOIN entries e ON a.entryId = e.id
        WHERE e.sectionId IN (:sectionIds)
        """
    )
    suspend fun attachmentsInSections(sectionIds: List<Long>): List<Attachment>

    @Insert
    suspend fun insertAttachments(attachments: List<Attachment>)

    @Update
    suspend fun updateAttachments(attachments: List<Attachment>)

    @Delete
    suspend fun deleteAttachments(attachments: List<Attachment>)
}
