package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.ui.model.MapTheme

private val CardDark = Color(0xFF16213E)
private val AccentBlue = Color(0xFF3B82F6)

@Composable
fun MapThemeSwitcher(
    currentTheme: MapTheme,
    onThemeChange: (MapTheme) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Main circle button (always visible, represents the current selection)
        Card(
            colors = CardDefaults.cardColors(containerColor = CardDark.copy(alpha = 0.92f)),
            shape = CircleShape,
            modifier = Modifier
                .size(48.dp)
                .clickable { isExpanded = !isExpanded },
            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = currentTheme.icon,
                    fontSize = 20.sp
                )
            }
        }

        // Expanded menu options
        if (isExpanded) {
            MapTheme.entries.forEach { theme ->
                val isSelected = currentTheme == theme
                val backgroundColor = if (isSelected) AccentBlue else CardDark.copy(alpha = 0.92f)
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = backgroundColor),
                    shape = CircleShape,
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            onThemeChange(theme)
                            isExpanded = false
                        },
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = theme.icon,
                            fontSize = 20.sp
                        )
                    }
                }
            }
        }
    }
}
