package com.linzin.linzindeskclock

import android.app.Activity
import android.content.Context
import android.graphics.RuntimeShader
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlin.random.Random

@Composable
fun GestureTutorialOverlay(
    showDontShowAgain: Boolean = true,
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    val gestures = listOf(
        Triple(R.string.tutorial_single_tap_title, R.string.tutorial_single_tap_desc, R.drawable.guide_single_click),
        Triple(R.string.tutorial_double_tap_title, R.string.tutorial_double_tap_desc, R.drawable.guide_click_twice),
        Triple(R.string.tutorial_two_finger_tap_title, R.string.tutorial_two_finger_tap_desc, R.drawable.guide_double_click),
        Triple(R.string.tutorial_swipe_title, R.string.tutorial_swipe_desc, R.drawable.guide_swipe)
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.tutorial_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gestures.take(2).forEach { item ->
                        TutorialGestureCard(
                            title = stringResource(item.first),
                            desc = stringResource(item.second),
                            iconRes = item.third,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.92f),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    gestures.drop(2).forEach { item ->
                        TutorialGestureCard(
                            title = stringResource(item.first),
                            desc = stringResource(item.second),
                            iconRes = item.third,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                if (showDontShowAgain) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { dontShowAgain = !dontShowAgain }
                            .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = dontShowAgain,
                            onCheckedChange = { dontShowAgain = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = Color(0xFF81C7D4),
                                uncheckedColor = Color(0xFFA0A0AA),
                                checkmarkColor = Color(0xFF041E49)
                            )
                        )
                        Text(
                            text = stringResource(R.string.tutorial_do_not_show_again),
                            color = Color(0xFFC4C4CC),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Surface(
                    onClick = { onDismiss(dontShowAgain) },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_btn_close),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialGestureCard(
    title: String,
    desc: String,
    iconRes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF1E1E24)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                painter = painterResource(id = iconRes),
                contentDescription = title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(38.dp)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = title,
                    color = Color(0xFF81C7D4),
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    color = Color(0xFFE8E8EE),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )
            }
        }
    }
}

