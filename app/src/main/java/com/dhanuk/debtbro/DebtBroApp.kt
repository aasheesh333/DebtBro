package com.dhanuk.debtbro

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.google.android.gms.ads.MobileAds
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
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()
    override fun onCreate() {
        super.onCreate()
        MobileAds.initialize(this) {}
        if (BuildConfig.ONESIGNAL_APP_ID.isNotBlank()) {
            OneSignal.Debug.logLevel = LogLevel.NONE
            OneSignal.initWithContext(this, BuildConfig.ONESIGNAL_APP_ID)
            CoroutineScope(Dispatchers.IO).launch { OneSignal.Notifications.requestPermission(false) }
        }
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "daily-debt-reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DebtReminderWorker>(1, TimeUnit.DAYS).build()
        )
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "weekly-summary",
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<WeeklySummaryWorker>(7, TimeUnit.DAYS).build()
        )
    }
}
