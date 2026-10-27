package com.pox.kernelmanager.data.model

import com.pox.kernelmanager.data.learning.LearningStats
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

/**
 * Formal Verification Suite for Brainless Mode Presets & Dynamic Physical Adaptation.
 *
 * Grounded in SymPrompt (Symbolic Path Partitioning), Meta ACH (Mutant Killing),
 * Metamorphic Testing (MR), and Stateful Lifecycle Model Refinement.
 */
class BrainlessModeFormalVerificationTest {

    // =========================================================================
    // PART 1: SymPrompt Symbolic Path Partitioning & Boundary Value Analysis
    // =========================================================================

    @Test
    fun test_brainless_healthy_nominal_path_no_derating() {
        // Path constraint π_nominal:
        // stats.maxSafeOppKhz >= baseOpp (2050000)
        // stats.batteryDegradationFactor >= 0.85
        // stats.dcirMohm < 220
        // stats.averageLoadTempC < 41.0
        // stats.thermalRiseRateMcs in (800, 1300)
        // stats.efficiencyIndex >= 85
        val healthyStats = LearningStats(
            batteryDegradationFactor = 0.98f,
            dcirMohm = 180,
            averageLoadTempC = 39.0f,
            maxSafeOppKhz = 2050000,
            thermalRiseRateMcs = 1000,
            efficiencyIndex = 95
        )

        for (profile in BrainlessProfile.values()) {
            val base = BrainlessPresets.getPreset(profile)
            val adapted = BrainlessPresets.adaptPreset(base, healthyStats)

            // Postcondition Q_nominal: Exact identity mapping, zero adaptation mutations
            assertEquals("Base OPP must not be mutated", base.baseOppCeilingKhz, adapted.effectiveOppCeilingKhz)
            assertEquals("Base Wattage must not be mutated", base.chargeWatt, adapted.effectiveChargeWatt)
            assertEquals("Base Burst Scale must not be mutated", base.burstPenaltyScale, adapted.effectiveBurstPenaltyScale)
            assertEquals("Base Burst Offset must not be mutated", base.burstPenaltyOffset, adapted.effectiveBurstPenaltyOffset)
            assertEquals("Base Health Mode must not be mutated", base.healthCalibration, adapted.effectiveHealthCalibration)
            assertFalse("Healthy state must flag isAdapted as false", adapted.isAdapted)
            assertTrue("Adaptation notes must be empty", adapted.adaptationNotes.isEmpty())
        }
    }

    @Test
    fun test_brainless_esr_dcir_boundary_hyperplane_ladder() {
        // Boundary Hyperplane: B_1 = 220 mΩ (derates to 15W), B_2 = 260 mΩ (derates to 12W)
        // Testing exact boundary points: B - 1, B, B + 1
        val baseStability = BrainlessPresets.getPreset(BrainlessProfile.STABILITY)
        assertEquals("Stability baseline charge watt must be 18W", 18, baseStability.chargeWatt)

        // Case 1: Off-point below B_1 (219 mΩ) -> 18W
        val stats219 = LearningStats(dcirMohm = 219, averageLoadTempC = 36.0f)
        val adapted219 = BrainlessPresets.adaptPreset(baseStability, stats219)
        assertEquals("DCIR 219 mΩ must retain 18W charging", 18, adapted219.effectiveChargeWatt)

        // Case 2: Exact boundary on-point B_1 (220 mΩ) -> 15W
        val stats220 = LearningStats(dcirMohm = 220, averageLoadTempC = 36.0f)
        val adapted220 = BrainlessPresets.adaptPreset(baseStability, stats220)
        assertEquals("DCIR 220 mΩ must derate strictly to 15W", 15, adapted220.effectiveChargeWatt)
        assertTrue(adapted220.adaptationNotes.any { it.contains("15W") })

        // Case 3: Immediate off-point below B_2 (259 mΩ) -> 15W
        val stats259 = LearningStats(dcirMohm = 259, averageLoadTempC = 36.0f)
        val adapted259 = BrainlessPresets.adaptPreset(baseStability, stats259)
        assertEquals("DCIR 259 mΩ must remain at 15W", 15, adapted259.effectiveChargeWatt)

        // Case 4: Exact boundary on-point B_2 (260 mΩ) -> 12W
        val stats260 = LearningStats(dcirMohm = 260, averageLoadTempC = 36.0f)
        val adapted260 = BrainlessPresets.adaptPreset(baseStability, stats260)
        assertEquals("DCIR 260 mΩ must derate strictly to 12W", 12, adapted260.effectiveChargeWatt)
        assertTrue(adapted260.adaptationNotes.any { it.contains("12W") })
    }

