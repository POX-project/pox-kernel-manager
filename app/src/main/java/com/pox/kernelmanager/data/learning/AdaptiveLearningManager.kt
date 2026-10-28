package com.pox.kernelmanager.data.learning

import android.content.Context
import android.content.SharedPreferences
import com.pox.kernelmanager.PoxApplication
import com.pox.kernelmanager.data.model.HealthCalibrationMode
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
    val maxSafeOppKhz: Int = 2050000,
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
    private const val KEY_BATTERY_ESR = "calibrated_battery_esr_mohm"
    private const val KEY_DCIR_MOHM = "calibrated_dcir_mohm"
    private const val KEY_MAX_SAFE_OPP = "calibrated_max_safe_opp_khz"
    private const val KEY_THERMAL_RISE = "calibrated_thermal_rise_mcs"
    private const val KEY_VOLTAGE_SAG = "calibrated_peak_voltage_sag_mv"
    private const val KEY_LAST_CALIBRATED = "last_calibrated_time"

    private val prefs: SharedPreferences by lazy {
        PoxApplication.instance.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun init(context: Context) {
        syncWithKernelTelemetry()
        // Boot-time hardware profile restoration
        val esr = prefs.getInt(KEY_BATTERY_ESR, 0)
        val oppCap = prefs.getInt(KEY_MAX_SAFE_OPP, 0)
        val gradient = prefs.getInt(KEY_THERMAL_RISE, 0)
        if (esr > 0 || oppCap > 0 || gradient > 0) {
            CoroutineScope(Dispatchers.IO).launch {
                if (esr > 0) TuningRepository.setBatteryEsr(esr)
                if (oppCap > 0) TuningRepository.setOppSagCeiling(oppCap)
                if (gradient > 0) TuningRepository.setThermalGradient(gradient)
                TuningRepository.setHealthCalibrationMode(HealthCalibrationMode.AUTO_ADAPTIVE)
                TuningRepository.setOppVoltageGuard(true)
            }
        }
    }

    /**
     * Synchronizes user-space learned parameters with kernel-space runtime
     * telemetry from /proc/perfmgr/health_status (active OPP clamps and droop).
     */
    fun syncWithKernelTelemetry() {
        val raw = readSysfs("/proc/perfmgr/health_status") ?: return
        val map = raw.lines().mapNotNull { line ->
            val idx = line.indexOf('=')
            if (idx > 0) line.substring(0, idx).trim() to line.substring(idx + 1).trim() else null
        }.toMap()

        val activeCeiling = map["active_opp_ceiling_khz"]?.toIntOrNull()
        val effEsr = map["battery_esr_effective_mohm"]?.toIntOrNull()
        val sagMv = map["battery_voltage_sag_mv"]?.toIntOrNull()

        val editor = prefs.edit()
        if (activeCeiling != null && activeCeiling in 1_100_000..2_050_000) {
            val savedMax = prefs.getInt(KEY_MAX_SAFE_OPP, 2050000)
            if (activeCeiling < savedMax) {
                editor.putInt(KEY_MAX_SAFE_OPP, activeCeiling)
            }
        }
        if (sagMv != null && sagMv > 0) {
            editor.putInt(KEY_VOLTAGE_SAG, sagMv)
        }
        if (effEsr != null && effEsr > 0) {
            val baseEsr = prefs.getInt(KEY_BATTERY_ESR, 150)
            val trackedEsr = ((baseEsr * 3 + effEsr) / 4).coerceIn(30, 800)
            editor.putInt(KEY_BATTERY_ESR, trackedEsr)
        }
        editor.apply()
    }

    /**
     * Records continuous runtime telemetry from active phone usage.
     * Updates long-term average load temperature and adjusts efficiency index.
     */
    fun recordUsageFeedback(sampleTempC: Float, vMv: Int, iMa: Int) {
        val currentAvg = prefs.getFloat(KEY_AVG_TEMP, 38.5f)
        val updatedAvg = (currentAvg * 0.95f + sampleTempC * 0.05f).coerceIn(15.0f, 65.0f)
        prefs.edit().putFloat(KEY_AVG_TEMP, updatedAvg).apply()
        syncWithKernelTelemetry()
    }

    private fun readSysfs(path: String): String? {
        val file = java.io.File(path)
        if (file.exists() && file.canRead()) {
            try {
                val text = file.readText().trim()
                if (text.isNotEmpty()) return text
            } catch (_: Exception) {}
        }
        return try {
            val res = Shell.cmd("cat $path 2>/dev/null").exec()
            if (res.isSuccess && res.out.isNotEmpty()) {
                res.out.joinToString("\n").trim().takeIf { it.isNotEmpty() }
            } else null
        } catch (_: Exception) {
            null
        }
    }

    fun validateEnvironment(): PreflightResult {
        val status = readSysfs("/sys/class/power_supply/battery/status")?.trim()
        if (status != null && (status.equals("Charging", ignoreCase = true) || status.equals("Full", ignoreCase = true))) {
            return PreflightResult.Failed("Charger connected! Please unplug your charger to calibrate battery ESR and silicon voltage droop under true discharge load.")
        }

        val capacity = readSysfs("/sys/class/power_supply/battery/capacity")?.toIntOrNull()
        if (capacity != null && capacity < 25) {
            return PreflightResult.Failed("Battery is too low ($capacity%). At least 25% battery is required to safely measure high-frequency OPP droop.")
        }

        val rawBatTemp = readSysfs("/sys/class/power_supply/battery/temp")?.toIntOrNull()
        if (rawBatTemp != null) {
            val tempC = rawBatTemp / 10.0f
            if (tempC < 15.0f || tempC > 42.0f) {
                return PreflightResult.Failed("Battery temperature (${String.format(Locale.US, "%.1f", tempC)}°C) out of safe range (15°C - 42°C). Let the device normalize before calibrating.")
            }
        }

        val rawCpuTemp = readSysfs("/sys/class/thermal/thermal_zone0/temp")?.toIntOrNull()
        if (rawCpuTemp != null) {
            val cpuC = if (rawCpuTemp > 1000) rawCpuTemp / 1000.0f else rawCpuTemp / 10.0f
            if (cpuC > 52.0f) {
                return PreflightResult.Failed("CPU is currently too hot (${String.format(Locale.US, "%.1f", cpuC)}°C). Wait until the phone cools down below 52°C.")
            }
        }

        return PreflightResult.Ok
    }

    fun getStats(): LearningStats {
        val directCycles = readSysfs("/sys/class/power_supply/battery/cycle_count")?.toIntOrNull()
        val realCycles = if (directCycles != null && directCycles > 0) directCycles else prefs.getInt(KEY_TOTAL_CYCLES, 0)

        val fullUah = readSysfs("/sys/class/power_supply/battery/charge_full")?.toFloatOrNull()
        val designUah = readSysfs("/sys/class/power_supply/battery/charge_full_design")?.toFloatOrNull()
        val realDegradation = if (fullUah != null && designUah != null && designUah > 0) {
            (fullUah / designUah).coerceIn(0.5f, 1.0f)
        } else {
            prefs.getFloat(KEY_DEGRADATION, 0.98f)
        }
        val realEfficiency = (realDegradation * 100).toInt().coerceIn(50, 100)

        val rawTemp = readSysfs("/sys/class/power_supply/battery/temp")?.toIntOrNull()
            ?: readSysfs(KernelNodes.TEMP_LIMIT_SYS)?.toIntOrNull() ?: 280
        val realTempC = (rawTemp / 10.0f).coerceIn(10.0f, 65.0f)

        val esr = prefs.getInt(KEY_BATTERY_ESR, 145)
        val dcir = prefs.getInt(KEY_DCIR_MOHM, 195)
        val maxOpp = prefs.getInt(KEY_MAX_SAFE_OPP, 2050000)
        val thermalRise = prefs.getInt(KEY_THERMAL_RISE, 980)
        val peakSag = prefs.getInt(KEY_VOLTAGE_SAG, 240)

        return LearningStats(
            isAutoEnabled = prefs.getBoolean(KEY_AUTO_ENABLED, true),
            totalCycles = realCycles,
            averageLoadTempC = prefs.getFloat(KEY_AVG_TEMP, realTempC),
            efficiencyIndex = realEfficiency,
            batteryDegradationFactor = realDegradation,
            siliconOppOffsetMhz = prefs.getInt(KEY_OPP_OFFSET, 0),
            batteryEsrMohm = esr,
            dcirMohm = dcir,
            maxSafeOppKhz = maxOpp,
            thermalRiseRateMcs = thermalRise,
            peakVoltageSagMv = peakSag,
            lastCalibratedTimestamp = prefs.getString(KEY_LAST_CALIBRATED, "Not Yet Calibrated") ?: "Not Yet Calibrated"
        )
    }

    fun setAutoEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_ENABLED, enabled).apply()
    }

    private fun normalizeVoltageMv(raw: Long): Int {
        return when {
            raw > 100_000L -> (raw / 1000L).toInt()
            raw in 2500L..5000L -> raw.toInt()
            else -> 4000
        }
    }

    private fun normalizeCurrentMa(raw: Long): Int {
        val absVal = abs(raw)
        return when {
            absVal > 20_000L -> (absVal / 1000L).toInt()
            absVal in 10L..20_000L -> absVal.toInt()
            else -> 0
        }
    }

    private fun sampleTelemetry(): Triple<Int, Int, Float> {
        val vRaw = readSysfs("/sys/class/power_supply/battery/voltage_now")?.toLongOrNull()
            ?: readSysfs("/sys/class/power_supply/sec-fuelgauge/voltage_now")?.toLongOrNull()
            ?: 4100000L
        val iRaw = readSysfs("/sys/class/power_supply/battery/current_now")?.toLongOrNull()
            ?: readSysfs("/sys/class/power_supply/sec-fuelgauge/current_now")?.toLongOrNull()
            ?: 350000L
        val tRaw = readSysfs("/sys/class/thermal/thermal_zone0/temp")?.toIntOrNull()
            ?: readSysfs("/sys/class/power_supply/battery/temp")?.toIntOrNull()
            ?: readSysfs("/sys/class/power_supply/sec-fuelgauge/temp")?.toIntOrNull()
            ?: 350
        val vMv = normalizeVoltageMv(vRaw)
        val iMa = normalizeCurrentMa(iRaw)
        val tC = if (tRaw > 1000) tRaw / 1000.0f else tRaw / 10.0f
        return Triple(vMv, iMa, tC)
    }

    private class StressWorker(private val active: AtomicBoolean) : Runnable {
        override fun run() {
            var x = 1.00001
            var y = 1.00002
            var bitMask = 0x5555555555555555L
            while (active.get()) {
                for (i in 0 until 4000) {
                    x = Math.fma(x, y, 1.000000001)
                    y = Math.fma(y, 1.000000002, x * 0.000000001)
                    bitMask = (bitMask shl 1) xor (bitMask ushr 1) xor 0x123456789ABCDEFL
                }
                if (x > 1e9) x = 1.00001
                if (y > 1e9) y = 1.00002
            }
        }
    }

    /**
     * Executes the true 5-phase physical calibration protocol:
     * 1. Baseline Quiescent Sampling (2.0s)
     * 2. Instantaneous Ohmic Step Pulse (0.6s) -> R_ohm (mOhm)
     * 3. Stepped OPP Sag Ladder (6.4s) -> Maximum Safe Frequency Cap (kHz)
     * 4. Sustained Thermal Saturation (5.0s) -> DCIR & dT/dt (mC/s)
     * 5. Cooldown & Hardware Commit (2.0s)
     *
     * Total Duration: ~16.0 seconds.
     */
    suspend fun runCalibration(
        onProgress: ((CalibrationProgress) -> Unit)? = null
    ): LearningStats = withContext(Dispatchers.IO) {
        val preflight = validateEnvironment()
        if (preflight is PreflightResult.Failed) {
            throw IllegalStateException(preflight.reason)
        }

        val stressActive = AtomicBoolean(false)
        val threadCount = Runtime.getRuntime().availableProcessors().coerceIn(4, 8)
        val threads = mutableListOf<Thread>()

        fun startStress() {
            stressActive.set(true)
            threads.clear()
            for (i in 0 until threadCount) {
                val t = Thread(StressWorker(stressActive), "PoxCalibWorker-$i")
                t.priority = Thread.MAX_PRIORITY
                threads.add(t)
                t.start()
            }
        }

        fun stopStress() {
            stressActive.set(false)
            for (t in threads) {
                t.interrupt()
            }
            threads.clear()
        }

        try {
            // Disarm existing limits for uncorrupted calibration
            TuningRepository.setOppSagCeiling(0)

            // =========================================================================
            // PHASE 0: Quiescent Baseline Sampling (2.0s, Progress 0% -> 12%)
            // =========================================================================
            val baselineVoltages = mutableListOf<Int>()
            val baselineCurrents = mutableListOf<Int>()
            var baselineTemp = 35.0f

            for (i in 0 until 20) {
                val (v, cur, t) = sampleTelemetry()
                baselineVoltages.add(v)
                baselineCurrents.add(cur)
                baselineTemp = t
                val p = i / 20.0f * 0.12f
                onProgress?.invoke(
                    CalibrationProgress(
                        phase = 0,
                        phaseName = "Quiescent Baseline",
                        progress = p,
                        statusMessage = "Measuring open-circuit battery voltage (V_oc)...",
                        liveVoltageMv = v,
                        liveCurrentMa = cur,
                        liveTempC = t
                    )
                )
                delay(100)
            }

            baselineVoltages.sort()
            val vOc = baselineVoltages[baselineVoltages.size / 2]
            baselineCurrents.sort()
            val iIdle = baselineCurrents[baselineCurrents.size / 2]

            // =========================================================================
            // PHASE 1: Ohmic Transient Step Pulse (0.6s, Progress 12% -> 22%)
            // =========================================================================
            startStress()
            val pulseVoltages = mutableListOf<Int>()
            val pulseCurrents = mutableListOf<Int>()
            var pulseTemp = baselineTemp

            for (i in 0 until 10) {
                val (v, cur, t) = sampleTelemetry()
                pulseVoltages.add(v)
                pulseCurrents.add(cur)
                pulseTemp = t
                val p = 0.12f + (i / 10.0f * 0.10f)
                onProgress?.invoke(
                    CalibrationProgress(
                        phase = 1,
                        phaseName = "Ohmic Transient Pulse",
                        progress = p,
                        statusMessage = "Exerting step current: capturing Ohmic contact ESR droop...",
                        liveVoltageMv = v,
                        liveCurrentMa = cur,
                        liveTempC = t
                    )
                )
                delay(60)
            }

            val vPulseMin = pulseVoltages.minOrNull() ?: (vOc - 150)
            val iPulseMax = pulseCurrents.maxOrNull() ?: (iIdle + 1500)
            val deltaI1 = max(100, iPulseMax - iIdle)
            val deltaV1 = max(10, vOc - vPulseMin)
            val rOhm = ((deltaV1.toLong() * 1000L) / deltaI1).toInt().coerceIn(30, 1200)

            // =========================================================================
            // PHASE 2: Stepped OPP Frequency Sag Ladder (6.4s, Progress 22% -> 62%)
            // =========================================================================
            val isM20 = com.pox.kernelmanager.data.model.DeviceInfo.isM20Device()
            val oppSteps = if (isM20) {
                listOf(832000, 1144000, 1352000, 1586000, 1768000)
            } else {
                listOf(1100000, 1500000, 1784000, 2050000)
            }
            var safeOppCeiling = if (isM20) 1768000 else 2050000
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
                    stepT = t
                    val stepBaseP = 0.22f + (stepIdx * 0.10f)
                    val p = stepBaseP + (s / 16.0f * 0.10f)
                    onProgress?.invoke(
                        CalibrationProgress(
                            phase = 2,
                            phaseName = "OPP Stepping Ladder (${freq / 1000}MHz)",
                            progress = p,
                            statusMessage = "Testing ${freq / 1000}MHz: observing transient battery voltage...",
                            liveVoltageMv = v,
                            liveCurrentMa = cur,
                            liveTempC = t
                        )
                    )
                    delay(100)
                }

                val currentSag = vOc - stepMinV
                maxMeasuredSag = max(maxMeasuredSag, currentSag)

                // Brownout Guard: UVLO floor is 3400mV + 50mV safety buffer
                if (stepMinV < 3450) {
                    safeOppCeiling = if (stepIdx > 0) oppSteps[stepIdx - 1] else freq
                    break
                } else {
                    safeOppCeiling = freq
                }
            }

            // Restore full frequency for Phase 3
            TuningRepository.setOppSagCeiling(safeOppCeiling)

            // =========================================================================
            // PHASE 3: Sustained Thermal & DCIR Saturation (5.0s, Progress 62% -> 88%)
            // =========================================================================
            val thermalPoints = mutableListOf<Pair<Long, Float>>()
            var finalLoadV = 3800
            var finalLoadI = 1800
            val startTimeMs = System.currentTimeMillis()

            for (i in 0 until 50) {
                val (v, cur, t) = sampleTelemetry()
                finalLoadV = v
                finalLoadI = cur
                val nowOffsetSec = (System.currentTimeMillis() - startTimeMs) / 1000.0
                thermalPoints.add(Pair(System.currentTimeMillis() - startTimeMs, t))

                val p = 0.62f + (i / 50.0f * 0.26f)
                onProgress?.invoke(
                    CalibrationProgress(
                        phase = 3,
                        phaseName = "Thermal & DCIR Saturation",
                        progress = p,
                        statusMessage = "Analyzing heatsink dissipation & sustained DC resistance...",
                        liveVoltageMv = v,
                        liveCurrentMa = cur,
                        liveTempC = t
                    )
                )
                delay(100)
            }

            // Linear regression: dT/dt in mC/sec
            var sumX = 0.0
            var sumY = 0.0
            var sumXY = 0.0
            var sumX2 = 0.0
            val n = thermalPoints.size.toDouble()

            for (pt in thermalPoints) {
                val sec = pt.first / 1000.0
                val temp = pt.second.toDouble()
                sumX += sec
                sumY += temp
                sumXY += (sec * temp)
                sumX2 += (sec * sec)
            }
            val denom = (n * sumX2 - (sumX * sumX))
            val slopeDegPerSec = if (abs(denom) > 1e-5) (n * sumXY - sumX * sumY) / denom else 0.8
            val thermalRiseMcs = (slopeDegPerSec * 1000.0).toInt().coerceIn(150, 4500)

            val deltaIdcir = max(100, finalLoadI - iIdle)
            val deltaVdcir = max(10, vOc - finalLoadV)
            val rDcir = ((deltaVdcir.toLong() * 1000L) / deltaIdcir).toInt().coerceIn(rOhm, 1500)

            // =========================================================================
            // PHASE 4: Cooldown & Hardware Commit (2.0s, Progress 88% -> 100%)
            // =========================================================================
            stopStress()

            for (i in 0 until 20) {
                val (v, cur, t) = sampleTelemetry()
                val p = 0.88f + (i / 20.0f * 0.12f)
                onProgress?.invoke(
                    CalibrationProgress(
                        phase = 4,
                        phaseName = "Hardware Latch & Commit",
                        progress = p,
                        statusMessage = "Saving calibrated OPP droop profile to Pox Kernel driver...",
                        liveVoltageMv = v,
                        liveCurrentMa = cur,
                        liveTempC = t
                    )
                )
                delay(100)
            }

            // Persistence & hardware commitment
            val directCycles = readSysfs("/sys/class/power_supply/battery/cycle_count")?.toIntOrNull()
            val realCycles = if (directCycles != null && directCycles > 0) directCycles else prefs.getInt(KEY_TOTAL_CYCLES, 0) + 1

            val fullUah = readSysfs("/sys/class/power_supply/battery/charge_full")?.toFloatOrNull()
            val designUah = readSysfs("/sys/class/power_supply/battery/charge_full_design")?.toFloatOrNull()
            val realDegradation = if (fullUah != null && designUah != null && designUah > 0) {
                (fullUah / designUah).coerceIn(0.5f, 1.0f)
            } else {
                0.98f
            }
            val realEfficiency = (realDegradation * 100).toInt().coerceIn(50, 100)

            val formatter = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
            val timestamp = formatter.format(Date())

            prefs.edit()
                .putInt(KEY_TOTAL_CYCLES, realCycles)
                .putFloat(KEY_AVG_TEMP, pulseTemp)
                .putInt(KEY_EFFICIENCY, realEfficiency)
                .putFloat(KEY_DEGRADATION, realDegradation)
                .putInt(KEY_BATTERY_ESR, rOhm)
                .putInt(KEY_DCIR_MOHM, rDcir)
                .putInt(KEY_MAX_SAFE_OPP, safeOppCeiling)
                .putInt(KEY_THERMAL_RISE, thermalRiseMcs)
                .putInt(KEY_VOLTAGE_SAG, maxMeasuredSag)
                .putString(KEY_LAST_CALIBRATED, timestamp)
                .apply()

            // Push verified parameters into kernel drivers
            TuningRepository.setBatteryEsr(rOhm)
            TuningRepository.setOppSagCeiling(safeOppCeiling)
            TuningRepository.setThermalGradient(thermalRiseMcs)
            TuningRepository.setHealthCalibrationMode(HealthCalibrationMode.AUTO_ADAPTIVE)
            TuningRepository.setOppVoltageGuard(true)

            getStats()
        } finally {
            stopStress()
        }
    }

    fun reset(): LearningStats {
        prefs.edit().clear().apply()
        CoroutineScope(Dispatchers.IO).launch {
            TuningRepository.setBatteryEsr(150)
            TuningRepository.setOppSagCeiling(0)
            TuningRepository.setThermalGradient(1000)
        }
        return getStats()
    }
}
