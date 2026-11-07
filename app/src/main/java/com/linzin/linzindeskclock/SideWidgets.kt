package com.linzin.linzindeskclock

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun SideDateWidget(
    calendarEvents: List<UpcomingCalendarEvent> = emptyList(),
    modifier: Modifier = Modifier
) {
    val currentTime = rememberCurrentTimeMillis()

    val calendar = remember(currentTime) {
        Calendar.getInstance().apply { timeInMillis = currentTime }
    }

    val dayOfWeekStr = remember(currentTime) {
        SimpleDateFormat("EEE", Locale.US).format(Date(currentTime))
    }
    val monthShortStr = remember(currentTime) {
        SimpleDateFormat("MMM", Locale.US).format(Date(currentTime))
    }
    val dayOfMonthStr = remember(currentTime) {
        SimpleDateFormat("d", Locale.US).format(Date(currentTime))
    }
    val monthFullStr = remember(currentTime) {
        SimpleDateFormat("MMMM", Locale.US).format(Date(currentTime)).uppercase(Locale.US)
    }

    val currentDay = calendar.get(Calendar.DAY_OF_MONTH)
    val maxDay = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstDayOfWeek = remember(currentTime) {
        val cal = Calendar.getInstance().apply {
            timeInMillis = currentTime
            set(Calendar.DAY_OF_MONTH, 1)
        }
        cal.get(Calendar.DAY_OF_WEEK) - 1
    }

    val daysOfWeek = listOf("S", "M", "T", "W", "T", "F", "S")

    val colorThu = Color(0xFF81C7D4)
    val colorAccent = Color(0xFF5DAC81)

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        val maxEventsToShow = when {
            maxHeight < 260.dp -> 1
            maxHeight < 355.dp -> 2
            else -> 3
        }
        val visibleEvents = calendarEvents.take(maxEventsToShow)
        val eventCount = visibleEvents.size

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    horizontal = 18.dp,
                    vertical = when {
                        eventCount >= 3 -> 8.dp
                        eventCount == 2 -> 10.dp
                        else -> 14.dp
                    }
                ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dayOfWeekStr,
                        color = colorThu,
                        fontSize = if (eventCount >= 3) 25.sp else 27.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = monthShortStr,
                        color = Color.White,
                        fontSize = if (eventCount >= 3) 25.sp else 27.sp,
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                        )
                    )
                }

                val digitSize = when {
                    visibleEvents.isEmpty() -> 72.sp
                    eventCount >= 3 -> 48.sp
                    eventCount == 2 -> 54.sp
                    else -> 64.sp
                }

                Text(
                    text = dayOfMonthStr,
                    color = Color.White,
                    fontSize = digitSize,
                    lineHeight = digitSize,
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                    ),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            if (visibleEvents.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    visibleEvents.forEach { event ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(7.dp))
                                .background(Color(0xFF1E1E24))
                                .padding(
                                    horizontal = 8.dp,
                                    vertical = if (eventCount >= 3) 2.5.dp else 3.5.dp
                                ),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(4.5.dp)
                                        .clip(CircleShape)
                                        .background(colorAccent)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = event.timeLabel,
                                    color = colorThu,
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    style = TextStyle(
                                        platformStyle = PlatformTextStyle(includeFontPadding = false)
                                    )
                                )
                            }

                            Text(
                                text = event.title,
                                color = Color.White,
                                fontSize = 10.5.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                style = TextStyle(
                                    platformStyle = PlatformTextStyle(includeFontPadding = false)
                                )
                            )
                        }
                    }
                }
            }

            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = monthFullStr,
                    color = colorAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(bottom = 2.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    daysOfWeek.forEach { dayName ->
                        Text(
                            text = dayName,
                            color = Color(0xFF8E8E93),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(1.dp))

                val totalCells = ((firstDayOfWeek + maxDay + 6) / 7) * 7
                for (row in 0 until (totalCells / 7)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 0.6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        for (col in 0 until 7) {
                            val cellIndex = row * 7 + col
                            val dayNum = cellIndex - firstDayOfWeek + 1

                            if (dayNum in 1..maxDay) {
                                val isToday = (dayNum == currentDay)
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .then(
                                            if (isToday) Modifier.border(
                                                width = 1.2.dp,
                                                color = colorAccent,
                                                shape = CircleShape
                                            ) else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "$dayNum",
                                        color = if (isToday) colorAccent else Color.White,
                                        fontSize = 8.5.sp,
                                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                        textAlign = TextAlign.Center,
                                        style = TextStyle(
                                            platformStyle = PlatformTextStyle(includeFontPadding = false)
                                        )
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SideWeatherWidget(
    weatherEnabled: Boolean,
    weatherInfo: WeatherInfo?,
    isLocationUnavailable: Boolean,
    isFahrenheit: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        if (!weatherEnabled || weatherInfo == null) {
            val showNoLocation = weatherEnabled && isLocationUnavailable
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(text = "⛅", fontSize = 38.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = stringResource(
                        if (showNoLocation) R.string.weather_no_location_title else R.string.weather_disabled_title
                    ),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(
                        if (showNoLocation) R.string.weather_no_location_desc else R.string.weather_disabled_desc
                    ),
                    color = Color(0xFF8E8E93),
                    fontSize = 11.sp,
                    lineHeight = 16.sp,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp, bottom = 2.dp)
                ) {
                    LocationPinIcon(
                        modifier = Modifier.size(width = 12.dp, height = 16.dp),
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = weatherInfo.cityName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.70f)
                ) {
                    val availableWidth = maxWidth
                    val availableHeight = maxHeight
                    val iconSize = min(availableHeight * 0.92f, availableWidth * 0.58f).coerceIn(100.dp, 138.dp)

                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = formatTemp(weatherInfo.currentTempC, isFahrenheit),
                            color = Color.White,
                            fontSize = 50.sp,
                            lineHeight = 50.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.offset(y = 12.dp)
                        )

                        MaterialWeatherIcon(
                            weatherCode = weatherInfo.currentWeatherCode,
                            modifier = Modifier.size(iconSize)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.30f),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    weatherInfo.hourlyList.take(4).forEachIndexed { index, hourly ->
                        if (index > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxHeight(0.70f)
                                    .width(1.dp)
                                    .background(Color(0xFF2C2C34))
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceEvenly
                        ) {
                            Text(
                                text = formatTemp(hourly.tempC, isFahrenheit),
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            MaterialWeatherIcon(
                                weatherCode = hourly.weatherCode,
                                modifier = Modifier.size(26.dp)
                            )

                            Text(
                                text = hourly.timeLabel,
                                color = Color(0xFFA0A0AA),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
