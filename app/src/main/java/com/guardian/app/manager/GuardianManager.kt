package com.guardian.app.manager

import android.content.Context
import com.guardian.app.data.SecurityManager
import com.guardian.app.data.db.DatabaseHelper

class GuardianManager(private val context: Context) {

    private val securityManager = SecurityManager(context)
    val dbHelper = DatabaseHelper.getInstance(context)

    fun isSetupComplete(): Boolean = securityManager.isSetupComplete()

    fun isWall1Enabled(): Boolean = securityManager.isWall1Enabled()

    fun isWall2Enabled(): Boolean = securityManager.isWall2Enabled()

    fun getDaysCleanStreak(): Int = dbHelper.getDaysCleanStreak()
}

