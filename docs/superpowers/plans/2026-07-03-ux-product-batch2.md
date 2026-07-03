# UX/Product Batch 2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship 9 workstreams across 5 batches — AI truncation fix, theme default → LIGHT, empty-state emoji → Material Icons, Settings AI Setup removal, Analytics rewarded-ad unlock, Onboarding sign-in/sign-up/skip, Cloud Function `cancelAccountDeletion`, 18-language string additions, InfinityFree page updates.

**Architecture:** Android (Kotlin/Jetpack Compose) MVVM + Hilt DI single-module app. Cloud Function via Firebase Functions (Node.js). InfinityFree static HTML pages updated via File Manager UI (JS anti-bot challenge prevents curl/fetch).

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Hilt, Room, Firebase Auth/Firestore/Functions, Retrofit (Gemini API), Material 3 Icons, Node.js (Cloud Functions).

## Global Constraints

- **minSdk stays at 26** (Android 9 users get "problem parsing the package" if raised).
- **Never run local builds** — CI is the only verification path (~6m40s avg per build). After each batch: commit, push, watch CI green.
- **No git worktrees** — direct pushes to `main`, CI auto-deploys GitHub Releases.
- **GitHub Actions secret name:** `GEMINI_API_KEY` (not `GROQ_API_KEY`).
- **InfinityFree hosting:** `if0_40517277`, domain `dhanuk.page.gd`, File Manager URL `https://filemanager.ai/new3/index.php`, CodeMirror editor via `document.querySelector('.CodeMirror').CodeMirror.getValue()/setValue()`. Must use File Manager UI — JS anti-bot blocks curl/fetch.
- **No tests** — project has zero test files per AGENTS.md ANTI-PATTERNS. Verification = successful CI build (compile + lint).
- **No comments in code** unless the file already has explanatory comments AND the change site is non-obvious. Prefer self-documenting code.
- **18 languages** in `LocalizedString.kt`: en, hi, es, fr, de, it, pt, ru, ja, ko, zh, ar, tr, id, vi, th, mr, pa (plus family fallback `gu/ne/sa → hi`). Unknown keys return the key itself as a literal string — so every new key MUST be added to all 18 language maps.
- **`MAX_FREE_REGENERATIONS`** = `AiRepository.Companion.MAX_FREE_REGENERATIONS` (=5).
- **User must run `firebase deploy --only functions`** from their machine (can't deploy from this environment).

---

## File Structure

### Files Modified
- `app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt` — 3 truncation fixes + `maxOutputTokens` bumps (roast 200→400, analyze 150→350, split 100→250)
- `app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt:92` — theme default `"SYSTEM"` → `"LIGHT"`
- `app/src/main/java/com/dhanuk/debtbro/presentation/components/EmptyStateView.kt` — add `icon: ImageVector?` param (keep `emoji` deprecated)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtlist/DebtListScreen.kt:126-130` — switch EmptyStateView callsites to `icon =`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt` — port rewarded-ad pattern from `DebtDetailViewModel`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsScreen.kt:~215` — remove refresh IconButton; add counter badge + ad dialog
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt:207-305` — delete AI Setup Card; keep notifications section
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt` — remove dead AI key/test methods; update deletion flow to 2-button revert
- `app/src/main/java/com/dhanuk/debtbro/data/firebase/AuthManager.kt` — add `cancelAccountDeletion(uid)` method
- `app/src/main/java/com/dhanuk/debtbro/data/network/AccountDeletionApiService.kt` — add `cancelDeletion` endpoint
- `app/src/main/java/com/dhanuk/debtbro/data/network/AccountDeletionModels.kt` — shared `AccountDeletionRequest` already exists; reuse for cancel
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt` — inject `AuthManager`; add `signInWithGoogle`/`signUpEmailPassword` methods
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt` — extend `Page5Name` with Google sign-in + email link + Skip
- `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt` — add ~12 new keys × 18 languages

### Files Created
- `functions/index.js` — `requestAccountDeletion` + `cancelAccountDeletion` HTTP endpoints + `processDeletionQueue` scheduled (every 30 min)
- `functions/package.json` — Node.js deps for `firebase-admin`, `firebase-functions`
- `firebase.json` — functions deploy config (if not present)

### Files NOT Modified (verified)
- `MainActivity.kt` — theme mapping works unchanged (already maps LIGHT → light theme)
- `AccountDeletionWorker.kt` — kept as offline fallback (belt-and-suspenders)
- `worker/AccountDeletionWorker.kt` — no changes
- `app/build.gradle.kts` — `BuildConfig.ACCOUNT_DELETION_URL` already wired at line 65

---

## Batch 1: AI Truncation Fix + Theme Default + Empty State Icons

### Task 1: Fix AI response truncation (3 sites) + bump maxOutputTokens

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt` (lines 397, 401, 426, 430, 448, 452)

**Root cause:** `parts.firstOrNull()?.text` drops all but the first Gemini response part. Gemini sometimes splits text across multiple `parts`. Fix: `parts.joinToString("") { it.text ?: "" }` so concatenated text is captured.

**Interfaces:** No signature changes — purely internal parsing.

- [ ] **Step 1: Fix `generateRoast` truncation + bump tokens**

In `AiRepository.kt`, find the `generateRoast` function. At the `GenerationConfig(... maxOutputTokens = 200)` line (~397), change to `maxOutputTokens = 400`. Then at the parsing line (~401):

Change:
```kotlin
        val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            ?: throw Exception("AI response is empty, try again")
```
To:
```kotlin
        val text = response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.takeIf { it.isNotBlank() }
            ?: throw Exception("AI response is empty, try again")
```

- [ ] **Step 2: Fix `analyzeDebts` truncation + bump tokens**

In `analyzeDebts` (~line 426), change `maxOutputTokens = 150` → `maxOutputTokens = 350`. Then at the parsing line (~430):

Change:
```kotlin
        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty AI response")
```
To:
```kotlin
        response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Empty AI response")
```

- [ ] **Step 3: Fix `generateSplitSummary` truncation + bump tokens**

In `generateSplitSummary` (~line 448), change `maxOutputTokens = 100` → `maxOutputTokens = 250`. Then at the parsing line (~452):

Change:
```kotlin
        response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text?.trim()
            ?: throw Exception("Empty AI response")
```
To:
```kotlin
        response.candidates.firstOrNull()?.content?.parts
            ?.joinToString("") { it.text ?: "" }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Empty AI response")
```

- [ ] **Step 4: Commit batch 1 part 1**

```bash
git add app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt
git commit -m "fix(ai): join multi-part Gemini responses + bump maxOutputTokens

Root cause: parts.firstOrNull() dropped all but the first Gemini
response part when the API split text across multiple parts.
Fix: parts.joinToString(\"\") { it.text ?: \"\" } at all 3 call sites
(generateRoast, analyzeDebts, generateSplitSummary).

Also bumps maxOutputTokens to give Gemini headroom for longer
roasts/insights: roast 200->400, analyze 150->350, split 100->250."
```

### Task 2: Change theme default to LIGHT

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt:92`

**Interfaces:** No signature changes. Existing users who already toggled theme keep their saved value (DataStore returns stored value, not the default). Only fresh installs get LIGHT.

- [ ] **Step 1: Change theme default**

At `AppPreferences.kt:92`:

Change:
```kotlin
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "SYSTEM" }
```
To:
```kotlin
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "LIGHT" }
```

- [ ] **Step 2: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt
git commit -m "feat(theme): default to LIGHT theme on fresh installs

Existing users keep their saved preference (DataStore returns
stored value). Only first-launch users get LIGHT instead of SYSTEM."
```

