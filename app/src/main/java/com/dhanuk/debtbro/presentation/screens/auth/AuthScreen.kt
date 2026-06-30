package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString

@Composable
fun AuthScreen(onAuthComplete: () -> Unit, onSkip: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: AuthViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    LaunchedEffect(signedIn) {
        if (signedIn) onAuthComplete()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AuthHero(
                title = when (state.mode) {
                    AuthMode.SIGN_IN -> LocalizedString.get("welcome_back")
                    AuthMode.SIGN_UP -> LocalizedString.get("create_account")
                    AuthMode.FORGOT_PASSWORD -> LocalizedString.get("forgot_password")
                }
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (state.mode != AuthMode.FORGOT_PASSWORD) {
                    OutlinedButton(
                        onClick = {
                            if (activity != null) viewModel.signInWithGoogle(activity)
                        },
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        shape = UITokens.ShapeLarge
                    ) {
                        GoogleGlyph()
                        Spacer(Modifier.size(10.dp))
                        Text(
                            LocalizedString.get("sign_in_google"),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = extra.divider)
                        Text(
                            LocalizedString.get("or_continue_with"),
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = extra.subtitleGray,
                            fontSize = 12.sp
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = extra.divider)
                    }
                }

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.setEmail(it) },
                    label = { Text("Email") },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (state.mode == AuthMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.mode != AuthMode.FORGOT_PASSWORD) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text(LocalizedString.get("password_6_chars_min")) },
                        singleLine = true,
                        enabled = !state.isBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (state.mode == AuthMode.SIGN_UP) ImeAction.Next else ImeAction.Done
                        ),
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (state.mode == AuthMode.SIGN_UP && state.password.isNotEmpty()) {
                        PasswordStrengthBar(state.passwordStrength)
                    }

                    if (state.mode == AuthMode.SIGN_UP) {
                        OutlinedTextField(
                            value = state.confirmPassword,
                            onValueChange = { viewModel.setConfirmPassword(it) },
                            label = { Text(LocalizedString.get("confirm_password")) },
                            singleLine = true,
                            enabled = !state.isBusy,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                            colors = authFieldColors(),
                            modifier = Modifier.fillMaxWidth(),
                            isError = state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword
                        )
                    }
                }

                state.errorRes?.let { err ->
                    val translated = when (err) {
                        "email_required" -> LocalizedString.get("email_required")
                        "password_6_chars_min" -> LocalizedString.get("password_6_chars_min")
                        "passwords_dont_match" -> LocalizedString.get("passwords_dont_match")
                        "daily_limit_reached" -> LocalizedString.get("daily_limit_reached")
                        else -> err
                    }
                    Text(
                        translated,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                if (state.resetLinkSent) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(UITokens.ShapeLarge)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(14.dp)
                    ) {
                        Text(
                            LocalizedString.get("reset_link_sent"),
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                Button(
                    onClick = { viewModel.submitEmailPassword() },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = UITokens.ShapeLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    when (state.mode) {
                        AuthMode.SIGN_IN -> Text(LocalizedString.get("sign_in"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        AuthMode.SIGN_UP -> Text(LocalizedString.get("create_account"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        AuthMode.FORGOT_PASSWORD -> {
                            val remainingAttempts = viewModel.dailyAttemptsRemaining()
                            val countdown = state.resendCountdownSeconds
                            val label = when {
                                countdown > 0 -> LocalizedString.get("resend_in").replace("{time}", countdown.toString())
                                else -> LocalizedString.get("send_reset_link")
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isBusy) {
                                    CircularProgressIndicator(
                                        color = MaterialTheme.colorScheme.onPrimary,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(Modifier.size(10.dp))
                                }
                                Text(label, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                        }
                    }
                }

                if (state.mode == AuthMode.FORGOT_PASSWORD) {
                    val attemptsRemaining by produceState(initialValue = AuthViewModel.MAX_DAILY_RESETS) {
                        value = AuthViewModel.MAX_DAILY_RESETS - viewModel.dailyAttemptsRemaining()
                    }
                    val used = AuthViewModel.MAX_DAILY_RESETS - attemptsRemaining
                    Text(
                        LocalizedString.get("attempts_remaining")
                            .replace("{count}", used.toString()),
                        color = extra.subtitleGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (state.mode != AuthMode.FORGOT_PASSWORD) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (state.mode == AuthMode.SIGN_IN) {
                            TextButton(onClick = { viewModel.setMode(AuthMode.FORGOT_PASSWORD) }) {
                                Text(LocalizedString.get("forgot_password"), color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            Spacer(Modifier.size(0.dp))
                        }

                        TextButton(onClick = {
                            viewModel.setMode(if (state.mode == AuthMode.SIGN_IN) AuthMode.SIGN_UP else AuthMode.SIGN_IN)
                        }) {
                            Text(
                                if (state.mode == AuthMode.SIGN_IN) LocalizedString.get("dont_have_account") else LocalizedString.get("already_have_account"),
                                color = extra.subtitleGray,
                                fontSize = 13.sp
                            )
                        }
                    }
                } else {
                    TextButton(
                        onClick = { viewModel.setMode(AuthMode.SIGN_IN) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(LocalizedString.get("back_to_sign_in"), color = MaterialTheme.colorScheme.primary)
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), color = extra.divider)

                Text(
                    LocalizedString.get("sign_in_to_sync_desc"),
                    color = extra.subtitleGray,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                TextButton(
                    onClick = onSkip,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.isBusy
                ) {
                    Text(LocalizedString.get("skip_for_now"), color = extra.subtitleGray)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun authFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
)

@Composable
private fun AuthHero(title: String) {
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
                "DebtBro",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun PasswordStrengthBar(strength: PasswordStrength) {
    val extra = LocalExtraColors.current
    val (segments, activeColor) = when (strength) {
        PasswordStrength.WEAK -> Triple(1, 3, MaterialTheme.colorScheme.error)
        PasswordStrength.MEDIUM -> Triple(2, 3, MaterialTheme.colorScheme.tertiary)
        PasswordStrength.STRONG -> Triple(3, 3, MaterialTheme.colorScheme.primary)
    }
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

@Composable
private fun GoogleGlyph() {
    val multicolor = listOf(
        Color(0xFF4285F4),
        Color(0xFFEA4335),
        Color(0xFFFBBC05),
        Color(0xFF34A853)
    )
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
            color = multicolor[0]
        )
    }
}
