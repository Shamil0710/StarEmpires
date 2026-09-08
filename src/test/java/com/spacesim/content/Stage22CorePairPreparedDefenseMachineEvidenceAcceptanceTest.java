package com.spacesim.content;

import com.spacesim.ship.Stage22CorePairTacticalFactory;
import com.spacesim.world.FleetReadinessState;
import com.spacesim.world.Stage22CorePairPreparedDefenseProbe;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B09 prepared-defense machine evidence over ordinary Stage-19/21 authorities.
 *
 * <p>The test archives raw readiness, reinforcement/supply and equal-policy tactical outcomes rather
 * than converting doctrine labels into combat bonuses. Exact Stage-22 engineering state is projected
 * by the ordinary readiness evaluator; reserve admission is physical-location gated; and loss of
 * prepared supply becomes ordinary withdrawal pressure. The same seeded/mirrored Stage-19 patrol
 * then checks the authored robustness axis while the existing paid replacement authority checks the
 * Industrial Union's distinct replacement-throughput contest path.</p>
 *
 * <p>This does not invent a synthetic system-defense simulator. Longer multi-wave campaign endurance
 * remains part of the wider B09/B13 campaign evidence boundary.</p>
 */
class Stage22CorePairPreparedDefenseMachineEvidenceAcceptanceTest {
    private static final int PREPARED_MISSION_FLOOR_BPS = 1_000;

