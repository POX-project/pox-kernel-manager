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

class ActiveMicTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
            val current = readCurrentMode()
            val next = (current + 1) % 4
            TuningRepository.setActiveMic(next)
            launch(Dispatchers.Main) {
                updateTileState(next)
            }
        }
    }

    private suspend fun readCurrentMode(): Int {
        val fromProc = ShellUtils.fastRead(KernelNodes.ACTIVE_MIC_PROC)?.toIntOrNull()
        val fromSys = ShellUtils.fastRead(KernelNodes.ACTIVE_MIC_SYS)?.toIntOrNull()
        return fromProc ?: fromSys ?: 0
    }

    private fun updateTileState(forcedMode: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val mode = forcedMode ?: readCurrentMode()
            val label = when (mode) {
                0 -> "Main Mic"
                1 -> "Top Mic"
                2 -> "Headset"
                3 -> "Dual Mic"
                else -> "Main Mic"
            }
            val subLabel = when (mode) {
                0 -> "Bottom (AIN0)"
                1 -> "Secondary (AIN2)"
                2 -> "3.5mm (AIN1)"
                3 -> "Stereo (AIN0+2)"
                else -> "Active"
            }
            launch(Dispatchers.Main) {
                qsTile?.let { tile ->
                    tile.state = Tile.STATE_ACTIVE
                    tile.icon = Icon.createWithResource(this@ActiveMicTileService, R.drawable.ic_qs_mic)
                    tile.label = label
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = subLabel
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
