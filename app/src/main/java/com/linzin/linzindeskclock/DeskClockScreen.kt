package com.linzin.linzindeskclock

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.RenderEffect
import android.graphics.RuntimeShader
import android.location.Geocoder
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope

@Composable
fun DeskClockScreen(
    foldState: String = "--°",
    hasHingeSensor: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val window = activity?.window

    val currentLocationStr = stringResource(R.string.current_location)

    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var burnInProtectionEnabled by remember {
        mutableStateOf(prefs.getBoolean("pref_burn_in_enabled", true))
    }
    var showGestureTutorial by remember {
        mutableStateOf(!prefs.getBoolean("pref_dismiss_gesture_tutorial", false))
    }
    var showLayoutTip by remember {
        mutableStateOf(!prefs.getBoolean("pref_dismiss_layout_tip", false))
    }
    var isTutorialFromSettings by remember { mutableStateOf(false) }

    var is24Hour by remember { mutableStateOf(prefs.getBoolean("pref_24_hour", false)) }
    var showBattery by remember { mutableStateOf(prefs.getBoolean("pref_show_battery", true)) }
    var showCornerDate by remember { mutableStateOf(prefs.getBoolean("pref_show_corner_date", true)) }
    var showHingeAngle by remember { mutableStateOf(prefs.getBoolean("pref_show_hinge_angle", false)) }
    var manualBrightness by remember { mutableStateOf(prefs.getBoolean("pref_manual_brightness", false)) }
    var brightnessValue by remember { mutableFloatStateOf(prefs.getFloat("pref_brightness_val", 0.6f)) }
    var timeoutMillis by remember { mutableLongStateOf(prefs.getLong("pref_timeout_millis", -1L)) }
    var layoutMode by remember {
        val savedModeStr = prefs.getString("pref_layout_mode", ClockLayoutMode.SINGLE.name)
        val mode = try { ClockLayoutMode.valueOf(savedModeStr ?: ClockLayoutMode.SINGLE.name) } catch (e: Exception) { ClockLayoutMode.SINGLE }
        mutableStateOf(mode)
    }

    var weatherEnabled by remember { mutableStateOf(prefs.getBoolean("pref_weather_enabled", false)) }
    var isFahrenheit by remember { mutableStateOf(prefs.getBoolean("pref_is_fahrenheit", false)) }
    var weatherInfo by remember { mutableStateOf<WeatherInfo?>(null) }
    var isLocationUnavailable by remember { mutableStateOf(false) }

    var showCalendarEvents by remember {
        mutableStateOf(prefs.getBoolean("pref_show_calendar_events", false))
    }
    var calendarEventsList by remember { mutableStateOf<List<UpcomingCalendarEvent>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()

    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        showCalendarEvents = isGranted
        if (isGranted) {
            coroutineScope.launch { calendarEventsList = fetchUpcomingCalendarEvents(context) }
        }
    }

    var isUiLocked by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(is24Hour, showBattery, showCornerDate, showHingeAngle, manualBrightness, brightnessValue, timeoutMillis, layoutMode, weatherEnabled, isFahrenheit, burnInProtectionEnabled, showCalendarEvents) {
        prefs.edit()
            .putBoolean("pref_24_hour", is24Hour)
            .putBoolean("pref_show_battery", showBattery)
            .putBoolean("pref_show_corner_date", showCornerDate)
            .putBoolean("pref_show_hinge_angle", showHingeAngle)
            .putBoolean("pref_manual_brightness", manualBrightness)
            .putFloat("pref_brightness_val", brightnessValue)
            .putLong("pref_timeout_millis", timeoutMillis)
            .putString("pref_layout_mode", layoutMode.name)
            .putBoolean("pref_weather_enabled", weatherEnabled)
            .putBoolean("pref_is_fahrenheit", isFahrenheit)
            .putBoolean("pref_burn_in_enabled", burnInProtectionEnabled)
            .putBoolean("pref_show_calendar_events", showCalendarEvents) // <-- 補上這行
            .apply()
    }

    var showSettingsIcon by remember { mutableStateOf(false) }
    var isSettingsOpen by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var isBurnInProtectionActive by remember { mutableStateOf(false) }
    var isFakeSleeping by remember { mutableStateOf(false) }

    val batteryState = rememberBatteryState()

    val actualMainPageCount = 3
    val actualSidePageCount = 2
    val loopMultiplier = 10_000

    val savedMainPage = remember { prefs.getInt("pref_main_page", 0).coerceIn(0, actualMainPageCount - 1) }
    val savedSidePage = remember { prefs.getInt("pref_side_page", 0).coerceIn(0, actualSidePageCount - 1) }

    val mainPagerState = rememberPagerState(
        initialPage = (loopMultiplier / 2) * actualMainPageCount + savedMainPage,
        pageCount = { loopMultiplier * actualMainPageCount }
    )
    val sidePagerState = rememberPagerState(
        initialPage = (loopMultiplier / 2) * actualSidePageCount + savedSidePage,
        pageCount = { loopMultiplier * actualSidePageCount }
    )

    LaunchedEffect(mainPagerState.settledPage) {
        prefs.edit().putInt("pref_main_page", mainPagerState.settledPage % actualMainPageCount).apply()
    }
    LaunchedEffect(sidePagerState.settledPage) {
        prefs.edit().putInt("pref_side_page", sidePagerState.settledPage % actualSidePageCount).apply()
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        weatherEnabled = granted
    }

    // 天氣更新：僅在雙區塊模式且功能開啟時在背景輪詢以省電
    LaunchedEffect(weatherEnabled, layoutMode) {
        val isDualLayout = (layoutMode != ClockLayoutMode.SINGLE)

        if (weatherEnabled && isDualLayout) {
            val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

            if (hasFine || hasCoarse) {
                while (true) {
                    val location = findCurrentLocation(context)
                    isLocationUnavailable = location == null

                    val data = location?.let { loc ->
                        val detectedCity = withContext(Dispatchers.IO) {
                            try {
                                @Suppress("DEPRECATION")
                                Geocoder(context, Locale.getDefault())
                                    .getFromLocation(loc.latitude, loc.longitude, 1)
                                    ?.firstOrNull()
                                    ?.let { it.locality ?: it.subAdminArea ?: it.adminArea }
                            } catch (e: Exception) {
                                Log.w("DeskClockWeather", "Reverse geocoding failed", e)
                                null
                            }
                        } ?: currentLocationStr
                        fetchOpenMeteoWeather(loc.latitude, loc.longitude, detectedCity)
                    }

                    // 失敗時保留上一次的資料，並提早重試
                    if (data != null) weatherInfo = data
                    delay(if (data != null) 30 * 60 * 1000L else 2 * 60 * 1000L)
                }
            } else {
                locationPermissionLauncher.launch(
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                )
            }
        }
    }

    // 行程更新：僅在雙區塊模式且功能開啟時定時背景查詢
    LaunchedEffect(showCalendarEvents, layoutMode) {
        val isDualLayout = (layoutMode != ClockLayoutMode.SINGLE)

        if (showCalendarEvents && isDualLayout) {
            val hasCalPerm = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CALENDAR
            ) == PackageManager.PERMISSION_GRANTED

            if (hasCalPerm) {
                while (true) {
                    calendarEventsList = fetchUpcomingCalendarEvents(context)
                    delay(10 * 60 * 1000L)
                }
            } else {
                calendarEventsList = emptyList()
            }
        } else if (!isDualLayout) {
            // 單區塊模式暫停查詢
        } else {
            calendarEventsList = emptyList()
        }
    }

    SystemBarsController(hideSystemBars = true)

    LaunchedEffect(manualBrightness, brightnessValue, isFakeSleeping) {
        window?.let { w ->
            val layoutParams = w.attributes
            layoutParams.screenBrightness = when {
                isFakeSleeping -> 0.0f
                manualBrightness -> brightnessValue.coerceIn(0.01f, 1f)
                else -> WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            w.attributes = layoutParams
        }
    }

    LaunchedEffect(showSettingsIcon, lastInteractionTime) {
        if (showSettingsIcon && !isSettingsOpen) {
            delay(5000L)
            showSettingsIcon = false
        }
    }

    LaunchedEffect(lastInteractionTime, timeoutMillis) {
        isBurnInProtectionActive = false
        isFakeSleeping = false

        launch {
            delay(60_000L)
            isBurnInProtectionActive = true
        }

        val systemTimeout = try {
            Settings.System.getInt(
                context.contentResolver,
                Settings.System.SCREEN_OFF_TIMEOUT,
                15_000
            ).toLong()
        } catch (e: Exception) {
            15_000L
        }

        when {
            timeoutMillis < 0L -> {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            timeoutMillis >= systemTimeout -> {
                window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                val appWaitMillis = (timeoutMillis - systemTimeout).coerceAtLeast(0L)
                delay(appWaitMillis)
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
            else -> {
                window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                delay(timeoutMillis)
                isFakeSleeping = true
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.changes.any { it.pressed || it.previousPressed != it.pressed }) {
                            lastInteractionTime = System.currentTimeMillis()
                            if (isFakeSleeping) {
                                isFakeSleeping = false
                            } else {
                                showSettingsIcon = true
                            }
                        }
                    }
                }
            }
            .checkerboardBurnInProtection(isIdle = isBurnInProtectionActive && burnInProtectionEnabled)
    ) {
        when (layoutMode) {
            ClockLayoutMode.SINGLE -> {
                MainClockSection(
                    state = mainPagerState,
                    foldState = foldState,
                    is24Hour = is24Hour,
                    showCornerDate = showCornerDate,
                    showHingeAngle = showHingeAngle,
                    showBattery = showBattery,
                    batteryState = batteryState,
                    isUiLocked = isUiLocked,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ClockLayoutMode.DUAL_LEFT_LARGE -> {
                Row(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.70f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                    ) {
                        MainClockSection(
                            state = mainPagerState,
                            foldState = foldState,
                            is24Hour = is24Hour,
                            showCornerDate = showCornerDate,
                            showHingeAngle = showHingeAngle,
                            showBattery = showBattery,
                            batteryState = batteryState,
                            isUiLocked = isUiLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.30f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                    ) {
                        SideWidgetPager(
                            state = sidePagerState,
                            weatherEnabled = weatherEnabled,
                            weatherInfo = weatherInfo,
                isLocationUnavailable = isLocationUnavailable,
                            isFahrenheit = isFahrenheit,
                            isUiLocked = isUiLocked,
                            calendarEvents = calendarEventsList,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            ClockLayoutMode.DUAL_RIGHT_LARGE -> {
                Row(
                    modifier = Modifier.fillMaxSize().background(Color.Black),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.30f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 24.dp, bottomEnd = 24.dp))
                    ) {
                        SideWidgetPager(
                            state = sidePagerState,
                            weatherEnabled = weatherEnabled,
                            weatherInfo = weatherInfo,
                isLocationUnavailable = isLocationUnavailable,
                            isFahrenheit = isFahrenheit,
                            isUiLocked = isUiLocked,
                            calendarEvents = calendarEventsList,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(0.70f)
                            .background(Color.Black)
                            .clip(RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp, topEnd = 0.dp, bottomEnd = 0.dp))
                    ) {
                        MainClockSection(
                            state = mainPagerState,
                            foldState = foldState,
                            is24Hour = is24Hour,
                            showCornerDate = showCornerDate,
                            showHingeAngle = showHingeAngle,
                            showBattery = showBattery,
                            batteryState = batteryState,
                            isUiLocked = isUiLocked,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSettingsIcon && !isFakeSleeping,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(end = 20.dp, top = 14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = {
                        isUiLocked = !isUiLocked
                        lastInteractionTime = System.currentTimeMillis()
                    },
                    color = if (isUiLocked) Color(0xFFC62828).copy(alpha = 0.75f) else Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        MaterialLockIcon(
                            isLocked = isUiLocked,
                            modifier = Modifier.size(20.dp),
                            color = Color.White
                        )
                    }
                }

                Surface(
                    onClick = {
                        if (!isUiLocked) {
                            isSettingsOpen = true
                            showSettingsIcon = false
                        }
                    },
                    color = if (isUiLocked) Color(0xFF2C2C34).copy(alpha = 0.45f) else Color.Black.copy(alpha = 0.65f),
                    shape = CircleShape,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "⚙",
                            fontSize = 24.sp,
                            color = if (isUiLocked) Color(0xFF75757F) else Color.White
                        )
                    }
                }
            }
        }

        if (isFakeSleeping) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            )
        }

        if (isSettingsOpen) {
            ClockSettingsDialog(
                is24Hour = is24Hour,
                on24HourChange = { is24Hour = it },
                showBattery = showBattery,
                onShowBatteryChange = { showBattery = it },
                showCornerDate = showCornerDate,
                onShowCornerDateChange = { showCornerDate = it },
                showHingeAngle = showHingeAngle,
                onShowHingeAngleChange = { showHingeAngle = it },
                hasHingeSensor = hasHingeSensor,
                manualBrightness = manualBrightness,
                onManualBrightnessChange = { manualBrightness = it },
                brightnessValue = brightnessValue,
                onBrightnessValueChange = { brightnessValue = it },
                timeoutMillis = timeoutMillis,
                onTimeoutChange = { timeoutMillis = it },
                layoutMode = layoutMode,
                onLayoutModeChange = { layoutMode = it },
                weatherEnabled = weatherEnabled,
                onWeatherEnabledChange = { enabled ->
                    if (enabled) {
                        val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (hasFine || hasCoarse) {
                            weatherEnabled = true
                        } else {
                            locationPermissionLauncher.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                            )
                        }
                    } else {
                        weatherEnabled = false
                    }
                },
                isFahrenheit = isFahrenheit,
                onFahrenheitChange = { isFahrenheit = it },
                showCalendarEvents = showCalendarEvents,
                onShowCalendarEventsChange = { enabled ->
                    if (enabled) {
                        val hasCalPerm = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.READ_CALENDAR
                        ) == PackageManager.PERMISSION_GRANTED
                        if (hasCalPerm) {
                            showCalendarEvents = true
                            coroutineScope.launch { calendarEventsList = fetchUpcomingCalendarEvents(context) }
                        } else {
                            calendarPermissionLauncher.launch(Manifest.permission.READ_CALENDAR)
                        }
                    } else {
                        showCalendarEvents = false
                        calendarEventsList = emptyList()
                    }
                },
                burnInProtectionEnabled = burnInProtectionEnabled,
                onBurnInProtectionChange = { burnInProtectionEnabled = it },
                onOpenTutorial = {
                    isSettingsOpen = false
                    isTutorialFromSettings = true
                    showGestureTutorial = true
                },
                onDismiss = { isSettingsOpen = false }
            )
        }

        if (showGestureTutorial) {
            GestureTutorialOverlay(
                showDontShowAgain = !isTutorialFromSettings,
                onDismiss = { dontShowAgain ->
                    showGestureTutorial = false
                    if (!isTutorialFromSettings && dontShowAgain) {
                        prefs.edit().putBoolean("pref_dismiss_gesture_tutorial", true).apply()
                    }
                    isTutorialFromSettings = false
                }
            )
        } else if (showLayoutTip) {
            LayoutTipOverlay(
                onDismiss = { dontShowAgain ->
                    showLayoutTip = false
                    if (dontShowAgain) {
                        prefs.edit().putBoolean("pref_dismiss_layout_tip", true).apply()
                    }
                }
            )
        }
    }
}

