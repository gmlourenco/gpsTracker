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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.segurancarural.gpstracker.ui.components.FamilyMemberListCard
import com.segurancarural.gpstracker.ui.viewmodel.FamilyGroupsViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
private val SurfaceDark = Color(0xFF1A1A2E)
private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val TextSecondary = Color(0xFF94A3B8)
private val AccentGreen = Color(0xFF16A34A)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyGroupsScreen(viewModel: FamilyGroupsViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var dropdownExpanded by remember { mutableStateOf(false) }
    var inviteCodeInput by remember { mutableStateOf("") }
    var newFarmNameInput by remember { mutableStateOf("") }
    // ── Confirmation Dialog ─────────────────────────────────────
    state.pendingAction?.let { pending ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissPendingAction() },
            title = { Text(pending.confirmationTitle, fontWeight = FontWeight.Bold) },
            text = { Text(pending.confirmationMessage) },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPendingAction() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (pending.action == "kick") Color(0xFFDC2626) else AccentGreen
                    )
                ) { Text("Confirmar") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissPendingAction() }) { Text("Cancelar") }
            },
            containerColor = CardDark,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary,
        )
    }
    Column(
        modifier = Modifier.fillMaxSize().background(SurfaceDark).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Grupos Familiares", style = MaterialTheme.typography.headlineSmall,
            color = TextPrimary, fontWeight = FontWeight.Bold)
        when {
            state.errorMessage != null -> {
                Text("Erro: ${state.errorMessage}", color = Color.Red)
                Button(onClick = { viewModel.loadFarms() },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                ) { Text("Tentar Novamente", color = Color.White) }
            }
            state.isLoading -> CircularProgressIndicator(color = AccentGreen)
            else -> {
                // ── Farm Selector ─────────────────────────────────
                if (state.farms.isNotEmpty()) {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = state.selectedFarm?.farmName ?: "Selecionar...",
                            onValueChange = {}, readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedContainerColor = CardDark, focusedContainerColor = CardDark,
                                unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                            )
                        )
                        ExposedDropdownMenu(expanded = dropdownExpanded, onDismissRequest = { dropdownExpanded = false }) {
                            state.farms.forEach { farm ->
                                DropdownMenuItem(
                                    text = { Text(farm.farmName) },
                                    onClick = { viewModel.selectFarm(farm); dropdownExpanded = false }
                                )
                            }
                        }
                    }
                    // ── Selected Farm Details ─────────────────────────
                    state.selectedFarm?.let { farm ->
                        // Invite Code Section (only for privileged users)
                        if (farm.canInvite) {
                            Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("Código de Convite", color = TextSecondary, fontSize = 12.sp)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = farm.inviteCode ?: "Nenhum código ativo",
                                            color = if (farm.inviteCode != null) AccentGreen else TextSecondary,
                                            fontWeight = FontWeight.Bold, fontSize = 24.sp
                                        )
                                        if (farm.inviteCode != null) {
                                            IconButton(onClick = {
                                                val sendIntent = Intent().apply {
                                                    action = Intent.ACTION_SEND
                                                    putExtra(Intent.EXTRA_TEXT,
                                                        "Junta-te à família ${farm.farmName} na app Segurança Rural com o código: ${farm.inviteCode}")
                                                    type = "text/plain"
                                                }
                                                context.startActivity(Intent.createChooser(sendIntent, "Partilhar código"))
                                            }) {
                                                Icon(Icons.Default.Share, contentDescription = "Partilhar", tint = AccentGreen)
                                            }
                                        }
                                    }
                                    // Invite metadata
                                    if (farm.inviteCode != null) {
                                        val expiresText = farm.inviteExpiresAt?.let { iso ->
                                            try {
                                                val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                                                    .withZone(ZoneId.systemDefault())
                                                "Expira: ${formatter.format(Instant.parse(iso))}"
                                            } catch (_: Exception) { "Expira em 7 dias" }
                                        } ?: ""
                                        val usesText = farm.inviteUsesRemaining?.let { "• $it uso(s) restante(s)" } ?: ""
                                        Text("$expiresText $usesText", color = TextSecondary, fontSize = 11.sp)
                                    } else {
                                        Text("Gera um novo código na gestão da família.", color = TextSecondary, fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        // ── Members List ──────────────────────────────
                        val sortedMembers = remember(farm.members, state.currentUserId) {
                            viewModel.sortedMembers(farm.members, state.currentUserId)
                        }
                        Text("Membros (${sortedMembers.size})", color = TextPrimary, fontWeight = FontWeight.Bold)
                        LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(sortedMembers, key = { it.resolvedUserId }) { member ->
                                val isMe = member.resolvedUserId == state.currentUserId
                                // Determine swipe actions based on permissions and target
                                val canKickThis = farm.canKick && !isMe && !member.isCreator &&
                                    !(member.isMasterAdmin && !farm.myTags.isMasterAdmin) // admin can't kick master
                                
                                val promoteAction = when {
                                    !farm.canPromote || isMe -> null
                                    farm.canPromoteMaster && member.isAdmin && !member.isMasterAdmin -> "promote_master_admin"
                                    farm.canPromoteMaster && member.isMasterAdmin -> "demote_master_admin"
                                    farm.canPromote && !member.isAdmin -> "promote_admin"
                                    farm.canPromote && member.isAdmin && !member.isMasterAdmin -> "demote_admin"
                                    else -> null
                                }
                                FamilyMemberListCard(
                                    member = member,
                                    isCurrentUser = isMe,
                                    canSwipeToKick = canKickThis,
                                    canSwipeToPromote = promoteAction != null,
                                    onSwipeKick = { viewModel.requestMemberAction(farm.farmId, member, "kick") },
                                    onSwipePromote = { promoteAction?.let { viewModel.requestMemberAction(farm.farmId, member, it) } },
                                )
                            }
                        }
                    }
                    // Toggle add section
                    if (!state.showAddSection) {
                        TextButton(onClick = { viewModel.toggleAddSection() }) {
                            Icon(Icons.Default.Add, contentDescription = null, tint = AccentGreen)
                            Spacer(modifier = Modifier.padding(4.dp))
                            Text("Adicionar / Juntar a outra família", color = AccentGreen)
                        }
                    }
                }
                // ── Create / Join Section ─────────────────────────
                if (state.showAddSection) {
                    if (state.farms.isNotEmpty()) {
                        TextButton(onClick = { viewModel.toggleAddSection() }) {
                            Text("Cancelar", color = TextSecondary)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = CardDark), modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Criar Nova Família", color = TextPrimary, fontWeight = FontWeight.Bold)
                            if (state.isAnonymous) {
                                Text("Para criar uma família precisas de login (Google). Com acesso anónimo, só podes juntar-te a famílias existentes.",
                                    color = TextSecondary, fontSize = 12.sp)
                            } else {
                                OutlinedTextField(
                                    value = newFarmNameInput,
                                    onValueChange = { newFarmNameInput = it },
                                    placeholder = { Text("Nome da Família (Ex: Família Silva)") },
                                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark,
                                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                                    )
                                )
                            }
                            Button(
                                onClick = { viewModel.createFarm(newFarmNameInput.trim()); newFarmNameInput = "" },
                                enabled = !state.isAnonymous && !state.isActionLoading && newFarmNameInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = AccentGreen),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (state.isActionLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                                else { Icon(Icons.Default.Add, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Criar Família") }
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Juntar por Código", color = TextPrimary, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = inviteCodeInput,
                                    onValueChange = { inviteCodeInput = it.uppercase() },
                                    placeholder = { Text("Ex: AB2X9PKL") },
                                    modifier = Modifier.weight(1f), singleLine = true,
                                    colors = OutlinedTextFieldDefaults.colors(
                                        unfocusedContainerColor = SurfaceDark, focusedContainerColor = SurfaceDark,
                                        unfocusedTextColor = TextPrimary, focusedTextColor = TextPrimary
                                    )
                                )
                                Button(
                                    onClick = { viewModel.joinFarm(inviteCodeInput); inviteCodeInput = "" },
                                    enabled = inviteCodeInput.isNotBlank() && !state.isActionLoading,
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGreen)
                                ) {
                                    if (state.isActionLoading) CircularProgressIndicator(modifier = Modifier.height(20.dp), color = Color.White)
                                    else { Icon(Icons.Default.GroupAdd, contentDescription = null); Spacer(Modifier.padding(4.dp)); Text("Juntar") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
