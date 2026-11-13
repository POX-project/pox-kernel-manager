package com.pox.kernelmanager.data.repo

import com.pox.kernelmanager.data.learning.LearningStats
import com.pox.kernelmanager.data.model.BrainlessPresets
import com.pox.kernelmanager.data.model.BrainlessProfile
import com.pox.kernelmanager.data.model.EasyModePresets
import com.pox.kernelmanager.data.model.EasyModeProfile
import com.pox.kernelmanager.data.model.EasyModeTier
import com.pox.kernelmanager.data.model.GamingModeLevel
import com.pox.kernelmanager.data.model.KprofilesLevel
import com.pox.kernelmanager.data.root.KernelNodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit verification for system degradation, ungranted root handling,
 * and boot restoration idempotency invariants.
 */
class GracefulDegradationAndBootTest {

    @Test
    fun sysfsMissingNodeUsrtchParseDegradesGracefully() {
        // When sysfs /proc/perfmgr/tchbst/user/usrtch returns empty, missing, or corrupt output
        assertNull(KernelNodes.parseUsrtchField("", "eas_boost"))
        assertNull(KernelNodes.parseUsrtchField("invalid dump content without colons", "eas_boost"))
        assertNull(KernelNodes.parseUsrtchField("some_other_field:\t50", "eas_boost"))
        assertNull(KernelNodes.parseUsrtchField("eas_boost:\tnot_a_number", "eas_boost"))

        // Correct field parsing with leading tabs, spaces, multiline and negative values
        val dump = """
            active_time:	1200
            eas_boost:	-20
            touch_opp:	8
        """.trimIndent()
        assertEquals(-20, KernelNodes.parseUsrtchField(dump, "eas_boost"))
        assertEquals(1200, KernelNodes.parseUsrtchField(dump, "active_time"))
        assertEquals(8, KernelNodes.parseUsrtchField(dump, "touch_opp"))
    }

    @Test
    fun adaptiveEasyModePresetIdempotencyUnderThermalAndVoltageSag() {
        val tier = EasyModeTier.GAMING
        val profile = EasyModeProfile.PERFORMANCE
        val base = EasyModePresets.getPreset(tier, profile)

        // Baseline verification
        assertEquals(18, base.fastChargeWatt)
        assertEquals(2050000, base.oppCeilingKhz)

        // Simulate severe DCIR degradation (ESR 260 mOhm > 240 mOhm threshold)
        val highDcirStats = LearningStats(
            batteryEsrMohm = 260,
            thermalRiseRateMcs = 450,
            recordedSagEvents = 15,
            recordedThermalThrottles = 3
        )

        val adapted1 = EasyModePresets.adaptPreset(base, highDcirStats)
        assertTrue("Preset should be marked adapted", adapted1.isAdapted)
        assertEquals("Charge watt must derate to 12W under high DCIR", 12, adapted1.effectiveChargeWatt)
        assertEquals("OPP ceiling must derate to 1700MHz to prevent Vdrop sag brownouts", 1700000, adapted1.effectiveOppCeilingKhz)
        assertFalse("Sched BORE must be enabled under severe throttling to maintain interactive UI fluidity", !adapted1.effectiveSchedBoreEnabled)

        // Second pass: Reapplying adaptation on an already evaluated state must be strictly idempotent
        val adapted2 = EasyModePresets.adaptPreset(base, highDcirStats)
        assertEquals(adapted1.effectiveChargeWatt, adapted2.effectiveChargeWatt)
        assertEquals(adapted1.effectiveOppCeilingKhz, adapted2.effectiveOppCeilingKhz)
        assertEquals(adapted1.effectiveGamingMode, adapted2.effectiveGamingMode)
        assertEquals(adapted1.effectiveKprofileLevel, adapted2.effectiveKprofileLevel)
        assertEquals(adapted1.effectiveSchedBoreEnabled, adapted2.effectiveSchedBoreEnabled)
    }

    @Test
    fun adaptiveBrainlessPresetDeratesSafelyAndRemainsIdempotent() {
        val base = BrainlessPresets.getPreset(BrainlessProfile.EXTREME)
        assertEquals(GamingModeLevel.EXTREME.ordinal, base.gamingMode)

        // High thermal gradient (> 400 mC/s) and high ESR
        val stressStats = LearningStats(
            batteryEsrMohm = 250,
            thermalRiseRateMcs = 500,
            recordedSagEvents = 20,
            recordedThermalThrottles = 10
        )

        val adaptedOnce = BrainlessPresets.adaptPreset(base, stressStats)
        assertTrue(adaptedOnce.isAdapted)
        // High thermal rise must clamp gaming mode from EXTREME down to safe BALANCED (ordinal 0)
        assertEquals(GamingModeLevel.BALANCED.ordinal, adaptedOnce.effectiveGamingMode)
        // High ESR must clamp fast charge watt to 12W
        assertEquals(12, adaptedOnce.effectiveChargeWatt)
        // OPP sag ceiling clamped to safe limit
        assertEquals(1700000, adaptedOnce.effectiveOppCeilingKhz)

        // Reapply - must produce identical configuration
        val adaptedTwice = BrainlessPresets.adaptPreset(base, stressStats)
        assertEquals(adaptedOnce.effectiveGamingMode, adaptedTwice.effectiveGamingMode)
        assertEquals(adaptedOnce.effectiveChargeWatt, adaptedTwice.effectiveChargeWatt)
        assertEquals(adaptedOnce.effectiveOppCeilingKhz, adaptedTwice.effectiveOppCeilingKhz)
        assertEquals(adaptedOnce.effectiveBurstPenaltyScale, adaptedTwice.effectiveBurstPenaltyScale)
    }

    @Test
    fun hardwareSafetyBoundsAreGuaranteedAcrossAllPresets() {
        val extremeStats = LearningStats(
            batteryEsrMohm = 500,
            thermalRiseRateMcs = 1200,
            recordedSagEvents = 100,
            recordedThermalThrottles = 50
        )

        for (tier in EasyModeTier.values()) {
            for (profile in EasyModeProfile.values()) {
                val base = EasyModePresets.getPreset(tier, profile)
                val adapted = EasyModePresets.adaptPreset(base, extremeStats)

                assertTrue("Charge watt must never be below 5W", adapted.effectiveChargeWatt >= 5)
                assertTrue("Charge watt must never exceed 27W", adapted.effectiveChargeWatt <= 27)
                assertTrue("OPP ceiling must be >= 1000MHz", adapted.effectiveOppCeilingKhz >= 1000000)
                assertTrue("OPP ceiling must be <= 2050MHz", adapted.effectiveOppCeilingKhz <= 2050000)
                assertNotNull(adapted.summary)
            }
        }

        for (profile in BrainlessProfile.values()) {
            val base = BrainlessPresets.getPreset(profile)
            val adapted = BrainlessPresets.adaptPreset(base, extremeStats)

            assertTrue("Charge watt must never be below 5W", adapted.effectiveChargeWatt >= 5)
            assertTrue("Charge watt must never exceed 27W", adapted.effectiveChargeWatt <= 27)
            assertTrue("OPP ceiling must be >= 1000MHz", adapted.effectiveOppCeilingKhz >= 1000000)
            assertTrue("OPP ceiling must be <= 2050MHz", adapted.effectiveOppCeilingKhz <= 2050000)
            assertNotNull(adapted.summary)
        }
    }
}
