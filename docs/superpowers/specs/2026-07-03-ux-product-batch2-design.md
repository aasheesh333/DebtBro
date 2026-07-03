# Design — UX/Product Batch 2 (2026-07-03)

Status: DRAFT — awaiting user approval
Branch target: `main` (CI auto-cut Release on push)
Verification: CI only — no local builds (per AGENTS.md)

## Background

Audit fixes (P0+P1+P2) shipped in v1.0.137 (commit `bc6b84a`). This batch addresses the next layer of UX/product issues surfaced during audit + user feedback:

1. App defaults to SYSTEM theme — Android users on Pie/Oreo get dark-first which looks unfinished
2. Onboarding pushes user to dashboard without sign-in — many users skip and lose Firebase sync
3. AI responses intermittently truncated to 1-2 words ("sirf do words de rahi hai")
4. Analytics screen AI insight has manual Refresh icon with no rate limit — users spam-tap and get 429s
5. Empty-state rendering inconsistent across device fonts because unicode emoji 📋/🗂️ render differently per OEM
6. Settings screen has "AI Setup" section exposing a Gemini API key input — unprofessional, security-flagged during audit
7. Account deletion currently uses WorkManager-only 24h grace — does not restore across devices (not even across app reinstalls)
8. Default-light theme doesn't match app's dark-first design status
9. InfinityFree-hosted legal pages (Privacy/Terms/Help/Delete-Account) need to reflect new deletion flow + AI Setup removal

## Scope

### In scope
- `AppPreferences.themeMode` default change
- `OnboardingScreen.kt::Page5Name` extension with Google sign-in / email link / Skip
- `OnboardingViewModel` wiring to `AuthManager` + `Activity` context
- `AiRepository.kt` 3-truncation fixes (lines ~401/430/452) + `maxOutputTokens` bump + `MAX_TOKENS` retry
- `AnalyticsScreen.kt` + `AnalyticsViewModel.kt` rewarded-ad unlock pattern (port from `DebtDetailViewModel`)
- `EmptyStateView.kt` signature: add `icon: ImageVector?` param; update `DebtListScreen.kt` callsite
- `SettingsScreen.kt` "AI Setup" section removal (lines ~207-336) + `SettingsViewModel` dead methods cleanup
- Firebase Cloud Function: `functions/index.js` with `requestAccountDeletion` + `cancelAccountDeletion` HTTP endpoints + `processDeletionQueue` scheduled function
- `AuthManager.cancelAccountDeletion(uid)` client method
- `AuthViewModel.onAuthSuccess` auto-restore dialog when pending deletion timestamp exists
- `SettingsScreen` delete-account dialog reverted to 2-button (Cancel + Delete Account)
- 18-language string additions for new onboarding auth buttons, rewarded-ad unlock CTA, account restore dialog, deletion dialog (new strings only — no re-translation of existing content)
- InfinityFree HTML page updates (Privacy / Terms / Help / Delete-Account) via Chrome DevTools MCP at `https://filemanager.ai/new3/index.php`
- README / docs note for Cloud Function deployment (`firebase deploy --only functions`)

### Out of scope
- Firebase CLI deployment from this environment (user runs `firebase deploy` on their machine)
- Onboarding visual redesign (only the last page auth additions)
- New legal copy beyond what's needed to reflect technical flow change (no policy rewrite)
- Re-translation of existing strings
- Test scaffolding (project has zero test files per AGENTS.md ANTI-PATTERNS — not starting now)
- Removing AI Setup section's backing code (`SecureStorage` / `AiRepository.apiKey()` migration paths) — kept for backward compat with previously-stored keys
- Removing `AccountDeletionWorker.kt` — kept as offline fallback (belt-and-suspenders)

## Architecture decisions

### 2.1 Theme default → LIGHT
- `AppPreferences.kt`: change `Keys.themeMode` default from `"SYSTEM"` → `"LIGHT"`.
- Existing theme-setting UI in Settings intact.
- `MainActivity.kt` already maps `"LIGHT"` → false → light theme. No code change beyond prefs.

### 2.2 Sign-in / Sign-up / Skip on onboarding last page
- `OnboardingScreen.kt::Page5Name` extends below the existing name field:
  - Google "Continue with Google" button (Credential Manager)
  - "Or continue with email" link → navigate to `Screen.Auth`
  - "Skip for now" button → `completeOnboarding()` directly
- Skip requires no name; name optional going forward. Settings already has name entry — keep that.
- `OnboardingViewModel` gets `AuthManager` + `Activity` injection. New method `signInWithGoogle(activity, onSuccess, onError)`.
- Sign-in success → `prefs.setOnboardingComplete(name)` + `onOnboardingComplete()`.
- Skip → `prefs.setOnboardingComplete("")` + `onOnboardingComplete()`.
- Pattern copied from existing `AuthScreen.kt` for Activity context retrieval: `LocalContext.current as? Activity`.

### 2.3 Language switching + AI response language
- Already wired:
  - `NavGraph.kt` calls `LocalizedString.setLanguage(code)` on language change → recomposition via `MutableState`
  - `AiRepository.buildSystemPrompt` injects `langInstruction`: `Respond ONLY in <language>` (lines 280-285)
