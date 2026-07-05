# Design — Split Auth into Sign Up + Sign In Screens

**Date:** 2026-07-05
**Repo:** DebtPayoff Pro (Android · Kotlin · Jetpack Compose)
**Branch:** `fix-google-signin-and-export-crash-15964286147323028907` (feature work will go on a new branch)
**Status:** Approved by user, awaiting implementation plan

## Motivation

The current `AuthScreen.kt` is a single 448-line composable that switches between three modes
(`SIGN_IN`, `SIGN_UP`, `FORGOT_PASSWORD`) via a `mode` state in a shared `AuthViewModel`.
This causes:
- Shared mutable state across modes (e.g., `passwordStrength` leaks from sign-up to sign-in)
- One screen doing too many things; hard to test, hard to evolve
- No way to collect a display name at sign-up without a separate gate later
- A redundant Onboarding name-input step for email/password users (Google users skip it via `displayName`)
- A stale "30-minute grace" text in the re-login alert that contradicts the live legal pages (which now say 24 hours — already updated on `/htdocs/DebtBro/Privacy-Policy.html`, `Delete-Account.html`, `Help-and-Support.html`)

We split auth into two purpose-focused screens, add a name field on sign-up, simplify the onboarding
gate, and align the in-app grace alert text with the published 24-hour policy.

## Decisions (from brainstorming)

| # | Decision | Rationale |
|---|---|---|
| 1 | Include "Display name" field on Sign Up (3-30 char minimum) | `AuthScreen` (the standalone re-sign-in screen for returning users or post-sign-out users) doesn't collect a name today. Adding a name field to `SignUpScreen` lets users who reach it (e.g., from a Settings "Sign out → create new account" path) sign up properly. |
| 2 | DO NOT modify OnboardingScreen — its existing Page5aName + Page6Auth flow is correct for first-time users | **Correction during spec self-review**: OnboardingScreen *already* collects name at Page5a (uses `NAME_REGEX`, has `name_min_length_error`) AND does Google/Email auth at Page6Auth inline. Removing Page5aName would break first-time onboarding. The Onboarding flow is unaffected by this PR. |
| 3 | On Sign Up success: stay Firebase-signed-in, show English-only verify alert, navigate to Dashboard | Matches existing pattern; uses `isEmailVerified` (already wired). Google users skip alert (auto-verified). |
| 4 | Two separate NavGraph routes: `Screen.SignUp` and `Screen.SignIn` (delete `Screen.Auth`) | Clean separation; explicit navigation intent. |
| 5 | Approach B architecture: two routes × two screens × two ViewModels | Best state isolation; no leak risk. Small validation utility duplication acceptable. |
| 6 | Generate HTML mockups via OpenDesign `start_run` (Material design system) before Compose translation | Visual fidelity check first, then translate to Compose using existing theme tokens. |
| 7 | Update stale `GRACE_PERIOD_MS` in `AccountDeletionWorker.kt:73` from 30 min → 24 h | Match the live Privacy Policy, Delete-Account, and Help-and-Support pages. |

## Architecture

### Approach B — Two routes, two files, two ViewModels

Files:

```
presentation/screens/auth/
├── AuthScreen.kt          → DELETE
├── AuthViewModel.kt       → DELETE
├── SignUpScreen.kt        → NEW (composable + private helpers)
├── SignUpViewModel.kt     → NEW
├── SignInScreen.kt        → NEW
├── SignInViewModel.kt     → NEW
└── authSharedUi.kt        → NEW (extracted composables + validation helpers)
```

`authSharedUi.kt` holds composables reused by both screens — `AuthHero`, `GoogleGlyph`,
`authFieldColors`, `PasswordStrengthBar` — and shared validation utilities
(`isEmailValid`, `isNameValid`, name/password constants). No `@Composable` is duplicated across
the two screens.

### Navigation changes

```diff
// Screen.kt
- object Auth : Screen("auth")
+ object SignUp : Screen("sign_up")
+ object SignIn : Screen("sign_in")
```

