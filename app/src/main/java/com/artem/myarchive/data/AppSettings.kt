package com.artem.myarchive.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppFont(val title: String) { DEFAULT("Обычный"), SERIF("С засечками"), MONO("Моноширинный"), CURSIVE("Рукописный") }

/** Настройки оформления. Хранятся в SharedPreferences; 0 в цвете означает «как в системе». */
class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)

    private var bgState by mutableIntStateOf(prefs.getInt(KEY_BG, 0))
    private var textState by mutableIntStateOf(prefs.getInt(KEY_TEXT, 0))
    private var fontState by mutableStateOf(
        runCatching { AppFont.valueOf(prefs.getString(KEY_FONT, null) ?: "") }.getOrDefault(AppFont.DEFAULT)
    )
    private var scaleState by mutableFloatStateOf(prefs.getFloat(KEY_SCALE, 1f))

    var backgroundColor: Int
        get() = bgState
        set(value) { bgState = value; prefs.edit().putInt(KEY_BG, value).apply() }

    var textColor: Int
        get() = textState
        set(value) { textState = value; prefs.edit().putInt(KEY_TEXT, value).apply() }

    var font: AppFont
        get() = fontState
        set(value) { fontState = value; prefs.edit().putString(KEY_FONT, value.name).apply() }

    var fontScale: Float
        get() = scaleState
        set(value) { scaleState = value; prefs.edit().putFloat(KEY_SCALE, value).apply() }

    fun reset() {
        backgroundColor = 0
        textColor = 0
        font = AppFont.DEFAULT
        fontScale = 1f
    }

    private companion object {
        const val KEY_BG = "background"
        const val KEY_TEXT = "text"
        const val KEY_FONT = "font"
        const val KEY_SCALE = "scale"
    }
}
