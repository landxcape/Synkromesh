# Synkromesh ProGuard / R8 Optimization & Obfuscation Rules

-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod

# Native JNI Audio Core: Preserve NativeOpus class and native method bindings
-keep class com.landxcape.synkromesh.data.datasource.audio.nativebridge.NativeOpus {
    native <methods>;
    *;
}
-keepclasseswithmembernames class * {
    native <methods>;
}

# Preserve Domain Models & Telemetry
-keep class com.landxcape.synkromesh.domain.model.** { *; }

# Google Nearby Connections API
-keep class com.google.android.gms.nearby.** { *; }
-dontwarn com.google.android.gms.nearby.**

# AndroidX Compose & Lifecycle
-keep class androidx.compose.** { *; }

# Hilt Dependency Injection
-keep class * extends dagger.hilt.internal.GeneratedComponent { *; }
-keep class dagger.hilt.** { *; }

# OpenStreetMap (osmdroid)
-keep class org.osmdroid.** { *; }
-dontwarn org.osmdroid.**

# Google ML Kit Code Scanner & ZXing
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class com.google.zxing.** { *; }

# AndroidX DataStore
-keep class androidx.datastore.** { *; }

# Release Logging Stripping (strip verbose, debug, info, and warning logs in release builds)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
}
