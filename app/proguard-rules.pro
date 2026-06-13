# Keep encrypted shared preferences
-keep class androidx.security.crypto.** { *; }

# Keep app classes (no obfuscation for reflection-sensitive code)
-keep class com.guardian.app.** { *; }

# Keep Kotlin coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
