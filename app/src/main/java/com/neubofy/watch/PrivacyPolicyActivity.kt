package com.neubofy.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.neubofy.watch.ui.theme.NFWatchTheme

/**
 * Required by Health Connect — shows privacy policy when user checks
 * permission rationale in Health Connect settings.
 */
class PrivacyPolicyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            NFWatchTheme {
                PrivacyPolicyScreen()
            }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Text(
                "NF Watch Privacy Policy",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                """NF Watch is designed with privacy as its core principle.

• ALL data stays on your device. There is no cloud sync, no server, and no internet connection.

• NF Watch does NOT have internet permission. It cannot send any data anywhere.

• NF Watch does NOT access your microphone. The microphone permission is explicitly blocked.

• Health data (heart rate, steps, sleep, SpO2) is stored in Android Health Connect — the operating system's health data store. You control this data through Health Connect settings.

• NF Watch only uses Bluetooth to communicate with your GoBoult smartwatch. No other wireless communication occurs.

• No analytics, tracking, or telemetry of any kind.

• No advertisements.

Developer: Pawan Washudev
Package: com.neubofy.watch""",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
