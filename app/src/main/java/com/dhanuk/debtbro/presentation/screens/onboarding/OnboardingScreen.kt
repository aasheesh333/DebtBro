package com.dhanuk.debtbro.presentation.screens.onboarding

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dhanuk.debtbro.presentation.components.LanguageSelectorGrid
import com.dhanuk.debtbro.presentation.theme.LocalExtraColors
import com.dhanuk.debtbro.presentation.theme.UITokens
import com.dhanuk.debtbro.util.LocalizedString
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onOnboardingComplete: () -> Unit) {
    val viewModel: OnboardingViewModel = hiltViewModel()
    val pagerState = rememberPagerState(pageCount = { 6 })
    val scope = rememberCoroutineScope()
    val hapticFeedback = LocalHapticFeedback.current
    val extra = LocalExtraColors.current
    val name by viewModel.userName.collectAsStateWithLifecycle()
    val selectedLanguage by viewModel.selectedLanguage.collectAsStateWithLifecycle()
    val showEmailForm by viewModel.showEmailForm.collectAsStateWithLifecycle()
    val isSignInMode by viewModel.isSignInMode.collectAsStateWithLifecycle()
    val showForgotPasswordDialog by viewModel.showForgotPasswordDialog.collectAsStateWithLifecycle()
    val forgotPasswordError by viewModel.forgotPasswordError.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var forgotPasswordEmail by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = context as? android.app.Activity

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 32.dp, end = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                if (pagerState.currentPage in 1..4) {
                    TextButton(onClick = { scope.launch { pagerState.animateScrollToPage(5) } }) {
                        Text(LocalizedString.get("skip"), color = extra.subtitleGray)
                    }
                } else {
                    Spacer(modifier = Modifier.height(48.dp))
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                when (page) {
                    0 -> Page1Welcome(selectedLanguage) { viewModel.setLanguage(it.code) }
                    1 -> Page2Track()
                    2 -> Page3Roasts()
                    3 -> Page4Sync()
                    4 -> Page5aName(
                        name = name,
                        onNameChange = { viewModel.onNameChange(it) },
                        onSkip = { viewModel.completeOnboarding(onOnboardingComplete) }
                    )
                    5 -> Page6Auth(
                        name = name,
                        showEmailForm = showEmailForm,
                        isSignInMode = isSignInMode,
                        onToggleEmailForm = { viewModel.toggleEmailForm() },
                        email = email,
                        password = password,
                        confirmPassword = confirmPassword,
                        onEmailChange = { email = it },
                        onPasswordChange = { password = it },
                        onConfirmPasswordChange = { confirmPassword = it },
                        authBusy = authBusy,
                        authError = authError,
                        onGoogleSignIn = {
                            if (activity != null) {
                                authBusy = true
                                viewModel.dismissAuthError()
                                viewModel.signInWithGoogle(activity) { success ->
                                    authBusy = false
                                    if (success) viewModel.completeOnboarding(onOnboardingComplete)
                                }
                            }
                        },
                        onEmailSubmit = {
                            authBusy = true
                            viewModel.dismissAuthError()
                            if (isSignInMode) {
                                viewModel.signInEmailPassword(email, password) { ok, err ->
                                    authBusy = false
                                    if (ok) viewModel.completeOnboarding(onOnboardingComplete)
                                }
                            } else {
                                viewModel.signUpEmailPassword(email, password, name) { ok, err ->
                                    authBusy = false
                                    if (ok) viewModel.completeOnboarding(onOnboardingComplete)
                                }
                            }
                        },
                        onSkip = { viewModel.completeOnboarding(onOnboardingComplete) },
                        onForgotPassword = { viewModel.showForgotPasswordDialog() },
                        forgotPasswordDialogState = ForgotPasswordDialogState(
                            visible = showForgotPasswordDialog,
                            email = forgotPasswordEmail,
                            onEmailChange = { forgotPasswordEmail = it },
                            onSend = { viewModel.sendPasswordResetEmail(forgotPasswordEmail) },
                            onDismiss = { viewModel.dismissForgotPasswordDialog() },
                            error = forgotPasswordError
                        )
                    )
                }
            }

            Column(
                modifier = Modifier.fillMaxWidth().padding(UITokens.SheetContentPadding),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(UITokens.SpaceXS),
                    modifier = Modifier.padding(bottom = UITokens.SheetBottomPadding)
                ) {
                    repeat(6) { index ->
                        val isSelected = pagerState.currentPage == index
                        val width by animateDpAsState(targetValue = if (isSelected) 24.dp else 8.dp, label = "dotWidth")
                        Box(
                            Modifier.height(8.dp).width(width).clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                        )
                    }
                }

                val buttonEnabled = when (pagerState.currentPage) {
                    4 -> name.isNotBlank()
                    else -> true
                }
                Button(
                    onClick = {
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                        if (pagerState.currentPage < 5) {
                            scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                        } else {
                            viewModel.completeOnboarding(onOnboardingComplete)
                        }
                    },
                    enabled = buttonEnabled,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, disabledContainerColor = MaterialTheme.colorScheme.outline),
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text(
                        if (pagerState.currentPage < 5) LocalizedString.get("next_arrow") else LocalizedString.get("lets_go"),
                        color = if (buttonEnabled) MaterialTheme.colorScheme.onPrimary else extra.subtitleGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun Page1Welcome(selectedLanguage: String, onLanguageSelected: (com.dhanuk.debtbro.presentation.components.AppLanguage) -> Unit) {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(32.dp))
        Text("\uD83D\uDCB8", fontSize = 80.sp)
        Text("DebtPayoff Pro", color = MaterialTheme.colorScheme.primary, fontSize = 48.sp, fontWeight = FontWeight.ExtraBold)
        Text(LocalizedString.get("app_tagline"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Text(LocalizedString.get("choose_language"), color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSubhead, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = UITokens.CardInnerPadding))
        LanguageSelectorGrid(selectedCode = selectedLanguage, onLanguageSelected = onLanguageSelected)
    }
}

@Composable
fun Page2Track() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDCB8", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(LocalizedString.get("track_who_owes"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(LocalizedString.get("track_desc"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill(LocalizedString.get("pill_they_owe"))
            FeaturePill(LocalizedString.get("pill_i_owe"))
            FeaturePill(LocalizedString.get("pill_analytics"))
        }
    }
}

