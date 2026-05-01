# Add project specific ProGuard rules here.

# Retrofit
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keep class sun.misc.Unsafe { *; }
-keep class com.google.gson.stream.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase

# Firebase
-keep class com.google.firebase.** { *; }

# AdMob
-keep class com.google.android.gms.ads.** { *; }

# Domain models
-keep class com.debtbro.app.domain.model.** { *; }
-keep class com.debtbro.app.data.db.entity.** { *; }
-keep class com.debtbro.app.data.network.model.** { *; }
