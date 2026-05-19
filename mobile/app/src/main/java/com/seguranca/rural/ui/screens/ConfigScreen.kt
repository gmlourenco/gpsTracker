package com.seguranca.rural.ui.screens

import android.content.Context
import android.content.Intent
import android.util.Log
import com.seguranca.rural.service.LocationForegroundService
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF16A34A)

private const val PREFS_NAME = "tracking_prefs"

/**
 * ConfigScreen — persists tracking parameters and device identity to SharedPreferences.
 * The LocationForegroundService reads these values on every location fix.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    // ── State ─────────────────────────────────────────────────────────────
    var deviceLabel by remember { mutableStateOf("") }
    var emergencyContact by remember { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }
    var syncOnMobileData by remember { mutableStateOf(true) }
    var pauseWhenStatic by remember { mutableStateOf(true) }

    // Interval: stored as Long minutes, shown as minutes
    val intervalOptions = listOf(1L, 5L, 10L, 15L, 30L, 60L)
    val intervalLabels = listOf("1 minuto", "5 minutos", "10 minutos", "15 minutos", "30 minutos", "60 minutos")
    var selectedIntervalIdx by remember { mutableStateOf(0) } // default 1min

    // Distance threshold in metres
    var distanceThresholdM by remember { mutableFloatStateOf(200f) }

    // ── Load from SharedPreferences on first composition ──────────────────
    LaunchedEffect(Unit) {
        deviceLabel = prefs.getString("device_label", "Dispositivo") ?: "Dispositivo"
        emergencyContact = prefs.getString("emergency_contact", "") ?: ""
        syncOnMobileData = prefs.getBoolean("sync_on_mobile_data", true)
        pauseWhenStatic = prefs.getBoolean("pause_when_static", true)
        distanceThresholdM = prefs.getFloat("tracking_distance_m", 200f)
        val savedIntervalMs = prefs.getLong("tracking_interval_ms", 1 * 60 * 1000L)
        val savedMinutes = savedIntervalMs / 60_000L
        selectedIntervalIdx = intervalOptions.indexOfFirst { it == savedMinutes }.coerceAtLeast(0)
        Log.d("ConfigScreen", "Settings loaded: label=$deviceLabel, interval=${savedMinutes}min, distance=${distanceThresholdM}m")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Configurações",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        // ── Device identity ────────────────────────────────────────────────
        ConfigCard(title = "Identidade do Dispositivo") {
            OutlinedTextField(
                value = deviceLabel,
                onValueChange = { deviceLabel = it },
                label = { Text("Nome do dispositivo", color = TextSecondary) },
                placeholder = { Text("Ex: Trator do João", color = TextSecondary.copy(0.5f)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = TextSecondary,
                )
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Nome visível no mapa da dashboard",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // ── Tracking interval ──────────────────────────────────────────────
        ConfigCard(title = "Intervalo de Rastreio") {
            var dropdownExpanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = intervalLabels[selectedIntervalIdx],
                    onValueChange = {},
                    readOnly = true,
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(dropdownExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = AccentGreen,
                        unfocusedBorderColor = TextSecondary,
                    )
                )
                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false },
                    modifier = Modifier.background(CardDark)
                ) {
                    intervalLabels.forEachIndexed { index, label ->
                        DropdownMenuItem(
                            text = { Text(label, color = TextPrimary) },
                            onClick = {
                                selectedIntervalIdx = index
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Tempo máximo entre atualizações de posição",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // ── Distance threshold ─────────────────────────────────────────────
        ConfigCard(title = "Distância Mínima de Movimento") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Limiar", color = TextSecondary, fontSize = 13.sp)
                Text(
                    "${distanceThresholdM.roundToInt()} m",
                    color = AccentGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Slider(
                value = distanceThresholdM,
                onValueChange = { distanceThresholdM = it },
                valueRange = 50f..500f,
                steps = 8, // 50m steps
                colors = SliderDefaults.colors(
                    thumbColor = AccentGreen,
                    activeTrackColor = AccentGreen,
                    inactiveTrackColor = TextSecondary.copy(alpha = 0.3f)
                )
            )
            Text(
                text = "O GPS só acorda o CPU após mover esta distância",
                color = TextSecondary,
                fontSize = 11.sp
            )
        }

        // ── Data policies ──────────────────────────────────────────────────
        ConfigCard(title = "Políticas de Dados") {
            SettingToggleRow(
                label = "Sincronizar em dados móveis",
                description = "Envia localizações mesmo sem Wi-Fi",
                checked = syncOnMobileData,
                onCheckedChange = { syncOnMobileData = it }
            )
            Spacer(modifier = Modifier.height(12.dp))
            SettingToggleRow(
                label = "Pausar quando parado",
                description = "Reduz frequência GPS se imóvel",
                checked = pauseWhenStatic,
                onCheckedChange = { pauseWhenStatic = it }
            )
        }

        // ── Emergency contact ──────────────────────────────────────────────
        ConfigCard(title = "Contacto de Emergência") {
            OutlinedTextField(
                value = emergencyContact,
                onValueChange = { value ->
                    emergencyContact = value
                    contactError = if (value.isNotEmpty() &&
                        !value.matches(Regex("^[+]?[0-9\\s\\-()]{9,15}$"))
                    ) {
                        "Número de telefone inválido"
                    } else null
                },
                label = { Text("Número de telefone", color = TextSecondary) },
                placeholder = { Text("+351 912 345 678", color = TextSecondary.copy(0.5f)) },
                isError = contactError != null,
                supportingText = {
                    contactError?.let { Text(it, color = Color(0xFFEF4444)) }
                        ?: Text("Usado como fallback se a rede falhar", color = TextSecondary, fontSize = 11.sp)
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentGreen,
                    unfocusedBorderColor = TextSecondary,
                )
            )
        }

        // ── Save button ────────────────────────────────────────────────────
        Button(
            onClick = {
                val intervalMs = intervalOptions[selectedIntervalIdx] * 60_000L
                prefs.edit()
                    .putString("device_label", deviceLabel.trim().ifEmpty { "Dispositivo" })
                    .putString("emergency_contact", emergencyContact)
                    .putBoolean("sync_on_mobile_data", syncOnMobileData)
                    .putBoolean("pause_when_static", pauseWhenStatic)
                    .putLong("tracking_interval_ms", intervalMs)
                    .putFloat("tracking_distance_m", distanceThresholdM)
                    .apply()
                Log.i("ConfigScreen", "✅ Settings saved: label=${deviceLabel}, interval=${intervalOptions[selectedIntervalIdx]}min, distance=${distanceThresholdM.roundToInt()}m")
                context.startService(
                    Intent(context, LocationForegroundService::class.java).apply {
                        action = LocationForegroundService.ACTION_RELOAD_CONFIG
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = contactError == null,
            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Guardar Configurações", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun ConfigCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SettingToggleRow(
    label: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TextPrimary, fontWeight = FontWeight.Medium, fontSize = 14.sp)
            Text(description, color = TextSecondary, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = AccentGreen,
                checkedThumbColor = Color.White
            )
        )
    }
}
