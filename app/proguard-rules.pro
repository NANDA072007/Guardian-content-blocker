# Keep encrypted shared preferences
-keep class androidx.security.crypto.** { *; }

# Keep Hilt entry points
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.internal.managers.ViewComponentManager$FragmentContextWrapper { *; }

# Keep Room entities and DAOs
-keep class com.guardian.app.data.db.entities.** { *; }
-keep class com.guardian.app.data.db.dao.** { *; }

# Keep Hilt @AndroidEntryPoint components
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }

# Keep Hilt @HiltViewModel classes
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep EntryPoint interfaces (used by EntryPointAccessors.fromApplication)
-keep @dagger.hilt.EntryPoint class * { *; }
-keep @dagger.hilt.InstallIn interface * { *; }

# Keep Room database
-keep class com.guardian.app.data.db.GuardianDatabase { *; }

# Keep BroadcastReceivers (registered in manifest)
-keep class com.guardian.app.core.GuardianAlarmReceiver { *; }
-keep class com.guardian.app.broadcast.AdbReceiver { *; }
-keep class com.guardian.app.broadcast.GuardianDeviceAdminReceiver { *; }

# Keep Services (registered in manifest)
-keep class com.guardian.app.core.GuardianCoreService { *; }
-keep class com.guardian.app.core.GuardianJobService { *; }
-keep class com.guardian.app.walls.wall1.DnsVpnService { *; }
-keep class com.guardian.app.walls.wall2.AccessibilitySentry { *; }

# Keep WorkManager workers
-keep class com.guardian.app.workers.** { *; }

# Keep kotlin.Metadata for reflection
-keepattributes *Annotation*
-keepattributes RuntimeVisibleAnnotations

# Keep Kotlin coroutines internals
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# Keep Compose stability
-dontwarn androidx.compose.**
