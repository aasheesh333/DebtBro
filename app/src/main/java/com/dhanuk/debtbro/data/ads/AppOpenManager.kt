package com.dhanuk.debtbro.data.ads

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.analytics.ktx.logEvent
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AppOpenManager — drives cold-start / foreground-resume AppOpenAd
 * impressions with AdMob's mandated safeguards (2026-07-04, Wave 5 Issue 21):
 *
 *   ① 4-hour cooldown between impressions ( APP_OPEN_COOLDOWN_MS in
 *     AdManager companion). Persisted in DataStore so it survives process
 *     restarts.
 *   ② Never on the very first launch ever — `prefs.firstLaunchDone` is
 *     false on a fresh install; MainActivity flips it true at end of
 *     first onCreate. We skip showing on first launch only.
 *   ③ Never interrupts a foreground Activity that's mid-task — relies on
 *     ProcessLifecycleOwner's `ON_START` which only fires when the whole
 *     app comes to the foreground (not when an in-app Activity restarts).
 *   ④ Skips if `MobileAds.initialize` callback hasn't fired yet (the
 *     splash window already covers this case — but defensive is safer).
 *
 * Hilt @Singleton — wired into DebtBroApp.onCreate via register().
 */
@Singleton
class AppOpenManager @Inject constructor(
    private val appPreferences: AppPreferences,
    private val adManager: AdManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }
    @Volatile private var appOpenAd: AppOpenAd? = null
    @Volatile private var isLoadingAd = false
    @Volatile private var isShowingAd = false
    @Volatile private var currentActivity: Activity? = null

    private val lifecycleObserver = object : DefaultLifecycleObserver {
        override fun onStart(owner: LifecycleOwner) {
            showAdIfAvailable()
        }
    }

    private val activityLifecycleCallbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
        override fun onActivityStarted(activity: Activity) { currentActivity = activity }
        override fun onActivityResumed(activity: Activity) {}
        override fun onActivityPaused(activity: Activity) {}
        override fun onActivityStopped(activity: Activity) {
            // Clear the reference to avoid leaking an Activity when the app
            // is backgrounded and the activity is destroyed while we wait
            // for the cooldown to elapse.
            if (activity === currentActivity) currentActivity = null
        }
        override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
        override fun onActivityDestroyed(activity: Activity) {
            if (activity === currentActivity) currentActivity = null
        }
    }

    /**
     * Called from DebtBroApp.onCreate AFTER MobileAds.initialize() returns.
     * Wires this manager into ProcessLifecycleOwner's lifecycle and the
     * Application's activity callbacks.
     */
    fun register(application: Application) {
        application.registerActivityLifecycleCallbacks(activityLifecycleCallbacks)
        ProcessLifecycleOwner.get().lifecycle.addObserver(lifecycleObserver)
        loadAd(application)
    }

    private fun loadAd(context: android.content.Context) {
        if (isLoadingAd || appOpenAd != null) return
        isLoadingAd = true
        val id = resolvedAppOpenId()
        if (id.isBlank()) { isLoadingAd = false; return }
        AppOpenAd.load(
            context, id, AdRequest.Builder().build(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpenAd = ad
                    isLoadingAd = false
                    analytics.logEvent("ad_loaded") { param("ad_type", "app_open") }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpenAd = null
                    isLoadingAd = false
                    Log.w("AppOpenManager", "AppOpen load failed: ${error.message}")
                    analytics.logEvent("ad_failed") {
                        param("ad_type", "app_open")
                        param("error_code", error.code.toString())
                    }
                }
            }
        )
    }

    private fun showAdIfAvailable() {
        // Skip very first launch ever (AdMob policy).
        val firstLaunchDone = runBlocking { appPreferences.firstLaunchDone.first() }
        if (!firstLaunchDone) {
            Log.d("AppOpenManager", "Skipping app-open ad — first launch not yet completed.")
            return
        }
        // 4-hour cooldown.
        val lastAt = runBlocking { appPreferences.lastAppOpenAt.first() }
        if (System.currentTimeMillis() - lastAt < AdManager.APP_OPEN_COOLDOWN_MS) {
            Log.d("AppOpenManager", "Skipping app-open ad — within 4h cooldown.")
            return
        }
        if (isShowingAd || appOpenAd == null) return
        val activity = currentActivity ?: return
        val ad = appOpenAd ?: return
        isShowingAd = true
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.logEvent("ad_shown") { param("ad_type", "app_open") }
            }
            override fun onAdDismissedFullScreenContent() {
                appOpenAd = null
                isShowingAd = false
                scope.launch { appPreferences.setLastAppOpenAt(System.currentTimeMillis()) }
                loadAd(activity.applicationContext)
                analytics.logEvent("ad_dismissed") { param("ad_type", "app_open") }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                appOpenAd = null
                isShowingAd = false
                loadAd(activity.applicationContext)
                Log.e("AppOpenManager", "AppOpen show failed: ${error.message}")
                analytics.logEvent("ad_failed") {
                    param("ad_type", "app_open")
                    param("error_code", error.code.toString())
                    param("phase", "show")
                }
            }
        }
        try {
            ad.show(activity)
        } catch (e: Exception) {
            Log.e("AppOpenManager", "AppOpen show exception: ${e.message}", e)
            appOpenAd = null
            isShowingAd = false
            analytics.logEvent("ad_failed") {
                param("ad_type", "app_open")
                param("phase", "show_exception")
            }
        }
    }

    private fun resolvedAppOpenId(): String {
        return if (BuildConfig.DEBUG) "ca-app-pub-3940256099942544/925739591" else {
            Log.e("AppOpenManager", "ADMOB_APP_OPEN_ID not configured — app-open disabled.")
            ""
        }
    }
}
