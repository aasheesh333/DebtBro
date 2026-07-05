# Auth Screens Split Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Split the combined `AuthScreen.kt` (448-line composable with SIGN_IN/SIGN_UP/FORGOT_PASSWORD modes) into two separate Jetpack Compose screens — `SignUpScreen` + `SignInScreen` — each with its own isolated ViewModel, add a Display name field to Sign Up, show an English-only email-verification alert on sign-up success, and update the stale `GRACE_PERIOD_MS` from 30 minutes to 24 hours to match the live legal pages.

**Architecture:** Approach B (two NavGraph routes × two screens × two ViewModels). Shared composables (`AuthHero`, `GoogleGlyph`, `authFieldColors`, `PasswordStrengthBar`) and validation helpers extracted into `authSharedUi.kt`. `OnboardingScreen.kt` is unchanged — its existing `Page5aName` + `Page6Auth` inline auth flow serves first-time users; the new standalone screens serve returning users (post-sign-out, "auth required" call sites in NavGraph).

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Hilt DI, Firebase Auth, DataStore Preferences, `android.util.Log` for logging.

## Global Constraints

- **Never run local builds** — CI is the only verification path (~6m40s avg). After each batch: commit + push + watch CI green before next batch.
- **No tests** — project has zero test files per `AGENTS.md` ANTI-PATTERNS. Verification = successful CI build (compile + lint).
- **No comments in code** unless the file already has explanatory comments AND the change site is non-obvious.
- **`LocalizedString.get(key)`** returns the key literally if not found — every new key MUST be added to all 25 language maps (`en`, `hi`, `es`, `fr`, `de`, `ja`, `mr`, `pa`, `gu`, `bn`, `pt`, `ar`, `zh`, `ko`, `ru`, `tr`, `it`, `id`, `ta`, `te`, `ur`, `sw`, `nl`, `pl`, `vi`).
- **Existing string keys reused (already present):** `welcome_back`, `create_account`, `sign_in`, `sign_up`, `already_have_account`, `dont_have_account`, `forgot_password`, `email`, `password_6_chars_min`, `confirm_password`, `passwords_dont_match`, `email_required`, `reset_link_sent`, `send_reset_link`, `resend_in`, `attempts_remaining`, `daily_limit_reached`, `back_to_sign_in`, `or_continue_with`, `sign_in_google`, `google_signin_unavailable`, `sign_in_to_sync_desc`, `skip_for_now`, `password_strength_weak`, `password_strength_medium`, `password_strength_strong`, `name_required`, `name_min_length_error`, `your_name_label`.
- **New keys to add (5):** `display_name`, `sign_up_with_google`, `please_verify_email`, `please_verify_email_desc`, `got_it` — added to all 25 language maps.
- **`OnboardingScreen.kt` and `OnboardingViewModel.kt` are UNCHANGED** — no tasks in this plan touch them.
- **`OnboardingScreen` already does auth inline** (`Page5aName` + `Page6Auth`) for first-time users; `AuthScreen` is for returning users only (reached from `navController.navigate(Screen.Auth.route)` in `NavGraph.kt` at lines 279/289/305).
- **`AuthManager.signUpWithEmailPassword`** already sends the email verification internally (line 92) — ViewModel does not call `sendEmailVerification` separately.
- **`AuthManager.updateProfile(displayName, photoUri)`** is the correct method name (line 246) — returns `Result<Unit>`, `.onFailure` is logged inside AuthManager.
- **Dark-first theme:** background `#0D0D0D`, cards `#1E1E1E`/`#222222`, `PrimaryGreen #00E5A0`, `DangerRed #F44336`, `Amber #FFB300`.
- **UITokens.ShapeLarge** for corner radius on buttons and fields (matching existing auth UI).
- **Verify alert text is English-only** (destructive-action UX clarity) — hardcoded, NOT via `LocalizedString.get()`.

---

## File Structure

### Files Created
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/authSharedUi.kt` — extracted composables (`AuthHero`, `GoogleGlyph`, `authFieldColors`, `PasswordStrengthBar`), `PasswordStrength` enum, validation helpers (`isEmailValid`, `isNameValid`, `computePasswordStrength`), constants (`MIN_NAME_LENGTH=3`, `MAX_NAME_LENGTH=30`, `MIN_PASSWORD_LENGTH=6`)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpViewModel.kt` — `@HiltViewModel`, `SignUpUiState`, `signedIn`/`showVerifyAlert`/`showGraceReLoginAlert` flows, `submit()`/`signUpWithGoogle()`/`dismissVerifyAlert()`/grace handlers
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpScreen.kt` — `@Composable fun SignUpScreen(...)`, renders hero + name + email + password + confirm + button + verify alert
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInViewModel.kt` — `@HiltViewModel`, `SignInMode` enum, `SignInUiState`, `signedIn`/`showGraceReLoginAlert` flows, `submit()`/`signInWithGoogle()`/forgot-password flow/grace handlers
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInScreen.kt` — `@Composable fun SignInScreen(...)`, renders hero + Google + email + password + forgot link + button + grace alert (updated 24h text)

### Files Modified
- `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/Screen.kt:14` — replace `object Auth : Screen("auth")` with `object SignUp : Screen("sign_up")` + `object SignIn : Screen("sign_in")`
- `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt:36,292-297,279,289,305` — import swap, replace `Screen.Auth` composable block with `Screen.SignUp` + `Screen.SignIn`, redirect 3 "auth required" call sites to `Screen.SignIn.route`
- `app/src/main/java/com/dhanuk/debtbro/worker/AccountDeletionWorker.kt:73` — `GRACE_PERIOD_MS` 30min → 24h
- `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt` — add 5 new keys × 25 language maps = 125 new entries

### Files Deleted
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt`

