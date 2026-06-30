package com.dhanuk.debtbro.data.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Singleton

val Context.dataStore by preferencesDataStore(name = "debtbro_prefs")

@Singleton
class AppPreferences(@ApplicationContext private val context: Context) {
    private object Keys {
        val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        val USER_NAME = stringPreferencesKey("user_name")
        val GROQ_API_KEY = stringPreferencesKey("groq_api_key")
        val ROAST_LEVEL = stringPreferencesKey("roast_level")
        val DEFAULT_CURRENCY = stringPreferencesKey("default_currency")
        val IS_GOOGLE_SIGNED_IN = booleanPreferencesKey("is_google_signed_in")
        val GOOGLE_USER_NAME = stringPreferencesKey("google_user_name")
        val GOOGLE_USER_EMAIL = stringPreferencesKey("google_user_email")
        val GOOGLE_USER_PHOTO = stringPreferencesKey("google_user_photo")
        val HAS_SHOWN_SIGNIN_PROMPT = booleanPreferencesKey("has_shown_signin_prompt")
        val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
        val REWARD_TIMESTAMP = longPreferencesKey("reward_timestamp")
        val LAST_INTERSTITIAL_AT = longPreferencesKey("last_interstitial_at")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SELECTED_LANGUAGE = stringPreferencesKey("selected_language")
        val AI_REGENERATION_COUNT = intPreferencesKey("ai_regeneration_count")
        val AI_REGENERATION_DATE = stringPreferencesKey("ai_regeneration_date")
        val SHOW_DESCRIPTION = booleanPreferencesKey("show_description")
        val SHOW_DUE_DATE = booleanPreferencesKey("show_due_date")
        val SHOW_EMOJI = booleanPreferencesKey("show_emoji")

        // ── Notification preferences ──────────────────────────────────────────
        val NOTIFY_DAILY_REMINDER = booleanPreferencesKey("notify_daily_reminder")
        val NOTIFY_WEEKLY_SUMMARY = booleanPreferencesKey("notify_weekly_summary")
        val NOTIFY_PAYMENT_ALERTS = booleanPreferencesKey("notify_payment_alerts")

        // ── Export preferences ────────────────────────────────────────────────
        val EXPORT_FORMAT = stringPreferencesKey("export_format")
        val CUSTOM_AVATAR_URI = stringPreferencesKey("custom_avatar_uri")
        val PENDING_DELETION_TIMESTAMP = longPreferencesKey("pending_deletion_timestamp")
        val FORGOT_PASSWORD_LAST_SENT = longPreferencesKey("forgot_password_last_sent")
        val FORGOT_PASSWORD_DAILY_COUNT = intPreferencesKey("forgot_password_daily_count")
        val FORGOT_PASSWORD_DAILY_DATE = stringPreferencesKey("forgot_password_daily_date")
    }


