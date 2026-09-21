package com.shen.screenfilter.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.shen.screenfilter.MainActivity
import com.shen.screenfilter.R
import com.shen.screenfilter.data.FilterConfig
import com.shen.screenfilter.data.FilterRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 全屏色彩蒙层。
 *
 * 做法是往 WindowManager 上挂一个 `TYPE_APPLICATION_OVERLAY` 的窗口：
 * 它是普通第三方 App 能拿到的最高层级，盖在所有 App 之上，但仍然在状态栏、
 * 输入法、锁屏这些系统关键窗口之下——所以状态栏那一小条不会被染上颜色，
 * 这是这类免 root「护眼 / 遮光」 App 的统一行为。
 *
 * 窗口带 `FLAG_NOT_TOUCHABLE`，手指事件全部穿透，蒙层不会挡住任何操作。
 *
 * 窗口内画两层（见 [TwoLayerOverlay]）：
 *  1. 着色层 —— 带颜色的那一层，透明度 = 强度，是「黑变灰」的唯一来源；
 *  2. 压暗层 —— 纯黑，透明度 = 1 − 亮度。纯黑叠加等于给整幅画面乘一个系数，
 *     黑乘任何数还是黑，所以压暗不再伤对比度。
 * 只用一层 View + 一个自绘 Drawable 画完，而不是挂两个全屏窗口，
 * 省掉一整块全屏 GPU layer 的开销。
 */
class FilterOverlayService : Service() {

    companion object {
        private const val ACTION_START = "com.shen.screenfilter.action.START"
        private const val ACTION_STOP = "com.shen.screenfilter.action.STOP"

        private const val CHANNEL_ID = "screen_filter_channel"
        private const val NOTIF_ID = 1001

        /** 与 App 同进程，用来让界面判断悬浮窗此刻是否真的挂着 */
        @Volatile
        var isRunning: Boolean = false
            private set

        fun start(context: Context) {
            val intent = Intent(context, FilterOverlayService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, FilterOverlayService::class.java).setAction(ACTION_STOP)
            runCatching { context.startService(intent) }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var observeJob: Job? = null

    private var windowManager: WindowManager? = null
    private var overlay: View? = null
    private val paint = TwoLayerOverlay()
    private var lastConfig: FilterConfig = FilterConfig()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                teardown()
                stopSelf()
                return START_NOT_STICKY
            }

            else -> {
                if (!Settings.canDrawOverlays(this)) {
                    // 权限被撤销了，别留一个半死不活的服务
                    teardown()
                    stopSelf()
                    return START_NOT_STICKY
                }
                FilterRepository.init(this)
                startInForeground()
                attachOverlay()
                observeConfig()
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(lastConfig), type)
    }

    private fun attachOverlay() {
        if (overlay != null) return
        val wm = (getSystemService(Context.WINDOW_SERVICE) as? WindowManager) ?: return

        var lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 铺满刘海挖孔区域
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
            // 不被状态栏 / 导航栏的 inset 顶开，真正铺满整屏
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setFitInsetsTypes(0)
            }
        }

        val view = View(this).apply {
            isClickable = false
            isFocusable = false
            background = paint
        }

        val added = runCatching { wm.addView(view, lp) }.isSuccess
        if (!added) return

        windowManager = wm
        overlay = view
        isRunning = true
        apply(FilterRepository.config.value)
    }

    private fun observeConfig() {
        if (observeJob != null) return
        observeJob = scope.launch {
            FilterRepository.config.collectLatest { cfg -> apply(cfg) }
        }
    }

    private fun apply(config: FilterConfig) {
        lastConfig = config
        paint.tintColor = config.tintLayerArgb
        paint.dimColor = config.dimLayerArgb
        overlay?.invalidate()
        if (isRunning) updateNotification(config)
    }

    private fun teardown() {
        observeJob?.cancel()
        observeJob = null

        overlay?.let { view ->
            runCatching { windowManager?.removeViewImmediate(view) }
        }
        overlay = null
        windowManager = null
        isRunning = false

        runCatching {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        }
        FilterRepository.setEnabled(false)
    }

    override fun onDestroy() {
        teardown()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- 通知栏

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                setSound(null, null)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(config: FilterConfig): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, FilterOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val summary = buildString {
            append(config.hue.roundToInt()).append("°")
            append(" · 灰度 ").append(config.gray.roundToInt()).append('%')
            append(" · 不透明度 ").append(config.strength.roundToInt()).append('%')
            append(" · 亮度 ").append(config.brightness.roundToInt()).append('%')
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_tile_filter)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(summary)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .addAction(0, getString(R.string.notif_action_stop), stop)
            .build()
    }

    private fun updateNotification(config: FilterConfig) {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.notify(NOTIF_ID, buildNotification(config)) }
    }
}

/**
 * 一层窗口里画两遍全屏纯色：先着色层，再压暗层。
 *
 * 两次 [Canvas.drawColor] 都是 src-over，叠起来等价于
 *   out = (1 − d) · [ (1 − t) · content + t · color ]
 * 正好对应 [FilterConfig] 的合成式 —— 黑位只被 t 抬起，压暗由 (1 − d) 完成。
 */
private class TwoLayerOverlay : Drawable() {

    var tintColor: Int = AndroidColor.TRANSPARENT
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    var dimColor: Int = AndroidColor.TRANSPARENT
        set(value) {
            if (field == value) return
            field = value
            invalidateSelf()
        }

    override fun draw(canvas: Canvas) {
        if (AndroidColor.alpha(tintColor) != 0) canvas.drawColor(tintColor)
        if (AndroidColor.alpha(dimColor) != 0) canvas.drawColor(dimColor)
    }

    override fun setAlpha(alpha: Int) = Unit

    override fun setColorFilter(colorFilter: ColorFilter?) = Unit

    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
