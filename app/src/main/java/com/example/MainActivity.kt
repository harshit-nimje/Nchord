package com.example

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.DisplayRefreshPolicy
import com.example.ui.NothingChordApp
import com.example.ui.NothingChordViewModel
import com.example.ui.components.LocalLowPowerAnimationMode
import com.example.ui.components.LocalPeakDisplayRefreshHz
import com.example.ui.theme.NothingChordTheme
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class DisplayModeCandidate(
    val modeId: Int,
    val physicalWidth: Int,
    val physicalHeight: Int,
    val refreshRate: Float
)

/**
 * Pure, resolution-safe display mode selector:
 * - Filters strictly to [activeWidth] x [activeHeight] so changing refresh policy NEVER
 *   inadvertently switches QHD+ <-> FHD+ panel resolution.
 * - `AUTO` returns `0` (delegating adaptive LTPO scaling to the OS) unless OS Power Save is
 *   active, where it caps to `<= 60.5Hz` at the active resolution.
 * - `HIGH` locks the highest available refresh rate matching the active physical resolution.
 * - `BATTERY_SAVER` selects the highest refresh rate `<= 60.5Hz` (typically 60Hz) matching the
 *   active physical resolution, or falls back to the lowest available rate at that resolution.
 */
internal fun selectPreferredDisplayModeId(
    policy: DisplayRefreshPolicy,
    isOsPowerSaveMode: Boolean,
    activeWidth: Int,
    activeHeight: Int,
    modes: List<DisplayModeCandidate>
): Int {
    if (modes.isEmpty() || activeWidth <= 0 || activeHeight <= 0) return 0
    val sameResModes = modes.filter {
        it.physicalWidth == activeWidth && it.physicalHeight == activeHeight
    }
    if (sameResModes.isEmpty()) return 0

    val effectivePolicy = if (policy == DisplayRefreshPolicy.AUTO && isOsPowerSaveMode) {
        DisplayRefreshPolicy.BATTERY_SAVER
    } else {
        policy
    }

    return when (effectivePolicy) {
        DisplayRefreshPolicy.AUTO -> 0
        DisplayRefreshPolicy.HIGH -> {
            sameResModes.maxByOrNull { it.refreshRate }?.modeId ?: 0
        }
        DisplayRefreshPolicy.BATTERY_SAVER -> {
            val capped60 = sameResModes
                .filter { it.refreshRate <= 60.5f }
                .maxByOrNull { it.refreshRate }
            (capped60 ?: sameResModes.minByOrNull { it.refreshRate })?.modeId ?: 0
        }
    }
}

/**
 * Computes the explicit target SurfaceFlinger / Window refresh rate hint in Hz:
 * - `AUTO` (normal power) -> `0f` (allow hardware LTPO controller to scale dynamically up to peak Hz)
 * - `HIGH` -> peak refresh rate at the active resolution (e.g., `144f` or `120f`)
 * - `BATTERY_SAVER` (or `AUTO` during OS Power Save) -> `60f` (or closest <= 60.5Hz mode)
 */
internal fun selectPreferredRefreshRateHz(
    policy: DisplayRefreshPolicy,
    isOsPowerSaveMode: Boolean,
    activeWidth: Int,
    activeHeight: Int,
    modes: List<DisplayModeCandidate>
): Float {
    if (modes.isEmpty() || activeWidth <= 0 || activeHeight <= 0) return 0f
    val sameResModes = modes.filter {
        it.physicalWidth == activeWidth && it.physicalHeight == activeHeight
    }
    if (sameResModes.isEmpty()) return 0f

    val effectivePolicy = if (policy == DisplayRefreshPolicy.AUTO && isOsPowerSaveMode) {
        DisplayRefreshPolicy.BATTERY_SAVER
    } else {
        policy
    }

    return when (effectivePolicy) {
        DisplayRefreshPolicy.AUTO -> 0f
        DisplayRefreshPolicy.HIGH -> {
            sameResModes.maxOfOrNull { it.refreshRate } ?: 0f
        }
        DisplayRefreshPolicy.BATTERY_SAVER -> {
            val capped60 = sameResModes
                .filter { it.refreshRate <= 60.5f }
                .maxOfOrNull { it.refreshRate }
            capped60 ?: (sameResModes.minOfOrNull { it.refreshRate } ?: 60f)
        }
    }
}

class MainActivity : ComponentActivity() {

    private val viewModel: NothingChordViewModel by viewModels {
        NothingChordViewModel.provideFactory(applicationContext)
    }

