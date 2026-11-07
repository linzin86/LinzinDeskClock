package com.linzin.linzindeskclock

import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Color as AndroidGraphicsColor
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val DeskClockColorScheme = darkColorScheme(
    primary = Color(0xFF81C7D4),
    onPrimary = Color(0xFF041E49),
    primaryContainer = Color(0xFF1E3A45),
    onPrimaryContainer = Color(0xFF81C7D4),
    secondary = Color(0xFF5DAC81),
    onSecondary = Color(0xFF041E49),
    surface = Color(0xFF1E1E22),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2C2C34),
    onSurfaceVariant = Color(0xFFA0A0AA),
    outline = Color(0xFF5A5A62)
)

/**
 * 非同步傳送 Shell 指令至系統（如呼叫 cmd device_state）
 */
fun executeShellCommand(command: String) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val process = ProcessBuilder("sh", "-c", command).start()
            process.waitFor()
            Log.d("FoldDeskClock", "Executed: $command")
        } catch (e: Exception) {
            Log.e("FoldDeskClock", "Failed: $command", e)
        }
    }
}

class MainActivity : ComponentActivity(), SensorEventListener {

    // 各品牌外螢幕狀態 ID 對應
    private val targetStateId: String by lazy {
        val brand = Build.MANUFACTURER.lowercase()
        when {
            brand.contains("honor") -> "4"
            brand.contains("google") -> "3"
            brand.contains("samsung") -> "1"
            brand.contains("oppo") || brand.contains("oneplus") -> "1"
            brand.contains("vivo") -> "1"
            brand.contains("xiaomi") -> "1"
            else -> "3"
        }
    }

    private val isHonorDevice: Boolean by lazy {
        Build.MANUFACTURER.lowercase().contains("honor")
    }

    /**
     * 判定是否為「開啟了三大虛擬導航按鍵」的三星裝置
     * 用於精準分流導覽列重置排程，避免全螢幕手勢機型產生黑塊過渡
     */
    private val isSamsungWithButtonNav: Boolean by lazy {
        val isSamsung = Build.MANUFACTURER.lowercase().contains("samsung")
        if (!isSamsung) return@lazy false

        try {
            val navMode = Settings.Secure.getInt(contentResolver, "navigation_mode", 0)
            val samsungGesture = Settings.Global.getInt(contentResolver, "navigation_bar_gesture_while_hidden", 0)
            navMode == 0 && samsungGesture == 0
        } catch (e: Exception) {
            true
        }
    }

    /**
     * 安全釋放全域覆寫鎖定 (Reset Device State)
     * 針對 HONOR (MagicOS) 採用雙重間隔補發機制以確保退出鎖死
     */
    private fun safelyResetDeviceState() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val p1 = ProcessBuilder("sh", "-c", "cmd device_state state reset").start()
                p1.waitFor()
                Log.d("FoldDeskClock", "Executed: cmd device_state state reset")

