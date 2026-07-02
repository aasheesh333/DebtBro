package com.dhanuk.debtbro.data.datastore

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecureStorage @Inject constructor(@ApplicationContext private val context: Context) {

    private object Keys {
        const val GEMINI_API_KEY = "gemini_api_key"
        const val GOOGLE_USER_NAME = "google_user_name"
        const val GOOGLE_USER_EMAIL = "google_user_email"
        const val GOOGLE_USER_PHOTO = "google_user_photo"
    }

    private val prefs: SharedPreferences

    init {
        prefs = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                "debtbro_secure_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w("SecureStorage", "EncryptedSharedPreferences unavailable, falling back to plain SharedPreferences", e)
            context.getSharedPreferences("debtbro_secure_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    private val _geminiApiKey = MutableStateFlow(prefs.getString(Keys.GEMINI_API_KEY, "") ?: "")
    private val _googleUserName = MutableStateFlow(prefs.getString(Keys.GOOGLE_USER_NAME, "") ?: "")
    private val _googleUserEmail = MutableStateFlow(prefs.getString(Keys.GOOGLE_USER_EMAIL, "") ?: "")
    private val _googleUserPhoto = MutableStateFlow(prefs.getString(Keys.GOOGLE_USER_PHOTO, "") ?: "")

    val geminiApiKey: Flow<String> = _geminiApiKey
    val googleUserName: Flow<String> = _googleUserName
    val googleUserEmail: Flow<String> = _googleUserEmail
    val googleUserPhoto: Flow<String> = _googleUserPhoto

    suspend fun saveGeminiApiKey(key: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.GEMINI_API_KEY, key.trim()).apply()
        _geminiApiKey.value = key.trim()
    }

    suspend fun saveGoogleUserName(name: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.GOOGLE_USER_NAME, name).apply()
        _googleUserName.value = name
    }

    suspend fun saveGoogleUserEmail(email: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.GOOGLE_USER_EMAIL, email).apply()
        _googleUserEmail.value = email
    }

    suspend fun saveGoogleUserPhoto(photo: String) = withContext(Dispatchers.IO) {
        prefs.edit().putString(Keys.GOOGLE_USER_PHOTO, photo).apply()
        _googleUserPhoto.value = photo
    }

    suspend fun clearSensitiveData() = withContext(Dispatchers.IO) {
        prefs.edit()
            .remove(Keys.GEMINI_API_KEY)
            .remove(Keys.GOOGLE_USER_NAME)
            .remove(Keys.GOOGLE_USER_EMAIL)
            .remove(Keys.GOOGLE_USER_PHOTO)
            .apply()
        _geminiApiKey.value = ""
        _googleUserName.value = ""
        _googleUserEmail.value = ""
        _googleUserPhoto.value = ""
    }
}
