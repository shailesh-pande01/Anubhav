# ==============================================================================
# Anubhav — Production ProGuard & R8 Configuration
# ==============================================================================

# 1. Debugging & Stack Trace Deobfuscation
# Retain file names and line numbers for crash reporting and deobfuscation
-keepattributes SourceFile,LineNumberTable
-keepattributes *Annotation*,Signature,InnerClasses,EnclosingMethod

# 2. Kotlinx Serialization
# Retain runtime serializer lookup, companion object serializers, and generated methods
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * extends kotlinx.serialization.KSerializer {
    *** Companion;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <init>(...);
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
}

# 3. Anubhav Application Data Transfer Objects & Domain Models
# Prevent R8 from obfuscating JSON field names and reflection targets
-keep class com.example.anubhav.data.remote.dto.** { *; }
-keepclassmembers class com.example.anubhav.data.remote.dto.** { *; }
-keep class com.example.anubhav.domain.model.** { *; }
-keepclassmembers class com.example.anubhav.domain.model.** { *; }

# 4. Supabase Kotlin SDK
-keep class io.github.jan.supabase.** { *; }
-keepclassmembers class io.github.jan.supabase.** { *; }
-dontwarn io.github.jan.supabase.**

# 5. Ktor Client & OkHttp Engine
-keep class io.ktor.** { *; }
-keepclassmembers class io.ktor.** { *; }
-dontwarn io.ktor.**
-dontwarn okhttp3.**
-dontwarn okio.**

# 6. Coil Image Loading
-keep class coil3.** { *; }
-keep class coil.** { *; }
-dontwarn coil3.**
-dontwarn coil.**

# 7. AndroidX & Jetpack Compose
-keep class androidx.compose.** { *; }
-dontwarn androidx.compose.**