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
     * Re-authenticate with Google (used before account deletion or sensitive operations).
     */
    suspend fun reauthenticateWithGoogle(activity: Activity): Result<Unit> = runCatching {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(true)
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
        val credential = EmailAuthProvider.getCredential(email, password) as AuthCredential
        auth.currentUser?.reauthenticate(credential)?.await()
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
        user.getIdToken(true).await()
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
