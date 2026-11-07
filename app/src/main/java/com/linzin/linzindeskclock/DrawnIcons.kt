package com.linzin.linzindeskclock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BatteryIndicator(
    batteryLevel: Int,
    isCharging: Boolean,
    modifier: Modifier = Modifier
) {
    val textShadow = Shadow(
        color = Color.Black.copy(alpha = 0.65f),
        offset = Offset(1.5f, 2f),
        blurRadius = 5f
    )

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$batteryLevel%",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            style = TextStyle(shadow = textShadow)
        )

        Spacer(modifier = Modifier.width(6.dp))

        Canvas(modifier = Modifier.size(width = 26.dp, height = 13.dp)) {
            val w = size.width
            val h = size.height
            val strokeW = 1.3.dp.toPx()

            val bodyWidth = w - 3.5.dp.toPx()
            val bodyHeight = h
            val bodyRadius = 3.2.dp.toPx()

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.40f),
                topLeft = Offset(0.8.dp.toPx(), 1.2.dp.toPx()),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                style = Stroke(width = strokeW)
            )

            drawRoundRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = Offset(0f, 0f),
                size = Size(bodyWidth, bodyHeight),
                cornerRadius = CornerRadius(bodyRadius, bodyRadius),
                style = Stroke(width = strokeW)
            )

            val capW = 2.dp.toPx()
            val capH = 5.dp.toPx()
            val capX = bodyWidth + 0.8.dp.toPx()
            val capY = (h - capH) / 2f
            drawRoundRect(
                color = Color.White.copy(alpha = 0.90f),
                topLeft = Offset(capX, capY),
                size = Size(capW, capH),
                cornerRadius = CornerRadius(1.2.dp.toPx(), 1.2.dp.toPx())
            )

            val pad = 2.dp.toPx()
            val innerW = (bodyWidth - pad * 2f).coerceAtLeast(0f)
            val innerH = (bodyHeight - pad * 2f).coerceAtLeast(0f)
            val fillW = (innerW * (batteryLevel.coerceIn(0, 100) / 100f)).coerceAtLeast(0f)

            val fillColor = when {
                isCharging -> Color(0xFF34C759)
                batteryLevel <= 15 -> Color(0xFFFF3B30)
                batteryLevel <= 20 -> Color(0xFFFF9500)
                else -> Color(0xFF34C759)
            }

            if (fillW > 0f) {
                drawRoundRect(
                    color = fillColor,
                    topLeft = Offset(pad, pad),
                    size = Size(fillW, innerH),
                    cornerRadius = CornerRadius(1.8.dp.toPx(), 1.8.dp.toPx())
                )
            }

            if (isCharging) {
                val boltPath = Path().apply {
                    val bcx = bodyWidth / 2f
                    val bcy = h / 2f
                    val bw = 6.dp.toPx()
                    val bh = 9.dp.toPx()

                    moveTo(bcx + bw * 0.15f, bcy - bh * 0.50f)
                    lineTo(bcx - bw * 0.45f, bcy + bh * 0.05f)
                    lineTo(bcx - bw * 0.05f, bcy + bh * 0.05f)
                    lineTo(bcx - bw * 0.20f, bcy + bh * 0.50f)
                    lineTo(bcx + bw * 0.45f, bcy - bh * 0.05f)
                    lineTo(bcx + bw * 0.05f, bcy - bh * 0.05f)
                    close()
                }

                drawPath(
                    path = boltPath,
                    color = Color.Black.copy(alpha = 0.5f),
                    style = Stroke(width = 1.2.dp.toPx())
                )
                drawPath(
                    path = boltPath,
                    color = Color(0xFFFFD600)
                )
            }
        }
    }
}

