package com.seguranca.rural.ui.screens

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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF16A34A)

/**
 * ConfigScreen — tracking parameter settings and emergency contact.
 *
 * Settings (persisted via SharedPreferences in a real implementation):
 *   - Tracking interval selector: 5min / 15min / 30min / Adaptive
 *   - Sync on mobile data toggle
 *   - Pause when static toggle
 *   - Emergency contact phone number input
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen() {
    val intervals = listOf("5 minutos", "15 minutos", "30 minutos", "Modo Adaptativo")
    var selectedInterval by remember { mutableStateOf(intervals[3]) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    var syncOnMobileData by remember { mutableStateOf(true) }
    var pauseWhenStatic by remember { mutableStateOf(true) }
    var emergencyContact by remember { mutableStateOf("") }
    var contactError by remember { mutableStateOf<String?>(null) }

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

        // ── Tracking interval ──────────────────────────────────────────────
        ConfigCard(title = "Intervalo de Rastreio") {
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedInterval,
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
                    intervals.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option, color = TextPrimary) },
                            onClick = {
                                selectedInterval = option
                                dropdownExpanded = false
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Modo Adaptativo ajusta automaticamente com base no movimento",
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
            onClick = { /* TODO: Persist to SharedPreferences */ },
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