### Task 3: Empty state emoji → Material Icons

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/components/EmptyStateView.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtlist/DebtListScreen.kt:126-130`

**Interfaces:**
- `EmptyStateView` gains: `icon: ImageVector? = null` param. When non-null, renders `Icon(icon, ...)` instead of `Text(emoji, ...)`. `emoji: String` stays for backward compat but defaults to empty string.

- [ ] **Step 1: Add `icon` parameter to `EmptyStateView`**

In `EmptyStateView.kt`, add imports + modify the composable signature and the emoji-rendering block. Add at top of file:

```kotlin
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
```

Change the function signature from:
```kotlin
@Composable
fun EmptyStateView(
    emoji: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(UITokens.SpaceXXL)
) {
```
To:
```kotlin
@Composable
fun EmptyStateView(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    emoji: String = "",
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(UITokens.SpaceXXL)
) {
```

Then the emoji render block (currently always `Text(emoji, fontSize = 48.sp, lineHeight = 56.sp)`). Change that block to:
```kotlin
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (emoji.isNotEmpty()) {
            Text(
                emoji,
                fontSize = 48.sp,
                lineHeight = 56.sp,
            )
        }
```
(Add `import androidx.compose.foundation.layout.size` if not already imported.)

- [ ] **Step 2: Update `DebtListScreen.kt` callsites**

Open `DebtListScreen.kt` around lines 126-130. Find the two `EmptyStateView(emoji = ...)` calls. Add import at top of file:
```kotlin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.AccountBalanceWallet
```
(Verify the exact icon imports compile — Material 3 bundled icons.)
Change each callsite to use `icon =` form. For the "no search results" empty state, use `Icons.Default.Search`. For the "no debts yet" empty state, use `Icons.Default.AccountBalanceWallet`. Replace `emoji = "🔍"` with `icon = Icons.Default.Search`, and `emoji = "🏷️"` with `icon = Icons.Default.AccountBalanceWallet`. The remaining named params (`title =`, `subtitle =`, etc.) stay verbatim.

- [ ] **Step 3: Commit batch 1 part 3**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/components/EmptyStateView.kt
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtlist/DebtListScreen.kt
git commit -m "feat(ui): empty states render Material Icons, not emoji

EmptyStateView gains icon: ImageVector? param (preferred over the
deprecated emoji: String form). DebtListScreen search/no-debts
empty states switched to Icons.Default.Search /
Icons.Default.AccountBalanceWallet. Existing emoji callers keep
working — emoji param defaults to \"\" and is rendered only when
icon == null."
```

### Task 4: Push Batch 1 + verify CI

- [ ] **Step 1: Push to main**

```bash
git push origin main
```

- [ ] **Step 2: Monitor CI**

Watch GitHub Actions run. Expected: green within ~6-7 minutes. If lint fails on unused import (`emoji` no longer required in `EmptyStateView.kt` is still a positional param call site, so any project-level `emoji = ` calls elsewhere will still compile — verify there are no compile errors). If the build fails, read the Actions log, fix the reported compile error, commit, push again.

---

## Batch 2: Remove AI Setup from Settings + Analytics Rewarded-Ad Unlock

### Task 5: Remove AI Setup Card from Settings

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt` (delete lines 207-305)
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt` (remove dead `testAiConnection`, `saveGeminiKey`, related state)

**Interfaces:** No public API surface changes for SettingsScreen. `SettingsViewModel` removes its `testAiConnection()`, `saveGeminiKey()`, `aiTestRunning`, `aiTestResult` StateFlows. `state.geminiApiKey` removed from `SettingsUiState`. Keep `SecureStorage` + `AiRepository.apiKey()` plumbing intact — users can still paste a key via the (now removed) UI? **No.** Per spec: AI Setup section fully removed. The bundled `BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE` still powers the AI behind the scenes.

- [ ] **Step 1: Delete AI Setup Card from `SettingsScreen.kt`**

In `SettingsScreen.kt`, delete the entire `item { Card(...) }` block from the comment line `// ── AI SETUP ──` (line 208) through the closing `}` of that `item {}` block (line 305). Keep the line 207 comment `// ── NOTIFICATIONS SECTION ──` (will be followed by the notifications section which now starts at line 307 `item { SectionHeader... }`).

Remove any now-unused imports the deleted block used:
- `androidx.compose.material.icons.Icons` (only if no other usages remain in file — verify first by grep)
- `androidx.compose.material.icons.filled.AutoAwesome`
- `androidx.compose.material.icons.filled.NetworkCheck`
- `androidx.compose.ui.text.input.PasswordVisualTransformation`
- `androidx.compose.material3.OutlinedTextField`/`OutlinedTextFieldDefaults` (only if not used elsewhere)

Run `grep -n "Icons\\.Default\\." SettingsScreen.kt` AFTER edits to confirm remaining icon usages still need the `Icons` import; if not, remove it.

- [ ] **Step 2: Remove dead AI methods from `SettingsViewModel.kt`**

Open `SettingsViewModel.kt`. Remove:
- `fun saveGeminiKey(...)` function
- `fun testAiConnection(...)` function
- `val aiTestRunning: StateFlow<...>` (and its backing `MutableStateFlow`)
- `val aiTestResult: StateFlow<...>` (and its backing `MutableStateFlow`)
- `geminiApiKey` field from the `SettingsUiState` data class (or wherever it lives in this file)
- Any `SecureStorage`/`AiRepository` fields in the ViewModel constructor that were ONLY used by `testAiConnection`/`saveGeminiKey`. Verify by reading the file — if `ai` is referenced elsewhere (e.g., for regeneration count), keep it.

After removing, read the file top-to-bottom to ensure no orphaned references remain (no `state.geminiApiKey` reads in the screen, no `viewModel.testAiConnection()` calls).

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt
git commit -m "feat(settings): remove AI Setup section

Per UX Batch 2 spec: AI Setup section (API key input + Test AI
connection panel) deleted from SettingsScreen. SettingsViewModel
loses the corresponding saveGeminiKey/testAiConnection methods and
aiTestRunning/aiTestResult state. Gemini still works via the
bundled BuildConfig.GEMINI_API_KEY_2_5_FLASH_LITE; users no longer
need to paste keys. SecureStorage + AiRepository.apiKey() plumbing
left intact for the legacy-key migration path."
```

### Task 6: Port rewarded-ad pattern into AnalyticsViewModel

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt`

**Interfaces:**
- `AnalyticsViewModel` exposes new state: `showRewardAd: StateFlow<Boolean>`, `remainingFree: StateFlow<Int>`. The `AdManager` is injected via constructor.

Pattern modeled on `DebtDetailViewModel.kt:167-248`:
- `_showRewardAd: MutableStateFlow<Boolean>` (init false)
- `_remainingFree: MutableStateFlow<Int>` (init from `ai.remainingFreeRegenerations()`)
- `preloadRewardedAd(context)` calls `adManager.loadRewardedAd(context)`
- `loadAiInsight(activity)` gates on `ai.canRegenerate()`: if not, and `activity` available, show ad → on reward reset count → reload; else `_showRewardAd.value = true`.

- [ ] **Step 1: Inject `AdManager` into `AnalyticsViewModel`**

At the `AnalyticsViewModel @Inject constructor` signature, add `private val adManager: com.dhanuk.debtbro.data.ads.AdManager` as a new constructor param. Add the import for `AdManager` at the top of the file.

- [ ] **Step 2: Add rewarded-ad state fields**

Just before `init { ... }`, add (mirror DebtDetailViewModel):
```kotlin
    private val _showRewardAd = MutableStateFlow(false)
    val showRewardAd: StateFlow<Boolean> = _showRewardAd.asStateFlow()

    private val _remainingFree = MutableStateFlow(5)
    val remainingFree: StateFlow<Int> = _remainingFree.asStateFlow()
```
Add imports:
```kotlin
import kotlinx.coroutines.flow.asStateFlow
import android.content.Context
import com.dhanuk.debtbro.data.repository.AiRepository.Companion.MAX_FREE_REGENERATIONS
```

- [ ] **Step 3: Init `_remainingFree` from prefs**

In the existing `init { viewModelScope.launch { ... } }` block, BEFORE `state.first { ... }`, add:
```kotlin
            _remainingFree.value = ai.remainingFreeRegenerations()
```

- [ ] **Step 4: Add `preloadRewardedAd` + new ad-gated `loadAiInsight` overload**

Replace the existing `fun loadAiInsight()` (no-arg) with a new `fun loadAiInsight(activity: android.app.Activity? = null)` that performs the ad gate. The body should mirror `DebtDetailViewModel.generateRoast(activity)`:

```kotlin
    fun preloadRewardedAd(context: Context) {
        adManager.loadRewardedAd(context)
    }

    fun loadAiInsight(activity: android.app.Activity? = null) = viewModelScope.launch {
        if (isLoadingInsight.value) return@launch
        isLoadingInsight.value = true

        val s = state.value
        if (s.totalOwedToMe + s.totalIOwe <= 0.0) {
            isLoadingInsight.value = false
            return@launch
        }

        if (!ai.canRegenerate()) {
            val connectivityManager = activity?.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager
            val isOffline = connectivityManager?.activeNetwork == null
            if (isOffline) {
                isLoadingInsight.value = false
                return@launch
            }
            if (activity != null) {
                isLoadingInsight.value = false
                adManager.showRewardedAd(activity, onRewarded = {
                    viewModelScope.launch {
                        ai.resetRegenerationCount()
                        _remainingFree.value = ai.remainingFreeRegenerations()
                        loadAiInsightInternal()
                    }
                }, onFailed = {
                    adManager.loadRewardedAd(activity)
                    _showRewardAd.value = false
                })
            } else {
                _showRewardAd.value = true
            }
            return@launch
        }

        loadAiInsightInternal()
    }

    private fun loadAiInsightInternal() = viewModelScope.launch {
        try {
            val s = state.value
            if (s.totalOwedToMe + s.totalIOwe <= 0.0) {
                aiInsight.value = ""
                return@launch
            }
            val result = ai.analyzeDebts(s.totalOwedToMe, s.totalIOwe, s.recoveryRate, s.worstOffender)
            aiInsight.value = when {
                result.isSuccess -> result.getOrThrow()
                result.exceptionOrNull() is NoApiKeyException ->
                    LocalizedString.get("no_api_key_message")
                else ->
                    LocalizedString.get("ai_error_message")
            }
            _remainingFree.value = ai.remainingFreeRegenerations()
        } finally {
            isLoadingInsight.value = false
        }
    }
```

Make sure the `init { ... state.first { ... loadAiInsight() } }` call is updated to call `loadAiInsightInternal()` (no ad gate on cold start — free regenerations always available on first launch). Actually safer: keep `init { loadAiInsight() }` calling the public no-arg overload with `activity = null` — but `activity == null` means we hit `_showRewardAd.value = true` when count is exhausted. Cold start always has full free budget, so this branch never fires on cold start. Leave init calling `loadAiInsight()` (no args = activity null). Verify by re-reading the init block.

- [ ] **Step 5: Commit (task 6)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt
git commit -m "feat(analytics): gate AI insight behind rewarded ad after 5 free

Ports DebtDetailViewModel's rewarded-ad pattern into
AnalyticsViewModel: AdManager injected, showRewardAd/
remainingFree StateFlows exposed. After 5 free regenerations
the user must watch a rewarded ad to refresh the insight.
loadAiInsight(activity) replaces the old no-arg form; the
internal worker (loadAiInsightInternal) is split out so the
rewarded-ad callback can resume it cleanly."
```

### Task 7: Replace manual refresh IconButton with counter badge + ad dialog

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsScreen.kt` (lines ~205-239)

**Pattern reference:** `DebtDetailScreen.kt:469-489` (rewarded-ad AlertDialog with "Watch Ad" / "Later" buttons).

- [ ] **Step 1: Collect new state in `AnalyticsScreen`**

Find where `AnalyticsViewModel` is collected in `AnalyticsScreen.kt` (probably `val viewModel: AnalyticsViewModel = hiltViewModel()` near top). Add:
```kotlin
val showRewardAd by viewModel.showRewardAd.collectAsStateWithLifecycle()
val remainingFree by viewModel.remainingFree.collectAsStateWithLifecycle()
```
Add `import androidx.lifecycle.compose.collectAsStateWithLifecycle` if not present.

Also need an `Activity` reference to pass to `loadAiInsight(activity)`. Add near the viewModel line:
```kotlin
val context = androidx.compose.ui.platform.LocalContext.current
val activity = context as? android.app.Activity
```

- [ ] **Step 2: Preload rewarded ad**

In the screen's `LaunchedEffect(Unit)` (or add one if missing) call:
```kotlin
LaunchedEffect(Unit) {
    viewModel.preloadRewardedAd(context)
}
```

- [ ] **Step 3: Replace the refresh `IconButton` with a counter badge**

In the AI Insights card's header Row (currently `IconButton(onClick = { viewModel.loadAiInsight() }) { Icon(Icons.Default.Refresh, ...) }`), replace that IconButton with a tappable counter badge. The badge shows remaining-free count if > 0, else "Watch Ad" (or the localized equivalent). Tapping it invokes `viewModel.loadAiInsight(activity)` (returns early if already loading).

Replace the `IconButton { Icon(Icons.Default.Refresh, ...) }` block with:
```kotlin
                            val badgeLabel = if (remainingFree > 0) "$remainingFree" else LocalizedString.get("watch_ad")
                            Box(
                                modifier = Modifier
                                    .clip(UITokens.ShapeLarge)
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .clickable { viewModel.loadAiInsight(activity) }
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    badgeLabel,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = UITokens.FontCaption,
                                    fontWeight = FontWeight.Bold
                                )
                            }
```
Add necessary imports: `androidx.compose.foundation.clickable`, `androidx.compose.ui.draw.clip`, `androidx.compose.foundation.background`, `androidx.compose.foundation.layout.Box`, `androidx.compose.foundation.layout.padding`. Remove now-unused `Icons.Default.Refresh` import IF no other usage remains in this file (grep to confirm).

Also remove the static `tap_refresh` placeholder text — when loading and insight is blank, show an actionable prompt instead. (Skip if the existing text already serves as placeholder.)

- [ ] **Step 4: Add the rewarded-ad `AlertDialog`**

At the end of the AI Insights `item { Card(...) }` block (inside the `Column`), add the reward-ad dialog. Pattern from `DebtDetailScreen.kt:469-489`:
```kotlin
                        if (showRewardAd) {
                            AlertDialog(
                                onDismissRequest = { viewModel.dismissRewardAd() },
                                title = { Text(LocalizedString.get("watch_ad")) },
                                text = { Text(LocalizedString.get("free_regenerations_desc")) },
                                confirmButton = {
                                    TextButton(onClick = {
                                        viewModel.dismissRewardAd()
                                        viewModel.loadAiInsight(activity)
                                    }) { Text(LocalizedString.get("watch_ad")) }
                                },
                                dismissButton = {
                                    TextButton(onClick = { viewModel.dismissRewardAd() }) {
                                        Text(LocalizedString.get("later"))
                                    }
                                }
                            )
                        }
```

- [ ] **Step 5: Add `dismissRewardAd()` to `AnalyticsViewModel`**

In `AnalyticsViewModel.kt`, add:
```kotlin
    fun dismissRewardAd() { _showRewardAd.value = false }
```

- [ ] **Step 6: Commit (task 7)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsScreen.kt
git commit -m "feat(analytics): replace manual refresh button with counter badge + ad dialog

The Refresh IconButton in the AI Insights card is replaced by a
tappable counter badge: shows remaining free regenerations (1-5)
or 'Watch Ad' when the budget is exhausted. Tapping it triggers
the ad-gated loadAiInsight(activity) flow. On free-budget
exhaustion, an AlertDialog offers 'Watch Ad' (resume generation
after reward) or 'Later' (dismiss). Mirrors the DebtDetail ad UX."
```

### Task 8: Push Batch 2 + verify CI

- [ ] **Step 1: Push & monitor CI**

```bash
git push origin main
```

Watch Actions. Expected green in ~6-7 min. If lint flags unused imports in `AnalyticsScreen.kt` (the removed `Icons.Default.Refresh`), remove them and re-push.

---

## Batch 3: Onboarding Sign-in/Sign-up/Skip + 18-language strings

### Task 9: Add new localized strings to all 18 languages

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

New keys needed:
- `sign_in_google` (likely present, verify)
- `sign_in_google_sync` (likely present, verify)
- `or_continue_with` — "or continue with"
- `email` — already present?
- `password`
- `sign_up` — register new account
- `sign_in` — sign in with email
- `dont_have_account` — "Don't have an account?"
- `already_have_account` — "Already have an account?"
- `skip_for_now` — "Skip for now"
- `later` (likely present, verify)
- `free_insights_left` — "Free insights left: {n}" (use simple "{n} free insights left" — for analytics badge alt-text; the badge itself uses numeric only)
- `account_setup_skip_note` — "You can sign in later from Settings"

**Family fallback rule:** `mr`/`pa`/`gu`/`ne`/`sa` fall back to `hi` when key missing. **But** the lookup returns the key-as-literal for unknown keys, NOT the fallback for these specific languages unless we explicitly add the entry. To be safe: add every new key to all 18 language maps INCLUDING the family-fallback languages, using `hi` translation copy `mr/pa/gu/ne/sa`. Verify by reading `LocalizedString.kt` lookups.

- [ ] **Step 1: Verify which keys already exist**

```bash
grep -n '"sign_in_google"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"later"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"email"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"password"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"sign_up"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"sign_in"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"skip"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"watch_ad"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
grep -n '"free_regenerations_desc"' app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
```

For any key returning zero matches or only present in `en`/`hi` lines, add to all 18 language maps.

- [ ] **Step 2: Add missing keys to each language map**

For each language map in `LocalizedString.kt` (the `en`, `hi`, `es`, `fr`, `de`, `it`, `pt`, `ru`, `ja`, `ko`, `zh`, `ar`, `tr`, `id`, `vi`, `th`, `mr`, `pa` blocks — find them by the language code keys referenced in the `lookup(lang, key)` function), add a line for each missing key with the language-appropriate translation. For brevity, the agent dispatches this task with the rule: "translate from English; for `mr`/`pa`/`gu`/`ne`/`sa` use the same string as `hi` (Hinglish); for RTL `ar` keep RTL direction; for `de`/`fr`/`es`/`it`/`pt` use natural translations; for `ja`/`ko`/`zh`/`vi`/`th`/`id` use natural translations; for `ru`/`tr` use natural; for `en` use the English originals below."

**English originals:**
- `sign_in_google`: "Sign in with Google"
- `sign_in_google_sync`: "Sync your debts across devices"
- `or_continue_with`: "or continue with"
- `email`: "Email"
- `password`: "Password"
- `sign_up`: "Sign up"
- `sign_in`: "Sign in"
- `dont_have_account`: "Don't have an account? Sign up"
- `already_have_account`: "Already have an account? Sign in"
- `skip_for_now`: "Skip for now"
- `later`: "Later"
- `watch_ad`: "Watch Ad"
- `free_regenerations_desc`: "Watch a short ad to get 5 more free AI insights"
- `account_setup_skip_note`: "You can sign in later from Settings"
- `lets_go` (verify exists): "Let's go"

Note to agent: Family-fallback languages (`mr`, `pa`, `gu`, `ne`, `sa`) — copy `hi` translations verbatim. If the file's structure does not have a top-level map per language code (e.g. it uses nested `get(code)` returning a `Map<String, String>`), insert each new key into the appropriate map.

- [ ] **Step 3: Commit (task 9)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
git commit -m "feat(i18n): add 14 keys for onboarding auth + analytics ad UX

Adds sign_in_google_sync, or_continue_with, sign_up,
dont_have_account, already_have_account, skip_for_now,
account_setup_skip_note, free_regenerations_desc, watch_ad,
later to all 18 language maps. Family-fallback languages
(mr/pa/gu/ne/sa) inherit hi (Hinglish) translations."
```

### Task 10: Extend `OnboardingViewModel` with `AuthManager`

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt`

**Interfaces:**
- `OnboardingViewModel @Inject constructor(prefs: AppPreferences, auth: AuthManager)` — new `auth` param.
- New methods: `signInWithGoogle(activity: Activity, onResult: (success: Boolean) -> Unit)`, `signUpEmailPassword(email: String, password: String, onResult: (success: Boolean, error: String?) -> Unit)`, `signInEmailPassword(email, password, onResult)`. The `result` callback drives UI state (toast/dialog).

- [ ] **Step 1: Inject `AuthManager`**

Change OnboardingViewModel.kt's constructor signature:
```kotlin
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val prefs: AppPreferences,
    private val auth: AuthManager
) : ViewModel() {
```
Add the import: `import com.dhanuk.debtbro.data.firebase.AuthManager`.

- [ ] **Step 2: Add `signInWithGoogle` method**

```kotlin
    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    fun signInWithGoogle(activity: android.app.Activity, onResult: (Boolean) -> Unit) = viewModelScope.launch {
        val result = auth.signInWithGoogle(activity)
        if (result.isSuccess) {
            val user = result.getOrThrow()
            prefs.saveGoogleUser(user.displayName ?: "", user.email ?: "", user.photoUrl?.toString() ?: "")
            onResult(true)
        } else {
            _authError.value = result.exceptionOrNull()?.message ?: "Sign-in failed"
            onResult(false)
        }
    }
```
Add import for `kotlinx.coroutines.flow.asStateFlow` (verify file already imports `asStateFlow` — it does).

- [ ] **Step 3: Add `signUpEmailPassword` + `signInEmailPassword` methods**

Check `AuthManager.kt` first — does it expose `signUpWithEmailPassword` and `signInWithEmailPassword`? If not, add those two methods to `AuthManager.kt`. Run:
```bash
grep -n "fun signUpWithEmail\|fun signInWithEmail" app/src/main/java/com/dhanuk/debtbro/data/firebase/AuthManager.kt
```
If found, use them; if not, add minimal versions to AuthManager following FirebaseAuth patterns (currentUser.createUserWithEmailAndPassword → await; signInWithEmailAndPassword → await; on success update profile display name = entered name from onboarding).

For now, write the OnboardingViewModel methods assuming these exist:
```kotlin
    fun signUpEmailPassword(
        email: String,
        password: String,
        name: String,
        onResult: (Boolean, String?) -> Unit
    ) = viewModelScope.launch {
        val result = auth.signUpWithEmailPassword(email, password, name)
        if (result.isSuccess) {
            onResult(true, null)
        } else {
            onResult(false, result.exceptionOrNull()?.message)
        }
    }

    fun signInEmailPassword(
        email: String,
        password: String,
        onResult: (Boolean, String?) -> Unit
    ) = viewModelScope.launch {
        val result = auth.signInWithEmailPassword(email, password)
        if (result.isSuccess) {
            onResult(true, null)
        } else {
            onResult(false, result.exceptionOrNull()?.message)
        }
    }
```

- [ ] **Step 4: Add `signUpWithEmailPassword`/`signInWithEmailPassword` to `AuthManager.kt` if missing**

In `AuthManager.kt`, after the existing Google sign-in methods, add (verify imports for EmailAuthProvider/UserProfileChangeRequest already present — they are per the existing imports):
```kotlin
    suspend fun signUpWithEmailPassword(email: String, password: String, displayName: String): Result<FirebaseUser> = runCatching {
        auth.createUserWithEmailAndPassword(email, password).await()
        val user = auth.currentUser ?: error("Sign-up failed")
        if (displayName.isNotBlank()) {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(displayName).build()).await()
        }
        user
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "signUpWithEmailPassword failed: ${e.message}", e)
    }

    suspend fun signInWithEmailPassword(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await()
        auth.currentUser ?: error("Sign-in failed")
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "signInWithEmailPassword failed: ${e.message}", e)
    }
