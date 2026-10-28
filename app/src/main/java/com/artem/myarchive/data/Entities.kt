package com.artem.myarchive.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/** Раздел. parentId == null — раздел верхнего уровня. Вложенность не ограничена. */
@Entity(
    tableName = "sections",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("parentId")],
)
data class Section(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long?,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** Запись внутри раздела: название, описание и вложения. */
@Entity(
    tableName = "entries",
    foreignKeys = [
        ForeignKey(
            entity = Section::class,
            parentColumns = ["id"],
            childColumns = ["sectionId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("sectionId")],
)
data class Entry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sectionId: Long,
    val title: String,
    val description: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
)

enum class AttachmentKind { IMAGE, VIDEO, AUDIO, PDF, DOCX, TEXT, OTHER }

/** Файл, скопированный во внутреннее хранилище приложения (filesDir/attachments/[fileName]). */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Entry::class,
            parentColumns = ["id"],
            childColumns = ["entryId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("entryId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entryId: Long,
    val displayName: String,
    val mimeType: String,
    val fileName: String,
    val kind: AttachmentKind,
    val size: Long,
    val position: Int = 0,
)

data class SectionItem(
    @Embedded val section: Section,
    val subCount: Int,
    val entryCount: Int,
)

data class EntryWithAttachments(
    @Embedded val entry: Entry,
    @Relation(parentColumn = "id", entityColumn = "entryId")
    val attachments: List<Attachment>,
)

val EntryWithAttachments.images: List<Attachment>
    get() = attachments.filter { it.kind == AttachmentKind.IMAGE }.sortedBy { it.position }

val EntryWithAttachments.files: List<Attachment>
    get() = attachments.filter { it.kind != AttachmentKind.IMAGE }.sortedBy { it.position }
