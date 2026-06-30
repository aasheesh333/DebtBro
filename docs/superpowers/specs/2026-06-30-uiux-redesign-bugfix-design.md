# DebtBro UI/UX Redesign + Bug Fix Spec

**Date:** 2026-06-30
**Branch:** `fix-google-signin-and-export-crash-15964286147323028907`
**Design Choice:** Sign In = Option B (Split Hero + Form)

---

## 1. Sign In / Sign Up / Forgot Password Screens

### Architecture
- New nav route: `Screen.Auth` in `Screen.kt`
- New files: `presentation/screens/auth/AuthScreen.kt` + `AuthViewModel.kt`
- Auth screen has 3 modes: `SIGN_IN`, `SIGN_UP`, `FORGOT_PASSWORD`
- Toggle between modes without navigation (state-driven UI swap)

### Sign In Screen (Option B: Split Hero + Form)
- **Top hero section**: Dark-to-green gradient (`#0D0D0D → #002920 → #004D3D`), emoji 💸🔥, "DebtBro" in `#00E5A0`, tagline "Your debts won't forget you 😈"
- **Form section**:
  - Title "Welcome back" / "Create Account" / "Reset Password" (mode-dependent)
  - Email input with 📧 prefix icon
  - Password input with 🔒 prefix icon + "Forgot?" link inline (only on SIGN_IN)
  - Confirm Password input (only on SIGN_UP)
  - Password strength indicator bar (only on SIGN_UP): weak=red, medium=yellow, strong=green (4 segments)
  - "Sign In" / "Sign Up" / "Send Reset Link" button in `#00E5A0`
  - Divider: "or continue with"
  - "Google" outlined button with Google logo
  - Bottom: "Sign Up" / "Sign In" toggle link + "Skip →" link
- **Skip**: Navigates to Dashboard, user stays offline
- **Google Sign-In**: Uses existing `AuthManager.signInWithGoogle(activity)`, auto-signs in on success

### Sign Up Screen
- Same layout as Sign In, mode = SIGN_UP
- Add "Confirm Password" field
- Password strength bar below password fields
- Remove "Forgot?" link
- Change welcome text to "Create Account"
- Button text: "Sign Up"
- Bottom toggle: "Already have account? Sign In"

### Forgot Password Screen
- Mode = FORGOT_PASSWORD
- Email input only
- "Send Reset Link" button
- Rate limiting UI:
  - 60 second cooldown between resends — button text changes to "Resend in Xs"
  - 5 attempts per day max — show "3/5 attempts remaining today"
  - Daily limit reached: "You've reached the daily limit. Try again tomorrow."
  - Track via `AppPreferences`: `forgotPasswordLastSent` (timestamp) + `forgotPasswordDailyCount` (int, reset at midnight)
- Success state: green banner "✅ Reset link sent! Check your inbox"
- "← Back to Sign In" link

### Auth Required Popup
- Shown when user performs sync-requiring action while not signed in
- **Trigger points**: AddDebtViewModel.saveDebt(), DebtDetailViewModel.addPayment(), markSettled(), deleteDebt(), SplitViewModel.createSplit()
- **Dialog design**: Dark AlertDialog
  - Icon: ☁️🚫
  - Title: "Sign in to sync"
  - Message: "Your data stays on this device only. Sign in to back up to the cloud."
  - "Sign In" button (PrimaryGreen) → navigates to AuthScreen
  - "Cancel" (gray) → dismisses, data saved locally only
  - Small hint: "Don't worry, your data is saved locally"
- Implementation: `StateFlow<Boolean>` in each ViewModel, observed by Screen composables
- **Only show once per session per action type** (track via `hasShownAuthPrompt` set in DataStore)

---

## 2. Settings Screen Redesign

### New Section Layout
1. **ACCOUNT** — User card (signed in state shows avatar+name+email, signed out shows "Tap to sign in & sync")
2. **APPEARANCE** — Theme selector (Dark / Light / System) with emoji icons
3. **PREFERENCES** — Display Name, Currency, Roast Level, Language, Notifications (toggle rows with emoji icons)
4. **DATA & EXPORT** — Export CSV, Clear Settled Debts
5. **LEGAL & SUPPORT** (NEW section):
   - Privacy Policy → `https://dhanuk.page.gd/DebtBro/Privacy-Policy.html`
   - Terms & Conditions → `https://dhanuk.page.gd/DebtBro/Terms-and-Conditions.html`
   - Help & Support → `https://dhanuk.page.gd/DebtBro/Help-and-Support.html`
   - Contact Us → `mailto:support@dhanuksoftwares.com`
   - Delete Account → existing grace period flow
6. **ABOUT** — Version + "Made with 💚 in India"

### URL Handling Fix
- Add `BuildConfig` fields for all 4 URLs in `build.gradle.kts`:
  - `PRIVACY_POLICY_URL` (already exists, from env/local.properties)
  - `TERMS_OF_SERVICE_URL` = `"https://dhanuk.page.gd/DebtBro/Terms-and-Conditions.html"` (hardcoded)
  - `HELP_URL` = `"https://dhanuk.page.gd/DebtBro/Help-and-Support.html"` (hardcoded)
  - `ACCOUNT_DELETION_URL` (already exists)