```

Check the existing `AppPreferences.kt` for `saveGoogleUser` signature (used in step 2 above). If the method is named differently (`saveGoogleUserProfile`?), use the actual name. Run:
```bash
grep -n "fun saveGoogleUser\|fun saveGoogleUserProfile\|fun setUserProfile" app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt
```
Adjust the `signInWithGoogle` call site to use the actual method name.

- [ ] **Step 5: Commit (task 10)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt
git add app/src/main/java/com/dhanuk/debtbro/data/firebase/AuthManager.kt
git commit -m "feat(onboarding): add AuthManager injection + email/Google sign-in

OnboardingViewModel gains AuthManager dep + signInWithGoogle,
signUpEmailPassword, signInEmailPassword methods. AuthManager
gains matching signUpWithEmailPassword (sets displayName) and
signInWithEmailPassword. Page 5 of the onboarding pager will
expose these to the user."
```

### Task 11: Extend `OnboardingScreen` page 5 with auth options

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt` (Page5Name + the CTA button handler)

**Pattern reference:** `AuthScreen.kt` for the Google sign-in button + email/password form. Read it first via `grep -r "fun AuthScreen" app/src/main/`.

- [ ] **Step 1: Study `AuthScreen.kt` for the Google button + email/password form**

```bash
grep -rn "GoogleSignInCard\|GoogleSignInButton\|onGoogleSignIn" app/src/main/java/com/dhanuk/debtbro/presentation/
find app/src/main/java/com/dhanuk/debtbro/presentation -name "AuthScreen.kt"
```
Read AuthScreen.kt to copy the exact Google button composable. If it uses a `GoogleSignInCard` component from `presentation/components/`, reuse that.

- [ ] **Step 2: Add a sign-in state to `OnboardingScreen`**

Add state for email + password fields and a "show email form" toggle in `OnboardingScreen`:
```kotlin
    var showEmailForm by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var authBusy by remember { mutableStateOf(false) }
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    val context = androidx.compose.ui.platform.LocalContext.current
```
At the top of `OnboardingScreen`'s composable function body.

- [ ] **Step 3: Extend `Page5Name` signature**

Change `fun Page5Name(name: String, onNameChange: (String) -> Unit)` to accept more callbacks. Cleaner: instead of bolting all the auth logic into `Page5Name`, create a NEW page-5 body that contains the name field PLUS auth actions. Rewrite the body of `Page5Name` to take a richer param set. Replace the callsite (line 76) accordingly.

Update the `when (page)` block at line 76:
```kotlin
                    4 -> Page5Name(
                        name = name,
                        onNameChange = { viewModel.onNameChange(it) },
                        showEmailForm = showEmailForm,
                        onToggleEmailForm = { showEmailForm = !showEmailForm },
                        email = email,
                        password = password,
                        onEmailChange = { email = it },
                        onPasswordChange = { password = it },
                        authBusy = authBusy,
                        authError = authError,
                        onGoogleSignIn = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                authBusy = true
                                viewModel.signInWithGoogle(activity) { success ->
                                    authBusy = false
                                    if (success) {
                                        viewModel.completeOnboarding(onOnboardingComplete)
                                    }
                                }
                            }
                        },
                        onEmailSubmit = {
                            authBusy = true
                            if (showEmailForm /* sign-up mode */) {
                                viewModel.signUpEmailPassword(email, password, name) { ok, err ->
                                    authBusy = false
                                    if (ok) viewModel.completeOnboarding(onOnboardingComplete)
                                    else authError = err
                                }
                            } else {
                                viewModel.signInEmailPassword(email, password) { ok, err ->
                                    authBusy = false
                                    if (ok) viewModel.completeOnboarding(onOnboardingComplete)
                                    else authError = err
                                }
                            }
                        },
                        onSkip = { viewModel.completeOnboarding(onOnboardingComplete) }
                    )
