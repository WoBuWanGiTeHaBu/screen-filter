package com.shen.screenfilter.data

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * 一条滤镜的完整参数。
 *
 * 渲染模型是「两层叠加」，自下而上：
 *   1. 着色层：色相 / 灰度决定的彩色，不透明度 = [tintAlpha]
 *   2. 压暗层：纯黑，不透明度 = [dimAlpha]
 *
 * 合成结果：  out = (1 − d) · [ (1 − t) · content + t · color ]
 *
 * 关键性质：content = 0（画面纯黑）时 out = (1 − d) · t · color。
 * 压暗层是纯黑叠加 = 整体乘一个系数，黑乘任何数还是黑，
 * 所以「把屏幕压暗」这件事由压暗层完成，一点都不损伤对比度；
 * 抬黑位的只有着色层的 t —— 把 [strength] 压住，黑就还是黑。
 *
 * 四个可调参数：
 *  - [hue]        色彩，0–360°，着色层的色相
 *  - [gray]       灰度，0–100。越高色偏越少，100 = 完全中性的灰
 *  - [strength]   不透明度（着色强度），0–100，着色层叠上去有多重
 *  - [brightness] 亮度，0–100，100 = 原样不压暗，0 = 全黑
 */
data class FilterConfig(
    val hue: Float = 208f,
    val gray: Float = 20f,
    val strength: Float = 18f,
    val brightness: Float = 78f,
    val enabled: Boolean = false,
) {

    private val grayFraction: Float get() = (gray / 100f).coerceIn(0f, 1f)

    /** 灰度越高，色偏越少 */
    val saturation: Float get() = 1f - grayFraction

    /** 灰度越高，中性色越沉；100 时落在中灰，不至于把画面洗白 */
    val value: Float get() = 1f - 0.5f * grayFraction

    /** 着色层的不透明度 */
    val tintAlpha: Float get() = (strength / 100f).coerceIn(0f, 1f)

    /** 压暗层的不透明度：亮度 100 → 0，亮度 0 → 1 */
    val dimAlpha: Float get() = 1f - (brightness / 100f).coerceIn(0f, 1f)

    private val hs = floatArrayOf(hue.coerceIn(0f, 360f), saturation, value)

    /** 不带透明度的着色，用于画色卡 */
    val tintColor: Color get() = Color(AndroidColor.HSVToColor(hs))

    /** 着色层真正交给悬浮窗的 ARGB（含 Alpha 通道） */
    val tintLayerArgb: Int
        get() = (toAlpha(tintAlpha) shl 24) or
                (AndroidColor.HSVToColor(hs) and 0x00FFFFFF)

    /** 压暗层真正交给悬浮窗的 ARGB（纯黑 + Alpha） */
    val dimLayerArgb: Int get() = toAlpha(dimAlpha) shl 24

    /**
     * 纯黑被抬到多亮（0–255）。这就是「黑变灰」的量：
     * 两层模型下它 = (1 − d) · t · colorLuma。
     */
    val blackLift: Float
        get() {
            val c = tintColor
            val luma = 0.2126f * c.red + 0.7152f * c.green + 0.0722f * c.blue
            return (1f - dimAlpha) * tintAlpha * luma * 255f
        }

    /** 纯黑叠完之后长什么样，比看数字直观 */
    val liftedBlackColor: Color
        get() {
            val c = tintColor
            val k = (1f - dimAlpha) * tintAlpha
            return Color(red = c.red * k, green = c.green * k, blue = c.blue * k)
        }

    /**
     * 色卡预览色：让白底走一遍整条管线。
     * 展示「蒙上去之后屏幕大概长什么样」，比单纯画一个色块更好读。
     */
    val previewColor: Color
        get() {
            val c = tintColor
            val gain = (1f - dimAlpha) * (1f - tintAlpha)
            val tintWeight = (1f - dimAlpha) * tintAlpha
            return Color(
                red = gain + tintWeight * c.red,
                green = gain + tintWeight * c.green,
                blue = gain + tintWeight * c.blue,
            )
        }

    fun sameParams(other: FilterConfig): Boolean =
        hue == other.hue && gray == other.gray &&
                strength == other.strength && brightness == other.brightness

    private fun toAlpha(fraction: Float): Int =
        (fraction.coerceIn(0f, 1f) * 255f).roundToInt().coerceIn(0, 255)
}