### Files NOT Modified (explicit)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt` — UNCHANGED
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt` — UNCHANGED

---

## Task 0: Generate OpenDesign Visual Mockup

**Files:**
- Generate via OpenDesign MCP (no project files modified)

**Interfaces:**
- Consumes: OpenDesign daemon at `http://127.0.0.1:7456` (systemd service `open-design-daemon.service`)
- Produces: HTML mockups for Sign Up + Sign In layouts, used as visual reference for Task 6-7

- [ ] **Step 1: Create OpenDesign project**

Call MCP tool `open-design_create_project`:
```
name: "DebtBro Auth Screens"
```
Save the returned project ID.

- [ ] **Step 2: Kick off design run**

Call MCP tool `open-design_start_run`:
```
project: <id from step 1>
prompt: "Design two mobile auth screens for an Android app called DebtPayoff Pro. Dark-first theme: background #0D0D0D, primary green #00E5A0, danger red #F44336, amber #FFB300. Material 3 design system. Mobile viewport 390px wide.

Screen 1 — Sign Up: vertical column with (top to bottom): hero header 220px tall with vertical gradient primary→background, money bag emoji 💸, title 'Create Account', subtitle 'DebtPayoff Pro'. Below hero: 'Sign up with Google' outlined button (52dp). Divider 'or continue with'. Display name text field. Email text field. Password text field with strength meter (3 segments). Confirm password text field. 'Sign Up' filled button (primary green, disabled state shown greyed). Bottom link 'Already have an account? Log in'.

Screen 2 — Sign In: vertical column with (top to bottom): hero header same gradient/title 'Welcome back'. 'Sign in with Google' outlined button. Divider. Email text field. Password text field. 'Forgot password?' text link right-aligned. 'Sign In' filled button. Bottom link 'Don't have an account? Sign up'. Below that: 'Skip for now' text link.

Generate sign-up.html and sign-in.html as mockups in this project."
```
Save the returned `runId`.

- [ ] **Step 3: Poll run until succeeded**

Call `open-design_get_run(runId)` every 45s. Expected: queued → running → succeeded (typical 5-30 min per spec). On `succeeded`, save `previewUrl` and `agentMessage`.

- [ ] **Step 4: Pull generated HTML**

Call `open-design_get_artifact()` (default project). Save the returned file set as visual reference for Task 6 (SignUp) and Task 7 (SignIn).

- [ ] **Step 5: Visual sanity check**

Call `describe_image_describe_image` on a screenshot of each mockup (capture via `open-design_get_run` `previewUrl` if available). Verify:
- Dark background `#0D0D0D` confirmed
- Primary green `#00E5A0` on buttons and hero gradient confirmed
- 52dp rounded buttons confirmed
- Name field present on Sign Up, absent on Sign In

If anything mismatches the spec, note deviations and proceed — the Compose implementation will follow the spec, not the mockup, when they diverge.

---

## Task 1: Add 5 New String Keys to LocalizedString.kt

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

**Interfaces:**
- Consumes: existing `mapOf("key" to "value", ...)` pattern per language
- Produces: 5 new keys available via `LocalizedString.get("display_name" | "sign_up_with_google" | "please_verify_email" | "please_verify_email_desc" | "got_it")` across all 25 languages

- [ ] **Step 1: Add new keys to English map**

In the English language map (first map in the file, starts around line 90), add 5 new entries right after `"name_min_length_error"` (line 98):

```kotlin
            "display_name" to "Display name",
            "sign_up_with_google" to "Sign up with Google",
            "please_verify_email" to "Please verify your email",
            "please_verify_email_desc" to "Please verify your email before adding debts or splits. Check your inbox (and spam folder).",
            "got_it" to "Got it",
```

- [ ] **Step 2: Add the same 5 keys + translated values to the other 24 language maps**

For each of `hi`, `es`, `fr`, `de`, `ja`, `mr`, `pa`, `gu`, `bn`, `pt`, `ar`, `zh`, `ko`, `ru`, `tr`, `it`, `id`, `ta`, `te`, `ur`, `sw`, `nl`, `pl`, `vi` — add these entries (translated; keep `please_verify_email` / `please_verify_email_desc` / `got_it` in English since the verify alert is English-only by design, but add them anyway so `LocalizedString.get()` never returns the raw key):

```kotlin
            "display_name" to "<translated per language>",
            "sign_up_with_google" to "<translated per language>",
            "please_verify_email" to "Please verify your email",
            "please_verify_email_desc" to "Please verify your email before adding debts or splits. Check your inbox (and spam folder).",
            "got_it" to "Got it",
```

Use these translations (already drafted for key languages):
- `hi`: `"डिस्प्ले नाम"`, `"Google से साइन अप करें"`
- `es`: `"Nombre para mostrar"`, `"Regístrate con Google"`
- `fr`: `"Nom affiché"`, `"S'inscrire avec Google"`
- `de`: `"Anzeigename"`, `"Mit Google registrieren"`
- `ja`: `"表示名"`, `"Googleで登録"`
- `ar`: `"الاسم المعروض"`, `"اشترك باستخدام Google"`
- `zh`: `"显示名称"`, `"使用 Google 注册"`
- `ko`: `"표시 이름"`, `"Google로 가입"`
- `ru`: `"Отображаемое имя"`, `"Зарегистрироваться через Google"`
- `pt`: `"Nome de exibição"`, `"Inscreva-se com o Google"`
- The remaining 14 languages follow the same pattern — provide a reasonable translation per the existing style in each map.

- [ ] **Step 3: Verify no compilation error**

Run: `grep -c '"display_name"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`
Expected: 25 (one per language map)

