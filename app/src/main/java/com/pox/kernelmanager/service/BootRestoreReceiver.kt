package com.pox.kernelmanager.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.pox.kernelmanager.data.repo.TuningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Restores tuning the kernel only keeps in RAM.
 *
 * `pox-begonia-kernel`'s `init.battery_guard.rc` writes
 * `charge_limit 100` on every boot, so the Charging Stop Limit chosen in the
 * app is lost unless it is written back after boot. Root managers and the
 * kernel nodes are not always ready the instant the broadcast arrives, so this
 * retries briefly before giving up; the application class performs the same
 * repair on the next app launch as a fallback.
 */
class BootRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            ACTION_QUICKBOOT_POWERON,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Unit
            else -> return
        }

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                repeat(ATTEMPTS) {
                    val restored = runCatching {
                        TuningRepository.restoreChargeLimitFromPrefs(force = true)
                    }.getOrElse {
                        Log.w(TAG, "Charge-limit restore failed", it)
                        false
                    }
                    if (restored) return@launch
                    delay(RETRY_DELAY_MS)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PoxBootRestore"

        /** Some MIUI/Qualcomm builds send this instead of BOOT_COMPLETED. */
        const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"

        /** Keep the total under the ~10 s `goAsync()` budget for manifest receivers. */
        const val ATTEMPTS = 6
        const val RETRY_DELAY_MS = 1_000L
    }
}
