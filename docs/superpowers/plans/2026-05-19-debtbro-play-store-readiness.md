# DebtBro Play Store Readiness Plan

**Date:** 2026-05-19
**Commit:** `efb066f`
**Branch:** `fix-google-signin-and-export-crash-15964286147323028907`

## Overview

This plan addresses all findings from the DebtBro Play Store readiness audit, including CRITICAL bugs (Play Console policy violations), HIGH-security issues (encryption, CSV injection, SQL injection vector), MEDIUM code quality improvements, and 16KB page alignment compliance.

## Execution Model

Every task is designed to be completed in a single commit. Tasks are self-contained and independent wherever possible, to enable parallel workstreams. Sequence matters only for dependencies explicitly noted.

## Phases

Phase 1: CRITICAL fixes (block Play submission)
Phase 2: HIGH-priority fixes (security, privacy, reliability)
Phase 3: MEDIUM fixes (code quality, edge cases, maintainability)

---

## Phase 1 Tasks (CRITICAL)

### Task 1: Add gma_ad_services_config.xml
Fixes missing gma_ad_services_config.xml referenced in AndroidManifest.xml, causing Android 14+ crash.

**Step 1: Create** `app/src/main/res/xml/gma_ad_services_config.xml`**
**Step 2: Populate** with the full AdServices element as described in the implementation details.**
**Step 3: Commit** `fix: add required gma_ad_services_config.xml for AdServicesConfig on Android 14+`  

---

### Task 2: Auto-Increment versionCode
Fixes hardcoded versionCode=1 that blocks second Play upload.

**Step 1: Replace** in `app/build.gradle.kts`**:**
```kotlin
versionCode = (System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 1)
```
**Step 2: Commit** `fix: auto-increment versionCode via GITHUB_RUN_NUMBER`  

---

### Task 3: Real Account Deletion URL
Fixes static help page that violates Play deletion policy.

**Step 1: Modify** `data/datastore/AppPreferences.kt`**:**
Update `ACCOUNT_DELETION_URL` to the real Cloud Function endpoint.
**Step 2: Document** Cloud Function implementation steps in `docs/FIREBASE_ACCOUNT_DELETION.md`**
**Step 3: Commit** `fix: point account deletion to real Cloud Function endpoint`  

---

### Task 4: Safe Deep Link Parse
Fixes `checkNotNull(savedStateHandle.get<Int>("debtId"))` crash on `debtbro://debt/`

**Step 1: Implement safe parse logic with handle + NavArg fallback**
**Step 2: Commit** `fix: guard debtId deep-link extraction; fallback to savedState + nav-arg`  

---

### Task 5: Remove `context as Activity` Cast
Fixes uncrashable cast in multi-window.

**Step 1: Create `ActivityFinder` utility**
**Step 2: Inject into screens instead of casting**
**Step 3: Commit** `fix: avoid context cast crash; use ActivityFinder`  

---

### Task 6: CSV Formula Injection Fix
Fixes `=+-@\t\r` prefix injection in export.

**Step 1: Sanitize all formula prefixes**
**Step 2: Commit** `fix: sanitize CSV cells to prevent formula injection`  

---

## Phase 2 Tasks (HIGH)

### Task 7: Remove .fallbackToDestructiveMigration()
Fixes silent database wipe.

**Step 1: Remove the line from DebtBroDB.kt**
**Step 2: Add Migrations 2 to 3 (or column additions)**
**Step 3: Commit** `fix: replace destructive migration with non-destructive v2 to v3`  

---

### Task 8: Sign-Out Clear Local State
Fixes cross-user data exposure.

**Step 1: Implement `clearAllLocalUserData` in PreferencesManager**
**Step 2: Close and clear Room DB on sign-out**
**Step 3: Commit** `fix: clear Room + DataStore on sign-out to prevent cross-user leak`  

---

### Task 9: SyncManager Pull Cloud-to-Local + Tombstone Propagation
Fixes ghosts and stale data.

**Step 1: Implement diff-based delete propagation**
**Step 2: Commit** `fix: pull Cloud to Local deletions onto tombstone list for offline sync`  

---

### Task 10: Payment Real-Time Listener + Merge Update
Fixes string-ID vs int-ID race in payments and splits.

**Step 1: Add `paymentDao.getByFireBaseId(id: String)`**
**Step 2: Implement merge-update for split entity**
**Step 3: Commit** `fix: payment real-time listener uses merge-update for reliable sync`  

---

### Task 11: Cloud DTO + Validation
Fixes raw Room data in Firestore.

**Step 1: Create DTO classes in `data/firebase/dto/`**
**Step 2: Create Validator utility**
**Step 3: Commit** `feat: add Firebase DTO + validation layer`  

---

### Task 12: Atomic Account Deletion
Fixes re-auth vs auth-state race.

**Step 1: Chain `sync.performFullPush()` first, then auth, then confirmation**
**Step 2: Handle three user-facing error states explicitly**
**Step 3: Commit** `fix: make account deletion atomic and idempotent; handle edge cases`  

