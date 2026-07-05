package com.dhanuk.debtbro

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dhanuk.debtbro.data.ads.AdManager
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.components.NotificationPermissionHandler
import com.dhanuk.debtbro.presentation.navigation.DebtBroNavGraph
import com.dhanuk.debtbro.presentation.theme.DebtBroTheme
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appPreferences: AppPreferences
    @Inject lateinit var adManager: AdManager
    override fun onCreate(savedInstanceState: Bundle?) {
        var isThemeReady by mutableStateOf(false)
val splashScreen = installSplashScreen()
        // Hard fallback: even if DataStore read stalls, surface the app after 1500ms
        // to avoid an indefinite blank screen on slow disks / corrupted prefs.
        splashScreen.setKeepOnScreenCondition { !isThemeReady }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appPreferences.themeMode.collectAsState(initial = null)
            LaunchedEffect(themeMode) {
                if (themeMode != null) {
                    isThemeReady = true
                }
            }
            // Safety net: never block the UI for >1.5s — fall back to system theme.
            LaunchedEffect(Unit) {
                delay(1500L)
                if (!isThemeReady) isThemeReady = true
            }
            val darkTheme = when (themeMode) {
                "LIGHT" -> false
                "DARK" -> true
                else -> isSystemInDarkTheme()
            }
            DebtBroTheme(darkTheme = darkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isThemeReady) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            NotificationPermissionHandler(appPreferences = appPreferences, onPermissionHandled = {}) {
                                DebtBroNavGraph(appPreferences = appPreferences, adManager = adManager)
                            }
                        } else {
                            DebtBroNavGraph(appPreferences = appPreferences, adManager = adManager)
                        }
                    } else {
                        // Show a blank screen while waiting for theme to load (splash screen is visible)
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }

        // ── App-open ad gate: flip firstLaunchDone at end of first onCreate ─────────
        // AdMob policy forbids app-open ads on the very first launch ever.
        // AppOpenManager checks prefs.firstLaunchDone before showing; we flip
        // the gate here so the SECOND cold start onward becomes the first
        // ad-eligible session. Disk IO is fire-and-forget on a background
        // dispatcher — never blocks the activity.
        CoroutineScope(Dispatchers.IO).launch { appPreferences.setFirstLaunchDone() }

        // ── UMP consent flow (EEA / UK / CH personalized ads) ────────────────────
        // AdMob requires the User Messaging Platform consent flow for personalized
        // ads in EEA / UK / Switzerland. The flow is asynchronous; we drive it
        // from MainActivity because UMP's requestConsentInfoUpdate API requires
        // an Activity context (not Application). After the flow completes,
        // `canRequestAds()` reflects the user's choice — if true, we initialize
        // MobileAds via DebtBroApp.initializeAds(). If false, the app simply
        // runs without personalized ads (non-personalized defaults kick in, no
        // policy violation). Per UMP guidance, also check canRequestAds()
        // eagerly because if consent was already obtained in a prior session
        // the SDK returns true immediately (and the requestConsentInfoUpdate
        // success callback may not fire).
        //
        // NOTE: DebtBroApp.onCreate ALSO runs the eager canRequestAds() check
        // and calls initializeAds if true; the @Volatile adsInitialized guard
        // in DebtBroApp makes the second call a no-op.
        runUmpConsentFlow()
    }

    override fun onResume() {
        super.onResume()
        CoroutineScope(Dispatchers.IO).launch { appPreferences.setLastAppActiveTime(System.currentTimeMillis()) }
    }

    /**
     * Drives the UMP consent flow on every Activity onCreate. On first launch
     * in EEA, this shows the consent form dialog (loadAndShowConsentFormIfRequired).
     * On subsequent launches or non-EEA regions, canRequestAds() is true and
     * we initialize MobileAds immediately without UI.
     */
    private fun runUmpConsentFlow() {
        val consentInfo = UserMessagingPlatform.getConsentInformation(this)
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        // Fast path: persisted consent from a prior session — initialize ads now.
        val debtBroApp = application as DebtBroApp
        if (consentInfo.canRequestAds()) {
            debtBroApp.initializeAds()
        }

        // Always re-check consent on each Activity creation — AdMob / UMP
        // guidance explicitly states "requestConsentInfoUpdate should be
        // called on every app launch" because regional / regulatory status
        // can change between launches.
        try {
            consentInfo.requestConsentInfoUpdate(
                this,
                params,
                {
                    // Success — UMP's stored consent state is up to date. If the
                    // user is in a region that requires consent AND hasn't yet
                    // consented (e.g. first launch in EEA), this loads + shows
                    // the consent form. After dismissal, `canRequestAds()`
                    // reflects whether the user opted in.
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(this) { formError ->
                        if (formError != null) {
                            android.util.Log.w(
                                "MainActivity",
                                "UMP consent form error: ${formError.errorCode} ${formError.message}"
                            )
                        }
                        if (consentInfo.canRequestAds()) {
                            debtBroApp.initializeAds()
                        }
                    }
                },
                { requestError ->
                    // Network or other failure — per UMP guidance, fall back to
                    // canRequestAds() check; if it's true (e.g. non-EEA or prior
                    // consent) we can still serve ads.
                    android.util.Log.w(
                        "MainActivity",
                        "UMP requestConsentInfoUpdate failed: ${requestError.errorCode} ${requestError.message}"
                    )
                    if (consentInfo.canRequestAds()) {
                        debtBroApp.initializeAds()
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("MainActivity", "UMP requestConsentInfoUpdate exception", e)
            // Defensive fallback — initialize ads anyway; the SDK gates ad
            // requests by its own consent state regardless of our flow.
            if (consentInfo.canRequestAds()) {
                debtBroApp.initializeAds()
            }
        }
    }
}