- Wrap ALL `startActivity(ACTION_VIEW)` calls in try-catch for `ActivityNotFoundException`
- Also fix: ShareUtils.shareFile, HtmlExporter.shareImage, CanvasExporter.shareDebtCard, SettingsViewModel CSV export — all need try-catch

### Row Style
- Each setting row: emoji icon (left) + label + value/right arrow + optional bottom border (#222)
- Sections separated by 16dp gaps, no section dividers
- Card background: `#1A1A1A`, border-radius 14dp

---

## 3. Theme Color Fix

### Problem
- `Theme.kt` light scheme uses `primary = PrimaryGreen` (#00E5A0) — too bright on white bg
- `BrandPrimaryLight` (#00A86B) is defined in `Color.kt` but completely unused
- 80+ direct `PrimaryGreen` references bypass theme system
- 30+ `Color.Black` / `Color.White` hardcoded — invisible in wrong theme

### Fix Plan
1. `Theme.kt` light scheme: `primary = BrandPrimaryLight` (#00A86B)
2. `Theme.kt` light scheme: `onPrimary = Color.White` (was `Color.Black`)
3. Replace all `PrimaryGreen` direct refs → `MaterialTheme.colorScheme.primary` (13 files)
4. Replace `Color.Black` on primary-colored surfaces → `MaterialTheme.colorScheme.onPrimary`
5. Replace `Color.White` text → `MaterialTheme.colorScheme.onSurface` or contextual token
6. Confetti colors: use `MaterialTheme.colorScheme.primary` instead of `Color(0xFF00E5A0)`
7. WhatsApp green (#25D366): keep hardcoded (brand color, not theme-dependent)

### Files Affected (PrimaryGreen → theme token)
- NavGraph.kt, DebtCard.kt, GoogleSignInCard.kt, LanguageSelectorGrid.kt
- AddDebtBottomSheet.kt, SettingsScreen.kt, DebtDetailScreen.kt
- DashboardScreen.kt, SplitScreen.kt, AnalyticsScreen.kt

### Files Affected (Color.Black/White → theme token)
- AddDebtBottomSheet.kt, DebtDetailScreen.kt, SplitScreen.kt
- LoadingDotsIndicator.kt, ConfettiOverlay.kt

---

## 4. Bug Fixes

### Critical
| Bug | File | Fix |
|-----|------|-----|
| Policy click crash | SettingsScreen.kt:311 | Wrap `startActivity` in try-catch `ActivityNotFoundException` |
| PRIVACY_POLICY_URL empty | build.gradle.kts:42 | Hardcode fallback to dhanuk.page.gd URL |

### Medium
| Bug | File | Fix |
|-----|------|-----|
| CSV export crash | SettingsViewModel.kt:378 | try-catch around `startActivity` |
| ShareUtils crash | ShareUtils.kt:58 | try-catch around `startActivity` |
| HtmlExporter crash | HtmlExporter.kt:394 | try-catch around `startActivity` |
| CanvasExporter crash | CanvasExporter.kt:420 | try-catch around `startActivity` |
| WhatsApp share fallback crash | DebtDetailViewModel.kt:356 | Ensure fallback also has try-catch |
| Dashboard fake refresh | DashboardScreen.kt:66 | Drive `isRefreshing` by actual sync completion |
| Shared retry counter | RealTimeSyncManager.kt:37 | Split into separate `debtRetryAttempt` + `splitRetryAttempt` |
| Link email no validation | SettingsScreen.kt:472 | Add min-length email/password validation |

### Low (not in this sprint)
- Split always creates THEY_OWE_ME
- SplitViewModel bulk sync not individual push
- Leaderboard O(n^2) indexOf
- SyncManager firebaseId set before Firestore confirm

---

## 5. Auth Flow Integration

### NavGraph Flow
- Auth screen accessible from: Settings (GoogleSignInCard tap), Auth popup "Sign In" button
- Not a start destination — user can always skip
- On successful sign-in: pop back to previous screen, trigger sync
- On skip: navigate to Dashboard (existing behavior)

### ViewModel Changes
- `AddDebtViewModel`: Add `showAuthPrompt` StateFlow, set true when `authManager.getCurrentUser() == null` and sync would be skipped
- `DebtDetailViewModel`: Same pattern for payment/settle/delete actions
- `SplitViewModel`: Same pattern for createSplit
- Each screen observes `showAuthPrompt` and shows the dialog
- Track `hasShownAuthPromptThisSession` to avoid spamming

### SettingsViewModel
- `signInWithGoogle()` remains current implementation
- Add `signInFromAuthScreen(activity)` — same logic but called from AuthScreen
- On success from AuthScreen: `navController.popBackStack()` then trigger sync
