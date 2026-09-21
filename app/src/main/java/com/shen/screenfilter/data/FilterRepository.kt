package com.shen.screenfilter.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * App 与悬浮窗服务之间唯一的共享状态源。
 *
 * 悬浮窗服务订阅 [config]，所以只要这里变了，屏幕上的蒙层立刻跟着变，
 * 不需要来回传 Intent。
 */
object FilterRepository {

    private const val PREF = "screen_filter_prefs"
    private const val K_HUE = "hue"
    private const val K_GRAY = "gray"
    private const val K_STRENGTH = "strength"
    private const val K_BRIGHTNESS = "brightness"
    private const val K_LIVE = "live_preview"
    private const val K_CUSTOM = "custom_presets"

    private var prefs: SharedPreferences? = null

    private val _config = MutableStateFlow(FilterConfig())
    val config: StateFlow<FilterConfig> = _config.asStateFlow()

    private val _customPresets = MutableStateFlow<List<FilterPreset>>(emptyList())
    val customPresets: StateFlow<List<FilterPreset>> = _customPresets.asStateFlow()

    private val _livePreview = MutableStateFlow(true)
    val livePreview: StateFlow<Boolean> = _livePreview.asStateFlow()

    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)
        prefs = p
        _config.value = FilterConfig(
            hue = p.getFloat(K_HUE, BuiltInPresets.default.hue),
            gray = p.getFloat(K_GRAY, BuiltInPresets.default.gray),
            strength = p.getFloat(K_STRENGTH, BuiltInPresets.default.strength),
            brightness = p.getFloat(K_BRIGHTNESS, BuiltInPresets.default.brightness),
            // 进程冷启动时悬浮窗一定还没挂上，所以 enabled 永远从 false 开始
            enabled = false,
        )
        _livePreview.value = p.getBoolean(K_LIVE, true)
        _customPresets.value = decode(p.getString(K_CUSTOM, null))
    }

    /** 只改参数，不动开关状态 */
    fun updateParams(hue: Float, gray: Float, strength: Float, brightness: Float) {
        val cur = _config.value
        if (cur.hue == hue && cur.gray == gray &&
            cur.strength == strength && cur.brightness == brightness
        ) return
        _config.value = cur.copy(
            hue = hue,
            gray = gray,
            strength = strength,
            brightness = brightness,
        )
        prefs?.edit()
            ?.putFloat(K_HUE, hue)
            ?.putFloat(K_GRAY, gray)
            ?.putFloat(K_STRENGTH, strength)
            ?.putFloat(K_BRIGHTNESS, brightness)
            ?.apply()
    }

    fun setEnabled(enabled: Boolean) {
        if (_config.value.enabled == enabled) return
        _config.value = _config.value.copy(enabled = enabled)
    }

    fun setLivePreview(enabled: Boolean) {
        _livePreview.value = enabled
        prefs?.edit()?.putBoolean(K_LIVE, enabled)?.apply()
    }

    fun addCustomPreset(name: String, config: FilterConfig): FilterPreset {
        val preset = FilterPreset(
            id = "custom_" + System.currentTimeMillis(),
            name = name.trim().ifBlank { "自定义" },
            hue = config.hue,
            gray = config.gray,
            strength = config.strength,
            brightness = config.brightness,
            custom = true,
        )
        val next = _customPresets.value + preset
        _customPresets.value = next
        prefs?.edit()?.putString(K_CUSTOM, encode(next))?.apply()
        return preset
    }

    fun removeCustomPreset(id: String) {
        val next = _customPresets.value.filterNot { it.id == id }
        _customPresets.value = next
        prefs?.edit()?.putString(K_CUSTOM, encode(next))?.apply()
    }

    private fun encode(list: List<FilterPreset>): String {
        val arr = JSONArray()
        list.forEach { p ->
            arr.put(
                JSONObject()
                    .put("id", p.id)
                    .put("name", p.name)
                    .put("hue", p.hue.toDouble())
                    .put("gray", p.gray.toDouble())
                    .put("strength", p.strength.toDouble())
                    .put("brightness", p.brightness.toDouble())
            )
        }
        return arr.toString()
    }

    private fun decode(raw: String?): List<FilterPreset> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                FilterPreset(
                    id = o.optString("id", "custom_$i"),
                    name = o.optString("name", "自定义"),
                    hue = o.optDouble("hue", 208.0).toFloat(),
                    gray = o.optDouble("gray", 20.0).toFloat(),
                    // 兼容旧版本存下来的 "alpha"，直接当作着色强度读回来
                    strength = o.optDouble("strength", o.optDouble("alpha", 18.0)).toFloat(),
                    brightness = o.optDouble("brightness", 78.0).toFloat(),
                    custom = true,
                )
            }
        }.getOrDefault(emptyList())
    }
}
