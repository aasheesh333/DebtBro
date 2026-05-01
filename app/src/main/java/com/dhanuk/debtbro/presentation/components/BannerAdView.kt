package com.dhanuk.debtbro.presentation.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.viewinterop.AndroidView
import com.dhanuk.debtbro.BuildConfig
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import kotlinx.coroutines.delay

@Composable
fun BannerAdView() {
    var adLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(3000); adLoaded = true }
    if (adLoaded) {
        AndroidView(factory = { context ->
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BuildConfig.ADMOB_BANNER_ID.ifEmpty { "ca-app-pub-3940256099942544/6300978111" }
                loadAd(AdRequest.Builder().build())
            }
        })
    }
}
