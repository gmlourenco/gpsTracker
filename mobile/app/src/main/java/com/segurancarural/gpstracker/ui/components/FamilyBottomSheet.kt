package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.ui.model.FamilyDeviceMarker

private val SurfaceDark = Color(0xFF1A1A2E)
private val TextSecondary = Color(0xFF94A3B8)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyBottomSheet(
    showBottomSheet: Boolean,
    onDismissRequest: () -> Unit,
    sheetState: SheetState,
    familyMarkers: List<FamilyDeviceMarker>,
    onMemberClick: (FamilyDeviceMarker) -> Unit,
    modifier: Modifier = Modifier
) {
    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = onDismissRequest,
            sheetState = sheetState,
            containerColor = SurfaceDark,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TextSecondary.copy(alpha = 0.5f)) },
            modifier = modifier
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp)
            ) {
                Text(
                    text = "Membros da Família",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                val sortedMarkers = remember(familyMarkers) {
                    familyMarkers.sortedWith(
                        compareByDescending<FamilyDeviceMarker> { it.emergencyState }
                            .thenBy { it.label }
                    )
                }

                if (sortedMarkers.isEmpty()) {
                    Text(
                        text = "Nenhum membro localizado ainda.",
                        color = TextSecondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(vertical = 24.dp)
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(sortedMarkers) { marker ->
                            FamilyMemberCard(
                                marker = marker,
                                onClick = { onMemberClick(marker) }
                            )
                        }
                    }
                }
            }
        }
    }
}