```diff
// NavGraph.kt
- composable(Screen.Auth.route) {
-     AuthScreen(onAuthComplete = ..., onSkip = ...)
- }
+ composable(Screen.SignUp.route) {
+     SignUpScreen(
+         onAuthComplete = { navController.popBackStack() },
+         onNavigateToSignIn = { navController.navigate(Screen.SignIn.route) { launchSingleTop = true } },
+         onSkip = { navController.popBackStack() }
+     )
+ }
+ composable(Screen.SignIn.route) {
+     SignInScreen(
+         onAuthComplete = { navController.popBackStack() },
+         onNavigateToSignUp = { navController.navigate(Screen.SignUp.route) { launchSingleTop = true } },
+         onSkip = { navController.popBackStack() }
+     )
+ }
```

All existing `navController.navigate(Screen.Auth.route)` call sites change:
- `NavGraph.kt:279` `SplitScreen.onAuthRequired` → `Screen.SignIn.route`
- `NavGraph.kt:289` `DebtDetailScreen.onAuthRequired` → `Screen.SignIn.route`
- `NavGraph.kt:305` `AddDebtBottomSheet.onSignInRequired` → `Screen.SignIn.route`
- (the `composable(Screen.Auth.route)` block on line 292 is replaced, not migrated)

Onboarding's own `Page6Auth` does its own inline auth — **NO nav-graph change to Onboarding**.

### Onboarding simplification

`OnboardingScreen.kt`: **NO CHANGES**. The existing flow is correct:
- Page 1: Welcome + language pick
- Page 2-4: Marketing slides  
- Page 5a: Name input (uses `NAME_REGEX`, validates min 3 chars, has `name_required`/`name_min_length_error` strings already in `LocalizedString.kt`)
- Page 6: `Page6Auth` inline Google/Email sign-in/sign-up (carries name from Page5a)

This PR does **NOT** touch `OnboardingScreen.kt` or `OnboardingViewModel.kt`. The two new screens are for the standalone-returning-user auth flow, which is reached via NavGraph from `SplitScreen`, `DebtDetailScreen`, `AddDebtBottomSheet` (all existing "auth required" call sites).

## Detailed designs

### `authSharedUi.kt`

Public exports:

```kotlin
enum class PasswordStrength { WEAK, MEDIUM, STRONG }

const val MIN_NAME_LENGTH = 3
const val MAX_NAME_LENGTH = 30
const val MIN_PASSWORD_LENGTH = 6

fun isEmailValid(s: String): Boolean =
    s.isNotBlank() && android.util.Patterns.EMAIL_ADDRESS.matcher(s).matches()

fun isNameValid(s: String): Boolean =
    s.trim().length in MIN_NAME_LENGTH..MAX_NAME_LENGTH

fun computePasswordStrength(pwd: String): PasswordStrength   // moved from AuthViewModel

@Composable
fun authFieldColors(): OutlinedTextFieldDefaults.colors(...)

@Composable
fun AuthHero(title: String)                                    // 220dp gradient, "🤑" emoji, title, "DebtPayoff Pro"

@Composable
fun GoogleGlyph()                                             // G in white circle, 4-color Google logo

@Composable
fun PasswordStrengthBar(strength: PasswordStrength)           // 3-segment bar, weak/medium/strong colors
```

These are extracted VERBATIM from `AuthScreen.kt:354-448` and `AuthViewModel.kt:109-119` —
no behavior change, just relocation.

### Sign Up

#### `SignUpUiState`

```kotlin
data class SignUpUiState(
    val name: String = "",
    val email: String = "",
    val password: String = "",
    val confirmPassword: String = "",
    val passwordStrength: PasswordStrength = PasswordStrength.WEAK,
    val isBusy: Boolean = false,
    val errorRes: String? = null
)
```

ViewModel exposes `state: StateFlow<SignUpUiState>`, `signedIn: StateFlow<Boolean>`,
`showVerifyAlert: StateFlow<Boolean>`, `showGraceReLoginAlert: StateFlow<Boolean>`.

