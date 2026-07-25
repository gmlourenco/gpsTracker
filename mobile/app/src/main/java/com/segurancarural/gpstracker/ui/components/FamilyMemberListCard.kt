package com.segurancarural.gpstracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.segurancarural.gpstracker.data.repository.FarmMemberDto

private val CardDark = Color(0xFF16213E)
private val TextPrimary = Color(0xFFF1F5F9)
private val AccentGreen = Color(0xFF16A34A)
private val SwipeRed = Color(0xFFDC2626)
private val SwipeBlue = Color(0xFF3B82F6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FamilyMemberListCard(
    member: FarmMemberDto,
    isCurrentUser: Boolean,
    canSwipeToKick: Boolean,
    canSwipeToPromote: Boolean,
    onSwipeKick: () -> Unit,
    onSwipePromote: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Swipe is disabled for current user and when no permissions
    val enableSwipe = !isCurrentUser && (canSwipeToKick || canSwipeToPromote)

    if (enableSwipe) {
        val dismissState = rememberSwipeToDismissBoxState(
            confirmValueChange = { value ->
                when (value) {
                    SwipeToDismissBoxValue.EndToStart -> { // Swipe left → kick
                        if (canSwipeToKick) onSwipeKick()
                        false // Don't actually dismiss, just trigger callback
                    }
                    SwipeToDismissBoxValue.StartToEnd -> { // Swipe right → promote
                        if (canSwipeToPromote) onSwipePromote()
                        false
                    }
                    SwipeToDismissBoxValue.Settled -> false
                }
            }
        )

        SwipeToDismissBox(
            state = dismissState,
            enableDismissFromEndToStart = canSwipeToKick,
            enableDismissFromStartToEnd = canSwipeToPromote,
            backgroundContent = {
                val direction = dismissState.dismissDirection
                val bgColor by animateColorAsState(
                    when (direction) {
                        SwipeToDismissBoxValue.EndToStart -> SwipeRed
                        SwipeToDismissBoxValue.StartToEnd -> SwipeBlue
                        else -> Color.Transparent
                    },
                    label = "swipe_bg"
                )
                val icon = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Icons.Default.Delete
                    SwipeToDismissBoxValue.StartToEnd -> Icons.Default.ArrowUpward
                    else -> null
                }
                val label = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> "Expulsar"
                    SwipeToDismissBoxValue.StartToEnd -> if (member.isAdmin) "Rebaixar" else "Promover"
                    else -> ""
                }
                val alignment = when (direction) {
                    SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                    SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                    else -> Alignment.Center
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(16.dp))
                        .background(bgColor)
                        .padding(horizontal = 20.dp),
                    contentAlignment = alignment
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        icon?.let { Icon(it, contentDescription = label, tint = Color.White) }
                        Text(label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            },
            modifier = modifier
        ) {
            MemberCardContent(member = member, isCurrentUser = false)
        }
    } else {
        MemberCardContent(member = member, isCurrentUser = isCurrentUser, modifier = modifier)
    }
}

@Composable
private fun MemberCardContent(
    member: FarmMemberDto,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val borderMod = if (isCurrentUser) {
        Modifier.border(1.5.dp, AccentGreen, RoundedCornerShape(16.dp))
    } else Modifier

    Card(
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrentUser) CardDark.copy(alpha = 0.85f) else CardDark
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth().then(borderMod)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Avatar
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AccentGreen),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = member.displayName?.take(1)?.uppercase() ?: "?",
                    color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = member.displayName ?: "Membro Anónimo",
                        color = TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isCurrentUser) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentGreen)
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                        ) {
                            Text("TU", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                MemberRoleChips(member = member)
            }
        }
    }
}
