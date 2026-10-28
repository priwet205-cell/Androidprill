package com.artem.myarchive.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters

class Converters {
    @TypeConverter
    fun kindToString(kind: AttachmentKind): String = kind.name

    @TypeConverter
    fun stringToKind(value: String): AttachmentKind =
        runCatching { AttachmentKind.valueOf(value) }.getOrDefault(AttachmentKind.OTHER)
}

@Database(
    entities = [Section::class, Entry::class, Attachment::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dao(): ArchiveDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "archive.db").build()
    }
}
