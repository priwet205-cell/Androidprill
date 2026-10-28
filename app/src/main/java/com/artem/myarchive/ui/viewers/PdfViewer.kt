package com.artem.myarchive.ui.viewers

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.artem.myarchive.data.Attachment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** PdfRenderer не потокобезопасен, поэтому все обращения идут под одним мьютексом. */
private class PdfDocument(file: File) {
    private val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    private val renderer = PdfRenderer(fd)
    private val mutex = Mutex()
    private var closed = false
    val pageCount = renderer.pageCount

    suspend fun render(index: Int, width: Int): Bitmap? = withContext(Dispatchers.IO) {
        mutex.withLock {
            if (closed) return@withLock null
            renderer.openPage(index).use { page ->
                val height = (width.toLong() * page.height / page.width).toInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bmp ->
                    bmp.eraseColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }

    fun close() {
        CoroutineScope(Dispatchers.IO).launch {
            mutex.withLock {
                closed = true
                renderer.close()
                fd.close()
            }
        }
    }
}

@Composable
fun PdfViewer(file: File, attachment: Attachment, modifier: Modifier) {
    val document = remember(file) { runCatching { PdfDocument(file) } }
    val doc = document.getOrNull()
    if (doc == null) {
        UnsupportedFile(attachment, modifier, reason = "Не удалось открыть PDF: файл повреждён или защищён паролем.")
        return
    }
    DisposableEffect(doc) { onDispose { doc.close() } }

    BoxWithConstraints(modifier.background(Color(0xFFE0E0E0))) {
        val widthPx = constraints.maxWidth
        LazyColumn(
            contentPadding = PaddingValues(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(doc.pageCount) { index -> PdfPage(doc, index, widthPx) }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDocument, index: Int, widthPx: Int) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) { bitmap = doc.render(index, widthPx) }
    val bmp = bitmap
    if (bmp == null) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f / 1.414f).background(Color.White))
    } else {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Страница ${index + 1}",
            contentScale = ContentScale.FillWidth,
            modifier = Modifier.fillMaxWidth().aspectRatio(bmp.width.toFloat() / bmp.height),
        )
    }
}
