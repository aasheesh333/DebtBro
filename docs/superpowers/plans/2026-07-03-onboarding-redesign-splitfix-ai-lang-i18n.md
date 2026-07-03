# Onboarding Redesign + Split Crash Fix + AI Language Gating + i18n Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship 4 workstreams — Split bill crash bugfix (2 Crashlytics-confirmed crashes), AI roast language gating to selected language, i18n full 25-language coverage (currently 10 maps, ~5179 entries to add), and onboarding flow redesign (split combined name+auth page into 2 separate pages).

**Architecture:** Android (Kotlin/Jetpack Compose) MVVM + Hilt DI single-module app. All i18n in `util/LocalizedString.kt` (giant `mapOf` of language maps; no `strings.xml`). Mockups already generated at opendesign project `debtbro-onboarding-redesign-495a` for the new onboarding pages.

**Tech Stack:** Kotlin 2.x, Jetpack Compose, Hilt, Room (DB v3), Firebase Auth/Firestore, Retrofit (Gemini API), Material 3, Gson.

## Global Constraints

- **minSdk stays at 26** (Android 9 users get "problem parsing the package" if raised).
- **Never run local builds** — CI is the only verification path (~6m40s avg per build). After each batch: commit + push + watch CI green before next batch.
- **No git worktrees** — direct pushes to `main`, CI auto-deploys GitHub Releases.
- **No tests** — project has zero test files per AGENTS.md ANTI-PATTERNS. Verification = successful CI build (compile + lint).
- **No comments in code** unless the file already has explanatory comments AND the change site is non-obvious. Prefer self-documenting code; preserve existing comments.
- **10 language maps exist today** in `LocalizedString.kt` (en/hi/es/fr/de/ja/mr/pa/gu/bn — pa/gu/bn are 29-36 entry stubs). The picker advertises 25 languages — 15 have ZERO coverage and fall back via `familyFallback` to either `hi` (mr/pa/gu) or English (everything else).
- **Translation generator**: opencode via model `nvidia/z-ai/glm-5.2`. Translations are fluent but not native-peer-reviewed. Per AGENTS.md ANTI-PATTERNS note, `LocalizedString` is an `object` with inline `mapOf` literals (NOT `strings.xml` resources).
- **`LocalizedString.get(key)`** returns the key literally if not found anywhere (per `LocalizedString.kt:65`), so every new key MUST be added to all advertised language maps.
- **`proguard-rules.pro` exists** at `app/proguard-rules.pro` (already referenced by `app/build.gradle.kts`).
- **Existing onboarding uses bottom-shared "Continue" button** (`OnboardingScreen.kt:152-172`) for ALL pages, with text `"next_arrow"` for pages <4 and `"lets_go"` for page 4. With 6 pages we must preserve this shared-button pattern (the per-page-CTA design proposed in the spec's mockups is for visual reference only — the actual code will keep the shared bottom Continue button).
- **Existing pager state**: `rememberPagerState(pageCount = { 5 })` at `OnboardingScreen.kt:40`.
- **`OnboardingViewModel` already implements** `signInWithGoogle`, `signUpEmailPassword`, `signInEmailPassword`, `authError`, `dismissAuthError`, `completeOnboarding` per prior Batch 3 work (commits `fd4efb1`, `864bcbe`). The forgot-password flow exists in `AuthViewModel.kt:168-206` (using `auth.sendPasswordResetEmail`).
- **`adb` not available** in this VPS — verification is CI build only, then user manual tap-through on their Android phone.

---

## File Structure

### Files Modified
- `app/proguard-rules.pro` — append Gson keep rules for `TypeToken` (Section 2 fix layer 2)
- `app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt` — extract `buildRoastPersona` helper, branch on `isIndicFamily`, gate "Hinglish welcome/ok" strings
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt` — pager `pageCount` 5→6, split `Page5Name` into `Page5aName` + `Page6Auth`, page indicator `repeat(5)`→`repeat(6)`, top-right Skip target page 4, bottom shared Continue button labels per page (page 4: Continue → page 5; page 5: `lets_go` → completeOnboarding)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt` — add `_isSignInMode`, `_showForgotPasswordDialog`, `_forgotPasswordError`, `toggleAuthMode`, `showForgotPasswordDialog`, `dismissForgotPasswordDialog`, `sendPasswordResetEmail` (the last calls `auth.sendPasswordResetEmail` in `viewModelScope.launch`)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt` — `ContactPickerBottomSheet` lambda: rewrite query URI to `CommonDataKinds.Phone.CONTENT_URI`, projection to `Phone.NUMBER` constant, wrap in `runCatching`, add `try { } catch (SecurityException)` for permission revocation
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt` — replace 2 anonymous `TypeToken` sites with `LIST_STRING_TYPE` constant, wrap 3 `viewModelScope.launch` bodies in `runCatching`, add `SharedFlow<String> snackbar` for split error feedback
- `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt` — add 31 new keys to all 25 language maps; fill 834 missing entries across 10 existing maps; create 15 new full maps (4035 entries); dedupe `settings` key in `pa` map; remove `familyFallback` entries that now have their own maps
- Hardcoded UI string sites (~31 sites across AddDebtBottomSheet, DebtDetailScreen, DebtDetailViewModel, SettingsScreen, SettingsViewModel, AuthScreen, AuthViewModel, SplitScreen, SplitViewModel, AnalyticsViewModel) — replace `Text("hardcoded literal")` with `Text(LocalizedString.get("<key>"))` per inventory list in batch tasks below

### Files NOT modified
- `NavGraph.kt`, `Screen.kt` — no new routes
- `SplitRepository.kt`, `SplitDao.kt`, `SplitEntity.kt` — no DB schema changes
- `AppPreferences.kt` — language pref mechanism already exists
- `firestore.rules`, `firebase.json` — handled in prior shippable

---

## Task 1: Fix SplitViewModel TypeToken crash (Crash #1)

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt:1-169`

**Interfaces:**
- Consumes: existing `Gson`, `split.participants` JSON string field
- Produces: `SplitViewModel.createDebtsFromSplit` and `getAiSummary` no longer crash under R8

- [ ] **Step 1: Inspect the current 2 TypeToken sites**

Read `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt:120-150`.
Confirm two sites: `createDebtsFromSplit` at `:123-126` and `getAiSummary` at `:142-145`, both using `object : TypeToken<List<String>>() {}.type`.

- [ ] **Step 2: Add explicit `LIST_STRING_TYPE` constant at top of file**

Edit `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt`. After the existing imports, add:

```kotlin
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type

private val LIST_STRING_TYPE: Type =
    TypeToken.getParameterized(List::class.java, String::class.java).type
```

If `TypeToken` and `Type` are already imported, do not re-add the imports. If a `private val` can't sit at the top of the file (depends on whether there is a top-level declaration), put the constant inside the `SplitViewModel` class body's companion object: `private companion object { val LIST_STRING_TYPE: Type = TypeToken.getParameterized(List::class.java, String::class.java).type }`. Reference it as `LIST_STRING_TYPE` (the companion getter is unqualified).

- [ ] **Step 3: Replace anonymous `TypeToken` site 1 (`createDebtsFromSplit`)**

At `SplitViewModel.kt:123-126`, find:
```kotlin
Gson().fromJson(split.participants, object : TypeToken<List<String>>() {}.type)
```
Replace with:
```kotlin
Gson().fromJson(split.participants, LIST_STRING_TYPE)
```

- [ ] **Step 4: Replace anonymous `TypeToken` site 2 (`getAiSummary`)**

At `SplitViewModel.kt:142-145`, find:
```kotlin
Gson().fromJson(split.participants, object : TypeToken<List<String>>() {}.type)
```
Replace with:
```kotlin
Gson().fromJson(split.participants, LIST_STRING_TYPE)
```

- [ ] **Step 5: Add ProGuard keep rules for Gson**

Edit `app/proguard-rules.pro` — append (preserve any existing content):
```
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keep class * implements com.google.gson.TypeAdapterFactory
-keep class * implements com.google.gson.JsonSerializer
-keep class * implements com.google.gson.JsonDeserializer
```

- [ ] **Step 6: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt app/proguard-rules.pro && git commit -m "fix(split): replace anonymous TypeToken with explicit getParameterized + ProGuard keep rules

Crashlytics issue 594a7f30 (SplitViewModel.java:144 IllegalStateException 'TypeToken must be created with a type argument'):
- Replace anonymous TypeToken<List<String>>() {}.type at 2 sites (createDebtsFromSplit + getAiSummary) with TypeToken.getParameterized(List::class.java, String::class.java).type — R8 strips anonymous-class generic signature at runtime; the parameterized builder constructs the Type from explicit class objects so no signature retention is required.
- Add -keepattributes Signature, EnclosingMethod, InnerClasses and -keep class com.google.gson.** proguard rules as defense-in-depth so any future hand-rolled TypeTokens survive R8."
```

- [ ] **Step 7: Push and watch CI**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```
Open `https://github.com/aasheesh333/DebtBro/actions` and wait for green (~6m40s). Do not start Task 2 until CI is green.

---

## Task 2: Fix SplitScreen ContactPicker `data1` column crash (Crash #2)

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt:300-360` (ContactPickerBottomSheet lambda area)

**Interfaces:**
- Consumes: `context.contentResolver`
- Produces: `onContactPicked(String)` no longer throws on contacts missing `data1`

- [ ] **Step 1: Find the current Cursor query code**

Read `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt:300-360`. Identify the lambda at line 328 (`ContactPickerBottomSheet$lambda$19`) where `contentResolver.query(..., projection, ...)` is called with `arrayOf("data1")` (or similar).

- [ ] **Step 2: Add `ContactsContract` import if not already present**

Verify `import android.provider.ContactsContract` exists at top of SplitScreen.kt. If not, add it. If multiple imports share `import android.provider.*`, that's fine.

- [ ] **Step 3: Rewrite the query URI to `CommonDataKinds.Phone.CONTENT_URI`**

In the lambda body, replace URI selector like `ContactsContract.Contacts.CONTENT_URI` (or whatever generic URI was used) with:
```kotlin
val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
val projection = arrayOf(
    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
    ContactsContract.CommonDataKinds.Phone.NUMBER
)
```

- [ ] **Step 4: Wrap the cursor query in runCatching**

Replace the inline query with:
```kotlin
val cursor = runCatching {
    context.contentResolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC")
}.getOrNull()
cursor?.use { c ->
    val nameIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
    val numIdx = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
    if (nameIdx >= 0 && numIdx >= 0) {
        while (c.moveToNext()) {
            val name = c.getString(nameIdx) ?: continue
            val num = c.getString(numIdx) ?: continue
            // (existing logic that consumes name+num)
        }
    }
}
```

- [ ] **Step 5: Wrap entire lambda in try/catch (SecurityException safety net)**

If the lambda already lives in a lambda passed to `(activityResult) -> Unit`, wrap the body in:
```kotlin
try {
    // ... existing runCatching+query logic ...
} catch (_: SecurityException) {
    // Permission revoked between grant and query — silently fail
}
```

- [ ] **Step 6: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt && git commit -m "fix(split): rewrite ContactPickerBottomSheet to use CommonDataKinds.Phone contract

Crashlytics issue 94720452 (SplitScreen.kt:328 IllegalArgumentException 'Invalid column data1'):
- Replace generic ContactsContract query URI with ContactsContract.CommonDataKinds.Phone.CONTENT_URI so 'data1' (PHONE.NUMBER) is a valid column for the row.
- Use Phone.NUMBER and Phone.DISPLAY_NAME contract constants instead of raw 'data1' string.
- Wrap query in runCatching { ... }.getOrNull() so IPC/parcel failures become null cursor instead of a thrown IllegalArgumentException.
- Wrap outer cursor op in try { } catch (SecurityException) so READ_CONTACTS revocation no longer crashes."
```

- [ ] **Step 7: Push and watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 3: Add `SharedFlow<String> snackbar` to SplitViewModel + wrap 3 launches in runCatching

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt`

**Interfaces:**
- Consumes: existing `viewModelScope.launch` blocks in `createSplit`, `createDebtsFromSplit`, `getAiSummary`
- Produces: `snackbar: SharedFlow<String>` to be collected by SplitScreen (wired in Task 4)

- [ ] **Step 1: Inspect the 3 launches**

Read `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt:93-150`. Identify the 3 `viewModelScope.launch { ... }` blocks at `:108-120` (createSplit), `:122-139` (createDebtsFromSplit), `:141-149` (getAiSummary).

- [ ] **Step 2: Add `MutableSharedFlow` field**

Near the top of `SplitViewModel` class, after existing state declarations, add:
```kotlin
private val _snackbar = kotlinx.coroutines.flow.MutableSharedFlow<String>()
val snackbar = _snackbar.asSharedFlow()
```
Add the `kotlinx.coroutines.flow.asSharedFlow` import if not present.

- [ ] **Step 3: Wrap `createSplit` body in runCatching**

In the `createSplit(onCreated: (SplitEntity) -> Unit)` function, change:
```kotlin
viewModelScope.launch {
    // body — splits.insertSplit, pushSplitImmediately, etc.
}
```
to:
```kotlin
viewModelScope.launch {
    runCatching {
        // body — splits.insertSplit, pushSplitImmediately, etc.
    }.onFailure { _snackbar.tryEmit("Couldn't create split: ${it.message ?: "unknown error"}") }
}
```
(Note: the function has an early-return `if (totalAmount.toDoubleOrNull() == null) return@createSplit` BEFORE the launch — keep that outside the runCatching so it stays non-async.)

Use the correct Kotlin syntax: `runCatching { }.onFailure { _snackbar.tryEmit("Couldn't create split: ${it.message ?: "unknown error"}") }`. The `tryEmit` is non-suspending on `MutableSharedFlow` — fine for instant emit.

- [ ] **Step 4: Wrap `createDebtsFromSplit` body in runCatching**

Apply same pattern. Error message: `"Couldn't create debts from split: ${it.message ?: "unknown error"}"`.

- [ ] **Step 5: Wrap `getAiSummary` body in runCatching**

Apply same pattern. Error message: `"Couldn't fetch AI summary: ${it.message ?: "unknown error"}"`.

- [ ] **Step 6: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt && git commit -m "feat(split): snackbar SharedFlow + runCatching-wrap 3 launches for crash defense

- Add MutableSharedFlow<String> _snackbar + asSharedFlow 'snackbar' for SplitScreen to render.
- Wrap createSplit, createDebtsFromSplit, getAiSummary launch bodies in runCatching {}.onFailure { tryEmit 'Couldn't ...' } so any future exception surfaces as a Snackbar message instead of silent no-op or coroutine-handler-eaten exception.
- Defense-in-depth on top of the TypeToken fix and contacts-cursor fix — even after those targeted fixes, this guarantees no crash escapes the split flow."
```

- [ ] **Step 7: Push and watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 4: Wire SplitScreen Snackbar host to consume SplitViewModel.snackbar

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt`

**Interfaces:**
- Consumes: `viewModel.snackbar: SharedFlow<String>` (from Task 3)
- Produces: visible Snackbar in SplitScreen UI

- [ ] **Step 1: Find the screen scaffold root**

Read `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt:1-80`. Identify the `Scaffold(snackbarHost = ...)` slot or `Box`/`Column` root. If no scaffold exists, wrap the screen content in a `Scaffold`.

- [ ] **Step 2: Add a `SnackbarHostState` and a `LaunchedEffect` collector**

```kotlin
val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
val vm: SplitViewModel = hiltViewModel()  // or reuse the existing VM remember
androidx.compose.runtime.LaunchedEffect(Unit) {
    vm.snackbar.collect { msg ->
        snackbarHostState.showSnackbar(msg)
    }
}
```

- [ ] **Step 3: Pass snackbar host into the Scaffold**

```kotlin
Scaffold(
    snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
    // ... existing topBar, content, etc.
) { padding ->
    // existing content; consume `padding` if not already
}
```

If the screen already uses `Scaffold`, just add the `snackbarHost` slot. If it doesn't, refactor the root to be a `Scaffold` with a `Box` content.

- [ ] **Step 4: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt && git commit -m "feat(split): SnackbarHost renders SplitViewModel.snackbar messages"
```

- [ ] **Step 5: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 5: AI roast language gating — extract `buildRoastPersona` helper

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt:291-358, 421, 446`

**Interfaces:**
- Consumes: existing `RoastLevel.MILD` / `.MEDIUM` / `.SPICY`, `prefs.selectedLanguage.first()` (already in `generateRoast`/`analyzeDebts`/`generateSplitSummary`)
- Produces: `buildRoastPersona(roastLevel, langCode): String` (private); `isIndicFamily(langCode): Boolean` (private). Behavior: non-Devanagari users get language-neutral prompts; hi/mr/pa/gu users keep Hinglish idiom.

- [ ] **Step 1: Read the existing `buildSystemPrompt` block**

Read `app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt:285-360`. Note that:
- The `langInstruction` `when(selectedLangCode)` block at `:292-313` is correct and unchanged.
- The `when(roastLevel)` block at `:319-357` hardcodes Hinglish.

- [ ] **Step 2: Add `isIndicFamily` and `buildRoastPersona` private helpers**

Just above `buildSystemPrompt` function definition, insert:

```kotlin
private fun isIndicFamily(langCode: String): Boolean =
    langCode in setOf("hi", "mr", "pa", "gu")

private fun buildRoastPersona(roastLevel: RoastLevel, langCode: String): String {
    val indic = isIndicFamily(langCode)
    val (baseRole, scenarios) = when (roastLevel) {
        RoastLevel.MILD -> "a witty friend writing a WhatsApp message about money" to "warm, funny, creative — metaphors or shared jokes"
        RoastLevel.MEDIUM -> "a clever, sarcastic friend dropping a subtle money hint" to "relatable everyday scenarios"
        RoastLevel.SPICY -> "a brutally funny debt collector with legendary comedic timing" to "creatively savage — wild metaphors" + (if (indic) " or Bollywood comparisons" else " or pop-culture references")
    }
    val rolePrefix = if (indic) "an Indian $baseRole" else "a $baseRole"
    val lingual = if (indic) {
        "Use Hinglish naturally (mix Hindi and English). Use relatable Indian scenarios (chai, zomato, petrol prices)."
    } else {
        "Use your selected language naturally. Use relatable everyday scenarios."
    }
    return "You are $rolePrefix. Be $scenarios. $lingual"
}
```

- [ ] **Step 3: Replace the `when(roastLevel)` block in `buildSystemPrompt`**

In `buildSystemPrompt`, replace the `when(roastLevel) { MILD -> "..."; SPICY -> "..."; else -> "..." }` block (lines 319-357) with a single call:
```kotlin
val persona = buildRoastPersona(roastLevel, langCode)
```
Then `buildSystemPrompt` returns `"$langInstruction\n$persona"` (replacing the old `"$langInstruction\n$prompt"` return). The earlier `if (debtType != null && debtType.isNotBlank())` append (if any) stays unchanged.

- [ ] **Step 4: Update `analyzeDebts` user prompt (line ~421)**

Find the user prompt at `AiRepository.kt:421` that says `"Give ONE sharp, funny, honest 2-line insight. Hinglish welcome."`. Replace with:
```kotlin
val lingualHint = if (isIndicFamily(langCode)) "Hinglish welcome" else "Use your selected language"
"Give ONE sharp, funny, honest 2-line insight. $lingualHint."
```
Where `langCode` is the local `val langCode = prefs.selectedLanguage.first()` that already exists at `AiRepository.kt:417`.

- [ ] **Step 5: Update `generateSplitSummary` user prompt (line ~446)**

Find the user prompt at `AiRepository.kt:446` that says `"Write ONE funny line about this. Hinglish ok."`. Replace with:
```kotlin
val lingualHint = if (isIndicFamily(langCode)) "Hinglish ok" else "Use your selected language"
"Write ONE funny line about this. $lingualHint."
```
Where `langCode` is the local `val langCode = prefs.selectedLanguage.first()` already at `:442`.

- [ ] **Step 6: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/data/repository/AiRepository.kt && git commit -m "fix(ai): gate Hinglish-only roast phrases on isIndicFamily (hi/mr/pa/gu); non-Indic users get language-neutral tone matching their selected language

Root cause of user report 'AI roast always returns Hinglish even when user picked Spanish/French/etc':
- AiRepository.buildSystemPrompt did prepend 'Respond ONLY in Spanish' langInstruction (correct) but then the literal when-roastLevel block hardcoded 'Use Hinglish naturally' / 'Bollywood comparisons' / 'chai zomato petrol prices' for every user regardless of langCode — these Indian cues leaked Hinglish into the response.
- Same Hinglish leak was in analyzeDebts user prompt at L421 and generateSplitSummary at L446 ('Hinglish welcome'/'Hinglish ok').

Fix: extract buildRoastPersona(roastLevel, langCode) that branches on isIndicFamily (setOf 'hi','mr','pa','gu'); for non-Indic users, persona is language-neutral with role/scenarios only, lingual cue is 'Use your selected language naturally'. The pre-existing langInstruction prefix continues to provide the explicit language name."
```

- [ ] **Step 7: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 6: Add 31 new keys to `en` map of `LocalizedString.kt`

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt:76-322` (the `en` map)

**Interfaces:**
- Consumes: existing `en` map structure
- Produces: 31 new key/value pairs available via `LocalizedString.get("<key>")`. Other 24 language maps will receive these keys in later tasks.

- [ ] **Step 1: Read the existing `en` map to learn style**

Read `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt:76-322`. Confirm the format `"key" to "value",` and find a good insertion location near related keys.

- [ ] **Step 2: Append 31 new key/value pairs to the `en` map**

Just before the closing `),` of the `en` map (at line ~322), insert:
```kotlin
        "add_debt_title" to "Add Debt",
        "person_name_required" to "Person Name *",
        "person_name_placeholder" to "Rahul, Priya, John...",
        "enter_valid_amount" to "Enter a valid amount",
        "amount_too_large" to "Amount seems too large. Please verify.",
        "avatar_label" to "Avatar",
        "from_device" to "From device",
        "description_optional" to "Description (optional)",
        "description_placeholder" to "Lunch, rent, trip...",
        "due_date_optional" to "Due Date (optional)",
        "select_due_date" to "Select due date",
        "notes_optional" to "Notes (optional)",
        "notes_placeholder" to "Any extra context...",
        "type_or_paste_emoji" to "Type or paste an emoji",
        "paste_emoji_here" to "Paste emoji here 😊",
        "please_enter_name_toast" to "Please enter a name",
        "please_enter_valid_amount_toast" to "Please enter a valid amount",
        "debt_not_found" to "Debt not found",
        "debt_link_error" to "This shared link could not be opened. It might have been deleted.",
        "go_back" to "Go back",
        "whatsapp_button" to "WhatsApp",
        "preparing_image" to "Preparing image...",
        "exceeds_remaining_balance" to "Exceeds remaining balance",
        "delete_now_button" to "Delete Now",
        "choose_how_to_proceed" to "Choose how to proceed:",
        "delete_immediately_button" to "Delete Immediately",
        "request_account_deletion_button" to "Request Account Deletion",
        "google_signin_unavailable" to "Google sign-in unavailable in this context.",
        "could_not_send_reset_email" to "Could not send reset email",
        "auth_no_uid_returned" to "Auth succeeded but no UID returned",
        "contacts_access_denied_toast" to "Contacts access was denied. Pick names manually instead.",
        "everyone_owes_each_receipts_dont_lie" to "Everyone owes {currency}{amount} each. Receipts don't lie.",
        "no_winner_yet" to "No winner yet",
        "syncing_your_data" to "Syncing your data...",
        "not_signed_in" to "Not signed in.",
        "account_deletion_failed" to "Account deletion failed",
        "could_not_share_csv" to "Could not share CSV",
        "export_failed" to "Export failed",
        "debt_still_loading_toast" to "Debt is still loading — try again in a moment",
        "no_internet_connection_toast" to "No internet connection. Tap refresh when online.",
        "debt_link_not_opened" to "This debt link could not be opened.",
        "could_not_generate_roast" to "Could not generate roast",
        "new_design_generated_toast" to "New design generated!",
        "failed_to_create_image" to "Failed to create image",
        "no_due_date" to "No due date",
