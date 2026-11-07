package com.linzin.linzindeskclock

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

/**
 * 每到整秒更新一次的目前時間，避免 delay(1000) 累積誤差造成跳秒
 */
@Composable
fun rememberCurrentTimeMillis(): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000L - System.currentTimeMillis() % 1000L)
            now = System.currentTimeMillis()
        }
    }
    return now
}

/**
 * 錶盤手勢：雙指輕點切換樣式、單指雙擊切換秒數（鎖定時忽略）
 */
fun Modifier.clockFaceTaps(
    isUiLocked: Boolean,
    onTwoFingerTap: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier = pointerInput(isUiLocked) {
    awaitPointerEventScope {
        var lastSingleTapTime = 0L

        while (true) {
            val downEvent = awaitPointerEvent(PointerEventPass.Main)
            val hasNewDown = downEvent.changes.any { it.pressed && !it.previousPressed }
            if (!hasNewDown) continue

            val startTime = System.currentTimeMillis()
            var maxPointers = downEvent.changes.count { it.pressed }
            var movedTooMuch = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val activePointers = event.changes.count { it.pressed }
                if (activePointers > maxPointers) {
                    maxPointers = activePointers
                }

                if (event.changes.any {
                        val dx = it.position.x - it.previousPosition.x
                        val dy = it.position.y - it.previousPosition.y
                        (dx * dx + dy * dy) > 1200f
                    }) {
                    movedTooMuch = true
                }

                if (event.changes.none { it.pressed }) break
                if (System.currentTimeMillis() - startTime > 450L) break
            }

            val elapsed = System.currentTimeMillis() - startTime

            if (!isUiLocked && !movedTooMuch && elapsed in 30L..450L) {
                when (maxPointers) {
                    2 -> {
                        onTwoFingerTap()
                        lastSingleTapTime = 0L
                    }
                    1 -> {
                        val now = System.currentTimeMillis()
                        if (now - lastSingleTapTime < 320L) {
                            onDoubleTap()
                            lastSingleTapTime = 0L
                        } else {
                            lastSingleTapTime = now
                        }
                    }
                }
            }
        }
    }
}

fun getDigitalDigitDrawable(char: Char, stylePrefix: String): Int? {
    return when (stylePrefix) {
        "tk" -> when (char) {
            '0' -> R.drawable.tk_0
            '1' -> R.drawable.tk_1
            '2' -> R.drawable.tk_2
            '3' -> R.drawable.tk_3
            '4' -> R.drawable.tk_4
            '5' -> R.drawable.tk_5
            '6' -> R.drawable.tk_6
            '7' -> R.drawable.tk_7
            '8' -> R.drawable.tk_8
            '9' -> R.drawable.tk_9
            ':' -> R.drawable.tk_cl
            else -> null
        }
        "ttn" -> when (char) {
            '0' -> R.drawable.ttn_0
            '1' -> R.drawable.ttn_1
            '2' -> R.drawable.ttn_2
            '3' -> R.drawable.ttn_3
            '4' -> R.drawable.ttn_4
            '5' -> R.drawable.ttn_5
            '6' -> R.drawable.ttn_6
            '7' -> R.drawable.ttn_7
            '8' -> R.drawable.ttn_8
            '9' -> R.drawable.ttn_9
            ':' -> R.drawable.ttn_cl
            else -> null
        }
        "rafm" -> when (char) {
            '0' -> R.drawable.rafm_0
            '1' -> R.drawable.rafm_1
            '2' -> R.drawable.rafm_2
            '3' -> R.drawable.rafm_3
            '4' -> R.drawable.rafm_4
            '5' -> R.drawable.rafm_5
            '6' -> R.drawable.rafm_6
            '7' -> R.drawable.rafm_7
            '8' -> R.drawable.rafm_8
            '9' -> R.drawable.rafm_9
            ':' -> R.drawable.rafm_cl
            else -> null
        }
        else -> null
    }
}

fun getDigitalAmPmDrawable(isAM: Boolean, stylePrefix: String): Int {
    return when (stylePrefix) {
        "tk" -> if (isAM) R.drawable.tk_am else R.drawable.tk_pm
        "ttn" -> if (isAM) R.drawable.ttn_am else R.drawable.ttn_pm
        "rafm" -> if (isAM) R.drawable.rafm_am else R.drawable.rafm_pm
        else -> if (isAM) R.drawable.tk_am else R.drawable.tk_pm
    }
}

@Composable
fun ClockDigitImage(
    char: Char,
    stylePrefix: String = "tk",
    height: Dp = 160.dp
) {
    val drawableRes = getDigitalDigitDrawable(char, stylePrefix)

    drawableRes?.let { resId ->
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = Modifier.height(height)
        )
    }
}

