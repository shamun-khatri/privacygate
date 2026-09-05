package com.privacygate.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = Color(0xFFD7FC70), background = Color(0xFF101211),
                surface = Color(0xFF1C201C), onPrimary = Color(0xFF17200B),
            )) {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.safeDrawingPadding().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("PRIVACYGATE", style = MaterialTheme.typography.headlineLarge)
                        Text("Share what they need. Nothing more.")
                        Card {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("System proof", style = MaterialTheme.typography.titleLarge)
                                Text("Protection is not connected yet.")
                                Text("This build establishes the Android app. WhatsApp protection will be enabled after device calibration.")
                            }
                        }
                    }
                }
            }
        }
    }
}
