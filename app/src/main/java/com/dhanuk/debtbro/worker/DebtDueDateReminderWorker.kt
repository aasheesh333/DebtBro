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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@HiltWorker
class DebtDueDateReminderWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val debtId = inputData.getLong(KEY_DEBT_ID, -1L)
        val debtName = inputData.getString(KEY_DEBT_NAME) ?: return Result.success()
        val dueDateIso = inputData.getString(KEY_DUE_DATE_ISO) ?: ""
        val windowLabel = inputData.getString(KEY_WINDOW) ?: "0d"
        if (debtId <= 0L) return Result.success()

        return runCatching {
            ensureChannel()
            val notificationManager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N &&
                !notificationManager.areNotificationsEnabled()
            ) {
                return Result.success()
            }

            val dueDateText = formatDate(dueDateIso)
            val title = "Upcoming payment reminder"
            val body = "$debtName is due on $dueDateText. Don't forget!"

            val intent = Intent(applicationContext, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = android.net.Uri.parse("debtbro://debt/$debtId")
            }
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                debtId.toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notificationId = (debtId.toInt() * 10) + windowOrdinal(windowLabel)
            NotificationCompat.Builder(applicationContext, CHANNEL)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(0xFF00E5A0.toInt())
                .setContentTitle(title)
                .setContentText(body)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setOnlyAlertOnce(true)
                .setGroup("debtbro_due_reminders")
                .build()
                .also { notificationManager.notify(notificationId, it) }

            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "Failed to post due-date reminder for debt=$debtId window=$windowLabel: ${e.message}", e)
            Result.success()
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = applicationContext
                .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Debt reminders", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
    }

    private fun formatDate(iso: String): String {
        if (iso.isBlank()) return ""
        return runCatching {
            // dueDateIso is an epoch-millis string (we write it from DebtEntity.dueDate)
            val millis = iso.toLongOrNull()
                ?: SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.getDefault()).parse(iso)?.time
                ?: return ""
            SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(millis))
        }.getOrNull() ?: iso
    }

    private fun windowOrdinal(label: String): Int = when (label) {
        "7d" -> 7
        "3d" -> 3
        "1d" -> 1
        else -> 0
    }

    companion object {
        const val TAG = "DueDateReminderWorker"
        const val CHANNEL = "debt_reminders"
        const val KEY_DEBT_ID = "debtId"
        const val KEY_DEBT_NAME = "debtName"
        const val KEY_DUE_DATE_ISO = "dueDateIso"
        const val KEY_WINDOW = "windowLabel"

        const val WINDOW_7D = "7d"
        const val WINDOW_3D = "3d"
        const val WINDOW_1D = "1d"
        const val WINDOW_0D = "0d"
    }
}
