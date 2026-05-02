package com.dhanuk.debtbro.data.ads

import android.app.Activity
import android.content.Context
import com.dhanuk.debtbro.BuildConfig
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdManager @Inject constructor(private val prefs: AppPreferences) {
    private var interstitialAd: InterstitialAd? = null
    private var rewardedAd: RewardedAd? = null
    fun loadInterstitial(context: Context) {
        val id = BuildConfig.ADMOB_INTERSTITIAL_ID.ifEmpty { "ca-app-pub-3940256099942544/1033173712" }
        InterstitialAd.load(context, id, AdRequest.Builder().build(), object : InterstitialAdLoadCallback() {
            override fun onAdLoaded(ad: InterstitialAd) { interstitialAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { interstitialAd = null }
        })
    }
    fun showInterstitialIfReady(activity: Activity, onDismissed: () -> Unit): Boolean {
        val lastShown = runBlocking { prefs.lastInterstitialAt.first() }
        if (System.currentTimeMillis() - lastShown < 300000L) return false
        val ad = interstitialAd ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                CoroutineScope(Dispatchers.IO).launch { prefs.setLastInterstitialAt(System.currentTimeMillis()) }
                interstitialAd = null
                loadInterstitial(activity)
                onDismissed()
            }
        }
        ad.show(activity)
        return true
    }
    fun loadRewardedAd(context: Context) {
        val id = BuildConfig.ADMOB_REWARDED_ID.ifEmpty { "ca-app-pub-3940256099942544/5224354917" }
        RewardedAd.load(context, id, AdRequest.Builder().build(), object : RewardedAdLoadCallback() {
            override fun onAdLoaded(ad: RewardedAd) { rewardedAd = ad }
            override fun onAdFailedToLoad(error: LoadAdError) { rewardedAd = null }
        })
    }
    fun showRewardedAd(activity: Activity, onRewarded: (RewardItem) -> Unit, onFailed: () -> Unit) {
        val ad = rewardedAd ?: return onFailed()
        ad.show(activity) { reward ->
            CoroutineScope(Dispatchers.IO).launch { prefs.setRewardTimestamp(System.currentTimeMillis()) }
            rewardedAd = null
            loadRewardedAd(activity)
            onRewarded(reward)
        }
    }
}