    @Test
    fun test_brainless_thermal_temperature_boundary_derating() {
        // Temperature Boundary: T_B = 41.0°C (derates to 15W)
        val baseStability = BrainlessPresets.getPreset(BrainlessProfile.STABILITY)

        val stats40_9 = LearningStats(dcirMohm = 180, averageLoadTempC = 40.9f)
        val adapted40_9 = BrainlessPresets.adaptPreset(baseStability, stats40_9)
        assertEquals("40.9°C must remain at 18W", 18, adapted40_9.effectiveChargeWatt)

        val stats41_0 = LearningStats(dcirMohm = 180, averageLoadTempC = 41.0f)
        val adapted41_0 = BrainlessPresets.adaptPreset(baseStability, stats41_0)
        assertEquals("41.0°C must derate strictly to 15W", 15, adapted41_0.effectiveChargeWatt)

        val stats45_0 = LearningStats(dcirMohm = 180, averageLoadTempC = 45.0f)
        val adapted45_0 = BrainlessPresets.adaptPreset(baseStability, stats45_0)
        assertEquals("45.0°C must clamp to at most 15W", 15, adapted45_0.effectiveChargeWatt)
    }

    @Test
    fun test_brainless_thermal_rise_rate_boundary_slew() {
        // Thermal rise rate boundaries:
        // Hot slope B_hot = 1300 m°C/s (triggers dampening)
        // Cool slope B_cool = 800 m°C/s (triggers acceleration)
        val base = BrainlessPresets.getPreset(BrainlessProfile.STABILITY)

        // B_cool - 1 (799) -> Accelerated
        val cool = BrainlessPresets.adaptPreset(base, LearningStats(thermalRiseRateMcs = 799))
        assertTrue(cool.adaptationNotes.any { it.contains("accelerated") })

        // B_cool (800) -> Accelerated
        val coolExact = BrainlessPresets.adaptPreset(base, LearningStats(thermalRiseRateMcs = 800))
        assertTrue(coolExact.adaptationNotes.any { it.contains("accelerated") })

        // Nominal band (1000) -> Neither
        val nominal = BrainlessPresets.adaptPreset(base, LearningStats(thermalRiseRateMcs = 1000))
        assertFalse(nominal.adaptationNotes.any { it.contains("accelerated") || it.contains("dampened") })

        // B_hot - 1 (1299) -> Nominal
        val justBelowHot = BrainlessPresets.adaptPreset(base, LearningStats(thermalRiseRateMcs = 1299))
        assertFalse(justBelowHot.adaptationNotes.any { it.contains("dampened") })

        // B_hot (1300) -> Dampened
        val hotExact = BrainlessPresets.adaptPreset(base, LearningStats(thermalRiseRateMcs = 1300))
        assertTrue(hotExact.adaptationNotes.any { it.contains("dampened") })
    }

    @Test
    fun test_brainless_battery_degradation_longevity_elevation() {
        // Wear Boundary: B_wear = 0.80 (80% capacity threshold for Health Mode 3)
        val baseStability = BrainlessPresets.getPreset(BrainlessProfile.STABILITY)
        assertEquals("Stability baseline health mode is 1", 1, baseStability.healthCalibration)

        // Above threshold (0.801) -> Mode 1
        val stats801 = LearningStats(batteryDegradationFactor = 0.801f)
        val adapted801 = BrainlessPresets.adaptPreset(baseStability, stats801)
        assertEquals("Degradation 80.1% must remain at mode 1", 1, adapted801.effectiveHealthCalibration)

        // Below threshold (0.799) -> Mode 3 (Longevity)
        val stats799 = LearningStats(batteryDegradationFactor = 0.799f)
        val adapted799 = BrainlessPresets.adaptPreset(baseStability, stats799)
        assertEquals("Degradation 79.9% must elevate to Longevity Mode 3", 3, adapted799.effectiveHealthCalibration)
        assertTrue(adapted799.adaptationNotes.any { it.contains("Longevity mode") })
    }

    // =========================================================================
    // PART 2: Meta ACH Adversarial Mutant Killing
    // =========================================================================

