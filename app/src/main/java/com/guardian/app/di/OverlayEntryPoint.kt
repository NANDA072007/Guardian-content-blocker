package com.guardian.app.di

import com.guardian.app.core.SecurityManager
import com.guardian.app.ui.overlay.BlockOverlayManager
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing Hilt singletons from BroadcastReceiver
 * which cannot use constructor injection.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface OverlayEntryPoint {
    fun blockOverlayManager(): BlockOverlayManager
    fun securityManager(): SecurityManager
}
