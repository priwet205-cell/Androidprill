package com.artem.myarchive

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.artem.myarchive.ui.AppNavigation
import com.artem.myarchive.ui.theme.MyArchiveTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyArchiveTheme {
                AppNavigation()
            }
        }
    }
}
