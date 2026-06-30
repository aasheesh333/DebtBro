# DebtBro UI/UX Redesign + Bug Fix Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add Sign In/Sign Up auth screens, fix theme color system, redesign Settings, add auth-required popups, fix all startActivity crashes, add policy page links.

**Architecture:** New AuthScreen with 3 modes (SIGN_IN/SIGN_UP/FORGOT_PASSWORD) using existing AuthManager. Theme fix by wiring BrandPrimaryLight into lightColorScheme. Settings redesign with new Legal & Support section. Auth popup triggered from ViewModels when sync actions happen without sign-in. All URL opens wrapped in try-catch.

**Tech Stack:** Kotlin, Jetpack Compose, Material3, Firebase Auth (Email+Google), DataStore Preferences, Hilt DI, Navigation Compose

## Global Constraints

- Play Store category = Productivity (not Finance)
- SAVAGE renamed to SPICY already, maintain this
- Contact email: support@dhanuksoftwares.com
- Hosting URLs: https://dhanuk.page.gd/DebtBro/ with Title-Case filenames
- Firebase project: DebtBro-01 (debtbro-01)
- Authentic Hinglish style for any user-facing strings
- No test files exist in project — no TDD steps, just implementation
- Compose theming: use `MaterialTheme.colorScheme.primary` not `PrimaryGreen` direct
- App theme: dark-first with PrimaryGreen(#00E5A0), light uses BrandPrimaryLight(#00A86B)
- Build commands: `./gradlew assembleDebug` for verification

---

## File Structure

### New Files
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt` — Auth screen composable (3 modes)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt` — Auth logic (sign in/up/forgot)

### Modified Files
- `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/Screen.kt` — Add `Auth` route
- `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt` — Add Auth composable destination
- `app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt` — Add forgot password rate limit prefs
- `app/src/main/java/com/dhanuk/debtbro/presentation/theme/Theme.kt` — Fix light scheme primary/onPrimary
- `app/src/main/java/com/dhanuk/debtbro/presentation/theme/Color.kt` — No changes needed (tokens already exist)
- `app/src/main/java/com/dhanuk/debtbro/app/build.gradle.kts` — Add TERMS_OF_SERVICE_URL, HELP_URL BuildConfig fields
- `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt` — Add new i18n keys
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt` — Full redesign + policy links + crash fix
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt` — Add auth navigation callback
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtViewModel.kt` — Add auth prompt flow
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailViewModel.kt` — Add auth prompt flow
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt` — Add auth prompt flow
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtBottomSheet.kt` — Observe auth prompt
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailScreen.kt` — Observe auth prompt
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt` — Observe auth prompt
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/dashboard/DashboardScreen.kt` — Fix fake refresh + PrimaryGreen refs
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsScreen.kt` — PrimaryGreen → theme token
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/DebtCard.kt` — PrimaryGreen → theme token
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/GoogleSignInCard.kt` — PrimaryGreen → theme token
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/LanguageSelectorGrid.kt` — PrimaryGreen → theme token
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/ConfettiOverlay.kt` — PrimaryGreen → theme token + Color.White
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/LoadingDotsIndicator.kt` — Color.White → theme token
- `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt` — PrimaryGreen → theme token
- `app/src/main/java/com/dhanuk/debtbro/util/ShareUtils.kt` — try-catch around startActivity
- `app/src/main/java/com/dhanuk/debtbro/util/HtmlExporter.kt` — try-catch around startActivity
- `app/src/main/java/com/dhanuk/debtbro/util/CanvasExporter.kt` — try-catch around startActivity
- `app/src/main/java/com/dhanuk/debtbro/data/firebase/RealTimeSyncManager.kt` — Split retry counters

---

### Task 1: Theme Color Fix — Light Scheme + Core Tokens

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/theme/Theme.kt:31-48`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/theme/Color.kt` (no changes, just verify BrandPrimaryLight exists)

**Interfaces:**
- Produces: `MaterialTheme.colorScheme.primary` will return `BrandPrimaryLight` (#00A86B) in light mode, `PrimaryGreen` (#00E5A0) in dark mode. `MaterialTheme.colorScheme.onPrimary` will return `Color.White` in light mode, `BackgroundDark` in dark mode.

- [ ] **Step 1: Fix LightColors in Theme.kt**

Change `LightColors` to use `BrandPrimaryLight` and `Color.White`:

```kotlin
private val LightColors = lightColorScheme(
    primary = BrandPrimaryLight,
    onPrimary = Color.White,
    primaryContainer = SurfaceVariantLight,
    onPrimaryContainer = OnSurfaceLight,
    secondary = PrimaryGreenDark,
    background = BackgroundLight,
    onBackground = OnSurfaceLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = SubtitleGrayLight,
    surfaceTint = BrandPrimaryLight,
    outline = OutlineLight,
    outlineVariant = DividerLight,
    error = DangerRedLight,
    onError = SurfaceLight
)
```

Also fix `debtBroExtraColors()` light branch to use `BrandPrimaryLight`:
```kotlin
} else {
    DebtBroExtraColors(SubtitleGrayLight, DividerLight, CardInnerLight, BrandPrimaryLight, DangerRedLight, WarningAmber)
}
```

- [ ] **Step 2: Replace PrimaryGreen with theme tokens across all screen/component files**

In each file listed below, replace all `PrimaryGreen` references with `MaterialTheme.colorScheme.primary` (except where it's used as a static color value like in `FilterChipDefaults.filterChipColors(selectedContainerColor = ...)` which also needs the same replacement).

Files and pattern:
- `NavGraph.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `DebtCard.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `GoogleSignInCard.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `LanguageSelectorGrid.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `AddDebtBottomSheet.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `SettingsScreen.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `DebtDetailScreen.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `DashboardScreen.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `SplitScreen.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `AnalyticsScreen.kt` — `PrimaryGreen` → `MaterialTheme.colorScheme.primary`
- `ConfettiOverlay.kt` — `Color(0xFF00E5A0)` → `MaterialTheme.colorScheme.primary`
- `LoadingDotsIndicator.kt` — `color: Color = Color.White` default param → `color: Color = MaterialTheme.colorScheme.onSurface` (but default params can't be composable, so remove default and pass from call sites)

Note: `PrimaryGreen` constant remains in Color.kt for reference. `Theme.kt` DarkColors still references it directly (it's the right value for dark scheme).

- [ ] **Step 3: Replace Color.Black/White with theme tokens**

Replace `Color.Black` on primary-colored surfaces with `MaterialTheme.colorScheme.onPrimary`:
- `AddDebtBottomSheet.kt` — lines 187, 261, 279, 378, 380, 384
- `DebtDetailScreen.kt` — lines 204, 206, 239, 538, 568, 619
- `SplitScreen.kt` — lines 171, 173, 256, 364, 366

Replace `Color.White` text references with `MaterialTheme.colorScheme.onSurface`:
- `AddDebtBottomSheet.kt:202` — `Color.White` → `MaterialTheme.colorScheme.onPrimary`
- `LoadingDotsIndicator.kt:23` — Change default param, pass from call sites
- `ConfettiOverlay.kt:29` — Keep confetti colors as decorative (not theme-dependent)
- `SplitScreen.kt:288` — `Color.LightGray` → `MaterialTheme.colorScheme.onSurfaceVariant`
- `DebtCard.kt:67` — `Color.Gray` → `extra.subtitleGray`

- [ ] **Step 4: Commit theme fix**

```bash
git add -A && git commit -m "fix: light theme primary color (#00A86B), replace 80+ hardcoded PrimaryGreen and Color.Black/White with theme tokens"
```

---

### Task 2: Bug Fixes — Crash Guards + Retry Counters

**Files:**
- Modify: `app/build.gradle.kts:42-43`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt:306-336`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt:378`
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/ShareUtils.kt:58`
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/HtmlExporter.kt:394`
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/CanvasExporter.kt:420`
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/firebase/RealTimeSyncManager.kt:37`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/dashboard/DashboardScreen.kt:66-70`

**Interfaces:**
- Produces: Safe `openUrl(context, url)` utility function. All `startActivity(ACTION_VIEW)` calls wrapped in try-catch. Separate retry counters for debt/split listeners.

- [ ] **Step 1: Add BuildConfig URL fields for Terms and Help**

In `app/build.gradle.kts`, after line 43 (`ACCOUNT_DELETION_URL`), add:

```kotlin
buildConfigField("String", "TERMS_OF_SERVICE_URL", "\"https://dhanuk.page.gd/DebtBro/Terms-and-Conditions.html\"")
buildConfigField("String", "HELP_URL", "\"https://dhanuk.page.gd/DebtBro/Help-and-Support.html\"")
```

Also change `PRIVACY_POLICY_URL` line 42 to have a hardcoded fallback instead of empty string:

```kotlin
buildConfigField("String", "PRIVACY_POLICY_URL", "\"${escapedProp("PRIVACY_POLICY_URL").ifEmpty { "https://dhanuk.page.gd/DebtBro/Privacy-Policy.html" }}\"")
```

- [ ] **Step 2: Create safe URL opener utility**

Add a top-level function in a new file `app/src/main/java/com/dhanuk/debtbro/util/UrlUtils.kt`:

```kotlin
package com.dhanuk.debtbro.util

import android.content.Context
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun openUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "No browser found to open this link", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open link", Toast.LENGTH_SHORT).show()
    }
}
```

- [ ] **Step 3: Fix SettingsScreen policy page click — replace direct startActivity with openUrl()**

In `SettingsScreen.kt`, replace the privacy policy click handler (around line 308-313):

```kotlin
// Old:
val url = BuildConfig.PRIVACY_POLICY_URL
if (url.isNotBlank()) {
    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
    context.startActivity(intent)
}

// New:
openUrl(context, BuildConfig.PRIVACY_POLICY_URL)
```

- [ ] **Step 4: Fix ShareUtils.shareFile crash**

In `ShareUtils.kt:58`, wrap `context.startActivity(intent)` in try-catch:

```kotlin
try {
    context.startActivity(intent)
} catch (e: Exception) {
    Toast.makeText(context, "Could not share file", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 5: Fix HtmlExporter.shareImage crash**

In `HtmlExporter.kt`, around line 394, wrap in try-catch:

```kotlin
try {
    context.startActivity(Intent.createChooser(intent, "Share DebtBro Card").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
} catch (e: Exception) {
    Toast.makeText(context, "Could not share image", Toast.LENGTH_SHORT).show()
}
```

- [ ] **Step 6: Fix CanvasExporter.shareDebtCard crash**

Same pattern as HtmlExporter in `CanvasExporter.kt:420`.

- [ ] **Step 7: Fix SettingsViewModel CSV export crash**

In `SettingsViewModel.kt:378`, wrap `context.startActivity(chooser)` in try-catch.

- [ ] **Step 8: Fix RealTimeSyncManager shared retry counter**

In `RealTimeSyncManager.kt`, replace single `retryAttempt` (line 37) with two:

```kotlin
private var debtRetryAttempt = 0
private var splitRetryAttempt = 0
```

And in the debt listener callback (around line 50), use `debtRetryAttempt++` instead of `retryAttempt++`. In the split listener (around line 73), use `splitRetryAttempt++`. Reset the appropriate counter on success.

- [ ] **Step 9: Fix Dashboard fake pull-to-refresh**

In `DashboardScreen.kt`, replace the fixed `delay(2000)` logic (lines 66-70) with one that waits for actual sync:

```kotlin
LaunchedEffect(isRefreshing) {
    if (isRefreshing) {
        viewModel.refresh() // This calls syncManager.fullSync()
        isRefreshing = false
    }
}
```

- [ ] **Step 10: Commit bug fixes**

```bash
git add -A && git commit -m "fix: crash guards on all startActivity calls, separate sync retry counters, real pull-to-refresh"
```

---

### Task 3: Add Forgot Password Rate Limit Prefs + i18n Keys

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

**Interfaces:**
- Produces: `AppPreferences.forgotPasswordLastSent` (Flow<Long>), `forgotPasswordDailyCount` (Flow<Int>), `setForgotPasswordLastSent(ts: Long)`, `setForgotPasswordDailyCount(count: Int)`, `getForgotPasswordDailyCount(): Int`. New i18n keys: `sign_in`, `sign_up`, `forgot_password`, `send_reset_link`, `resend_in`, `daily_limit_reached`, `attempts_remaining`, `reset_link_sent`, `back_to_sign_in`, `welcome_back`, `create_account`, `confirm_password`, `password_strength_weak`, `password_strength_medium`, `password_strength_strong`, `sign_in_to_sync`, `sign_in_to_sync_desc`, `data_saved_locally`, `or_continue_with`, `skip_for_now`, `dont_have_account`, `already_have_account`, `terms_and_conditions`, `help_and_support`, `contact_us`, `legal_and_support`, `appearance`, `preferences`, `data_and_export`, `about`, `no_browser_found`, `could_not_open_link`, `password_6_chars_min`, `email_required`, `passwords_dont_match`

- [ ] **Step 1: Add forgot password rate limit keys and flows to AppPreferences**

Add to `Keys` object after line 50:

```kotlin
val FORGOT_PASSWORD_LAST_SENT = longPreferencesKey("forgot_password_last_sent")
val FORGOT_PASSWORD_DAILY_COUNT = intPreferencesKey("forgot_password_daily_count")
val FORGOT_PASSWORD_DAILY_DATE = stringPreferencesKey("forgot_password_daily_date")
```

Add flows after line 77:

```kotlin
val forgotPasswordLastSent: Flow<Long> = context.dataStore.data.map { it[Keys.FORGOT_PASSWORD_LAST_SENT] ?: 0L }
val forgotPasswordDailyCount: Flow<Int> = context.dataStore.data.map { it[Keys.FORGOT_PASSWORD_DAILY_COUNT] ?: 0 }
```

Add setters after line 108:

```kotlin
suspend fun setForgotPasswordLastSent(ts: Long) = context.dataStore.edit { it[Keys.FORGOT_PASSWORD_LAST_SENT] = ts }
suspend fun setForgotPasswordDailyCount(count: Int) {
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    context.dataStore.edit {
        it[Keys.FORGOT_PASSWORD_DAILY_COUNT] = count
        it[Keys.FORGOT_PASSWORD_DAILY_DATE] = today
    }
}
suspend fun getForgotPasswordDailyCount(): Int {
    val prefs = context.dataStore.data.first()
    val count = prefs[Keys.FORGOT_PASSWORD_DAILY_COUNT] ?: 0
    val date = prefs[Keys.FORGOT_PASSWORD_DAILY_DATE] ?: ""
    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
    return if (date != today) 0 else count
}
```

- [ ] **Step 2: Add all new i18n keys to LocalizedString.kt**

Add to EACH of the 6 language maps (`"en"`, `"hi"`, `"es"`, `"fr"`, `"de"`, `"ja"`) before their closing `},`:

**English (`"en"`) additions:**
```kotlin
"sign_in" to "Sign In",
"sign_up" to "Sign Up",
"forgot_password" to "Forgot Password?",
"send_reset_link" to "Send Reset Link",
"resend_in" to "Resend in {time}s",
"daily_limit_reached" to "You've reached the daily limit. Try again tomorrow.",
"attempts_remaining" to "{count}/5 attempts remaining today",
"reset_link_sent" to "Reset link sent! Check your inbox",
"back_to_sign_in" to "Back to Sign In",
"welcome_back" to "Welcome back",
"create_account" to "Create Account",
"confirm_password" to "Confirm Password",
"password_strength_weak" to "Weak",
"password_strength_medium" to "Medium",
"password_strength_strong" to "Strong",
"sign_in_to_sync" to "Sign in to sync",
"sign_in_to_sync_desc" to "Your data stays on this device only. Sign in to back up to the cloud.",
"data_saved_locally" to "Don't worry, your data is saved locally",
"or_continue_with" to "or continue with",
"skip_for_now" to "Skip for now",
"dont_have_account" to "Don't have an account?",
"already_have_account" to "Already have an account?",
"terms_and_conditions" to "Terms & Conditions",
"help_and_support" to "Help & Support",
"contact_us" to "Contact Us",
"legal_and_support" to "Legal & Support",
"appearance" to "Appearance",
"preferences" to "Preferences",
"data_and_export" to "Data & Export",
"about" to "About",
"no_browser_found" to "No browser found to open this link",
"could_not_open_link" to "Could not open link",
"password_6_chars_min" to "Password (6+ chars)",
"email_required" to "Email is required",
"passwords_dont_match" to "Passwords don't match"
```

For `"hi"`, `"es"`, `"fr"`, `"de"`, `"ja"` — translate each key to the respective language. Hinglish for Hindi, standard translations for others. (Agent should look at existing pattern in LocalizedString.kt for translation style.)

- [ ] **Step 3: Commit prefs + i18n**

```bash
git add -A && git commit -m "feat: forgot password rate limit prefs + 30+ i18n keys for auth/settings"
```

---

### Task 4: Auth Screen — Sign In / Sign Up / Forgot Password

**Files:**
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt`
- Create: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt`

**Interfaces:**
- Consumes: `AuthManager.signInWithGoogle()`, `AuthManager.signInWithEmailPassword()`, `AuthManager.linkWithEmailPassword()`, `AppPreferences.forgotPasswordLastSent`, `AppPreferences.getForgotPasswordDailyCount()`, `LocalizedString.get()`
- Produces: `AuthViewModel` with `signInWithEmail()`, `signUpWithEmail()`, `sendResetLink()`, `signInWithGoogle()`, `authMode` StateFlow, `uiState` StateFlow. `AuthScreen(onAuthSuccess: () -> Unit, onSkip: () -> Unit)` composable.

- [ ] **Step 1: Create AuthViewModel.kt**

```kotlin
package com.dhanuk.debtbro.presentation.screens.auth

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.firebase.AuthManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, SIGN_UP, FORGOT_PASSWORD }

data class AuthUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val forgotPasswordSent: Boolean = false,
    val resendCooldown: Int = 0,
    val dailyAttemptsLeft: Int = 5
)

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val auth: AuthManager,
    private val prefs: AppPreferences
) : ViewModel() {

    private val _authMode = MutableStateFlow(AuthMode.SIGN_IN)
    val authMode: StateFlow<AuthMode> = _authMode

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState

    fun setMode(mode: AuthMode) {
        _authMode.value = mode
        _uiState.value = AuthUiState()
    }

    fun signInWithEmail(email: String, password: String) {
        if (email.isBlank()) { _uiState.value = _uiState.value.copy(error = "Email is required"); return }
        if (password.length < 6) { _uiState.value = _uiState.value.copy(error = "Password must be 6+ chars"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            auth.signInWithEmailPassword(email, password)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun signUpWithEmail(email: String, password: String, confirmPassword: String) {
        if (email.isBlank()) { _uiState.value = _uiState.value.copy(error = "Email is required"); return }
        if (password.length < 6) { _uiState.value = _uiState.value.copy(error = "Password must be 6+ chars"); return }
        if (password != confirmPassword) { _uiState.value = _uiState.value.copy(error = "Passwords don't match"); return }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            auth.signInWithEmailPassword(email, password)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { e ->
                    _uiState.value = _uiState.value.copy(isLoading = false, error = e.message)
                }
        }
    }

    fun signInWithGoogle(activity: Activity) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            auth.signInWithGoogle(activity)
                .onSuccess { _uiState.value = _uiState.value.copy(isLoading = false) }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun sendResetLink(email: String) {
        if (email.isBlank()) { _uiState.value = _uiState.value.copy(error = "Email is required"); return }
        viewModelScope.launch {
            val lastSent = prefs.forgotPasswordLastSent.first()
            val elapsed = System.currentTimeMillis() - lastSent
            if (elapsed < 60_000) {
                _uiState.value = _uiState.value.copy(resendCooldown = ((60_000 - elapsed) / 1000).toInt())
                return@launch
            }
            val dailyCount = prefs.getForgotPasswordDailyCount()
            if (dailyCount >= 5) {
                _uiState.value = _uiState.value.copy(error = "You've reached the daily limit. Try again tomorrow.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            auth.sendPasswordResetEmail(email)
                .onSuccess {
                    prefs.setForgotPasswordLastSent(System.currentTimeMillis())
                    prefs.setForgotPasswordDailyCount(dailyCount + 1)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        forgotPasswordSent = true,
                        dailyAttemptsLeft = 4 - dailyCount
                    )
                }
                .onFailure { e -> _uiState.value = _uiState.value.copy(isLoading = false, error = e.message) }
        }
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
```

Note: `auth.sendPasswordResetEmail()` needs to be added to `AuthManager.kt` as a thin wrapper around `FirebaseAuth.getInstance().sendPasswordResetEmail(email)`.

- [ ] **Step 2: Add sendPasswordResetEmail to AuthManager**

In `AuthManager.kt`, add after `signInWithEmailPassword()`:

```kotlin
suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
    auth.sendPasswordResetEmail(email).await()
}.onFailure { e ->
    android.util.Log.e("AuthManager", "sendPasswordResetEmail failed: ${e.message}", e)
}
```

- [ ] **Step 3: Create AuthScreen.kt (Option B: Split Hero + Form)**

The composable follows the Option B design pattern:
- Top: gradient hero with 💸🔥 emoji, "DebtBro" in primary color, tagline
- Bottom: form section with mode-dependent content
- Mode toggles via state, not navigation

Key composable structure:
```kotlin
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    onAuthSuccess: () -> Unit,
    onSkip: () -> Unit,
    viewModel: AuthViewModel = hiltViewModel()
) {
    val mode by viewModel.authMode.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val extra = LocalExtraColors.current

    // Observe Firebase auth state to detect successful sign-in
    LaunchedEffect(Unit) {
        authManager.authStateFlow().collect { user ->
            if (user != null) onAuthSuccess()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Hero section
        Box(modifier = Modifier.fillMaxWidth().weight(0.4f).background(
            Brush.verticalGradient(colors = listOf(Color(0xFF0D0D0D), Color(0xFF002920), Color(0xFF004D3D)))
        ), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("💸🔥", fontSize = 44.sp)
                Spacer(Modifier.height(6.dp))
                Text("DebtBro", color = MaterialTheme.colorScheme.primary, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your debts won't forget you 😈", color = extra.subtitleGray, fontSize = 11.sp)
            }
        }
        // Form section
        Column(modifier = Modifier.fillMaxWidth().weight(0.6f).padding(20.dp, 16.dp)) {
            // Mode-dependent title
            Text(when(mode) { SIGN_UP -> "Create Account" FORGOT_PASSWORD -> "Reset Password" else -> "Welcome back" }, color = MaterialTheme.colorScheme.onSurface, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))

            // Email input
            OutlinedTextField(... emoji prefix 📧)

            // Password input (not for FORGOT_PASSWORD)
            if (mode != FORGOT_PASSWORD) {
                OutlinedTextField(... emoji prefix 🔒, "Forgot?" inline trailing on SIGN_IN)
            }

            // Confirm Password (SIGN_UP only)
            if (mode == SIGN_UP) {
                OutlinedTextField(...)
                // Strength bar
                Row { repeat(4) { Box(modifier = Modifier.weight(1f).height(3.dp).background(strengthColor)) } }
            }

            // Rate limit info (FORGOT_PASSWORD only)
            if (mode == FORGOT_PASSWORD) { ... }

            // Main action button
            Button(onClick = { /* mode-dependent call */ }, modifier = Modifier.fillMaxWidth()) {
                Text(when(mode) { SIGN_IN -> "Sign In" SIGN_UP -> "Sign Up" FORGOT_PASSWORD -> "Send Reset Link" })
            }

            // Divider: "or continue with"
            // Google outlined button
            // Bottom toggle links + Skip
        }
    }
}
```

- [ ] **Step 4: Commit auth screen files**

```bash
git add -A && git commit -m "feat: auth screen (sign in/sign up/forgot password) with Option B design"
```

---

### Task 5: Auth Screen Navigation + Auth Popup Integration

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/Screen.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/navigation/NavGraph.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt` (navigate to Auth on tap)
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtBottomSheet.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailScreen.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt`

**Interfaces:**
- Consumes: `AuthScreen(onAuthSuccess, onSkip)` from Task 4, `AuthManager.isSignedIn()` to check auth
- Produces: `Screen.Auth` route. Each ViewModel has `showAuthPrompt: StateFlow<Boolean>`. Each Screen observes it and shows dialog. `onNavigateToAuth: () -> Unit` callback passed to screens.

- [ ] **Step 1: Add Auth route to Screen.kt**

```kotlin
object Auth : Screen("auth")
```

- [ ] **Step 2: Add Auth composable to NavGraph.kt**

After Settings composable (line 264), add:

```kotlin
composable(Screen.Auth.route) {
    AuthScreen(
        onAuthSuccess = { navController.popBackStack() },
        onSkip = { navController.popBackStack() }
    )
}
```

- [ ] **Step 3: Add auth prompt flow to ViewModels**

In `AddDebtViewModel.kt`, add:

```kotlin
private val _showAuthPrompt = MutableStateFlow(false)
val showAuthPrompt: StateFlow<Boolean> = _showAuthPrompt

// In saveDebt(), after checking authManager.getCurrentUser() == null:
if (authManager.getCurrentUser() == null) {
    _showAuthPrompt.value = true
    // Still save locally
}
```

Same pattern for:
- `DebtDetailViewModel.kt` — in `addPayment()`, `markSettled()`, `deleteDebt()`
- `SplitViewModel.kt` — in `createSplit()`

Add `dismissAuthPrompt()` function to each:

```kotlin
fun dismissAuthPrompt() { _showAuthPrompt.value = false }
```

- [ ] **Step 4: Add auth prompt dialog to screens**

In `AddDebtBottomSheet.kt`, `DebtDetailScreen.kt`, `SplitScreen.kt`, add:

```kotlin
val showAuthPrompt by viewModel.showAuthPrompt.collectAsStateWithLifecycle()

if (showAuthPrompt) {
    AlertDialog(
        onDismissRequest = { viewModel.dismissAuthPrompt() },
        title = { Text(LocalizedString.get("sign_in_to_sync")) },
        text = { Text(LocalizedString.get("sign_in_to_sync_desc")) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.dismissAuthPrompt()
                onNavigateToAuth()
            }) { Text(LocalizedString.get("sign_in"), color = MaterialTheme.colorScheme.primary) }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.dismissAuthPrompt() }) {
                Text(LocalizedString.get("cancel"), color = extra.subtitleGray)
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    )
}
```

Pass `onNavigateToAuth: () -> Unit` callback through NavGraph from each screen's composable call.

- [ ] **Step 5: Update Settings GoogleSignInCard tap to navigate to AuthScreen**

In `SettingsScreen.kt`, the `onSignIn` callback should navigate:

```kotlin
onSignIn = { navController.navigate(Screen.Auth.route) }
```

This requires passing `navController` (or a lambda) to SettingsScreen.

- [ ] **Step 6: Commit auth navigation + popup**

```bash
git add -A && git commit -m "feat: auth screen navigation route + auth-required popup on sync actions"
```

---

### Task 6: Settings Screen Redesign

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt` (major rewrite)

**Interfaces:**
- Consumes: `BuildConfig.PRIVACY_POLICY_URL`, `BuildConfig.TERMS_OF_SERVICE_URL`, `BuildConfig.HELP_URL`, `BuildConfig.ACCOUNT_DELETION_URL`, `openUrl()` from Task 2, `LocalizedString.get()` keys from Task 3, `MaterialTheme.colorScheme.primary` from Task 1

- [ ] **Step 1: Rewrite SettingsScreen with new section layout**

Replace the current LazyColumn with the new grouped section layout:

Sections (top to bottom):
1. **ACCOUNT** — Card with user info or "Tap to sign in & sync" (navigate to AuthScreen)
2. **APPEARANCE** — Card with theme chips (Dark/Light/System with emoji icons)
3. **PREFERENCES** — Card with rows: Display Name, Currency, Roast Level, Language, Notifications toggle
4. **DATA & EXPORT** — Card with rows: Export CSV, Clear Settled Debts
5. **LEGAL & SUPPORT** — Card with rows: Privacy Policy (↗), Terms & Conditions (↗), Help & Support (↗), Contact Us (shows email), Delete Account (red text)
6. **ABOUT** — Small card: "DebtBro v1.0.0" + "Made with 💚 in India"

Each row: `Row(modifier = Modifier.fillMaxWidth().clickable { ... }.padding(horizontal = 14.dp, vertical = 12.dp))` with emoji prefix, label text, and value/arrow suffix.

All policy links use `openUrl(context, BuildConfig.XXX_URL)`.
Contact Us uses `openUrl(context, "mailto:support@dhanuksoftwares.com")`.

- [ ] **Step 2: Commit settings redesign**

```bash
git add -A && git commit -m "feat: settings screen redesign with Legal & Support section, all policy links, grouped layout"
```

---

### Task 7: Build Verification + Push

**Files:**
- None (verification only)

- [ ] **Step 1: Build debug APK to verify no compilation errors**

```bash
./gradlew assembleDebug 2>&1 | tail -20
```

Expected: `BUILD SUCCESSFUL`

- [ ] **Step 2: If build fails, fix compilation errors**

Common issues to watch for:
- `MaterialTheme.colorScheme.primary` used outside `@Composable` context
- Missing imports for `Brush`, `ActivityResultContracts`, etc.
- `openUrl` not imported in files that use it
- New BuildConfig fields not generated yet (clean build may be needed)

- [ ] **Step 3: Push all commits to remote**

```bash
git push origin fix-google-signin-and-export-crash-15964286147323028907
```

- [ ] **Step 4: Monitor CI build**

```bash
gh run list --limit 1 --branch fix-google-signin-and-export-crash-15964286147323028907
```

Wait for green status.

- [ ] **Step 5: Final commit message if needed**

```bash
git add -A && git commit -m "chore: fix remaining compilation issues from UI/UX redesign"
```