#### `SignUpViewModel` behaviors

```kotlin
fun setName(v: String)            // trims, caps at MAX_NAME_LENGTH, clears errorRes
fun setEmail(v: String)
fun setPassword(v: String)         // recompute strength
fun setConfirmPassword(v: String)
fun signUpWithGoogle(activity: Activity)
fun submit()                        // validates + calls auth.signUpWithEmailPassword
fun dismissVerifyAlert()
fun cancelDeletionViaGraceAlert()
fun signOutFromGraceAlert()
```

`submit()` validation (in order; first failure wins):
1. `isNameValid(name)` — else `errorRes = "name_required"`
2. `isEmailValid(email)` — else `errorRes = "email_required"`
3. `password.length >= 6` — else `errorRes = "password_6_chars_min"`
4. `password == confirmPassword` — else `errorRes = "passwords_dont_match"`

On all-pass: set `isBusy = true`, call `auth.signUpWithEmailPassword(email, password)`.
- `onSuccess` → `_showVerifyAlert.value = true`, then `onAuthSuccess(...)` which:
  - Calls `auth.updateProfile(displayName = name, photoUri = null)` (defined in `AuthManager.kt:246`) to write Firebase Auth profile. Returns `Result<Unit>`; `.onFailure` is logged inside `AuthManager` so we swallow it here.
  - Calls `prefs.setSignedIn(provider = "email", name = name, email = email, photo = "")` (also serves as fallback if `updateProfile` fails — the app-side prefs always carry the user-typed name)
  - Starts real-time sync + full sync (wrapped in `runCatching`)
  - Calls `checkGracePeriodOnSignIn(uid)` (wrapped in `runCatching`)
  - Sets `_signedIn.value = true`
- `onFailure` → `errorRes = e.message ?: "Sign-up failed"`, `isBusy = false`

Google sign-up (`signUpWithGoogle`) bypasses the verify alert entirely; name is taken from
`user.displayName` (already populated by Google).

#### Screen layout (top to bottom)

1. `AuthHero("create_account")`
2. Google sign-up button (`OutlinedButton`, 52dp, `UITokens.ShapeLarge`)
3. `google_signin_unavailable` subtitle if `activity == null`
4. Divider row (`or_continue_with`)
5. Display name field — `OutlinedTextField`, label `display_name`, single line, capitalization words, ImeAction.Next
6. Email field — `OutlinedTextField`, Keyboard Email, ImeAction.Next
7. Password field — `OutlinedTextField`, `PasswordVisualTransformation`, ImeAction.Next
8. `PasswordStrengthBar` — visible when `password.isNotEmpty()`
9. Confirm password field — `PasswordVisualTransformation`, ImeAction.Done; `isError` if `confirmPassword.isNotEmpty() && confirmPassword != password`
10. Error text (red, 13sp)
11. Sign Up button — `Button`, 52dp, PrimaryGreen, shows `CircularProgressIndicator` if `isBusy`
    - **Enable rule**: `!isBusy && name.trim().length in 3..30 && email.isNotBlank() && password.length >= 6 && password == confirmPassword`
12. Bottom link: `"already_have_account"` as `TextButton`, navigates to `Screen.SignIn`

#### Post-sign-up verify alert (English-only)

`AlertDialog` shown when `showVerifyAlert == true`:
- Title: `"Please verify your email"`
- Body: `"Please verify your email before adding debts or splits. Check your inbox (and spam folder)."`
- Confirm button: `"Got it"` → calls `viewModel.dismissVerifyAlert()`. After dismissal,
  `LaunchedEffect(signedIn)` observes `signedIn == true && !showVerifyAlert` and calls `onAuthComplete()`.

Google users never trigger this alert (their `isEmailVerified == true` from Firebase Auth).

### Sign In

#### `SignInUiState`

```kotlin
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
```

ViewModel exposes `state`, `signedIn`, `showGraceReLoginAlert`.

