package com.dhanuk.debtbro.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhanuk.debtbro.MainActivity
import com.dhanuk.debtbro.R
import com.dhanuk.debtbro.data.db.dao.DebtDao
import com.dhanuk.debtbro.data.datastore.AppPreferences
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

import kotlinx.coroutines.flow.first

@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val debtDao: DebtDao,
    private val prefs: com.dhanuk.debtbro.data.datastore.AppPreferences
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!prefs.notifyDailyReminder.first()) return Result.success()
        ensureChannel()
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && !notificationManager.areNotificationsEnabled()) {
            return Result.success()
        }
        val now = System.currentTimeMillis()
        val debts = (debtDao.getOverdueDebts(now) + debtDao.getDebtsDueBetween(now, now + 2 * 86400000L))
            .distinctBy { it.id }
        debts.forEach { debt ->
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("debtbro://debt/${debt.id}")
            }
            val pendingIntent = PendingIntent.getActivity(applicationContext, debt.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val amountText = com.dhanuk.debtbro.util.formatCurrency(debt.amount - debt.amountPaid, debt.currency)
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("${debt.personName} has a DebtPayoff Pro reminder")
                .setContentText("$amountText is still pending")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setGroup("debtbro_reminders")
                .build()
                .also { notificationManager.notify(1000 + debt.id, it) }
        }
        return Result.success()
    }
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "Debt reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    companion object { private const val CHANNEL = "debt_reminders" }
}
