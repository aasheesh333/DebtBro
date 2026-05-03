-keep class com.dhanuk.debtbro.** { *; }
-keep class retrofit2.** { *; }
-keep class com.squareup.okhttp3.** { *; }
-keep class com.google.gson.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class *
-keep class com.google.android.gms.** { *; }
-keep class com.google.firebase.** { *; }
-keep class com.onesignal.** { *; }
-keep @androidx.annotation.Keep class *
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
