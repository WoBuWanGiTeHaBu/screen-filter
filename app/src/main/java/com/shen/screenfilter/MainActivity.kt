package com.shen.screenfilter

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.shen.screenfilter.ui.FilterRoute
import com.shen.screenfilter.ui.theme.ScreenFilterTheme
import com.shen.screenfilter.ui.vm.FilterViewModel

class MainActivity : ComponentActivity() {

    private val vm: FilterViewModel by viewModels()

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    /** 悬浮窗权限是跳系统设置页授予的，回来时得手动刷一下状态 */
    private val overlayPermission =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.refreshPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        askNotificationPermission()

        setContent {
            ScreenFilterTheme {
                FilterRoute(
                    vm = vm,
                    onRequestOverlayPermission = { requestOverlayPermission() },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.refreshPermission()
        vm.syncWithService()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestOverlayPermission() {
        if (Settings.canDrawOverlays(this)) {
            vm.refreshPermission()
            return
        }
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        runCatching { overlayPermission.launch(intent) }
            .onFailure {
                // 个别定制 ROM 没有这个页面，退回到应用详情页
                runCatching {
                    startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.parse("package:$packageName")
                        )
                    )
                }
            }
    }
}