@Composable
fun DigitalClockFace(
    foldState: String,
    is24Hour: Boolean = false,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val currentTime = rememberCurrentTimeMillis()

    val availableStyles = remember { listOf("tk", "ttn", "rafm") }
    var currentStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_digital_style_idx", 0).coerceIn(0, availableStyles.size - 1))
    }
    val currentStyle = availableStyles[currentStyleIndex % availableStyles.size]

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_digital_show_sec", false))
    }

    val timeString = remember(currentTime, is24Hour, showSeconds) {
        val pattern = when {
            is24Hour && showSeconds -> "HH:mm:ss"
            is24Hour && !showSeconds -> "HH:mm"
            !is24Hour && showSeconds -> "hh:mm:ss"
            else -> "hh:mm"
        }
        SimpleDateFormat(pattern, Locale.US).format(Date(currentTime))
    }

    val isAM = remember(currentTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        cal.get(Calendar.AM_PM) == Calendar.AM
    }

    val dateString = remember(currentTime) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTime))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 24.dp, vertical = 20.dp)
            .clockFaceTaps(
                isUiLocked = isUiLocked,
                onTwoFingerTap = {
                    currentStyleIndex = (currentStyleIndex + 1) % availableStyles.size
                    prefs.edit().putInt("pref_digital_style_idx", currentStyleIndex).apply()
                },
                onDoubleTap = {
                    showSeconds = !showSeconds
                    prefs.edit().putBoolean("pref_digital_show_sec", showSeconds).apply()
                }
            )
    ) {
        val heightBasedSize = maxHeight * 0.70f

        val widthDivider = when {
            showSeconds && is24Hour -> 5.5f
            showSeconds && !is24Hour -> 6.8f
            !showSeconds && is24Hour -> 3.5f
            else -> 4.5f
        }

        val calculatedHeight = (maxWidth - 48.dp) / widthDivider
        val finalDigitHeight = min(heightBasedSize, calculatedHeight)

        val animatedDigitHeight by animateDpAsState(
            targetValue = finalDigitHeight,
            animationSpec = tween(durationMillis = 300)
        )

        val dynamicSpacing = if (showSeconds) animatedDigitHeight * 0.02f else animatedDigitHeight * 0.03f
        val spacingToAmPm = if (showSeconds) animatedDigitHeight * 0.03f else animatedDigitHeight * 0.045f

        val amPmHeight = if (currentStyle == "rafm") {
            animatedDigitHeight * (if (showSeconds) 0.32f else 0.28f)
        } else {
            animatedDigitHeight * (if (showSeconds) 0.24f else 0.22f)
        }

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(
                    dynamicSpacing,
                    Alignment.CenterHorizontally
                )
            ) {
                timeString.forEach { char ->
                    ClockDigitImage(
                        char = char,
                        stylePrefix = currentStyle,
                        height = animatedDigitHeight
                    )
                }
            }

            if (!is24Hour) {
                Spacer(modifier = Modifier.width(spacingToAmPm))

                val verticalOffset = if (currentStyle == "rafm") {
                    amPmHeight * 0.12f
                } else {
                    0.dp
                }

                Box(
                    modifier = Modifier
                        .height(animatedDigitHeight)
                        .align(Alignment.Bottom),
                    contentAlignment = Alignment.BottomStart
                ) {
                    Image(
                        painter = painterResource(
                            id = getDigitalAmPmDrawable(isAM, currentStyle)
                        ),
                        contentDescription = if (isAM) "AM" else "PM",
                        contentScale = ContentScale.FillHeight,
                        modifier = Modifier
                            .height(amPmHeight)
                            .offset(y = verticalOffset)
                    )
                }
            }
        }

        if (showCornerDate) {
            Text(
                text = dateString,
                color = Color(0xFFC4C4CC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(bottom = 8.dp)
            )
        }

        if (showHingeAngle) {
            Text(
                text = "Hinge: $foldState",
                color = Color(0xFF5A5A62),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(bottom = 8.dp)
            )
        }
    }
}