Run: `grep -c '"sign_up_with_google"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`
Expected: 25

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
git commit -m "feat(i18n): add 5 new auth string keys across 25 languages

New keys: display_name, sign_up_with_google, please_verify_email,
please_verify_email_desc, got_it. The verify-alert keys stay
English-only by design (destructive-action UX clarity) but are
added to all 25 maps so LocalizedString.get() never returns raw key."
```

---

## Task 2: Create authSharedUi.kt (extracted composables + validation)

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/authSharedUi.kt`

**Interfaces:**
- Consumes: `UITokens`, `LocalExtraColors`, `LocalizedString`, `PasswordStrength` enum (defined here)
- Produces (used by Task 3, 4, 5, 6):
  - `enum class PasswordStrength { WEAK, MEDIUM, STRONG }`
  - `const val MIN_NAME_LENGTH = 3`
  - `const val MAX_NAME_LENGTH = 30`
  - `const val MIN_PASSWORD_LENGTH = 6`
  - `fun isEmailValid(s: String): Boolean`
  - `fun isNameValid(s: String): Boolean`
  - `fun computePasswordStrength(pwd: String): PasswordStrength`
  - `@Composable fun authFieldColors(): OutlinedTextFieldDefaults.colors`
  - `@Composable fun AuthHero(title: String)`
  - `@Composable fun GoogleGlyph()`
  - `@Composable fun PasswordStrengthBar(strength: PasswordStrength)`

- [ ] **Step 1: Create authSharedUi.kt with extracted composables + helpers**

Write the full file:

```kotlin
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
fun AuthHero(title: String) {
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
                "DebtPayoff Pro",
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
```

This is the **verbatim** content of `AuthScreen.kt:354-448` + `AuthViewModel.kt:24,109-119` relocated; no behavior change.

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/authSharedUi.kt
git commit -m "feat(auth): extract shared composables to authSharedUi.kt

Move AuthHero, GoogleGlyph, authFieldColors, PasswordStrengthBar
(and PasswordStrength enum) out of AuthScreen.kt into a shared file
that both SignUpScreen and SignInScreen will use. Includes validation
helpers (isEmailValid, isNameValid, computePasswordStrength) and
constants (MIN_NAME_LENGTH=3, MAX_NAME_LENGTH=30, MIN_PASSWORD_LENGTH=6).

Content is verbatim from AuthScreen.kt:354-448 + AuthViewModel.kt:109-119."
```

---

## Task 3: Create SignUpViewModel.kt

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpViewModel.kt`

**Interfaces:**
- Consumes: `AuthManager` (`signUpWithEmailPassword`, `signInWithGoogle`, `updateProfile`, `checkDeletionRequest`, `cancelAccountDeletion`, `signOut`, `getUserId`), `AppPreferences` (`setSignedIn`, `pendingDeletionTimestamp`, `setPendingDeletionTimestamp`, `clearPendingDeletion`), `SyncManager` (`fullSync`), `RealTimeSyncManager` (`startListening`), `AccountDeletionWorker.GRACE_PERIOD_MS`
- Produces (used by Task 6):
  - `@HiltViewModel class SignUpViewModel`
  - `state: StateFlow<SignUpUiState>`
  - `signedIn: StateFlow<Boolean>`
  - `showVerifyAlert: StateFlow<Boolean>`
  - `showGraceReLoginAlert: StateFlow<Boolean>`
  - `fun setName(v: String)`, `fun setEmail(v: String)`, `fun setPassword(v: String)`, `fun setConfirmPassword(v: String)`
  - `fun signUpWithGoogle(activity: Activity)`
  - `fun submit()`
  - `fun dismissVerifyAlert()`
  - `fun cancelDeletionViaGraceAlert()`
  - `fun signOutFromGraceAlert()`

- [ ] **Step 1: Create the file**

