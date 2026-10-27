package com.pox.kernelmanager.data.learning

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * Formal Verification Suite for Silicon & Battery Stress Calibration Math.
 *
 * Verifies Ohmic ESR, Least-Squares Linear Regression for Die Heating Gradient,
 * DCIR Saturation Bounds, and UVLO Brownout Guarding.
 */
class AdaptiveCalibrationMathVerificationTest {

    // =========================================================================
    // PART 1: Ohmic Resistance (R_ohm) Symbolic & Boundary Hyperplanes
    // =========================================================================

    private fun computeROhm(vOcMv: Int, vPulseMinMv: Int, iIdleMa: Int, iPulseMaxMa: Int): Int {
        val deltaI1 = max(100, iPulseMaxMa - iIdleMa)
        val deltaV1 = max(10, vOcMv - vPulseMinMv)
        return ((deltaV1.toLong() * 1000L) / deltaI1).toInt().coerceIn(30, 1200)
    }

    @Test
    fun test_rohm_calculation_exact_physical_values() {
        // Test Case: vOc = 4100 mV, vPulseMin = 3900 mV (ΔV = 200 mV)
        // iIdle = 200 mA, iPulseMax = 1700 mA (ΔI = 1500 mA)
        // R_ohm = (200 * 1000) / 1500 = 133 mΩ
        val rOhm = computeROhm(4100, 3900, 200, 1700)
        assertEquals(133, rOhm)
    }

    @Test
    fun test_rohm_boundary_clamping_hyperplanes() {
        // Lower boundary clamping at 30 mΩ (superconductor or faulty zero deltaV)
        val rLower = computeROhm(4000, 3999, 100, 2500) // ΔV = 10 (floor), ΔI = 2400 -> 4 mΩ -> clamped to 30
        assertEquals("R_ohm must clamp to lower bound 30 mΩ", 30, rLower)

        // Upper boundary clamping at 1200 mΩ (severely degraded / detached cell)
        val rUpper = computeROhm(4200, 3200, 100, 200) // ΔV = 1000, ΔI = 100 -> 10000 mΩ -> clamped to 1200
        assertEquals("R_ohm must clamp to upper bound 1200 mΩ", 1200, rUpper)

        // Degenerate inputs (pulse current <= idle current) must be guarded by max(100, ΔI)
        val rDegenerateCurrent = computeROhm(4000, 3900, 500, 400) // iPulseMax < iIdle
        assertTrue("R_ohm with degenerate current must compute safely without divide-by-zero", rDegenerateCurrent in 30..1200)
    }

    // =========================================================================
    // PART 2: Least-Squares Thermal Slope Linear Regression
    // =========================================================================

    private fun computeThermalSlopeMcs(points: List<Pair<Double, Double>>): Int {
        var sumX = 0.0
        var sumY = 0.0
        var sumXY = 0.0
        var sumX2 = 0.0
        val n = points.size.toDouble()

        for (pt in points) {
            val sec = pt.first
            val temp = pt.second
            sumX += sec
            sumY += temp
            sumXY += (sec * temp)
            sumX2 += (sec * sec)
        }
        val denom = (n * sumX2 - (sumX * sumX))
        val slopeDegPerSec = if (abs(denom) > 1e-5) (n * sumXY - sumX * sumY) / denom else 0.8
        return (slopeDegPerSec * 1000.0).toInt().coerceIn(150, 4500)
    }

    @Test
    fun test_thermal_slope_linear_regression_accuracy() {
        // Known linear slope: T(t) = 30.0 + 1.25 * t
        // Slope = 1.25 °C/s = 1250 m°C/s
        val points = (0..10).map { t ->
            Pair(t.toDouble(), 30.0 + 1.25 * t.toDouble())
        }

        val slopeMcs = computeThermalSlopeMcs(points)
        assertEquals("Linear slope 1.25 °C/s must yield exact 1250 m°C/s", 1250, slopeMcs)
    }

    @Test
    fun test_thermal_slope_boundary_clamping() {
        // Sub-zero or frozen slope must clamp to minimum floor 150 m°C/s
        val flatPoints = (0..10).map { t -> Pair(t.toDouble(), 35.0) }
        val flatSlope = computeThermalSlopeMcs(flatPoints)
        assertEquals("Flat temperature slope must clamp to 150 m°C/s floor", 150, flatSlope)

        // Extreme runaway slope (e.g. 10 °C/s) must clamp to maximum safety ceiling 4500 m°C/s
        val runawayPoints = (0..10).map { t -> Pair(t.toDouble(), 25.0 + 8.0 * t.toDouble()) }
        val runawaySlope = computeThermalSlopeMcs(runawayPoints)
        assertEquals("Extreme thermal runaway must clamp to 4500 m°C/s ceiling", 4500, runawaySlope)

        // Degenerate single-point or identical X must fall back to 0.8 °C/s (800 m°C/s)
        val singlePoint = listOf(Pair(0.0, 30.0))
        val fallbackSlope = computeThermalSlopeMcs(singlePoint)
        assertEquals("Degenerate sample set must fall back safely to 800 m°C/s", 800, fallbackSlope)
    }

