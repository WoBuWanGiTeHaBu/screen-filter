package com.shen.screenfilter.data

import androidx.compose.ui.graphics.Color

/** 一个可一键套用的滤镜色卡 */
data class FilterPreset(
    val id: String,
    val name: String,
    val hue: Float,
    val gray: Float,
    val strength: Float,
    val brightness: Float,
    val custom: Boolean = false,
) {
    fun toConfig(): FilterConfig = FilterConfig(
        hue = hue,
        gray = gray,
        strength = strength,
        brightness = brightness,
    )

    val previewColor: Color get() = toConfig().previewColor

    fun matches(config: FilterConfig): Boolean =
        config.hue == hue && config.gray == gray &&
                config.strength == strength && config.brightness == brightness
}

/**
 * 内置预设。
 *
 * 两层模型下每个预设是两个独立旋钮的组合：
 *  - [FilterPreset.strength]   着色层强度 —— 决定色偏有多浓，也是黑位抬升的唯一来源
 *  - [FilterPreset.brightness] 亮度 —— 决定画面被纯黑层压暗多少，不影响对比度
 *
 * 所以「浅 / 中 / 深」这组不再靠堆不透明度做出层次，而是靠亮度，
 * 色偏始终压在一个克制的水平上。
 */
object BuiltInPresets {

    val all: List<FilterPreset> = listOf(
        FilterPreset("light", "浅", 210f, 50f, 10f, 94f),
        FilterPreset("medium", "中", 210f, 35f, 20f, 84f),
        FilterPreset("dark", "深", 210f, 20f, 24f, 62f),
        FilterPreset("ink", "墨", 208f, 20f, 18f, 78f),
        FilterPreset("warm", "暖", 34f, 60f, 16f, 86f),
    )

    /** 首次启动停在「墨」，正好复现原型的主色调 208° */
    val default: FilterPreset = all.first { it.id == "ink" }
}
