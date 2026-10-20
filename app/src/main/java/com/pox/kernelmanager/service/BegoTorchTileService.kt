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

class BegoTorchTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
            val current = readCurrentLevel()
            // Cycle: 0 -> 1 -> 2 -> 0
            val next = when (current) {
                0 -> 1
                1 -> 2
                else -> 0
            }
            TuningRepository.setTorchBrightness(next)
            launch(Dispatchers.Main) {
                updateTileState(next)
            }
        }
    }

    private suspend fun readCurrentLevel(): Int {
        val raw = ShellUtils.fastRead(KernelNodes.TORCH_BRIGHTNESS_PROC)?.trim()
        return raw?.toIntOrNull() ?: 0
    }

    private fun updateTileState(forcedLevel: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val level = forcedLevel ?: readCurrentLevel()
            val isActive = level > 0
            val label = "BegoTorch"
            val subLabel = when (level) {
                1 -> "High Level 1"
                2 -> "Overdrive Level 2"
                else -> "Off"
            }

            launch(Dispatchers.Main) {
                qsTile?.let { tile ->
                    tile.state = if (isActive) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
                    tile.icon = Icon.createWithResource(this@BegoTorchTileService, R.drawable.ic_qs_torch)
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