- **Gap**: 18-language coverage for new strings (onboarding auth buttons, rewarded-ad CTA, account restore dialog, deletion dialog text)
- **Action**: add missing keys per language for new strings only. No refactor.

### 2.4 Analytics AI refresh → rewarded-ad unlock
- **Remove** existing `IconButton(onClick = { viewModel.loadAiInsight() })` from `AnalyticsScreen.kt` (lines ~215-217)
- **Add** counter badge showing `5 - count` free remaining; on tap when count=0 → "Watch ad for more" dialog → rewarded ad (reuse `AdManager` + DebtDetail pattern)
- `AnalyticsViewModel` new fields:
  - `isGeneratingAi: StateFlow<Boolean>`
  - `remainingFree: StateFlow<Int>` (init from `prefs.aiRegenerationCount` → `MAX_FREE_REGENERATIONS - count`)
  - `showRewardAd: StateFlow<Boolean>`
- `loadAiInsight()` rewrite using DebtDetail's `generateRoast` template: free-first, after free limit exhausted show ad → on ad reward increment counter headroom.
- `MAX_FREE_REGENERATIONS` constant already in `AiRepository` (=5) — shared via SettingsViewModel or new public field.

### 2.5 AI response truncation fix (root cause)
- **Root cause**: `AiRepository.kt` line ~401 reads `response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text` — only the FIRST `Part` is extracted. Gemini frequently splits text across multiple `parts` in candidates. Trailing parts (the bulk of the roast text) are dropped.
- **Fix** at 3 sites (lines ~401, ~430, ~452):
  ```kotlin
  val text = response.candidates.firstOrNull()?.content?.parts
      ?.joinToString("") { it.text }
  ```
- Bump `maxOutputTokens`:
  - roast: 200 → 400
  - analyzeDebts: 150 → 350
  - tips: 100 → 250
- Add `finishReason == MAX_TOKENS` check → if hit AND `response.text` length is suspiciously close to limit, retry once with doubled limit (safety net only — primary fix is the parts join).

### 2.6 Empty state emoji → Material Icons
- `EmptyStateView.kt`: deprecate `emoji: String` param (keep as nullable default-`null` for callers we miss) and add `icon: ImageVector?`.
- `DebtListScreen.kt:127` callsite switched from `emoji = "🔍"` → `icon = Icons.Default.Search`.
- Add `Icons` import from `androidx.compose.material.icons.filled.*`. Already in compose dependency graph.
- Per OEM font rendering variations on emoji go away.

### 2.7 Remove AI Setup from Settings
- `SettingsScreen.kt` lines ~207-336 (entire "AI SETUP" section incl. connection-test panel) → delete
- `SettingsViewModel.kt`:
  - Remove `saveGeminiKey`, `testAiConnection`, `aiTestRunning`, `aiTestResult` (UI plumb points only)
  - Keep backing code: `SecureStorage` reads/writes, `AiRepository.apiKey()` migration paths — left intact for users who already stored a key before this build
- `SettingsScreen.kt` remove imports for `OutlinedTextField`, `OutlinedButton` if no other use
- Result: users always use bundled CI key.

### 2.8 Account deletion — Cloud Function
Server-side new files:
- `functions/package.json`
- `functions/index.js` exposing:
  - HTTP `requestAccountDeletion(uid)` → writes Firestore `deletionQueue/{uid}` doc `{ queuedAt: serverTimestamp, status: "PENDING" }`
  - HTTP `cancelAccountDeletion(uid)` → marks doc `status: "CANCELLED"` if within 24h; idempotent
  - Scheduled `processDeletionQueue` (every 30 min, `functions.pubsub.schedule('*/30 * * * *')`):
    - For each PENDING doc where `queuedAt < now - 24h`:
      - `auth.getUser(uid)` → if `metadata.lastRefreshAt > queuedAt` → user signed in during grace → set `CANCELLED`, skip
      - Else: `auth.deleteUser(uid)` + wipe Firestore `users/{uid}` + sub-collections. Set `COMPLETED`.
  - All endpoints require auth context (`context.auth.uid === data.uid`)
- `firebase.json` adds functions deploy target
- `functions/README.md` with deploy instructions

Client-side:
- `AuthManager.kt`: add `suspend fun cancelAccountDeletion(uid: String): Result<Unit>` POSTing to `/cancelAccountDeletion`
- `AuthViewModel.onAuthSuccess` checks `prefs.pendingDeletionTimestamp`:
  - If set & within 24h → emit `restoreAccountDialog` state → UI shows confirm dialog
  - On "Restore" → `auth.cancelAccountDeletion(uid)` + clear timestamp + `WorkManager.cancelUniqueWork(ACCOUNT_DELETION_WORK_NAME)`
