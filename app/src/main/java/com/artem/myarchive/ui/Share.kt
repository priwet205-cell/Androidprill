package com.artem.myarchive.ui

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.artem.myarchive.repository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Упаковывает выбранные разделы и записи в один zip и открывает меню «Отправить». */
class ShareAction(private val context: Context, private val scope: CoroutineScope) {
    var busy by mutableStateOf(false)
        private set

    fun share(name: String, sectionIds: List<Long>, entryIds: List<Long>, onDone: () -> Unit = {}) {
        if (busy || (sectionIds.isEmpty() && entryIds.isEmpty())) return
        busy = true
        scope.launch {
            try {
                val repo = context.repository
                val zip = repo.exportZip(name, sectionIds, entryIds)
                repo.files.shareFile(zip)
                onDone()
            } catch (e: Exception) {
                Toast.makeText(context, "Не удалось подготовить архив: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }
}

@Composable
fun rememberShareAction(): ShareAction {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember { ShareAction(context.applicationContext, scope) }
}

/** Окно ожидания, пока собирается архив (большие видео копируются не мгновенно). */
@Composable
fun ShareProgress(action: ShareAction) {
    if (!action.busy) return
    Dialog(
        onDismissRequest = {},
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Card {
            Row(
                Modifier.padding(24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator(Modifier.size(28.dp))
                Text("Подготовка архива…")
            }
        }
    }
}