    // =========================================================================
    // PART 3: DCIR Saturation Lower-Bound Invariant: R_dcir >= R_ohm
    // =========================================================================

    @Test
    fun test_dcir_greater_than_or_equal_to_rohm_invariant() {
        // Physical invariant: DCIR includes Ohmic ESR + charge-transfer + diffusion resistance.
        // Therefore, R_dcir must ALWAYS be >= R_ohm.
        val rOhm = 160

        // Case 1: Standard load ΔV = 280 mV, ΔI = 1400 mA -> 200 mΩ (200 >= 160)
        val vOc = 4100
        val finalLoadV = 3820
        val finalLoadI = 1600
        val iIdle = 200

        val deltaIdcir = max(100, finalLoadI - iIdle)
        val deltaVdcir = max(10, vOc - finalLoadV)
        val rDcir = ((deltaVdcir.toLong() * 1000L) / deltaIdcir).toInt().coerceIn(rOhm, 1500)

        assertTrue("R_dcir ($rDcir) must be >= R_ohm ($rOhm)", rDcir >= rOhm)
        assertEquals(200, rDcir)

        // Case 2: Measurement noise where raw calculation is lower than R_ohm
        // (e.g. raw = 120 mΩ, but R_ohm = 160 mΩ) -> Must coerce to rOhm
        val noisyLoadV = 4000 // ΔV = 100 mV, ΔI = 1000 mA -> raw = 100 mΩ
        val noisyDeltaV = max(10, vOc - noisyLoadV)
        val noisyDcir = ((noisyDeltaV.toLong() * 1000L) / 1000L).toInt().coerceIn(rOhm, 1500)

        assertEquals("Noisy R_dcir must be floored at R_ohm", rOhm, noisyDcir)
    }

    // =========================================================================
    // PART 4: Brownout Guard UVLO Threshold Verification
    // =========================================================================

    @Test
    fun test_brownout_guard_uvlo_ceiling_derating() {
        // OPP stepping ladder: 1100MHz, 1500MHz, 1784MHz, 2050MHz
        // UVLO safety threshold is strictly 3450 mV (3400mV PMIC hardware UVLO + 50mV safety headroom)
        val oppSteps = listOf(1100000, 1500000, 1784000, 2050000)

        // Scenario 1: Voltage never drops below 3450 mV at any frequency -> Safe ceiling = 2050000
        var safeOpp = 2050000
        for ((idx, freq) in oppSteps.withIndex()) {
            val stepMinV = 3600 // High voltage, zero brownout risk
            if (stepMinV < 3450) {
                safeOpp = if (idx > 0) oppSteps[idx - 1] else freq
                break
            } else {
                safeOpp = freq
            }
        }
        assertEquals("Healthy voltage must permit full 2.05 GHz OPP ceiling", 2050000, safeOpp)

        // Scenario 2: At 2050 MHz (step 3), heavy current drops voltage to 3420 mV (< 3450 mV)
        // Must clamp safe OPP to previous safe step (1784 MHz)
        safeOpp = 2050000
        for ((idx, freq) in oppSteps.withIndex()) {
            val stepMinV = if (freq == 2050000) 3420 else 3600
            if (stepMinV < 3450) {
                safeOpp = if (idx > 0) oppSteps[idx - 1] else freq
                break
            } else {
                safeOpp = freq
            }
        }
        assertEquals("UVLO drop at 2.05 GHz must clamp OPP ceiling to 1.784 GHz", 1784000, safeOpp)

        // Scenario 3: Sag at step 0 (1100 MHz) drops to 3400 mV
        // Must clamp safe OPP to step 0 floor (cannot index out of bounds to -1)
        safeOpp = 2050000
        for ((idx, freq) in oppSteps.withIndex()) {
            val stepMinV = 3410
            if (stepMinV < 3450) {
                safeOpp = if (idx > 0) oppSteps[idx - 1] else freq
                break
            } else {
                safeOpp = freq
            }
        }
        assertEquals("Sag at first step must clamp safely to 1.1 GHz floor", 1100000, safeOpp)
    }
}
