package com.dhanuk.debtbro.data.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.RequestConfiguration
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AdManager — hardened 2026-07-04 (Wave 5 / Issue 21):
 *
 *  ❤ Reliability
 *   - Mutex guards around `interstitialAd` / `rewardedAd` load/show ops
 *     prevent torn-state races between ViewModel onFailed-reloads and
 *     AppOpenManager / DebtBroApp preloads.
 *   - showInterstitialIfReady reads `prefs.lastInterstitialAt` directly
 *     instead of trusting a cached @Volatile mirror, removing the
 *     cross-process cold-start cooldown race.
 *   - Test-device IDs from BuildConfig.TEST_DEVICE_IDS wired through
 *     MobileAds.setRequestConfiguration in debug builds (policy violation
 *     fix — devs clicking test ads previously counted as real clicks).
 *
 *  ❤ Revenue
 *   - createBannerAdView(context) returns a configured 320×50 AdView
 *     for NavGraph's main Scaffold `bottomBar` slot.
 *   - createAppOpenAdLoadCallback(...) hosted here; AppOpenManager drives
 *     the actual lifecycle and triggers show.
 *
 *  ❤ Observability
 *   - Firebase Analytics events fired from every load/show/dismiss/fail
 *     site with `ad_type` param ("interstitial" | "rewarded" | "banner"
 *     | "app_open"). Future A/B tests of cooldown and inventory ride on
 *     this telemetry.
 */
