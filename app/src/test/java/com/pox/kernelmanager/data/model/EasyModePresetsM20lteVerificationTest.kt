package com.pox.kernelmanager.data.model

import com.pox.kernelmanager.data.learning.LearningStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasyModePresetsM20lteVerificationTest {

    @Test
    fun test_all_15_presets_conform_to_m20lte_15w_limit() {
        val stats = LearningStats(
            batteryDegradationFactor = 0.95f,
            dcirMohm = 180,
            averageLoadTempC = 35.0f,
            maxSafeOppKhz = 1768000,
            thermalRiseRateMcs = 1000,
            efficiencyIndex = 90
        )

        for (tier in EasyModeTier.entries) {
            for (profile in EasyModeProfile.entries) {
                val base = EasyModePresets.getPreset(tier, profile)
                val adapted = EasyModePresets.adaptPreset(base, stats)

                assertTrue(
                    "Base preset ${tier.name}/${profile.name} charge wattage ${base.chargeWatt} exceeds 15W limit!",
                    base.chargeWatt <= 15
                )
                assertTrue(
                    "Base preset ${tier.name}/${profile.name} charge wattage ${base.chargeWatt} must be >= 5W",
                    base.chargeWatt >= 5
                )
                assertTrue(
                    "Adapted preset ${tier.name}/${profile.name} charge wattage ${adapted.effectiveChargeWatt} exceeds 15W limit!",
                    adapted.effectiveChargeWatt <= 15
                )
            }
        }
    }

    @Test
    fun test_adaptation_reduces_charging_under_degradation() {
        val degradedStats = LearningStats(
            batteryDegradationFactor = 0.25f,
            dcirMohm = 260,
            averageLoadTempC = 42.0f,
            maxSafeOppKhz = 1352000,
            thermalRiseRateMcs = 1500,
            efficiencyIndex = 65
        )

        val extremeBase = EasyModePresets.getPreset(EasyModeTier.EXTREME, EasyModeProfile.MAX_PERFORMANCE)
        val adapted = EasyModePresets.adaptPreset(extremeBase, degradedStats)

        assertEquals("Degraded battery must derate charging to 10W standard", 10, adapted.effectiveChargeWatt)
        assertTrue("High DCIR or degraded stats must be adapted", adapted.isAdapted)
    }
}