```

Note the count above is actually 45 keys, not 31 — be sure to add ALL of them. (Recount: I added more keys than originally inventoried when reviewing the codebase; some toasts and event strings surfaced. The complete set is what's listed above. The total file size impact is what matters; the per-task count is not strict.)

- [ ] **Step 3: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "feat(i18n): add ~45 new en keys for previously-hardcoded English UI strings wiring"
```

Skip push yet — wait until Task 7 wires the sites and Task 8 translates.

---

## Task 7: Wire 31+ hardcoded English UI strings through `LocalizedString.get()`

**Files modified (one commit per file):**
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtBottomSheet.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailScreen.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailViewModel.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt` (additional site at L408)
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt:151`
- `app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt:32, 79`

**Interfaces:**
- Consumes: keys added in Task 6
- Produces: all hardcoded sites replaced with `LocalizedString.get("<key>")`

- [ ] **Step 1: Wire sites in `AddDebtBottomSheet.kt`**

For each hardcoded string identified in the spec inventory at lines 118, 129, 130, 144, 159, 173, 217, 221, 292, 293, 307, 308, 329, 330, 398, 408, 414, 435, and toasts 346/349/352: replace `Text("Add Debt"...)` → `Text(LocalizedString.get("add_debt_title")...)`, `Text("Person Name *")` → `Text(LocalizedString.get("person_name_required"))`, etc. Use the en-key name as the key.

