package com.artem.myarchive

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import com.artem.myarchive.ui.AppNavigation
import com.artem.myarchive.ui.IncomingImport
import com.artem.myarchive.ui.theme.MyArchiveTheme

class MainActivity : ComponentActivity() {

    /** zip-архив, который открыли в приложении из мессенджера или файлового менеджера. */
    private var incomingZip by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) incomingZip = zipFrom(intent)
        setContent {
            MyArchiveTheme {
                AppNavigation()
                incomingZip?.let { uri -> IncomingImport(uri, onDone = { incomingZip = null }) }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        zipFrom(intent)?.let { incomingZip = it }
    }

    private fun zipFrom(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_VIEW -> intent.data
        Intent.ACTION_SEND -> IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
        else -> null
    }
}
