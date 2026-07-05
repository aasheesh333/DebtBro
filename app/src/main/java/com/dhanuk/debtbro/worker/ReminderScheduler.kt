package com.dhanuk.debtbro.worker

import android.content.Context
import android.util.Log
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.dhanuk.debtbro.data.db.entity.DebtEntity
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val workManager: WorkManager
) {
    suspend fun scheduleForDebt(debt: DebtEntity) {
        val dueDate = debt.dueDate
        if (dueDate == null || debt.settled || debt.status == SETTLED) {
            cancelForDebt(debt.id.toLong())
            return
        }
        val zone = ZoneId.systemDefault()
        val dueInstant = Instant.ofEpochMilli(dueDate)
        val dueZdt = dueInstant.atZone(zone)
        val dueDateLocal = dueZdt.toLocalDate()

        val targets = listOf(
            WINDOW_7D to dueDateLocal.minusDays(7),
            WINDOW_3D to dueDateLocal.minusDays(3),
            WINDOW_1D to dueDateLocal.minusDays(1),
            WINDOW_0D to dueDateLocal
        ).map { (window, day) -> window to day.atTime(9, 0).atZone(zone) }

        val now = Instant.now()
        targets.forEach { (window, targetZdt) ->
            val name = workName(debt.id.toLong(), window)
            val tag = tag(debt.id.toLong())
            val delayMs = targetZdt.toInstant().toEpochMilli() - now.toEpochMilli()
            if (delayMs <= 0L) return@forEach

            val data = Data.Builder()
                .putLong(DebtDueDateReminderWorker.KEY_DEBT_ID, debt.id.toLong())
                .putString(DebtDueDateReminderWorker.KEY_DEBT_NAME, debt.personName)
                .putString(DebtDueDateReminderWorker.KEY_DUE_DATE_ISO, dueDate.toString())
                .putString(DebtDueDateReminderWorker.KEY_WINDOW, window)
                .build()

            val request = OneTimeWorkRequestBuilder<DebtDueDateReminderWorker>()
                .setInitialDelay(delayMs, TimeUnit.MILLISECONDS)
                .setInputData(data)
                .addTag(tag)
                .build()

            workManager.enqueueUniqueWork(name, ExistingWorkPolicy.REPLACE, request)
            Log.d(TAG, "Scheduled $name (delay=${delayMs}ms, target=$targetZdt)")
        }
    }

    fun cancelForDebt(debtId: Long) {
        val tag = tag(debtId)
        workManager.cancelAllWorkByTag(tag)
        WINDOWS.forEach { window ->
            workManager.cancelUniqueWork(workName(debtId, window))
        }
        Log.d(TAG, "Cancelled reminders for debt=$debtId (tag=$tag)")
    }

    suspend fun rescheduleAll(repository: DebtRepository) {
        runCatching {
            val debts = repository.getAllUnscheduledDueDebts()
            Log.d(TAG, "Rescheduling ${debts.size} unscheduled due debts")
            debts.forEach { scheduleForDebt(it) }
            val ids = debts.map { it.id }.distinct()
            ids.forEach { id ->
                runCatching { repository.markReminderScheduled(id) }
            }
        }.onFailure { e ->
            Log.e(TAG, "rescheduleAll failed: ${e.message}", e)
        }
    }

    private fun workName(debtId: Long, window: String) = "due_reminder_${debtId}_$window"
    private fun tag(debtId: Long) = "due_reminder_$debtId"

    companion object {
        private const val TAG = "ReminderScheduler"
        const val SETTLED = "SETTLED"
        const val WINDOW_7D = "7d"
        const val WINDOW_3D = "3d"
        const val WINDOW_1D = "1d"
        const val WINDOW_0D = "0d"
        private val WINDOWS = listOf(WINDOW_7D, WINDOW_3D, WINDOW_1D, WINDOW_0D)
    }
}