Toast messages:
- `Toast.makeText(context, "Please enter a name", ...)` → `Toast.makeText(context, LocalizedString.get("please_enter_name_toast"), ...)`
- `Toast.makeText(context, "Please enter a valid amount", ...)` → `... LocalizedString.get("please_enter_valid_amount_toast") ...`
- `Toast.makeText(context, "Amount seems too large. Please verify.", ...)` → `... LocalizedString.get("amount_too_large") ...`

Add the `import com.dhanuk.debtbro.util.LocalizedString` if not already present.

- [ ] **Step 2: Commit AddDebt**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/adddebt/AddDebtBottomSheet.kt && git commit -m "i18n: route AddDebtBottomSheet hardcoded English through LocalizedString.get"
```

- [ ] **Step 3: Wire sites in `DebtDetailScreen.kt`**

For each of lines 126, 129-130, 143, 308, 499, 555, 572, 690, 692 in `DebtDetailScreen.kt`: replace `Text("Debt not found")` → `Text(LocalizedString.get("debt_not_found"))`; `Text("This shared link could not be opened. It might have been deleted.")` → `Text(LocalizedString.get("debt_link_error"))`; `Text("Go back")` → `Text(LocalizedString.get("go_back"))`; `Text("WhatsApp")` → `Text(LocalizedString.get("whatsapp_button"))`; `Text("Preparing image...")` → `Text(LocalizedString.get("preparing_image"))`; `Text("Exceeds remaining balance")` → `Text(LocalizedString.get("exceeds_remaining_balance"))`; `Toast "Amount exceeds remaining balance"` → `Toast.makeText(ctx, LocalizedString.get("exceeds_remaining_balance"), ...)`.

- [ ] **Step 4: Commit DebtDetailScreen**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailScreen.kt && git commit -m "i18n: route DebtDetailScreen hardcoded English through LocalizedString.get"
```

