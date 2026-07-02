package com.guardian.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.guardian.app.core.AuthenticationManager
import com.guardian.app.data.repository.GuardianRepository
import com.guardian.app.domain.ManageProtectionStateUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: GuardianRepository,
    private val manageProtection: ManageProtectionStateUseCase,
    private val auth: AuthenticationManager
) : ViewModel() {

    private val _currentStreak = MutableStateFlow(0)
    val currentStreak: StateFlow<Int> = _currentStreak.asStateFlow()

    fun refreshData() {
        viewModelScope.launch {
            _currentStreak.value = repository.getDaysCleanStreak()
        }
    }

    fun isWall1Enabled(): Boolean = manageProtection.isWall1Enabled()
    fun isWall2Enabled(): Boolean = manageProtection.isWall2Enabled()
    fun isOemOptimizationAcknowledged(): Boolean = manageProtection.isOemOptimizationAcknowledged()

    fun setWall1Enabled(enabled: Boolean) = manageProtection.setWall1Enabled(enabled)
    fun setWall2Enabled(enabled: Boolean) = manageProtection.setWall2Enabled(enabled)
    fun setOemOptimizationAcknowledged(acknowledged: Boolean) = manageProtection.setOemOptimizationAcknowledged(acknowledged)

    fun getUninstallUnlockTime(): Long = auth.getUninstallUnlockTime()
    fun setUninstallUnlockTime(time: Long) = auth.setUninstallUnlockTime(time)

    fun checkIsUnlocked(): Boolean {
        val uTime = getUninstallUnlockTime()
        return (uTime > 0L) && (System.currentTimeMillis() > uTime)
    }

    fun relockApp() {
        setUninstallUnlockTime(0L)
    }

    fun startVpn() {
        manageProtection.startVpn()
        relockApp()
    }

    fun stopVpn() {
        manageProtection.stopVpn()
    }
}