```kotlin
package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isBusy: Boolean = false,
    val errorRes: String? = null
)

@HiltViewModel
class SignUpViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(SignUpUiState())
    val state: StateFlow<SignUpUiState> = _state.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _showVerifyAlert = MutableStateFlow(false)
    val showVerifyAlert: StateFlow<Boolean> = _showVerifyAlert.asStateFlow()

    private val _showGraceReLoginAlert = MutableStateFlow(false)
    val showGraceReLoginAlert: StateFlow<Boolean> = _showGraceReLoginAlert.asStateFlow()

    fun setName(value: String) {
        val capped = value.take(MAX_NAME_LENGTH)
        _state.value = _state.value.copy(name = capped, errorRes = null)
    }

    fun setEmail(value: String) {
        _state.value = _state.value.copy(email = value.trim(), errorRes = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(
            password = value,
            passwordStrength = computePasswordStrength(value),
            errorRes = null
        )
    }

    fun setConfirmPassword(value: String) {
        _state.value = _state.value.copy(confirmPassword = value, errorRes = null)
    }

    fun signUpWithGoogle(activity: Activity) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithGoogle(activity).fold(
                onSuccess = { user ->
                    _showVerifyAlert.value = false
                    onAuthSuccess(
                        uid = user.uid,
                        displayName = user.displayName ?: user.email?.substringBefore('@') ?: "DebtPayoff Pro user",
                        email = user.email ?: "",
                        photo = user.photoUrl?.toString().orEmpty(),
                        provider = "google"
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message)
                }
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isBusy) return
        when {
            !isNameValid(current.name) -> _state.value = current.copy(errorRes = "name_required")
            !isEmailValid(current.email) -> _state.value = current.copy(errorRes = "email_required")
            current.password.length < MIN_PASSWORD_LENGTH -> _state.value = current.copy(errorRes = "password_6_chars_min")
            current.password != current.confirmPassword -> _state.value = current.copy(errorRes = "passwords_dont_match")
            else -> doSignUp()
        }
    }

    private fun doSignUp() {
        val current = _state.value
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signUpWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user ->
                    runCatching { auth.updateProfile(displayName = current.name, photoUri = null) }
                    _showVerifyAlert.value = true
                    onAuthSuccess(
                        uid = user.uid,
                        displayName = current.name,
                        email = user.email ?: current.email,
                        photo = "",
                        provider = "email"
                    )
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-up failed")
                }
            )
        }
    }

    private suspend fun onAuthSuccess(uid: String?, displayName: String, email: String, photo: String, provider: String) {
        if (uid == null) {
            _state.value = _state.value.copy(isBusy = false, errorRes = "auth_no_uid_returned")
            return
        }
        prefs.setSignedIn(provider, displayName, email, photo)
        runCatching {
            realTimeSyncManager.startListening(uid)
            sync.fullSync(uid)
        }
        runCatching { checkGracePeriodOnSignIn(uid) }
        _state.value = _state.value.copy(isBusy = false)
    }

    private suspend fun checkGracePeriodOnSignIn(uid: String) {
        val serverRequestedAt = runCatching { auth.checkDeletionRequest(uid) }.getOrNull()
        val effectiveTs = serverRequestedAt ?: prefs.pendingDeletionTimestamp.first().takeIf { it > 0L }
        if (effectiveTs != null && effectiveTs > 0L) {
            val elapsed = System.currentTimeMillis() - effectiveTs
            if (elapsed < GRACE_PERIOD_MS) {
                prefs.setPendingDeletionTimestamp(effectiveTs)
                _showGraceReLoginAlert.value = true
            }
        }
    }

    fun dismissVerifyAlert() { _showVerifyAlert.value = false }

    fun cancelDeletionViaGraceAlert() = viewModelScope.launch {
        val uid = auth.getUserId()
        if (uid != null) {
            runCatching { auth.cancelAccountDeletion(uid, System.currentTimeMillis()) }
        }
        prefs.clearPendingDeletion()
        _showGraceReLoginAlert.value = false
    }

    fun signOutFromGraceAlert() = viewModelScope.launch {
        auth.signOut()
        prefs.setSignedIn(null)
        _showGraceReLoginAlert.value = false
    }

    companion object {
        private const val GRACE_PERIOD_MS = AccountDeletionWorker.GRACE_PERIOD_MS
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpViewModel.kt
git commit -m "feat(auth): add SignUpViewModel with isolated sign-up state

New ViewModel owns name/email/password/confirmPassword state,
passwordStrength, isBusy, errorRes, plus signedIn/showVerifyAlert/
showGraceReLoginAlert flows. Calls auth.signUpWithEmailPassword then
auth.updateProfile(displayName=name). Show verify alert for email
provider only — Google path skips it (auto-verified)."
```

---

## Task 4: Create SignInViewModel.kt (with forgot-password flow moved here)

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInViewModel.kt`

**Interfaces:**
- Consumes: `AuthManager` (`signInWithEmailPassword`, `signInWithGoogle`, `sendPasswordResetEmail`, `checkDeletionRequest`, `cancelAccountDeletion`, `signOut`, `getUserId`), `AppPreferences` (`setSignedIn`, `pendingDeletionTimestamp`, `setPendingDeletionTimestamp`, `clearPendingDeletion`, `getForgotPasswordDailyCount`, `setForgotPasswordLastSent`, `setForgotPasswordDailyCount`, `forgotPasswordLastSent`), `SyncManager` (`fullSync`), `RealTimeSyncManager` (`startListening`), `AccountDeletionWorker.GRACE_PERIOD_MS`, `LocalizedString`
- Produces (used by Task 7):
  - `enum class SignInMode { SIGN_IN, FORGOT_PASSWORD }`
  - `@HiltViewModel class SignInViewModel`
  - `state: StateFlow<SignInUiState>`
  - `signedIn: StateFlow<Boolean>`
  - `showGraceReLoginAlert: StateFlow<Boolean>`
  - `fun setEmail(v)`, `fun setPassword(v)`, `fun setMode(mode: SignInMode)`
  - `fun signInWithGoogle(activity: Activity)`
  - `fun submit()`
  - `suspend fun dailyAttemptsRemaining(): Int`
  - `fun dismissGraceReLoginAlert()`, `fun cancelDeletionViaGraceAlert()`, `fun signOutFromGraceAlert()`

- [ ] **Step 1: Create the file**

```kotlin
package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.firebase.SyncManager
import com.dhanuk.debtbro.util.LocalizedString
import com.dhanuk.debtbro.worker.AccountDeletionWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SignInMode { SIGN_IN, FORGOT_PASSWORD }

