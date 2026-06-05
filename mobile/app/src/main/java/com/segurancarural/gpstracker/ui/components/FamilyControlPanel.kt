package com.segurancarural.gpstracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.ui.model.FamilyRefreshStatus

private val CardDark = Color(0xFF16213E)
private val SurfaceDark = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentBlue = Color(0xFF3B82F6)

@Composable
fun FamilyControlPanel(
    findFamilyEnabled: Boolean,
    onFindFamilyChange: (Boolean) -> Unit,
    refreshStatus: FamilyRefreshStatus,
    onRefreshClick: () -> Unit,
    onTextClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Refresh icon spinning animation
    val rotationTransition = rememberInfiniteTransition(label = "refresh_rotation")
    val rotationAngle by rotationTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotation"
    )

    // Dynamic color depending on refresh status
    val refreshButtonColor by animateColorAsState(
        targetValue = when (refreshStatus) {
            FamilyRefreshStatus.Loading -> AccentBlue
            FamilyRefreshStatus.Success -> Color(0xFF16A34A) // Green (for 1s)
            FamilyRefreshStatus.Error -> Color(0xFFDC2626)   // Red (for 5s)
            FamilyRefreshStatus.Idle -> CardDark.copy(alpha = 0.9f)
        },
        animationSpec = tween(300),
        label = "refresh_color"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.92f)),
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(48.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🔎 Localizar Família",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onTextClick() }
                )
                Switch(
                    checked = findFamilyEnabled,
                    onCheckedChange = onFindFamilyChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = AccentBlue,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = SurfaceDark.copy(alpha = 0.6f)
                    ),
                    modifier = Modifier.scale(0.85f)
                )
            }

            if (findFamilyEnabled) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(refreshButtonColor)
                        .clickable(enabled = refreshStatus != FamilyRefreshStatus.Loading) {
                            onRefreshClick()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Atualizar",
                        tint = Color.White,
                        modifier = Modifier
                            .size(20.dp)
                            .rotate(if (refreshStatus == FamilyRefreshStatus.Loading) rotationAngle else 0f)
                    )
                }
            }
        }
    }
}
