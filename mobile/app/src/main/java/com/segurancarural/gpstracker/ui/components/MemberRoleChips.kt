package com.segurancarural.gpstracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.data.repository.FarmMemberDto

private val GoldCreator = Color(0xFFFFD700)
private val PurpleMaster = Color(0xFF9333EA)
private val BlueAdmin = Color(0xFF3B82F6)
private val GreenMember = Color(0xFF22C55E)
private val SlateGuest = Color(0xFF64748B)

@Composable
fun MemberRoleChips(member: FarmMemberDto, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (member.isCreator) {
            RoleChip(text = "Criador", color = GoldCreator)
        }
        when {
            member.isMasterAdmin -> RoleChip(text = "Master Admin", color = PurpleMaster)
            member.isAdmin -> RoleChip(text = "Admin", color = BlueAdmin)
            else -> RoleChip(text = "Membro", color = GreenMember)
        }
        if (!member.isAuthenticated) {
            RoleChip(text = "👤 Convidado", color = SlateGuest)
        }
    }
}

@Composable
private fun RoleChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
