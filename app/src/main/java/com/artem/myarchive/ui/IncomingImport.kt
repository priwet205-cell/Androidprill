package com.artem.myarchive.ui

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.artem.myarchive.repository

/** zip, открытый из мессенджера («Открыть с помощью»): спрашиваем, в какой раздел его добавить. */
@Composable
fun IncomingImport(uri: Uri, onDone: () -> Unit) {
    val context = LocalContext.current
    val name = remember(uri) { context.repository.files.displayName(uri) ?: "архив" }
    val importAction = rememberImportAction()
    var started by remember(uri) { mutableStateOf(false) }

    if (!started) {
        MoveDialog(
            movingSections = emptySet(),
            hasEntries = false,
            startId = null,
            allowStart = true,
            title = "Куда добавить «$name»?",
            confirmText = "Добавить сюда",
            onDismiss = onDone,
            onMove = { target ->
                started = true
                importAction.import(uri, target, onDone)
            },
        )
    }
    ImportProgress(importAction)
}
