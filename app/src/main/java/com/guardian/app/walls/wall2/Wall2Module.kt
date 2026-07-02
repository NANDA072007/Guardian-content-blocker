package com.guardian.app.walls.wall2

import com.guardian.app.walls.wall2.adapter.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class Wall2Module {

    @Binds
    @Singleton
    abstract fun bindMetricsRepository(impl: MetricsRepositoryImpl): MetricsRepository

    @Binds
    @Singleton
    abstract fun bindAuditTrail(impl: AuditTrailImpl): AuditTrail

    @Binds
    @Singleton
    abstract fun bindOverlayController(impl: OverlayControllerImpl): OverlayController
}
