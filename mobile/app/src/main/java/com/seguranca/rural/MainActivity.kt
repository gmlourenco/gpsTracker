package com.seguranca.rural

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.seguranca.rural.ui.screens.ConfigScreen
import com.seguranca.rural.ui.screens.HomeScreen
import com.seguranca.rural.ui.screens.MapScreen
import com.seguranca.rural.ui.theme.SegurancaRuralTheme

/**
 * MainActivity — single-activity host with bottom navigation.
 *
 * Handles:
 *   - Location permission requests on first launch
 *   - Navigation between Home, Map, and Config screens
 *   - Starting the periodic [SyncWorker] on launch
 */
class MainActivity : ComponentActivity() {

    // ── Permission launcher ────────────────────────────────────────────────

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            checkBackgroundAndBattery()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            checkBatteryOptimization()
        } else {
            Toast.makeText(this, "Aviso: Sem permissão em 2º plano, o GPS pode falhar com ecrã desligado.", Toast.LENGTH_LONG).show()
            checkBatteryOptimization()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule background sync (safe to call multiple times)
        SyncWorker.schedule(this)

        // Request location and notification permissions if not already granted
        if (!hasLocationPermission()) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            locationPermissionLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            checkBackgroundAndBattery()
        }

        setContent {
            SegurancaRuralTheme {
                var currentScreen by rememberSaveable { mutableStateOf(AppScreen.HOME) }

                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    bottomBar = {
                        NavigationBar {
                            AppScreen.entries.forEach { screen ->
                                NavigationBarItem(
                                    icon = { Icon(screen.icon, contentDescription = screen.label) },
                                    label = { Text(screen.label) },
                                    selected = currentScreen == screen,
                                    onClick = { currentScreen = screen }
                                )
                            }
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        AnimatedContent(targetState = currentScreen) { screen ->
                            when (screen) {
                                AppScreen.HOME -> HomeScreen(
                                    onStartService = { startLocationService() },
                                    onStopService = { stopLocationService() },
                                    onSosActivate = { activateSos() },
                                    onSosDeactivate = { deactivateSos() },
                                )
                                AppScreen.MAP -> MapScreen()
                                AppScreen.CONFIG -> ConfigScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun checkBackgroundAndBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return
            }
        }
        checkBatteryOptimization()
    }

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        }
    }

    private fun startLocationService() {
        getSharedPreferences("tracking_prefs", MODE_PRIVATE)
            .edit().putBoolean("tracking_active", true).apply()
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_START
        }
        startForegroundService(intent)
    }

    private fun stopLocationService() {
        getSharedPreferences("tracking_prefs", MODE_PRIVATE)
            .edit().putBoolean("tracking_active", false).apply()
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_STOP
        }
        startService(intent)
    }

    private fun activateSos() {
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_SOS_ACTIVATE
        }
        startForegroundService(intent)
    }

    private fun deactivateSos() {
        val intent = Intent(this, LocationForegroundService::class.java).apply {
            action = LocationForegroundService.ACTION_SOS_DEACTIVATE
        }
        startService(intent)
    }
}

// ── Navigation destinations ────────────────────────────────────────────────

enum class AppScreen(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    HOME("Início", Icons.Default.Home),
    MAP("Mapa", Icons.Filled.Map),
    CONFIG("Config", Icons.Default.Settings),
}
