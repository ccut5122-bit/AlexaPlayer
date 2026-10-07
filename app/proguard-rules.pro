# Shrink hard: merge every remaining class into one root package, mangle member names and
# drop any access modifiers R8 no longer needs. Decompiling still yields bytecode, but names,
# structure and the original source layout no longer survive.
-repackageclasses ""
-allowaccessmodification
-overloadaggressively

# Keep line numbers for readable crash reports while still obfuscating names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Media3 / ExoPlayer
-dontwarn androidx.media3.**
-keep class androidx.media3.exoplayer.** { *; }
-keep class androidx.media3.session.** { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { <init>(); }
-dontwarn androidx.room.paging.**

# App models persisted / reflected by Room converters
-keep class com.alexaplayer.data.local.entity.** { *; }
-keepclassmembers class com.alexaplayer.data.local.entity.** { *; }

# Kotlin coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }

# Keep our service entry point (referenced from the manifest only)
-keep class com.alexaplayer.playback.PlaybackService { *; }
