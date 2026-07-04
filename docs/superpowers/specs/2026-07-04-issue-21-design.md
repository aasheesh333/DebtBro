# Design: Split nav-bar gap, CSV permission, Ad revenue + reliability

**Date:** 2026-07-04
**Status:** Approved (user said "continue")
**Scope:** Three independent bug fixes + ad-reliability hardening + ad-revenue expansion (policy-safe)

---

## Issue 1 — Split screen empty space above bottom nav bar

### Root cause
`SplitScreen.kt:56` wraps content in its own inner `Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) })`. Material3's `Scaffold` defaults to `contentWindowInsets = WindowInsets.systemBars`, which re-applies the system nav-bar inset that the outer NavGraph Scaffold (`NavGraph.kt:94-232`) already accounts for via its `bottomBar` slot's `paddingValues`. Result: double-inset strip visible as "white space above nav bar" on light theme.

Among the 4 bottom-nav tabs (Dashboard, DebtList, Split, Analytics), **Split is the only one with an inner Scaffold**. All others use plain `Box`→`LazyColumn` with `LazyColumn.contentPadding` for breathing room.

### Approach chosen (approach 1 of 3 considered)
**Replace the inner `Scaffold` with a plain `Box` that hosts the SnackbarHost independently.** Match the pattern used by the other 3 tabs. Keep the Snackbar functionality (Split is the only tab that uses Snackbars for the AI Take flow).

**Rejected:**
- Override `contentWindowInsets = WindowInsets(0)` on the inner Scaffold — keeps the Scaffold wrapper but masks the symptom, doesn't fix the duplicate-inset logic for any future fields.
- Remove the Snackbar entirely — would break the "AI Take failed, try again" UX.

### Concrete change
- `SplitScreen.kt:56-66` — drop the Scaffold; replace with `Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))`. Host the SnackbarHost as a child `Box` aligned to `Alignment.BottomCenter`. Move the 16dp outer padding into `LazyColumn.contentPadding` + `.padding(horizontal=...)` like the sibling screens do. Add a `Spacer(height=ScreenBottomPadding)` at the bottom of the LazyColumn to match DebtList screen's breathing-room pattern.

---

## Issue 2 — CSV export permission error on API 26-28

### Root cause
`CsvExporter.kt:72-76` throws `IOException("Storage permission required...")` when `ContextCompat.checkSelfPermission(WRITE_EXTERNAL_STORAGE) != GRANTED` on API ≤ 28. The permission is declared in `AndroidManifest.xml:33-35` with `maxSdkVersion=28` and the manifest permits it, but **nothing ever requests it at runtime**. `SettingsScreen.kt:280` calls `viewModel.exportCsv(context)` directly with no permission preamble.

On API 29+, the code takes the MediaStore branch and bypasses the permission entirely — that's why the bug only manifests on older devices.

`HtmlExporter` (the image export path) avoids this by writing to app-private `cacheDir/share_images/` (zero permissions), sharing via FileProvider. The CSV path uses `Environment.getExternalStoragePublicDirectory(DIRECTORY_DOWNLOADS)` which requires the runtime grant on API ≤ 28.

### Approach chosen
**Mirror the SplitScreen READ_CONTACTS pattern:** gate the CSV button behind `ActivityResultContracts.RequestPermission()`. Only request when `Build.VERSION.SDK_INT < 29` (on Q+ we use MediaStore, no permission needed). On grant, proceed with the export; on deny, show a clear toast.

To keep the architecture clean, the permission request lives in the SettingsScreen (Compose-level), not the ViewModel, because `ActivityResultContracts.RequestPermission()` requires an `ActivityResultLauncher` tied to a `rememberLauncherForActivityResult` — a Composable-scoped API. ViewModel exposes `exportCsv(context)` unchanged.