data class SignInUiState(
    val mode: SignInMode = SignInMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val isBusy: Boolean = false,
    val errorRes: String? = null,
    val resetLinkSent: Boolean = false,
    val resendCountdownSeconds: Int = 0
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences,
    private val sync: SyncManager,
    private val realTimeSyncManager: RealTimeSyncManager
) : ViewModel() {

    private val _state = MutableStateFlow(SignInUiState())
    val state: StateFlow<SignInUiState> = _state.asStateFlow()

    private val _signedIn = MutableStateFlow(false)
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _showGraceReLoginAlert = MutableStateFlow(false)
    val showGraceReLoginAlert: StateFlow<Boolean> = _showGraceReLoginAlert.asStateFlow()

    private var countdownJob: Job? = null

    fun setEmail(value: String) {
        _state.value = _state.value.copy(email = value.trim(), errorRes = null)
    }

    fun setPassword(value: String) {
        _state.value = _state.value.copy(password = value, errorRes = null)
    }

    fun setMode(mode: SignInMode) {
        countdownJob?.cancel()
        _state.value = _state.value.copy(
            mode = mode,
            errorRes = null,
            resendCountdownSeconds = 0,
            resetLinkSent = false
        )
    }

    fun signInWithGoogle(activity: Activity) {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithGoogle(activity).fold(
                onSuccess = { user ->
                    onAuthSuccess(user.uid, user.displayName ?: "DebtPayoff Pro user", user.email ?: "", user.photoUrl?.toString().orEmpty(), "google")
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message)
                }
            )
        }
    }

    fun submit() {
        val current = _state.value
        if (current.isBusy) return
        when (current.mode) {
            SignInMode.SIGN_IN -> doSignIn()
            SignInMode.FORGOT_PASSWORD -> sendResetEmail()
        }
    }

    private fun doSignIn() {
        val current = _state.value
        if (current.password.length < MIN_PASSWORD_LENGTH) {
            _state.value = current.copy(errorRes = "password_6_chars_min")
            return
        }
        if (!isEmailValid(current.email)) {
            _state.value = current.copy(errorRes = "email_required")
            return
        }
        _state.value = current.copy(isBusy = true, errorRes = null)
        viewModelScope.launch {
            auth.signInWithEmailPassword(current.email, current.password).fold(
                onSuccess = { user -> onAuthSuccess(user.uid, user.displayName ?: "DebtPayoff Pro user", user.email ?: "", "", "email") },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: "Sign-in failed")
                }
            )
        }
    }

    private fun sendResetEmail() {
        val current = _state.value
        if (!isEmailValid(current.email)) {
            _state.value = current.copy(errorRes = "email_required")
            return
        }
        viewModelScope.launch {
            val count = prefs.getForgotPasswordDailyCount()
            if (count >= MAX_DAILY_RESETS) {
                _state.value = _state.value.copy(errorRes = "daily_limit_reached")
                return@launch
            }
            val now = System.currentTimeMillis()
            val lastSent = prefs.forgotPasswordLastSent.first()
            val secondsSinceLastSend = if (lastSent == 0L) RESEND_COOLDOWN_SECONDS.toLong() else (now - lastSent) / 1000
            if (lastSent > 0 && secondsSinceLastSend < RESEND_COOLDOWN_SECONDS) {
                val remaining = (RESEND_COOLDOWN_SECONDS - secondsSinceLastSend).toInt().coerceAtLeast(1)
                startCountdown(remaining)
                return@launch
            }
            _state.value = current.copy(isBusy = true, errorRes = null)
            auth.sendPasswordResetEmail(current.email).fold(
                onSuccess = {
                    prefs.setForgotPasswordLastSent(System.currentTimeMillis())
                    val newCount = prefs.getForgotPasswordDailyCount() + 1
                    prefs.setForgotPasswordDailyCount(newCount)
                    _state.value = _state.value.copy(
                        isBusy = false,
                        resetLinkSent = true,
                        resendCountdownSeconds = RESEND_COOLDOWN_SECONDS
                    )
                    startCountdown(RESEND_COOLDOWN_SECONDS)
                },
                onFailure = { e ->
                    _state.value = _state.value.copy(isBusy = false, errorRes = e.message ?: LocalizedString.get("could_not_send_reset_email"))
                }
            )
        }
    }

    private fun startCountdown(from: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var remaining = from
            while (remaining > 0) {
                _state.value = _state.value.copy(resendCountdownSeconds = remaining)
                delay(1000)
                remaining -= 1
            }
            _state.value = _state.value.copy(resendCountdownSeconds = 0)
        }
    }

    suspend fun dailyAttemptsRemaining(): Int {
        val count = prefs.getForgotPasswordDailyCount()
        return (MAX_DAILY_RESETS - count).coerceAtLeast(0)
    }

    private suspend fun onAuthSuccess(uid: String?, displayName: String, email: String, photo: String, provider: String) {
        if (uid == null) {
            _state.value = _state.value.copy(isBusy = false, errorRes = LocalizedString.get("auth_no_uid_returned"))
            return
        }
        prefs.setSignedIn(provider, displayName, email, photo)
        runCatching {
            realTimeSyncManager.startListening(uid)
            sync.fullSync(uid)
        }
        runCatching { checkGracePeriodOnSignIn(uid) }
        _state.value = _state.value.copy(isBusy = false)
    }

    private suspend fun checkGracePeriodOnSignIn(uid: String) {
        val serverRequestedAt = runCatching { auth.checkDeletionRequest(uid) }.getOrNull()
        val effectiveTs = serverRequestedAt ?: prefs.pendingDeletionTimestamp.first().takeIf { it > 0L }
        if (effectiveTs != null && effectiveTs > 0L) {
            val elapsed = System.currentTimeMillis() - effectiveTs
            if (elapsed < GRACE_PERIOD_MS) {
                prefs.setPendingDeletionTimestamp(effectiveTs)
                _showGraceReLoginAlert.value = true
            }
        }
    }

    fun dismissGraceReLoginAlert() { _showGraceReLoginAlert.value = false }

    fun cancelDeletionViaGraceAlert() = viewModelScope.launch {
        val uid = auth.getUserId()
        if (uid != null) {
            runCatching { auth.cancelAccountDeletion(uid, System.currentTimeMillis()) }
        }
        prefs.clearPendingDeletion()
        _showGraceReLoginAlert.value = false
    }

    fun signOutFromGraceAlert() = viewModelScope.launch {
        auth.signOut()
        prefs.setSignedIn(null)
        _showGraceReLoginAlert.value = false
    }

    companion object {
        const val MAX_DAILY_RESETS = 5
        const val RESEND_COOLDOWN_SECONDS = 60
        private const val GRACE_PERIOD_MS = AccountDeletionWorker.GRACE_PERIOD_MS
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInViewModel.kt
git commit -m "feat(auth): add SignInViewModel with isolated sign-in + forgot-password state

New ViewModel owns mode (SIGN_IN / FORGOT_PASSWORD), email, password,
isBusy, errorRes, resetLinkSent, resendCountdownSeconds. Forgot-password
flow moved here from AuthViewModel.kt - daily cap MAX_DAILY_RESETS=5,
RESEND_COOLDOWN_SECONDS=60, persisted via AppPreferences. Grace-period
alert flow moved here too."
```

---

## Task 5: Update Screen.kt + NavGraph.kt routing

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/Screen.kt:14`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt:36,279,289,292-297,305`

**Interfaces:**
- Consumes: `Screen.kt` routes, `NavGraph.kt` composable builder
- Produces: `Screen.SignUp` and `Screen.SignIn` routes available for navigation; composable blocks in NavGraph ready to host `SignUpScreen` and `SignInScreen` (added in Task 6 + 7)

- [ ] **Step 1: Update Screen.kt**

Replace line 14:

```kotlin
// Before
object Auth : Screen("auth")

// After
object SignUp : Screen("sign_up")
object SignIn : Screen("sign_in")
```

The full file becomes:

```kotlin
package com.dhanuk.debtbro.presentation.navigation

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Dashboard : Screen("dashboard")
    object DebtList : Screen("debt_list")
    object DebtDetail : Screen("debt_detail/{debtId}") {
        fun createRoute(debtId: Int) = "debt_detail/$debtId"
        fun createRoute(debtId: String) = "debt_detail/$debtId"
    }
    object Split : Screen("split")
    object Analytics : Screen("analytics")
    object Settings : Screen("settings")
    object SignUp : Screen("sign_up")
    object SignIn : Screen("sign_in")
}
```

- [ ] **Step 2: Update NavGraph.kt imports**

In `NavGraph.kt:36`, replace:

```kotlin
// Before
import com.dhanuk.debtbro.presentation.screens.auth.AuthScreen

