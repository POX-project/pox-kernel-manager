package com.pox.kernelmanager.data.learning

import android.content.Context
import android.content.SharedPreferences
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.HealthCalibrationMode
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.repo.TuningRepository
import com.pox.kernelmanager.data.root.KernelNodes
import com.pox.kernelmanager.data.root.ShellUtils
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

data class LearningStats(
    val isAutoEnabled: Boolean = true,
    val totalCycles: Int = 0,
    val averageLoadTempC: Float = 38.5f,
    val efficiencyIndex: Int = 94,
    val batteryDegradationFactor: Float = 0.98f,
    val siliconOppOffsetMhz: Int = 0,
    val batteryEsrMohm: Int = 145,
    val dcirMohm: Int = 195,
    val maxSafeOppKhz: Int = 1768000,
    val thermalRiseRateMcs: Int = 980,
    val peakVoltageSagMv: Int = 240,
    val lastCalibratedTimestamp: String = "Never",
    val statusSummary: String = "Active • Monitoring Silicon & Discharge Profile"
)

data class CalibrationProgress(
    val phase: Int,
    val totalPhases: Int = 5,
    val phaseName: String,
    val progress: Float,
    val statusMessage: String,
    val liveVoltageMv: Int,
    val liveCurrentMa: Int,
    val liveTempC: Float
)

sealed class PreflightResult {
    object Ok : PreflightResult()
    data class Failed(val reason: String) : PreflightResult()
}

object AdaptiveLearningManager {
    private const val PREFS_NAME = "pox_adaptive_learning"
    private const val KEY_AUTO_ENABLED = "auto_learning_enabled"
    private const val KEY_TOTAL_CYCLES = "total_learning_cycles"
    private const val KEY_AVG_TEMP = "average_load_temp"
    private const val KEY_EFFICIENCY = "efficiency_index"
    private const val KEY_DEGRADATION = "battery_degradation_factor"
    private const val KEY_OPP_OFFSET = "silicon_opp_offset"
    private const val KEY_BATTERY_ESR = "battery_esr_mohm"
    private const val KEY_DCIR = "dcir_mohm"
    private const val KEY_MAX_SAFE_OPP = "max_safe_opp_khz"
    private const val KEY_THERMAL_RISE = "thermal_rise_rate_mcs"
    private const val KEY_PEAK_SAG = "peak_voltage_sag_mv"
    private const val KEY_LAST_CALIBRATED = "last_calibrated_time"

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val isCalibrating = AtomicBoolean(false)