    @Test
    void b09RunsEightPairedPreparedDefenseCellsThroughOrdinaryAuthorities() {
        var empireReplacement = Stage22CorePairReplacementProbe.run(true);
        var unionReplacement = Stage22CorePairReplacementProbe.run(false);
        assertTrue(empireReplacement.valid());
        assertTrue(unionReplacement.valid());
        assertTrue(unionReplacement.buildSeconds() < empireReplacement.buildSeconds(),
                "B09 contest path requires the authored Union paid-replacement throughput advantage");

        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B09",
                "prepared_defense_operational_authority",
                "stage19-stage21.current",
                Stage22CorePairEvidenceProfile.schedule(8),
                (scenario, variant, profile, coordinate) -> {
                    var result = Stage22CorePairPreparedDefenseProbe.run(coordinate.permutation());
                    var empire = result.empire();
                    var union = result.union();
                    var empireReadiness = empire.defenderReadiness();
                    var unionReadiness = union.defenderReadiness();
                    var patrol = Stage22CorePairTacticalProbe.run(
                            Stage22CorePairTacticalProbe.Variant.PATROL,
                            coordinate,
                            false);
                    var empireProtection = protection(
                            patrol,
                            Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
                    var unionProtection = protection(
                            patrol,
                            Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

                    boolean empireReady = empire.defenderReadiness().missionCapable(PREPARED_MISSION_FLOOR_BPS)
                            && empire.reserveReadiness().missionCapable(PREPARED_MISSION_FLOOR_BPS);
                    boolean unionReady = union.defenderReadiness().missionCapable(PREPARED_MISSION_FLOOR_BPS)
                            && union.reserveReadiness().missionCapable(PREPARED_MISSION_FLOOR_BPS);
                    boolean arrivalGate = empire.rejectedBeforePhysicalArrival()
                            && union.rejectedBeforePhysicalArrival()
                            && empire.attachedAfterPhysicalArrival()
                            && union.attachedAfterPhysicalArrival()
                            && empire.committedParticipantCount() == 2
                            && union.committedParticipantCount() == 2;
                    boolean preparedContinuation = empire.preparedDecision() == SupplyDecision.CONTINUE
                            && union.preparedDecision() == SupplyDecision.CONTINUE;
                    boolean supplyLossVisible = empire.unsupportedDecision()
                            == SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER
                            && union.unsupportedDecision() == SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER;
                    boolean commonTacticalPolicyValid = patrol.valid();
                    boolean empireRobustnessVisible = empireProtection.meanCompartmentIntegrity()
                            > unionProtection.meanCompartmentIntegrity()
                            && empireProtection.totalShieldReserveJ() > unionProtection.totalShieldReserveJ();
                    boolean unionReplacementContestPath = unionReplacement.buildSeconds()
                            < empireReplacement.buildSeconds();

                    List<String> breaches = new ArrayList<>();
                    if (!empireReady) breaches.add("b09_empire_exact_fit_not_operation_ready");
                    if (!unionReady) breaches.add("b09_union_exact_fit_not_operation_ready");
                    if (!arrivalGate) breaches.add("b09_reinforcement_physical_arrival_gate_drift");
                    if (!preparedContinuation) breaches.add("b09_prepared_supply_does_not_continue");
                    if (!supplyLossVisible) breaches.add("b09_supply_loss_hidden_from_operation");
                    if (!commonTacticalPolicyValid) breaches.add("b09_common_tactical_policy_invalid");
                    if (!empireRobustnessVisible) breaches.add("b09_empire_prepared_robustness_identity_lost");
                    if (!unionReplacementContestPath) breaches.add("b09_union_replacement_contest_path_lost");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.ofEntries(
                                    Map.entry("empire_defender_readiness_bps", (double) empireReadiness.overallBps()),
                                    Map.entry("empire_ammunition_readiness_bps", (double) empireReadiness.ammunitionBps()),
                                    Map.entry("empire_propellant_readiness_bps", (double) empireReadiness.propellantBps()),
                                    Map.entry("empire_structural_readiness_bps", (double) empireReadiness.structuralBps()),
                                    Map.entry("empire_sensor_readiness_bps", (double) empireReadiness.sensorsBps()),
                                    Map.entry("empire_maintenance_readiness_bps", (double) empireReadiness.maintenanceBps()),
                                    Map.entry("union_defender_readiness_bps", (double) unionReadiness.overallBps()),
                                    Map.entry("union_ammunition_readiness_bps", (double) unionReadiness.ammunitionBps()),
                                    Map.entry("union_propellant_readiness_bps", (double) unionReadiness.propellantBps()),
                                    Map.entry("union_structural_readiness_bps", (double) unionReadiness.structuralBps()),
                                    Map.entry("union_sensor_readiness_bps", (double) unionReadiness.sensorsBps()),
                                    Map.entry("union_maintenance_readiness_bps", (double) unionReadiness.maintenanceBps()),
                                    Map.entry("empire_committed_participants", (double) empire.committedParticipantCount()),
                                    Map.entry("union_committed_participants", (double) union.committedParticipantCount()),
                                    Map.entry("empire_final_mean_integrity", empireProtection.meanCompartmentIntegrity()),
                                    Map.entry("union_final_mean_integrity", unionProtection.meanCompartmentIntegrity()),
                                    Map.entry("empire_final_shield_reserve_j", empireProtection.totalShieldReserveJ()),
                                    Map.entry("union_final_shield_reserve_j", unionProtection.totalShieldReserveJ()),
                                    Map.entry("empire_paid_replacement_seconds", empireReplacement.buildSeconds()),
                                    Map.entry("union_paid_replacement_seconds", unionReplacement.buildSeconds())),
                            Map.of(
                                    "empire_exact_fit_ready", empireReady ? 1d : 0d,
                                    "union_exact_fit_ready", unionReady ? 1d : 0d,
                                    "reinforcement_physical_arrival_gate", arrivalGate ? 1d : 0d,
                                    "prepared_supply_continues", preparedContinuation ? 1d : 0d,
                                    "supply_loss_visible", supplyLossVisible ? 1d : 0d,
                                    "common_tactical_policy_valid", commonTacticalPolicyValid ? 1d : 0d,
                                    "empire_robustness_visible", empireRobustnessVisible ? 1d : 0d,
                                    "union_replacement_contest_path", unionReplacementContestPath ? 1d : 0d),
                            breaches);
                });

        assertEquals(Stage22CorePairEvidenceProfile.seedCount(8), vector.pairedSeedCount());
        assertEquals(Stage22CorePairEvidenceProfile.seedCount(8) * 2, vector.runCount());
        assertTrue(vector.metricMeans().get("empire_defender_readiness_bps") >= PREPARED_MISSION_FLOOR_BPS);
        assertTrue(vector.metricMeans().get("union_defender_readiness_bps") >= PREPARED_MISSION_FLOOR_BPS);
        assertEquals(2d, vector.metricMeans().get("empire_committed_participants"));
        assertEquals(2d, vector.metricMeans().get("union_committed_participants"));
        assertEquals(1d, vector.guardMetricMeans().get("empire_exact_fit_ready"));
        assertEquals(1d, vector.guardMetricMeans().get("union_exact_fit_ready"));
        assertEquals(1d, vector.guardMetricMeans().get("reinforcement_physical_arrival_gate"));
        assertEquals(1d, vector.guardMetricMeans().get("prepared_supply_continues"));
        assertEquals(1d, vector.guardMetricMeans().get("supply_loss_visible"));
        assertEquals(1d, vector.guardMetricMeans().get("common_tactical_policy_valid"));
        assertEquals(1d, vector.guardMetricMeans().get("empire_robustness_visible"));
        assertEquals(1d, vector.guardMetricMeans().get("union_replacement_contest_path"));
        assertEquals(0, vector.hardRuleBreachCount());

        Stage22CorePairCausalSamples.archive("PreparedDefenseMachineEvidenceAcceptanceTest", vector, "union_final_mean_integrity",
                coordinate -> Stage22CorePairTacticalProbe.run(
                        Stage22CorePairTacticalProbe.Variant.PATROL, coordinate, true));

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
                "B09-prepared-defense-operational-paired-" + Stage22CorePairEvidenceProfile.seedCount(8),
                archive,
                "Paired/mirrored cells crossing exact Stage-22 fits through ordinary readiness, reinforcement, supply and common Stage-19 tactical authorities. Prepared starting magazines are finite and authored before operation admission at the declared ten-percent readiness floor; no in-operation refill is granted. Empire retains the authored robustness contour under the same tactical policy; Industrial Union retains a separate paid-replacement throughput contest path. No faction-specific defensive modifier is introduced; longer campaign/multi-wave endurance remains coupled to B13.");
    }

    private static com.spacesim.ship.LiveTacticalBattleWeaponRuntime.TargetProtectionFingerprint protection(
            Stage22CorePairTacticalProbe.Evidence evidence,
            long entityId) {
        return evidence.last().protection().stream()
                .filter(value -> value.entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing tactical protection fingerprint " + entityId));
    }
}
