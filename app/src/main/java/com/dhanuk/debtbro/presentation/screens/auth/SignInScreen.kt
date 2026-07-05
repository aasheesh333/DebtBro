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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
fun SignInScreen(
    onAuthComplete: () -> Unit,
    onNavigateToSignUp: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: SignInViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val showGraceReLoginAlert by viewModel.showGraceReLoginAlert.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    LaunchedEffect(signedIn) {
        if (signedIn && !showGraceReLoginAlert) onAuthComplete()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AuthHero(
                title = LocalizedString.get("welcome_back"),
                subtitle = "Pick up where you left off"
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = { if (activity != null) viewModel.signInWithGoogle(activity) },
                    enabled = !state.isBusy && activity != null,
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

                if (activity == null) {
                    Text(
                        LocalizedString.get("google_signin_unavailable"),
                        color = extra.subtitleGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
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

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.setEmail(it) },
                    label = { Text(LocalizedString.get("email")) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = if (state.mode == SignInMode.FORGOT_PASSWORD) ImeAction.Done else ImeAction.Next
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.mode != SignInMode.FORGOT_PASSWORD) {
                    OutlinedTextField(
                        value = state.password,
                        onValueChange = { viewModel.setPassword(it) },
                        label = { Text(LocalizedString.get("password_6_chars_min")) },
                        singleLine = true,
                        enabled = !state.isBusy,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = ImeAction.Done
                        ),
                        colors = authFieldColors(),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { viewModel.setMode(SignInMode.FORGOT_PASSWORD) }) {
                            Text(LocalizedString.get("forgot_password"), color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(Modifier.size(0.dp))
                    }
                }

                state.errorRes?.let { err ->
                    val translated = when (err) {
                        "email_required" -> LocalizedString.get("email_required")
                        "password_6_chars_min" -> LocalizedString.get("password_6_chars_min")
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
                    onClick = { viewModel.submit() },
                    enabled = !state.isBusy,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = UITokens.ShapeLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    when (state.mode) {
                        SignInMode.SIGN_IN -> {
                            if (state.isBusy) {
                                CircularProgressIndicator(
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.size(10.dp))
                            }
                            Text(LocalizedString.get("sign_in"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        SignInMode.FORGOT_PASSWORD -> {
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

                if (state.mode == SignInMode.FORGOT_PASSWORD) {
                    val attemptsRemaining by produceState(initialValue = SignInViewModel.MAX_DAILY_RESETS) {
                        value = SignInViewModel.MAX_DAILY_RESETS - viewModel.dailyAttemptsRemaining()
                    }
                    val used = SignInViewModel.MAX_DAILY_RESETS - attemptsRemaining
                    Text(
                        LocalizedString.get("attempts_remaining")
                            .replace("{count}", used.toString()),
                        color = extra.subtitleGray,
                        fontSize = 12.sp,
                        textAlign = TextAlign.End,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        LocalizedString.get("dont_have_account") + " ",
                        color = extra.subtitleGray,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onNavigateToSignUp) {
                        Text(LocalizedString.get("sign_up"), color = MaterialTheme.colorScheme.primary)
                    }
                }

                if (state.mode == SignInMode.FORGOT_PASSWORD) {
                    TextButton(
                        onClick = { viewModel.setMode(SignInMode.SIGN_IN) },
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

                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth(), enabled = !state.isBusy) {
                    Text(LocalizedString.get("skip_for_now"), color = extra.subtitleGray)
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }

    if (showGraceReLoginAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.signOutFromGraceAlert() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Account deletion in progress", color = MaterialTheme.colorScheme.error) },
            text = {
                Text(
                    "You're within the 24-hour account deletion grace window. Do you want to come back?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.cancelDeletionViaGraceAlert() }) {
                    Text("Log in and reactivate", color = MaterialTheme.colorScheme.primary)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.signOutFromGraceAlert() }) {
                    Text("Cancel", color = extra.subtitleGray)
                }
            }
        )
    }
}
