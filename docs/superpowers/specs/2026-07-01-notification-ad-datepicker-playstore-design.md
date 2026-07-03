# DebtBro — Notification Manager, Ad System, Date Picker & Play Store Readiness

**Date:** 2026-07-01
**Status:** Approved
**Branch:** `fix-google-signin-and-export-crash-15964286147323028907`

---

## Overview

Three subsystems redesign + Play Store readiness fixes:

1. **Notification Manager System** — 4-type due date notification system with custom sounds, precise scheduling, and runtime permission handling
2. **Ad Manager System** — Balanced ad strategy (interstitial + banner + rewarded) with 3-min gap, UMP consent, and Google Play Billing "Remove Ads" IAP
3. **Date Picker Fix** — Block past dates, timezone-correct overdue calculation, contextual labels
4. **Play Store Readiness** — 10 critical fixes for policy compliance and crash prevention

---

## 1. Notification Manager System

### 1.1 Notification Types

| Type | Trigger | Channel ID | Importance | Sound | Vibration | Notification Text |
|------|---------|------------|------------|-------|-----------|-------------------|
| **Approaching** | 3 days before due | `debt_approaching` | DEFAULT | `chime_light.mp3` | None | `"{amount} due in 3 days — {personName}"` |
| **Urgent** | 1 day before due | `debt_urgent` | HIGH | `chime_urgent.mp3` | `[0, 200, 100, 200]` | `"{amount} due TOMORROW — {personName}"` |
| **Due Today** | Due date day (9 AM) | `debt_due_today` | HIGH | `whistle_sharp.mp3` | `[0, 200, 100, 300]` | `"{amount} is due TODAY — {personName}"` |
| **Overdue** | Daily at 9 AM after due | `debt_overdue` | HIGH | `whistle_sharp.mp3` | `[0, 300, 100, 300]` | `"{amount} is {X} days OVERDUE — {personName}"` |

Weekly summary notification stays on `weekly_summary` channel with `IMPORTANCE_LOW`.

### 1.2 Architecture

**New files:**
- `util/NotificationScheduler.kt` — Schedules `OneTimeWorkRequest` with `initialDelay` when a debt is saved/updated with a due date. Calculates exact trigger times for the 4 notification windows.
- `util/NotificationPermissionHandler.kt` — Composable function that requests `POST_NOTIFICATIONS` at runtime (Android 13+). Uses Accompanist permissions library (already in `libs.versions.toml`).
- `res/raw/chime_light.mp3` — Gentle notification chime
- `res/raw/chime_urgent.mp3` — Urgent notification chime
- `res/raw/whistle_sharp.mp3` — Sharp whistle for due today/overdue

**Modified files:**
- `worker/DebtReminderWorker.kt` — Complete rewrite:
  - Read `AppPreferences.notifyDailyReminder` — if disabled, return `Result.success()`
  - Query debts for each of the 4 windows separately
  - Post contextual notifications with type-specific channel, sound, vibration
  - Post group summary notification with `.setGroupSummary(true)` for `debtbro_reminders` group
  - Wrap in `try/catch` with `Result.retry()` on transient errors
  - Channel descriptions set for all notification channels
- `worker/WeeklySummaryWorker.kt` — Read `AppPreferences.notifyWeeklySummary`, add `areNotificationsEnabled()` check, add error handling
- `DebtBroApp.kt` — Change `ExistingPeriodicWorkPolicy.UPDATE` to `KEEP`; add flex interval `15 minutes` to daily work; call `NotificationScheduler.rescheduleAll()` on startup
- `AppPreferences.kt` — Remove `NOTIFY_PAYMENT_ALERTS` key and related flows/setters (dead code)

### 1.3 Scheduling Logic (NotificationScheduler)

When a debt is saved/updated with a `dueDate`:
1. Calculate `approachingTime = dueDate - 3 days` (at 9 AM local)
2. Calculate `urgentTime = dueDate - 1 day` (at 9 AM local)
3. `dueTodayTime = dueDate` (at 9 AM local)
4. `overdueTime = dueDate + 1 day` (at 9 AM local)
5. For each time where `time > now`, enqueue `OneTimeWorkRequest` with `initialDelay = time - now`
6. Tag work with `debt_notification_{debtId}_{type}` for cancellation on update/delete
7. On debt delete or settle, cancel all tagged works for that debtId

