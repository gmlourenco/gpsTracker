package com.segurancarural.gpstracker.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import android.util.Log
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import android.widget.Toast
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.segurancarural.gpstracker.ui.viewmodel.HomeViewModel
import com.segurancarural.gpstracker.data.db.TelemetryDao
import com.segurancarural.gpstracker.util.SosManager
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ── Colours ────────────────────────────────────────────────────────────────
private val SosRed = Color(0xFFDC2626)
private val SosRedPressed = Color(0xFF991B1B)
private val OnlineGreen = Color(0xFF16A34A)
private val OfflineAmber = Color(0xFFF59E0B)
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)

/**
 * HomeScreen — the primary operational screen.
 *
 * Layout (top to bottom):
 *   1. App title
 *   2. Tracking toggle switch
 *   3. Giant SOS button (40% of vertical space)
 *   4. Status tiles: connectivity badge + pending count
 *   5. Metrics row: battery + GPS accuracy
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartService: () -> Unit,
    onStopService: () -> Unit,
    onSosActivate: () -> Unit,
    onSosDeactivate: () -> Unit,
    unsyncedCount: Int = 0,
    batteryLevel: Int = -1,
    lastAccuracy: Float? = null,
    isOnline: Boolean = true,
    viewModel: HomeViewModel = viewModel()
) {
    val isTracking by viewModel.isTracking.collectAsState()
    val isSosActive by viewModel.isSosActive.collectAsState()
    val gpsAccuracy by viewModel.lastAccuracy.collectAsState()

    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var showDeactivateSheet by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var userInputCode by remember { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    val deactivateSheetState = rememberModalBottomSheetState()

    // Trigger Toast when SOS changes state
    LaunchedEffect(isSosActive) {
        if (isSosActive) {
            Toast.makeText(context, "SOS ATIVADO! Alerta de emergência enviado.", Toast.LENGTH_LONG).show()
        }
    }

    // SOS long-press state
    var sosProgress by remember { mutableFloatStateOf(0f) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    // Pulse animation for SOS active state
    val infiniteTransition = rememberInfiniteTransition(label = "sos_pulse")
    val sosScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSosActive) 1.06f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sos_scale"
    )

    // Continuous radiating radar ripple animation
    val radarScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = if (isSosActive) 1.6f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_scale"
    )
    val radarAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = if (isSosActive) 0f else 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "radar_alpha"
    )

    val sosColor by animateColorAsState(
        targetValue = if (isSosActive) SosRedPressed else SosRed,
        animationSpec = tween(300),
        label = "sos_color"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // ── Header ─────────────────────────────────────────────────────────
        Column {
            Text(
                text = "🚜  Segurança Rural",
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (isSosActive) "⚠️  MODO EMERGÊNCIA ATIVO" else "Sistema de rastreio GPS",
                color = if (isSosActive) SosRed else TextSecondary,
                fontSize = 13.sp
            )
        }

        // ── Tracking toggle ────────────────────────────────────────────────
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = if (isTracking) "Rastreio Ativo" else "Rastreio Inativo",
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                    Text(
                        text = if (isTracking) "GPS a recolher localização" else "Pressione para ativar",
                        color = TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Switch(
                    checked = isTracking,
                    onCheckedChange = { checked ->
                        Log.i("HomeScreen", "👇 User toggled tracking switch to: $checked")
                        if (checked) onStartService() else onStopService()
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = OnlineGreen,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = CardDark
                    )
                )
            }
        }

        // ── SOS Button (40% vertical area) ────────────────────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp),
            contentAlignment = Alignment.Center
        ) {
            // Radar pulse ring (visible when SOS is active)
            if (isSosActive) {
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(radarScale)
                        .clip(CircleShape)
                        .background(SosRed.copy(alpha = radarAlpha))
                )
                Box(
                    modifier = Modifier
                        .size(200.dp)
                        .scale(sosScale + 0.12f)
                        .clip(CircleShape)
                        .background(SosRed.copy(alpha = 0.22f))
                )
            }

            // Circular progress indicator showing hold progress
            if (sosProgress > 0f) {
                CircularProgressIndicator(
                    progress = { sosProgress },
                    modifier = Modifier.size(216.dp),
                    color = SosRed,
                    strokeWidth = 6.dp
                )
            }

            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(sosScale)
                    .clip(CircleShape)
                    .background(sosColor)
                    .pointerInput(isSosActive) {
                        detectTapGestures(
                            onPress = {
                                if (!isSosActive) {
                                    // Long press to activate SOS (timings loaded from SosManager.ACTIVATION_HOLD_MS)
                                    longPressJob = scope.launch {
                                        val durationMs = SosManager.ACTIVATION_HOLD_MS
                                        val steps = 10
                                        val stepMs = durationMs / steps
                                        for (i in 1..steps) {
                                            delay(stepMs)
                                            sosProgress = i.toFloat() / steps
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        }
                                        Log.w("HomeScreen", "🚨 User activated SOS mode!")
                                        onSosActivate()
                                        sosProgress = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    val released = tryAwaitRelease()
                                    if (!released || sosProgress < 1f) {
                                        longPressJob?.cancel()
                                        sosProgress = 0f
                                    }
                                } else {
                                    // Already active: long press 10 seconds to prompt deactivation sheet
                                    longPressJob = scope.launch {
                                        val durationMs = SosManager.DEACTIVATION_HOLD_MS
                                        val steps = 100
                                        val stepMs = durationMs / steps
                                        for (i in 1..steps) {
                                            delay(stepMs)
                                            sosProgress = i.toFloat() / steps
                                            if (i % 10 == 0) {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            }
                                        }
                                        Log.w("HomeScreen", "🔓 10s hold complete. Launching deactivation code verify.")
                                        verificationCode = SosManager.generateVerificationCode()
                                        userInputCode = ""
                                        hasError = false
                                        showDeactivateSheet = true
                                        sosProgress = 0f
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    }

                                    val released = tryAwaitRelease()
                                    if (!released || sosProgress < 1f) {
                                        longPressJob?.cancel()
                                        sosProgress = 0f
                                    }
                                }
                            }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "SOS",
                        color = Color.White,
                        fontSize = 48.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Text(
                        text = if (isSosActive) "Pressione 10s" else "Pressione 0.2s",
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Status tiles ───────────────────────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Connectivity badge
            StatusTile(
                modifier = Modifier.weight(1f),
                title = if (isOnline) "ONLINE" else "OFFLINE",
                subtitle = if (isOnline) "Sincronizado" else "$unsyncedCount posições retidas",
                tileColor = if (isOnline) OnlineGreen.copy(alpha = 0.15f) else OfflineAmber.copy(alpha = 0.15f),
                titleColor = if (isOnline) OnlineGreen else OfflineAmber
            )

            // GPS accuracy
            StatusTile(
                modifier = Modifier.weight(1f),
                title = gpsAccuracy?.let { "±${it.toInt()}m" } ?: "–",
                subtitle = "Precisão GPS",
                tileColor = CardDark,
                titleColor = TextPrimary
            )
        }

        if (showDeactivateSheet) {
            ModalBottomSheet(
                onDismissRequest = { showDeactivateSheet = false },
                sheetState = deactivateSheetState,
                containerColor = SurfaceDark,
                dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.5f)) }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .padding(bottom = 36.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Confirmar Desativação SOS",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Para desligar o modo de emergência, introduz o código de verificação abaixo:",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center
                    )
                    
                    Text(
                        text = verificationCode,
                        color = SosRed,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 6.sp,
                        modifier = Modifier
                            .background(CardDark, RoundedCornerShape(8.dp))
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                    
                    OutlinedTextField(
                        value = userInputCode,
                        onValueChange = {
                            if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                                userInputCode = it
                                hasError = false
                                if (it == verificationCode) {
                                    onSosDeactivate()
                                    showDeactivateSheet = false
                                }
                            }
                        },
                        label = { Text("Código de 6 dígitos", color = TextSecondary) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = if (hasError) SosRed else OnlineGreen,
                            unfocusedBorderColor = TextSecondary,
                        )
                    )
                    
                    if (hasError) {
                        Text(
                            text = "Código incorreto. Tenta novamente.",
                            color = SosRed,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = { showDeactivateSheet = false },
                            colors = ButtonDefaults.buttonColors(containerColor = CardDark),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Cancelar", color = TextSecondary)
                        }

                        Button(
                            onClick = {
                                if (userInputCode == verificationCode) {
                                    onSosDeactivate()
                                    showDeactivateSheet = false
                                } else {
                                    hasError = true
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = SosRed),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Confirmar", color = Color.White)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun StatusTile(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    tileColor: Color,
    titleColor: Color,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = tileColor),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                color = titleColor,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Text(
                text = subtitle,
                color = TextSecondary,
                fontSize = 11.sp
            )
        }
    }
}