As a **defense-in-depth fallback**, also rewrite CsvExporter to write to app-private `cacheDir` + share via FileProvider as the path of last resort on API ≤ 28 when the user has *denied* the permission. This keeps the export functional even on permission denial (matches HtmlExporter's pattern) and never blocks the user out of their data. The Downloads dir is "preferred" but cache+share is "graceful fallback".

**Edge cases:**
- API 29+: no permission request dialog, MediaStore path works (current behavior preserved)
- API 26-28 + grant: Downloads dir path works
- API 26-28 + deny: cache + FileProvider share via share sheet — user still gets their CSV

### Concrete change
- `SettingsScreen.kt` — add `rememberLauncherForActivityResult(RequestPermission())` for `WRITE_EXTERNAL_STORAGE`, gated by `Build.VERSION.SDK_INT < 29`. CSV button launches the request; on grant calls `viewModel.exportCsv(context)`. On deny shows toast with `LocalizedString.get("permission_denied_storage")` (new i18n key).
- `CsvExporter.kt` — when `checkSelfPermission != GRANTED` on API ≤ 28, fall through to a new `writeToCacheFile(context, ...)` that writes to `cacheDir/exports/` and returns a FileProvider URI. The caller (SettingsViewModel) uses `ShareUtils.shareCsv(uri)` if the URI is from cache, otherwise the existing path.
- `LocalizedString.kt` — add `"permission_denied_storage"` and `"export_csv_share_fallback"` keys to all 25 language maps (mirroring the existing 284-entry structure).
- `file_paths.xml` — add `<cache-path name="exports" path="exports/" />` entry for the CSV cache fallback.

---

## Issue 3 — Ads: reliability hardening + max earning (Balanced)

### 3A. Reliability fixes (no UX change, no new inventory)

**3A.1 — Retry failed loads on network restore (fixes B1/B6)**
- Add a `ConnectivityManager.NetworkCallback` in `DebtBroApp.onCreate` (registered against `getSystemService(CONNECTIVITY_SERVICE)`), calling `adManager.loadInterstitial(this)` and `adManager.loadRewardedAd(this)` on `onAvailable`. Throttled with a 30s min-interval guard to avoid spamming on flaky networks.

**3A.2 — Concurrency guard on ad fields (fixes B5)**
- Add a `Mutex` to `AdManager` around `interstitialAd` and `rewardedAd` load/show operations. Prevent the torn-state race where ViewModel's `onFailed → loadRewardedAd(activity)` runs concurrently with AdManager's internal `loadRewardedAd(activity)` reload.

**3A.3 — Test-device IDs (fixes B7, policy compliance)**
- In `AdManager.init`, call `MobileAds.setRequestConfiguration(RequestConfiguration.Builder().setTestDeviceIds(BuildConfig.TEST_DEVICE_IDS.toList()).build())` if `BuildConfig.DEBUG`. Add a `TEST_DEVICE_IDS` BuildConfig field (comma-separated string from `local.properties`, default empty). Document the workflow in AGENTS.md: developers add their device's hashed advertising ID (visible in logcat on first ad load attempt) to `local.properties` so ad clicks during dev don't count.

**3A.4 — Rewarded ad preload at app launch (fixes B10/D5)**
- In `DebtBroApp.kt:79-82` `MobileAds.initialize` callback, alongside `loadInterstitial(this)`, also call `adManager.loadRewardedAd(this)`. Eliminates the "AI Take tap fires before preload completes" race on first screen visit. Screen-level `preloadRewardedAd(context)` calls remain as belt-and-suspenders.

**3A.5 — Fix `lastInterstitialAt` stale-mirror race (fixes B3)**
- In `AdManager.showInterstitialIfReady`, read `prefs.lastInterstitialAt.first()` directly via `runBlocking { prefs.lastInterstitialAt.first() }` instead of trusting the cached `@Volatile var`. Removes the cold-start race where two interstitials can fire within 5 min across a process restart.

**3A.6 — Ad-event analytics (D9)**
- Add Firebase Analytics events: `ad_shown` (with `ad_type=interstitial|rewarded|banner|app_open`), `ad_dismissed`, `ad_failed`, `ad_opt_out`. Log from `AdManager` callback sites. Enables future A/B testing of cooldown and inventory.

### 3B. Revenue expansion — Balanced (policy-safe, UX-smooth)

**3B.1 — Banner ad in main Scaffold (D1, biggest earner)**
- Add a 320×50 dp `AdView` banner above the bottom nav bar in `NavGraph.kt:96-226` `bottomBar` slot. Use `BuildConfig.ADMOB_BANNER_ID` (real) or `ca-app-pub-3940256099942544/6300978111` (debug test ID, AdMob-blessed).
- The banner is mounted as a `AndroidView { AdView(context).apply { setAdSize(AdSize.BANNER); adUnitId = ...; loadAd(AdRequest.Builder().build()) } }` placed in a `Column` inside `bottomBar`, above the `NavigationBar`. Banner is hidden on Dashboard's empty state to avoid ad-noise on a freshly-onboarded user.
- **Policy:** banners are the original ad format. AdMob permits persistent banners as long as they don't obstruct navigation. 50 dp above the nav bar is industry-standard.
- **UX:** adds a ~50dp black strip above the bottom nav. Negligible impact on a finance utility where the user spends short bursts of time.

**3B.2 — App-Open ad with 4-hour cooldown (D2, high cold-start multiplier)**
- Create `AppOpenManager.kt` (Hilt `@Singleton`, observes `ProcessLifecycleOwner.get().lifecycle` `ON_START` events). Loads `AppOpenAd` via `MobileAds.loadAppOpenAd(...)`. Shows on `ON_START` if `System.currentTimeMillis() - lastAppOpenAt > 4_HOURS_MS` and the current Activity is foreground (ask `ActivityManager.getRunningAppProcesses`).
- Register in `DebtBroApp.onCreate` after `MobileAds.initialize` returns.
- Skip on first launch ever (preserve `prefs.firstLaunchDone` boolean — set true after first `MainActivity.onCreate` completes); AdMob policy explicitly forbids app-open on the very first launch.
- Uses existing `MainActivity` 1.5s splash window — the user is conditioned to wait briefly on cold start, the app-open ad rides that window.
- **Policy:** AdMob's published app-open guidance: 4-hour cooldown between impressions, never on first launch, never interrupt if user is mid-task. All three enforced.
- **UX:** light — one ad per 4 hours of session boundary. Most users see 0-1 per day.

**3B.3 — Interstitial after additional natural breaks (D3)**
- Add `_showInterstitial.emit(Unit)` to:
  - `DebtDetailViewModel.markSettled()` — settling a debt is the most satisfying moment, perfect natural transition
  - `SplitViewModel.createDebtsFromSplit()` — splits settlement is a natural "task complete"
  - `SettingsViewModel.exportCsv()` — export completion is a natural break
- Keep the 5-minute cooldown — it self-throttles all 4 interstitial triggers (AddDebt + Settled + Split + Export). User doing 4 things in 5 minutes still sees max 1 interstitial.
- **Policy:** each is AdMob's canonical "natural transition" / "task complete" timing, explicitly permitted.
- **UX:** cooldown-protected. Adding more triggers doesn't add more than one ad per 5 min — but it does mean users who don't add debts still see occasional interstitials on their own actions.

**NOT in scope for this wave (deferred to next sprint):**
- 5min→2min cooldown tuning (needs analytics data first; 3A.6 enables it)
- "Watch ad → 30-min unlimited AI regen" tier (uses dead `setRewardTimestamp`; would need UI design)
- Rewarded interstitial format
- Mediation networks (would need separate Mediation Groups setup on AdMob console)

### 3C. UMP consent for EEA/UK/CH (fixes C7, protects existing revenue)

- Add `play-services-ads` UMP dependency: `com.google.android.umd:services:2.0.1` (already bundled with play-services-ads 23.6.0 — no new dependency needed)
- In `DebtBroApp.onCreate`, before `MobileAds.initialize`, call `ConsentInformation.consentInformation(this).requestConsentInfoUpdate(...)` with `ConsentRequestParameters.Builder().setTagForUnderAgeOfConsent(false).build()`. On completion, `if (consentInformation.canRequestAds()) MobileAds.initialize(...)` — both paths (consent granted and consent rejected) are handled, only fully reject = no ads.
- Add a privacy policy URL via `setPrivacyPolicyUrl("https://dhanuk.page.gd/DebtBro/Privacy-Policy.html")` (already declared in Manifest as `android.content.PRIVACY_POLICY_URL` meta-data).
- Consent debug geography documented: test EEA flow with `DebugGeography.EEA` in debug builds only.
- **Policy:** required for personalized ads in EEA/UK/CH. Without it, AdMob serves *limited* (non-personalized) ads, structurally capping eCPM in ~30 countries.

---

## File change summary

```
app/src/main/AndroidManifest.xml         # no change (already has AD_ID)
app/src/main/java/com/dhanuk/debtbro/
  DebtBroApp.kt                          # UMP + retry-callback + rewarded preload + AppOpenManager register
  MainActivity.kt                        # set firstLaunchDone flag at end of onCreate
  data/ads/
    AdManager.kt                         # Mutex guards + test devices + analytics events + read-prefs-cooldown
    AppOpenManager.kt                    # NEW — process lifecycle observer for app-open ads
  data/datastore/AppPreferences.kt       # add firstLaunchDone + lastAppOpenAt keys/flows/setters
  presentation/navigation/NavGraph.kt    # add banner AdView to bottomBar Column
  presentation/screens/split/SplitScreen.kt          # remove inner Scaffold, use Box + Snackbar
  presentation/screens/debtlist/DebtListScreen.kt    # compare only, no change
  presentation/screens/settings/SettingsScreen.kt    # add permission launcher for CSV on API≤28
  presentation/screens/debtdetail/DebtDetailViewModel.kt  # emit interstitial after markSettled
  presentation/screens/split/SplitViewModel.kt           # emit interstitial after createDebtsFromSplit
  data/repository/SettingsViewModel.kt?               # emit interstitial after CSV export
  util/CsvExporter.kt                    # fall back to cacheDir+FileProvider on permission deny
  util/LocalizedString.kt                # add permission_denied_storage + share_fallback keys
  res/xml/file_paths.xml                 # add <cache-path name="exports" path="exports/" />
app/build.gradle.kts                     # add BuildConfig.TEST_DEVICE_IDS field
```

## Commit strategy

Three commits, one push, one CI run:

1. **`fix(split): drop inner Scaffold to remove double-inset above bottom nav`**
   - SplitScreen.kt only.

2. **`fix(csv): request WRITE_EXTERNAL_STORAGE at runtime on API≤28 + cache fallback`**
   - SettingsScreen.kt, CsvExporter.kt, LocalizedString.kt, file_paths.xml

3. **`feat(ads): banner in main Scaffold, app-open ad, UMP consent, reliability hardening`**
   - AdManager.kt, AppOpenManager.kt (new), DebtBroApp.kt, NavGraph.kt, AppPreferences.kt, MainActivity.kt, build.gradle.kts, DebtDetailViewModel.kt, SplitViewModel.kt, SettingsViewModel.kt

## Verification

- No tests in repo (per AGENTS.md)
- CI green is the verification bar
- Manual smoke test checklist for the user (after CI green):
  - Split tab no longer has white gap above nav bar
  - CSV export on a test Android 8/9 device prompts for storage permission → grants → exports to Downloads; denies → falls through to share sheet
  - Cold-start the app — interstitial loads within ~3s, banner appears above nav bar across all 4 tabs
  - Open the app, kill it, wait 4h, reopen → app-open ad shows
  - Settle a debt → interstitial fires (if cooldown elapsed)
  - AI Take after 5 free/day used → rewarded ad plays (no "try again later" toast)

## Out of scope / future

- 5→2 min cooldown tuning (needs analytics)
- 30-min unlimited regen "watch ad" tier
- Rewarded interstitial format
- Mediation networks (AdMob console config, not code)
- Remote Config gating for cooldown + free-regen cap