Also keep the periodic daily worker as a safety net — fires once daily at ~9 AM with flex to catch any debts that missed one-time scheduling (e.g., device reboot, app force stop).

### 1.4 Notification Permission Flow

1. On first app launch (after onboarding), call `NotificationPermissionHandler` composable
2. If `Build.VERSION.SDK_INT >= 33` and permission not granted, show rationale dialog: "DebtBro needs notification permission to remind you about due dates."
3. On grant: proceed. On deny: show snackbar with settings link. App works without notifications but no reminders.
4. Check permission status in `DebtReminderWorker` before posting.

### 1.5 Group Summary Notification

For every batch of debt notifications, post a summary:
```
Notification.Builder(context, channelId)
    .setGroup("debtbro_reminders")
    .setGroupSummary(true)
    .setContentTitle("DebtBro")
    .setContentText("${count} reminders")
    .setSmallIcon(R.drawable.ic_notification)
    .build()
```
Summary notification ID: `999`. Individual notification IDs: `1000 + debtId`.

### 1.6 AndroidManifest.xml Cleanup

- Remove `RECEIVE_BOOT_COMPLETED` (WorkManager auto-reschedules persisted workers)
- Remove `VIBRATE` (vibration is set programmatically on notification channel, no manifest permission needed for API 26+)

---

## 2. Ad Manager System

### 2.1 Ad Placements

| # | Placement | Type | Trigger | Frequency Cap | Screen |
|---|-----------|------|---------|---------------|--------|
| 1 | After "Add Debt" save | Interstitial | Debt saved successfully | 3 min gap | Any (modal dismiss) |
| 2 | After "Mark Settled" | Interstitial | Debt marked settled | 3 min gap | DebtDetail |
| 3 | After "Create Split" | Interstitial | Split created successfully | 3 min gap | Split |
| 4 | Debt List bottom | Banner | Always visible when tab active | Persistent | DebtList |
| 5 | AI Roast regeneration | Rewarded | Free regens exhausted (5/day) | 3/hr | DebtDetail |
| 6 | Premium export themes | Rewarded | Unlock premium HTML template | 3/hr | DebtDetail export |
| 7 | Advanced stats unlock | Rewarded | Unlock detailed analytics | 3/hr | DebtDetail stats |

### 2.2 Interstitial Ad Logic

- **Frequency cap**: 3 minutes between interstitials (`lastInterstitialAt` in `AppPreferences`)
- **Never show on**: app exit, back press, first screen, onboarding, settings
- **Only after success actions**: debt saved, debt settled, split created
- **Preload**: Load interstitial on app startup, reload after each show
- **If ad not ready**: Skip silently, no delay for user

### 2.3 Banner Ad Logic

- `AdView` at bottom of `DebtListScreen` inside a `Box` below the `LazyColumn`
- Height: 50dp standard banner
- Background: `Color(0xFF1E1E1E)` (matches card color)
- **If `isAdFree`**: Banner container hidden entirely

### 2.4 Rewarded Ad Logic

- **Frequency cap**: 3 per hour per reward type
- Track `rewardTimestamp` + `rewardCountThisHour` per type in `AppPreferences`
- Preload on entering `DebtDetailScreen` and on app startup
- Clear "Watch Ad" button with Ad icon, not misleading
- On reward earned: grant feature immediately
- On ad failed: show toast "Ad not available, try again later"

### 2.5 UMP Consent (EU/UK)

- On first ad load attempt, check UMP consent status
- If consent required (EU/UK user): show UMP consent form before loading any ad
- Use `UserMessagingPlatform.loadConsentFormIfRequired()` from `play-services-ads` 23.x
- After consent: load ads normally
- If consent denied: load non-personalized ads only (NPA)
- Store consent status in `SharedPreferences` (UMP handles this internally)

### 2.6 "Remove Ads" IAP (Google Play Billing)

**Product:** `com.dhanuk.debtbro.remove_ads` (one-time purchase)

**New files:**
- `data/billing/BillingManager.kt` — `@Singleton` Hilt-injected. Wraps `BillingClient`. Handles connection, purchase flow, consumption/acknowledgement, and verification.
- `data/billing/BillingRepository.kt` — Abstracts billing operations: `isAdFree(): Flow<Boolean>`, `purchaseRemoveAds(activity)`, `queryPurchases()`

