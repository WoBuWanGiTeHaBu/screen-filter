package com.shen.screenfilter.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PageBg = Color(0xFFF4F5F7)
val CardBg = Color(0xFFFFFFFF)
val InkPrimary = Color(0xFF101114)
val InkSecondary = Color(0xFF8A8F98)
val InkTertiary = Color(0xFFA8ADB5)
val InkBody = Color(0xFF6B7280)
val Accent = Color(0xFF2F7BFF)
val HairLine = Color(0xFFEDEFF3)
val ChipIdleBorder = Color(0xFFEDF0F4)
val TrackIdle = Color(0xFFE7E9ED)
val SoftButton = Color(0xFFF2F3F6)
val WarnBg = Color(0xFFFFF6E5)
val WarnFg = Color(0xFF96650B)

private val scheme = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = PageBg,
    onBackground = InkPrimary,
    surface = CardBg,
    onSurface = InkPrimary,
)

@Composable
fun ScreenFilterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}
