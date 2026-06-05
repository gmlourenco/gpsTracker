package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val CardDark = Color(0xFF16213E)
private val TextSecondary = Color(0xFF94A3B8)

@Composable
fun MapEmptyState(
    findFamilyEnabled: Boolean,
    isEmptyFamily: Boolean,
    isEmptyRoute: Boolean,
    modifier: Modifier = Modifier
) {
    if (findFamilyEnabled) {
        if (isEmptyFamily) {
            Text(
                text = "A carregar localizações da família...",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDark.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    } else {
        if (isEmptyRoute) {
            Text(
                text = "Sem histórico local — inicia o rastreio para ver a rota",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(CardDark.copy(alpha = 0.9f))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }
    }
}
