package com.knowapp.android

import android.os.Bundle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.knowapp.android.data.ThemeMode
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.knowapp.android.ui.navigation.KnowNavGraph
import com.knowapp.android.ui.theme.KnowTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as KnowApplication).container

        setContent {
            val mode by container.themeStore.mode.collectAsState()
            val dark = when (mode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }
            KnowTheme(darkTheme = dark) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KnowNavGraph(container = container)
                }
            }
        }
    }
}