    private var latestPolicy: DisplayRefreshPolicy = DisplayRefreshPolicy.AUTO
    private var latestOsPowerSave: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        com.example.data.remote.NetworkModule.initializeRuntimeCaches(applicationContext)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )

        if (savedInstanceState == null) {
            consumeOAuthIntentIfPresent(intent)
        }

        setContent {
            val glyphSettings by viewModel.glyphSettings.collectAsStateWithLifecycle()
            val powerManager = remember {
                getSystemService(Context.POWER_SERVICE) as? PowerManager
            }
            var isOsPowerSave by remember {
                mutableStateOf(powerManager?.isPowerSaveMode == true)
            }

            DisposableEffect(powerManager) {
                val receiver = object : BroadcastReceiver() {
                    override fun onReceive(context: Context?, intent: Intent?) {
                        isOsPowerSave = powerManager?.isPowerSaveMode == true
                    }
                }
                val filter = IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
                } else {
                    registerReceiver(receiver, filter)
                }
                onDispose {
                    try {
                        unregisterReceiver(receiver)
                    } catch (_: IllegalArgumentException) {
                    }
                }
            }

            var peakDisplayHz by remember { mutableIntStateOf(detectPeakDisplayRefreshHz()) }

            LaunchedEffect(glyphSettings.displayRefreshPolicy, isOsPowerSave) {
                latestPolicy = glyphSettings.displayRefreshPolicy
                latestOsPowerSave = isOsPowerSave
                peakDisplayHz = detectPeakDisplayRefreshHz()
                applyWindowDisplayRefreshPolicy(
                    policy = glyphSettings.displayRefreshPolicy,
                    isOsPowerSaveMode = isOsPowerSave
                )
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val notificationPermLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                    contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
                    onResult = {}
                )
                LaunchedEffect(Unit) {
                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                        this@MainActivity,
                        android.Manifest.permission.POST_NOTIFICATIONS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    if (!granted) {
                        notificationPermLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                }
            }

            LaunchedEffect(glyphSettings.oledDarkMode) {
                if (glyphSettings.oledDarkMode) {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
                        navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    )
                } else {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        ),
                        navigationBarStyle = SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT
                        )
                    )
                }
            }

            val lowPowerAnimations = glyphSettings.displayRefreshPolicy == DisplayRefreshPolicy.BATTERY_SAVER ||
                (glyphSettings.displayRefreshPolicy == DisplayRefreshPolicy.AUTO && isOsPowerSave)

            CompositionLocalProvider(
                LocalLowPowerAnimationMode provides lowPowerAnimations,
                LocalPeakDisplayRefreshHz provides peakDisplayHz
            ) {
                NothingChordTheme(darkTheme = glyphSettings.oledDarkMode) {
                    NothingChordApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyWindowDisplayRefreshPolicy(
            policy = latestPolicy,
            isOsPowerSaveMode = latestOsPowerSave
        )
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyWindowDisplayRefreshPolicy(
                policy = latestPolicy,
                isOsPowerSaveMode = latestOsPowerSave
            )
        }
    }

    private fun detectPeakDisplayRefreshHz(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return 60
        val activeDisplay = display ?: return 120
        val currentMode = activeDisplay.mode ?: return 120
        val peak = activeDisplay.supportedModes
            .filter {
                it.physicalWidth == currentMode.physicalWidth &&
                    it.physicalHeight == currentMode.physicalHeight
            }
            .maxOfOrNull { it.refreshRate }
            ?: activeDisplay.supportedModes.maxOfOrNull { it.refreshRate }
            ?: currentMode.refreshRate
        return peak.roundToInt().coerceAtLeast(60)
    }

    private fun applyWindowDisplayRefreshPolicy(
        policy: DisplayRefreshPolicy,
        isOsPowerSaveMode: Boolean
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val activeDisplay = display ?: return
        val currentMode = activeDisplay.mode ?: return
        val candidates = activeDisplay.supportedModes.map { mode ->
            DisplayModeCandidate(
                modeId = mode.modeId,
                physicalWidth = mode.physicalWidth,
                physicalHeight = mode.physicalHeight,
                refreshRate = mode.refreshRate
            )
        }
        val targetModeId = selectPreferredDisplayModeId(
            policy = policy,
            isOsPowerSaveMode = isOsPowerSaveMode,
            activeWidth = currentMode.physicalWidth,
            activeHeight = currentMode.physicalHeight,
            modes = candidates
        )
        val targetHz = selectPreferredRefreshRateHz(
            policy = policy,
            isOsPowerSaveMode = isOsPowerSaveMode,
            activeWidth = currentMode.physicalWidth,
            activeHeight = currentMode.physicalHeight,
            modes = candidates
        )

        val lp = window.attributes
        val modeChanged = lp.preferredDisplayModeId != targetModeId
        val hzChanged = abs(lp.preferredRefreshRate - targetHz) > 0.1f
        if (modeChanged || hzChanged) {
            lp.preferredDisplayModeId = targetModeId
            lp.preferredRefreshRate = targetHz
            window.attributes = lp
        }

        val effectivePolicy = if (policy == DisplayRefreshPolicy.AUTO && isOsPowerSaveMode) {
            DisplayRefreshPolicy.BATTERY_SAVER
        } else {
            policy
        }

        val decor = window.decorView
        // Android 15+ (API 35+): Vote exact target Hz (e.g. 144.0f) or category on the root DecorView
        // so LTPO panels lock VSYNC phase without 60<->144Hz gear-shift stutter.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            decor.requestedFrameRate = when (effectivePolicy) {
                DisplayRefreshPolicy.HIGH -> {
                    if (targetHz > 0f) targetHz else View.REQUESTED_FRAME_RATE_CATEGORY_HIGH
                }
                DisplayRefreshPolicy.BATTERY_SAVER -> {
                    if (targetHz > 0f) targetHz else View.REQUESTED_FRAME_RATE_CATEGORY_LOW
                }
                DisplayRefreshPolicy.AUTO -> View.REQUESTED_FRAME_RATE_CATEGORY_DEFAULT
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeOAuthIntentIfPresent(intent)
    }

    private fun consumeOAuthIntentIfPresent(targetIntent: Intent?) {
        val uri = targetIntent?.data ?: return
        if (uri.scheme == "nothingchord" && uri.host == "callback") {
            targetIntent.data = null
            setIntent(targetIntent)
            viewModel.handleIncomingOAuthIntent(uri)
        }
    }
}
