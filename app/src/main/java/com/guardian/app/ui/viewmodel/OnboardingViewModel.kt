package com.guardian.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import com.guardian.app.core.AuthenticationManager
import com.guardian.app.domain.ManageProtectionStateUseCase
import com.guardian.app.util.CryptoUtils

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val manageProtection: ManageProtectionStateUseCase,
    private val auth: AuthenticationManager
) : ViewModel() {

    private var cachedCode: String? = null

    fun isWall1Enabled(): Boolean = manageProtection.isWall1Enabled()

    fun startVpn() = manageProtection.startVpn()

    fun generateGuardianCode(): String {
        val code = CryptoUtils.generateGuardianCode()
        auth.setMasterKeyHash(CryptoUtils.sha256(code.toByteArray()))
        cachedCode = code
        return code
    }

    fun completeSetup() {
        manageProtection.setSetupComplete(true)
    }
}
