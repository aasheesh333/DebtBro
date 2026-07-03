-keep @androidx.room.Entity class com.dhanuk.debtbro.data.db.entity.** { *; }
-keep class com.dhanuk.debtbro.data.db.entity.** { *; }
-keep class com.dhanuk.debtbro.data.firebase.dto.** { *; }
-keep class retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
-keep class com.google.firebase.** { *; }
-dontwarn com.google.android.gms.**

# OneSignal — its SDK relies on reflection (annotation processors, callback
# discovery, notification lifecycle). Without these, R8 strips callbacks in
# release builds and push silently fails. Verified via the OneSignal Gradle
# plugin recommended rules. See DebtBroApp.kt for the init site.
-keep class com.onesignal.** { *; }
-dontwarn com.onesignal.**
-keep class onesignal.** { *; }
-dontwarn onesignal.**

# Google Identity Services + AndroidX Credentials (used by AuthManager for
# the Credential Manager / GetGoogleIdOption flow). The GIS library parses
# GoogleIdTokenCredential through reflection — if R8 renames it, sign-in
# crashes at runtime with a ClassCastException / "Cannot access class" — this
# is the failure mode described by the
# `fix-google-signin-and-export-crash-...` branch.
-keep class com.google.android.libraries.identity.** { *; }
-dontwarn com.google.android.libraries.identity.**
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keepclassmembers class com.google.android.libraries.identity.googleid.** { *; }

# Retrofit/Gson TypeToken & generic signatures are picked up via the
# Signature attribute above; explicit keep for the Gemini request/response
# data classes so the Gson converter doesn't lose field names in release.
-keep class com.dhanuk.debtbro.data.network.Gemini** { *; }
-keep class com.dhanuk.debtbro.data.network.GenerationConfig { *; }

# OkHttp's platform-specific classes (Android 10+ platform proxy class).
-dontwarn okhttp3.internal.platform.Android10Platform