// After
import com.dhanuk.debtbro.presentation.screens.auth.SignUpScreen
import com.dhanuk.debtbro.presentation.screens.auth.SignInScreen
```

- [ ] **Step 3: Replace Auth composable block with SignUp + SignIn**

In `NavGraph.kt:292-297`, replace:

```kotlin
// Before (lines 292-297)
            composable(Screen.Auth.route) {
                AuthScreen(
                    onAuthComplete = { navController.popBackStack() },
                    onSkip = { navController.popBackStack() }
                )
            }

// After
            composable(Screen.SignUp.route) {
                SignUpScreen(
                    onAuthComplete = { navController.popBackStack() },
                    onNavigateToSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
                    onSkip = { navController.popBackStack() }
                )
            }
            composable(Screen.SignIn.route) {
                SignInScreen(
                    onAuthComplete = { navController.popBackStack() },
                    onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) { launchSingleTop = true } },
                    onSkip = { navController.popBackStack() }
                )
            }
```

- [ ] **Step 4: Redirect "auth required" call sites to SignIn**

In `NavGraph.kt:279`, replace `Screen.Auth.route` with `Screen.SignIn.route`:

```kotlin
// Before
composable(Screen.Split.route) { SplitScreen(onAuthRequired = { navController.navigate(Screen.Auth.route) }) }

// After
composable(Screen.Split.route) { SplitScreen(onAuthRequired = { navController.navigate(Screen.SignIn.route) }) }
```

In `NavGraph.kt:289`, replace `Screen.Auth.route` with `Screen.SignIn.route`:

```kotlin
// Before
onAuthRequired = { navController.navigate(Screen.Auth.route) }

// After
onAuthRequired = { navController.navigate(Screen.SignIn.route) }
```

In `NavGraph.kt:305`, replace `Screen.Auth.route` with `Screen.SignIn.route`:

```kotlin
// Before
onSignInRequired = { navController.navigate(Screen.Auth.route) }

// After
onSignInRequired = { navController.navigate(Screen.SignIn.route) }
```

- [ ] **Step 5: Verify no remaining references to Screen.Auth**

Run: `grep -rn "Screen\.Auth" app/src/main/java/`
Expected: no output (zero matches)

- [ ] **Step 6: Commit (do NOT push yet — code won't compile until Task 6+7 create the screen files)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/navigation/Screen.kt
git add app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt
git commit -m "feat(nav): replace Screen.Auth with Screen.SignUp + Screen.SignIn

Three 'auth required' call sites (SplitScreen, DebtDetailScreen,
AddDebtBottomSheet) now navigate to SignIn (returning-user flow).
OnboardingScreen does NOT use Screen.Auth - its inline Page6Auth is
unaffected. AuthScreen/AuthViewModel imports replaced; files will be
deleted after the new SignUp/SignIn screen files land in next tasks."
```

---

## Task 6: Create SignUpScreen.kt

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpScreen.kt`

**Interfaces:**
- Consumes: `SignUpViewModel` (Task 3), `authSharedUi.kt` (Task 2), `LocalizedString`, `UITokens`, `LocalExtraColors`, Material 3 components
- Produces: `@Composable fun SignUpScreen(onAuthComplete: () -> Unit, onNavigateToSignIn: () -> Unit, onSkip: () -> Unit)` referenced by NavGraph (Task 5)

- [ ] **Step 1: Create the file**

```kotlin
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
            AuthHero(title = LocalizedString.get("create_account"))

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
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignUpScreen.kt
git commit -m "feat(auth): add SignUpScreen with name field, verify alert, grace alert

