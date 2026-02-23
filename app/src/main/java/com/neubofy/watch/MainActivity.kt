package com.neubofy.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.neubofy.watch.ble.BleConnectionManager
import com.neubofy.watch.ble.BleScanner
import com.neubofy.watch.ble.ConnectionState
import com.neubofy.watch.data.AppCache
import com.neubofy.watch.data.HealthConnectManager
import com.neubofy.watch.ui.screens.*
import com.neubofy.watch.ui.theme.NFWatchTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var bleScanner: BleScanner
    private lateinit var connectionManager: BleConnectionManager
    private lateinit var appCache: AppCache
    private lateinit var healthConnectManager: HealthConnectManager
    private lateinit var healthRepository: com.neubofy.watch.data.HealthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        bleScanner = BleScanner(this)
        connectionManager = BleConnectionManager.getInstance(this)
        appCache = AppCache(this)
        healthConnectManager = HealthConnectManager(this)
        
        val database = com.neubofy.watch.data.db.HealthDatabase.getDatabase(this)
        healthRepository = com.neubofy.watch.data.HealthRepository(database.healthDao(), healthConnectManager, appCache)
        connectionManager.setRepository(healthRepository) // Inject repository

        // Auto-sync current day from Health Connect back to Room DB on app open
        lifecycleScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                if (healthConnectManager.isAvailable() && healthConnectManager.hasAllPermissions()) {
                    healthRepository.syncTodayFromHealthConnect()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Schedule the "Hour Waker" for periodic background sync and reconnection insurance
        com.neubofy.watch.worker.WatchSyncWorker.schedule(this, 60)

        // Conditional Foreground Service Start
        lifecycleScope.launch {
            val runBg = true // Always run in background
            val complete = appCache.isOnboardingCompleteSynchronous() 
            val hasPerms = BleScanner.hasPermissions(this@MainActivity)
            val serviceIntent = android.content.Intent(this@MainActivity, com.neubofy.watch.service.NFWatchService::class.java)
            
            if (complete && hasPerms && runBg) {
                try {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent)
                    } else {
                        startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                stopService(serviceIntent)
            }
        }

        // Auto-reconnect on launch if we have a paired device AND permissions are granted
        lifecycleScope.launch {
            appCache.pairedDeviceAddress.collect { address ->
                if (address != null && connectionManager.connectionState.value == ConnectionState.DISCONNECTED) {
                    val hasPerm = BleScanner.hasPermissions(this@MainActivity)
                    if (hasPerm) {
                        try { connectionManager.reconnect(address) } catch (_: Exception) {}
                    }
                }
            }
        }

        setContent {
            val uiAccent by appCache.uiAccent.collectAsState(initial = "Gold")
            NFWatchTheme(accentName = uiAccent) {
                NFWatchApp(
                    bleScanner = bleScanner,
                    connectionManager = connectionManager,
                    appCache = appCache,
                    healthConnectManager = healthConnectManager,
                    healthRepository = healthRepository
                )
            }
        }
    }
}

@Composable
fun NFWatchApp(
    bleScanner: BleScanner,
    connectionManager: BleConnectionManager,
    appCache: AppCache,
    healthConnectManager: HealthConnectManager,
    healthRepository: com.neubofy.watch.data.HealthRepository
) {
    val navController = rememberNavController()
    val currentBackStack by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStack?.destination?.route ?: "welcome"
    val scope = rememberCoroutineScope()
    val pairedAddress by appCache.pairedDeviceAddress.collectAsState(initial = null)
    val onboardingComplete by appCache.onboardingComplete.collectAsState(initial = false)
    
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var hasPermissions by remember { mutableStateOf(BleScanner.hasPermissions(context)) }

    // Re-check permissions whenever the app comes to foreground
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                hasPermissions = BleScanner.hasPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Determine start: permissions (if missing) → home (if paired) → scan
    val startDestination = when {
        !hasPermissions -> "permissions"
        pairedAddress != null -> "home"
        else -> "scan"
    }


    // Hide bottom nav on onboarding screens
    val showBottomBar = currentRoute in listOf("home", "watch", "developer", "settings")

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Home, contentDescription = null) },
                        label = { Text("Home") },
                        selected = currentRoute == "home",
                        onClick = {
                            if (currentRoute != "home") {
                                navController.navigate("home") {
                                    popUpTo("home") { inclusive = true }
                                }
                            }
                        }
                    )
                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Watch, contentDescription = null) },
                        label = { Text("Watch") },
                        selected = currentRoute == "watch",
                        onClick = {
                            if (currentRoute != "watch") {
                                navController.navigate("watch") {
                                    popUpTo("home")
                                }
                            }
                        }
                    )

                    NavigationBarItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                        selected = currentRoute == "settings",
                        onClick = {
                            if (currentRoute != "settings") {
                                navController.navigate("settings") {
                                    popUpTo("home")
                                }
                            }
                        }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(padding)
        ) {
            composable("permissions") {
                PermissionsScreen(
                    healthConnectManager = healthConnectManager,
                    onAllGranted = {
                        scope.launch { appCache.setOnboardingComplete() }
                        val target = if (pairedAddress != null) "home" else "scan"
                        navController.navigate(target) {
                            popUpTo("permissions") { inclusive = true }
                        }
                    }
                )
            }

            // Main app flow
            composable("scan") {
                ScanScreen(
                    bleScanner = bleScanner,
                    onDeviceSelected = { device ->
                        scope.launch {
                            appCache.savePairedDevice(device.address, device.name)
                        }
                        connectionManager.connect(device.address)
                        navController.navigate("home") {
                            popUpTo("scan") { inclusive = true }
                        }
                    }
                )
            }
            composable("home") {
                HomeScreen(
                    connectionManager = connectionManager,
                    appCache = appCache,
                    healthRepository = healthRepository,
                    onNavigateToDetail = { type ->
                        if (type == "permissions_redirect") {
                            navController.navigate("permissions")
                        } else {
                            navController.navigate("health_detail/$type")
                        }
                    }
                )
            }
            composable(
                route = "health_detail/{type}",
                arguments = listOf(navArgument("type") { type = NavType.StringType })
            ) { backStackEntry ->
                val type = backStackEntry.arguments?.getString("type") ?: "unknown"
                HealthDetailScreen(
                    metricType = type,
                    healthConnectManager = healthConnectManager,
                    connectionManager = connectionManager,
                    healthRepository = healthRepository,
                    appCache = appCache,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("watch") {
                WatchScreen(
                    connectionManager = connectionManager,
                    appCache = appCache,
                    onNavigateToNotifications = {
                        navController.navigate("notification_settings")
                    }
                )
            }
            composable("notification_settings") {
                NotificationSettingsScreen(
                    appCache = appCache,
                    onBack = { navController.popBackStack() }
                )
            }

            composable("settings") {
                SettingsScreen(
                    connectionManager = connectionManager,
                    appCache = appCache,
                    onDisconnect = {
                        navController.navigate("scan") {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                    onNavigateToDeveloper = {
                        navController.navigate("developer")
                    }
                )
            }
        }
    }
}
