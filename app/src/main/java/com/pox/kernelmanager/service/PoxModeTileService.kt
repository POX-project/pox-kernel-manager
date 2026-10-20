package com.pox.kernelmanager.service

import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.pox.kernelmanager.R
import com.pox.kernelmanager.data.model.EasyModePresets
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.repo.TuningRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PoxModeTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        serviceScope.launch(Dispatchers.IO) {
            val currentTier = TuningRepository.getActiveEasyTier()
            val currentProfile = TuningRepository.getActiveEasyProfile()

            val tiers = EasyModeTier.values()
            val nextIndex = (currentTier.ordinal + 1) % tiers.size
            val nextTier = tiers[nextIndex]

            val preset = EasyModePresets.getPreset(nextTier, currentProfile)
            TuningRepository.applyEasyPreset(preset)

            launch(Dispatchers.Main) {
                updateTileState(nextTier, currentProfile)
            }
        }
    }

    private fun updateTileState(
        forcedTier: EasyModeTier? = null,
        forcedProfile: EasyModeProfile? = null
    ) {
        serviceScope.launch(Dispatchers.IO) {
            val tier = forcedTier ?: TuningRepository.getActiveEasyTier()
            val profile = forcedProfile ?: TuningRepository.getActiveEasyProfile()

            launch(Dispatchers.Main) {
                qsTile?.let { tile ->
                    tile.state = Tile.STATE_ACTIVE
                    tile.icon = Icon.createWithResource(this@PoxModeTileService, R.drawable.ic_qs_mode)
                    tile.label = "Pox: ${tier.title}"
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        tile.subtitle = profile.title
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
