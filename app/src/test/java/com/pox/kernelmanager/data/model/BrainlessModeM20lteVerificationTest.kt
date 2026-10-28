package com.pox.kernelmanager.data.model

import com.pox.kernelmanager.data.learning.LearningStats
import org.junit.Assert.assertTrue
import org.junit.Test

class BrainlessModeM20lteVerificationTest {

    @Test
    fun test_m20lte_brainless_profiles_15w_clamp_invariant() {
        val stats = LearningStats(
            batteryDegradationFactor = 0.95f,
            dcirMohm = 180,
            averageLoadTempC = 35.0f,
            maxSafeOppKhz = 1768000,
            thermalRiseRateMcs = 1000,
            efficiencyIndex = 90
        )

        for (profile in BrainlessProfile.entries) {
            val base = BrainlessPresets.getPreset(profile)
            val adapted = BrainlessPresets.adaptPreset(base, stats)

            assertTrue(
                "M20LTE charging wattage must not exceed 15W hardware AFC limit",
                adapted.effectiveChargeWatt <= 15
            )
            assertTrue(
                "M20LTE charging wattage must be at least 5W",
                adapted.effectiveChargeWatt >= 5
            )
            assertTrue(
                "M20LTE OPP frequency must not exceed 1768000 kHz",
                adapted.effectiveOppCeilingKhz <= 1768000
            )
        }
    }

    @Test
    fun test_m20lte_brainless_dcir_degradation_ladder() {
        val degradedStats = LearningStats(
            batteryDegradationFactor = 0.30f,
            dcirMohm = 280,
            averageLoadTempC = 46.0f,
            maxSafeOppKhz = 1352000,
            thermalRiseRateMcs = 2000,
            efficiencyIndex = 60
        )

        val basePerf = BrainlessPresets.getPreset(BrainlessProfile.PERFORMANCE)
        val adapted = BrainlessPresets.adaptPreset(basePerf, degradedStats)

        assertTrue("Degraded battery must trigger adaptation", adapted.isAdapted)
        assertTrue("High DCIR must throttle charging to safe 10W standard", adapted.effectiveChargeWatt <= 10)
        assertTrue("Thermal/sag must clamp frequency to safe OPP", adapted.effectiveOppCeilingKhz <= 1352000)
    }
}
