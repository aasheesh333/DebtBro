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
class WeeklySummaryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val debtDao: DebtDao
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ensureChannel()
        val debts = debtDao.getAllDebtsOnce()
        val currency = debts.firstOrNull()?.currency ?: "₹"
        val owed = debts.filter { it.type == "THEY_OWE_ME" && it.status != "SETTLED" }.sumOf { it.amount - it.amountPaid }
        val recovered = debts.filter { it.status == "SETTLED" && System.currentTimeMillis() - it.updatedAt < 7 * 86400000L }.sumOf { it.amount }
        val intent = PendingIntent.getActivity(applicationContext, 42, Intent(applicationContext, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("DebtBro weekly summary")
            .setContentText("Owed: ${currency}${owed.toInt()} • Recovered this week: ${currency}${recovered.toInt()}")
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
            .also { (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(77, it) }
        return Result.success()
    }
    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel(CHANNEL, "Weekly summary", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }
    companion object { private const val CHANNEL = "weekly_summary" }
}
