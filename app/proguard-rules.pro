# Room generated code
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# OkHttp
-dontwarn okhttp3.**
-dontwarn okio.**

# ML Kit barcode models are loaded reflectively
-keep class com.google.mlkit.** { *; }
