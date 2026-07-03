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
import kotlinx.coroutines.delay
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.components.NotificationPermissionHandler
import com.dhanuk.debtbro.presentation.navigation.DebtBroNavGraph
import com.dhanuk.debtbro.presentation.theme.DebtBroTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appPreferences: AppPreferences
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
                                DebtBroNavGraph(appPreferences = appPreferences)
                            }
                        } else {
                            DebtBroNavGraph(appPreferences = appPreferences)
                        }
                    } else {
                        // Show a blank screen while waiting for theme to load (splash screen is visible)
                        Box(modifier = Modifier.fillMaxSize())
                    }
                }
            }
        }
    }
}
