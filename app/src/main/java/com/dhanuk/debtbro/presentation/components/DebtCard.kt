package com.dhanuk.debtbro.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.presentation.theme.DangerRed
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.WarningAmber
import com.dhanuk.debtbro.util.daysUntil
import com.dhanuk.debtbro.util.formatCurrency
import com.dhanuk.debtbro.util.toReadableDate

@Composable
fun DebtCard(debt: DebtEntity, isSignedIn: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val remaining = (debt.amount - debt.amountPaid).coerceAtLeast(0.0)
    Card(modifier = modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = UITokens.ShapeSmall) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceSmall)) {
            Box(Modifier.size(UITokens.AvatarLarge).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) { Text(debt.personEmoji) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(UITokens.SpaceTiny)) {
                Text(debt.personName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(debt.description.ifBlank { if (debt.type == "THEY_OWE_ME") "Money lent" else "Money borrowed" }, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(debt.status)
                    debt.dueDate?.let { DuePill(it) }
                    if (isSignedIn) Box(Modifier.size(UITokens.SpaceXS).background(if (debt.isSynced) MaterialTheme.colorScheme.primary else DangerRed, CircleShape))
                }
            }
            Text(formatCurrency(remaining, debt.currency), color = if (debt.type == "THEY_OWE_ME") MaterialTheme.colorScheme.primary else DangerRed, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun StatusPill(status: String) {
    Text(status.lowercase().replaceFirstChar { it.uppercase() }, Modifier.background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), style = MaterialTheme.typography.labelSmall)
}

@Composable
private fun DuePill(dueDate: Long) {
    val days = dueDate.daysUntil()
    val extra = LocalExtraColors.current
    val color = when {
        days < 0 -> DangerRed
        days <= 2 -> WarningAmber
        else -> extra.subtitleGray
    }
    Text(if (days < 0) "Overdue ${-days}d" else dueDate.toReadableDate(), Modifier.background(color.copy(alpha = 0.16f), RoundedCornerShape(50)).padding(horizontal = 8.dp, vertical = 3.dp), color = color, style = MaterialTheme.typography.labelSmall)
}
