package com.neubofy.watch.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.service.MediaListenerService
import com.neubofy.watch.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchScreen(
    connectionManager: BleConnectionManager,
    appCache: AppCache,
    onNavigateToNotifications: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val connectionState by connectionManager.connectionState.collectAsState()
    val isConnected = connectionState == ConnectionState.CONNECTED

    // Notification Access status check
    var hasNotificationAccess by remember {
        mutableStateOf(MediaListenerService.isNotificationAccessGranted(context))
    }
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess = MediaListenerService.isNotificationAccessGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // DataStore-backed settings
    val userHeight by appCache.userHeight.collectAsState(initial = 170)
    val userWeight by appCache.userWeight.collectAsState(initial = 70)
    val userAge by appCache.userAge.collectAsState(initial = 25)
    val userIsMale by appCache.userIsMale.collectAsState(initial = true)
    
    val voiceAssistantEnabled by appCache.voiceAssistantEnabled.collectAsState(initial = false)

    var showUserInfoDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceBlack)
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // ═══ Header ═══
        Text(
            "Watch",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Gold
        )
        Text(
            if (isConnected) "Connected" else "Disconnected",
            style = MaterialTheme.typography.bodySmall,
            color = if (isConnected) Color(0xFF4CAF50) else TextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        // ═══ Notification Access Card ═══
        if (!hasNotificationAccess) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Gold.copy(alpha = 0.1f))
            ) {
                Row(
                    modifier = Modifier.padding(14.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.MusicNote, null, tint = Gold, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Enable Watch Features", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text("Notification access is required for song info and app alerts", style = MaterialTheme.typography.bodySmall, color = TextMuted)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Gold)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ═══ SECTION: App Features ═══
        SectionLabel(title = "App Features")

        // Notification Settings
        FeatureCard(
            icon = Icons.Default.Notifications,
            title = "Notification Manager",
            subtitle = "Choose which apps send alerts to watch",
            accent = Color(0xFF4CAF50),
            onClick = onNavigateToNotifications
        )
        Spacer(modifier = Modifier.height(12.dp))

        // Voice Assistant Toggle
        FeatureCard(
            icon = Icons.Default.Hearing,
            title = "Voice Assistant",
            subtitle = if (voiceAssistantEnabled) "Tap watch button to trigger phone assistant" else "Feature disabled",
            accent = if (voiceAssistantEnabled) Gold else Color.Gray,
            trailing = {
                Switch(
                    checked = voiceAssistantEnabled,
                    onCheckedChange = { scope.launch { appCache.setVoiceAssistantEnabled(it) } },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Gold,
                        checkedTrackColor = Gold.copy(alpha = 0.4f)
                    )
                )
            }
        )
        
        Spacer(modifier = Modifier.height(16.dp))

        // ═══ SECTION: User Info ═══
        SectionLabel(title = "User Profile")

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showUserInfoDialog = true },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceCard)
        ) {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).background(Color(0xFF00BCD4).copy(alpha = 0.2f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Person, null, tint = Color(0xFF00BCD4), modifier = Modifier.size(22.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Personal Stats", fontWeight = FontWeight.Bold, color = TextPrimary)
                        Text(
                            "${userHeight}cm • ${userWeight}kg • ${userAge}y • ${if (userIsMale) "Male" else "Female"}",
                            style = MaterialTheme.typography.bodySmall, color = TextMuted
                        )
                    }
                }
                Icon(Icons.Default.Edit, null, tint = TextMuted, modifier = Modifier.size(18.dp))
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    // ═══ User Info Dialog ═══
    if (showUserInfoDialog) {
        UserInfoDialog(
            height = userHeight,
            weight = userWeight,
            age = userAge,
            isMale = userIsMale,
            onDismiss = { showUserInfoDialog = false },
            onSave = { h, w, a, m ->
                scope.launch {
                    appCache.setUserHeight(h)
                    appCache.setUserWeight(w)
                    appCache.setUserAge(a)
                    appCache.setUserIsMale(m)
                }
                if (isConnected) connectionManager.setUserInfo(h, w, a, m)
                showUserInfoDialog = false
            }
        )
    }
}

@Composable
fun FeatureCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceCard)
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(accent.copy(alpha = 0.2f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = accent, modifier = Modifier.size(24.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, color = TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = TextMuted)
            }
            if (trailing != null) {
                trailing()
            } else if (onClick != null) {
                Icon(Icons.Default.ChevronRight, null, tint = TextMuted)
            }
        }
    }
}


@Composable
fun UserInfoDialog(
    height: Int,
    weight: Int,
    age: Int,
    isMale: Boolean,
    onDismiss: () -> Unit,
    onSave: (Int, Int, Int, Boolean) -> Unit
) {
    var h by remember { mutableStateOf(height.toString()) }
    var w by remember { mutableStateOf(weight.toString()) }
    var a by remember { mutableStateOf(age.toString()) }
    var m by remember { mutableStateOf(isMale) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Profile") },
        text = {
            Column {
                OutlinedTextField(
                    value = h,
                    onValueChange = { h = it },
                    label = { Text("Height (cm)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = w,
                    onValueChange = { w = it },
                    label = { Text("Weight (kg)") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = a,
                    onValueChange = { a = it },
                    label = { Text("Age") },
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Gender:")
                    Spacer(modifier = Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { m = true }) {
                        RadioButton(selected = m, onClick = { m = true })
                        Text("Male")
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { m = false }) {
                        RadioButton(selected = !m, onClick = { m = false })
                        Text("Female")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(h.toIntOrNull() ?: 170, w.toIntOrNull() ?: 70, a.toIntOrNull() ?: 25, m)
            }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
