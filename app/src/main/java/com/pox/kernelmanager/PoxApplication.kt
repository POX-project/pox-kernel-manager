package com.pox.kernelmanager

import android.app.Application
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PoxApplication : Application() {

    companion object {
        lateinit var instance: PoxApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Configure libsu root shell builder
        try {
            Shell.enableVerboseLogging = BuildConfig.DEBUG
            Shell.setDefaultBuilder(
                Shell.Builder.create()
                    .setTimeout(10)
            )
        } catch (_: Throwable) {
            // Already initialized
        }

        // Check if su binary exists before attempting pre-warming
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (Shell.isAppGrantedRoot() == true) {
                    Shell.getShell()
                }
            } catch (_: Throwable) {
                // Device is unrooted or permission denied - safe fallback
            }
        }
    }
}