New SignUpScreen composable renders AuthHero, Google sign-up button,
name/email/password/confirm fields, password strength bar, Sign Up
button (gated on name>=3 + email + password>=6 + confirm match).
On email sign-up success, shows English-only 'Please verify your
email' alert; on Google sign-up, skips alert (auto-verified). Grace
alert uses '24-hour' copy matching live legal pages."
```

---

## Task 7: Create SignInScreen.kt (with forgot-password + grace 24h update)

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInScreen.kt`

**Interfaces:**
- Consumes: `SignInViewModel` (Task 4), `authSharedUi.kt` (Task 2), `LocalizedString`, `UITokens`, `LocalExtraColors`, Material 3 components
- Produces: `@Composable fun SignInScreen(onAuthComplete: () -> Unit, onNavigateToSignUp: () -> Unit, onSkip: () -> Unit)` referenced by NavGraph (Task 5)

- [ ] **Step 1: Create the file**

```kotlin
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
            AuthHero(title = LocalizedString.get("welcome_back"))

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
```

Note: grace-period text updated from `"30-minute"` to `"24-hour"` to match the live legal pages (`Privacy-Policy.html`, `Delete-Account.html`, `Help-and-Support.html` — all already updated in prior work).

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/SignInScreen.kt
git commit -m "feat(auth): add SignInScreen with forgot-password + grace 24h text

New SignInScreen composable renders AuthHero, Google sign-in button,
email/password fields, forgot-password link, Sign In button, 'Don't
have an account? Sign up' link, Skip for now. Forgot-password flow
moved here from AuthViewModel - daily cap, cooldown, attempts text.
Grace alert text updated from '30-minute' to '24-hour' to match
live legal pages."
```

---

## Task 8: Update AccountDeletionWorker.kt GRACE_PERIOD_MS + delete old files

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/worker/AccountDeletionWorker.kt:73`
- Delete: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt`
- Delete: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt`

**Interfaces:**
- Consumes: existing `AccountDeletionWorker.kt`
- Produces: `GRACE_PERIOD_MS` constant now reflects 24h; old combined files gone, freeing the codebase to compile against only `SignUp*`/`SignIn*` files

- [ ] **Step 1: Update GRACE_PERIOD_MS**

In `AccountDeletionWorker.kt:73`, replace:

```kotlin
// Before
private const val GRACE_PERIOD_MS = 30L * 60L * 1000L

// After
private const val GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L
```

- [ ] **Step 2: Delete AuthScreen.kt**

```bash
rm app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt
```

- [ ] **Step 3: Delete AuthViewModel.kt**

```bash
rm app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt
```

- [ ] **Step 4: Verify the AuthViewModel.PasswordStrength enum (formerly at AuthViewModel.kt:24) is no longer referenced**

Run: 
```bash
grep -rn "AuthViewModel" app/src/main/java/
grep -rn "AuthScreen" app/src/main/java/
grep -rn "PasswordStrength" app/src/main/java/
```
Expected:
- Zero matches for `AuthViewModel` and `AuthScreen`
- Matches for `PasswordStrength` only in `authSharedUi.kt`, `SignUpViewModel.kt`, `SignUpScreen.kt` (the new file in Task 2/3/6)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/worker/AccountDeletionWorker.kt
git rm app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt
git rm app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt
git commit -m "feat(auth): update GRACE_PERIOD_MS to 24h; delete old AuthScreen/AuthViewModel

AccountDeletionWorker.kt:73 GRACE_PERIOD_MS changed from 30min to 24h
to match live Privacy-Policy/Delete-Account/Help-and-Support pages.