    val hasCompletedOnboarding: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAS_COMPLETED_ONBOARDING] ?: false }
    val userName: Flow<String> = context.dataStore.data.map { it[Keys.USER_NAME] ?: "" }
    val groqApiKey: Flow<String> = context.dataStore.data.map { it[Keys.GROQ_API_KEY] ?: "" }
    val roastLevel: Flow<String> = context.dataStore.data.map { it[Keys.ROAST_LEVEL]?.let { v -> if (v == "SAVAGE") "SPICY" else v } ?: "MEDIUM" }
    val defaultCurrency: Flow<String> = context.dataStore.data.map { it[Keys.DEFAULT_CURRENCY] ?: "₹" }
    val isGoogleSignedIn: Flow<Boolean> = context.dataStore.data.map { it[Keys.IS_GOOGLE_SIGNED_IN] ?: false }
    val googleUserName: Flow<String> = context.dataStore.data.map { it[Keys.GOOGLE_USER_NAME] ?: "" }
    val googleUserEmail: Flow<String> = context.dataStore.data.map { it[Keys.GOOGLE_USER_EMAIL] ?: "" }
    val googleUserPhoto: Flow<String> = context.dataStore.data.map { it[Keys.GOOGLE_USER_PHOTO] ?: "" }
    val hasShownSignInPrompt: Flow<Boolean> = context.dataStore.data.map { it[Keys.HAS_SHOWN_SIGNIN_PROMPT] ?: false }
    val lastSyncedAt: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_SYNCED_AT] ?: 0L }
    val rewardTimestamp: Flow<Long> = context.dataStore.data.map { it[Keys.REWARD_TIMESTAMP] ?: 0L }
    val lastInterstitialAt: Flow<Long> = context.dataStore.data.map { it[Keys.LAST_INTERSTITIAL_AT] ?: 0L }
    val themeMode: Flow<String> = context.dataStore.data.map { it[Keys.THEME_MODE] ?: "SYSTEM" }
    val selectedLanguage: Flow<String> = context.dataStore.data.map { it[Keys.SELECTED_LANGUAGE] ?: "en" }
    val showDescription: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DESCRIPTION] ?: true }
    val showDueDate: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_DUE_DATE] ?: true }
    val showEmoji: Flow<Boolean> = context.dataStore.data.map { it[Keys.SHOW_EMOJI] ?: true }
    val notifyDailyReminder: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_DAILY_REMINDER] ?: true }
    val notifyWeeklySummary: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_WEEKLY_SUMMARY] ?: true }
    val notifyPaymentAlerts: Flow<Boolean> = context.dataStore.data.map { it[Keys.NOTIFY_PAYMENT_ALERTS] ?: true }
    val exportFormat: Flow<String> = context.dataStore.data.map { it[Keys.EXPORT_FORMAT] ?: "CSV" }
    val customAvatarUri: Flow<String> = context.dataStore.data.map { it[Keys.CUSTOM_AVATAR_URI] ?: "" }
    val pendingDeletionTimestamp: Flow<Long> = context.dataStore.data.map { it[Keys.PENDING_DELETION_TIMESTAMP] ?: 0L }
    val forgotPasswordLastSent: Flow<Long> = context.dataStore.data.map { it[Keys.FORGOT_PASSWORD_LAST_SENT] ?: 0L }
    val forgotPasswordDailyCount: Flow<Int> = context.dataStore.data.map { it[Keys.FORGOT_PASSWORD_DAILY_COUNT] ?: 0 }

    suspend fun setOnboardingComplete(name: String) = context.dataStore.edit {
        it[Keys.HAS_COMPLETED_ONBOARDING] = true
        it[Keys.USER_NAME] = name.ifBlank { "Bro" }
    }
    suspend fun saveUserName(name: String) = context.dataStore.edit { it[Keys.USER_NAME] = name.ifBlank { "Bro" } }
    suspend fun saveGroqKey(key: String) = context.dataStore.edit { it[Keys.GROQ_API_KEY] = key.trim() }
    suspend fun setRoastLevel(level: String) = context.dataStore.edit { it[Keys.ROAST_LEVEL] = level }
    suspend fun setCurrency(c: String) = context.dataStore.edit { it[Keys.DEFAULT_CURRENCY] = c }
    suspend fun setGoogleSignedIn(value: Boolean, name: String = "", email: String = "", photo: String = "") = context.dataStore.edit {
        it[Keys.IS_GOOGLE_SIGNED_IN] = value
        it[Keys.GOOGLE_USER_NAME] = name
        it[Keys.GOOGLE_USER_EMAIL] = email
        it[Keys.GOOGLE_USER_PHOTO] = photo
    }
    suspend fun setLastSyncedAt(ts: Long) = context.dataStore.edit { it[Keys.LAST_SYNCED_AT] = ts }
    suspend fun setRewardTimestamp(ts: Long) = context.dataStore.edit { it[Keys.REWARD_TIMESTAMP] = ts }
    suspend fun setLastInterstitialAt(ts: Long) = context.dataStore.edit { it[Keys.LAST_INTERSTITIAL_AT] = ts }
    suspend fun setHasShownSignInPrompt(value: Boolean) = context.dataStore.edit { it[Keys.HAS_SHOWN_SIGNIN_PROMPT] = value }
    suspend fun setThemeMode(mode: String) = context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    suspend fun setLanguage(code: String) = context.dataStore.edit { it[Keys.SELECTED_LANGUAGE] = code }
    suspend fun setShowDescription(value: Boolean) = context.dataStore.edit { it[Keys.SHOW_DESCRIPTION] = value }
    suspend fun setShowDueDate(value: Boolean) = context.dataStore.edit { it[Keys.SHOW_DUE_DATE] = value }
    suspend fun setShowEmoji(value: Boolean) = context.dataStore.edit { it[Keys.SHOW_EMOJI] = value }
    suspend fun setNotifyDailyReminder(value: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_DAILY_REMINDER] = value }
    suspend fun setNotifyWeeklySummary(value: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_WEEKLY_SUMMARY] = value }
    suspend fun setNotifyPaymentAlerts(value: Boolean) = context.dataStore.edit { it[Keys.NOTIFY_PAYMENT_ALERTS] = value }
    suspend fun setExportFormat(format: String) = context.dataStore.edit { it[Keys.EXPORT_FORMAT] = format }
    suspend fun setCustomAvatarUri(uri: String) = context.dataStore.edit { it[Keys.CUSTOM_AVATAR_URI] = uri }
    suspend fun setPendingDeletionTimestamp(ts: Long) = context.dataStore.edit { it[Keys.PENDING_DELETION_TIMESTAMP] = ts }
    suspend fun clearPendingDeletion() = context.dataStore.edit { it.remove(Keys.PENDING_DELETION_TIMESTAMP) }
    suspend fun setForgotPasswordLastSent(ts: Long) = context.dataStore.edit { it[Keys.FORGOT_PASSWORD_LAST_SENT] = ts }
    suspend fun setForgotPasswordDailyCount(count: Int) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        context.dataStore.edit {
            it[Keys.FORGOT_PASSWORD_DAILY_COUNT] = count
            it[Keys.FORGOT_PASSWORD_DAILY_DATE] = today
        }
    }
    suspend fun getForgotPasswordDailyCount(): Int {
        val prefs = context.dataStore.data.first()
        val count = prefs[Keys.FORGOT_PASSWORD_DAILY_COUNT] ?: 0
        val date = prefs[Keys.FORGOT_PASSWORD_DAILY_DATE] ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return if (date != today) 0 else count
    }

    suspend fun saveAiRegenerationCount(count: Int) {
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        context.dataStore.edit {
            it[Keys.AI_REGENERATION_COUNT] = count
            it[Keys.AI_REGENERATION_DATE] = today
        }
    }

    suspend fun getAiRegenerationCount(): Int {
        val prefs = context.dataStore.data.first()
        val count = prefs[Keys.AI_REGENERATION_COUNT] ?: 0
        val date = prefs[Keys.AI_REGENERATION_DATE] ?: ""
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        return if (date != today) 0 else count
    }

    suspend fun clearAll() = context.dataStore.edit { it.clear() }
}
