package com.artem.myarchive.ui.viewers

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.artem.myarchive.data.Attachment
import com.artem.myarchive.repository

/** Общая обёртка экранов просмотра: заголовок, «назад» и «открыть в другом приложении». */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ViewerScaffold(
    title: String,
    attachment: Attachment?,
    onBack: () -> Unit,
    dark: Boolean = false,
    content: @Composable (Modifier) -> Unit,
) {
    val context = LocalContext.current
    Scaffold(
        containerColor = if (dark) Color.Black else MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (attachment != null) {
                        IconButton(onClick = { context.repository.files.openExternally(attachment) }) {
                            Icon(Icons.Default.OpenInNew, contentDescription = "Открыть в другом приложении")
                        }
                    }
                },
                colors = if (dark) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Black,
                        titleContentColor = Color.White,
                        navigationIconContentColor = Color.White,
                        actionIconContentColor = Color.White,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors()
                },
            )
        },
    ) { padding ->
        content(Modifier.fillMaxSize().padding(padding))
    }
}
