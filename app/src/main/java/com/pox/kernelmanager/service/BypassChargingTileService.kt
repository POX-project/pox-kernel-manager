package com.pox.kernelmanager.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pox.kernelmanager.R
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.data.root.KernelNodes
import com.pox.kernelmanager.data.root.ShellUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BypassChargingTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
            val isBypass = isBypassActive()
            val next = !isBypass
            TuningRepository.setBypassCharging(next)
            launch(Dispatchers.Main) {
                updateTileState(next)
            }
        }
    }

    private suspend fun isBypassActive(): Boolean {
        val fromProc = ShellUtils.fastRead(KernelNodes.BATTERY_BYPASS_PROC)?.trim()
        val fromSys = ShellUtils.fastRead(KernelNodes.BATTERY_BYPASS_SYS)?.trim()
        return fromProc == "1" || fromSys == "1"
    }

    private fun updateTileState(forcedActive: Boolean? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val active = forcedActive ?: isBypassActive()
            launch(Dispatchers.Main) {
                qsTile?.let { tile ->
                    tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.icon = Icon.createWithResource(this@BypassChargingTileService, R.drawable.ic_qs_bypass)
                    tile.label = "Direct Bypass"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = if (active) "Active (Power Port Only)" else "Normal Charging"
                    }
                    tile.updateTile()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
