package com.dhanuk.debtbro.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.dhanuk.debtbro.R
import com.dhanuk.debtbro.data.network.AccountDeletionApiService
import com.dhanuk.debtbro.data.network.AccountDeletionRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
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
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(
    private val auth: FirebaseAuth,
    private val credentialManager: CredentialManager,
    private val accountDeletionApi: AccountDeletionApiService,
    @Named("accountDeletionUrl") private val accountDeletionUrl: String,
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
     * Best-effort POST to the configured Cloud Function (BuildConfig.ACCOUNT_DELETION_URL)
     * to record an account-deletion request server-side and kick off the 24-hour GDPR
     * grace window. Returns silently without throwing if the URL is missing, malformed,
     * points outside the trusted Google Cloud host set, or HTTP fails — local-only
     * deletion bookkeeping continues regardless.
     *
     * SSRF guard: parse the URL with OkHttp's HttpUrl (no scheme/host can sneak
     * past) and refuse any host outside the `*.cloudfunctions.net` / `*.googleapis.com`
     * family. This protects against a tampered local.properties or compromised CI
     * secret pointing at an attacker-controlled HTTPS endpoint that would otherwise
     * receive the user's Firebase UID.
     *
     * The URL comes from the GH secret `ACCOUNT_DELETION_URL` (wired via build.yml →
     * buildConfigField → NetworkModule's @Named("accountDeletionUrl") provider).
     */
    suspend fun requestAccountDeletion(uid: String): Result<Unit> = runCatching {
        if (accountDeletionUrl.isBlank()) return@runCatching
        val parsed = accountDeletionUrl.toHttpUrlOrNull() ?: run {
            android.util.Log.w("AuthManager", "requestAccountDeletion: URL is malformed, skipping — $accountDeletionUrl")
            return@runCatching
        }
        if (parsed.scheme != "https") {
            android.util.Log.w("AuthManager", "requestAccountDeletion: non-HTTPS scheme '${parsed.scheme}', skipping")
            return@runCatching
        }
        val host = parsed.host.lowercase()
        val isTrustedHost = host == "cloudfunctions.net" ||
            host.endsWith(".cloudfunctions.net") ||
            host.endsWith(".googleapis.com")
        if (!isTrustedHost) {
            android.util.Log.w("AuthManager", "requestAccountDeletion: untrusted host '$host', refusing to POST. " +
                "ACCOUNT_DELETION_URL must point to a *.cloudfunctions.net or *.googleapis.com endpoint.")
            return@runCatching
        }
        accountDeletionApi.requestDeletion(
            url = accountDeletionUrl,
            request = AccountDeletionRequest(uid)
        )
        Unit
    }.onFailure { e ->
        android.util.Log.w(
            "AuthManager",
            "requestAccountDeletion HTTP failed (continuing with local-only deletion) — ${e.message}",
            e
        )
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