- [ ] **Step 5: Wire sites in `DebtDetailViewModel.kt`**

Lines 114, 199, 214, 279, 295, 373, 378, 485, 390 are toast/event messages. Replace each literal string with `LocalizedString.get("<key>")`. Use `debt_link_not_opened`, `debt_still_loading_toast`, `no_internet_connection_toast`, `could_not_generate_roast`, `new_design_generated_toast`, `failed_to_create_image`, `no_due_date`. Note that the ViewModels may need to access `LocalizedString` in a non-Composable scope; the existing `LocalizedString.get(key)` static method works in any context (it reads `currentLang.value` synchronously).

- [ ] **Step 6: Commit DebtDetailViewModel**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/debtdetail/DebtDetailViewModel.kt && git commit -m "i18n: route DebtDetailViewModel toast/event strings through LocalizedString.get"
```

- [ ] **Step 7: Wire sites in `SettingsScreen.kt`**

Lines 380 (version — leave as-is per spec note "intentionally hardcoded"), 461 (`Delete Now`), 495 (`Choose how to proceed:`), 537 (`Delete Immediately`), 546 (`Request Account Deletion`). Replace the non-intentional literals: `Text("Delete Now")` → `Text(LocalizedString.get("delete_now_button"))`; `Text("Choose how to proceed:")` → `Text(LocalizedString.get("choose_how_to_proceed"))`; `Text("Delete Immediately")` → `Text(LocalizedString.get("delete_immediately_button"))`; `Text("Request Account Deletion")` → `Text(LocalizedString.get("request_account_deletion_button"))`.

- [ ] **Step 8: Commit SettingsScreen**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsScreen.kt && git commit -m "i18n: route SettingsScreen hardcoded button labels through LocalizedString.get"
```

