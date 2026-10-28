package com.pox.kernelmanager.data.model

import com.pox.kernelmanager.data.learning.LearningStats
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Formal Verification Suite for Easy Mode Presets (15 Tier/Profile Permutations)
 * and Adaptive Physics Derating Engine.
 */
class EasyModePresetsFormalVerificationTest {

    // =========================================================================
    // PART 1: SymPrompt Partitioning & 15-Permutation Invariants
    // =========================================================================

    @Test
    fun test_easymode_all_15_presets_structural_invariants() {
        // Enumerate all 5 Tiers x 3 Profiles = 15 distinct configurations
        val tiers = EasyModeTier.values()
        val profiles = EasyModeProfile.values()
        assertEquals(5, tiers.size)
        assertEquals(3, profiles.size)

        var testedPermutations = 0

        for (tier in tiers) {
            for (profile in profiles) {
                val preset = EasyModePresets.getPreset(tier, profile)
                testedPermutations++

                // Invariant 1: Charge Wattage must strictly obey Begonia 18W architecture
                assertTrue("Preset $tier/$profile charge watt ${preset.chargeWatt} must be in [10, 18]",
                    preset.chargeWatt in 10..18)

                // Invariant 2: Base OPP Ceiling must match valid MediaTek Helio G90T OPP steps
                assertTrue("Preset $tier/$profile base OPP ${preset.baseOppCeilingKhz} must be valid",
                    preset.baseOppCeilingKhz in listOf(1664000, 1784000, 1989000, 2050000))

                // Invariant 3: BORE Scheduler Burst Penalty Scale in valid kernel sysfs range [512, 2048]
                assertTrue("BORE scale ${preset.burstPenaltyScale} out of bounds",
                    preset.burstPenaltyScale in 512..2048)

                // Invariant 4: BORE Burst Penalty Offset in [0, 32]
                assertTrue("BORE offset ${preset.burstPenaltyOffset} out of bounds",
                    preset.burstPenaltyOffset in 0..32)

                // Invariant 5: Summary highlights must be non-empty and descriptive
                assertTrue("Highlights must not be empty", preset.summaryHighlights.isNotEmpty())
            }
        }

        assertEquals("Exactly 15 permutations must be verified", 15, testedPermutations)
    }

    @Test
    fun test_easymode_tier_progression_monotonicity() {
        // Monotonic progression invariant:
        // For any fixed profile P:
        // OPP(LOW) <= OPP(NORMAL) <= OPP(PRO) <= OPP(ULTRA) <= OPP(EXTREME)
        // BORE_Scale(LOW) >= BORE_Scale(NORMAL) >= BORE_Scale(PRO) >= BORE_Scale(ULTRA) >= BORE_Scale(EXTREME)
        for (profile in EasyModeProfile.values()) {
            val low = EasyModePresets.getPreset(EasyModeTier.LOW, profile)
            val normal = EasyModePresets.getPreset(EasyModeTier.NORMAL, profile)
            val pro = EasyModePresets.getPreset(EasyModeTier.PRO, profile)
            val ultra = EasyModePresets.getPreset(EasyModeTier.ULTRA, profile)
            val extreme = EasyModePresets.getPreset(EasyModeTier.EXTREME, profile)

            // OPP monotonicity (Speed increases)
            assertTrue("LOW OPP <= NORMAL OPP", low.baseOppCeilingKhz <= normal.baseOppCeilingKhz)
            assertTrue("NORMAL OPP <= PRO OPP", normal.baseOppCeilingKhz <= pro.baseOppCeilingKhz)
            assertTrue("PRO OPP <= ULTRA OPP", pro.baseOppCeilingKhz <= ultra.baseOppCeilingKhz)
            assertTrue("ULTRA OPP <= EXTREME OPP", ultra.baseOppCeilingKhz <= extreme.baseOppCeilingKhz)

            // BORE background penalty scale monotonicity (Lower tiers freeze background harder)
            assertTrue("LOW BORE Scale >= NORMAL BORE Scale", low.burstPenaltyScale >= normal.burstPenaltyScale)
            assertTrue("NORMAL BORE Scale >= PRO BORE Scale", normal.burstPenaltyScale >= pro.burstPenaltyScale)
            assertTrue("PRO BORE Scale >= ULTRA BORE Scale", pro.burstPenaltyScale >= ultra.burstPenaltyScale)
            assertTrue("ULTRA BORE Scale >= EXTREME BORE Scale", ultra.burstPenaltyScale >= extreme.burstPenaltyScale)
        }
    }