@Composable
fun AnalogClockFace(
    foldState: String,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val currentTime = rememberCurrentTimeMillis()

    var analogStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_analog_style_idx", 0).coerceIn(0, 2))
    }

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_analog_show_sec", true))
    }

    val painterAbw12 = painterResource(id = R.drawable.abw_12)
    val painterAbw6 = painterResource(id = R.drawable.abw_6)
    val painterAbw3 = painterResource(id = R.drawable.abw_3)
    val painterAbw9 = painterResource(id = R.drawable.abw_9)

    val painterOet12 = painterResource(id = R.drawable.oet_12)
    val painterOet6 = painterResource(id = R.drawable.oet_6)
    val painterOet3 = painterResource(id = R.drawable.oet_3)
    val painterOet9 = painterResource(id = R.drawable.oet_9)

    val painterZmg12 = painterResource(id = R.drawable.zmg_12)
    val painterZmg6 = painterResource(id = R.drawable.zmg_6)
    val painterZmg3 = painterResource(id = R.drawable.zmg_3)
    val painterZmg9 = painterResource(id = R.drawable.zmg_9)

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }
    val hours = calendar.get(Calendar.HOUR)
    val minutes = calendar.get(Calendar.MINUTE)
    val seconds = calendar.get(Calendar.SECOND)
    val dateString = remember(currentTime) {
        SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(Date(currentTime)).uppercase()
    }

    val currentBgColor = when (analogStyleIndex) {
        0 -> Color(0xFFEED55E)
        1 -> Color(0xFF0D0D11)
        else -> Color(0xFF161413)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(currentBgColor)
            .clockFaceTaps(
                isUiLocked = isUiLocked,
                onTwoFingerTap = {
                    analogStyleIndex = (analogStyleIndex + 1) % 3
                    prefs.edit().putInt("pref_analog_style_idx", analogStyleIndex).apply()
                },
                onDoubleTap = {
                    showSeconds = !showSeconds
                    prefs.edit().putBoolean("pref_analog_show_sec", showSeconds).apply()
                }
            )
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val cx = width / 2f
            val cy = height / 2f
            val baseRadius = minOf(width, height) / 2f

            val hourAngleDeg = (hours + minutes / 60f + seconds / 3600f) * 30f - 90f
            val minAngleDeg = (minutes + seconds / 60f) * 6f - 90f
            val secAngleDeg = seconds * 6f - 90f

            when (analogStyleIndex) {
                0 -> {
                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val ux = cos(angleRad)
                        val uy = sin(angleRad)

                        val tx = if (ux > 0.00001f) (width / 2f) / ux else if (ux < -0.00001f) (-width / 2f) / ux else Float.MAX_VALUE
                        val ty = if (uy > 0.00001f) (height / 2f) / uy else if (uy < -0.00001f) (-height / 2f) / uy else Float.MAX_VALUE
                        val t = minOf(tx, ty)

                        val isHourTick = (i % 5 == 0)
                        val tickLength = if (isHourTick) 32.dp.toPx() else 18.dp.toPx()
                        val tickStroke = if (isHourTick) 3.5.dp.toPx() else 1.8.dp.toPx()
                        val tickColor = if (isHourTick) Color(0xFF181818) else Color(0xFF181818).copy(alpha = 0.55f)

                        val startX = cx + t * ux
                        val startY = cy + t * uy
                        val endX = startX - tickLength * ux
                        val endY = startY - tickLength * uy

                        drawLine(
                            color = tickColor,
                            start = Offset(startX, startY),
                            end = Offset(endX, endY),
                            strokeWidth = tickStroke,
                            cap = StrokeCap.Square
                        )
                    }

                    val digitHeight = (minOf(width, height) * 0.16f).coerceIn(38.dp.toPx(), 54.dp.toPx())

                    val aspect12 = if (painterAbw12.intrinsicSize.height > 0) painterAbw12.intrinsicSize.width / painterAbw12.intrinsicSize.height else 0.80f
                    val w12 = digitHeight * aspect12
                    val y12 = 32.dp.toPx() + 8.dp.toPx() + digitHeight / 2f
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterAbw12) { draw(size = Size(w12, digitHeight)) }
                    }

                    val aspect6 = if (painterAbw6.intrinsicSize.height > 0) painterAbw6.intrinsicSize.width / painterAbw6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = height - (32.dp.toPx() + 8.dp.toPx() + digitHeight / 2f)
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterAbw6) { draw(size = Size(w6, digitHeight)) }
                    }

                    val aspect9 = if (painterAbw9.intrinsicSize.height > 0) painterAbw9.intrinsicSize.width / painterAbw9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = 32.dp.toPx() + 8.dp.toPx() + w9 / 2f
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterAbw9) { draw(size = Size(w9, digitHeight)) }
                    }

                    val aspect3 = if (painterAbw3.intrinsicSize.height > 0) painterAbw3.intrinsicSize.width / painterAbw3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = width - (32.dp.toPx() + 8.dp.toPx() + w3 / 2f)
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterAbw3) { draw(size = Size(w3, digitHeight)) }
                    }

                    val hourRad = (hourAngleDeg * (PI / 180f)).toFloat()
                    val hourLength = baseRadius * 0.52f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, cy),
                        end = Offset(cx + hourLength * cos(hourRad), cy + hourLength * sin(hourRad)),
                        strokeWidth = 8.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val minRad = (minAngleDeg * (PI / 180f)).toFloat()
                    val minLength = baseRadius * 0.78f
                    drawLine(
                        color = Color.White,
                        start = Offset(cx, cy),
                        end = Offset(cx + minLength * cos(minRad), cy + minLength * sin(minRad)),
                        strokeWidth = 5.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.88f
                        val tailLength = 22.dp.toPx()
                        drawLine(
                            color = Color(0xFF181818),
                            start = Offset(cx - tailLength * cos(secRad), cy - tailLength * sin(secRad)),
                            end = Offset(cx + secLength * cos(secRad), cy + secLength * sin(secRad)),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    drawCircle(color = Color.White, radius = 8.dp.toPx(), center = Offset(cx, cy))
                    if (showSeconds) {
                        drawCircle(color = Color(0xFF181818), radius = 3.5.dp.toPx(), center = Offset(cx, cy))
                    }
                }

                1 -> {
                    val vintageGold = Color(0xFFD4AF37)
                    val goldDim = Color(0xFF8C7326)
                    val steelWhite = Color(0xFFE8E8EE)

                    val outerTrackR = baseRadius * 0.92f
                    val innerTrackR = baseRadius * 0.85f

                    drawCircle(
                        color = goldDim.copy(alpha = 0.45f),
                        radius = outerTrackR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )
                    drawCircle(
                        color = goldDim.copy(alpha = 0.45f),
                        radius = innerTrackR,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.2.dp.toPx())
                    )

                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        if (i % 5 == 0) {
                            val diamondCenterR = (outerTrackR + innerTrackR) / 2f
                            val dx = cx + diamondCenterR * cosA
                            val dy = cy + diamondCenterR * sinA
                            val dSize = 3.2.dp.toPx()

                            val dPath = Path().apply {
                                moveTo(dx, dy - dSize)
                                lineTo(dx + dSize * 0.7f, dy)
                                lineTo(dx, dy + dSize)
                                lineTo(dx - dSize * 0.7f, dy)
                                close()
                            }
                            drawPath(path = dPath, color = vintageGold)
                        } else {
                            val startX = cx + innerTrackR * cosA
                            val startY = cy + innerTrackR * sinA
                            val endX = cx + outerTrackR * cosA
                            val endY = cy + outerTrackR * sinA

                            drawLine(
                                color = goldDim.copy(alpha = 0.5f),
                                start = Offset(startX, startY),
                                end = Offset(endX, endY),
                                strokeWidth = 1.dp.toPx(),
                                cap = StrokeCap.Square
                            )
                        }
                    }

                    val digitHeight = (baseRadius * 0.30f).coerceIn(40.dp.toPx(), 62.dp.toPx())
                    val numberOffsetR = innerTrackR * 0.76f

                    val aspect12 = if (painterOet12.intrinsicSize.height > 0) painterOet12.intrinsicSize.width / painterOet12.intrinsicSize.height else 0.85f
                    val w12 = digitHeight * aspect12
                    val y12 = cy - numberOffsetR
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterOet12) {
                            draw(size = Size(w12, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect6 = if (painterOet6.intrinsicSize.height > 0) painterOet6.intrinsicSize.width / painterOet6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = cy + numberOffsetR
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterOet6) {
                            draw(size = Size(w6, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect9 = if (painterOet9.intrinsicSize.height > 0) painterOet9.intrinsicSize.width / painterOet9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = cx - numberOffsetR
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterOet9) {
                            draw(size = Size(w9, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    val aspect3 = if (painterOet3.intrinsicSize.height > 0) painterOet3.intrinsicSize.width / painterOet3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = cx + numberOffsetR
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterOet3) {
                            draw(size = Size(w3, digitHeight), colorFilter = ColorFilter.tint(vintageGold))
                        }
                    }

                    fun drawSwordHand(angleDeg: Float, length: Float, baseW: Float, color: Color) {
                        val rad = (angleDeg * (PI / 180f)).toFloat()
                        val perpRad = rad + (PI / 2f).toFloat()

                        val tipX = cx + length * cos(rad)
                        val tipY = cy + length * sin(rad)
                        val waistR = length * 0.72f
                        val waistW = baseW * 0.85f

                        val w1X = cx + waistR * cos(rad) + waistW * cos(perpRad)
                        val w1Y = cy + waistR * sin(rad) + waistW * sin(perpRad)
                        val w2X = cx + waistR * cos(rad) - waistW * cos(perpRad)
                        val w2Y = cy + waistR * sin(rad) - waistW * sin(perpRad)

                        val b1X = cx + baseW * cos(perpRad)
                        val b1Y = cy + baseW * sin(perpRad)
                        val b2X = cx - baseW * cos(perpRad)
                        val b2Y = cy - baseW * sin(perpRad)

                        val tailX = cx - (length * 0.18f) * cos(rad)
                        val tailY = cy - (length * 0.18f) * sin(rad)

                        val path = Path().apply {
                            moveTo(tipX, tipY)
                            lineTo(w1X, w1Y)
                            lineTo(b1X, b1Y)
                            lineTo(tailX, tailY)
                            lineTo(b2X, b2Y)
                            lineTo(w2X, w2Y)
                            close()
                        }

                        drawPath(path = path, color = color)
                        drawPath(path = path, color = vintageGold, style = Stroke(width = 1.2.dp.toPx()))
                    }

                    drawSwordHand(hourAngleDeg, baseRadius * 0.50f, 4.8.dp.toPx(), steelWhite)
                    drawSwordHand(minAngleDeg, baseRadius * 0.75f, 3.8.dp.toPx(), steelWhite)

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.86f
                        val secTail = baseRadius * 0.22f
                        val secEndX = cx + secLength * cos(secRad)
                        val secEndY = cy + secLength * sin(secRad)
                        val secStartX = cx - secTail * cos(secRad)
                        val secStartY = cy - secTail * sin(secRad)

                        drawLine(
                            color = vintageGold,
                            start = Offset(secStartX, secStartY),
                            end = Offset(secEndX, secEndY),
                            strokeWidth = 1.6.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val counterbalanceR = secTail * 0.65f
                        val cbX = cx - counterbalanceR * cos(secRad)
                        val cbY = cy - counterbalanceR * sin(secRad)
                        drawCircle(color = vintageGold, radius = 3.8.dp.toPx(), center = Offset(cbX, cbY))
                    }

                    drawCircle(color = vintageGold, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
                    drawCircle(color = Color(0xFF0D0D11), radius = 2.8.dp.toPx(), center = Offset(cx, cy))
                }

                2 -> {
                    val color12 = Color(0xFFFFD166)
                    val color3 = Color(0xFF81C784)
                    val color6 = Color(0xFFFF708D)
                    val color9 = Color(0xFF90CAF9)

                    val handHourColor = Color(0xFFF3EEE6)
                    val handMinColor = Color(0xFFE09F67)
                    val lollipopRed = Color(0xFFFF5252)

                    val dotMajor = Color(0xFFE6DCCD)
                    val dotMinor = Color(0xFF4A403A)

                    val baseTrackR = baseRadius * 0.86f
                    val squircleExponent = 3.6f

                    for (i in 0 until 60) {
                        val angleDeg = i * 6f - 90f
                        val angleRad = (angleDeg * (PI / 180f)).toFloat()
                        val cosA = cos(angleRad)
                        val sinA = sin(angleRad)

                        val denominator = Math.pow(
                            Math.pow(Math.abs(cosA.toDouble()), squircleExponent.toDouble()) +
                                    Math.pow(Math.abs(sinA.toDouble()), squircleExponent.toDouble()),
                            1.0 / squircleExponent
                        ).toFloat()

                        val squircleR = if (denominator > 0.001f) (baseTrackR / denominator) else baseTrackR
                        val px = cx + squircleR * cosA
                        val py = cy + squircleR * sinA

                        if (i % 5 == 0) {
                            drawCircle(
                                color = dotMajor,
                                radius = 3.4.dp.toPx(),
                                center = Offset(px, py)
                            )
                        } else {
                            drawCircle(
                                color = dotMinor,
                                radius = 1.6.dp.toPx(),
                                center = Offset(px, py)
                            )
                        }
                    }

                    val digitHeight = (baseRadius * 0.28f).coerceIn(38.dp.toPx(), 58.dp.toPx())
                    val numberOffsetR = baseTrackR * 0.74f

                    val aspect12 = if (painterZmg12.intrinsicSize.height > 0) painterZmg12.intrinsicSize.width / painterZmg12.intrinsicSize.height else 0.80f
                    val w12 = digitHeight * aspect12
                    val y12 = cy - numberOffsetR
                    translate(left = cx - w12 / 2f, top = y12 - digitHeight / 2f) {
                        with(painterZmg12) {
                            draw(size = Size(w12, digitHeight), colorFilter = ColorFilter.tint(color12))
                        }
                    }

                    val aspect6 = if (painterZmg6.intrinsicSize.height > 0) painterZmg6.intrinsicSize.width / painterZmg6.intrinsicSize.height else 0.65f
                    val w6 = digitHeight * aspect6
                    val y6 = cy + numberOffsetR
                    translate(left = cx - w6 / 2f, top = y6 - digitHeight / 2f) {
                        with(painterZmg6) {
                            draw(size = Size(w6, digitHeight), colorFilter = ColorFilter.tint(color6))
                        }
                    }

                    val aspect9 = if (painterZmg9.intrinsicSize.height > 0) painterZmg9.intrinsicSize.width / painterZmg9.intrinsicSize.height else 0.65f
                    val w9 = digitHeight * aspect9
                    val x9 = cx - numberOffsetR
                    translate(left = x9 - w9 / 2f, top = cy - digitHeight / 2f) {
                        with(painterZmg9) {
                            draw(size = Size(w9, digitHeight), colorFilter = ColorFilter.tint(color9))
                        }
                    }

                    val aspect3 = if (painterZmg3.intrinsicSize.height > 0) painterZmg3.intrinsicSize.width / painterZmg3.intrinsicSize.height else 0.65f
                    val w3 = digitHeight * aspect3
                    val x3 = cx + numberOffsetR
                    translate(left = x3 - w3 / 2f, top = cy - digitHeight / 2f) {
                        with(painterZmg3) {
                            draw(size = Size(w3, digitHeight), colorFilter = ColorFilter.tint(color3))
                        }
                    }

                    val hourRad = (hourAngleDeg * (PI / 180f)).toFloat()
                    val hourLength = baseRadius * 0.48f
                    val hourTail = baseRadius * 0.10f
                    drawLine(
                        color = handHourColor,
                        start = Offset(cx - hourTail * cos(hourRad), cy - hourTail * sin(hourRad)),
                        end = Offset(cx + hourLength * cos(hourRad), cy + hourLength * sin(hourRad)),
                        strokeWidth = 9.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    val minRad = (minAngleDeg * (PI / 180f)).toFloat()
                    val minLength = baseRadius * 0.72f
                    val minTail = baseRadius * 0.10f
                    drawLine(
                        color = handMinColor,
                        start = Offset(cx - minTail * cos(minRad), cy - minTail * sin(minRad)),
                        end = Offset(cx + minLength * cos(minRad), cy + minLength * sin(minRad)),
                        strokeWidth = 6.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )

                    if (showSeconds) {
                        val secRad = (secAngleDeg * (PI / 180f)).toFloat()
                        val secLength = baseRadius * 0.84f
                        val secTail = baseRadius * 0.16f

                        drawLine(
                            color = lollipopRed,
                            start = Offset(cx - secTail * cos(secRad), cy - secTail * sin(secRad)),
                            end = Offset(cx + secLength * cos(secRad), cy + secLength * sin(secRad)),
                            strokeWidth = 2.2.dp.toPx(),
                            cap = StrokeCap.Round
                        )

                        val lollipopHeadR = 5.2.dp.toPx()
                        val popX = cx + (secLength - lollipopHeadR * 0.4f) * cos(secRad)
                        val popY = cy + (secLength - lollipopHeadR * 0.4f) * sin(secRad)
                        drawCircle(
                            color = lollipopRed,
                            radius = lollipopHeadR,
                            center = Offset(popX, popY)
                        )
                    }

                    drawCircle(color = handHourColor, radius = 6.5.dp.toPx(), center = Offset(cx, cy))
                    if (showSeconds) {
                        drawCircle(color = lollipopRed, radius = 2.8.dp.toPx(), center = Offset(cx, cy))
                    }
                }
            }
        }

        if (showCornerDate) {
            val dateTextColor = when (analogStyleIndex) {
                0 -> Color(0xFF222222)
                1 -> Color(0xFF8E8E98)
                else -> Color(0xFF9E8F85)
            }
            Text(
                text = dateString,
                color = dateTextColor,
                fontSize = if (analogStyleIndex == 1) 13.sp else 14.sp,
                fontWeight = if (analogStyleIndex == 1) FontWeight.SemiBold else FontWeight.Bold,
                letterSpacing = if (analogStyleIndex == 1) 1.2.sp else 0.sp,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 28.dp, bottom = 24.dp)
            )
        }

        if (showHingeAngle) {
            val hingeTextColor = when (analogStyleIndex) {
                0 -> Color(0xFF555555)
                1 -> Color(0xFF4A4A52)
                else -> Color(0xFF5C534D)
            }
            Text(
                text = "Hinge: $foldState",
                color = hingeTextColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 28.dp, bottom = 24.dp)
            )
        }
    }
}

