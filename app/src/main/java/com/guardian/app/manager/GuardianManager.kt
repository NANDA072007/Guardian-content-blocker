package com.guardian.app.manager

import com.guardian.app.core.SecurityManager
import com.guardian.app.data.db.DatabaseHelper
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GuardianManager @Inject constructor(
    private val securityManager: SecurityManager,
    private val dbHelper: DatabaseHelper
) {

    fun isSetupComplete(): Boolean = securityManager.isSetupComplete()

    fun isWall1Enabled(): Boolean = securityManager.isWall1Enabled()

    fun isWall2Enabled(): Boolean = securityManager.isWall2Enabled()

    fun getDaysCleanStreak(): Int = dbHelper.getDaysCleanStreak()
}

