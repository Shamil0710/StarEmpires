package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B14 paired machine evidence for finite post-war replacement burden.
 *
 * <p>This acceptance test deliberately reuses the ordinary Stage-21G paid replacement probe. Every
 * coordinate therefore reconstructs a real core-faction yard, requires finite hull/module stock and
 * finite yard work, commissions a fresh ordinary {@code FleetId}, and verifies byte-stable
 * continuation. The evidence batch owns no recovery state and cannot manufacture replacement
 * capability.</p>
 *
 * <p>The validation framework defines a 25% matched-loss recovery-time differential as a stop-ship
 * threshold. Until a continuous campaign capability curve exists, this test applies that bound only
 * to the already-authoritative fresh-replacement latency dimension. It must not be cited as closure
 * of the remaining recoveryT50/recoveryT80 campaign-curve requirement.</p>
 */
class Stage22CorePairPostWarRecoveryMachineEvidenceAcceptanceTest {
    private static final double MAX_MATCHED_REPLACEMENT_LATENCY_DIFFERENTIAL = 0.25d;

    @Test
    void b14RunsEightPairedPaidReplacementCellsWithinMatchedLatencyGuard() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B14",
                "paid_fresh_identity_replacement",
                "core_pair.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(8),
                (scenario, variant, profile, coordinate) -> {
                    var empire = Stage22CorePairReplacementProbe.run(true);
                    var union = Stage22CorePairReplacementProbe.run(false);
                    double latencyDifferential = relativeDifferential(
                            empire.buildSeconds(), union.buildSeconds());
                    boolean authoritiesValid = empire.valid() && union.valid();
                    boolean latencyWithinGuard = latencyDifferential
                            <= MAX_MATCHED_REPLACEMENT_LATENCY_DIFFERENTIAL + 1e-12d;
                    List<String> breaches = new ArrayList<>();
                    if (!authoritiesValid) breaches.add("b14_paid_replacement_authority_invalid");
                    if (!latencyWithinGuard) breaches.add("b14_matched_replacement_latency_over_25_percent");
                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.of(
                                    "empire_replacement_latency_seconds", empire.buildSeconds(),
                                    "union_replacement_latency_seconds", union.buildSeconds(),
                                    "matched_replacement_latency_differential", latencyDifferential,
                                    "empire_hull_input_mass_kg", empire.hullInputMassKg(),
                                    "union_hull_input_mass_kg", union.hullInputMassKg(),
                                    "empire_module_input_mass_kg", empire.moduleInputMassKg(),
                                    "union_module_input_mass_kg", union.moduleInputMassKg()),
                            Map.of(
                                    "empire_paid_replacement_valid", empire.valid() ? 1d : 0d,
                                    "union_paid_replacement_valid", union.valid() ? 1d : 0d,
                                    "matched_latency_guard_passed", latencyWithinGuard ? 1d : 0d),
                            breaches);
                });

        assertEquals(8, vector.pairedSeedCount());
        assertEquals(16, vector.runCount());
        assertEquals(1d, vector.guardMetricMeans().get("empire_paid_replacement_valid"));
        assertEquals(1d, vector.guardMetricMeans().get("union_paid_replacement_valid"));
        assertEquals(1d, vector.guardMetricMeans().get("matched_latency_guard_passed"));
        assertTrue(vector.metricMeans().get("empire_replacement_latency_seconds") > 0d);
        assertTrue(vector.metricMeans().get("union_replacement_latency_seconds") > 0d);
        assertTrue(vector.metricMeans().get("matched_replacement_latency_differential")
                <= MAX_MATCHED_REPLACEMENT_LATENCY_DIFFERENTIAL + 1e-12d);
        assertEquals(0, vector.hardRuleBreachCount());

        LinkedHashMap<String, Object> archive = new LinkedHashMap<>();
        archive.put("scenarioId", vector.scenarioId());
        archive.put("scenarioVersion", vector.scenarioVersion());
        archive.put("variantId", vector.variantId());
        archive.put("profileId", vector.profileId());
        archive.put("pairedSeedCount", vector.pairedSeedCount());
        archive.put("runCount", vector.runCount());
        archive.put("metricMeans", vector.metricMeans());
        archive.put("guardMetricMeans", vector.guardMetricMeans());
        archive.put("hardRuleBreachCount", vector.hardRuleBreachCount());
        archive.put("evidenceFingerprint", vector.evidenceFingerprint());
        archive.put("observations", vector.observations());
        Stage22CorePairEvidenceArchive.write(
                "B14-paid-replacement-paired-8",
                archive,
                "Finite Stage-21G fresh-identity replacement latency and raw material burdens only; continuous post-war capability curves, manufacturing/loss campaign integration and recoveryT50/recoveryT80 remain open.");
    }

    private static double relativeDifferential(double first, double second) {
        double denominator = Math.max(first, second);
        if (!(denominator > 0d) || !Double.isFinite(denominator)) {
            throw new IllegalArgumentException("Replacement latencies must be finite and positive");
        }
        return Math.abs(first - second) / denominator;
    }
}
