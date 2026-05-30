package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker

private val CardDark = Color(0xFF16213E)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentBlue = Color(0xFF3B82F6)

@Composable
fun FamilyMemberCard(
    marker: FamilyDeviceMarker,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val parsedColor = remember(marker.markerColorHex) {
        try {
            Color(android.graphics.Color.parseColor(marker.markerColorHex))
        } catch (e: Exception) {
            Color(0xFF16A34A)
        }
    }

    val cardBorder = if (marker.emergencyState) {
        Modifier.border(2.dp, Color(0xFFDC2626), RoundedCornerShape(16.dp))
    } else {
        Modifier.border(1.dp, parsedColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
    }

    val cardBackground = if (marker.emergencyState) {
        CardDark.copy(alpha = 0.4f)
    } else {
        CardDark
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = cardBackground),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(cardBorder)
            .clickable { onClick() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (marker.emergencyState) {
                        Color(0xFFDC2626).copy(alpha = 0.15f)
                    } else {
                        Color.Transparent
                    }
                )
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(parsedColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = marker.markerLetter,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                    }

                    Text(
                        text = marker.label,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (marker.emergencyState) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFDC2626))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "🚨 SOS ATIVO",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF16A34A).copy(alpha = 0.2f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = "Ativo",
                            color = Color(0xFF4ADE80),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "🔋 ${marker.batteryLevel}%",
                        color = TextSecondary,
                        fontSize = 13.sp
                    )
                    if (marker.batteryCharging) {
                        Text(
                            text = "⚡",
                            color = Color(0xFFF59E0B),
                            fontSize = 12.sp
                        )
                    }
                }

                Text(
                    text = "📍 " + String.format("%.1f", marker.speed) + " km/h",
                    color = TextSecondary,
                    fontSize = 13.sp
                )

                Text(
                    text = "📱 v${marker.appVersion}",
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Spacer(modifier = Modifier.height(1.dp).fillMaxWidth().background(Color.White.copy(alpha = 0.08f)))
            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "➔ Toca para pré-visualizar rota no mapa",
                color = if (marker.emergencyState) Color(0xFFF87171) else AccentBlue,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}