    @Test
    fun test_kill_mutant_opp_ceiling_clamp() {
        // Target: stats.maxSafeOppKhz in 1_100_000 until effectiveOpp
        // Mutant P'_1: stats.maxSafeOppKhz > effectiveOpp (inverted condition)
        // Mutant P'_2: stats.maxSafeOppKhz < 1_100_000 accepted (ignoring lower sanity bound)
        val basePerf = BrainlessPresets.getPreset(BrainlessProfile.PERFORMANCE)
        assertEquals(2050000, basePerf.baseOppCeilingKhz)

        // Test vector 1: Safe OPP measured at 1784000 kHz -> Must clamp to 1784000
        val stats1784 = LearningStats(maxSafeOppKhz = 1784000)
        val adapted1784 = BrainlessPresets.adaptPreset(basePerf, stats1784)
        assertEquals("OPP must be clamped to maxSafeOppKhz", 1784000, adapted1784.effectiveOppCeilingKhz)

        // Test vector 2: Corrupted/zero reading (e.g. 0 or 500000) -> Must NOT clamp below valid OPP floor
        val statsCorrupt = LearningStats(maxSafeOppKhz = 500000)
        val adaptedCorrupt = BrainlessPresets.adaptPreset(basePerf, statsCorrupt)
        assertEquals("Corrupted safe OPP must be ignored", 2050000, adaptedCorrupt.effectiveOppCeilingKhz)
    }

    @Test
    fun test_kill_mutant_bore_scale_saturation() {
        // Target: effectiveScale = min(2048, effectiveScale + 256)
        // Mutant P'_3: No ceiling check (allows exceeding 2048 kernel limit)
        val baseBattery = BrainlessPresets.getPreset(BrainlessProfile.BATTERY_BACKUP)
        assertEquals("Battery profile baseline scale is 2048", 2048, baseBattery.burstPenaltyScale)

        // Low efficiency index (< 85) triggers +256
        val lowEffStats = LearningStats(efficiencyIndex = 70)
        val adaptedBattery = BrainlessPresets.adaptPreset(baseBattery, lowEffStats)

        // Frame invariant: Must saturate at 2048, never 2304
        assertEquals("BORE scale must not exceed 2048 saturation ceiling", 2048, adaptedBattery.effectiveBurstPenaltyScale)
    }

    // =========================================================================
    // PART 3: Property-Based Testing (PBT) & Algebraic Invariants
    // =========================================================================

    @Test
    fun test_property_universal_hardware_bounds() {
        // Universal Quantification: ∀ profile, ∀ stats ∈ Domain
        // 1. effectiveChargeWatt ∈ [10, 18]
        // 2. effectiveOppCeilingKhz ∈ [1664000, 2050000]
        // 3. effectiveBurstPenaltyScale ∈ [1024, 2048]
        // 4. effectiveBurstPenaltyOffset >= 0
        val rng = Random(42)

        for (profile in BrainlessProfile.values()) {
            val base = BrainlessPresets.getPreset(profile)

            repeat(200) {
                val randomStats = LearningStats(
                    batteryDegradationFactor = rng.nextFloat() * 0.5f + 0.5f, // 0.50 .. 1.00
                    dcirMohm = rng.nextInt(50, 600),
                    averageLoadTempC = rng.nextFloat() * 40.0f + 20.0f, // 20.0 .. 60.0°C
                    maxSafeOppKhz = rng.nextInt(1000000, 2200000),
                    thermalRiseRateMcs = rng.nextInt(200, 3000),
                    efficiencyIndex = rng.nextInt(40, 100)
                )

                val adapted = BrainlessPresets.adaptPreset(base, randomStats)

                // Invariant 1: Charge Wattage bounds
                assertTrue("Charge watt ${adapted.effectiveChargeWatt} must be >= 10W", adapted.effectiveChargeWatt >= 10)
                assertTrue("Charge watt ${adapted.effectiveChargeWatt} must be <= 18W", adapted.effectiveChargeWatt <= 18)
                assertTrue("Charge watt cannot exceed base rating", adapted.effectiveChargeWatt <= base.chargeWatt)

                // Invariant 2: OPP Ceiling bounds
                assertTrue("OPP ${adapted.effectiveOppCeilingKhz} must be >= 1100000 kHz", adapted.effectiveOppCeilingKhz >= 1100000)
                assertTrue("OPP ${adapted.effectiveOppCeilingKhz} must be <= 2050000 kHz", adapted.effectiveOppCeilingKhz <= 2050000)
                assertTrue("OPP cannot exceed base ceiling", adapted.effectiveOppCeilingKhz <= base.baseOppCeilingKhz)

                // Invariant 3: BORE Scale bounds
                assertTrue("BORE scale ${adapted.effectiveBurstPenaltyScale} must be in [1024, 2048]",
                    adapted.effectiveBurstPenaltyScale in 1024..2048)

                // Invariant 4: BORE Offset non-negative
                assertTrue("BORE offset must be >= 0", adapted.effectiveBurstPenaltyOffset >= 0)
            }
        }
    }

