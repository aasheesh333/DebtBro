package com.dhanuk.debtbro

import android.app.Application
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.ads.AppOpenManager
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.UserMessagingPlatform
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.FirebasePerformance
import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel
import com.dhanuk.debtbro.worker.DebtReminderWorker
import com.dhanuk.debtbro.worker.WeeklySummaryWorker
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@HiltAndroidApp
class DebtBroApp : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var adManager: AdManager
    @Inject lateinit var appOpenManager: AppOpenManager
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

    /** Minimum interval between retry-loads triggered by NetworkCallback
     *  onAvailable — throttles flaky-network spam. */
    private var lastNetworkRetryAt = 0L
    /** Guards against double-init of MobileAds (the UMP success path AND
     *  the eager canRequestAds() check can both fire). */
    @Volatile private var adsInitialized = false

    override fun onCreate() {
        super.onCreate()

        // ── Crashlytics opt-in (collected only in non-debug if enabled) ─────────
        val collectCrashlytics = BuildConfig.ENABLE_CRASHLYTICS
        FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(collectCrashlytics)

        // ── Install a default uncaught-exception handler that pipes into Crashlytics
        //    and keeps the system default so the OS still tears the process down properly.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                crashlytics.recordException(throwable)
                analytics.logEvent("uncaught_exception") {
                    param("thread_name", thread.name)
                    param("exception_class", throwable.javaClass.name)
                }
            } catch (e: Throwable) {
                Log.e("DebtBroApp", "Error reporting crash failed: ${e.message}", e)
            }
            previousHandler?.uncaughtException(thread, throwable)
        }

        // ── Performance monitoring ─────────────────────────────────────────────
        try {
            FirebasePerformance.getInstance().isPerformanceCollectionEnabled = BuildConfig.ENABLE_PERFORMANCE_MONITORING
        } catch (_: Exception) { /* Property may be unavailable on some devices */ }

        // ── OneSignal (must initialize BEFORE Ads) ─────────────────────────────
        if (BuildConfig.ONESIGNAL_APP_ID.isNotBlank()) {
            OneSignal.Debug.logLevel = if (BuildConfig.DEBUG) LogLevel.VERBOSE else LogLevel.NONE
            OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
            // Defer notification permission request to first UI screen
            // (POST_NOTIFICATIONS runtime permission should be requested with user gesture)
        }

        // ── UMP consent + Ads initialization (2026-07-04, Wave 5 Issue 21 3C) ───
        // The actual UMP requestConsentInfoUpdate MUST run from an Activity
        // context (UMP 2.x SDK signature requires Activity), so we drive the
        // consent flow from MainActivity.onCreate. Here in Application.onCreate
        // we only fire the eager path: if consent was persisted in a prior
        // session (canRequestAds() returns true immediately), MobileAds can be
        // initialized at app start without waiting for the Activity / consent
        // dialog. The MainActivity path covers first-launch EEA consent.
        val consentInfo = UserMessagingPlatform.getConsentInformation(this)
        if (consentInfo.canRequestAds()) initializeAds()

        // ── WorkManager ────────────────────────────────────────────────────────
        val workerConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
            .build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-debt-reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS)
                .setConstraints(workerConstraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly-summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS)
                .setConstraints(workerConstraints)
                .setBackoffCriteria(androidx.work.BackoffPolicy.EXPONENTIAL, 10, TimeUnit.MINUTES)
                .build()
        )

        // ── First-launch analytics ─────────────────────────────────────────────
        analytics.logEvent("app_launched") {
            param("theme_dark", "SYSTEM")
            param("version_name", BuildConfig.VERSION_NAME)
            param("version_code", BuildConfig.VERSION_CODE.toString())
        }
    }

    /**
     * Initialize MobileAds, preload interstitial + rewarded ads (eliminates
     * the "first tap fires before preload completes" race), wire the
     * ConnectivityManager network-restore retry trigger, and register
     * [AppOpenManager] for cold-start / foreground app-open ads.
     *
     * Idempotent — protected by [adsInitialized] so the eager path in
     * [onCreate] AND MainActivity's UMP success callback can both call
     * this safely; only the first call wins.
     *
     * Called from:
     *  - [DebtBroApp.onCreate] eagerly, if UMP persisted consent is already
     *    `canRequestAds()=true` from a prior session.
     *  - MainActivity.onCreate after the UMP consent flow's success / failure
     *    callback, if `canRequestAds()` is true at that point.
     */
    fun initializeAds() {
        if (adsInitialized) return
        adsInitialized = true
        synchronized(this) {
            try {
                MobileAds.initialize(this) {
                    Log.d("DebtBroApp", "MobileAds init complete")
                    adManager.loadInterstitial(this)
                    // Pre-load rewarded ad at launch so the very first
                    // AnalyticsScreen / SplitScreen AI Take tap doesn't
                    // fire `showRewardedAd` against an unloaded slot.
                    adManager.loadRewardedAd(this)
                    appOpenManager.register(this)
                }
            } catch (e: Exception) {
                Log.e("DebtBroApp", "MobileAds initialize failed", e)
            }
        }

        // ── Network-restore retry trigger ─────────────────────────────────────
        // If MobileAds.loadInterstitial / loadRewardedAd fail (offline at app
        // start, flaky handoff, etc.), the slot stays null for the whole
        // session. This NetworkCallback fires onAvailable and re-issues the
        // loads. Throttled to once per 30s to avoid spamming on bad Wi-Fi.
        registerNetworkRetryCallback()
    }

    private fun registerNetworkRetryCallback() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                val now = System.currentTimeMillis()
                if (now - lastNetworkRetryAt < 30_000L) return
                lastNetworkRetryAt = now
                Log.d("DebtBroApp", "Network restored — retrying ad loads.")
                adManager.loadInterstitial(this@DebtBroApp)
                adManager.loadRewardedAd(this@DebtBroApp)
            }
        })
    }
}