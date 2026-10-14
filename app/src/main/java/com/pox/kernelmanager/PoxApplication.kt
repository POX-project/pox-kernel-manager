package com.pox.kernelmanager

import android.app.Application
import com.pox.kernelmanager.data.root.ShellUtils
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

        // Touching ShellUtils installs the libsu builder; pre-warm the root shell so
        // the first dashboard read does not pay for the su prompt.
        CoroutineScope(Dispatchers.IO).launch {
            ShellUtils.prewarm()
        }
    }
}