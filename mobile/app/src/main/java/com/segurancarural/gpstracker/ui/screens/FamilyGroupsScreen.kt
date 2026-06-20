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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.segurancarural.gpstracker.data.repository.FarmDto
import kotlinx.coroutines.launch

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
    var isActionLoading by remember { mutableStateOf(false) }
    val farmRepository = remember { com.segurancarural.gpstracker.data.repository.FarmRepository(context) }
    
    var isAnonymous by remember { mutableStateOf(true) }
    var farmsList by remember { mutableStateOf<List<FarmDto>>(emptyList()) }
    var selectedFarm by remember { mutableStateOf<FarmDto?>(null) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    
    var showAddFamilySection by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }

    fun loadFarms() {
        scope.launch {
            isLoading = true
            errorMessage = null
            val result = farmRepository.getFarmDetails()
            if (result.isSuccess) {
                val data = result.getOrNull()
                isAnonymous = data?.isAnonymous ?: true
                farmsList = data?.farms ?: emptyList()
                if (farmsList.isNotEmpty() && selectedFarm == null) {
                    selectedFarm = farmsList.first()
                } else if (farmsList.isNotEmpty() && selectedFarm != null) {
                    // Update selected farm details
                    selectedFarm = farmsList.find { it.farmId == selectedFarm?.farmId } ?: farmsList.first()
                }
                
                if (farmsList.isEmpty()) {
                    showAddFamilySection = true
                } else {
                    showAddFamilySection = false
                }
            } else {
                errorMessage = result.exceptionOrNull()?.message
            }
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadFarms()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SurfaceDark)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Grupos Familiares",
            style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )

        if (errorMessage != null) {
            Text(text = "Erro: $errorMessage", color = Color.Red)
            Button(onClick = { loadFarms() }, colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)) {
                Text("Tentar Novamente", color = Color.White)
            }
        } else if (isLoading) {
            CircularProgressIndicator(color = AccentGreen)
        } else {
            // Se tiver farms, mostrar o Dropdown e Detalhes
            if (farmsList.isNotEmpty()) {
                // Dropdown para selecionar a Farm
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedFarm?.farmName ?: "Selecionar...",
                        onValueChange = {},
                        readOnly = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = CardDark,
                            focusedContainerColor = CardDark,
                            unfocusedTextColor = TextPrimary,
                            focusedTextColor = TextPrimary
                        )
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        farmsList.forEach { farm ->
                            DropdownMenuItem(
                                text = { Text(farm.farmName) },
                                onClick = {
                                    if (selectedFarm?.farmId != farm.farmId) {
                                        selectedFarm = farm
                                        scope.launch {
                                            farmRepository.syncCurrentDeviceToFarm(farm.farmId)
                                        }
                                    }
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Detalhes da Farm Selecionada
                selectedFarm?.let { farm ->
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
                                    text = farm.inviteCode ?: "Nenhum código ativo",
                                    color = AccentGreen,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 24.sp
                                )
                                if (farm.inviteCode != null) {
                                    IconButton(onClick = {
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND
                                            putExtra(Intent.EXTRA_TEXT, "Junta-te ao meu Grupo Familiar na app Segurança Rural com o código: ${farm.inviteCode}")
                                            type = "text/plain"
                                        }
                                        context.startActivity(Intent.createChooser(sendIntent, "Partilhar código"))
                                    }) {
                                        Icon(Icons.Default.Share, contentDescription = "Partilhar", tint = AccentGreen)
                                    }
                                }
                            }
                            if (farm.userRole == "owner" || farm.userRole == "admin") {
                                Text("O código expira em 7 dias.", color = TextSecondary, fontSize = 11.sp)
                            } else {
                                Text("Apenas o Administrador pode ver/gerar códigos de convite.", color = TextSecondary, fontSize = 11.sp)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text("Membros (${farm.members.size})", color = TextPrimary, fontWeight = FontWeight.Bold)
                    
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(farm.members) { member ->
                            Card(colors = CardDefaults.cardColors(containerColor = CardDark)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Membro: ${member.user_id.take(8)}...", color = TextPrimary, fontSize = 14.sp)
                                    Text(member.role.uppercase(), color = AccentGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                if (!showAddFamilySection) {
                    TextButton(onClick = { showAddFamilySection = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Adicionar", tint = AccentGreen)
                        Spacer(modifier = Modifier.padding(4.dp))
                        Text("Adicionar / Juntar a outra família", color = AccentGreen)
                    }
                }
            }

            // Secção de Criar/Juntar
            if (showAddFamilySection) {
                if (farmsList.isNotEmpty()) {
                    TextButton(onClick = { showAddFamilySection = false }) {
                        Text("Cancelar", color = TextSecondary)
                    }
                }
                
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardDark),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Criar Nova Família", color = TextPrimary, fontWeight = FontWeight.Bold)
                        if (isAnonymous) {
                            Text("Para criar uma família precisas de ter feito login com a tua conta (Google). Como estás num acesso anónimo/convidado, só podes juntar-te a famílias existentes.", color = TextSecondary, fontSize = 12.sp)
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    isActionLoading = true
                                    val res = farmRepository.createFarm()
                                    if (res.isSuccess) {
                                        val newFarmId = res.getOrNull()?.farmId
                                        if (newFarmId != null) {
                                            farmRepository.syncCurrentDeviceToFarm(newFarmId)
                                        }
                                        loadFarms()
                                    } else {
                                        errorMessage = res.exceptionOrNull()?.message
                                    }
                                    isActionLoading = false
                                }
                            },
                            enabled = !isAnonymous && !isActionLoading,
                            colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            if (isActionLoading && !isAnonymous) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                            } else {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(modifier = Modifier.padding(4.dp))
                                Text("Criar Família")
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Juntar por Código", color = TextPrimary, fontWeight = FontWeight.Bold)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = inviteCodeInput,
                                onValueChange = { inviteCodeInput = it.uppercase() },
                                placeholder = { Text("Ex: AB2X9P") },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedContainerColor = SurfaceDark,
                                    focusedContainerColor = SurfaceDark,
                                    unfocusedTextColor = TextPrimary,
                                    focusedTextColor = TextPrimary
                                )
                            )
                            Button(
                                onClick = {
                                    if (inviteCodeInput.isNotBlank()) {
                                        scope.launch {
                                            isActionLoading = true
                                            val res = farmRepository.joinFarm(inviteCodeInput)
                                            if (res.isSuccess) {
                                                val newFarmId = res.getOrNull()?.farmId
                                                if (newFarmId != null) {
                                                    farmRepository.syncCurrentDeviceToFarm(newFarmId)
                                                }
                                                inviteCodeInput = ""
                                                loadFarms()
                                            } else {
                                                errorMessage = res.exceptionOrNull()?.message
                                            }
                                            isActionLoading = false
                                        }
                                    }
                                },
                                enabled = inviteCodeInput.isNotBlank() && !isActionLoading,
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                            ) {
                                if (isActionLoading && inviteCodeInput.isNotBlank()) {
                                    CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                                } else {
                                    Icon(Icons.Default.GroupAdd, contentDescription = null)
                                    Spacer(modifier = Modifier.padding(4.dp))
                                    Text("Juntar")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
