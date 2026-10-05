package com.lionel.hub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

/** Lionel Hub: command bar, smart suggestions, auto-organized apps, focus mode and app lock. */
class LionelActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            LionelTheme {
                LionelHub()
            }
        }
    }
}
