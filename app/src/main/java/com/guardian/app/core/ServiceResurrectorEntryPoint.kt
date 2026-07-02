package com.guardian.app.core

import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * EntryPoint for accessing ServiceResurrector from components that cannot use
 * constructor injection (BroadcastReceiver, JobService).
 *
 * Usage:
 *   val entryPoint = EntryPointAccessors.fromApplication(context, ServiceResurrectorEntryPoint::class.java)
 *   val resurrector = entryPoint.serviceResurrector()
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface ServiceResurrectorEntryPoint {
    fun serviceResurrector(): ServiceResurrector
    fun securityManager(): SecurityManager
    fun orchestrator(): ProtectionOrchestrator
}

