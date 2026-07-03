package com.dhanuk.debtbro.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.dhanuk.debtbro.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.EmailAuthProvider
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthRecentLoginRequiredException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val firebaseRepository: FirebaseRepository,
    @ApplicationContext private val context: Context
) {
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val result = credentialManager.getCredential(
            context = activity,
            request = request
        )

        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            val authResult = auth.signInWithCredential(firebaseCredential).await()
            val user = authResult.user ?: error("Firebase login failed")
            if (user.uid.isBlank()) error("Firebase user missing UID")
            user
        } else {
            error("Invalid credential type")
        }
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "signInWithGoogle failed: ${e.message}", e)
    }

    /**
     * Link an email/password account to the current Firebase user.
     * Returns Result.failure with [FirebaseAuthRecentLoginRequiredException] if
     * the user hasn't authenticated recently — callers must re-authenticate first.
     */
    suspend fun linkWithEmailPassword(email: String, password: String): Result<FirebaseUser> = runCatching {
        val credential = EmailAuthProvider.getCredential(email, password)
        val currentUser = auth.currentUser ?: error("Not signed in")
        currentUser.linkWithCredential(credential).await().user ?: error("Link returned no user")
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "linkWithEmailPassword failed: ${e.message}", e)
    }

    /**
     * Sign in with email + password (for users without Google).
     */
    suspend fun signInWithEmailPassword(email: String, password: String): Result<FirebaseUser> = runCatching {
        auth.signInWithEmailAndPassword(email, password).await().user ?: error("Sign-in returned no user")
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "signInWithEmailPassword failed: ${e.message}", e)
    }

    /**
     * Create a new account using email + password.
     * Caller should usually link this to an existing Google account via [linkWithEmailPassword]
     * or use this for email-only sign up.
     */
    suspend fun signUpWithEmailPassword(email: String, password: String): Result<FirebaseUser> = runCatching {
        val result = auth.createUserWithEmailAndPassword(email, password).await()
        result.user ?: error("Sign-up returned no user")
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "signUpWithEmailPassword failed: ${e.message}", e)
    }

    /**
     * Send a password-reset email to [email]. Returns success even if the address is
     * not registered (Firebase privacy rule) so callers always get a positive UI signal.
     */
    suspend fun sendPasswordResetEmail(email: String): Result<Unit> = runCatching {
        auth.sendPasswordResetEmail(email).await()
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "sendPasswordResetEmail failed: ${e.message}", e)
    }

    /**
     * Re-authenticate with Google (used before account deletion or sensitive operations).
     */
    suspend fun reauthenticateWithGoogle(activity: Activity): Result<Unit> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(context.getString(R.string.default_web_client_id))
            .build()
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()
        val result = credentialManager.getCredential(activity, request)
        val credential = result.credential
        if (credential is GoogleIdTokenCredential) {
            val firebaseCredential = GoogleAuthProvider.getCredential(credential.idToken, null)
            auth.currentUser?.reauthenticate(firebaseCredential)?.await()
            Unit
        } else error("Invalid credential type")
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "reauthenticateWithGoogle failed: ${e.message}", e)
    }

    /**
     * Re-authenticate with email/password credential.
     */
    suspend fun reauthenticateWithEmailPassword(email: String, password: String): Result<Unit> = runCatching {
        val currentUser = auth.currentUser ?: error("Cannot reauthenticate: no current user")
        val credential = EmailAuthProvider.getCredential(email, password) as AuthCredential
        currentUser.reauthenticate(credential).await()
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "reauthenticateWithEmailPassword failed: ${e.message}", e)
    }

    /**
     * Delete the current Firebase account.
     * Caller is responsible for wiping associated Firestore/Room data first.
     * Throws [FirebaseAuthRecentLoginRequiredException] if re-authentication is needed;
     * callers should invoke [reauthenticateWithGoogle] / [reauthenticateWithEmailPassword] first.
     */
    suspend fun deleteAccount(): Result<Unit> = runCatching {
        val currentUser = auth.currentUser ?: error("Not signed in")
        currentUser.delete().await()
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "deleteAccount failed: ${e.message}", e)
    }

    /**
     * Spark-plan account-deletion request: writes a `PENDING` doc to
     * Firestore at `deletionRequests/{uid}` with the `requestedAt` epoch-ms.
     * No Cloud Functions / Blaze plan needed — the Android client itself is
     * the deletion orchestrator via the Firestore SDK.
     *
     * Combined with [checkDeletionRequest] called on every sign-in, this
     * gives us a server-of-record so a deletion scheduled on one device
     * (or before an uninstall) is correctly honored if the user signs in
     * from another device or reinstalls. The existing [AccountDeletionWorker]
     * (WorkManager one-shot, ~30 min delay) is the failsafe for the
     * "user never signs in again" case.
     */
    suspend fun requestAccountDeletion(uid: String, requestedAt: Long): Result<Unit> = runCatching {
        firebaseRepository.recordDeletionRequest(uid, requestedAt)
        Unit
    }.onFailure { e ->
        android.util.Log.w("AuthManager", "requestAccountDeletion Firestore write failed: ${e.message}", e)
    }

    /**
     * Read the server-side deletion-request status for [uid]. Returns the
     * `requestedAt` epoch-ms if a PENDING request exists and is still in
     * force; null otherwise (no doc, doc missing status, status=CANCELLED,
     * or status=COMPLETED).
     *
     * Called by `SettingsViewModel.signInWithGoogle` / email-password
     * sign-in paths and by the `init` block to surface the "Cancel
     * deletion?" alert or to commit deletion if grace has elapsed.
     */
    suspend fun checkDeletionRequest(uid: String): Long? = runCatching {
        firebaseRepository.fetchDeletionRequest(uid)
    }.onFailure { e ->
        android.util.Log.w("AuthManager", "checkDeletionRequest Firestore read failed: ${e.message}", e)
    }.getOrNull()

    /**
     * Cancel a previously-recorded deletion request: marks the Firestore doc
     * `status=CANCELLED` (with `cancelledAt` timestamp). Idempotent — safe to
     * call even if no pending request exists.
     */
    suspend fun cancelAccountDeletion(uid: String, cancelledAt: Long): Result<Unit> = runCatching {
        firebaseRepository.cancelDeletionRequest(uid, cancelledAt)
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "cancelAccountDeletion failed: ${e.message}", e)
    }

    /**
     * Hard-delete the deletion-request doc. Called by the deletion-commit
     * path once Firestore + Firebase Auth account have been wiped, so we
     * don't leave orphaned queue state behind.
     */
    suspend fun clearDeletionRequest(uid: String) {
        runCatching { firebaseRepository.deleteDeletionRequest(uid) }
            .onFailure { e ->
                android.util.Log.w("AuthManager", "clearDeletionRequest failed: ${e.message}", e)
            }
    }

    /**
     * Update the current user's display name (profile) and photo URL.
     */
    suspend fun updateProfile(displayName: String?, photoUri: String?): Result<Unit> = runCatching {
        val user = auth.currentUser ?: error("Not signed in")
        val updates = UserProfileChangeRequest.Builder()
            .apply {
                displayName?.takeIf { it.isNotBlank() }?.let { setDisplayName(it) }
                photoUri?.takeIf { it.isNotBlank() }?.let { setPhotoUri(android.net.Uri.parse(it)) }
            }
            .build()
        user.updateProfile(updates).await()
        Unit
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "updateProfile failed: ${e.message}", e)
    }

    /** Returns provider IDs currently linked to the signed-in user (Google, Email, etc). */
    fun linkedProviders(): List<String> = auth.currentUser?.providerData
        ?.mapNotNull { it.providerId }
        ?.filter { it != "firebase" }
        ?: emptyList()

    /**
     * Refresh the ID token if it's missing or close to expiry. Safe to call periodically
     * to avoid stale-token sign-out bugs.
     */
    suspend fun refreshTokenIfStale(forceRefreshSeconds: Long = 300L): Result<Unit> = runCatching {
        val user = auth.currentUser ?: return@runCatching
        val meta = user.metadata
        val lastSignIn = meta?.lastSignInTimestamp ?: 0L
        val ageMs = System.currentTimeMillis() - lastSignIn
        if (ageMs > forceRefreshSeconds * 1000L) {
            user.getIdToken(true).await()
        } else {
            user.getIdToken(false).await()
        }
    }.onFailure { e ->
        android.util.Log.e("AuthManager", "token refresh failed: ${e.message}", e)
    }

    suspend fun signOut() {
        auth.signOut()
        runCatching { credentialManager.clearCredentialState(ClearCredentialStateRequest()) }
            .onFailure { e -> android.util.Log.e("AuthManager", "clearCredentialState failed: ${e.message}", e) }
    }

    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isSignedIn(): Boolean = auth.currentUser != null
    fun getUserId(): String? = auth.currentUser?.uid

    /** Observe Firebase auth state changes as a Flow */
    fun authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            trySend(firebaseAuth.currentUser)
        }
        auth.addAuthStateListener(listener)
        // Emit current value immediately
        trySend(auth.currentUser)
        awaitClose { auth.removeAuthStateListener(listener) }
    }
}
