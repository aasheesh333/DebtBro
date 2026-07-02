package com.dhanuk.debtbro.data.network

import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Backend Cloud Function call that records an account-deletion request
 * server-side, kicking off the 24-hour GDPR grace period.
 *
 * The endpoint URL is configured from BuildConfig.ACCOUNT_DELETION_URL (read
 * from the CI secret of the same name) — wired through [com.dhanuk.debtbro.di.NetworkModule].
 *
 * After the grace window expires, [com.dhanuk.debtbro.data.firebase.AuthManager.deleteAccount]
 * still performs the actual Firebase Auth + Firestore wipe.
 */
data class AccountDeletionRequest(val uid: String)
data class AccountDeletionResponse(val success: Boolean, val message: String? = null)

interface AccountDeletionApiService {
    @POST
    suspend fun requestDeletion(
        @Url url: String,
        @Body request: AccountDeletionRequest
    ): AccountDeletionResponse
}
