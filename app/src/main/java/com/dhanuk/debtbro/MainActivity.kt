package com.dhanuk.debtbro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.presentation.navigation.DebtBroNavGraph
import com.dhanuk.debtbro.presentation.theme.DebtBroTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var appPreferences: AppPreferences
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DebtBroTheme {
                CompositionLocalProvider(
                    LocalLifecycleOwner provides this
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        DebtBroNavGraph(appPreferences = appPreferences)
                    }
                }
            }
        }
    }
}