---

### Task 13: Worker Pref. Check & Error Handling
Fixes notification-less or crash-prone workers.

**Step 1: Add constraints, retry logic, and prefs check**
**Step 2: Commit** `fix: add worker constraints, retry, and prefs-driven notification toggle`  

---

### Task 14: EncryptedSharedPreferences Wrapper
Fixes plaintext API key and PII.

**Step 1: Replace sensitive keys with wrapper**
**Step 2: Commit** `fix: migrate sensitive tokens/PII to EncryptedSharedPreferences`  

---

### Task 15: Fix Re-auth Filter
Fixes re-auth with stale credentials.

**Step 1: Apply one-line fix**
**Step 2: Commit** `fix: show all Google accounts in re-auth (not just authorized)`  

---

### Task 16: WebView Bitmap Web-to-Device Export Fix
Fixes leak/OOM.

**Step 1: .destroy() + scale factor + image cap**
**Step 2: Hard-ref WebView to avoid GC**
**Step 3: Commit** `fix: WebView export memory-safe (destroy, scale, cap)`  

---

### Task 17: ProGuard Keep Rules Narrowing
Reduces app size.

**Step 1: Replace broad `@Keep` with explicit keep lists**
**Step 2: Commit** `fix: narrow ProGuard keep rules to entity + DTOs only`  

---

### Task 18: Upgrade SDK & Dependencies
Fixes compatibility.

**Step 1: Update `build.gradle.kt` + `libs.versions.toml`**
**Step 2: Commit** `fix: bump dependencies (16KB alignment, security, FCM)`  

---

### Task 19: 16KB Page Alignment
Fixes new device crash.

**Step 1: Add `PAGE_SIZE_COMPAT=16`**
**Step 2: Add `network_security_config.xml`**
**Step 3: Commit** `fix: enable 16KB page alignment via PAGE_SIZE_COMPAT + network_security_config`  

---

### Task 20: POST_NOTIFICATIONS Permission UI
Fixes missing permission.

**Step 1: Add permission card in Settings**
**Step 2: Commit** `feat: allow user to toggle notifications in Settings`  

---

### Task 21: CSV UTF-8 BOM + Injection Defense
Fixes circular injection.

**Step 1: Add BOM and sanitize formulas**
**Step 2: Commit** `fix: CSV BOM + sanitize cell formulas`  

---

### Task 22: Test Tag Mismatch Fix
Fixes testing.

**Step 1: Fix test tag case** (if file exists)
**Step 2: Commit** `fix: correct test tag case` (or skip)  

---

## Phase 3 Tasks (MEDIUM)

### Task 23: Typed EmptyGroqResponseException
Fixes generic error.

**Step 1: Create typed exception**
**Step 2: Throw in extractContent**
**Step 3: Commit** `fix: typed exception for empty Groq AI response`  

---

### Task 24: Safe Long.toInt Cast
Fixes ID corruptions.

**Step 1: Range-check before cast**
**Step 2: Commit** `fix: safe Long.toInt with range-check`  

---

### Task 25: Atomic Refresh + Debounced Sync
Fixes concurrency races.

**Step 1: Use atomic flag + STTX for DB ops**
**Step 2: Commit** `fix: atomic refresh with debounced incremental sync`  

---

### Task 26: Recovery Rate Clamp
Fixes UX.

**Step 1: Clamp at boundary**
**Step 2: Commit** `fix: clamp recovery rate to [0, 1]`  

---

### Task 27: Double-Tap Payment Guard
Fixes UX.

**Step 1: Add flag to UI layer**
**Step 2: Commit** `fix: guard double-tap on payment`  

---

### Task 28: @Volatile Globals
Fixes race.

**Step 1: Add @Volatile**
**Step 2: Commit** `fix: volatile job/singleton state variables`  

---

### Task 29: LocalizedString Lazy Init
Fixes memory leak.

**Step 1: Re-use AtomicReference / handle**
**Step 2: Commit** `fix: lazy init string map to prevent multiple loads`  

---

### Task 30: Centralize Groq Model Constant
Fixes string-typos.

**Step 1: Extract to constant**
**Step 2: Commit** `refactor: centralize Groq model ID`  

---

### Task 31: Tighten Firestore Rules
Fixes security.

**Step 1: Limit user field creation**
**Step 2: Validate document.data**
**Step 3: Commit** `fix: tighten Firestore security rules`  

---

### Task 32: Remove All e.printStackTrace()
Fixes privacy.

**Step 1: Search/replace with logged variants**
**Step 2: Commit** `fix: replace printStackTrace with logging`  

---

## Verification Steps

After completing all phases, run the following to verify:

1. `./gradlew assembleDebug` - Build passes
2. `./gradlew lint` - No new lint errors
3. Manual smoke test: install on Android 11-15, exercise all critical user flows
4. Security audit: verify no plaintext API keys, validate CSV/PDF export
5. Play Console: verify all Data Safety fields are filled