@Composable
fun LayoutTipOverlay(
    onDismiss: (dontShowAgain: Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    var dontShowAgain by remember { mutableStateOf(false) }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.85f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp)
                .padding(bottom = 14.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = stringResource(R.string.layout_tip_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = stringResource(R.string.layout_tip_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.85f)
                )

                Spacer(modifier = Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(0.90f),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_single),
                        mode = ClockLayoutMode.SINGLE,
                        modifier = Modifier.weight(1f)
                    )
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_dual_left),
                        mode = ClockLayoutMode.DUAL_LEFT_LARGE,
                        modifier = Modifier.weight(1f)
                    )
                    LayoutPreviewCard(
                        title = stringResource(R.string.layout_tip_dual_right),
                        mode = ClockLayoutMode.DUAL_RIGHT_LARGE,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
            ) {
                Row(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { dontShowAgain = !dontShowAgain }
                        .padding(end = 12.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = dontShowAgain,
                        onCheckedChange = { dontShowAgain = it },
                        colors = CheckboxDefaults.colors(
                            checkedColor = Color(0xFF81C7D4),
                            uncheckedColor = Color(0xFFA0A0AA),
                            checkmarkColor = Color(0xFF041E49)
                        )
                    )
                    Text(
                        text = stringResource(R.string.tutorial_do_not_show_again),
                        color = Color(0xFFC4C4CC),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Surface(
                    onClick = { onDismiss(dontShowAgain) },
                    shape = RoundedCornerShape(22.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(40.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.tutorial_btn_close),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LayoutPreviewCard(
    title: String,
    mode: ClockLayoutMode,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1E1E24)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF2C2C34))
                    .padding(3.dp)
            ) {
                when (mode) {
                    ClockLayoutMode.SINGLE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF81C7D4))
                        )
                    }
                    ClockLayoutMode.DUAL_LEFT_LARGE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF81C7D4))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF5DAC81))
                            )
                        }
                    }
                    ClockLayoutMode.DUAL_RIGHT_LARGE -> {
                        Row(
                            modifier = Modifier.fillMaxSize(),
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(0.3f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF5DAC81))
                            )
                            Box(
                                modifier = Modifier
                                    .weight(0.7f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF81C7D4))
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = title,
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun SwitchToDualLayoutDialog(
    onDismiss: () -> Unit,
    onKeepSingle: () -> Unit,
    onSelectLeftLarge: () -> Unit,
    onSelectRightLarge: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.layout_switch_dialog_title),
                    color = Color.White,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = stringResource(R.string.layout_switch_dialog_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                Surface(
                    onClick = {
                        onSelectLeftLarge()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_left),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = {
                        onSelectRightLarge()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF5DAC81),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_right),
                            color = Color(0xFF041E49),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    onClick = {
                        onKeepSingle()
                        onDismiss()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C34),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.layout_switch_opt_no),
                            color = Color(0xFFA0A0AA),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun NonFoldableDialog(
    onDismiss: () -> Unit,
    onAccept: () -> Unit
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    val youtubeUrl = "https://www.youtube.com/@Linzin"
    val bilibiliUrl = "https://space.bilibili.com/360811714"

    val rejectToasts = listOf(
        stringResource(R.string.toast_reject_1),
        stringResource(R.string.toast_reject_2),
        stringResource(R.string.toast_reject_3)
    )

    val acceptThanksText = stringResource(R.string.toast_accept_thanks)
    val acceptPermanentText = stringResource(R.string.toast_accept_permanent)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF1E1E22)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 1. 大字標題
                Text(
                    text = stringResource(R.string.non_fold_dialog_title),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 2. 小字內容
                Text(
                    text = stringResource(R.string.non_fold_dialog_desc),
                    color = Color(0xFFC4C4CC),
                    fontSize = 13.5.sp,
                    lineHeight = 20.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(18.dp))

                // 3. 社群圓形按鈕
                Row(
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        onClick = { uriHandler.openUriSafely(youtubeUrl) },
                        shape = CircleShape,
                        color = Color(0xFF2C2C34),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_youtube),
                                contentDescription = "YouTube",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    Surface(
                        onClick = { uriHandler.openUriSafely(bilibiliUrl) },
                        shape = CircleShape,
                        color = Color(0xFF2C2C34),
                        modifier = Modifier.size(50.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_bilibili),
                                contentDescription = "Bilibili",
                                colorFilter = ColorFilter.tint(Color.White),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(22.dp))

                // 4. 拒絕按鈕
                Surface(
                    onClick = {
                        val randomToast = rejectToasts[Random.nextInt(rejectToasts.size)]
                        Toast.makeText(context, randomToast, Toast.LENGTH_SHORT).show()
                        (context as? Activity)?.finishAffinity()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF2C2C34),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.non_fold_btn_reject),
                            color = Color(0xFFE57373),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 5. 同意放行按鈕（計數與 Toast 切換核心邏輯）
                Surface(
                    onClick = {
                        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                        val currentCount = prefs.getInt(PREF_BYPASS_COUNT, 0) + 1
                        prefs.edit().putInt(PREF_BYPASS_COUNT, currentCount).apply()

                        // 第 3 次按下時，跳出解鎖終身放行的專屬 Toast
                        val toastMsg = if (currentCount >= PERMANENT_BYPASS_COUNT) acceptPermanentText else acceptThanksText
                        Toast.makeText(context, toastMsg, Toast.LENGTH_SHORT).show()

                        onDismiss()
                        onAccept()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF81C7D4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.non_fold_btn_accept),
                            color = Color(0xFF041E49),
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ClockSettingsDialog(
    is24Hour: Boolean,
    on24HourChange: (Boolean) -> Unit,
    showBattery: Boolean,
    onShowBatteryChange: (Boolean) -> Unit,
    showCornerDate: Boolean,
    onShowCornerDateChange: (Boolean) -> Unit,
    showHingeAngle: Boolean,
    onShowHingeAngleChange: (Boolean) -> Unit,
    hasHingeSensor: Boolean = true,
    showCalendarEvents: Boolean,
    onShowCalendarEventsChange: (Boolean) -> Unit,
    manualBrightness: Boolean,
    onManualBrightnessChange: (Boolean) -> Unit,
    brightnessValue: Float,
    onBrightnessValueChange: (Float) -> Unit,
    timeoutMillis: Long,
    onTimeoutChange: (Long) -> Unit,
    layoutMode: ClockLayoutMode,
    onLayoutModeChange: (ClockLayoutMode) -> Unit,
    weatherEnabled: Boolean,
    onWeatherEnabledChange: (Boolean) -> Unit,
    isFahrenheit: Boolean,
    onFahrenheitChange: (Boolean) -> Unit,
    burnInProtectionEnabled: Boolean,
    onBurnInProtectionChange: (Boolean) -> Unit,
    onOpenTutorial: () -> Unit,
    onDismiss: () -> Unit
) {
    var showSuggestDualDialog by remember { mutableStateOf(false) }

    val handleWeatherToggle: (Boolean) -> Unit = { targetState ->
        onWeatherEnabledChange(targetState)
        if (targetState && layoutMode == ClockLayoutMode.SINGLE) {
            showSuggestDualDialog = true
        }
    }

    val handleCalendarToggle: (Boolean) -> Unit = { targetState ->
        onShowCalendarEventsChange(targetState)
        if (targetState && layoutMode == ClockLayoutMode.SINGLE) {
            showSuggestDualDialog = true
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.88f),
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF1E1E22)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 60.dp)
                ) {
                    Text(
                        text = stringResource(R.string.settings_title),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Spacer(modifier = Modifier.height(14.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 1. 區塊版面模式選擇
                    Text(
                        text = stringResource(R.string.layout_section_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )

                    val layoutOptions = listOf(
                        ClockLayoutMode.SINGLE to stringResource(R.string.layout_single),
                        ClockLayoutMode.DUAL_LEFT_LARGE to stringResource(R.string.layout_dual_left),
                        ClockLayoutMode.DUAL_RIGHT_LARGE to stringResource(R.string.layout_dual_right)
                    )

                    layoutOptions.forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onLayoutModeChange(mode) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (layoutMode == mode),
                                onClick = { onLayoutModeChange(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (layoutMode == mode) Color.White else Color.Gray
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 2. 當地天氣資訊
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleWeatherToggle(!weatherEnabled) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_weather_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_weather_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = weatherEnabled,
                            onCheckedChange = null
                        )
                    }

                    if (weatherEnabled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onFahrenheitChange(!isFahrenheit) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = stringResource(R.string.temp_unit_switch),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Text(
                                    text = if (isFahrenheit) stringResource(R.string.temp_unit_f) else stringResource(R.string.temp_unit_c),
                                    fontSize = 13.sp,
                                    color = Color.Gray
                                )
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "°C",
                                    color = if (!isFahrenheit) Color(0xFF5DAC81) else Color(0xFF75757F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Switch(
                                    checked = isFahrenheit,
                                    onCheckedChange = null,
                                    colors = SwitchDefaults.colors(
                                        checkedTrackColor = Color(0xFF81C7D4),
                                        checkedThumbColor = Color(0xFF041E49),
                                        checkedBorderColor = Color(0xFF81C7D4),
                                        uncheckedTrackColor = Color(0xFF5DAC81),
                                        uncheckedThumbColor = Color(0xFF041E49),
                                        uncheckedBorderColor = Color(0xFF5DAC81)
                                    )
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Text(
                                    text = "°F",
                                    color = if (isFahrenheit) Color(0xFF81C7D4) else Color(0xFF75757F),
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 3. 行事曆近期行程
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { handleCalendarToggle(!showCalendarEvents) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_calendar_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_calendar_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(
                            checked = showCalendarEvents,
                            onCheckedChange = null
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 4. 12 / 24 小時制切換
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { on24HourChange(!is24Hour) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_24h_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (is24Hour) stringResource(R.string.pref_24h_desc_on) else stringResource(R.string.pref_24h_desc_off),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = is24Hour, onCheckedChange = null)
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 5. 顯示元素開關 (電量、角落日期、轉軸角度)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowBatteryChange(!showBattery) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_battery_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_battery_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = showBattery, onCheckedChange = null)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onShowCornerDateChange(!showCornerDate) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_corner_date_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_corner_date_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = showCornerDate, onCheckedChange = null)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (hasHingeSensor) {
                                    Modifier.clickable { onShowHingeAngleChange(!showHingeAngle) }
                                } else Modifier
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_hinge_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (hasHingeSensor) Color.White else Color(0xFF6E6E78)
                            )
                            Text(
                                text = if (hasHingeSensor) {
                                    stringResource(R.string.pref_hinge_desc)
                                } else {
                                    stringResource(R.string.pref_hinge_unsupported)
                                },
                                fontSize = 13.sp,
                                color = if (hasHingeSensor) Color.Gray else Color(0xFFE57373)
                            )
                        }
                        Switch(
                            checked = if (hasHingeSensor) showHingeAngle else false,
                            onCheckedChange = null,
                            enabled = hasHingeSensor
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 6. 防烙印開關 (RuntimeShader 需 Android 13 以上)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .then(
                                if (isBurnInProtectionSupported) {
                                    Modifier.clickable { onBurnInProtectionChange(!burnInProtectionEnabled) }
                                } else Modifier
                            )
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_burn_in_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isBurnInProtectionSupported) Color.White else Color(0xFF6E6E78)
                            )
                            Text(
                                text = if (isBurnInProtectionSupported) {
                                    stringResource(R.string.pref_burn_in_desc)
                                } else {
                                    stringResource(R.string.pref_burn_in_unsupported)
                                },
                                fontSize = 13.sp,
                                color = if (isBurnInProtectionSupported) Color.Gray else Color(0xFFE57373)
                            )
                        }
                        Switch(
                            checked = if (isBurnInProtectionSupported) burnInProtectionEnabled else false,
                            onCheckedChange = null,
                            enabled = isBurnInProtectionSupported
                        )
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 7. 螢幕亮度手動控制
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onManualBrightnessChange(!manualBrightness) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_brightness_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = if (manualBrightness) stringResource(R.string.pref_brightness_desc_manual) else stringResource(R.string.pref_brightness_desc_auto),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Switch(checked = manualBrightness, onCheckedChange = null)
                    }

                    if (manualBrightness) {
                        Column(modifier = Modifier.padding(bottom = 12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = stringResource(R.string.brightness_value),
                                    color = Color.LightGray,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "${(brightnessValue * 100).toInt()}%",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Slider(
                                value = brightnessValue,
                                onValueChange = onBrightnessValueChange,
                                valueRange = 0.05f..1.0f
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 8. 螢幕關閉逾時
                    Text(
                        text = stringResource(R.string.pref_timeout_title),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        modifier = Modifier.padding(top = 14.dp, bottom = 4.dp)
                    )

                    Text(
                        text = stringResource(R.string.pref_timeout_desc),
                        fontSize = 12.sp,
                        color = Color(0xFF9E9EA8),
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    val timeoutOptions = listOf(
                        60_000L to stringResource(R.string.timeout_1m),
                        180_000L to stringResource(R.string.timeout_3m),
                        600_000L to stringResource(R.string.timeout_10m),
                        1_800_000L to stringResource(R.string.timeout_30m),
                        3_600_000L to stringResource(R.string.timeout_1h),
                        10_800_000L to stringResource(R.string.timeout_3h),
                        -1L to stringResource(R.string.timeout_never)
                    )

                    timeoutOptions.forEach { (millis, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onTimeoutChange(millis) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = (timeoutMillis == millis),
                                onClick = { onTimeoutChange(millis) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = label,
                                fontSize = 16.sp,
                                color = if (timeoutMillis == millis) Color.White else Color.Gray
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF33333A)))

                    // 9. 檢視手勢操作指南
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { onOpenTutorial() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_tutorial_title),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.pref_tutorial_desc),
                                fontSize = 13.sp,
                                color = Color.Gray
                            )
                        }
                        Text(
                            text = "➔",
                            color = Color(0xFF81C7D4),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }

                Surface(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFF5DAC81),
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(20.dp)
                        .size(56.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Canvas(modifier = Modifier.size(24.dp)) {
                            val strokeWidth = 3.5.dp.toPx()
                            val checkColor = Color(0xFF041E49)

                            val p1 = Offset(size.width * 0.20f, size.height * 0.52f)
                            val p2 = Offset(size.width * 0.42f, size.height * 0.74f)
                            val p3 = Offset(size.width * 0.82f, size.height * 0.28f)

                            drawLine(
                                color = checkColor,
                                start = p1,
                                end = p2,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                            drawLine(
                                color = checkColor,
                                start = p2,
                                end = p3,
                                strokeWidth = strokeWidth,
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                if (showSuggestDualDialog) {
                    SwitchToDualLayoutDialog(
                        onDismiss = { showSuggestDualDialog = false },
                        onKeepSingle = {},
                        onSelectLeftLarge = {
                            onLayoutModeChange(ClockLayoutMode.DUAL_LEFT_LARGE)
                        },
                        onSelectRightLarge = {
                            onLayoutModeChange(ClockLayoutMode.DUAL_RIGHT_LARGE)
                        }
                    )
                }
            }
        }
    }
}
