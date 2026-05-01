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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class DebtReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val debtDao: DebtDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ensureChannel()
        val now = System.currentTimeMillis()
        val debts = debtDao.getOverdueDebts(now) + debtDao.getDebtsDueBetween(now, now + 2 * 86400000L)
        debts.distinctBy { it.id }.forEach { debt ->
            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("debtbro://debt/${debt.id}")
            }
            val pendingIntent = PendingIntent.getActivity(applicationContext, debt.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_launcher)
                .setContentTitle("${debt.personName} has a DebtBro reminder")
                .setContentText("${debt.currency}${(debt.amount - debt.amountPaid).toInt()} is still pending")
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .build()
                .also { (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(1000 + debt.id, it) }
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