@Composable
fun Page3Roasts() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83E\uDD16", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(LocalizedString.get("ai_roasts_title"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = UITokens.ShapeLarge
        ) {
            Text(
                "Hey bro, still waiting on that \u20B9500 for pizza. Unless you're paying me in exposure?",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(16.dp),
                fontSize = 15.sp
            )
        }
        Spacer(Modifier.height(24.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FeaturePill(LocalizedString.get("mild"))
            FeaturePill(LocalizedString.get("medium"))
            FeaturePill(LocalizedString.get("savage"))
        }
    }
}

@Composable
fun Page4Sync() {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\u2601\uFE0F", fontSize = 96.sp)
        Spacer(Modifier.height(24.dp))
        Text(LocalizedString.get("never_lose_data"), color = MaterialTheme.colorScheme.onSurface, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Text(LocalizedString.get("sync_settings_desc"), color = extra.subtitleGray, fontSize = 16.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(LocalizedString.get("add_debt"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text(LocalizedString.get("auto_sync"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("\u2192", color = extra.subtitleGray)
            Text(LocalizedString.get("access_anywhere"), color = MaterialTheme.colorScheme.primary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun Page5aName(
    name: String,
    onNameChange: (String) -> Unit,
    onSkip: () -> Unit
) {
    val extra = LocalExtraColors.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDC4B", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(LocalizedString.get("what_call_you"), color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(LocalizedString.get("name_page_subtitle"), color = extra.subtitleGray, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) onNameChange(it.trimStart()) },
            label = { Text(LocalizedString.get("your_name_label")) },
            placeholder = { Text(LocalizedString.get("name_example")) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, capitalization = KeyboardCapitalization.Words),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                unfocusedTextColor = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = { Text("${name.length}/30", color = extra.subtitleGray, fontSize = 11.sp) }
        )
        Spacer(Modifier.height(8.dp))
        Text(LocalizedString.get("name_change_hint"), color = extra.subtitleGray, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun Page6Auth(
    name: String,
    showEmailForm: Boolean,
    isSignInMode: Boolean,
    onToggleEmailForm: () -> Unit,
    email: String,
    password: String,
    confirmPassword: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onConfirmPasswordChange: (String) -> Unit,
    authBusy: Boolean,
    authError: String?,
    onGoogleSignIn: () -> Unit,
    onEmailSubmit: () -> Unit,
    onSkip: () -> Unit,
    onForgotPassword: () -> Unit,
    forgotPasswordDialogState: ForgotPasswordDialogState
) {
    val extra = LocalExtraColors.current
    var passwordVisible by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().padding(horizontal = 24.dp).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("\uD83D\uDD12", fontSize = 72.sp)
        Spacer(Modifier.height(16.dp))
        Text(
            if (isSignInMode) LocalizedString.get("auth_page_signin_heading") else LocalizedString.get("auth_page_signup_heading"),
            color = MaterialTheme.colorScheme.onSurface, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        Text(LocalizedString.get("auth_page_subtitle"), color = extra.subtitleGray, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onGoogleSignIn,
            enabled = !authBusy,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) {
            Text(LocalizedString.get("sign_in_google"), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
            Text(LocalizedString.get("or_continue_with"), color = extra.subtitleGray, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 8.dp))
            Box(Modifier.weight(1f).height(1.dp).background(MaterialTheme.colorScheme.outline))
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onToggleEmailForm) {
            Text(
                if (!showEmailForm || isSignInMode) LocalizedString.get("dont_have_account_signup") else LocalizedString.get("already_have_account_signin"),
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (showEmailForm) {
            OutlinedTextField(
                value = email,
                onValueChange = onEmailChange,
                label = { Text(LocalizedString.get("email")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = { Text(LocalizedString.get("password")) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            if (passwordVisible) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            if (!isSignInMode) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = onConfirmPasswordChange,
                    label = { Text(LocalizedString.get("confirm_password")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    )
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onEmailSubmit,
                enabled = !authBusy &&
                    email.isNotBlank() &&
                    password.length >= 6 &&
                    (isSignInMode || (confirmPassword == password && confirmPassword.length >= 6)),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (isSignInMode) LocalizedString.get("sign_in") else LocalizedString.get("sign_up"),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (isSignInMode) {
                    TextButton(onClick = onForgotPassword) {
                        Text(LocalizedString.get("forgot_password"), color = extra.subtitleGray)
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
            }
        }
        authError?.let { err ->
            Spacer(Modifier.height(8.dp))
            Text(err, color = MaterialTheme.colorScheme.error, fontSize = UITokens.FontCaption, textAlign = TextAlign.Center)
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onSkip) {
            Text(LocalizedString.get("skip_for_now"), color = extra.subtitleGray)
        }
        Text(LocalizedString.get("account_setup_skip_note"), color = extra.subtitleGray, fontSize = UITokens.FontCaption, textAlign = TextAlign.Center)
    }
    if (forgotPasswordDialogState.visible) {
        ForgotPasswordDialog(state = forgotPasswordDialogState)
    }
}

private data class ForgotPasswordDialogState(
    val visible: Boolean,
    val email: String,
    val onEmailChange: (String) -> Unit,
    val onSend: () -> Unit,
    val onDismiss: () -> Unit,
    val error: String?
)

@Composable
private fun ForgotPasswordDialog(state: ForgotPasswordDialogState) {
    AlertDialog(
        onDismissRequest = state.onDismiss,
        title = { Text(LocalizedString.get("forgot_password")) },
        text = {
            Column {
                OutlinedTextField(
                    value = state.email,
                    onValueChange = state.onEmailChange,
                    label = { Text(LocalizedString.get("email")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth()
                )
                state.error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, color = MaterialTheme.colorScheme.error, fontSize = UITokens.FontCaption)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = state.onSend) {
                Text(LocalizedString.get("send_reset_email"))
            }
        },
        dismissButton = {
            TextButton(onClick = state.onDismiss) {
                Text(LocalizedString.get("cancel"))
            }
        }
    )
}

@Composable
fun FeaturePill(text: String) {
    val extra = LocalExtraColors.current
    Box(
        modifier = Modifier
            .clip(UITokens.ShapeLarge)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurface, fontSize = UITokens.FontSmall, fontWeight = FontWeight.SemiBold)
    }
}
