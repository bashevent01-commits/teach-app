package com.knowapp.android

import android.os.Bundle
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
            KnowTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    KnowNavGraph(container = container)
                }
            }
        }
    }
}
