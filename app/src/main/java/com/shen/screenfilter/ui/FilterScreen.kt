package com.shen.screenfilter.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shen.screenfilter.data.BuiltInPresets
import com.shen.screenfilter.data.FilterPreset
import com.shen.screenfilter.ui.components.ThinSlider
import com.shen.screenfilter.ui.theme.Accent
import com.shen.screenfilter.ui.theme.CardBg
import com.shen.screenfilter.ui.theme.ChipIdleBorder
import com.shen.screenfilter.ui.theme.HairLine
import com.shen.screenfilter.ui.theme.InkBody
import com.shen.screenfilter.ui.theme.InkPrimary
import com.shen.screenfilter.ui.theme.InkSecondary
import com.shen.screenfilter.ui.theme.InkTertiary
import com.shen.screenfilter.ui.theme.PageBg
import com.shen.screenfilter.ui.theme.SoftButton
import com.shen.screenfilter.ui.theme.WarnBg
import com.shen.screenfilter.ui.theme.WarnFg
import com.shen.screenfilter.ui.vm.FilterViewModel
import kotlin.math.roundToInt

@Composable
fun FilterRoute(
    vm: FilterViewModel,
    onRequestOverlayPermission: () -> Unit,
) {
    val applied by vm.applied.collectAsState()
    val draft by vm.draft.collectAsState()
    val customPresets by vm.customPresets.collectAsState()
    val livePreview by vm.livePreview.collectAsState()
    val hasPermission by vm.hasOverlayPermission.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<FilterPreset?>(null) }

    val presets = remember(customPresets) { BuiltInPresets.all + customPresets }
    val selected = presets.firstOrNull { it.matches(draft) }
    val dirty = !applied.sameParams(draft)
    val buttonLabel = if (applied.enabled && !dirty) "关闭滤镜" else "应用滤镜"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PageBg)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(6.dp))

        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "滤镜",
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = InkPrimary,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "护眼遮光 · 全屏可自定义",
                    fontSize = 13.sp,
                    color = InkSecondary,
                )
            }
            LivePreviewToggle(
                on = livePreview,
                onToggle = vm::setLivePreview,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        Spacer(Modifier.height(20.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 14.dp,
                    shape = RoundedCornerShape(24.dp),
                    clip = false,
                    ambientColor = Color(0x0D1B2A4A),
                    spotColor = Color(0x141B2A4A),
                )
                .clip(RoundedCornerShape(24.dp))
                .background(CardBg)
                .padding(horizontal = 18.dp, vertical = 20.dp)
        ) {
            SectionTitle("预设滤镜", "快速选择")
            Spacer(Modifier.height(16.dp))

            PresetGrid(
                presets = presets,
                selectedId = selected?.id,
                onSelect = vm::selectPreset,
                onAdd = { showAddDialog = true },
                onLongPressCustom = { pendingDelete = it },
            )

            Spacer(Modifier.height(22.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(HairLine))
            Spacer(Modifier.height(20.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("自定义", "参数微调")
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(draft.previewColor)
                        .border(1.dp, Color(0x1A0B1B33), CircleShape)
                )
            }
            Spacer(Modifier.height(14.dp))

            ParamRow(
                label = "色彩",
                valueText = "${draft.hue.roundToInt()}°",
                value = draft.hue,
                range = 0f..360f,
                step = 5f,
                rainbow = true,
                onValueChange = vm::setHue,
            )
            ParamRow(
                label = "灰度",
                valueText = "${draft.gray.roundToInt()}%",
                value = draft.gray,
                range = 0f..100f,
                step = 5f,
                onValueChange = vm::setGray,
            )
            ParamRow(
                label = "不透明度",
                valueText = "${draft.strength.roundToInt()}%",
                value = draft.strength,
                range = 0f..100f,
                step = 5f,
                onValueChange = vm::setStrength,
            )
            ParamRow(
                label = "亮度",
                valueText = "${draft.brightness.roundToInt()}%",
                value = draft.brightness,
                range = 0f..100f,
                step = 5f,
                onValueChange = vm::setBrightness,
            )

            Spacer(Modifier.height(2.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Box(
                    Modifier
                        .padding(top = 5.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFB9C0CB))
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "压暗走的是纯黑层，黑位不会被抬亮，所以「想更暗」请拉亮度、" +
                            "「想更有颜色」再抬不透明度 —— 两者互不干扰。",
                    fontSize = 11.5.sp,
                    color = InkTertiary,
                    lineHeight = 17.sp,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))

            if (!hasPermission) {
                PermissionHint(onRequestOverlayPermission)
                Spacer(Modifier.height(12.dp))
            }

            ApplyButton(label = buttonLabel, onClick = vm::applyOrClose)

            Spacer(Modifier.height(4.dp))
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "开启后手指操作照常，蒙层不拦截触摸；下拉通知栏或快捷开关「屏幕滤镜」可随时关闭。",
            fontSize = 12.sp,
            color = InkTertiary,
            lineHeight = 18.sp,
        )
        Spacer(Modifier.height(28.dp))
    }

    if (showAddDialog) {
        AddPresetDialog(
            preview = draft.previewColor,
            onDismiss = { showAddDialog = false },
            onConfirm = { name ->
                vm.addCustomPreset(name)
                showAddDialog = false
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("删除预设", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = { Text("「${target.name}」将从色卡列表里移除。", fontSize = 14.sp, color = InkBody) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeCustomPreset(target.id)
                    pendingDelete = null
                }) { Text("删除", color = Color(0xFFE5484D)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
            },
            containerColor = CardBg,
        )
    }
}

// ------------------------------------------------------------------ 组件

@Composable
private fun SectionTitle(main: String, sub: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(main, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
        Spacer(Modifier.width(6.dp))
        Text(sub, fontSize = 12.sp, color = InkTertiary)
    }
}

@Composable
private fun LivePreviewToggle(
    on: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .clickable { onToggle(!on) }
            .padding(horizontal = 6.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(if (on) Accent else Color(0xFFC6CAD1))
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = "实时预览",
            fontSize = 13.sp,
            color = if (on) Accent else InkSecondary,
            fontWeight = if (on) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun PresetGrid(
    presets: List<FilterPreset>,
    selectedId: String?,
    onSelect: (FilterPreset) -> Unit,
    onAdd: () -> Unit,
    onLongPressCustom: (FilterPreset) -> Unit,
) {
    val cellCount = presets.size + 1 // 末尾的「添加」
    val rowCount = (cellCount + 2) / 3

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        for (row in 0 until rowCount) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                for (col in 0 until 3) {
                    val index = row * 3 + col
                    Box(Modifier.weight(1f)) {
                        when {
                            index < presets.size -> {
                                val preset = presets[index]
                                PresetChip(
                                    preset = preset,
                                    selected = preset.id == selectedId,
                                    onClick = { onSelect(preset) },
                                    onLongClick = if (preset.custom) {
                                        { onLongPressCustom(preset) }
                                    } else null,
                                )
                            }

                            index == presets.size -> AddChip(onClick = onAdd)
                            else -> Unit
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetChip(
    preset: FilterPreset,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(preset.previewColor)
                .then(
                    if (selected) {
                        Modifier.border(2.dp, Accent, RoundedCornerShape(16.dp))
                    } else {
                        Modifier.border(1.dp, ChipIdleBorder, RoundedCornerShape(16.dp))
                    }
                )
                .combinedClickable(onClick = onClick, onLongClick = onLongClick)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = preset.name,
            fontSize = 13.sp,
            maxLines = 1,
            color = if (selected) Accent else InkBody,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

@Composable
private fun AddChip(onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(66.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFFEFF2F6))
                .border(1.dp, ChipIdleBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(22.dp)) {
                val thickness = 2.6.dp.toPx()
                val corner = CornerRadius(thickness / 2f, thickness / 2f)
                drawRoundRect(
                    color = Accent,
                    topLeft = Offset(size.width / 2f - thickness / 2f, 0f),
                    size = Size(thickness, size.height),
                    cornerRadius = corner,
                )
                drawRoundRect(
                    color = Accent,
                    topLeft = Offset(0f, size.height / 2f - thickness / 2f),
                    size = Size(size.width, thickness),
                    cornerRadius = corner,
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("添加", fontSize = 13.sp, color = InkBody)
    }
}

@Composable
private fun ParamRow(
    label: String,
    valueText: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    step: Float,
    onValueChange: (Float) -> Unit,
    rainbow: Boolean = false,
) {
    val span = range.endInclusive - range.start
    val fraction = ((value - range.start) / span).coerceIn(0f, 1f)

    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = InkPrimary)
            Spacer(Modifier.weight(1f))
            Text(valueText, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = Accent)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            StepButton("−") {
                onValueChange((value - step).coerceIn(range.start, range.endInclusive))
            }
            Spacer(Modifier.width(10.dp))
            ThinSlider(
                fraction = fraction,
                onFractionChange = { onValueChange(range.start + it * span) },
                modifier = Modifier.weight(1f),
                rainbow = rainbow,
            )
            Spacer(Modifier.width(10.dp))
            StepButton("+") {
                onValueChange((value + step).coerceIn(range.start, range.endInclusive))
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(SoftButton)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF565B63),
        )
    }
}

@Composable
private fun PermissionHint(onRequest: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(WarnBg)
            .clickable(onClick = onRequest)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(Color(0xFFFFA940))
        )
        Spacer(Modifier.width(10.dp))
        Text(
            text = "还需授予「显示在其他应用上层」权限，点这里去开启",
            fontSize = 12.5.sp,
            color = WarnFg,
            lineHeight = 17.sp,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ApplyButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 12.dp,
                shape = RoundedCornerShape(16.dp),
                clip = false,
                ambientColor = Color(0x332F7BFF),
                spotColor = Color(0x4D2F7BFF),
            )
            .clip(RoundedCornerShape(16.dp))
            .background(Accent)
            .clickable(onClick = onClick)
            .padding(vertical = 17.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
    }
}

@Composable
private fun AddPresetDialog(
    preview: Color,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardBg,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("存为预设", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = InkPrimary)
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(preview)
                        .border(1.dp, Color(0x1A0B1B33), CircleShape)
                )
            }
        },
        text = {
            Column {
                Text(
                    text = "把现在的色彩、灰度、不透明度和亮度存成一张色卡，之后一键就能套回来。",
                    fontSize = 13.sp,
                    color = InkBody,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("例如：夜读", fontSize = 14.sp, color = InkTertiary) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        },
    )
}
