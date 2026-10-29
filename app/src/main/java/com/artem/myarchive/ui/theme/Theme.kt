package com.artem.myarchive.ui.theme

import android.os.Build
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import com.artem.myarchive.data.AppFont
import com.artem.myarchive.settings

private val LightColors = lightColorScheme(
    primary = Color(0xFF3F51B5),
    secondary = Color(0xFF5C6BC0),
    tertiary = Color(0xFF00897B),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB8C3FF),
    secondary = Color(0xFFC5CAE9),
    tertiary = Color(0xFF80CBC4),
)

@Composable
fun MyArchiveTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val settings = context.settings
    val systemDark = isSystemInDarkTheme()

    val background = settings.backgroundColor.takeIf { it != 0 }?.let { Color(it) }
    val dark = background?.let { it.luminance() < 0.4f } ?: systemDark
    val base = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    // Цвет шрифта: выбранный пользователем, иначе контрастный к выбранному фону.
    val text = settings.textColor.takeIf { it != 0 }?.let { Color(it) }
        ?: background?.let { if (dark) Color(0xFFF2F2F2) else Color(0xFF1B1B1B) }

    var colors = base
    if (background != null) {
        val tint = text ?: base.onSurface
        colors = colors.copy(
            background = background,
            surface = background,
            surfaceBright = background,
            surfaceContainerLowest = background,
            surfaceContainerLow = lerp(background, tint, 0.03f),
            surfaceContainer = lerp(background, tint, 0.05f),
            surfaceContainerHigh = lerp(background, tint, 0.08f),
            surfaceContainerHighest = lerp(background, tint, 0.11f),
            surfaceVariant = lerp(background, tint, 0.11f),
            surfaceDim = lerp(background, tint, 0.06f),
        )
    }
    if (text != null) {
        colors = colors.copy(
            onBackground = text,
            onSurface = text,
            onSurfaceVariant = text.copy(alpha = 0.75f),
        )
    }

    // Значки строки состояния — светлые на тёмном фоне и тёмные на светлом.
    val activity = context as? ComponentActivity
    SideEffect {
        val transparent = Color.Transparent.toArgb()
        activity?.enableEdgeToEdge(
            statusBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
            navigationBarStyle = if (dark) SystemBarStyle.dark(transparent) else SystemBarStyle.light(transparent, transparent),
        )
    }

    MaterialTheme(
        colorScheme = colors,
        typography = typography(settings.font, settings.fontScale),
        content = content,
    )
}

private fun typography(font: AppFont, scale: Float): Typography {
    val family = when (font) {
        AppFont.DEFAULT -> FontFamily.Default
        AppFont.SERIF -> FontFamily.Serif
        AppFont.MONO -> FontFamily.Monospace
        AppFont.CURSIVE -> FontFamily.Cursive
    }
    fun TextStyle.adjust() = copy(fontFamily = family, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    val t = Typography()
    return Typography(
        displayLarge = t.displayLarge.adjust(),
        displayMedium = t.displayMedium.adjust(),
        displaySmall = t.displaySmall.adjust(),
        headlineLarge = t.headlineLarge.adjust(),
        headlineMedium = t.headlineMedium.adjust(),
        headlineSmall = t.headlineSmall.adjust(),
        titleLarge = t.titleLarge.adjust(),
        titleMedium = t.titleMedium.adjust(),
        titleSmall = t.titleSmall.adjust(),
        bodyLarge = t.bodyLarge.adjust(),
        bodyMedium = t.bodyMedium.adjust(),
        bodySmall = t.bodySmall.adjust(),
        labelLarge = t.labelLarge.adjust(),
        labelMedium = t.labelMedium.adjust(),
        labelSmall = t.labelSmall.adjust(),
    )
}
