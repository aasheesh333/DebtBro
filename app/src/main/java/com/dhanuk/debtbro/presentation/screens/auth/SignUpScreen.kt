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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
fun SignUpScreen(
    onAuthComplete: () -> Unit,
    onNavigateToSignIn: () -> Unit,
    onSkip: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val viewModel: SignUpViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val showVerifyAlert by viewModel.showVerifyAlert.collectAsStateWithLifecycle()
    val showGraceReLoginAlert by viewModel.showGraceReLoginAlert.collectAsStateWithLifecycle()
    val extra = LocalExtraColors.current

    LaunchedEffect(signedIn) {
        if (signedIn && !showVerifyAlert && !showGraceReLoginAlert) onAuthComplete()
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AuthHero(
                title = LocalizedString.get("create_account"),
                subtitle = "Sign up to DebtPayoff Pro"
            )

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                OutlinedButton(
                    onClick = { if (activity != null) viewModel.signUpWithGoogle(activity) },
                    enabled = !state.isBusy && activity != null,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = UITokens.ShapeLarge
                ) {
                    GoogleGlyph()
                    Spacer(Modifier.size(10.dp))
                    Text(
                        LocalizedString.get("sign_up_with_google"),
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
                    value = state.name,
                    onValueChange = { viewModel.setName(it) },
                    label = { Text(LocalizedString.get("display_name")) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    isError = state.name.isNotEmpty() && !isNameValid(state.name),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.email,
                    onValueChange = { viewModel.setEmail(it) },
                    label = { Text(LocalizedString.get("email")) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.password,
                    onValueChange = { viewModel.setPassword(it) },
                    label = { Text(LocalizedString.get("password_6_chars_min")) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                if (state.password.isNotEmpty()) {
                    PasswordStrengthBar(state.passwordStrength)
                }

                OutlinedTextField(
                    value = state.confirmPassword,
                    onValueChange = { viewModel.setConfirmPassword(it) },
                    label = { Text(LocalizedString.get("confirm_password")) },
                    singleLine = true,
                    enabled = !state.isBusy,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    ),
                    colors = authFieldColors(),
                    modifier = Modifier.fillMaxWidth(),
                    isError = state.confirmPassword.isNotEmpty() && state.password != state.confirmPassword
                )

                state.errorRes?.let { err ->
                    val translated = when (err) {
                        "name_required" -> LocalizedString.get("name_required")
                        "email_required" -> LocalizedString.get("email_required")
                        "password_6_chars_min" -> LocalizedString.get("password_6_chars_min")
                        "passwords_dont_match" -> LocalizedString.get("passwords_dont_match")
                        else -> err
                    }
                    Text(
                        translated,
                        color = MaterialTheme.colorScheme.error,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Button(
                    onClick = { viewModel.submit() },
                    enabled = !state.isBusy &&
                        isNameValid(state.name) &&
                        state.email.isNotBlank() &&
                        state.password.length >= MIN_PASSWORD_LENGTH &&
                        state.password == state.confirmPassword,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                    shape = UITokens.ShapeLarge,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    )
                ) {
                    if (state.isBusy) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(LocalizedString.get("create_account"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    } else {
                        Text(LocalizedString.get("create_account"), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        LocalizedString.get("already_have_account") + " ",
                        color = extra.subtitleGray,
                        fontSize = 13.sp
                    )
                    TextButton(onClick = onNavigateToSignIn) {
                        Text(LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary)
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

    if (showVerifyAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissVerifyAlert() },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Please verify your email", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Text(
                    "Please verify your email before adding debts or splits. Check your inbox (and spam folder).",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissVerifyAlert() }) {
                    Text("Got it", color = MaterialTheme.colorScheme.primary)
                }
            }
        )
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
