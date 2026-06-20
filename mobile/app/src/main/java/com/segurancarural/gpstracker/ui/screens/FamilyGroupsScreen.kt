package com.segurancarural.gpstracker.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF16A34A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(false) }
    val farmRepository = remember { com.segurancarural.gpstracker.data.repository.FarmRepository(context) }
    var inviteCode by remember { mutableStateOf<String?>(null) }
    var farmName by remember { mutableStateOf<String?>(null) }
    var userRole by remember { mutableStateOf<String?>(null) }
    var memberCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        val result = farmRepository.getFarmDetails()
        if (result.isSuccess) {
            val data = result.getOrNull()
            inviteCode = data?.inviteCode
            farmName = data?.farmName
            userRole = data?.userRole
            memberCount = data?.members?.size ?: 0
        } else {
            errorMessage = result.exceptionOrNull()?.message
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Família ${farmName?.let { " - $it" } ?: ""}",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        if (errorMessage != null) {
            Text(text = "Erro: $errorMessage", color = Color.Red)
        } else if (isLoading) {
            CircularProgressIndicator(color = AccentGreen)
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Código de Convite", color = TextSecondary, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = inviteCode ?: "Nenhum código ativo",
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp
                        )
                        if (inviteCode != null) {
                            IconButton(onClick = {
                                val sendIntent = Intent().apply {
                                    action = Intent.ACTION_SEND
                                    putExtra(Intent.EXTRA_TEXT, "Junta-te ao meu Grupo Familiar na app Segurança Rural com o código: $inviteCode")
                                    type = "text/plain"
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Partilhar código"))
                            }) {
                                Icon(Icons.Default.Share, contentDescription = "Partilhar", tint = AccentGreen)
                            }
                        }
                    }
                    if (userRole == "owner" || userRole == "admin") {
                        Text("O código expira em 7 dias.", color = TextSecondary, fontSize = 11.sp)
                    } else {
                        Text("Apenas o Administrador pode gerar códigos de convite.", color = TextSecondary, fontSize = 11.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            Text("Membros da Família", color = TextPrimary, fontWeight = FontWeight.Bold)
            Text("$memberCount membro(s) nesta família", color = TextSecondary, fontSize = 12.sp)
        }
    }
}
