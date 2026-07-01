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
