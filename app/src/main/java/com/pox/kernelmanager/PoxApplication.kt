package com.pox.kernelmanager

import android.app.Application
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PoxApplication : Application() {

    /** App-scoped, supervised; cancelled in [onTerminate] so nothing outlives the process. */
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        lateinit var instance: PoxApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        com.pox.kernelmanager.data.learning.AdaptiveLearningManager.init(this)

        // Touching ShellUtils installs the libsu builder; pre-warm the root shell so
        // the first dashboard read does not pay for the su prompt.
        appScope.launch {
            ShellUtils.prewarm()
            // The kernel keeps the charge cap in RAM and resets it to 100 on
            // boot; repair it here when the boot receiver could not run.
            runCatching { TuningRepository.restoreChargeLimitFromPrefs() }
        }
    }

    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}