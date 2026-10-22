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
            // Cycle: 0 (Off) -> 4 (Stock 100mA) -> 10 (Max Overdrive 325mA) -> 0 (Off)
            val next = when {
                current <= 0 -> 4
                current in 1..5 -> 10
                else -> 0
            }
            TuningRepository.setTorchBrightness(next)
            launch(Dispatchers.Main) {
                updateTileState(next)
            }
        }
    }

    private suspend fun readCurrentLevel(): Int {
        // Fast direct file read without shell fork (mode 0666 in kernel)
        try {
            val file = java.io.File(KernelNodes.TORCH_BRIGHTNESS_PROC)
            if (file.exists() && file.canRead()) {
                val direct = file.readText().trim().toIntOrNull()
                if (direct != null) return direct
            }
        } catch (_: Exception) { }
        val raw = ShellUtils.fastRead(KernelNodes.TORCH_BRIGHTNESS_PROC)?.trim()
        return raw?.toIntOrNull() ?: 0
    }

    private fun updateTileState(forcedLevel: Int? = null) {
        serviceScope.launch(Dispatchers.IO) {
            val level = forcedLevel ?: readCurrentLevel()
            val isActive = level > 0
            val label = "BegoTorch"
            val subLabel = when {
                level <= 0 -> "Off"
                level in 1..5 -> "Stock (Level $level)"
                else -> "Overdrive (Level $level)"
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