fun getFlipDigitDrawable(char: Char, isTop: Boolean, stylePrefix: String = "pp"): Int? {
    return when (stylePrefix) {
        "pp" -> when (char) {
            '0' -> if (isTop) R.drawable.pp_0_u else R.drawable.pp_0_d
            '1' -> if (isTop) R.drawable.pp_1_u else R.drawable.pp_1_d
            '2' -> if (isTop) R.drawable.pp_2_u else R.drawable.pp_2_d
            '3' -> if (isTop) R.drawable.pp_3_u else R.drawable.pp_3_d
            '4' -> if (isTop) R.drawable.pp_4_u else R.drawable.pp_4_d
            '5' -> if (isTop) R.drawable.pp_5_u else R.drawable.pp_5_d
            '6' -> if (isTop) R.drawable.pp_6_u else R.drawable.pp_6_d
            '7' -> if (isTop) R.drawable.pp_7_u else R.drawable.pp_7_d
            '8' -> if (isTop) R.drawable.pp_8_u else R.drawable.pp_8_d
            '9' -> if (isTop) R.drawable.pp_9_u else R.drawable.pp_9_d
            else -> null
        }
        "fft" -> when (char) {
            '0' -> if (isTop) R.drawable.fft_0_u else R.drawable.fft_0_d
            '1' -> if (isTop) R.drawable.fft_1_u else R.drawable.fft_1_d
            '2' -> if (isTop) R.drawable.fft_2_u else R.drawable.fft_2_d
            '3' -> if (isTop) R.drawable.fft_3_u else R.drawable.fft_3_d
            '4' -> if (isTop) R.drawable.fft_4_u else R.drawable.fft_4_d
            '5' -> if (isTop) R.drawable.fft_5_u else R.drawable.fft_5_d
            '6' -> if (isTop) R.drawable.fft_6_u else R.drawable.fft_6_d
            '7' -> if (isTop) R.drawable.fft_7_u else R.drawable.fft_7_d
            '8' -> if (isTop) R.drawable.fft_8_u else R.drawable.fft_8_d
            '9' -> if (isTop) R.drawable.fft_9_u else R.drawable.fft_9_d
            else -> null
        }
        else -> null
    }
}

