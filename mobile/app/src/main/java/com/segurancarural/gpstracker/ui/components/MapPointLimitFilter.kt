package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.ui.model.MapPointLimit

private val CardDark = Color(0xFF16213E)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentBlue = Color(0xFF3B82F6)

@Composable
fun MapPointLimitFilter(
    selectedLimit: MapPointLimit,
    onLimitSelected: (MapPointLimit) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(CardDark.copy(alpha = 0.92f))
                .padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            MapPointLimit.entries.forEach { limit ->
                FilterChip(
                    selected = selectedLimit == limit,
                    onClick = { onLimitSelected(limit) },
                    label = {
                        Text(
                            text = limit.label,
                            fontSize = 12.sp,
                            color = if (selectedLimit == limit) Color.White else TextSecondary
                        )
                    },
                    modifier = Modifier.padding(horizontal = 3.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentBlue,
                        containerColor = Color.Transparent,
                    ),
                    border = null,
                )
            }
        }
    }
}
