package com.spacesim.content;

import com.spacesim.content.Stage22IndustrialUnionProductionState.YardSeriesState;
import com.spacesim.persistence.Stage22IndustrialUnionProductionStateCodec;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 acceptance for deterministic machine evidence without a shadow scenario simulator. */
class Stage22CorePairMachineEvidenceBatchTest {
    @Test
    void b00CapturesRealPackageValidatorEvidenceAcrossExactMirroredPairs() {
        var schedule = Stage22CorePairExperimentProtocol.pairedSchedule(2);
        var first = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "core_pair_rc",
                "profiles.current",
                schedule,
                (scenario, variant, profile, coordinate) -> {
                    var empire = Stage22EmpirePackageValidator.validateDefault();
                    var union = Stage22IndustrialUnionPackageValidator.validateDefault();
                    return payload(
                            Map.of(
                                    "empire_family_count", (double) empire.familyMetrics().size(),
                                    "union_family_count", (double) union.familyMetrics().size(),
                                    "fingerprints_distinct",
                                    empire.packageFingerprint().equals(union.packageFingerprint()) ? 0d : 1d),
                            Map.of("authority_validation_passed", 1d),
                            List.of());
                });
        var second = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "core_pair_rc",
                "profiles.current",
                schedule,
                (scenario, variant, profile, coordinate) -> {
                    var empire = Stage22EmpirePackageValidator.validateDefault();
                    var union = Stage22IndustrialUnionPackageValidator.validateDefault();
                    return payload(
                            Map.of(
                                    "empire_family_count", (double) empire.familyMetrics().size(),
                                    "union_family_count", (double) union.familyMetrics().size(),
                                    "fingerprints_distinct",
                                    empire.packageFingerprint().equals(union.packageFingerprint()) ? 0d : 1d),
                            Map.of("authority_validation_passed", 1d),
                            List.of());
                });

        assertEquals(2, first.pairedSeedCount());
        assertEquals(4, first.runCount());
        assertEquals(9d, first.metricMeans().get("empire_family_count"));
        assertEquals(9d, first.metricMeans().get("union_family_count"));
        assertEquals(1d, first.metricMeans().get("fingerprints_distinct"));
        assertEquals(0, first.hardRuleBreachCount());
        assertEquals(64, first.evidenceFingerprint().length());
        assertEquals(first.evidenceFingerprint(), second.evidenceFingerprint());
    }

    @Test
    void b01UsesRealUnionProductionCodecAndProducesByteStablePersistenceEvidence() {
        String fingerprint = Stage22IndustrialUnionPackageValidator.validateDefault().packageFingerprint();
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B01",
                "union_production_sidecar",
                "persistence.v1",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    var state = new Stage22IndustrialUnionProductionState(
                            Stage22IndustrialUnionProductionState.CURRENT_VERSION,
                            Stage22IndustrialUnionProductionState.STABLE_FACTION_ID,
                            fingerprint,
                            coordinate.seed(),
                            List.of(Stage22IndustrialUnionProductionState.unqualifiedYard(
                                    Stage22IndustrialUnionIndustrialProgram.YARD_ID)));
                    byte[] encoded = Stage22IndustrialUnionProductionStateCodec.encode(state);
                    var decoded = Stage22IndustrialUnionProductionStateCodec.decode(encoded);
                    byte[] reencoded = Stage22IndustrialUnionProductionStateCodec.encode(decoded);
                    boolean stable = Arrays.equals(encoded, reencoded);
                    return payload(
                            Map.of(
                                    "encoded_bytes", (double) encoded.length,
                                    "byte_stable_roundtrip", stable ? 1d : 0d),
                            Map.of(
                                    "stable_faction_id_preserved",
                                    state.stableFactionId().equals(decoded.stableFactionId()) ? 1d : 0d,
                                    "sequence_preserved", state.sequence() == decoded.sequence() ? 1d : 0d),
                            stable ? List.of() : List.of("production_state_roundtrip_drift"));
                });

        assertEquals(1d, vector.metricMeans().get("byte_stable_roundtrip"));
        assertEquals(1d, vector.guardMetricMeans().get("stable_faction_id_preserved"));
        assertEquals(1d, vector.guardMetricMeans().get("sequence_preserved"));
        assertEquals(0, vector.hardRuleBreachCount());
    }

    @Test
    void b04AndB05MeasureRealPricedSubstitutionAndCorrelatedNetworkLoss() {
        var base = Stage22CommonManufacturingProfiles.definitions().stream()
                .filter(value -> value.id().equals(Stage22CommonManufacturingProfiles.CARGO_TANK_STORES))
                .findFirst().orElseThrow();
        var alternate = Stage22EmpireIndustrialProgram.cargoStructuralSubstitution();
        var disruption = correlatedUnionDisruption();

        var shortage = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B04",
                "critical_material_shortage",
                "production.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> payload(
                        Map.of(
                                "empire_substitution_energy_ratio",
                                alternate.energyJPerOutputKg() / base.energyJPerOutputKg(),
                                "empire_substitution_work_ratio",
                                alternate.workSecondsPerOutputKg() / base.workSecondsPerOutputKg(),
                                "union_correlated_throughput_degradation", disruption.throughputDegradation()),
                        Map.of("union_correlated_disruption", disruption.correlatedDisruption() ? 1d : 0d),
                        disruption.correlatedDisruption() ? List.of() : List.of("union_commonality_downside_missing")));

        var routeLoss = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B05",
                "single_hub_route_loss",
                "production.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    var healthy = Stage22IndustrialUnionCommonalityNetwork.observe(
                            steadyUnionLogisticsYard(),
                            "ship_family.industrial_union.freight",
                            Stage22IndustrialUnionCommonalityNetwork.healthy());
                    var correlated = correlatedUnionDisruption();
                    boolean material = correlated.correlatedDisruption()
                            && correlated.throughputDegradation() >= 0.25d
                            && correlated.throughputDegradation() > healthy.throughputDegradation();
                    return payload(
                            Map.of(
                                    "healthy_throughput_degradation", healthy.throughputDegradation(),
                                    "disrupted_throughput_degradation", correlated.throughputDegradation(),
                                    "disrupted_work_burden", correlated.workBurdenMultiplier()),
                            Map.of("material_correlated_loss", material ? 1d : 0d),
                            material ? List.of() : List.of("hub_route_loss_not_material"));
                });

        assertEquals(1.25d, shortage.metricMeans().get("empire_substitution_energy_ratio"), 1e-12d);
        assertEquals(1.35d, shortage.metricMeans().get("empire_substitution_work_ratio"), 1e-12d);
        assertTrue(shortage.metricMeans().get("union_correlated_throughput_degradation") >= 0.25d);
        assertEquals(0, shortage.hardRuleBreachCount());
        assertEquals(0d, routeLoss.metricMeans().get("healthy_throughput_degradation"), 1e-12d);
        assertTrue(routeLoss.metricMeans().get("disrupted_throughput_degradation") >= 0.25d);
        assertEquals(1d, routeLoss.guardMetricMeans().get("material_correlated_loss"));
        assertEquals(0, routeLoss.hardRuleBreachCount());
        assertNotEquals(shortage.evidenceFingerprint(), routeLoss.evidenceFingerprint());
    }

    @Test
    void b17ObservesFiniteUnionRetoolDebtAndFailClosedSeriesChange() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B17",
                "new_enemy_adaptation",
                "union_retool.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> {
                    YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                            Stage22IndustrialUnionIndustrialProgram.YARD_ID);
                    YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                            yard, "ship_family.industrial_union.freight");
                    boolean blockedBeforePayment;
                    try {
                        Stage22IndustrialUnionIndustrialProgram.modifierFor(
                                pending, "ship_family.industrial_union.freight");
                        blockedBeforePayment = false;
                    } catch (IllegalStateException expected) {
                        blockedBeforePayment = true;
                    }
                    YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                            pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
                    YardSeriesState completed = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
                    boolean completedAfterPayment = "assembly_series.industrial_union.logistics"
                            .equals(completed.activeSeriesId());
                    boolean legal = blockedBeforePayment && completedAfterPayment;
                    return payload(
                            Map.of(
                                    "retool_work_debt_seconds", (double) pending.retoolWorkRemainingSeconds(),
                                    "retool_energy_debt_j", (double) pending.retoolEnergyRemainingJ()),
                            Map.of(
                                    "blocked_before_payment", blockedBeforePayment ? 1d : 0d,
                                    "completed_after_payment", completedAfterPayment ? 1d : 0d),
                            legal ? List.of() : List.of("retool_failed_closed_contract"));
                });

        assertTrue(vector.metricMeans().get("retool_work_debt_seconds") > 0d);
        assertTrue(vector.metricMeans().get("retool_energy_debt_j") > 0d);
        assertEquals(1d, vector.guardMetricMeans().get("blocked_before_payment"));
        assertEquals(1d, vector.guardMetricMeans().get("completed_after_payment"));
        assertEquals(0, vector.hardRuleBreachCount());
    }

    @Test
    void machineBatchRejectsHumanGatesBrokenPairingAndMetricShapeDrift() {
        var validProbe = (Stage22CorePairMachineEvidenceBatch.ScenarioProbe)
                (scenario, variant, profile, coordinate) -> payload(Map.of("value", 1d), Map.of(), List.of());
        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairMachineEvidenceBatch.runScenario(
                "B18", "human", "human", Stage22CorePairExperimentProtocol.pairedSchedule(1), validProbe));
        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairMachineEvidenceBatch.runScenario(
                "B20", "human", "human", Stage22CorePairExperimentProtocol.pairedSchedule(1), validProbe));
        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "broken",
                "broken",
                List.of(Stage22CorePairExperimentProtocol.pairedSchedule(1).get(0)),
                validProbe));
        assertThrows(IllegalArgumentException.class, () -> Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "drift",
                "drift",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> payload(
                        coordinate.permutation() == Stage22CorePairExperimentProtocol.Permutation.DEFAULT
                                ? Map.of("first", 1d)
                                : Map.of("second", 1d),
                        Map.of(),
                        List.of())));
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload payload(
            Map<String, Double> metrics,
            Map<String, Double> guards,
            List<String> breaches) {
        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics, guards, breaches);
    }

    private static YardSeriesState steadyUnionLogisticsYard() {
        YardSeriesState yard = Stage22IndustrialUnionProductionState.unqualifiedYard(
                Stage22IndustrialUnionIndustrialProgram.YARD_ID);
        YardSeriesState pending = Stage22IndustrialUnionIndustrialProgram.beginRetool(
                yard, "ship_family.industrial_union.freight");
        YardSeriesState paid = Stage22IndustrialUnionIndustrialProgram.applyRetoolInputs(
                pending, pending.retoolWorkRemainingSeconds(), pending.retoolEnergyRemainingJ());
        yard = Stage22IndustrialUnionIndustrialProgram.completeRetool(paid);
        for (int index = 0; index < 3; index++) {
            yard = Stage22IndustrialUnionIndustrialProgram.recordCompletedUnit(
                    yard, "ship_family.industrial_union.freight");
        }
        return yard;
    }

    private static Stage22IndustrialUnionCommonalityNetwork.Report correlatedUnionDisruption() {
        LinkedHashMap<String, Double> assemblies = new LinkedHashMap<>();
        Stage22IndustrialUnionCommonalityNetwork.SHARED_ASSEMBLY_IDS.stream().sorted()
                .forEach(id -> assemblies.put(id, 0.75d));
        return Stage22IndustrialUnionCommonalityNetwork.observe(
                steadyUnionLogisticsYard(),
                "ship_family.industrial_union.freight",
                new Stage22IndustrialUnionCommonalityNetwork.Availability(assemblies, 0.75d, 0.75d));
    }
}
