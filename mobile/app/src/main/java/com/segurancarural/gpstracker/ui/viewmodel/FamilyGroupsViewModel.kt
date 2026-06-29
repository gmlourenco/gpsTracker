package com.segurancarural.gpstracker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.segurancarural.gpstracker.data.repository.FarmDto
import com.segurancarural.gpstracker.data.repository.FarmMemberDto
import com.segurancarural.gpstracker.data.repository.FarmRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FamilyGroupsUiState(
    val isLoading: Boolean = false,
    val isActionLoading: Boolean = false,
    val isAnonymous: Boolean = true,
    val currentUserId: String? = null,
    val farms: List<FarmDto> = emptyList(),
    val selectedFarm: FarmDto? = null,
    val errorMessage: String? = null,
    val showAddSection: Boolean = false,
    // Confirmation dialog
    val pendingAction: PendingMemberAction? = null,
)

data class PendingMemberAction(
    val farmId: String,
    val targetUserId: String,
    val targetDisplayName: String,
    val action: String, // "kick", "promote_admin", "demote_admin", "promote_master_admin", "demote_master_admin"
) {
    val confirmationTitle: String get() = when (action) {
        "kick" -> "Expulsar Membro"
        "promote_admin" -> "Promover a Admin"
        "demote_admin" -> "Rebaixar de Admin"
        "promote_master_admin" -> "Promover a Master Admin"
        "demote_master_admin" -> "Rebaixar de Master Admin"
        else -> "Confirmar"
    }
    val confirmationMessage: String get() = when (action) {
        "kick" -> "Tens a certeza que queres expulsar \"$targetDisplayName\" desta família?"
        "promote_admin" -> "Promover \"$targetDisplayName\" a Admin? Poderá convidar e expulsar membros."
        "demote_admin" -> "Rebaixar \"$targetDisplayName\" de Admin a Membro?"
        "promote_master_admin" -> "Promover \"$targetDisplayName\" a Master Admin? Terá quase todas as permissões."
        "demote_master_admin" -> "Rebaixar \"$targetDisplayName\" de Master Admin a Admin?"
        else -> "Confirmar ação?"
    }
}

class FamilyGroupsViewModel(application: Application) : AndroidViewModel(application) {
    private val farmRepository = FarmRepository()

    private val _uiState = MutableStateFlow(FamilyGroupsUiState())
    val uiState: StateFlow<FamilyGroupsUiState> = _uiState.asStateFlow()

    init { loadFarms() }

    fun loadFarms() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            farmRepository.getFarmDetails().fold(
                onSuccess = { data ->
                    val farms = data.farms
                    val currentSelected = _uiState.value.selectedFarm
                    val selected = when {
                        farms.isEmpty() -> null
                        currentSelected != null -> farms.find { it.farmId == currentSelected.farmId } ?: farms.first()
                        else -> farms.first()
                    }
                    _uiState.update { it.copy(
                        isLoading = false,
                        isAnonymous = data.isAnonymous,
                        currentUserId = data.currentUserId,
                        farms = farms,
                        selectedFarm = selected,
                        showAddSection = farms.isEmpty(),
                    ) }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun selectFarm(farm: FarmDto) {
        if (_uiState.value.selectedFarm?.farmId == farm.farmId) return
        _uiState.update { it.copy(selectedFarm = farm) }
        viewModelScope.launch {
            farmRepository.syncCurrentDeviceToFarm(farm.farmId)
        }
    }

    fun createFarm(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            farmRepository.createFarm(name).fold(
                onSuccess = { res ->
                    res.farmId?.let { farmRepository.syncCurrentDeviceToFarm(it) }
                    loadFarms()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun joinFarm(code: String) {
        if (code.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true) }
            farmRepository.joinFarm(code).fold(
                onSuccess = { res ->
                    res.farmId?.let { farmRepository.syncCurrentDeviceToFarm(it) }
                    loadFarms()
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun toggleAddSection() {
        _uiState.update { it.copy(showAddSection = !it.showAddSection) }
    }

    // ── Member management (swipe actions) ─────────────────────────

    fun requestMemberAction(farmId: String, member: FarmMemberDto, action: String) {
        _uiState.update { it.copy(
            pendingAction = PendingMemberAction(
                farmId = farmId,
                targetUserId = member.resolvedUserId,
                targetDisplayName = member.displayName ?: "Membro Anónimo",
                action = action,
            )
        ) }
    }

    fun confirmPendingAction() {
        val pending = _uiState.value.pendingAction ?: return
        _uiState.update { it.copy(pendingAction = null, isActionLoading = true) }
        viewModelScope.launch {
            farmRepository.manageMember(pending.farmId, pending.targetUserId, pending.action).fold(
                onSuccess = { loadFarms() },
                onFailure = { e ->
                    _uiState.update { it.copy(isActionLoading = false, errorMessage = e.message) }
                }
            )
        }
    }

    fun dismissPendingAction() {
        _uiState.update { it.copy(pendingAction = null) }
    }

    /** Sort members: current user first → creators → master admins → admins → members */
    fun sortedMembers(members: List<FarmMemberDto>, currentUserId: String?): List<FarmMemberDto> {
        return members.sortedWith(
            compareByDescending<FarmMemberDto> { it.resolvedUserId == currentUserId }
                .thenByDescending { it.isCreator }
                .thenByDescending { it.isMasterAdmin }
                .thenByDescending { it.isAdmin }
                .thenBy { it.displayName ?: "zzz" }
        )
    }
}
