# Kavach ProGuard Rules

# Keep AdMob classes
-keep class com.google.android.gms.ads.** { *; }
-keep class com.google.ads.** { *; }


# Keep data models
-keep class com.kavach.app.data.** { *; }

# Compose
-dontwarn androidx.compose.**