#### `SignInViewModel` behaviors

```kotlin
fun setEmail(v: String)
fun setPassword(v: String)
fun setMode(mode: SignInMode)             // cancels countdown, clears error
fun signInWithGoogle(activity: Activity)
fun submit()                              // SIGN_IN: signIn; FORGOT_PASSWORD: sendResetEmail
fun dismissGraceReLoginAlert()
fun cancelDeletionViaGraceAlert()
fun signOutFromGraceAlert()
suspend fun dailyAttemptsRemaining(): Int  // for the FORGOT_PASSWORD attempts display
```

Forgot-password flow (moved here from `AuthViewModel.kt:192-243`):
- Daily cap: `MAX_DAILY_RESETS = 5`
- Cooldown: `RESEND_COOLDOWN_SECONDS = 60`
- Calls `auth.sendPasswordResetEmail(email)`, records timestamp and count to `AppPreferences`

Same `signedIn` / `showGraceReLoginAlert` flow as before — grace alert logic moves from `AuthScreen` to `SignInScreen`.

#### Screen layout (top to bottom)

1. `AuthHero("welcome_back")`
2. Google sign-in button (same style as sign-up, different copy: `"sign_in_google"`)
3. `google_signin_unavailable` subtitle if `activity == null`
4. Divider row (`or_continue_with`)
5. Email field
6. Password field (hidden in `FORGOT_PASSWORD` mode)
7. Forgot password row (only in `SIGN_IN` mode): `TextButton` → `setMode(FORGOT_PASSWORD)`
8. Error text
9. Reset-link banner (green block, only in `FORGOT_PASSWORD` mode and `resetLinkSent == true`)
10. Submit button:
    - `SIGN_IN`: text `"sign_in"`, enabled `!isBusy && email.isNotBlank() && password.length >= 6`
    - `FORGOT_PASSWORD`: text `"send_reset_link"` or `"resend_in {time}"`, shows spinner when busy
11. Daily attempts remaining (only `FORGOT_PASSWORD`): `"attempts_remaining"` with `{count}`
12. Bottom link: `"dont_have_account"` as `TextButton`, navigates to `Screen.SignUp`
13. Divider
14. `"sign_in_to_sync_desc"` explanatory text
15. `TextButton` `"skip_for_now"` → `onSkip`

#### Grace re-login alert — UPDATED TEXT

The existing `AlertDialog` (currently shows "30-minute grace window") is moved here and the copy
is updated to reflect the legal pages' new 24-hour grace:

- Title: `"Account deletion in progress"`
- Body: `"You're within the 24-hour account deletion grace window. Do you want to come back?"`
- Confirm: `"Log in and reactivate"` → `cancelDeletionViaGraceAlert()`
- Dismiss: `"Cancel"` → `signOutFromGraceAlert()`

This is hardcoded English intentionally — it is a destructive-action guard, not routine UX.

## Worker change

### `AccountDeletionWorker.kt:73`

```diff
- private const val GRACE_PERIOD_MS = 30L * 60L * 1000L           // 30 minutes — STALE
+ private const val GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L     // 24 hours — matches live legal pages
```

This is the ONLY code change needed for the grace alignment. The legal pages already reflect 24h.

## Visual mockup pipeline (via OpenDesign MCP)

Before writing any Kotlin, generate HTML mockups to validate the visual layout:

1. `open-design_create_project("DebtBro Auth Screens")`
2. `open-design_start_run` with a prompt describing both `sign-up.html` and `sign-in.html`,
   specifying:
   - Design system: Material (closest to Material3 Compose)
   - Palette: dark-first background `#0D0D0D`, card `#1E1E1E`, primary `#00E5A0`, danger `#F44336`, amber `#FFB300`
   - 390px mobile width (iPhone 14 viewport)
   - Same `AuthHero` gradient: PrimaryGreen → background
   - 4 mockup variants:
     - `sign-up-empty` (Sign Up button disabled)
     - `sign-up-filled` (all 4 fields valid, button enabled)
     - `sign-in-empty`
     - `sign-in-busy` (spinner on submit button)