- [ ] **Step 9: Wire sites in `SettingsViewModel.kt`**

Lines 336 (`Syncing your data...`), 446 (`Not signed in.`), 463, 467, 470 (`Account deletion failed`), 494 (`Could not share CSV`), 499 (`Export failed: ${e.message}` — split into `LocalizedString.get("export_failed") + ": " + e.message`). Replace each with `LocalizedString.get("<key>")`.

- [ ] **Step 10: Commit SettingsViewModel**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/settings/SettingsViewModel.kt && git commit -m "i18n: route SettingsViewModel toast/event strings through LocalizedString.get"
```

- [ ] **Step 11: Wire sites in `AuthScreen.kt`**

Line 106 (`Google sign-in unavailable in this context.`) and line 132 (`Text("Email")`). Replace with `LocalizedString.get("google_signin_unavailable")` and `LocalizedString.get("email")` (the `email` key already exists).

- [ ] **Step 12: Commit AuthScreen**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthScreen.kt && git commit -m "i18n: route AuthScreen 'Email' label + Google-signin-unavailable through LocalizedString.get"
```

- [ ] **Step 13: Wire sites in `AuthViewModel.kt`**

Lines 202 (`Could not send reset email`) and 228 (`Auth succeeded but no UID returned`). Replace with `LocalizedString.get("could_not_send_reset_email")` and `LocalizedString.get("auth_no_uid_returned")`.

- [ ] **Step 14: Commit AuthViewModel**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/auth/AuthViewModel.kt && git commit -m "i18n: route AuthViewModel error strings through LocalizedString.get"
```

- [ ] **Step 15: Wire SplitScreen.kt:408**

Replace literal `"Contacts access was denied. Pick names manually instead."` with `LocalizedString.get("contacts_access_denied_toast")`.

- [ ] **Step 16: Commit SplitScreen**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitScreen.kt && git commit -m "i18n: route SplitScreen contacts-denied toast through LocalizedString.get"
```

- [ ] **Step 17: Wire SplitViewModel.kt:151**

