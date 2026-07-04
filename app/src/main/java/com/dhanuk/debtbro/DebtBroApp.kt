package com.dhanuk.debtbro

import android.app.Application
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.dhanuk.debtbro.data.ads.AdManager
import com.google.android.gms.ads.MobileAds
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
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }
    private val crashlytics: FirebaseCrashlytics by lazy { FirebaseCrashlytics.getInstance() }

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

        // ── Ads ────────────────────────────────────────────────────────────────
        try {
            MobileAds.initialize(this) { status ->
                Log.d("DebtBroApp", "MobileAds init complete: ${status.adapterStatusByAdNetwork.size} adapter(s)")
                adManager.loadInterstitial(this)
            }
        } catch (e: Exception) {
            Log.e("DebtBroApp", "MobileAds initialize failed", e)
        }

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
}