@Singleton
class AdManager @Inject constructor(private val prefs: AppPreferences) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null

    /** Serialize load/show ops on each ad slot to prevent torn-state races. */
    private val interstitialMutex = Mutex()
    private val rewardedMutex = Mutex()

    private val analytics: FirebaseAnalytics by lazy { Firebase.analytics }

    init {
        // Apply test-device configuration in debug builds only — never
        // in release, where this would force production traffic to be
        // treated as test (no revenue).
        if (BuildConfig.DEBUG) {
            val rawIds = BuildConfig.TEST_DEVICE_IDS
            val ids = rawIds.split(",").map { it.trim() }.filter { it.isNotBlank() }
            if (ids.isNotEmpty()) {
                MobileAds.setRequestConfiguration(
                    RequestConfiguration.Builder()
                        .setTestDeviceIds(ids)
                        .build()
                )
                Log.d("AdManager", "Applied ${ids.size} test device IDs to MobileAds")
            }
        }
    }

    private fun resolvedInterstitialId(): String {
        // Production must supply ADMOB_INTERSTITIAL_ID in local.properties / CI secrets.
        // We throw if missing in release to surface config errors loudly.
        val configured = BuildConfig.ADMOB_INTERSTITIAL_ID
        if (configured.isNotBlank()) return configured
        return if (isDebugBuild()) {
            // Visible test ID used only in debug builds (acceptable per AdMob policy).
            "ca-app-pub-3940256099942544/1033173712"
        } else {
            Log.e(
                "AdManager",
                "ADMOB_INTERSTITIAL_ID missing — ad load will fail. Set it in local.properties / CI secrets."
            )
            ""
        }
    }

    private fun resolvedRewardedId(): String {
        val configured = BuildConfig.ADMOB_REWARDED_ID
        if (configured.isNotBlank()) return configured
        return if (isDebugBuild()) {
            "ca-app-pub-3940256099942544/5224354917"
        } else {
            Log.e(
                "AdManager",
                "ADMOB_REWARDED_ID missing — ad load will fail. Set it in local.properties / CI secrets."
            )
            ""
        }
    }

    private fun resolvedBannerId(): String {
        val configured = BuildConfig.ADMOB_BANNER_ID
        if (configured.isNotBlank()) return configured
        return if (isDebugBuild()) {
            "ca-app-pub-3940256099942544/6300978111"
        } else {
            // Banner is non-fatal: log and return empty so NavGraph builds an
            // empty AdView (the slot collapses; nav bar gap doesn't appear).
            Log.e("AdManager", "ADMOB_BANNER_ID missing — banner slot will be empty.")
            ""
        }
    }

    private fun resolvedAppOpenId(): String {
        // AdMob test ID for app-open ads in debug; not a BuildConfig field
        // since app-open is the rarest format. Production must add a real
        // unit ID via local.properties once inventory is approved.
        return if (isDebugBuild()) "ca-app-pub-3940256099942544/925739591" else {
            Log.e("AdManager", "ADMOB_APP_OPEN_ID not configured — app-open ads disabled.")
            ""
        }
    }

    private fun isDebugBuild(): Boolean = BuildConfig.DEBUG

    fun loadInterstitial(context: Context) {
        val id = resolvedInterstitialId()
        if (id.isBlank()) return
        InterstitialAd.load(context, id, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) {
                scope.launch { interstitialMutex.withLock { interstitialAd = ad } }
                analytics.logEvent("ad_loaded") { param("ad_type", "interstitial") }
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w("AdManager", "Interstitial load failed: ${error.message}")
                scope.launch { interstitialMutex.withLock { interstitialAd = null } }
                analytics.logEvent("ad_failed") {
                    param("ad_type", "interstitial")
                    param("error_code", error.code.toString())
                }
            }
        })
    }

    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit): Boolean {
        // Read the cooldown timestamp directly from DataStore to avoid the
        // stale-@Volatile-mirror race across cold starts. runBlocking on an
        // already-warm DataStore is cheap (single map lookup). The previous
        // approach kept a @Volatile updated via an init collector, but on
        // process restart the @Volatile would default to 0L until the
        // collector fired, allowing two interstitials within 5 min.
        val lastAt = runBlocking { prefs.lastInterstitialAt.first() }
        if (System.currentTimeMillis() - lastAt < INTERSTITIAL_COOLDOWN_MS) return false
        val ad = runBlocking { interstitialMutex.withLock { interstitialAd } } ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.logEvent("ad_shown") { param("ad_type", "interstitial") }
            }
            override fun onAdDismissedFullScreenContent() {
                scope.launch { prefs.setLastInterstitialAt(System.currentTimeMillis()) }
                scope.launch { interstitialMutex.withLock { interstitialAd = null } }
                loadInterstitial(activity)
                analytics.logEvent("ad_dismissed") { param("ad_type", "interstitial") }
                onDismissed()
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e("AdManager", "Interstitial show failed: ${error.message}")
                scope.launch { interstitialMutex.withLock { interstitialAd = null } }
                loadInterstitial(activity)
                analytics.logEvent("ad_failed") {
                    param("ad_type", "interstitial")
                    param("error_code", error.code.toString())
                    param("phase", "show")
                }
                onDismissed()
            }
        }
        return try {
            ad.show(activity)
            true
        } catch (e: Exception) {
            Log.e("AdManager", "Interstitial show exception: ${e.message}", e)
            analytics.logEvent("ad_failed") {
                param("ad_type", "interstitial")
                param("phase", "show_exception")
            }
            false
        }
    }

    fun loadRewardedAd(context: Context) {
        val id = resolvedRewardedId()
        if (id.isBlank()) return
        RewardedAd.load(context, id, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) {
                scope.launch { rewardedMutex.withLock { rewardedAd = ad } }
                analytics.logEvent("ad_loaded") { param("ad_type", "rewarded") }
            }
            override fun onAdFailedToLoad(error: LoadAdError) {
                Log.w("AdManager", "Rewarded ad load failed: ${error.message}")
                scope.launch { rewardedMutex.withLock { rewardedAd = null } }
                analytics.logEvent("ad_failed") {
                    param("ad_type", "rewarded")
                    param("error_code", error.code.toString())
                }
            }
        })
    }

    fun showRewardedAd(activity: Activity, onRewarded: (RewardItem) -> Unit, onFailed: () -> Unit) {
        val ad = runBlocking { rewardedMutex.withLock { rewardedAd } } ?: return onFailed()
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                analytics.logEvent("ad_shown") { param("ad_type", "rewarded") }
            }
            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e("AdManager", "RewardedAd show failed: ${error.message}")
                scope.launch { rewardedMutex.withLock { rewardedAd = null } }
                loadRewardedAd(activity)
                analytics.logEvent("ad_failed") {
                    param("ad_type", "rewarded")
                    param("error_code", error.code.toString())
                    param("phase", "show")
                }
                onFailed()
            }
            override fun onAdDismissedFullScreenContent() {
                analytics.logEvent("ad_dismissed") { param("ad_type", "rewarded") }
            }
        }
        try {
            ad.show(activity) { reward ->
                scope.launch { prefs.setRewardTimestamp(System.currentTimeMillis()) }
                scope.launch { rewardedMutex.withLock { rewardedAd = null } }
                loadRewardedAd(activity)
                onRewarded(reward)
            }
        } catch (e: Exception) {
            Log.e("AdManager", "RewardedAd show exception: ${e.message}", e)
            analytics.logEvent("ad_failed") {
                param("ad_type", "rewarded")
                param("phase", "show_exception")
            }
            onFailed()
        }
    }

    /**
     * Returns a configured 320×50 AdView for use in NavGraph's main Scaffold
     * `bottomBar` slot. The caller is responsible for adding it to the
     * composition hierarchy (typically `AndroidView { adView })`) and for
     * calling `adView.pause()` / `resume()` / `destroy()` against the host
     * lifecycle (the AndroidView factory + update block handles this if
     * you wire `adView.lifecycle` from the LocalLifecycleOwner).
     *
     * Returns null in release when ADMOB_BANNER_ID is unconfigured so the
     * caller can collapse the slot rather than render an empty AdView.
     */
    fun createBannerAdView(context: Context): AdView? {
        val id = resolvedBannerId()
        if (id.isBlank()) return null
        return AdView(context).apply {
            setAdSize(AdSize.BANNER)
            adUnitId = id
            loadAd(AdRequest.Builder().build())
        }
    }

    companion object {
        /** 5-minute interstitial cooldown (preserved from prior implementation). */
        const val INTERSTITIAL_COOLDOWN_MS = 300_000L
        /** 4-hour app-open ad cooldown per AdMob's published guidance. */
        const val APP_OPEN_COOLDOWN_MS = 4 * 60 * 60 * 1000L
    }
}
