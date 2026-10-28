package com.artem.myarchive.ui.viewers

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import com.artem.myarchive.data.Attachment
import com.artem.myarchive.data.images
import com.artem.myarchive.repository

/** Полноэкранный просмотр фотографий записи: листание и масштабирование (двойной тап / щипок). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageViewerScreen(entryId: Long, startIndex: Int, onBack: () -> Unit) {
    val repo = LocalContext.current.repository
    val entry by remember(entryId) { repo.entry(entryId) }.collectAsState(initial = null)
    val images = entry?.images ?: return

    if (images.isEmpty()) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, images.lastIndex)) { images.size }

    ViewerScaffold(
        title = "${pager.currentPage + 1} / ${images.size}",
        attachment = images.getOrNull(pager.currentPage),
        onBack = onBack,
        dark = true,
    ) { modifier ->
        HorizontalPager(state = pager, modifier = modifier.background(Color.Black)) { page ->
            ZoomableImage(images[page])
        }
    }
}

@Composable
private fun ZoomableImage(attachment: Attachment) {
    val repo = LocalContext.current.repository
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = {
                    if (scale > 1f) { scale = 1f; offset = Offset.Zero } else scale = 2.5f
                })
            }
            .pointerInput(Unit) {
                // Щипок масштабирует; сдвиг перехватываем только при увеличении,
                // иначе свайп достаётся пейджеру для перелистывания фото.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + event.calculatePan()
                            event.changes.forEach { it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        AsyncImage(
            model = repo.files.file(attachment),
            contentDescription = attachment.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
    }
}