Removed AuthScreen.kt (448 lines) and AuthViewModel.kt (298 lines) -
replaced by SignUpScreen/SignUpViewModel/SignInScreen/SignInViewModel
+ authSharedUi.kt."
```

---

## Task 9: Push, Watch CI Green, Update AGENTS.md

**Files:**
- Modify: `AGENTS.md` (optional — update structure section to reflect new auth file layout)

**Interfaces:**
- Consumes: all prior tasks complete in local branch
- Produces: green CI build on feature branch `feat/auth-screens-split`

- [ ] **Step 1: Push to remote**

```bash
git push origin main
```

(Or push to a feature branch if preferred — see Step 1 alternate below. The repo's `build.yml` runs CI on every push to any branch, and creates a Release only on `main`.)

- [ ] **Step 1 alternate: Create a feature branch + PR**

```bash
git checkout -b feat/auth-screens-split
git push -u origin feat/auth-screens-split
gh pr create --title "feat(auth): split AuthScreen into SignUp + SignIn; grace 24h" --body "See docs/superpowers/specs/2026-07-05-auth-screens-split-design.md"
```

- [ ] **Step 2: Watch CI until green**

```bash
gh run watch --exit-status
```
Expected: ✓ Workflow `build (release)` APK build succeeds. ✓ Workflow `build (debug)` APK build succeeds.

If CI fails, read the logs via `gh run view --log-failed`, fix the issue (e.g., missing import, type mismatch, lint error), commit, and push again. Repeat until GREEN.

- [ ] **Step 3: Update AGENTS.md structure tree (optional)**

In `AGENTS.md`, under the STRUCTURE section, update the `presentation/screens/` line to reflect the new auth file layout:

```markdown
├── presentation/
│   ├── screens/            # 8 screens: onboarding, dashboard, debtlist, debtdetail, adddebt, analytics, settings, split
│   │   └── auth/           # SignUpScreen + SignInScreen + authSharedUi (split in 2026-07)
```

- [ ] **Step 4: Commit AGENTS.md (if changed) + amend PR description to mention the spec doc**

```bash
git add AGENTS.md
git commit -m "docs(agents): update structure tree for new auth screen layout"
git push
```

---

## Self-Review

**1. Spec coverage:**

| Spec section | Task(s) | Coverage |
|---|---|---|
| `authSharedUi.kt` extraction | Task 2 | ✅ verbatim from AuthScreen.kt:354-448 + AuthValidator.kt:109-119 |
| `SignUpViewModel.kt` + behavior | Task 3 | ✅ matches spec `submit()` validation order, `onAuthSuccess` calls `auth.updateProfile(displayName=name, photoUri=null)`, verify alert tripped only for email provider |
| `SignInViewModel.kt` + forgot-password + grace | Task 4 | ✅ matches spec — forgot-password constants `MAX_DAILY_RESETS=5`, `RESEND_COOLDOWN_SECONDS=60` preserved |
| `SignUpScreen.kt` | Task 6 | ✅ matches spec layout (12 elements bottom-up), enable rule `!isBusy && name.trim().length in 3..30 && email.isNotBlank() && password.length >= 6 && password == confirmPassword` |
| `SignInScreen.kt` | Task 7 | ✅ matches spec layout, forgot-password flow, grace alert with "24-hour" text |
| `Screen.kt` routes | Task 5 | ✅ `SignUp` + `SignIn` replace `Auth` |
| `NavGraph.kt` routes + call-site redirects | Task 5 | ✅ 3 call sites redirected to `Screen.SignIn.route`, AuthScreen composable block replaced |
| `OnboardingScreen.kt` unchanged | (not in any task) | ✅ no task touches this file or `OnboardingViewModel.kt` |
| `AccountDeletionWorker.kt:73` 30min → 24h | Task 8 | ✅ matches spec acceptance criterion 5 |
| `LocalizedString.kt` 5 new keys × 25 maps = 125 entries | Task 1 | ✅ matches spec strings section |
| Verify alert English-only ("Please verify your email before adding debts or splits. Check your inbox (and spam folder).") | Task 6 inline | ✅ hardcoded English string in `SignUpScreen.kt`, not via `LocalizedString.get()` |
| Grace alert text "24-hour" | Task 6 + 7 inline | ✅ hardcoded English in `SignUpScreen.kt` and `SignInScreen.kt` |
| Acceptance criterion 12 (CI green) | Task 9 | ✅ `gh run watch --exit-status` gate |
| Acceptance criterion 6 (Onboarding unchanged) | (no task) | ✅ verified by `git diff` only listing Onboarding in "Files NOT Modified" |

All 13 spec acceptance criteria covered. ✓

**2. Placeholder scan:**

Searched plan for: `TBD`, `TODO`, `implement later`, `fill in`, "appropriate error handling", "add validation", "handle edge cases", "Write tests for the above", "Similar to Task N". None found. Every task embeds complete Kotlin code with actual imports, signatures, and logic. Every step shows exact `grep`/`git` commands with expected outputs.

**3. Type consistency:**

- `PasswordStrength` enum: defined in Task 2 `authSharedUi.kt`. Used in Task 3 `SignUpViewModel.kt` (field `passwordStrength: PasswordStrength = PasswordStrength.WEAK`). Used in Task 6 `SignUpScreen.kt` (passes `state.passwordStrength` to `PasswordStrengthBar`). ✓ consistent
- `SignInMode` enum: defined in Task 4 `SignInViewModel.kt`. Used in Task 7 `SignInScreen.kt` (compares `state.mode == SignInMode.FORGOT_PASSWORD`). ✓ consistent
- `SignUpUiState` (Task 3): fields `(name, email, password, confirmPassword, passwordStrength, isBusy, errorRes)` — used in Task 6 with same names. ✓ consistent
- `SignInUiState` (Task 4): fields `(mode, email, password, isBusy, errorRes, resetLinkSent, resendCountdownSeconds)` — used in Task 7 with same names. ✓ consistent
- `MIN_NAME_LENGTH`, `MAX_NAME_LENGTH`, `MIN_PASSWORD_LENGTH`: defined in Task 2 `authSharedUi.kt`. Used in `SignUpViewModel.submit()` validation (Task 3), `SignUpScreen.kt` button enable rule (Task 6), `SignInViewModel.doSignIn()` validation (Task 4). ✓ consistent
- `isNameValid`, `isEmailValid`, `computePasswordStrength`: defined Task 2. `isNameValid` used Task 3 + Task 6. `isEmailValid` used Task 3 + Task 4. `computePasswordStrength` used Task 3 in `setPassword()`. ✓ consistent
- `authFieldColors()`, `AuthHero(title)`, `GoogleGlyph()`, `PasswordStrengthBar(strength)`: defined Task 2. Used identically Task 6 + Task 7. ✓ consistent
- `SignUpScreen`/`SignInScreen` signatures (`onAuthComplete, onNavigateToSignIn/SignUp, onSkip`): Task 6/7 declares them, Task 5 NavGraph call sites use same parameter order. ✓ consistent
- `auth.updateProfile(displayName, photoUri)`: Task 3 calls it as `auth.updateProfile(displayName = current.name, photoUri = null)` — matches AuthManager.kt:246 signature `suspend fun updateProfile(displayName: String?, photoUri: String?): Result<Unit>`. ✓ consistent (verified during spec self-review)
- `AccountDeletionWorker.GRACE_PERIOD_MS`: referenced in `SignUpViewModel`/`SignInViewModel` companion objects — same pattern as old `AuthViewModel`. After Task 8 changes the constant to `24L * 60L * 60L * 1000L`, both ViewModels automatically see the new value. ✓ consistent

No type inconsistencies found. ✓

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-07-05-auth-screens-split.md`.

**Two execution options:**

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration. Best for catching integration issues early without blowing context.

**2. Inline Execution** — I execute tasks in this session using the executing-plans skill, with batch checkpoints for your review.

Which approach?
