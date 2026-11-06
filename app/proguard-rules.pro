# VibeVault ProGuard rules.
# The app ships unminified by default; if you enable minify for release,
# keep the youtubedl-android / Jackson model classes (they are (de)serialised
# reflectively from yt-dlp's JSON output).
-keep class com.yausername.youtubedl_android.** { *; }
-keep class com.yausername.youtubedl_common.** { *; }
-keep class com.yausername.ffmpeg.** { *; }
-keep class com.fasterxml.jackson.** { *; }
-dontwarn com.fasterxml.jackson.**
