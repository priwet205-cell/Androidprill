package com.artem.myarchive.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

data class ImportResult(val sections: Int, val entries: Int, val merged: Int = 0)

/**
 * Импорт zip-архива. Понимает архивы, сделанные кнопкой «Отправить» (папка записи содержит «Описание.txt»),
 * и обычные zip: папки становятся подразделами, а каждый отдельный файл — записью с этим файлом.
 * Если раздел с таким названием уже есть, новые записи добавляются в него, а не в новую копию раздела.
 */
class ZipImporter(private val repo: Repository, private val files: FileStorage) {

    private class Node(val name: String) {
        val dirs = LinkedHashMap<String, Node>()
        val files = mutableListOf<Pair<String, ZipEntry>>()
        val isEntryFolder: Boolean get() = files.any { it.first.equals(DESCRIPTION, ignoreCase = true) }
    }

    private var sections = 0
    private var entries = 0
    private var merged = 0

    suspend fun import(uri: Uri, targetId: Long?): ImportResult = withContext(Dispatchers.IO) {
        val archiveName = files.displayName(uri)?.substringBeforeLast('.')?.ifBlank { null } ?: "Импорт"
        val tmp = files.copyToTemp(uri)
        try {
            openZip(tmp).use { zip ->
                val root = buildTree(zip)
                var holder: Long? = targetId
                // Записи нельзя класть на главный экран — для них создаётся раздел с именем архива.
                val holderProvider: suspend () -> Long = {
                    holder ?: sectionFor(null, archiveName).also { holder = it }
                }
                importInto(zip, root, targetId, holderProvider)
            }
        } finally {
            tmp.delete()
        }
        ImportResult(sections, entries, merged)
    }

    /** Имена файлов в zip из Windows бывают в кодировке CP866 — пробуем её, если UTF-8 не подошла. */
    private fun openZip(file: File): ZipFile {
        val utf8 = ZipFile(file, Charsets.UTF_8)
        return try {
            utf8.entries().toList() // имена декодируются здесь — проверяем, что UTF-8 подходит
            utf8
        } catch (e: Exception) {
            utf8.close()
            ZipFile(file, Charset.forName("CP866"))
        }
    }

    private fun buildTree(zip: ZipFile): Node {
        val root = Node("")
        for (entry in zip.entries()) {
            val parts = entry.name.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." && it != ".." }
            if (parts.isEmpty() || parts.first() == "__MACOSX") continue
            var node = root
            val dirParts = if (entry.isDirectory) parts else parts.dropLast(1)
            dirParts.forEach { part -> node = node.dirs.getOrPut(part) { Node(part) } }
            if (!entry.isDirectory) {
                val name = parts.last()
                if (name == ".DS_Store" || name.equals("Thumbs.db", ignoreCase = true)) continue
                node.files += name to entry
            }
        }
        return root
    }

    private suspend fun importInto(zip: ZipFile, node: Node, sectionId: Long?, holder: suspend () -> Long) {
        for (dir in node.dirs.values) {
            if (dir.isEntryFolder && dir.dirs.isEmpty()) {
                importEntryFolder(zip, dir, sectionId ?: holder())
            } else {
                val id = sectionFor(sectionId, dir.name)
                importInto(zip, dir, id) { id }
            }
        }
        // Отдельные файлы (не в папке записи) — каждый становится записью.
        for ((name, entry) in node.files) {
            if (name.equals(DESCRIPTION, ignoreCase = true)) continue
            val attachment = zip.getInputStream(entry).use { files.importStream(name, it) }
            repo.saveEntry(null, sectionId ?: holder(), name.substringBeforeLast('.').ifBlank { name }, "", listOf(attachment), emptyList())
            entries++
        }
    }

    /**
     * Папка из архива с тем же названием, что и существующий раздел на этом уровне,
     * не создаёт дубликат: её содержимое добавляется в уже существующий раздел.
     */
    private suspend fun sectionFor(parentId: Long?, title: String): Long {
        repo.findSection(parentId, title)?.let { merged++; return it.id }
        sections++
        return repo.addSection(parentId, title)
    }

    private suspend fun importEntryFolder(zip: ZipFile, dir: Node, sectionId: Long) {
        val descEntry = dir.files.first { it.first.equals(DESCRIPTION, ignoreCase = true) }.second
        val text = zip.getInputStream(descEntry).use { it.readBytes().toString(Charsets.UTF_8) }.removePrefix("﻿")
        val lines = text.lines()
        val title = lines.firstOrNull()?.trim()?.ifBlank { null } ?: dir.name
        val description = lines.drop(1).joinToString("\n").trim()
        val attachments = dir.files
            .filter { !it.first.equals(DESCRIPTION, ignoreCase = true) }
            .map { (name, entry) -> zip.getInputStream(entry).use { files.importStream(name, it) } }
        repo.saveEntry(null, sectionId, title, description, attachments, emptyList())
        entries++
    }

    companion object {
        const val DESCRIPTION = "Описание.txt"
    }
}
