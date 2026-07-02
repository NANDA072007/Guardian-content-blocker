package com.guardian.app.domain

import android.content.Context
import com.guardian.app.engine.BlocklistEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Singleton
class InitializeBlocklistUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val blocklistEngine = BlocklistEngine(context)

    fun execute() {
        CoroutineScope(Dispatchers.IO).launch {
            blocklistEngine.initializeBlocklistIfNeeded()
        }
    }
}
