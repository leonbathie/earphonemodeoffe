package com.example

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Tuile de reglages rapides ouvrant le selecteur de sortie audio d'Android.
 *
 * C'est le seul chemin qui bascule reellement le son vers le haut-parleur sans permission
 * systeme : le forcage via AudioSystem.setForceUse est refuse aux applications ordinaires,
 * mais le selecteur natif, lui, y arrive. La tuile le rend accessible depuis n'importe ou,
 * TikTok ou YouTube au premier plan compris, sans quitter la video.
 */
class MediaOutputTileService : TileService() {

  override fun onStartListening() {
    super.onStartListening()
    qsTile?.apply {
      state = Tile.STATE_INACTIVE
      label = getString(R.string.tile_label)
      updateTile()
    }
  }

  override fun onClick() {
    super.onClick()
    val intent = mediaOutputIntent()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
      // Android 14 a retire la variante prenant un Intent.
      val pending =
        PendingIntent.getActivity(
          this,
          0,
          intent,
          PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
      startActivityAndCollapse(pending)
    } else {
      @Suppress("DEPRECATION") startActivityAndCollapse(intent)
    }
  }

  private fun mediaOutputIntent(): Intent {
    // Le panneau dedie n'existe qu'a partir d'Android 10 ; avant, on ouvre les reglages son.
    val action =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
        "com.android.settings.panel.action.MEDIA_OUTPUT"
      else Settings.ACTION_SOUND_SETTINGS
    return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
  }
}