- `SettingsScreen.kt` delete-account dialog → reverted from 3-step to 2-button ("Cancel" + "Delete Account"). On "Delete Account": `viewModel.requestAccountDeletion(context) {}` then close dialog + sign out UI.
- `AccountDeletionWorker.kt` ke `doWork()` updated to call Cloud Function endpoint instead of just Firebase client `delete()` — for cross-device restoration to actually work, the worker's local Room + DataStore clear + sign-out sequence matters, but the **server-side deletion happens via the scheduled Cloud Function**, not the worker.

### 2.9 InfinityFree HTML pages (Chrome DevTools MCP)
Pages to update (sequential edits via File Manager UI):
- `Privacy-Policy.html` — remove section about user-pasting Gemini API key (since removed from app); update deletion section to describe Cloud Function + 24h cross-device restore window
- `Terms-and-Conditions.html` — same as above for AI key user-paste clause; update deletion section
- `Help-and-Support.html` — change FAQ "Can I recover my deleted account?" answer from "no, only local grace" to "YES — within 24h cross-device via re-login"
- `Delete-Account.html` — replace "Method 1: Delete Now + 24h Grace (local)" with "Method 1: Delete Account in-app → 24h cross-device restore window via re-login on any device. Method 2: email (7 business days). Method 3: Play Store request."

Technique: navigate File Manager CodeMirror editor via `chrome-devtools_*` MCP, read-modify-write via `document.querySelector('.CodeMirror').CodeMirror.getValue()/setValue()` JS snippet in `chrome-devtools_evaluate_script`.

## Sequencing

Implementation batches (each batch ends with commit + push + CI observation; ~6m40s avg per build):

**Batch 1 — Pure-local code fixes** (lowest risk):
- 2.5 AI truncation fix (3 sites + token bump + retry)
- 2.1 Theme default → LIGHT (single line)
- 2.6 Empty state emoji → Material Icons

**Batch 2 — Settings/Analytics UI cleanup**:
- 2.7 Remove AI Setup from Settings + SettingsViewModel dead methods
- 2.4 Analytics AI refresh → rewarded-ad unlock (port from DebtDetailViewModel)

**Batch 3 — Onboarding + auth flow + new localized strings**:
- 2.2 Sign-in/Sign-up/Skip on onboarding last page
- 2.3 Add missing 18-language keys for all new strings

**Batch 4 — Account deletion Cloud Function**:
- 2.8 `functions/index.js` (3 endpoints + scheduled worker) + `firebase.json` + README
- Client-side `AuthManager.cancelAccountDeletion`, `AuthViewModel` auto-restore dialog
- `SettingsScreen` 2-button dialog revert
- `AccountDeletionWorker` update to call CF endpoint instead of `auth.delete()`

**Batch 5 — InfinityFree HTML pages** (independent, can parallel with Batches 1-4):
- 2.9 Privacy / Terms / Help / Delete-Account edits via Chrome DevTools MCP

## Verification & risk

### Verification
- `./gradlew assembleRelease --stacktrace` (CI only — no local builds per AGENTS.md)
- No tests (project has zero test files per AGENTS.md ANTI-PATTERNS)
- Manual smoke after each CI green: theme on fresh install, onboarding last-page Google sign-in + email nav + Skip, AI roast on a debt (check non-truncation), Analytics AI insight free-then-ad flow, Settings has no AI Setup, Settings delete-account 2-button + sign-in-within-24h restore

### Risk items
1. **2.2 Onboarding last page Activity context retrieval** — copy `AuthScreen.kt` pattern exactly; otherwise Credential Manager throws `null activity`.
2. **2.7 Removing AI Setup**: imports cleanup must be exhaustive — `OutlinedTextField`, `OutlinedButton`, `SnackbarHostState` may have other callers. Audit before deletion.
3. **2.8 Cloud Function deployment**: env can't deploy. User runs `firebase deploy --only functions` after CLI installed. CF URLs must be hardcoded into `AuthManager.kt` after deploy — capture URL via `firebase functions:config` or write to `BuildConfig.DELETION_CF_URL` via CI secret. **Open question for deploy step**.
4. **2.9 InfinityFree JS anti-bot**: must use File Manager UI (browser session already established). Cannot curl/fetch.
5. **AccountDeletionWorker kept** as belt-and-suspenders fallback — even if Cloud Function fails or never deployed, user's local data is cleared + signed out.
6. **maxOutputTokens increase** — never a 503 cause from Gemini; conservative bump + the parts-join fix should resolve truncation entirely without retry.

## Open questions (defer to implementation)

1. Backfill the Cloud Function URL into `AuthManager.kt` — manual step between CF deploy + APK release. Document in `functions/README.md`.
2. Whether to expose bundled-CI-key vs user-paste-key distinction in `AiRepository` further — decision now: bundled-only, SecureStorage kept only for migration. No user-facing key field.
3. Deletion Cloud Function URL configuration: hardcode for v1 or wire as `BuildConfig` secret. Will coordinate with user during Batch 4 implementation.

## Out-of-scope explicitly

- Firebase deployment execution
- Test infrastructure setup (zero-test project — not introducing now)
- Onboarding visual redesign
- New legal copy beyond technical flow reflection
- Re-translation of existing strings (only new strings translated)