    fun init(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            syncWithKernelTelemetry()
            val esr = prefs.getInt(KEY_BATTERY_ESR, 0)
            val oppCap = prefs.getInt(KEY_MAX_SAFE_OPP, 0)
            val gradient = prefs.getInt(KEY_THERMAL_RISE, 0)
            if (esr > 0) TuningRepository.setBatteryEsr(esr)
            if (oppCap > 0) TuningRepository.setOppSagCeiling(oppCap)
            if (gradient > 0) TuningRepository.setThermalGradient(gradient)
        }
    }

    fun isCalibrationRunning(): Boolean = isCalibrating.get()

    fun getStats(): LearningStats {
        return LearningStats(
            isAutoEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, true),
            totalCycles = prefs.getInt(KEY_TOTAL_CYCLES, 0),
            averageLoadTempC = prefs.getFloat(KEY_AVG_TEMP, 38.5f),
            efficiencyIndex = prefs.getInt(KEY_EFFICIENCY, 94),
            batteryDegradationFactor = prefs.getFloat(KEY_DEGRADATION, 0.98f),
            siliconOppOffsetMhz = prefs.getInt(KEY_OPP_OFFSET, 0),
            batteryEsrMohm = prefs.getInt(KEY_BATTERY_ESR, 145),
            dcirMohm = prefs.getInt(KEY_DCIR, 195),
            maxSafeOppKhz = prefs.getInt(KEY_MAX_SAFE_OPP, 1768000),
            thermalRiseRateMcs = prefs.getInt(KEY_THERMAL_RISE, 980),
            peakVoltageSagMv = prefs.getInt(KEY_PEAK_SAG, 240),
            lastCalibratedTimestamp = prefs.getString(KEY_LAST_CALIBRATED, "Never") ?: "Never",
            statusSummary = generateSummary()
        )
    }

    fun setAutoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    private fun generateSummary(): String {
        val total = prefs.getInt(KEY_TOTAL_CYCLES, 0)
        val dcir = prefs.getInt(KEY_DCIR, 195)
        val opp = prefs.getInt(KEY_MAX_SAFE_OPP, 1768000) / 1000
        val last = prefs.getString(KEY_LAST_CALIBRATED, null)

        return if (last == null || total == 0) {
            "Ready • Tap Calibrate to run 5-phase physical stress protocol"
        } else {
            "Calibrated ($last) • DCIR: ${dcir}mΩ • Safe OPP: ${opp}MHz • $total profile runs"
        }
    }

    suspend fun syncWithKernelTelemetry() = withContext(Dispatchers.IO) {
        if (!ShellUtils.isRootAvailable()) return@withContext
        val output = Shell.cmd("cat /proc/perfmgr/opp_sag_status 2>/dev/null").exec().out.joinToString("\n")
        if (output.isNotBlank()) {
            parseOppSagOutput(output)
        }
    }

    private fun parseOppSagOutput(output: String) {
        val lines = output.lines()
        var updated = false
        val editor = prefs.edit()

        for (line in lines) {
            val parts = line.split("=")
            if (parts.size != 2) continue
            val key = parts[0].trim()
            val value = parts[1].trim()

            when (key) {
                "dcir_mohm" -> value.toIntOrNull()?.let {
                    editor.putInt(KEY_DCIR, it)
                    updated = true
                }
                "safe_opp_khz" -> value.toIntOrNull()?.let {
                    editor.putInt(KEY_MAX_SAFE_OPP, it)
                    updated = true
                }
                "thermal_rate_mcs" -> value.toIntOrNull()?.let {
                    editor.putInt(KEY_THERMAL_RISE, it)
                    updated = true
                }
            }
        }
        if (updated) editor.apply()
    }

    fun preflightCheck(): PreflightResult {
        if (!ShellUtils.rootKnownGranted() && Shell.isAppGrantedRoot() != true) {
            return PreflightResult.Failed("Root access is required to run hardware calibration.")
        }
        if (isCalibrating.get()) {
            return PreflightResult.Failed("A calibration run is already in progress.")
        }
        return PreflightResult.Ok
    }

    suspend fun runPhysicalCalibration(
        onProgress: (CalibrationProgress) -> Unit
    ): LearningStats = withContext(Dispatchers.IO) {
        if (!isCalibrating.compareAndSet(false, true)) {
            throw IllegalStateException("Calibration already running")
        }

        try {
            // PHASE 1: Open Circuit Voltage (Voc) & DCIR Pulse
            val (vOc, iIdle, t0) = sampleTelemetry()
            onProgress(CalibrationProgress(1, 5, "Voc & DCIR Pulse", 0.05f, "Stabilizing open-circuit baseline...", vOc, iIdle, t0))
            delay(1000)

            var vPulseMin = vOc
            var iPulseMax = iIdle
            var pulseTemp = t0

            val stressJob = CoroutineScope(Dispatchers.Default).launch {
                val end = System.currentTimeMillis() + 2500
                while (System.currentTimeMillis() < end) {
                    var x = 1.000001
                    for (i in 0 until 50_000) { x = x * 1.0000001 + 0.000001 }
                }
            }

            for (step in 0 until 12) {
                delay(200)
                val (v, cur, t) = sampleTelemetry()
                vPulseMin = min(vPulseMin, v)
                iPulseMax = max(iPulseMax, cur)
                pulseTemp = max(pulseTemp, t)
                val prog = 0.05f + (step / 12f) * 0.15f
                onProgress(CalibrationProgress(1, 5, "Voc & DCIR Pulse", prog, "Measuring internal impedance (DCIR)...", v, cur, t))
            }
            stressJob.join()

            val deltaI1 = max(100, iPulseMax - iIdle)
            val deltaV1 = max(10, vOc - vPulseMin)
            val rOhm = ((deltaV1.toLong() * 1000L) / deltaI1).toInt().coerceIn(30, 1200)

            // PHASE 2: Stepped OPP Frequency Sag Ladder (Exynos 7904)
            val oppSteps = listOf(832000, 1144000, 1352000, 1586000, 1768000)
            var safeOppCeiling = 1768000
            var maxMeasuredSag = deltaV1

            for ((stepIdx, freq) in oppSteps.withIndex()) {
                TuningRepository.setOppSagCeiling(freq)
                var stepMinV = 5000
                var stepMaxI = 0
                var stepT = pulseTemp

                for (s in 0 until 16) {
                    val (v, cur, t) = sampleTelemetry()
                    stepMinV = min(stepMinV, v)
                    stepMaxI = max(stepMaxI, cur)
                    stepT = max(stepT, t)
                    val prog = 0.22f + ((stepIdx * 16 + s).toFloat() / (oppSteps.size * 16f)) * 0.40f
                    val mhz = freq / 1000
                    onProgress(CalibrationProgress(2, 5, "OPP Voltage Sag Ladder", prog, "Profiling OPP at ${mhz}MHz...", v, cur, t))
                    delay(80)
                }

                val sagAtStep = max(0, vOc - stepMinV)
                maxMeasuredSag = max(maxMeasuredSag, sagAtStep)

                if (stepMinV < 3350) {
                    safeOppCeiling = if (stepIdx > 0) oppSteps[stepIdx - 1] else freq
                    break
                }
            }

            // PHASE 3: Thermal Gradient Derating Rate
            val tStart = pulseTemp
            val timeStartMs = System.currentTimeMillis()
            var tPeak = tStart

            for (s in 0 until 16) {
                delay(250)
                val (v, cur, t) = sampleTelemetry()
                tPeak = max(tPeak, t)
                val prog = 0.62f + (s / 16f) * 0.18f
                onProgress(CalibrationProgress(3, 5, "Thermal Slope Derating", prog, "Calculating thermal dissipation gradient...", v, cur, t))
            }
            val elapsedSec = max(1.0f, (System.currentTimeMillis() - timeStartMs) / 1000f)
            val thermalRiseMcs = ((max(0.1f, tPeak - tStart) * 1000f) / elapsedSec).toInt().coerceIn(200, 3500)

            // PHASE 4: Electrochemical Degradation Factor
            val (vRest, iRest, tRest) = sampleTelemetry()
            onProgress(CalibrationProgress(4, 5, "Degradation Index", 0.82f, "Computing electrochemical degradation factor...", vRest, iRest, tRest))
            delay(1200)

            val degradationFactor = when {
                rOhm <= 180 -> 1.0f
                rOhm <= 220 -> 0.95f
                rOhm <= 260 -> 0.88f
                rOhm <= 320 -> 0.80f
                else -> 0.72f
            }
            val efficiencyIndex = ((1.0f - (rOhm / 1200f)) * 100).toInt().coerceIn(40, 99)

            // PHASE 5: Persist & Hardware Invariant Commit
            onProgress(CalibrationProgress(5, 5, "Committing Silicon Model", 0.95f, "Flushing physical calibration to kernel hardware nodes...", vRest, iRest, tRest))

            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
            val currentCycles = prefs.getInt(KEY_TOTAL_CYCLES, 0) + 1

            prefs.edit()
                .putInt(KEY_TOTAL_CYCLES, currentCycles)
                .putFloat(KEY_AVG_TEMP, tPeak)
                .putInt(KEY_EFFICIENCY, efficiencyIndex)
                .putFloat(KEY_DEGRADATION, degradationFactor)
                .putInt(KEY_BATTERY_ESR, rOhm / 2)
                .putInt(KEY_DCIR, rOhm)
                .putInt(KEY_MAX_SAFE_OPP, safeOppCeiling)
                .putInt(KEY_THERMAL_RISE, thermalRiseMcs)
                .putInt(KEY_PEAK_SAG, maxMeasuredSag)
                .putString(KEY_LAST_CALIBRATED, timestamp)
                .apply()

            TuningRepository.setBatteryEsr(rOhm / 2)
            TuningRepository.setOppSagCeiling(safeOppCeiling)
            TuningRepository.setThermalGradient(thermalRiseMcs)
            TuningRepository.setOppVoltageGuard(true)

            onProgress(CalibrationProgress(5, 5, "Calibration Complete", 1.0f, "Physical model locked: DCIR ${rOhm}mΩ, OPP ${safeOppCeiling / 1000}MHz", vRest, iRest, tRest))
            delay(500)

            getStats()
        } finally {
            isCalibrating.set(false)
        }
    }

    private fun sampleTelemetry(): Triple<Int, Int, Float> {
        val v = Shell.cmd("cat /sys/class/power_supply/battery/voltage_now 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull()?.let {
            if (it > 100000) it / 1000 else it
        } ?: 3850

        val cur = Shell.cmd("cat /sys/class/power_supply/battery/current_now 2>/dev/null").exec().out.firstOrNull()?.toIntOrNull()?.let {
            abs(if (it > 10000) it / 1000 else it)
        } ?: 320

        val t = Shell.cmd("cat /sys/class/power_supply/battery/temp 2>/dev/null").exec().out.firstOrNull()?.toFloatOrNull()?.let {
            if (it > 100) it / 10f else it
        } ?: 36.5f

        return Triple(v, cur, t)
    }

    fun reset(): LearningStats {
        prefs.edit().clear().apply()
        CoroutineScope(Dispatchers.IO).launch {
            TuningRepository.setBatteryEsr(145)
            TuningRepository.setOppSagCeiling(0)
            TuningRepository.setThermalGradient(980)
        }
        return getStats()
    }

    suspend fun runCalibration(
        tier: EasyModeTier? = null,
        profile: EasyModeProfile? = null,
        onProgress: ((CalibrationProgress) -> Unit)? = null
    ): LearningStats = runPhysicalCalibration(onProgress ?: {})
}
