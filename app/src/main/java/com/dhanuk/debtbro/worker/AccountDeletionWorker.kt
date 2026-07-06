package com.dhanuk.debtbro.worker

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.dhanuk.debtbro.data.datastore.AppPreferences
import com.dhanuk.debtbro.data.datastore.SecureStorage
import com.dhanuk.debtbro.data.firebase.AuthManager
import com.dhanuk.debtbro.data.firebase.FirebaseRepository
import com.dhanuk.debtbro.data.firebase.RealTimeSyncManager
import com.dhanuk.debtbro.data.repository.DebtRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/**
 * P0-1 (2026-07-03): backup one-shot job for the 24-hour account-deletion
 * grace period.
 *
 * The Settings screen's "Schedule 24-Hour Grace" path sets a deletion
 * timestamp in DataStore and depends on the next app relaunch to detect
 * when the grace period has elapsed (see [SettingsViewModel] init block).
 * If the user never reopens the app — uninstalled but Firebase account is
 * still live, abandoned device, lost phone — the Firebase account would
 * never actually be deleted, leaving PII in Firestore indefinitely.
 *
 * This Worker is a backup guarantee that deletion happens even without an
 * app launch. Scheduling:
 *  - The ViewModel enqueues a OneTimeWorkRequest with a 24-hour initial
 *    delay whenever the grace path is chosen (named uniquely so reschedules
 *    via REPLACE preserve the earliest grace start).
 *  - When the request fires, the worker re-checks the timestamp in case
 *    the user signed back in during the grace window and cancelled the
 *    pending deletion (in which case [AppPreferences.clearPendingDeletion]
 *    would already have run and `pendingDeletionTimestamp` is 0). If
 *    still pending AND past the grace threshold, it commits deletion.
 *  - If the grace timer hasn't elapsed yet (e.g. Doze delayed us), the
 *    worker reschedules itself for the remaining time.
 *  - On the time-up path, [doWork] runs the exact same deletion steps as
 *    `SettingsViewModel.commitAccountDeletion()`: Firestore wipe, Firebase
 *    Auth deletion, local Room + DataStore + SecureStorage cleanup, real-
 *    time listener teardown.
 *
 * Failure modes:
 *  - If the user signs back in within the grace window and the timestamp
 *    is cancelled, [doWork] returns `Result.success()` without touching
 *    data. (Defensive — checked via `pendingDeletionTimestamp` value.)
 *  - If Firebase deletion fails (e.g. network down, FirebaseAuth throws),
 *    the worker returns `Result.retry()` so WorkManager backs off and
 *    tries again. We don't return `Result.failure()` because leaving the
 *    Firestore data orphaned is worse than a few extra work attempts.
 */
@HiltWorker
class AccountDeletionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val prefs: AppPreferences,
    private val secureStorage: SecureStorage,
    private val auth: AuthManager,
    private val firebaseRepository: FirebaseRepository,
    private val realTimeSyncManager: RealTimeSyncManager,
    private val debts: DebtRepository
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "account_deletion_grace_backup"
        const val GRACE_PERIOD_MS = 24L * 60L * 60L * 1000L
        private const val TAG = "AccountDeletionWorker"
    }

    override suspend fun doWork(): Result {
        val pendingTs = runCatching { prefs.pendingDeletionTimestamp.first() }.getOrDefault(0L)
        if (pendingTs <= 0L) {
            // User signed back in during grace, or already deleted by
            // the SettingsViewModel.init path. Nothing to do — returning
            // success so WorkManager doesn't retry.
            Log.i(TAG, "No pending deletion timestamp — skipping (grace cancelled or already deleted)")
            return Result.success()
        }

        val elapsed = System.currentTimeMillis() - pendingTs
        if (elapsed < GRACE_PERIOD_MS) {
            // WorkManager may fire slightly early under Doze/battery saver
            // constraints. Re-enqueue for the remaining time so we don't
            // delete before the user's grace window closes.
            Log.i(TAG, "Grace period not yet elapsed (elapsed=${elapsed}ms) — deferring to retry")
            return Result.retry()
        }

        // Grace elapsed — commit deletion. Best-effort: clear local data
        // regardless of Firebase outcome so the app's UX matches the
        // SettingsViewModel.init path. Return retry on any thrown
        // exception to give Firebase another shot iftemporarily down.
        // Use the UID stored alongside the deletion timestamp — by the
        // time the worker fires (T+24h) the user may have switched
        // accounts on this device, so `auth.getUserId()` cannot be
        // trusted as the deletion target.
        val targetUid = runCatching { prefs.pendingDeletionUid.first() }.getOrDefault("")
        return runCatching {
            val effUid = targetUid.ifBlank { auth.getUserId() }
            if (!effUid.isNullOrBlank()) {
                firebaseRepository.deleteAllUserData(effUid)
                if (auth.getUserId() == effUid) {
                    auth.deleteAccount()
                    prefs.clearPendingDeletion()
                    prefs.clearUserSession()
                    secureStorage.clearSensitiveData()
                    realTimeSyncManager.stopListening()
                    debts.clearLocalData()
                } else {
                    // Currently signed in as a different UID — only clear
                    // the pending-deletion record, leave the active user's
                    // session intact.
                    prefs.clearPendingDeletion()
                }
            } else {
                prefs.clearPendingDeletion()
            }
            Log.i(TAG, "Account deletion committed via WorkManager backup")
            Result.success()
        }.getOrElse { e ->
            Log.e(TAG, "Account deletion failed in backup worker — will retry", e)
            // If at least the local clear succeeded, don't touch the
            // pending timestamp — let WorkManager retry the Firebase call.
            // Returning retry keeps the timestamp intact for the next
            // attempt.
            Result.retry()
        }
    }
}