fun getFlipColonDrawable(stylePrefix: String): Int {
    return when (stylePrefix) {
        "fft" -> R.drawable.ftt_cl
        else -> R.drawable.pp_cl
    }
}

fun getFlipAmPmDrawable(isAM: Boolean, stylePrefix: String): Int {
    return when (stylePrefix) {
        "fft" -> if (isAM) R.drawable.fft_am else R.drawable.fft_pm
        else -> if (isAM) R.drawable.pp_am else R.drawable.pp_pm
    }
}

@Composable
fun FlipTopHalf(
    char: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    val resId = getFlipDigitDrawable(char, isTop = true, stylePrefix = stylePrefix)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = modifier.height(halfHeight)
        )
    }
}

@Composable
fun FlipBottomHalf(
    char: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    val resId = getFlipDigitDrawable(char, isTop = false, stylePrefix = stylePrefix)
    if (resId != null) {
        Image(
            painter = painterResource(id = resId),
            contentDescription = char.toString(),
            contentScale = ContentScale.FillHeight,
            modifier = modifier.height(halfHeight)
        )
    }
}

@Composable
fun SingleFlipDigitCard(
    digit: Char,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    modifier: Modifier = Modifier
) {
    var previousDigit by remember { mutableStateOf(digit) }
    var displayedDigit by remember { mutableStateOf(digit) }
    val rotation = remember { Animatable(0f) }
    val density = LocalDensity.current.density

    LaunchedEffect(digit) {
        if (digit != displayedDigit) {
            previousDigit = displayedDigit
            rotation.snapTo(0f)
            rotation.animateTo(
                targetValue = 180f,
                animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
            )
            displayedDigit = digit
            rotation.snapTo(0f)
        }
    }

    val currentRotation = rotation.value
    val isFlippingTop = currentRotation <= 90f
    val isAnimating = rotation.isRunning

    Box(
        modifier = modifier.wrapContentSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentSize()
        ) {
            val staticTopDigit = if (isAnimating) digit else displayedDigit
            FlipTopHalf(char = staticTopDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)

            Spacer(modifier = Modifier.height(0.5.dp))

            val staticBottomDigit = if (isAnimating) previousDigit else displayedDigit
            FlipBottomHalf(char = staticBottomDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)
        }

        if (isAnimating) {
            if (isFlippingTop) {
                val topAngle = -currentRotation
                val shadowAlpha = (currentRotation / 90f) * 0.45f

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 1f)
                            rotationX = topAngle
                        }
                ) {
                    FlipTopHalf(char = previousDigit, halfHeight = halfHeight, stylePrefix = stylePrefix)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = shadowAlpha))
                    )
                }
            } else {
                val bottomAngle = 180f - currentRotation
                val shadowAlpha = ((180f - currentRotation) / 90f) * 0.45f

                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .graphicsLayer {
                            cameraDistance = 16f * density
                            transformOrigin = TransformOrigin(0.5f, 0f)
                            rotationX = bottomAngle
                        }
                ) {
                    FlipBottomHalf(char = digit, halfHeight = halfHeight, stylePrefix = stylePrefix)
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = shadowAlpha))
                    )
                }
            }
        }
    }
}

