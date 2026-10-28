package com.artem.myarchive.ui.viewers

import android.util.Xml
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.artem.myarchive.data.Attachment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile

/** Блоки документа Word, которые умеем показывать. */
private sealed interface DocBlock {
    /** level: 0 — обычный абзац, 1..6 — заголовок, -1 — заголовок документа (Title). */
    data class Paragraph(val text: AnnotatedString, val level: Int, val listItem: Boolean) : DocBlock
    data class TableRow(val cells: List<AnnotatedString>) : DocBlock
    class Image(val bytes: ByteArray) : DocBlock
}

/**
 * Простой встроенный просмотрщик .docx: текст с жирным/курсивом/подчёркиванием,
 * заголовки, списки, таблицы и картинки. Сложная вёрстка Word упрощается.
 */
@Composable
fun DocxViewer(file: File, attachment: Attachment, modifier: Modifier) {
    val result by produceState<Result<List<DocBlock>>?>(null, file) {
        value = withContext(Dispatchers.IO) { runCatching { parseDocx(file) } }
    }
    val blocks = when (val r = result) {
        null -> return Loading(modifier)
        else -> r.getOrNull()
            ?: return UnsupportedFile(attachment, modifier, reason = "Не удалось прочитать документ.")
    }

    SelectionContainer(modifier) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(blocks) { block ->
                when (block) {
                    is DocBlock.Paragraph -> {
                        val style = when (block.level) {
                            -1 -> MaterialTheme.typography.headlineMedium
                            1 -> MaterialTheme.typography.headlineSmall
                            2 -> MaterialTheme.typography.titleLarge
                            3 -> MaterialTheme.typography.titleMedium
                            in 4..9 -> MaterialTheme.typography.titleSmall
                            else -> MaterialTheme.typography.bodyLarge
                        }
                        val text = if (block.listItem) AnnotatedString("•  ") + block.text else block.text
                        Text(
                            text,
                            style = style,
                            modifier = if (block.listItem) Modifier.padding(start = 12.dp) else Modifier,
                        )
                    }
                    is DocBlock.TableRow -> {
                        val border = MaterialTheme.colorScheme.outlineVariant
                        Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
                            block.cells.forEach { cell ->
                                Text(
                                    cell,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .border(0.5.dp, border)
                                        .padding(6.dp),
                                )
                            }
                        }
                    }
                    is DocBlock.Image -> AsyncImage(
                        model = block.bytes,
                        contentDescription = null,
                        contentScale = ContentScale.FillWidth,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Разбор .docx (это zip-архив с XML внутри)
// ---------------------------------------------------------------------------

private fun parseDocx(file: File): List<DocBlock> = ZipFile(file).use { zip ->
    fun open(name: String): InputStream? = zip.getEntry(name)?.let { zip.getInputStream(it) }

    val styleNames = open("word/styles.xml")?.use(::parseStyles).orEmpty()
    val relations = open("word/_rels/document.xml.rels")?.use(::parseRelations).orEmpty()
    val document = open("word/document.xml") ?: error("word/document.xml не найден")

    document.use { input ->
        val loadImage: (String) -> ByteArray? = { relId ->
            relations[relId]?.let { target ->
                val path = if (target.startsWith("/")) target.removePrefix("/") else "word/$target"
                open(path)?.use { it.readBytes() }
            }
        }
        DocumentParser(styleNames, loadImage).parse(input)
    }
}

private fun newParser(input: InputStream): XmlPullParser = Xml.newPullParser().apply {
    setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
    setInput(input, null)
}

/** styleId -> имя стиля в нижнем регистре ("heading 1", "title", "list paragraph"…). */
private fun parseStyles(input: InputStream): Map<String, String> {
    val parser = newParser(input)
    val result = HashMap<String, String>()
    var currentId: String? = null
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType != XmlPullParser.START_TAG) continue
        when (parser.name) {
            "w:style" -> currentId = parser.getAttributeValue(null, "w:styleId")
            "w:name" -> currentId?.let { id ->
                parser.getAttributeValue(null, "w:val")?.let { result[id] = it.lowercase() }
            }
        }
    }
    return result
}

private fun parseRelations(input: InputStream): Map<String, String> {
    val parser = newParser(input)
    val result = HashMap<String, String>()
    while (parser.next() != XmlPullParser.END_DOCUMENT) {
        if (parser.eventType == XmlPullParser.START_TAG && parser.name == "Relationship") {
            val id = parser.getAttributeValue(null, "Id")
            val target = parser.getAttributeValue(null, "Target")
            if (id != null && target != null) result[id] = target
        }
    }
    return result
}

private class DocumentParser(
    private val styleNames: Map<String, String>,
    private val loadImage: (relId: String) -> ByteArray?,
) {
    private val blocks = mutableListOf<DocBlock>()

    private class Para {
        val text = AnnotatedString.Builder()
        var styleId: String? = null
        var isList = false
    }

    private val paragraphs = ArrayDeque<Para>()
    private var tableDepth = 0
    private var row: MutableList<AnnotatedString>? = null
    private var cell: AnnotatedString.Builder? = null

    private var bold = false
    private var italic = false
    private var underline = false
    private var inRunProps = false

    fun parse(input: InputStream): List<DocBlock> {
        val parser = newParser(input)
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            when (parser.eventType) {
                XmlPullParser.START_TAG -> start(parser)
                XmlPullParser.END_TAG -> end(parser.name)
            }
        }
        return blocks
    }

    private fun flag(parser: XmlPullParser): Boolean =
        parser.getAttributeValue(null, "w:val") !in setOf("0", "false", "none")

    private fun start(p: XmlPullParser) {
        when (p.name) {
            "w:p" -> paragraphs.addLast(Para())
            "w:pStyle" -> paragraphs.lastOrNull()?.styleId = p.getAttributeValue(null, "w:val")
            "w:numPr" -> paragraphs.lastOrNull()?.isList = true
            "w:r" -> { bold = false; italic = false; underline = false }
            "w:rPr" -> inRunProps = true
            "w:b" -> if (inRunProps) bold = flag(p)
            "w:i" -> if (inRunProps) italic = flag(p)
            "w:u" -> if (inRunProps) underline = flag(p)
            "w:t" -> append(p.nextText())
            "w:tab" -> append("\t")
            "w:br", "w:cr" -> append("\n")
            "w:tbl" -> tableDepth++
            "w:tr" -> if (tableDepth == 1) row = mutableListOf()
            "w:tc" -> if (tableDepth == 1) cell = AnnotatedString.Builder()
            "a:blip" -> {
                val relId = p.getAttributeValue(null, "r:embed") ?: return
                val bytes = runCatching { loadImage(relId) }.getOrNull() ?: return
                blocks += DocBlock.Image(bytes)
            }
        }
    }

    private fun end(name: String) {
        when (name) {
            "w:rPr" -> inRunProps = false
            "w:p" -> {
                val para = paragraphs.removeLastOrNull() ?: return
                val text = para.text.toAnnotatedString()
                val parent = paragraphs.lastOrNull()
                val currentCell = cell
                when {
                    // Абзац внутри надписи/сноски — дописываем в внешний абзац.
                    parent != null -> parent.text.append(text)
                    tableDepth > 0 && currentCell != null -> {
                        if (currentCell.length > 0) currentCell.append("\n")
                        currentCell.append(text)
                    }
                    tableDepth > 0 -> Unit
                    else -> blocks += DocBlock.Paragraph(text, headingLevel(para.styleId), para.isList)
                }
            }
            "w:tc" -> if (tableDepth == 1) {
                cell?.let { row?.add(it.toAnnotatedString()) }
                cell = null
            }
            "w:tr" -> if (tableDepth == 1) {
                row?.takeIf { it.isNotEmpty() }?.let { blocks += DocBlock.TableRow(it) }
                row = null
            }
            "w:tbl" -> tableDepth--
        }
    }

    private fun append(s: String) {
        val para = paragraphs.lastOrNull() ?: return
        val style = SpanStyle(
            fontWeight = if (bold) FontWeight.Bold else null,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) TextDecoration.Underline else null,
        )
        if (bold || italic || underline) {
            para.text.pushStyle(style)
            para.text.append(s)
            para.text.pop()
        } else {
            para.text.append(s)
        }
    }

    private fun headingLevel(styleId: String?): Int {
        if (styleId == null) return 0
        val name = styleNames[styleId] ?: styleId.lowercase()
        return when {
            name == "title" -> -1
            name.startsWith("heading") -> name.filter { it.isDigit() }.toIntOrNull() ?: 1
            else -> 0
        }
    }
}
