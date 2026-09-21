package com.shen.screenfilter.ui.vm

import android.app.Application
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import com.shen.screenfilter.data.FilterConfig
import com.shen.screenfilter.data.FilterPreset
import com.shen.screenfilter.data.FilterRepository
import com.shen.screenfilter.overlay.FilterOverlayService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * [draft] 是界面上正在拧的那组参数；[FilterRepository.config] 是真正下发给悬浮窗的那组。
 * 「实时预览」打开时两者同步，关掉时只有点了「应用滤镜」才同步过去。
 */
class FilterViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = FilterRepository

    val applied: StateFlow<FilterConfig> = repo.config
    val customPresets: StateFlow<List<FilterPreset>> = repo.customPresets
    val livePreview: StateFlow<Boolean> = repo.livePreview

    private val _draft = MutableStateFlow(repo.config.value)
    val draft: StateFlow<FilterConfig> = _draft.asStateFlow()

    private val _hasOverlayPermission = MutableStateFlow(canDrawOverlays())
    val hasOverlayPermission: StateFlow<Boolean> = _hasOverlayPermission.asStateFlow()

    fun refreshPermission() {
        _hasOverlayPermission.value = canDrawOverlays()
    }

    private fun canDrawOverlays(): Boolean =
        Settings.canDrawOverlays(getApplication())

    fun setHue(value: Float) = editDraft { it.copy(hue = value) }
    fun setGray(value: Float) = editDraft { it.copy(gray = value) }

    /** 着色层强度：色偏有多浓，也是黑位抬升的唯一来源 */
    fun setStrength(value: Float) = editDraft { it.copy(strength = value) }

    /** 亮度：100 = 原样，0 = 全黑。靠纯黑层压暗，不动对比度 */
    fun setBrightness(value: Float) = editDraft { it.copy(brightness = value) }

    fun selectPreset(preset: FilterPreset) = editDraft {
        it.copy(
            hue = preset.hue,
            gray = preset.gray,
            strength = preset.strength,
            brightness = preset.brightness,
        )
    }

    private fun editDraft(transform: (FilterConfig) -> FilterConfig) {
        val next = transform(_draft.value)
        _draft.value = next
        if (repo.livePreview.value && repo.config.value.enabled) {
            repo.updateParams(next.hue, next.gray, next.strength, next.brightness)
        }
    }

    /**
     * 按钮语义：
     *  - 还没开 → 下发参数并开启
     *  - 已开但参数被改过 → 只下发新参数
     *  - 已开且参数一致 → 关闭
     */
    fun applyOrClose() {
        val context = getApplication<Application>()
        val current = repo.config.value
        val pending = _draft.value

        if (current.enabled && current.sameParams(pending)) {
            FilterOverlayService.stop(context)
            repo.setEnabled(false)
            return
        }
        if (!canDrawOverlays()) {
            refreshPermission()
            return
        }
        repo.updateParams(pending.hue, pending.gray, pending.strength, pending.brightness)
        repo.setEnabled(true)
        FilterOverlayService.start(context)
    }

    fun setLivePreview(enabled: Boolean) {
        repo.setLivePreview(enabled)
        if (enabled && repo.config.value.enabled) {
            val pending = _draft.value
            repo.updateParams(pending.hue, pending.gray, pending.strength, pending.brightness)
        }
    }

    fun addCustomPreset(name: String) {
        val preset = repo.addCustomPreset(name, _draft.value)
        selectPreset(preset)
    }

    fun removeCustomPreset(id: String) = repo.removeCustomPreset(id)

    /** 进程被系统回收后重启时，仓库里的 enabled 会和真实的服务状态对不上，这里纠偏 */
    fun syncWithService() {
        if (!FilterOverlayService.isRunning && repo.config.value.enabled) {
            repo.setEnabled(false)
        }
    }
}
