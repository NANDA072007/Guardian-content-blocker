package com.guardian.app.di

import android.os.Build
import com.guardian.app.core.oem.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideOemProfile(): OemProfile {
        val manufacturer = Build.MANUFACTURER.lowercase()
        return when {
            manufacturer.contains("samsung") -> SamsungProfile()
            manufacturer.contains("xiaomi") || manufacturer.contains("redmi") || manufacturer.contains("poco") -> XiaomiProfile()
            manufacturer.contains("huawei") || manufacturer.contains("honor") -> HuaweiProfile()
            manufacturer.contains("oneplus") -> OnePlusProfile()
            manufacturer.contains("realme") || manufacturer.contains("oppo") -> RealmeOppoProfile()
            manufacturer.contains("vivo") -> VivoProfile()
            else -> DefaultProfile()
        }
    }

    @Provides
    @Singleton
    fun provideTimeProvider(): com.guardian.app.util.TimeProvider {
        return com.guardian.app.util.RealTimeProvider()
    }

    @Provides
    @Singleton
    fun provideVpnController(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): com.guardian.app.walls.wall1.VpnController {
        return com.guardian.app.walls.wall1.VpnControllerImpl(context)
    }

    @Provides
    @Singleton
    fun provideCoroutineDispatcher(): kotlinx.coroutines.CoroutineDispatcher {
        return kotlinx.coroutines.Dispatchers.Default
    }
}