```
Pass `showEmailForm` and the toggle through.

- [ ] **Step 4: Rewrite `Page5Name` body**

Replace the existing `Page5Name` composable with one that renders: name field (existing), OR-continue-with divider, Google sign-in button, "use email instead" toggle, email/password fields (only when `showEmailForm`), submit button, "Skip for now" text link, and the `account_setup_skip_note` caption. Pattern from `AuthScreen.kt`:

```kotlin
@Composable
fun Page5Name(
    name: String,
    onNameChange: (String) -> Unit,
    showEmailForm: Boolean,
    onToggleEmailForm: () -> Unit,
    email: String,
    password: String,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    authBusy: Boolean,
    authError: String?,
    onGoogleSignIn: () -> Unit,
    onEmailSubmit: () -> Unit,
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
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = name,
            onValueChange = { if (it.length <= 30) onNameChange(it.trimStart()) },
            label = { Text(LocalizedString.get("your_name_label")) },
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
        Spacer(Modifier.height(24.dp))

        GoogleSignInCard(onClick = onGoogleSignIn, busy = authBusy, caption = LocalizedString.get("sign_in_google_sync"))
        Spacer(Modifier.height(12.dp))

        Text(LocalizedString.get("or_continue_with"), color = extra.subtitleGray, fontSize = UITokens.FontCaption)
        TextButton(onClick = onToggleEmailForm) {
            Text(if (showEmailForm) LocalizedString.get("already_have_account") else LocalizedString.get("dont_have_account"))
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
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusetBorderColor = MaterialTheme.colorScheme.outline
                )
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = onEmailSubmit,
                enabled = !authBusy && email.isNotBlank() && password.length >= 6,
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) {
                Text(
                    if (showEmailForm) LocalizedString.get("sign_up") else LocalizedString.get("sign_in"),
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
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
}
```
Fix `unfocusetBorderColor` typo → `unfocusedBorderColor`. Add necessary imports (verify which exist):
- `androidx.compose.foundation.layout.height`
- `androidx.compose.material3.OutlinedTextField`/`OutlinedTextFieldDefaults`
- `androidx.compose.ui.text.input.KeyboardType`
- `androidx.compose.ui.text.input.PasswordVisualTransformation`
- `androidx.compose.foundation.shape.RoundedCornerShape`
- `com.dhanuk.debtbro.presentation.components.GoogleSignInCard` — IF that composable exists. Verify by reading AuthScreen.kt: if AuthScreen uses `GoogleSignInCard`, reuse it; else inline the Google sign-in button.

If `GoogleSignInCard` does not exist as a component, search for the actual Google button composable used in `AuthScreen.kt` and call THAT by name instead. Either way, the top of the page must surface a "Sign in with Google" button.

- [ ] **Step 5: Update the CTA button at the bottom of `OnboardingScreen`**

The current page-5 CTA at line 98-118 calls `viewModel.completeOnboarding(onOnboardingComplete)` for page 4. With the new page 5 design, the bottom CTA on page 5 should NOT auto-complete onboarding — the auth actions on the page do that. Two options:

  **Option A (simpler):** Have the bottom CTA on page 5 invoke `onSkip` (the skip path): `viewModel.completeOnboarding(onOnboardingComplete)`. Keep the existing line 104 call exactly as is — it acts as the "Skip" path so non-signed-in users can still continue. The Google/email buttons do their own auth + completion.

  Go with Option A. Keep the existing CTA logic as the skip fallback. No CTA change needed.

- [ ] **Step 6: Build-check, fix compile errors, commit (task 11)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt
git commit -m "feat(onboarding): page 5 adds Google/email sign-in + Skip

Page 5 of the onboarding pager now exposes: name input (kept),
Sign in with Google button, optional email/password form (toggle
with 'Don't have an account? Sign up' / 'Already have an
account? Sign in'), and 'Skip for now'. Successful auth calls
completeOnboarding immediately. Skip mirrors the existing CTA path.
The bottom pager CTA remains the skip fallback for page 5."
```

### Task 12: Push Batch 3 + verify CI

```bash
git push origin main
```
Watch CI. Fix any unused-import or missing-symbol errors flagged by lint.

---

## Batch 4: Cloud Function `cancelAccountDeletion` + client wiring + Settings revert dialog

### Task 13: Create `functions/index.js` with deletion endpoints

**Files:**
- Create: `functions/index.js`
- Create: `functions/package.json`
- Create: `firebase.json` (only if it doesn't exist; check first)

**Interfaces:**
- HTTP endpoint `requestAccountDeletion` (POST, takes `{ uid }` in body, enqueues a 24h grace-window Firestore doc).
- HTTP endpoint `cancelAccountDeletion` (POST, takes `{ uid }` in body, clears the queue doc).
- Scheduled `processDeletionQueue` (every 30 min) sweeps Firestore queue for entries whose `requestAt + 24h < now`, calls `admin.auth().deleteUser(uid)` + wipes Firestore userData + clears the queue doc.
- Client calls `cancelAccountDeletion` via `AccountDeletionApiService` to undo within 24h.

- [ ] **Step 1: Check existing `functions/` and `firebase.json`**

```bash
ls -la /home/ubuntu/DebtBro/functions 2>/dev/null || echo "functions dir missing"
ls -la /home/ubuntu/DebtBro/firebase.json /home/ubuntu/DebtBro/.firebaserc 2>/dev/null || echo "firebase config missing"
```
If `functions/` is missing, this is greenfield. If `firebase.json` is missing, we need a minimal config so `firebase deploy --only functions` works.

- [ ] **Step 2: Write `functions/package.json`**

```json
{
  "name": "debtbro-functions",
  "version": "1.0.0",
  "description": "Account deletion queue + GDPR grace-window for DebtBro",
  "main": "index.js",
  "engines": { "node": "20" },
  "dependencies": {
    "firebase-admin": "^12.6.0",
    "firebase-functions": "^5.1.0"
  },
  "private": true
}
```

- [ ] **Step 3: Write `functions/index.js`**

```javascript
const functions = require("firebase-functions");
const admin = require("firebase-admin");

admin.initializeApp();

const db = admin.firestore();
const GRACE_WINDOW_MS = 24 * 60 * 60 * 1000;

exports.requestAccountDeletion = functions
  .https.on Call(async (req, res) => {
    const uid = req.data?.uid;
    if (!uid) {
      res.status(400).json({ error: "uid is required" });
      return;
    }
    await db.collection("deletionQueue").doc(uid).set({
      uid,
      requestAt: admin.firestore.FieldValue.serverTimestamp(),
      status: "pending"
    });
    res.status(200).json({ ok: true, graceWindowHours: 24 });
  });

exports.cancelAccountDeletion = functions
  .https.onCall(async (req, res) => {
    const uid = req.data?.uid;
    if (!uid) {
      res.status(400).json({ error: "uid is required" });
      return;
    }
    await db.collection("deletionQueue").doc(uid).delete();
    res.status(200).json({ ok: true, cancelled: true });
  });

exports.processDeletionQueue = functions
  .pubsub.schedule("every 30 minutes")
  .onRun(async (context) => {
    const now = admin.firestore.Timestamp.now();
    const cutoff = new Date(now.toMillis() - GRACE_WINDOW_MS);
    const snapshot = await db.collection("deletionQueue")
      .where("status", "==", "pending")
      .where("requestAt", "<", cutoff)
      .get();
    if (snapshot.empty) {
      console.log("processDeletionQueue: no pending entries past grace window");
      return null;
    }
    const batch = db.batch();
    const deletions = [];
    snapshot.forEach((doc) => {
      const uid = doc.get("uid");
      deletions.push(
        admin.auth().deleteUser(uid)
          .then(() => wipeUserData(uid))
          .then(() => batch.delete(doc.ref))
          .catch((err) => console.error(`Failed to delete ${uid}:`, err.message))
      );
    });
    await Promise.all(deletions);
    await batch.commit();
    return null;
  });

async function wipeUserData(uid) {
  const userCollections = ["debts", "payments", "splits", "userSettings"];
  for (const coll of userCollections) {
    const snap = await db.collection("users").doc(uid).collection(coll).get();
    const batch = db.batch();
    snap.forEach((d) => batch.delete(d.ref));
    await batch.commit();
  }
  await db.collection("users").doc(uid).delete().catch(() => {});
}
```
Fix the typo `on Call` → `onCall` (and `functions.https.on Call` → `functions.https.onCall`).

- [ ] **Step 4: Write `firebase.json`** (skip if exists)

```json
{
  "functions": {
    "source": "functions",
    "runtime": "nodejs20"
  }
}
```

- [ ] **Step 5: Write `.firebaserc`** (skip if exists)

```json
{
  "projects": {
    "default": "debtbro-4e3c9"
  }
}
```
Verify the project ID matches the existing one referenced in `build.gradle.kts:65` (`debtbro-4e3c9` per the Cloud Functions URL pattern). Read the existing URL to confirm: the BuildConfig default is `https://us-central1-debtbro-4e3c9.cloudfunctions.net/requestAccountDeletion`. YES, project = `debtbro-4e3c9`.

- [ ] **Step 6: Commit (task 13)**

```bash
git add functions/index.js functions/package.json firebase.json .firebaserc
git commit -m "feat(functions): account deletion queue + cancel endpoint + scheduled sweep

Cloud Functions: requestAccountDeletion enqueues a Firestore doc
with 24h grace window. cancelAccountDeletion clears it.
processDeletionQueue (every 30 min) sweeps past-grace entries,
deletes Firebase Auth user + wipes user's Firestore data, then
clears the queue doc. Implements GDPR-friendly delayed deletion."
```

### Task 14: Add `cancelAccountDeletion` to client side

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/network/AccountDeletionApiService.kt`
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/firebase/AuthManager.kt`

- [ ] **Step 1: Add `cancelDeletion` to `AccountDeletionApiService`**

Open `AccountDeletionApiService.kt`. Add a method mirroring `requestDeletion`:
```kotlin
    @POST
    suspend fun cancelDeletion(
        @Url url: String,
        @Body request: AccountDeletionRequest
    ): retrofit2.Response<Unit>
```
Reuse the existing `AccountDeletionRequest(uid)` model (already shared).

- [ ] **Step 2: Add `cancelAccountDeletion(uid)` to `AuthManager.kt`**

Mirroring `requestAccountDeletion`, add a `cancelAccountDeletion` method. Same SSRF/HTTPS/trusted-host guards. After the existing `requestAccountDeletion`:

```kotlin
    suspend fun cancelAccountDeletion(uid: String): Result<Unit> = runCatching {
        if (accountDeletionUrl.isBlank()) return@runCatching
        val cancelUrl = accountDeletionUrl.removeSuffix("/requestAccountDeletion") + "/cancelAccountDeletion"
        val parsed = cancelUrl.toHttpUrlOrNull() ?: run {
            android.util.Log.w("AuthManager", "cancelAccountDeletion: URL is malformed, skipping — $cancelUrl")
            return@runCatching
        }
        if (parsed.scheme != "https") {
            android.util.Log.w("AuthManager", "cancelAccountDeletion: non-HTTPS scheme '${parsed.scheme}', skipping")
            return@runCatching
        }
        val host = parsed.host.lowercase()
        val isTrustedHost = host == "cloudfunctions.net" ||
            host.endsWith(".cloudfunctions.net") ||
            host.endsWith(".googleapis.com")
        if (!isTrustedHost) {
            android.util.Log.w("AuthManager", "cancelAccountDeletion: untrusted host '$host', refusing to POST")
            return@runCatching
        }
        accountDeletionApi.cancelDeletion(
            url = cancelUrl,
            request = AccountDeletionRequest(uid)
        )
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "cancelAccountDeletion failed: ${e.message}", e)
    }
```
The cancel URL is derived from `accountDeletionUrl` by swapping `/requestAccountDeletion` suffix. Verify the `BuildConfig.ACCOUNT_DELETION_URL` actually ends with `/requestAccountDeletion` — re-read `build.gradle.kts:65` if needed; the value `https://us-central1-debtbro-4e3c9.cloudfunctions.net/requestAccountDeletion` does end with that suffix, so the swap works.

- [ ] **Step 3: Commit (task 14)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/data/network/AccountDeletionApiService.kt
git add app/src/main/java/com/dhanuk/debtbro/data/firebase/AuthManager.kt
git commit -m "feat(auth): client-side cancelAccountDeletion

AccountDeletionApiService gains cancelDeletion(@Url, @Body).
AuthManager gains cancelAccountDeletion(uid) mirroring the SSRF
guards on requestAccountDeletion. The cancel URL is derived from
the existing ACCOUNT_DELETION_URL by swapping the
/requestAccountDeletion suffix -> /cancelAccountDeletion."
```

### Task 15: Settings deletion → 2-button revert + rollback of pending request on auth success

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt` (update `requestAccountDeletion` to set pending timestamp, add `cancelAccountDeletion` method, add auto-restore on next launch)
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt` (delete dialog: 2-button "Cancel deletion" + "Delete account")

- [ ] **Step 1: Add `cancelAccountDeletion` to `SettingsViewModel`**

Open `SettingsViewModel.kt`. Find existing `requestAccountDeletion(context, onSuccess)` (lines 399-430 per investigation) — it sets a pending timestamp + enqueues WorkManager. Add a sibling cancel method:

```kotlin
    fun cancelAccountDeletion(onSuccess: () -> Unit) = viewModelScope.launch {
        val uid = auth.currentUser?.uid
        if (uid != null) {
            auth.cancelAccountDeletion(uid)
        }
        prefs.clearPendingAccountDeletion()
        accountDeletionWorkerScheduler.cancelEnqueued()
        onSuccess()
    }
```
Check existing `AppPreferences.kt` for the method names that set/clear the pending timestamp. Run:
```bash
grep -n "PendingAccountDeletion\|pendingDeletion\|accountDeletionPending" app/src/main/java/com/dhanuk/debtbro/data/datastore/AppPreferences.kt app/src/main/java/com/dhanuk/debtbro/worker/ app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt
```
Adjust the actual method names (`prefs.clearPendingAccountDeletion()` and `accountDeletionWorkerScheduler.cancelEnqueued()` may need to be the actual names — read `SettingsViewModel.kt` first, then write methods that mirror the existing `requestAccountDeletion` plumbing).

- [ ] **Step 2: Add "Cancel deletion" button to the Settings delete-account dialog**

Find the delete-account confirmation `AlertDialog` in `SettingsScreen.kt`. Update it to show TWO buttons when a deletion is pending: a "Cancel deletion" button (calls `viewModel.cancelAccountDeletion { ... }`) and the existing "Delete account" button (kept). The dialog's `text` should mention "Your account will be deleted in 24 hours. You can cancel before then."

Add a state collection near the top of `SettingsScreen`:
```kotlin
val pendingDeletionAt by viewModel.pendingDeletionTimestamp.collectAsStateWithLifecycle()
val isPending = pendingDeletionAt > 0L
```
Adjust the dialog:
```kotlin
                    AlertDialog(
                        onDismissRequest = { viewModel.dismissDeleteDialog() },
                        title = { Text(LocalizedString.get("account_deletion")) },
                        text = {
                            Text(
                                if (isPending) LocalizedString.get("deletion_pending_desc")
                                else LocalizedString.get("delete_account_desc")
                            )
                        },
                        confirmButton = {
                            if (isPending) {
                                TextButton(onClick = {
                                    viewModel.cancelAccountDeletion {
                                        viewModel.dismissDeleteDialog()
                                    }
                                }) { Text(LocalizedString.get("cancel_deletion")) }
                            } else {
                                TextButton(onClick = {
                                    viewModel.requestAccountDeletion(context) {
                                        viewModel.dismissDeleteDialog()
                                    }
                                }) { Text(LocalizedString.get("delete_account")) }
                            }
                        },
                        dismissButton = {
                            TextButton(onClick = { viewModel.dismissDeleteDialog() }) {
                                Text(LocalizedString.get("later"))
                            }
                        }
                    )
```
Add corresponding localized keys (`account_deletion`, `deletion_pending_desc`, `delete_account`, `cancel_deletion`, `delete_account_desc`) to `LocalizedString.kt` across 18 languages similar to Task 9.

- [ ] **Step 3: Auto-restore dialog on `AuthViewModel.onAuthSuccess` when a deletion is pending**

When a user re-signs into an account whose deletion is pending, surface a dialog offering to cancel the deletion. Find `AuthViewModel.kt` or wherever the auth-success callback lives:

```bash
grep -rln "onAuthSuccess\|fun onAuthComplete\|AuthViewModel" app/src/main/java/com/dhanuk/debtbro/presentation/
```
On auth success, check `prefs.pendingDeletionTimestamp`. If > 0 and within 24h, surface a `MutableStateFlow<Boolean>` say `showDeletionRestoreDialog`. The UI collects it and shows an AlertDialog offering "Restore account" → `viewModel.cancelAccountDeletion { }`.

Add to the relevant ViewModel (likely AuthViewModel or the host NavGraph-level VM):
```kotlin
    private val _showDeletionRestore = MutableStateFlow(false)
    val showDeletionRestore: StateFlow<Boolean> = _showDeletionRestore.asStateFlow()

    fun onAuthSuccess() {
        viewModelScope.launch {
            val ts = prefs.pendingDeletionTimestamp.first()
            if (ts > 0L) {
                _showDeletionRestore.value = true
            }
        }
    }

    fun confirmRestoreAccount() = viewModelScope.launch {
        cancelAccountDeletion { _showDeletionRestore.value = false }
    }

    fun dismissDeletionRestore() { _showDeletionRestore.value = false }
```
Wire the corresponding UI dialog where the screen collects `authViewModel.showDeletionRestore`. If the existing flow does not have `AuthViewModel`, place this logic in the host `MainActivity`-level state holder instead. Read `MainActivity.kt` + `NavGraph.kt` first to find the right insertion point.

- [ ] **Step 4: Commit (task 15)**

```bash
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt
git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt
git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt
# + any AuthViewModel / MainActivity edits
git commit -m "feat(settings): 2-button delete-account dialog + auto-restore on re-login

SettingsViewModel gains cancelAccountDeletion which fires the
client cancelAccountDeletion request + clears pending timestamp
+ cancels the WorkManager enqueue. SettingsScreen's delete
dialog now shows 'Cancel deletion' (calls cancelAccountDeletion)
when a deletion is pending, otherwise the existing 'Delete
account' flow. On subsequent sign-in to an account with a
pending deletion, a restore dialog offers one-tap undo."
```

### Task 16: Push Batch 4 + verify CI

```bash
git push origin main
```

CI is for the app only — `functions/` is deployed separately by the user via `firebase deploy --only functions`. Push the functions source to the repo so it's tracked, but remind the user to deploy manually.

---

## Batch 5: InfinityFree Page Updates

### Task 17: Update HTML pages on InfinityFree via File Manager UI

**Files:** (no local files — runtime manipulation via Chrome DevTools MCP on the InfinityFree File Manager)

**Note:** Cannot script this with bash/curl due to InfinityFree's JS anti-bot challenge. Use Chrome DevTools MCP (`chrome-devtools_navigate_page` to `https://filemanager.ai/new3/index.php`, log in, select target HTML file, open CodeMirror editor, use `chrome-devtools_evaluate_script` to call `document.querySelector('.CodeMirror').CodeMirror.setValue(newContent)`, save).

- [ ] **Step 1: User-driven manual confirmation**

Pause and ask the user which HTML pages they want updated and what content changes are needed. The previous summary does not specify the exact pages. Likely targets:
- A landing/privacy page (`privacy.html` or `index.html`)
- A terms-of-service page (`terms.html`)
- A support/contact page

Confirm with user before proceeding — InfinityFree updates are sensitive, irreversible (no version control), and need exact content from the user.

- [ ] **Step 2: Navigate to InfinityFree File Manager and log in**

(Only proceed if user confirmed Step 1.)

```bash
# Use chrome-devtools_navigate_page to https://filemanager.ai/new3/index.php
# Complete login (user may need to enter credentials via chrome-devtools_fill)
```

- [ ] **Step 3: Open target HTML in CodeMirror, replace content**

Use `chrome-devtools_evaluate_script` with:
```js
() => {
  const cm = document.querySelector('.CodeMirror').CodeMirror;
  const newContent = `<PASTE USER-CONFIRMED HTML HERE>`;
  cm.setValue(newContent);
  // Trigger the File Manager's Save action (usually a button by id like 'save' or a Save icon)
  document.querySelector('button[title="Save"]')?.click();
  return 'saved';
}
```

- [ ] **Step 4: User runs `firebase deploy --only functions`**

Remind the user to deploy Cloud Functions separately from their machine. Their Firebase CLI credentials are not in this environment.

---

## Post-Implementation

### Task 18: Final verification

- [ ] **Step 1: Verify all 5 batch commits landed on `main`**

```bash
git log --oneline -20
```
Expect to see commits for: AI truncation fix, theme default, empty state icons, Settings AI Setup removal, analytics rewarded ad, onboarding auth, strings, cloud function, cancelAccountDeletion, 2-button dialog.

- [ ] **Step 2: Confirm latest CI run is green**

Check the most recent GitHub Actions workflow run.

- [ ] **Step 3: Remind user to deploy Cloud Functions**

```bash
echo "Reminder: run 'cd /home/ubuntu/DebtBro && firebase deploy --only functions' from YOUR machine to ship the new requestAccountDeletion/cancelAccountDeletion/processDeletionQueue Cloud Functions."
```

- [ ] **Step 4: Remind user of InfinityFree updates pending**

Surface the InfinityFree pending-action note from Task 17.

---

## Self-Review Notes

**Spec coverage spot-check:**
- AI truncation fix → Task 1 ✓ (3 sites + token bumps)
- Theme default → LIGHT → Task 2 ✓
- Empty state emoji → Material Icons → Task 3 ✓
- Settings AI Setup removal → Task 5 ✓
- Analytics rewarded-ad unlock → Tasks 6, 7 ✓
- Onboarding sign-in/sign-up/skip → Tasks 10, 11 ✓
- i18n string additions → Task 9 ✓
- Cloud Function `cancelAccountDeletion` → Tasks 13, 14 ✓
- Settings 2-button revert + auto-restore → Task 15 ✓
- InfinityFree updates → Task 17 ✓ (pending user confirmation)

**Placeholder flag:** Task 17's HTML content is intentionally user-supplied — flagged as "user must confirm" rather than empty placeholder. This is the only open-ended spot; all other tasks have concrete code blocks. Confirm with user before executing Batch 5.

**Type/signature consistency:** `loadAiInsight(activity)` consistent across Tasks 6 & 7. `cancelAccountDeletion(uid)` consistent between Tasks 14 & 15. `MAX_FREE_REGENERATIONS` referenced correctly. `GoogleSignInCard` usage in Task 11 assumes it exists as a component — Step 1 of Task 11 verifies this via grep before use.