In `SplitViewModel.kt`, find the fallback `getOrElse` message `"Everyone owes ${currency}${...} each. Receipts don't lie."` at line 151. Replace with a parametrized call: `LocalizedString.get("everyone_owes_each_receipts_dont_lie").replace("{currency}", currency).replace("{amount}", amount.toString())`. Note the `en` value contains `{currency}` and `{amount}` placeholders.

- [ ] **Step 18: Commit SplitViewModel**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/split/SplitViewModel.kt && git commit -m "i18n: route SplitViewModel fallback AI-summary message through LocalizedString.get with currency+amount placeholders"
```

- [ ] **Step 19: Wire AnalyticsViewModel.kt:32, 79**

Replace `"No winner yet"` literal at `AnalyticsViewModel.kt:32` and `:79` with `LocalizedString.get("no_winner_yet")`. Both sites may need to read the value once and reuse it as a constant.

- [ ] **Step 20: Commit AnalyticsViewModel**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/analytics/AnalyticsViewModel.kt && git commit -m "i18n: route AnalyticsViewModel 'No winner yet' default through LocalizedString.get"
```

- [ ] **Step 21: Push all and watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

Verify CI green before Task 8.

---

## Task 8: Add the same 45 new keys to the 9 existing non-`en` language maps (hi/es/fr/de/ja/mr/pa/gu/bn)

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

**Interfaces:**
- Consumes: the 45 new en keys from Task 6
- Produces: 9 existing non-en maps each grow by 45 entries.

- [ ] **Step 1: For each non-`en` map (`hi`, `es`, `fr`, `de`, `ja`, `mr`, `pa`, `gu`, `bn`), append the 45 new keys**

Inside each existing `map` literal (e.g. `"hi" to mapOf(...)`), just before the closing `)`, append 45 key/value pairs matching the en keys from Task 6. The values must be idiomatically translated for that specific language. Use the LLM (you, the implementer) to translate the English value.

Concretely, generate translations by:
1. Reading the 45 English key/values added in Task 6 Step 2.
2. For each translation target language (hi/es/fr/de/ja/mr/pa/gu/bn), produce idiomatic translations for each key.
3. For toasts/messages with placeholders like `"{currency}{amount} each. Receipts don't lie."`, preserve the `{currency}` and `{amount}` placeholders in the translated message in the appropriate location for that language.

For languages with limited stub coverage (`pa` has only 36 keys, `gu` has 29, `bn` has 29) — adding 45 more brings them to 81/74/74, still partial. Task 9 will fill the full en-pairs gap.

- [ ] **Step 2: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "i18n: add 45 new keys × 9 existing-non-en maps = 405 new translated entries

Languages touched: hi, es, fr, de, ja, mr, pa, gu, bn. New keys: add_debt_title / person_name_required / enter_valid_amount / ... (matches Task 6 en additions)."
```

- [ ] **Step 3: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 9: Fill the 834 missing entries in 10 existing language maps to fully match `en` coverage

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

**Interfaces:**
- Consumes: current `en` map (full 238+45 = 283 keys master)
- Produces: 9 non-en maps achieve full coverage to match en's keys (pa/gu/bn still partial at 81/74/74, need many more entries).

- [ ] **Step 1: Compute diff for each existing non-en map**

For each language in `(hi, es, fr, de, ja, mr, pa, gu, bn)`:
1. Read current map's keys.
2. Compare to en keys.
3. List missing keys.

Expected counts (per spec inventory section):
- hi: 21 keys missing (22 if not done in Task 8 baseline)
- es: 45 missing
- fr: 45 missing
- de: 45 missing
- ja: 51 missing
- mr: 7 missing
- pa: 202 missing (skeleton)
- gu: 209 missing (skeleton)
- bn: 209 missing (skeleton)

Note: these counts are baseline before Task 8. After Task 8, the 45 new keys are added (so the missing-gap shrinks by 45 for each map if the values were NOT in those languages before). Final diff after Task 8: hi missing = 21 - 45 = 0 (gap closed), es missing = 45 - 45 = 0, fr/de/ja = 0, mr missing = 7 - 7 (only those 7 missing keys were `ai_api_key_*`, `no_api_key_message`, `search_language` — all distinct from the 45 new ones) = -38 (so mr's missing gap = 0 too if Task 8 happened to include them, otherwise the remaining 7 missing keys still need filling). pa/gu/bn: still missing ~157/164/164 each.

Recompute live diffs in the file. The implementer's actual diff tooling should compute this — don't trust the spec baseline blindly.

- [ ] **Step 2: Generate translations for each missing key in each map**

For each missing (lang, key) pair: produce an idiomatic translation, matching the en value semantics. Append to the `mapOf(...)` literal for that language.

- [ ] **Step 3: Dedupe the duplicate `"settings"` key in `pa` map**

In `LocalizedString.kt:1571-1610`, the `pa` map reportedly contains two `"settings" to "..."` entries — Kotlin mapOf takes the last value silently. Audit the `pa` map for any duplicated keys and remove duplicates. Add a comment if helpful noting the cleanup.

- [ ] **Step 4: Commit (single commit per language to keep diffs reviewable)**

For each of the 9 existing non-en languages, commit:
```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "i18n: fill <NN> missing entries in <lang> map to match en coverage"
```
Make 9 commits — one per language.

- [ ] **Step 5: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 10: Create 15 new language maps (pt, ar, zh, ko, ru, tr, it, id, ta, te, ur, sw, nl, pl, vi) with full coverage

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt`

**Interfaces:**
- Consumes: the master en map as key source
- Produces: 15 new `"lang" to mapOf(...)` entries inside `private val strings = mapOf(...)`. Each new map has all the same keys as `en`, with translated values.

- [ ] **Step 1: For each of the 15 new languages, generate a full translation map**

For each language code in `(pt, ar, zh, ko, ru, tr, it, id, ta, te, ur, sw, nl, pl, vi)`, produce a complete `"<lang>" to mapOf(...)` block with all en keys present and values translated for that language. The block is appended to the `strings` map right before its closing `)`.

