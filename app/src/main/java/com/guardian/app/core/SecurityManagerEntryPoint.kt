package com.guardian.app.core

import com.guardian.app.domain.InitiateCooloffUseCase
import com.guardian.app.domain.PauseProtectionUseCase
import com.guardian.app.walls.wall2.health.RestrictedSettingsHelper
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@EntryPoint
@InstallIn(SingletonComponent::class)
interface SecurityManagerEntryPoint {
    fun securityManager(): SecurityManager
    fun pauseProtectionUseCase(): PauseProtectionUseCase
    fun initiateCooloffUseCase(): InitiateCooloffUseCase
    fun protectionOrchestrator(): ProtectionOrchestrator
    fun restrictedSettingsHelper(): RestrictedSettingsHelper
}

@Composable
fun rememberSecurityManager(): SecurityManager {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SecurityManagerEntryPoint::class.java)
    return entryPoint.securityManager()
}

@Composable
fun rememberProtectionOrchestrator(): ProtectionOrchestrator {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SecurityManagerEntryPoint::class.java)
    return entryPoint.protectionOrchestrator()
}

@Composable
fun rememberRestrictedSettingsHelper(): RestrictedSettingsHelper {
    val context = LocalContext.current
    val entryPoint = EntryPointAccessors.fromApplication(context.applicationContext, SecurityManagerEntryPoint::class.java)
    return entryPoint.restrictedSettingsHelper()
}