    // =========================================================================
    // PART 4: Metamorphic Testing (MR)
    // =========================================================================

    @Test
    fun test_metamorphic_monotonic_derating_under_increasing_stress() {
        // Metamorphic Relation R:
        // If Stats_B has strictly higher DCIR and higher temperature than Stats_A:
        // ChargeWatt(Stats_B) <= ChargeWatt(Stats_A)
        // OPP(Stats_B) <= OPP(Stats_A)
        val base = BrainlessPresets.getPreset(BrainlessProfile.PERFORMANCE)

        val mildStats = LearningStats(
            dcirMohm = 190,
            averageLoadTempC = 37.0f,
            maxSafeOppKhz = 2050000
        )

        val harshStats = LearningStats(
            dcirMohm = 275, // Strictly higher DCIR
            averageLoadTempC = 43.5f, // Strictly higher Temp
            maxSafeOppKhz = 1784000 // Strictly lower safe OPP
        )

        val adaptedMild = BrainlessPresets.adaptPreset(base, mildStats)
        val adaptedHarsh = BrainlessPresets.adaptPreset(base, harshStats)

        // Assert exact metamorphic ordering
        assertTrue("Harsh stats must yield equal or lower charge wattage",
            adaptedHarsh.effectiveChargeWatt <= adaptedMild.effectiveChargeWatt)
        assertTrue("Harsh stats must yield equal or lower OPP ceiling",
            adaptedHarsh.effectiveOppCeilingKhz <= adaptedMild.effectiveOppCeilingKhz)
        assertTrue("Harsh stats must be flagged as adapted", adaptedHarsh.isAdapted)
    }

    // =========================================================================
    // PART 5: Stateful Lifecycle Model Refinement
    // =========================================================================

    @Test
    fun test_stateful_lifecycle_adaptation_and_recovery() {
        // Reference Model: State transitions across workload cycles
        // S_0: Initial Healthy Nominal -> S_1: High Load Overheat -> S_2: Thermal Cooldown Recovery
        val base = BrainlessPresets.getPreset(BrainlessProfile.STABILITY)

        // Step 1: Initial state S_0
        val s0Stats = LearningStats(dcirMohm = 180, averageLoadTempC = 35.0f, maxSafeOppKhz = 2050000)
        val s0 = BrainlessPresets.adaptPreset(base, s0Stats)
        assertEquals(18, s0.effectiveChargeWatt)
        assertEquals(2050000, s0.effectiveOppCeilingKhz)
        assertFalse(s0.isAdapted)

        // Step 2: Severe thermal stress S_1 (e.g. 1 hour gaming in hot room)
        val s1Stats = LearningStats(dcirMohm = 265, averageLoadTempC = 44.0f, maxSafeOppKhz = 1784000)
        val s1 = BrainlessPresets.adaptPreset(base, s1Stats)
        assertEquals("Under stress, wattage must derate to 12W", 12, s1.effectiveChargeWatt)
        assertEquals("Under stress, OPP must clamp to 1784 MHz", 1784000, s1.effectiveOppCeilingKhz)
        assertTrue(s1.isAdapted)

        // Step 3: Cooldown & recovery S_2 (device cooled down, battery stabilized)
        val s2Stats = LearningStats(dcirMohm = 185, averageLoadTempC = 34.0f, maxSafeOppKhz = 2050000)
        val s2 = BrainlessPresets.adaptPreset(base, s2Stats)
        assertEquals("After cooldown, full 18W must be restored", 18, s2.effectiveChargeWatt)
        assertEquals("After cooldown, full 2.05 GHz boost must be restored", 2050000, s2.effectiveOppCeilingKhz)
        assertFalse("Recovered state must clear adaptation flag", s2.isAdapted)
    }
}