                if (isHonorDevice) {
                    delay(120L)
                    val p2 = ProcessBuilder("sh", "-c", "cmd device_state state reset").start()
                    p2.waitFor()
                }
            } catch (e: Exception) {
                Log.e("FoldDeskClock", "Failed to reset device state", e)
            }
        }
    }

    /**
     * 導覽列收合排程：
     * 僅針對「啟用三大虛擬導航按鍵的三星摺疊機」重置 Insets 狀態機以壓制幽靈按鍵；
     * 其他機型 (含手勢導航三星) 直接全螢幕隱藏，保證零閃爍、無過渡黑塊。
     */
    private fun triggerImmersiveCleanUp() {
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        if (!isSamsungWithButtonNav) {
            controller.hide(WindowInsetsCompat.Type.systemBars())
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            window.navigationBarColor = AndroidGraphicsColor.BLACK
            window.statusBarColor = AndroidGraphicsColor.BLACK

            delay(350L)
            controller.show(WindowInsetsCompat.Type.navigationBars())
            window.decorView.requestApplyInsets()

            delay(150L)
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private var hasHingeSensor = false
    private var lastRecordedHingeAngle: Float = -1f
    private var isNonFoldableBypassed = false

    private var foldState by mutableStateOf("--°")
    private var showClockScreen by mutableStateOf(false)
    private lateinit var sensorManager: SensorManager
    private var hingeAngleSensor: Sensor? = null

    private var isRearDisplayActive = false

    private fun isPermanentBypass(): Boolean =
        !hasHingeSensor &&
            getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).getInt(PREF_BYPASS_COUNT, 0) >= PERMANENT_BYPASS_COUNT

    private fun isTallAspectRatio(): Boolean {
        val metrics = resources.displayMetrics
        val maxDim = maxOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        val minDim = minOf(metrics.widthPixels, metrics.heightPixels).toFloat()
        return (maxDim / minDim) >= 2.05f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 鎖死視窗底色為純黑，防止螢幕切換/旋轉瞬間漏出系統白底
        window.setBackgroundDrawableResource(android.R.color.black)
        window.statusBarColor = AndroidGraphicsColor.BLACK
        window.navigationBarColor = AndroidGraphicsColor.BLACK

        // 允許畫面延伸至挖孔/瀏海區域
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }

        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        hingeAngleSensor = sensorManager.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE)
        hasHingeSensor = (hingeAngleSensor != null)

        if (!hasHingeSensor) {
            foldState = "NO_HINGE"
            if (isPermanentBypass()) {
                isNonFoldableBypassed = true
                showClockScreen = true
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }

        setContent {
            MaterialTheme(colorScheme = DeskClockColorScheme) {
                BackHandler(enabled = showClockScreen) {
                    // 若是非摺疊機且已達成永久放行門檻，按返回鍵直接退出整個 App
                    if (isPermanentBypass()) {
                        finish()
                    } else {
                        // 一般大摺疊機：恢復原本狀態並退回引導頁面
                        isRearDisplayActive = false
                        isNonFoldableBypassed = false
                        showClockScreen = false
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                        safelyResetDeviceState()
                    }
                }

                if (showClockScreen) {
                    DeskClockScreen(
                        foldState = foldState,
                        hasHingeSensor = hasHingeSensor
                    )
                } else {
                    InitialScreen(
                        hasHingeSensor = hasHingeSensor,
                        isClamshell = (hasHingeSensor && isTallAspectRatio() && lastRecordedHingeAngle >= 160f),
                        onEnterClockDirectly = {
                            isNonFoldableBypassed = true
                            showClockScreen = true
                            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    )
                }
            }
        }
    }


    override fun onResume() {
        super.onResume()
        hingeAngleSensor?.let {
            sensorManager.unregisterListener(this)
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // 從背景切回 APP 時，若正在時鐘介面，重新恢復全螢幕沉浸模式
        if (showClockScreen) {
            setImmersiveMode(window, true)
            if (isPermanentBypass()) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 只要視窗重新拿到焦點 (從桌面切回、手勢動畫結束等)，且處於時鐘畫面，就強制壓下狀態列
        if (hasFocus && showClockScreen) {
            setImmersiveMode(window, true)
        }
    }

    override fun onPause() {
        super.onPause()
        // 若處於外螢幕時鐘畫面，保留感應器監聽以確保能感應後續展開動作
        if (!showClockScreen) {
            sensorManager.unregisterListener(this)
        }
    }

    override fun onStop() {
        super.onStop()
        sensorManager.unregisterListener(this)

        // 如果是已永久放行的非摺疊機，按 Home 鍵退回背景時，不要重置 showClockScreen
        if (isPermanentBypass()) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            setImmersiveMode(window, false)
            return
        }

        // 一般大摺疊機：離開 APP 時釋放全域覆寫鎖定並重置狀態
        if (isRearDisplayActive || showClockScreen) {
            isRearDisplayActive = false
            showClockScreen = false
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            setImmersiveMode(window, false)
            safelyResetDeviceState()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        setImmersiveMode(window, false)
        safelyResetDeviceState()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_HINGE_ANGLE) {
            val currentAngle = event.values[0]
            lastRecordedHingeAngle = currentAngle
            foldState = "${currentAngle.toInt()}°"

            if (isNonFoldableBypassed) {
                return
            }

            // 完全展開 (> 150°)：無條件退出外螢幕並回歸內螢幕
            if (currentAngle > 150f) {
                if (showClockScreen || isRearDisplayActive) {
                    isRearDisplayActive = false
                    showClockScreen = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setImmersiveMode(window, false)
                    safelyResetDeviceState()
                }
                return
            }

            // 完全閉合 (<= 5°)：退出時鐘，恢復預設外螢幕狀態
            if (currentAngle <= 5f) {
                if (showClockScreen || isRearDisplayActive) {
                    isRearDisplayActive = false
                    showClockScreen = false
                    window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    setImmersiveMode(window, false)
                    safelyResetDeviceState()
                }
                return
            }

            // 半摺角度 (30° ~ 120°)：觸發外螢幕橫向時鐘
            if (currentAngle in 30f..120f) {
                if (!showClockScreen) {
                    showClockScreen = true
                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE

                    isRearDisplayActive = true
                    executeShellCommand("cmd device_state state $targetStateId")

                    triggerImmersiveCleanUp()
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        fun setImmersiveMode(window: Window, enable: Boolean) {
            WindowCompat.setDecorFitsSystemWindows(window, !enable)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (enable) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
