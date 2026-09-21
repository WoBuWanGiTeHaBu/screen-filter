package com.shen.screenfilter.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shen.screenfilter.ui.theme.Accent
import com.shen.screenfilter.ui.theme.TrackIdle

private val HueStops = arrayOf(
    0.00f to Color(0xFFFF3B30),
    0.17f to Color(0xFFFFCC00),
    0.33f to Color(0xFF34C759),
    0.50f to Color(0xFF32ADE6),
    0.67f to Color(0xFF2F7BFF),
    0.83f to Color(0xFFC032E6),
    1.00f to Color(0xFFFF3B30),
)

/**
 * 细轨滑杆。自己画而不用 Material 的 Slider，是为了：
 *  - 色相轨能直接铺一条彩虹渐变；
 *  - 轨道粗细、滑块大小完全贴合原型（原型是细轨 + 大圆点）；
 *  - 点按和拖拽在同一套手势里处理，点哪跳哪。
 */
@Composable
fun ThinSlider(
    fraction: Float,
    onFractionChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    rainbow: Boolean = false,
    activeColor: Color = Accent,
    trackHeight: Dp = 5.dp,
    thumbRadius: Dp = 10.dp,
) {
    val onChange by rememberUpdatedState(onFractionChange)

    Box(
        modifier = modifier
            .height(thumbRadius * 2 + 12.dp)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        onChange((down.position.x / width).coerceIn(0f, 1f))
                        down.consume()

                        var pointer = down
                        while (pointer.pressed) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (change.pressed) {
                                onChange((change.position.x / width).coerceIn(0f, 1f))
                                change.consume()
                            }
                            pointer = change
                        }
                    }
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val track = trackHeight.toPx()
            val radius = thumbRadius.toPx()
            val top = centerY - track / 2f
            val corner = CornerRadius(track / 2f)
            val f = fraction.coerceIn(0f, 1f)

            if (rainbow) {
                drawRoundRect(
                    brush = Brush.horizontalGradient(*HueStops),
                    topLeft = Offset(0f, top),
                    size = Size(size.width, track),
                    cornerRadius = corner,
                )
            } else {
                drawRoundRect(
                    color = TrackIdle,
                    topLeft = Offset(0f, top),
                    size = Size(size.width, track),
                    cornerRadius = corner,
                )
                if (f > 0.001f) {
                    drawRoundRect(
                        color = activeColor,
                        topLeft = Offset(0f, top),
                        size = Size(size.width * f, track),
                        cornerRadius = corner,
                    )
                }
            }

            val cx = (size.width * f).coerceIn(radius, (size.width - radius).coerceAtLeast(radius))
            val cy = centerY
            drawCircle(Color(0x14000000), radius + 1.5.dp.toPx(), Offset(cx, cy + 1.dp.toPx()))
            drawCircle(Color(0xFFEDEFF2), radius, Offset(cx, cy))
            drawCircle(Color(0xFFFAFBFC), radius * 0.72f, Offset(cx, cy))
            drawCircle(
                color = Color(0x1A0B1B33),
                radius = radius,
                center = Offset(cx, cy),
                style = Stroke(width = 1.dp.toPx()),
            )
        }
    }
}
