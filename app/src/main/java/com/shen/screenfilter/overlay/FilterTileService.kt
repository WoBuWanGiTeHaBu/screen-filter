package com.shen.screenfilter.overlay

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.shen.screenfilter.MainActivity
import com.shen.screenfilter.R
import com.shen.screenfilter.data.FilterRepository

/**
 * 下拉快捷开关里的磁贴：一键开 / 关滤镜。
 * 平时滤镜挂在后台，靠这个磁贴比重新打开 App 快得多。
 */
class FilterTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        FilterRepository.init(applicationContext)
        syncTile()
    }

    override fun onClick() {
        super.onClick()
        FilterRepository.init(applicationContext)
        if (FilterRepository.config.value.enabled) {
            FilterOverlayService.stop(this)
            FilterRepository.setEnabled(false)
        } else if (!Settings.canDrawOverlays(this)) {
            openAppForPermission()
        } else {
            FilterRepository.setEnabled(true)
            runCatching { FilterOverlayService.start(this) }
        }
        syncTile()
    }

    private fun syncTile() {
        val tile = qsTile ?: return
        val on = FilterRepository.config.value.enabled
        tile.state = if (on) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.app_name)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = if (on) "已开启" else "已关闭"
        }
        tile.icon = Icon.createWithResource(this, R.drawable.ic_tile_filter)
        tile.updateTile()
    }

    private fun openAppForPermission() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(
                    this, 0, intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