**Modified files:**
- `AdManager.kt` — Check `billingRepository.isAdFree()` before loading/showing any ad (except rewarded). If `isAdFree == true`, skip interstitial + banner loads, hide banner container.
- `AppPreferences.kt` — Add `IS_AD_FREE` boolean key (cached from Billing queries, restored on app startup)
- `SettingsScreen.kt` — Add "Remove Ads" row in Account section with price and purchase button
- `build.gradle.kts` — Add `billing-ktx` dependency (`com.android.billingclient:billing-ktx:7.0.0`)

**Purchase flow:**
1. User taps "Remove Ads" in Settings
2. `BillingManager` launches `BillingFlowParams` with product ID
3. On purchase success: verify purchase token, acknowledge purchase, set `isAdFree = true`
4. All interstitial + banner ads disabled immediately
5. Rewarded ads still available (user opts in)

**Restore purchases:** On app startup, call `billingClient.queryPurchasesAsync()` to restore `isAdFree` status.

### 2.7 Missing File Fix

Create `res/xml/gma_ad_services_config.xml`:
```xml
<?xml version="1.0" encoding="utf-8"?>
<ad-services-config>
    <consent>https://dhanuk.page.gd/DebtBro/Privacy-Policy.html</consent>
</ad-services-config>
```

### 2.8 AppPreferences Additions

| Key | Type | Default | Purpose |
|-----|------|---------|---------|
| `is_ad_free` | Boolean | false | Cached IAP status |
| `reward_count_ai_roast` | Int | 0 | Rewarded ad count this hour (AI roast) |
| `reward_count_export` | Int | 0 | Rewarded ad count this hour (export) |
| `reward_count_stats` | Int | 0 | Rewarded ad count this hour (stats) |
| `reward_hour_start` | Long | 0L | Hour window start timestamp |

Remove: `NOTIFY_PAYMENT_ALERTS` (dead code)

---

## 3. Date Picker Fix

### 3.1 Block Past Dates

In `AddDebtBottomSheet.kt`, set `selectableDates` on `rememberDatePickerState`:
```kotlin
val todayStart = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

val selectableDates = object : SelectableDates {
    override fun isSelectableDate(utcMillis: Long): Boolean {
        return utcMillis >= todayStart
    }
}

val datePickerState = rememberDatePickerState(
    initialSelectedDateMillis = null,
    selectableDates = selectableDates
)
```

Key change: `initialSelectedDateMillis = null` (not today) — user must explicitly pick a date, accidental "OK" tap won't set today.

### 3.2 Timezone-Correct Overdue Calculation

New helper in `DateUtils.kt`:
```kotlin
fun Long.toLocalMidnight(): Long {
    val cal = Calendar.getInstance()
    cal.timeInMillis = this
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

fun daysUntilDue(dueDateMillis: Long): Int {
    val todayLocal = System.currentTimeMillis().toLocalMidnight()
    val dueLocal = dueDateMillis.toLocalMidnight()
    return ((dueLocal - todayLocal) / 86_400_000L).toInt()
}
```

Replace all raw `dueDate < System.currentTimeMillis()` comparisons with `daysUntilDue(dueDate) < 0`.

Affected files:
- `DashboardViewModel.kt` — overdue calculation
- `DebtCard.kt` — DuePill labels
- `DebtReminderWorker.kt` — overdue query window

### 3.3 Contextual DuePill Labels

In `DebtCard.kt`, replace raw date display with:
```kotlin
val days = daysUntilDue(dueDate)
val label = when {
    days < 0 -> "Overdue ${-days}d"
    days == 0 -> "Due TODAY"
    days == 1 -> "Due tomorrow"
    days <= 3 -> "Due in $days days"
    else -> dueDate.toReadableDate()
}
val color = when {
    days < 0 -> DangerRed
    days == 0 -> DangerRed
    days <= 3 -> WarningAmber
    else -> extra.subtitleGray
}
```

---

## 4. Play Store Readiness Fixes