@Composable
fun MaterialLockIcon(
    isLocked: Boolean,
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        val bodyW = w * 0.72f
        val bodyH = h * 0.48f
        val bodyLeft = (w - bodyW) / 2f
        val bodyTop = h * 0.46f
        val bodyRadius = 2.5.dp.toPx()

        val shackleW = bodyW * 0.58f
        val shackleRadius = shackleW / 2f
        val shackleStroke = 2.0.dp.toPx()

        if (isLocked) {
            val arcTop = h * 0.16f
            val arcLeft = (w - shackleW) / 2f

            val shacklePath = Path().apply {
                moveTo(arcLeft, bodyTop + 1.dp.toPx())
                lineTo(arcLeft, arcTop + shackleRadius)
                arcTo(
                    rect = Rect(arcLeft, arcTop, arcLeft + shackleW, arcTop + shackleW),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                lineTo(arcLeft + shackleW, bodyTop + 1.dp.toPx())
            }

            drawPath(
                path = shacklePath,
                color = color,
                style = Stroke(width = shackleStroke, cap = StrokeCap.Round)
            )
        } else {
            val arcTop = h * 0.08f
            val arcLeft = (w - shackleW) / 2f

            val openShacklePath = Path().apply {
                moveTo(arcLeft, bodyTop - 2.dp.toPx())
                lineTo(arcLeft, arcTop + shackleRadius)
                arcTo(
                    rect = Rect(arcLeft, arcTop, arcLeft + shackleW, arcTop + shackleW),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                lineTo(arcLeft + shackleW, arcTop + shackleRadius + 2.dp.toPx())
            }

            drawPath(
                path = openShacklePath,
                color = color,
                style = Stroke(width = shackleStroke, cap = StrokeCap.Round)
            )
        }

        drawRoundRect(
            color = color,
            topLeft = Offset(bodyLeft, bodyTop),
            size = Size(bodyW, bodyH),
            cornerRadius = CornerRadius(bodyRadius, bodyRadius)
        )

        val keyHoleCx = w / 2f
        val keyHoleCy = bodyTop + bodyH * 0.42f
        val keyHoleR = 1.8.dp.toPx()
        val holeColor = Color(0xFF1E1E22)

        drawCircle(
            color = holeColor,
            radius = keyHoleR,
            center = Offset(keyHoleCx, keyHoleCy)
        )
        drawLine(
            color = holeColor,
            start = Offset(keyHoleCx, keyHoleCy),
            end = Offset(keyHoleCx, keyHoleCy + bodyH * 0.32f),
            strokeWidth = 1.6.dp.toPx(),
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun LocationPinIcon(
    modifier: Modifier = Modifier,
    color: Color = Color.White
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val r = w * 0.40f
        val cy = r + h * 0.04f
        val strokeW = 1.6.dp.toPx()

        val path = Path().apply {
            arcTo(
                rect = Rect(cx - r, cy - r, cx + r, cy + r),
                startAngleDegrees = -198f,
                sweepAngleDegrees = 216f,
                forceMoveTo = false
            )
            lineTo(cx, h - strokeW * 0.5f)
            close()
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeW, cap = StrokeCap.Round)
        )

        drawCircle(
            color = color,
            radius = r * 0.42f,
            center = Offset(cx, cy),
            style = Stroke(width = strokeW)
        )
    }
}

@Composable
fun MaterialWeatherIcon(
    weatherCode: Int,
    modifier: Modifier = Modifier
) {
    val sunYellow = Color(0xFFFFD54F)
    val cloudWhite = Color.White
    val rainBlue = Color(0xFF42A5F5)
    val drizzleBlue = Color(0xFF81D4FA)
    val fogGrey = Color(0xFFB0BEC5)
    val lightningGold = Color(0xFFFFCA28)
    val snowCyan = Color(0xFFE1F5FE)

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height

        fun drawCloud(cx: Float, cy: Float, scaleW: Float = 1f, scaleH: Float = 1f, color: Color = cloudWhite) {
            val baseW = w * 0.60f * scaleW
            val baseH = h * 0.28f * scaleH
            val left = cx - baseW / 2f

            drawRoundRect(
                color = color,
                topLeft = Offset(left, cy),
                size = Size(baseW, baseH * 0.65f),
                cornerRadius = CornerRadius(baseH * 0.32f, baseH * 0.32f)
            )
            drawCircle(color = color, radius = baseH * 0.52f, center = Offset(cx - baseW * 0.22f, cy + baseH * 0.10f))
            drawCircle(color = color, radius = baseH * 0.70f, center = Offset(cx + baseW * 0.04f, cy - baseH * 0.05f))
            drawCircle(color = color, radius = baseH * 0.44f, center = Offset(cx + baseW * 0.28f, cy + baseH * 0.18f))
        }

        when (weatherCode) {
            0 -> {
                val cx = w / 2f
                val cy = h / 2f
                val r = w * 0.28f
                drawCircle(color = sunYellow, radius = r, center = Offset(cx, cy))

                val rayDist = r + w * 0.08f
                val rayLen = w * 0.13f
                val strokeW = w * 0.075f
                for (i in 0 until 8) {
                    val angle = (i * 45f) * (PI / 180f).toFloat()
                    val sx = cx + rayDist * cos(angle)
                    val sy = cy + rayDist * sin(angle)
                    val ex = cx + (rayDist + rayLen) * cos(angle)
                    val ey = cy + (rayDist + rayLen) * sin(angle)
                    drawLine(color = sunYellow, start = Offset(sx, sy), end = Offset(ex, ey), strokeWidth = strokeW, cap = StrokeCap.Round)
                }
            }

            1, 2 -> {
                val sunCx = w * 0.68f
                val sunCy = h * 0.34f
                val sunR = w * 0.22f
                drawCircle(color = sunYellow, radius = sunR, center = Offset(sunCx, sunCy))

                for (i in 0 until 6) {
                    val angle = (i * 60f) * (PI / 180f).toFloat()
                    val dist = sunR + w * 0.06f
                    val len = w * 0.09f
                    drawLine(
                        color = sunYellow,
                        start = Offset(sunCx + dist * cos(angle), sunCy + dist * sin(angle)),
                        end = Offset(sunCx + (dist + len) * cos(angle), sunCy + (dist + len) * sin(angle)),
                        strokeWidth = w * 0.055f,
                        cap = StrokeCap.Round
                    )
                }
                drawCloud(cx = w * 0.44f, cy = h * 0.58f, scaleW = 1.05f, scaleH = 1.05f)
            }

            3 -> {
                drawCloud(cx = w / 2f, cy = h * 0.48f, scaleW = 1.25f, scaleH = 1.25f)
            }

            45, 48 -> {
                drawCloud(cx = w / 2f, cy = h * 0.38f, scaleW = 1.15f, scaleH = 1.05f)
                val strokeW = w * 0.055f
                drawLine(color = fogGrey, start = Offset(w * 0.22f, h * 0.70f), end = Offset(w * 0.78f, h * 0.70f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = fogGrey, start = Offset(w * 0.32f, h * 0.84f), end = Offset(w * 0.68f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)
            }

            51, 53, 55 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)
                val dotR = w * 0.042f
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.35f, h * 0.73f))
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.51f, h * 0.82f))
                drawCircle(color = drizzleBlue, radius = dotR, center = Offset(w * 0.67f, h * 0.73f))
            }

            80 -> {
                val sunCx = w * 0.70f
                val sunCy = h * 0.32f
                val sunR = w * 0.20f
                drawCircle(color = sunYellow, radius = sunR, center = Offset(sunCx, sunCy))

                for (i in 0 until 5) {
                    val angle = (i * 72f) * (PI / 180f).toFloat()
                    val dist = sunR + w * 0.06f
                    drawLine(
                        color = sunYellow,
                        start = Offset(sunCx + dist * cos(angle), sunCy + dist * sin(angle)),
                        end = Offset(sunCx + (dist + w * 0.08f) * cos(angle), sunCy + (dist + w * 0.08f) * sin(angle)),
                        strokeWidth = w * 0.05f,
                        cap = StrokeCap.Round
                    )
                }
                drawCloud(cx = w * 0.42f, cy = h * 0.52f, scaleW = 1.0f, scaleH = 1.0f)

                val strokeW = w * 0.05f
                drawLine(color = rainBlue, start = Offset(w * 0.38f, h * 0.74f), end = Offset(w * 0.33f, h * 0.88f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = rainBlue, start = Offset(w * 0.54f, h * 0.74f), end = Offset(w * 0.49f, h * 0.88f), strokeWidth = strokeW, cap = StrokeCap.Round)
            }

            61, 63, 65, 81, 82 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)
                val strokeW = w * 0.055f
                val rainLen = h * 0.16f
                for (k in 0..2) {
                    val sx = w * (0.33f + k * 0.17f)
                    val sy = h * 0.70f
                    drawLine(
                        color = rainBlue,
                        start = Offset(sx, sy),
                        end = Offset(sx - w * 0.055f, sy + rainLen),
                        strokeWidth = strokeW,
                        cap = StrokeCap.Round
                    )
                }
            }

            95, 96, 99 -> {
                drawCloud(cx = w / 2f, cy = h * 0.38f, scaleW = 1.15f, scaleH = 1.05f)

                val strokeW = w * 0.045f
                drawLine(color = rainBlue, start = Offset(w * 0.28f, h * 0.70f), end = Offset(w * 0.23f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)
                drawLine(color = rainBlue, start = Offset(w * 0.72f, h * 0.70f), end = Offset(w * 0.67f, h * 0.84f), strokeWidth = strokeW, cap = StrokeCap.Round)

                val boltPath = Path().apply {
                    moveTo(w * 0.54f, h * 0.58f)
                    lineTo(w * 0.43f, h * 0.74f)
                    lineTo(w * 0.52f, h * 0.74f)
                    lineTo(w * 0.45f, h * 0.94f)
                    lineTo(w * 0.60f, h * 0.71f)
                    lineTo(w * 0.51f, h * 0.71f)
                    close()
                }
                drawPath(path = boltPath, color = lightningGold)
            }

            71, 73, 75, 77, 85, 86 -> {
                drawCloud(cx = w / 2f, cy = h * 0.40f, scaleW = 1.15f, scaleH = 1.05f)

                fun drawSnowflake(scx: Float, scy: Float, sr: Float) {
                    val sStroke = w * 0.035f
                    for (i in 0 until 3) {
                        val angle = (i * 60f) * (PI / 180f).toFloat()
                        drawLine(
                            color = snowCyan,
                            start = Offset(scx - sr * cos(angle), scy - sr * sin(angle)),
                            end = Offset(scx + sr * cos(angle), scy + sr * sin(angle)),
                            strokeWidth = sStroke,
                            cap = StrokeCap.Round
                        )
                    }
                }

                drawSnowflake(scx = w * 0.34f, scy = h * 0.75f, sr = w * 0.065f)
                drawSnowflake(scx = w * 0.51f, scy = h * 0.83f, sr = w * 0.065f)
                drawSnowflake(scx = w * 0.68f, scy = h * 0.75f, sr = w * 0.065f)
            }

            else -> {
                drawCloud(cx = w / 2f, cy = h * 0.48f, scaleW = 1.25f, scaleH = 1.25f)
            }
        }
    }
}