    // =========================================================================
    // PART 2: Adaptation Boundary Value Analysis & Mutant Killing
    // =========================================================================

    @Test
    fun test_easymode_low_tier_battery_wear_opp_sweetspot() {
        // Path constraint: stats.batteryDegradationFactor < 0.85 && base.tier == EasyModeTier.LOW
        // Proves clamping to 1664 MHz efficiency sweet-spot for aged cells
        val lowBalanced = EasyModePresets.getPreset(EasyModeTier.LOW, EasyModeProfile.BALANCED)

        // Degraded cell (84%)
        val degradedStats = LearningStats(batteryDegradationFactor = 0.84f)
        val adapted = EasyModePresets.adaptPreset(lowBalanced, degradedStats)

        assertEquals("Aged cell on LOW tier must clamp OPP to 1664 MHz", 1664000, adapted.effectiveOppCeilingKhz)
        assertTrue(adapted.adaptationNotes.any { it.contains("1664 MHz") })

        // Nominal cell (86%)
        val nominalStats = LearningStats(batteryDegradationFactor = 0.86f)
        val adaptedNominal = EasyModePresets.adaptPreset(lowBalanced, nominalStats)
        assertEquals("Healthy cell must maintain base OPP", lowBalanced.baseOppCeilingKhz, adaptedNominal.effectiveOppCeilingKhz)
    }

    @Test
    fun test_easymode_bore_offset_reduction_on_high_tiers_only() {
        // Path constraint: base.tier in [PRO, ULTRA, EXTREME] && stats.averageLoadTempC < 38.0
        // Reduces burst offset by 4 for zero jitter
        val coolStats = LearningStats(averageLoadTempC = 35.0f)
        val warmStats = LearningStats(averageLoadTempC = 39.0f)

        for (tier in listOf(EasyModeTier.PRO, EasyModeTier.ULTRA, EasyModeTier.EXTREME)) {
            val base = EasyModePresets.getPreset(tier, EasyModeProfile.BALANCED)

            val coolAdapted = EasyModePresets.adaptPreset(base, coolStats)
            val expectedOffset = (base.burstPenaltyOffset - 4).coerceAtLeast(0)
            assertEquals("High tier $tier under cool die must lower burst offset by 4",
                expectedOffset, coolAdapted.effectiveBurstPenaltyOffset)

            val warmAdapted = EasyModePresets.adaptPreset(base, warmStats)
            assertEquals("High tier $tier under warm die must keep baseline offset",
                base.burstPenaltyOffset, warmAdapted.effectiveBurstPenaltyOffset)
        }

        // Low tier must NEVER have offset lowered (preserves background throttling)
        val lowBase = EasyModePresets.getPreset(EasyModeTier.LOW, EasyModeProfile.BALANCED)
        val lowCool = EasyModePresets.adaptPreset(lowBase, coolStats)
        assertEquals("LOW tier must never lower burst penalty offset",
            lowBase.burstPenaltyOffset, lowCool.effectiveBurstPenaltyOffset)
    }

    // =========================================================================
    // PART 3: Property-Based Universal Quantification
    // =========================================================================

    @Test
    fun test_property_universal_easymode_soundness() {
        val rng = Random(1337)

        for (tier in EasyModeTier.values()) {
            for (profile in EasyModeProfile.values()) {
                val base = EasyModePresets.getPreset(tier, profile)

                repeat(50) {
                    val stats = LearningStats(
                        batteryDegradationFactor = rng.nextFloat() * 0.4f + 0.6f,
                        dcirMohm = rng.nextInt(100, 500),
                        averageLoadTempC = rng.nextFloat() * 30.0f + 25.0f,
                        maxSafeOppKhz = rng.nextInt(1000000, 2100000),
                        thermalRiseRateMcs = rng.nextInt(400, 2500),
                        efficiencyIndex = rng.nextInt(50, 100)
                    )

                    val adapted = EasyModePresets.adaptPreset(base, stats)

                    // Postconditions Q:
                    assertTrue("Charge watt strictly in [10, 18]", adapted.effectiveChargeWatt in 10..18)
                    assertTrue("OPP strictly <= base ceiling", adapted.effectiveOppCeilingKhz <= base.baseOppCeilingKhz)
                    assertTrue("OPP strictly >= 1100000", adapted.effectiveOppCeilingKhz >= 1100000)
                    assertTrue("Burst scale strictly in [512, 2048]", adapted.effectiveBurstPenaltyScale in 512..2048)
                    assertTrue("Health mode strictly in [1, 3]", adapted.effectiveHealthCalibration in 1..3)
                }
            }
        }
    }
}
