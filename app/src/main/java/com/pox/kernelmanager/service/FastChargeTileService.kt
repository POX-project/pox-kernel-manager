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

class FastChargeTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
            val isEnabled = isFastChargeEnabled()
            val next = !isEnabled
            TuningRepository.setFastCharge(next)
            launch(Dispatchers.Main) {
                updateTileState(next)
            }
        }
    }

    private suspend fun isFastChargeEnabled(): Boolean {
        val raw = ShellUtils.fastRead(KernelNodes.FAST_CHARGE_PROC)?.trim()
        val num = raw?.toIntOrNull()
        return num != null && num > 0
    }

    private suspend fun readCurrentWatt(): Int {
        val raw = ShellUtils.fastRead(KernelNodes.CHARGE_WATT_PROC)?.trim()
        return raw?.toIntOrNull() ?: 18
    }

    private fun updateTileState(forcedActive: Boolean? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val active = forcedActive ?: isFastChargeEnabled()
            val watt = readCurrentWatt()
            launch(Dispatchers.Main) {
                qsTile?.let { tile ->
                    tile.state = if (active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.icon = Icon.createWithResource(this@FastChargeTileService, R.drawable.ic_qs_fastcharge)
                    tile.label = "Fast Charge"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = if (active) "${watt}W Locked" else "Standard (10W)"
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
