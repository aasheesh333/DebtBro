package com.dhanuk.debtbro.data.firebase

import android.app.Activity
import androidx.credentials.CredentialManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthManager @Inject constructor(private val auth: FirebaseAuth, private val credentialManager: CredentialManager) {
    suspend fun signInWithGoogle(activity: Activity): Result<FirebaseUser> = runCatching {
        val current = auth.currentUser
        if (current != null) return@runCatching current
        auth.signInAnonymously().await().user ?: error("Unable to sign in")
    }
    suspend fun signOut() {
        auth.signOut()
        credentialManager.clearCredentialState(androidx.credentials.ClearCredentialStateRequest())
    }
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    fun isSignedIn(): Boolean = auth.currentUser != null
}
