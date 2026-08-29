package com.anm.signalrules.reconstruction.runtime

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.anm.signalrules.reconstruction.R

/** Quick Settings control for the local metadata capture gate. */
class SignalCapturePauseTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        CaptureGate.load(this)
        updateTile()
    }

    override fun onClick() {
        super.onClick()
        CaptureGate.setPaused(this, !CaptureGate.isPaused())
        updateTile()
    }

    private fun updateTile() {
        qsTile?.let { tile ->
            val paused = CaptureGate.isPaused()
            tile.state = if (paused) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            tile.label = if (paused) "Capture paused" else "Capture notifications"
            tile.contentDescription = if (paused) "Resume notification capture" else "Pause notification capture"
            tile.icon = Icon.createWithResource(this, R.mipmap.ic_launcher_monochrome)
            tile.updateTile()
        }
    }
}
