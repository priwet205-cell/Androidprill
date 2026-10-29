package com.artem.myarchive.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.artem.myarchive.data.AppFont
import com.artem.myarchive.settings

private val BackgroundColors = listOf(
    0xFFFFFFFF, 0xFFF5F5F5, 0xFFFFF8E7, 0xFFF1F5EC, 0xFFE9F2FB, 0xFFFDEFF2,
    0xFFE0E0E0, 0xFF37474F, 0xFF263238, 0xFF1E2A1E, 0xFF121212, 0xFF000000,
)

private val TextColors = listOf(
    0xFF000000, 0xFF1B1B1B, 0xFF424242, 0xFF0D47A1, 0xFF1B5E20, 0xFF4E342E,
    0xFFB71C1C, 0xFFFFFFFF, 0xFFE0E0E0, 0xFFFFF59D, 0xFFA5D6A7, 0xFF90CAF9,
)

private val Scales = listOf(0.9f to "Мелкий", 1f to "Обычный", 1.15f to "Крупный", 1.3f to "Очень крупный")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val settings = LocalContext.current.settings

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Оформление") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = { TextButton(onClick = { settings.reset() }) { Text("Сбросить") } },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Пример того, как будет выглядеть список.
            Card(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Folder, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                    Column(Modifier.padding(start = 16.dp)) {
                        Text("Пример раздела", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            "2 подраздела · 5 записей",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            SectionTitle("Цвет фона")
            Swatches(BackgroundColors, settings.backgroundColor) { settings.backgroundColor = it }

            SectionTitle("Цвет шрифта")
            Swatches(TextColors, settings.textColor) { settings.textColor = it }

            SectionTitle("Шрифт")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppFont.entries.forEach { font ->
                    FilterChip(
                        selected = settings.font == font,
                        onClick = { settings.font = font },
                        label = { Text(font.title, fontFamily = font.family()) },
                    )
                }
            }

            SectionTitle("Размер шрифта")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Scales.forEach { (scale, title) ->
                    FilterChip(
                        selected = settings.fontScale == scale,
                        onClick = { settings.fontScale = scale },
                        label = { Text(title) },
                    )
                }
            }
        }
    }
}

private fun AppFont.family(): FontFamily = when (this) {
    AppFont.DEFAULT -> FontFamily.Default
    AppFont.SERIF -> FontFamily.Serif
    AppFont.MONO -> FontFamily.Monospace
    AppFont.CURSIVE -> FontFamily.Cursive
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
}

/** Ряд цветных кружков; первый — «Авто» (как в системе / подбирается под фон). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(colors: List<Long>, selected: Int, onSelect: (Int) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Swatch(color = null, selected = selected == 0) { onSelect(0) }
        colors.forEach { value ->
            val color = Color(value)
            Swatch(color = color, selected = selected == color.toArgb()) { onSelect(color.toArgb()) }
        }
    }
}

@Composable
private fun Swatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    val outline = MaterialTheme.colorScheme.outline
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(color ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(
                BorderStroke(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else outline),
                CircleShape,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val iconTint = if (color == null) MaterialTheme.colorScheme.onSurfaceVariant
        else if (color.luminance() < 0.5f) Color.White else Color.Black
        when {
            selected -> Icon(Icons.Default.Check, contentDescription = "Выбрано", tint = iconTint)
            color == null -> Icon(Icons.Default.Autorenew, contentDescription = "Авто", tint = iconTint)
        }
    }
}