@Composable
fun FlipCard(
    value: String,
    halfHeight: Dp,
    stylePrefix: String = "pp",
    digitSpacing: Dp = 1.5.dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.wrapContentSize(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(digitSpacing)
    ) {
        value.forEach { char ->
            SingleFlipDigitCard(
                digit = char,
                halfHeight = halfHeight,
                stylePrefix = stylePrefix
            )
        }
    }
}

@Composable
fun FlipClockFace(
    foldState: String,
    is24Hour: Boolean = true,
    showCornerDate: Boolean = true,
    showHingeAngle: Boolean = true,
    isUiLocked: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    val currentTime = rememberCurrentTimeMillis()

    val availableStyles = remember { listOf("pp", "fft") }
    var currentStyleIndex by remember {
        mutableIntStateOf(prefs.getInt("pref_flip_style_idx", 0).coerceIn(0, availableStyles.size - 1))
    }
    val currentStyle = availableStyles[currentStyleIndex % availableStyles.size]

    var showSeconds by remember {
        mutableStateOf(prefs.getBoolean("pref_flip_show_sec", false))
    }

    val hourPattern = if (is24Hour) "HH" else "hh"
    val hourString = remember(currentTime, is24Hour) {
        SimpleDateFormat(hourPattern, Locale.US).format(Date(currentTime))
    }
    val minString = remember(currentTime) {
        SimpleDateFormat("mm", Locale.US).format(Date(currentTime))
    }
    val secString = remember(currentTime) {
        SimpleDateFormat("ss", Locale.US).format(Date(currentTime))
    }
    val isAM = remember(currentTime) {
        val cal = Calendar.getInstance().apply { timeInMillis = currentTime }
        cal.get(Calendar.AM_PM) == Calendar.AM
    }
    val dateString = remember(currentTime) {
        SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(Date(currentTime))
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 20.dp, vertical = 12.dp)
            .clockFaceTaps(
                isUiLocked = isUiLocked,
                onTwoFingerTap = {
                    currentStyleIndex = (currentStyleIndex + 1) % availableStyles.size
                    prefs.edit().putInt("pref_flip_style_idx", currentStyleIndex).apply()
                },
                onDoubleTap = {
                    showSeconds = !showSeconds
                    prefs.edit().putBoolean("pref_flip_show_sec", showSeconds).apply()
                }
            )
    ) {
        val targetWidth = maxWidth * 0.85f

        val widthDivider = if (showSeconds) {
            if (is24Hour) 5.15f else 5.65f
        } else {
            if (is24Hour) 3.30f else 3.80f
        }

        val calculatedCardHeight = targetWidth / widthDivider
        val finalCardHeight = min(calculatedCardHeight, maxHeight * 0.85f)

        val animatedCardHeight by animateDpAsState(
            targetValue = finalCardHeight,
            animationSpec = tween(durationMillis = 350)
        )
        val halfHeight = animatedCardHeight / 2f
        val cardSpacing = animatedCardHeight * 0.035f

        Row(
            modifier = Modifier.align(Alignment.Center),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            FlipCard(value = hourString, halfHeight = halfHeight, stylePrefix = currentStyle)

            Spacer(modifier = Modifier.width(cardSpacing))

            Image(
                painter = painterResource(id = getFlipColonDrawable(currentStyle)),
                contentDescription = ":",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier.height(animatedCardHeight)
            )

            Spacer(modifier = Modifier.width(cardSpacing))

            FlipCard(value = minString, halfHeight = halfHeight, stylePrefix = currentStyle)

            if (showSeconds) {
                Spacer(modifier = Modifier.width(cardSpacing))

                Image(
                    painter = painterResource(id = getFlipColonDrawable(currentStyle)),
                    contentDescription = ":",
                    contentScale = ContentScale.FillHeight,
                    modifier = Modifier.height(animatedCardHeight)
                )

                Spacer(modifier = Modifier.width(cardSpacing))

                FlipCard(value = secString, halfHeight = halfHeight, stylePrefix = currentStyle)
            }

            if (!is24Hour) {
                Spacer(modifier = Modifier.width(cardSpacing * 1.2f))
                Box(
                    modifier = Modifier
                        .height(animatedCardHeight)
                        .align(Alignment.Bottom)
                ) {
                    Image(
                        painter = painterResource(
                            id = getFlipAmPmDrawable(isAM, currentStyle)
                        ),
                        contentDescription = if (isAM) "AM" else "PM",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(animatedCardHeight * 0.32f)
                            .align(Alignment.BottomStart)
                    )
                }
            }
        }

        if (showCornerDate) {
            Text(
                text = dateString,
                color = Color(0xFFC4C4CC),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 10.dp)
            )
        }

        if (showHingeAngle) {
            Text(
                text = "Hinge: $foldState",
                color = Color(0xFF5A5A62),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 10.dp)
            )
        }
    }
}
