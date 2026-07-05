package com.dhanuk.debtbro.presentation.screens.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString

enum class PasswordStrength { WEAK, MEDIUM, STRONG }

const val MIN_NAME_LENGTH = 3
const val MAX_NAME_LENGTH = 30
const val MIN_PASSWORD_LENGTH = 6

fun isEmailValid(s: String): Boolean =
    s.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()

fun isNameValid(s: String): Boolean =
    s.trim().length in MIN_NAME_LENGTH..MAX_NAME_LENGTH

fun computePasswordStrength(pwd: String): PasswordStrength {
    if (pwd.length < 6) return PasswordStrength.WEAK
    val hasLetter = pwd.any { it.isLetter() }
    val hasDigit = pwd.any { it.isDigit() }
    val hasSymbol = pwd.any { !it.isLetterOrDigit() }
    return when {
        pwd.length >= 10 && hasLetter && hasDigit && hasSymbol -> PasswordStrength.STRONG
        pwd.length >= 8 && ((hasLetter && hasDigit) || hasSymbol) -> PasswordStrength.MEDIUM
        else -> PasswordStrength.WEAK
    }
}

@Composable
fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
fun AuthHero(title: String, subtitle: String = "DebtPayoff Pro") {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                        MaterialTheme.colorScheme.background
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("\uD83D\uDCB8", fontSize = 56.sp)
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun GoogleGlyph() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(Color(0xFFFFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "G",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF4285F4)
        )
    }
}

@Composable
fun PasswordStrengthBar(strength: PasswordStrength) {
    val extra = LocalExtraColors.current
    val strengthData = when (strength) {
        PasswordStrength.WEAK -> Triple(1, 3, MaterialTheme.colorScheme.error)
        PasswordStrength.MEDIUM -> Triple(2, 3, MaterialTheme.colorScheme.tertiary)
        PasswordStrength.STRONG -> Triple(3, 3, MaterialTheme.colorScheme.primary)
    }
    val segments = strengthData.first
    val activeColor = strengthData.third
    Row(
        modifier = Modifier.fillMaxWidth().height(6.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        repeat(3) { i ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(6.dp)
                    .clip(UITokens.ShapeSmall)
                    .background(
                        if (i < segments) activeColor
                        else extra.divider.copy(alpha = 0.6f)
                    )
            )
        }
    }
    val label = when (strength) {
        PasswordStrength.WEAK -> LocalizedString.get("password_strength_weak")
        PasswordStrength.MEDIUM -> LocalizedString.get("password_strength_medium")
        PasswordStrength.STRONG -> LocalizedString.get("password_strength_strong")
    }
    Text(label, color = activeColor, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
}