Notes per language:
- `ar`: Arabic, RIGHT-TO-LEFT script. Strings should be in Arabic Unicode. (Note: Compose will render RTL per system locale; we only provide strings, not layout direction. If the app's `LocalLayoutDirection` is not set based on language, this is a separate fix not in scope of this plan.)
- `zh`: Simplified Chinese (Mainland). Strings in Chinese characters.
- `ko`: Korean Hangul.
- `ja`: is already a partial map (Task 9 filled it).
- `ru`: Russian Cyrillic.
- `tr`: Turkish.
- `it`: Italian.
- `id`: Indonesian (Bahasa).
- `ta`: Tamil Unicode.
- `te`: Telugu Unicode.
- `ur`: Urdu (Arabic-derived script, RTL).
- `sw`: Swahili.
- `nl`: Dutch.
- `pl`: Polish.
- `vi`: Vietnamese.
- `pt`: Portuguese (Brazilian `pt-BR` is the typical default — use that unless we distinguish `pt-PT` in picker).

- [ ] **Step 2: Commit each new map**

For each language, do one commit:
```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "i18n: add full <CN> language map <lang> with <N> entries"
```
15 commits — one per language.

- [ ] **Step 3: Update `familyFallback`** 

Edit `LocalizedString.kt:25-35` `familyFallback`. After Task 10, all 25 advertised languages have their own map. The `familyFallback` chain only needs entries for non-advertised languages (`ne` Nepali and `sa` Sanskrit) — keep `"ne" to "hi"` and `"sa" to "hi"` and remove `mr/pa/gu` entries (they now have their own complete maps).

```kotlin
private val familyFallback = mapOf(
    "ne" to "hi",
    "sa" to "hi"
)
```

- [ ] **Step 4: Commit familyFallback cleanup**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "i18n: trim familyFallback to only ne/sa → hi (mr/pa/gu have full maps now)"
```

- [ ] **Step 5: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Task 11: Onboarding redesign — split `Page5Name` into `Page5aName` + `Page6Auth`

**Files:**
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt:36-176, 274-390`
- Modify: `app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt`

**Interfaces:**
- Consumes: existing `OnboardingViewModel` `signInWithGoogle`/`signUpEmailPassword`/`signInEmailPassword`/`authError`/`completeOnboarding`; existing localized keys `what_call_you`, `your_name_label`, `name_example`, `skip_for_now`, `or_continue_with`, `dont_have_account`, `already_have_account`, `email`, `password`, `sign_in`, `sign_up`, `sign_in_google`, `account_setup_skip_note`.
- Produces: 6 pages of onboarding; new keys `auth_page_signup_heading`, `auth_page_signin_heading`, `auth_page_subtitle`, `confirm_password`, `already_have_account_signin`, `dont_have_account_signup`, `forgot_password`, `password_reset_email_sent`, `send_reset_email`, `name_page_subtitle`, `name_change_hint`, `continue_button`.

- [ ] **Step 1: Update pager total and skip target**

Edit `OnboardingScreen.kt:40` `val pagerState = rememberPagerState(pageCount = { 5 })` → `val pagerState = rememberPagerState(pageCount = { 6 })`.

Edit `OnboardingScreen.kt:67-70` — change `pagerState.currentPage in 1..3` → `pagerState.currentPage in 1..4` (Skip visible on marketing + name page). Change `pagerState.animateScrollToPage(4)` → `pagerState.animateScrollToPage(5)` (Skip moves to the new auth page).

Edit `OnboardingScreen.kt:142` — `repeat(5)` → `repeat(6)`.

- [ ] **Step 2: Update bottom shared Continue button logic for 6 pages**

Edit `OnboardingScreen.kt:152-172`. Currently:
- `enabled = pagerState.currentPage < 4 || name.isNotBlank()` → change to `enabled = when (pagerState.currentPage) { 4 -> name.isNotBlank(); else -> true }` (pages 0-3 always enabled, page 4 needs name, page 5 always enabled since auth is optional).
- The `onClick` "if currentPage < 4 advance else completeOnboarding" becomes `if currentPage < 5 animateScrollToPage(currentPage + 1) else completeOnboarding`.
- The text logic: `if (pagerState.currentPage < 5) LocalizedString.get("next_arrow") else LocalizedString.get("lets_go")`.

- [ ] **Step 3: Replace `Page5Name(...)` invocation (`OnboardingScreen.kt:85-130`) with `Page5aName(...)` and `Page6Auth(...)`**

Update the `when (page) { 0 -> ... ; 4 -> ... }` block at `:80-131` to:
```kotlin
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
        onToggleEmailForm = {
            if (!showEmailForm) {
                showEmailForm = true
                isSignInMode = false
            } else {
                isSignInMode = !isSignInMode
            }
        },
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
```

Also add the state fields near the existing Compose state (lines 46-52):
```kotlin
var confirmPassword by remember { mutableStateOf("") }
val showForgotPasswordDialog by viewModel.showForgotPasswordDialog.collectAsStateWithLifecycle()
val forgotPasswordError by viewModel.forgotPasswordError.collectAsStateWithLifecycle()
var forgotPasswordEmail by remember { mutableStateOf("") }
val isSignInMode by viewModel.isSignInMode.collectAsStateWithLifecycle()  // promoted from local var to VM state
val showEmailForm by viewModel.showEmailForm.collectAsStateWithLifecycle()  // promoted
// Remove the local var email/password/etc? — Keep the local `email`/`password` mutableState since they're form input; the form mode (sign-in vs sign-up) is in the VM.
```

Wait — `isSignInMode` and `showEmailForm` use Compose `remember { mutableStateOf(false) }` at `OnboardingScreen.kt:46-47`. They need to migrate to VM (`OnboardingViewModel`) so the toggle persists across recompositions when VM is created early. Add to VM (in Step 5 below).

- [ ] **Step 4: Replace the `Page5Name` composable definition (lines 275-390) with two new composables: `Page5aName` and `Page6Auth`**

Define `Page5aName` as a stripped-down version of the current `Page5Name` — only name field + Skip. Pattern:
```kotlin
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
```

Define `Page6Auth` based on the original `Page5Name`'s auth half + new features (confirm password, forgot-password link + dialog):
```kotlin
@Composable
fun Page6Auth(
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
        // OR divider
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
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
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
                            if (passwordVisible) androidx.compose.material.icons.Icons.Outlined.Visibility else androidx.compose.material.icons.Icons.Outlined.VisibilityOff,
                            contentDescription = null
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
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
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = MaterialTheme.colorScheme.primary, unfocusedBorderColor = MaterialTheme.colorScheme.outline)
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
            // Bottom links row: forgot-password (always) + mode-toggle for sign-in mode
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // empty placeholder on left for sign-up mode; in sign-in mode shows forgot password link
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
```

Add new imports as needed: `androidx.compose.foundation.verticalScroll`, `androidx.compose.foundation.rememberScrollState`, `androidx.compose.material.icons.Icons.Outlined.Visibility` / `VisibilityOff`, `androidx.compose.material3.Icon`, `androidx.compose.material3.IconButton`, `androidx.compose.material3.AlertDialog`.

- [ ] **Step 5: Update `OnboardingViewModel` to add the new state + methods**

Edit `OnboardingViewModel.kt`. Add:
```kotlin
private val _isSignInMode = MutableStateFlow(false)
val isSignInMode = _isSignInMode.asStateFlow()

private val _showEmailForm = MutableStateFlow(false)
val showEmailForm = _showEmailForm.asStateFlow()

private val _showForgotPasswordDialog = MutableStateFlow(false)
val showForgotPasswordDialog = _showForgotPasswordDialog.asStateFlow()

private val _forgotPasswordError = MutableStateFlow<String?>(null)
val forgotPasswordError = _forgotPasswordError.asStateFlow()

fun toggleAuthMode() { _isSignInMode.value = !_isSignInMode.value }
fun toggleEmailForm() {
    if (!_showEmailForm.value) { _showEmailForm.value = true; _isSignInMode.value = false }
    else { _isSignInMode.value = !_isSignInMode.value }
}
fun showForgotPasswordDialog() { _showForgotPasswordDialog.value = true; _forgotPasswordError.value = null }
fun dismissForgotPasswordDialog() { _showForgotPasswordDialog.value = false; _forgotPasswordError.value = null }
fun sendPasswordResetEmail(email: String) {
    if (email.isBlank()) {
        _forgotPasswordError.value = LocalizedString.get("email") + " required" // or a new key
        return
    }
    viewModelScope.launch {
        runCatching { auth.sendPasswordResetEmail(email) }
            .onSuccess {
                _forgotPasswordError.value = null
                _showForgotPasswordDialog.value = false
                // optionally emit a snackbar "Password reset email sent"
            }
            .onFailure { _forgotPasswordError.value = it.message ?: "Could not send reset email" }
    }
}
```

Update the screen to consume VM state. The screen's `onToggleEmailForm` lambda now calls `viewModel.toggleEmailForm()` instead of mutating local Compose state.

Add the imports as needed: `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.flow.asStateFlow`, `com.dhanuk.debtbro.util.LocalizedString` (if not present).

- [ ] **Step 6: Add the 12 new keys to all 25 language maps**

Just like Task 8 additions, append the following 12 keys to each of the 25 maps (`en`, plus 9 existing non-en, plus 15 new):
- `auth_page_signup_heading`: en="Sign up to save your data"
- `auth_page_signin_heading`: en="Welcome back"
- `auth_page_subtitle`: en="Sync across devices, never lose a debt"
- `confirm_password`: en="Confirm password"
- `already_have_account_signin`: en="Already have an account? Sign in"
- `dont_have_account_signup`: en="Don't have an account? Sign up"
- `forgot_password`: en="Forgot password?"  (may already exist in some maps from AuthScreen)
- `password_reset_email_sent`: en="Password reset email sent"
- `send_reset_email`: en="Send reset email"
- `name_page_subtitle`: en="We'll personalize your experience"
- `name_change_hint`: en="You can change this later in Settings"
- `continue_button`: en="Continue"  (only if not already present)

`en` map gets the literal values. Other 24 maps get translated values.

- [ ] **Step 7: Commit**

```bash
cd /home/ubuntu/DebtBro && git add app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingScreen.kt app/src/main/java/com/dhanuk/debtbro/presentation/screens/onboarding/OnboardingViewModel.kt app/src/main/java/com/dhanuk/debtbro/util/LocalizedString.kt && git commit -m "feat(onboarding): split page 4 into separate name + signup pages (Approach A — 6 pages total)

- Page5aName: name input only + Skip + hint 'You can change this later in Settings'. Continue button advances to page 6 (handled by shared bottom Continue at OnboardingScreen.kt:152-172 logic updated for 6-page pager).
- Page6Auth: Google button + Email/Password form + Confirm-Password (sign-up only) + Password-eye toggle + Forgot-password inline AlertDialog + bottom-row links. Skip-for-now preserved (existing completeOnboarding call).
- OnboardingViewModel: promote isSignInMode and showEmailForm from local Compose state to StateFlow (persist recompositions); add showForgotPasswordDialog + forgotPasswordError StateFlows; add toggleAuthMode/toggleEmailForm/showForgotPasswordDialog/dismissForgotPasswordDialog/sendPasswordResetEmail methods.
- LocalizedString: add 12 new auth-page keys to all 25 language maps with idiomatically translated values."
```

- [ ] **Step 8: Push, watch CI green**

```bash
cd /home/ubuntu/DebtBro && git push origin main
```

---

## Final verification

- [ ] Final Step 1: CI green on the latest commit

Open `https://github.com/aasheesh333/DebtBro/actions` — confirm latest run on `main` is green.

- [ ] Final Step 2: User manual tap-through

Hand off to user: download the latest APK from Releases, install on Android phone, tap through:
1. Onboarding: pick language (page 0). Skip 3 marketing pages. Enter name (page 4). Continue. See auth page (page 5). Try Google sign-in, email sign-up with confirm password mismatch, forgot-password dialog. Sign in successfully. Reach Dashboard.
2. Switch language in Settings → Spanish (es). Trigger an AI roast. Verify the roast returns Spanish (not Hinglish).
3. Switch language → hi. Trigger a roast. Verify the roast returns Hinglish idioms ("Bhai", "bheja", etc.).
4. From Split tab, tap "Create Split" — verify NO crash. Tap "Add Participant from Contacts" — verify NO crash even if contact has no phone number. Try a malformed contact manually.
5. Trigger a forced Gson crash via malformed `participants` JSON — verify Snackbar shows "Couldn't fetch AI summary: ..." instead of crash.

---

## Out of scope (do NOT attempt)

- Marketing pages 1-3 redesign (Approach B was rejected).
- Native-speaker review of LLM-generated translations (out of v1 scope).
- Reducing the 25-language picker (Scope B rejected).
- Cloud Functions sweep (parked).
- Anything not explicitly listed in the approved spec at `docs/superpowers/specs/2026-07-03-onboarding-redesign-splitfix-ai-lang-i18n-design.md`.
