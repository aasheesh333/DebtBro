package com.dhanuk.debtbro.data.firebase

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.dhanuk.debtbro.R
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
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
