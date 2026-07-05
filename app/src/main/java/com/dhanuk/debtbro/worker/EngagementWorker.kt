package com.dhanuk.debtbro.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhanuk.debtbro.MainActivity
import com.dhanuk.debtbro.R
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.util.LocalizedString
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class EngagementWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val debtDao: DebtDao,
    private val prefs: AppPreferences
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val enabled = runCatching { prefs.engagementNotificationsEnabled.first() }.getOrDefault(true)
        if (!enabled) return Result.success()

        ensureChannel()
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.areNotificationsEnabled()) {
            return Result.success()
        }

        val now = System.currentTimeMillis()
        val lastPushAt = runCatching { prefs.lastEngagementPushTime.first() }.getOrDefault(0L)
        if (lastPushAt > 0L && (now - lastPushAt) < TWENTY_FOUR_HOURS_MS) {
            return Result.success()
        }

        // Sync the worker's language with the persisted user preference so
        // LocalizedString.get renders the template in the user's chosen
        // language even while the UI is not running.
        runCatching { prefs.selectedLanguage.first() }.getOrNull()?.let { LocalizedString.setLanguage(it) }

        val overdue = runCatching { debtDao.getOverdueDebts(now) }.getOrDefault(emptyList())
        val lastAppActive = runCatching { prefs.lastAppActiveTime.first() }.getOrDefault(0L)
        val state = when {
            overdue.isNotEmpty() -> STATE_OVERDUE
            lastAppActive > 0L && (now - lastAppActive) > THREE_DAYS_MS -> STATE_INACTIVE
            else -> STATE_ACTIVE
        }

        val templateRange = when (state) {
            STATE_OVERDUE -> 0..3
            STATE_INACTIVE -> 4..6
            else -> 7..9
        }
        val templateIdx = templateRange.random()
        val message = LocalizedString.get("engagement_template_$templateIdx")
        if (message.isBlank()) {
            Log.w(TAG, "Empty engagement template for idx=$templateIdx; skipping push")
            return Result.success()
        }

        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            ENGAGEMENT_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(android.graphics.BitmapFactory.decodeResource(applicationContext.resources, R.drawable.ic_launcher_foreground))
            .setColor(0xFF00E5A0.toInt())
            .setContentTitle("DebtPayoff Pro")
            .setContentText(message)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOnlyAlertOnce(true)
            .setGroup("debtbro_engagement")
            .build()
            .also { notificationManager.notify(ENGAGEMENT_NOTIFICATION_ID, it) }

        runCatching { prefs.setLastEngagementPushTime(now) }
        Log.d(TAG, "Engagement push sent (state=$state, template=$templateIdx)")
        return Result.success()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Debt reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    companion object {
        private const val TAG = "EngagementWorker"
        private const val CHANNEL = "debt_reminders"
        private const val ENGAGEMENT_REQUEST_CODE = 7011
        private const val ENGAGEMENT_NOTIFICATION_ID = 7011
        private const val STATE_OVERDUE = "overdue"
        private const val STATE_INACTIVE = "inactive"
        private const val STATE_ACTIVE = "active"
        private const val TWENTY_FOUR_HOURS_MS = 24L * 60L * 60L * 1000L
        private const val THREE_DAYS_MS = 3L * 24L * 60L * 60L * 1000L
    }
}
