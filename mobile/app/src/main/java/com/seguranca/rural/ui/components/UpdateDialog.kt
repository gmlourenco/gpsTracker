package com.seguranca.rural.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.seguranca.rural.update.AppUpdateOffer

@Composable
fun AppUpdateDialog(
    offer: AppUpdateOffer,
    onDismiss: () -> Unit,
    onConfirmUpdate: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!offer.forceUpdate) onDismiss() },
        title = { Text("Atualização disponível") },
        text = {
            Text(
                "Versão ${offer.latestVersion} disponível.\n\n${offer.releaseNotes}\n\n" +
                    "As tuas configurações serão mantidas."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirmUpdate) {
                Text("Atualizar", color = Color(0xFF16A34A))
            }
        },
        dismissButton = if (!offer.forceUpdate) {
            {
                TextButton(onClick = onDismiss) {
                    Text("Mais tarde")
                }
            }
        } else null,
    )
}