@Composable
fun MainClockSection(
    state: PagerState,
    foldState: String,
    is24Hour: Boolean,
    showCornerDate: Boolean,
    showHingeAngle: Boolean,
    showBattery: Boolean,
    batteryState: BatteryState,
    isUiLocked: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        MainClockPager(
            state = state,
            foldState = foldState,
            is24Hour = is24Hour,
            showCornerDate = showCornerDate,
            showHingeAngle = showHingeAngle,
            isUiLocked = isUiLocked,
            modifier = Modifier.fillMaxSize()
        )

        if (showBattery) {
            BatteryIndicator(
                batteryLevel = batteryState.level,
                isCharging = batteryState.isCharging,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp, top = 16.dp)
            )
        }
    }
}

@Composable
fun MainClockPager(
    state: PagerState,
    foldState: String,
    is24Hour: Boolean,
    showCornerDate: Boolean,
    showHingeAngle: Boolean,
    isUiLocked: Boolean,
    modifier: Modifier = Modifier
) {
    VerticalPager(
        state = state,
        userScrollEnabled = !isUiLocked,
        modifier = modifier
    ) { page ->
        when (page % 3) {
            0 -> DigitalClockFace(
                foldState = foldState,
                is24Hour = is24Hour,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
            1 -> AnalogClockFace(
                foldState = foldState,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
            2 -> FlipClockFace(
                foldState = foldState,
                is24Hour = is24Hour,
                showCornerDate = showCornerDate,
                showHingeAngle = showHingeAngle,
                isUiLocked = isUiLocked
            )
        }
    }
}

@Composable
fun SideWidgetPager(
    state: PagerState,
    weatherEnabled: Boolean,
    weatherInfo: WeatherInfo?,
    isLocationUnavailable: Boolean,
    isFahrenheit: Boolean,
    isUiLocked: Boolean,
    calendarEvents: List<UpcomingCalendarEvent>,
    modifier: Modifier = Modifier
) {
    VerticalPager(
        state = state,
        userScrollEnabled = !isUiLocked,
        modifier = modifier
    ) { page ->
        when (page % 2) {
            0 -> SideDateWidget(calendarEvents = calendarEvents)
            1 -> SideWeatherWidget(
                weatherEnabled = weatherEnabled,
                weatherInfo = weatherInfo,
                isLocationUnavailable = isLocationUnavailable,
                isFahrenheit = isFahrenheit
            )
        }
    }
}

val isBurnInProtectionSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

fun Modifier.checkerboardBurnInProtection(isIdle: Boolean): Modifier = composed {
    if (!isBurnInProtectionSupported) {
        return@composed this
    }

    var phase by remember { mutableIntStateOf(0) }

    LaunchedEffect(isIdle) {
        if (isIdle) {
            while (true) {
                delay(300_000L)
                phase = (phase + 1) % 2
            }
        } else {
            phase = 0
        }
    }

    if (!isIdle) {
        return@composed this
    }

    val shader = remember {
        RuntimeShader(
            """
            uniform shader composable;
            uniform float phase;

            half4 main(float2 fragCoord) {
                float check = mod(floor(fragCoord.x) + floor(fragCoord.y) + phase, 2.0);
                if (check < 1.0) {
                    return composable.eval(fragCoord);
                }
                return half4(0.0, 0.0, 0.0, 1.0);
            }
            """.trimIndent()
        )
    }

    graphicsLayer {
        try {
            shader.setFloatUniform("phase", phase.toFloat())
            renderEffect = RenderEffect
                .createRuntimeShaderEffect(shader, "composable")
                .asComposeRenderEffect()
        } catch (e: Exception) {
            Log.e("DeskClockBurnIn", "Burn-in shader failed", e)
            renderEffect = null
        }
    }
}

@Composable
fun rememberBatteryState(): BatteryState {
    val context = LocalContext.current
    var batteryState by remember { mutableStateOf(BatteryState()) }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                intent?.let { batteryState = batteryStateFrom(it) }
            }
        }
        // 黏性廣播會同步回傳目前狀態，先套用以免第一個畫面顯示預設值
        context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?.let { batteryState = batteryStateFrom(it) }

        onDispose { context.unregisterReceiver(receiver) }
    }
    return batteryState
}

private fun batteryStateFrom(intent: Intent): BatteryState {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
    return BatteryState(
        level = if (level >= 0 && scale > 0) (level * 100 / scale) else 100,
        isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    )
}

@Composable
fun SystemBarsController(hideSystemBars: Boolean) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window ?: return
        DisposableEffect(hideSystemBars) {
            val controller = WindowCompat.getInsetsController(window, view)
            WindowCompat.setDecorFitsSystemWindows(window, !hideSystemBars)

            if (hideSystemBars) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }

            onDispose {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
