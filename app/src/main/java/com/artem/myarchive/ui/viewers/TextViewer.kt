package com.artem.myarchive.ui.viewers

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction

private const val MAX_BYTES = 2 * 1024 * 1024

@Composable
fun TextViewer(file: File, modifier: Modifier) {
    val text by produceState<String?>(null, file) {
        value = withContext(Dispatchers.IO) { readText(file) }
    }
    val content = text ?: return Loading(modifier)
    SelectionContainer(modifier.verticalScroll(rememberScrollState())) {
        Text(content, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
    }
}

/** Читает текст в UTF-8, а если файл не в UTF-8 — в Windows-1251 (частый случай для русских файлов). */
private fun readText(file: File): String {
    val bytes = file.inputStream().use { input ->
        val buf = ByteArray(minOf(file.length(), MAX_BYTES.toLong()).toInt())
        var read = 0
        while (read < buf.size) {
            val n = input.read(buf, read, buf.size - read)
            if (n < 0) break
            read += n
        }
        buf.copyOf(read)
    }
    // Если файл обрезан, последний символ UTF-8 мог разрезаться — отбрасываем до 3 байт.
    val truncated = file.length() > MAX_BYTES
    val decoded = (0..(if (truncated) 3 else 0)).firstNotNullOfOrNull { cut ->
        decodeUtf8Strict(bytes, bytes.size - cut)
    } ?: String(bytes, Charset.forName("windows-1251"))
    return if (truncated) "$decoded\n\n… (показаны первые 2 МБ файла)" else decoded
}

private fun decodeUtf8Strict(bytes: ByteArray, length: Int): String? = try {
    Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(bytes, 0, length.coerceAtLeast(0)))
        .toString()
} catch (e: CharacterCodingException) {
    null
}