3. Poll `open-design_get_run(runId)` every 30-60s (expected 5-30 min)
4. Once succeeded, `open-design_get_artifact()` to pull generated files
5. `describe_image_describe_image` to visually verify color/spacing against app theme
6. Translate visual design to Jetpack Compose, mapping to existing tokens (`UITokens.ShapeLarge`,
   `MaterialTheme.colorScheme.primary`, `LocalExtraColors`)

## Strings added to `LocalizedString.kt`

Will grep implementation-time to verify which already exist. **Already confirmed during spec self-review:**

- `name_required` → already exists (LocalizedString.kt:97) — `"Please enter your name"`
- `name_min_length_error` → already exists (LocalizedString.kt:98) — `"Name must be at least 3 characters"`

**New keys required** (used by SignUpScreen/SignInScreen new copy):

- `display_name` — for the name field label on SignUp (the existing `your_name_label` key `"Your name"` could be reused; pick alphabet during implementation)
- `sign_up_with_google` — new (current `sign_in_google` doesn't fit Sign Up CTA)
- `please_verify_email` — verify alert title (English-only copy, NOT translated)
- `please_verify_email_desc` — verify alert body (English-only)
- `got_it` — verify alert dismiss button

All 26 other reused keys confirmed present in `LocalizedString.kt` (verified via grep during spec self-review):

`welcome_back`, `create_account`, `sign_in`, `sign_up`, `already_have_account`, `dont_have_account`, `forgot_password`, `email`, `password_6_chars_min`, `confirm_password`, `passwords_dont_match`, `email_required`, `reset_link_sent`, `send_reset_link`, `resend_in`, `attempts_remaining`, `daily_limit_reached`, `back_to_sign_in`, `or_continue_with`, `sign_in_google`, `google_signin_unavailable`, `sign_in_to_sync_desc`, `skip_for_now`, `password_strength_weak`, `password_strength_medium`, `password_strength_strong`.

If a new key turns out to overlap with an existing one (e.g. reusing `your_name_label` for the Sign Up name field), prefer the existing key. The 5 new keys above will be added to all 25 supported languages using the existing pattern at the top of each language block.

## Error handling

- All Firebase calls wrapped in `.fold(onSuccess, onFailure)` — same pattern as current `AuthViewModel`
- `runCatching` around sync calls so failures don't break sign-in completion
- `errorRes` uses string keys (resolved via `LocalizedString.get`) for all user-facing errors except:
  - The verify alert (English-only, intentional — destructive-action UX clarity)
  - The grace alert (English-only, on `SignInScreen`, same intention)

## Testing

The project has zero test files today. This PR does NOT introduce tests (per AGENTS.md "No tests"):
- Static verification: type errors fail Gradle compilation in CI
- Visual verification: HTML mockup review by user before implementation
- Behavioral verification: GitHub Actions CI green on `assembleDebug` + `assembleRelease`

If a future agent introduces tests, the Approach B architecture (isolated ViewModels per screen)
supports unit testing each independently without state cross-contamination.

## Verification commands

Per `AGENTS.md` and the user's "Never build locally" mandate:

```bash
git checkout -b feat/auth-screens-split
git add -A && git commit -m "feat(auth): split AuthScreen into separate SignUp + SignIn screens

- Two NavGraph routes (Screen.SignUp, Screen.SignIn); remove Screen.Auth
- Two ViewModels (SignUpViewModel, SignInViewModel) for state isolation
- Add Display name field on Sign Up (3-30 char, min 3 to enable Sign Up button)
- Show English-only email-verification alert on sign-up success
- Move forgot-password flow from AuthViewModel to SignInViewModel
- Update grace-period text from '30-minute' to '24-hour' to match live legal pages
- Update AccountDeletionWorker.GRACE_PERIOD_MS from 30 min to 24 h
- Extract shared composables to authSharedUi.kt (AuthHero, GoogleGlyph, etc.)
- OnboardingScreen.kt is UNCHANGED (existing Page5aName + Page6Auth inline flow stays)

Refs: docs/superpowers/specs/2026-07-05-auth-screens-split-design.md" \
&& git push -u origin feat/auth-screens-split
```

Then watch CI:
```bash
gh run watch
```

## Out of scope for this PR

- Visual redesign of `AuthHero` (sticking with current gradient + emoji pattern — keep parity with rest of app)
- Password-less email link auth
- Biometric auth
- App Check / reCAPTCHA enforcement
- Updating other screens' UX (only file touch-ups for nav target changes)

Each of these can be a follow-up PR if needed.

## Risk assessment

| Risk | Likelihood | Mitigation |
|---|---|---|
| Removing `OnboardingScreen` name step breaks another part of the codebase that expected gate | Low — grep will catch any uses of the name field beyond sign-up during implementation | `grep -rn "name" app/src/main/java/com/dhanuk/debtbro/` verification |
| New routes break `navController.navigate(Screen.Auth.route)` call sites | Low — mechanical swap; grep covers all call sites | All call sites enumerated: `NavGraph.kt` lines 279 / 289 / 292 (the composable block itself) / 305 — three migratory, one self-replacing. OnboardingScreen does NOT use `Screen.Auth.route`. |
| Verify alert text string is missing in some languages | High — only English declared in the spec | Verify alert is English-ONLY by design — no translation needed |
| `auth.updateProfile` may fail (network/reauth issue) | Low — `updateProfile` is allowed for fresh sign-ups | `.onFailure` is logged inside `AuthManager`; we still call `prefs.setSignedIn(name = name, ...)` so the app-side copy always carries the user-typed name |
| `AccountDeletionWorker.GRACE_PERIOD_MS` change silently lengthens the window | Expected behavior — matches legal pages that already shipped | Document in PR description; existing user XOR operations proceed as before |
| OpenDesign `start_run` takes longer than 30 min | Low — typical 5-30 min per current daemon logs | If timeout, retry once; if still failing, fall back to direct Compose implementation per user's prior decision tree |

## Acceptance criteria

Implementation is complete when ALL of the following are true:

1. `AuthScreen.kt` and `AuthViewModel.kt` are deleted
2. `SignUpScreen.kt`, `SignUpViewModel.kt`, `SignInScreen.kt`, `SignInViewModel.kt`, `authSharedUi.kt` exist
3. `Screen.kt` has `SignUp` + `SignIn` and no longer has `Auth`
4. `NavGraph.kt` hosts both new composables
5. `AccountDeletionWorker.kt:73` shows `24L * 60L * 60L * 1000L`
6. `OnboardingScreen.kt` and `OnboardingViewModel.kt` are unchanged (verifiable via `git diff`)
7. Grace-period alert text in `SignInScreen.kt` says "24-hour"
8. Sign Up button cannot be enabled with name < 3 chars
9. Successful email/password Sign Up triggers the English-only verify alert
10. Successful Google Sign In/Up skips the verify alert
11. All existing `navController.navigate(Screen.Auth.route)` call sites in `NavGraph.kt` are updated
12. GitHub Actions CI run on the feature branch is GREEN (assembleDebug + assembleRelease both pass)
13. The previously-shipped grace-period behavior remains intact: signing in within the 24-hour window cancels the pending deletion

## References

- Live Privacy Policy: `https://dhanuk.page.gd/DebtBro/Privacy-Policy.html` (24-hour grace text — already updated)
- Live Delete-Account: `https://dhanuk.page.gd/DebtBro/Delete-Account.html` (24-hour grace only, no "Delete Now")
- Live Help-and-Support: `https://dhanuk.page.gd/DebtBro/Help-and-Support.html` (URL fixed, 24h grace only)
- `AGENTS.md` — project conventions (Hilt, MVVM, dark-first theme, never build locally)
- Brainstorming conversation: 2026-07-05 clarifying questions and design sections 1-5