| # | Issue | Fix | File |
|---|-------|-----|------|
| 1 | Missing `gma_ad_services_config.xml` | Create file in `res/xml/` | New: `res/xml/gma_ad_services_config.xml` |
| 2 | No UMP consent for EU/UK | Add UMP consent flow before first ad | `AdManager.kt` |
| 3 | WhatsApp share crash without app | Try-catch + chooser intent fallback | `DebtDetailViewModel.kt` |
| 4 | `RECEIVE_BOOT_COMPLETED` with no receiver | Remove permission | `AndroidManifest.xml` |
| 5 | `VIBRATE` permission unused | Remove (not needed on API 26+) | `AndroidManifest.xml` |
| 6 | `notifyPaymentAlerts` dead toggle | Remove key + settings toggle | `AppPreferences.kt`, `SettingsScreen.kt` |
| 7 | Notification prefs not read by workers | Read prefs, skip when disabled | `DebtReminderWorker.kt`, `WeeklySummaryWorker.kt` |
| 8 | No POST_NOTIFICATIONS runtime request | Add permission handler on first launch | `MainActivity.kt` + new `NotificationPermissionHandler.kt` |
| 9 | Group summary notification missing | Add group summary notification | `DebtReminderWorker.kt` |
| 10 | `default_web_client_id` manual override | Remove from `strings.xml`, let google-services plugin handle it | `res/values/strings.xml` |

### 4.1 WhatsApp Share Fix

Current code crashes if WhatsApp not installed. Fix: remove `setPackage("com.whatsapp")`, use `Intent.createChooser()` with try-catch. This also enables sharing via Telegram, SMS, etc.

---

## 5. New Sound Assets Required

| File | Purpose | Duration | Style |
|------|---------|----------|-------|
| `res/raw/chime_light.mp3` | 3-day approaching reminder | ~0.5s | Soft, pleasant ding |
| `res/raw/chime_urgent.mp3` | 1-day urgent reminder | ~0.8s | Alerting but not harsh |
| `res/raw/whistle_sharp.mp3` | Due today / overdue | ~1.0s | Sharp whistle, "paisa wapas de!" vibe |

Must be short (<2s) and royalty-free (CC0 license from freesound.org or similar).

---

## 6. Dependency Additions

| Dependency | Version | Purpose |
|-----------|---------|---------|
| `com.android.billingclient:billing-ktx` | 7.0.0 | Google Play Billing for "Remove Ads" IAP |
| `com.google.accompanist:accompanist-permissions` | 0.34.0 | Already declared — just need to USE it for POST_NOTIFICATIONS |

---

## 7. i18n Strings Required (LocalizedString.kt)

New keys for 18 languages:

| Key | English |
|-----|---------|
| `notification_permission_title` | "Enable Reminders" |
| `notification_permission_desc` | "DebtBro needs notification permission to remind you about due dates." |
| `notification_permission_denied` | "Notifications disabled. Enable in Settings for due date reminders." |
| `due_today` | "Due TODAY" |
| `due_tomorrow` | "Due tomorrow" |
| `due_in_days` | "Due in {days} days" |
| `overdue_days` | "Overdue {days}d" |
| `remove_ads` | "Remove Ads" |
| `remove_ads_desc` | "Enjoy an ad-free experience forever" |
| `purchase_success` | "Purchase successful!" |
| `watch_ad_for_feature` | "Watch an ad to unlock this feature" |
| `ad_not_available` | "Ad not available, try again later" |
| `share_via` | "Share via..." |
| `no_date_selected` | "No due date" |
| `3_day_reminder` | "{amount} due in 3 days — {name}" |
| `1_day_reminder` | "{amount} due TOMORROW — {name}" |
| `due_today_reminder` | "{amount} is due TODAY — {name}" |
| `overdue_reminder` | "{amount} is {days} days OVERDUE — {name}" |
| `debt_reminders_summary` | "{count} reminders" |

---

## 8. Scope Boundaries

**In scope:**
- Notification Manager (4 types + scheduling + permissions + sounds)
- Ad Manager (interstitial integration + banner + rewarded caps + UMP consent)
- Google Play Billing IAP for "Remove Ads"
- Date picker past-date blocking
- Timezone-correct overdue calculation
- Contextual DuePill labels
- Play Store readiness fixes (10 items)

**Out of scope:**
- Server-side purchase verification (local verification for v7)
- A/B testing ad placements
- Mediation (ironSource, AppLovin, etc.) — future consideration
- Push notifications via OneSignal (already integrated, separate system)
- Full test suite (project has zero tests — not changing this now)
