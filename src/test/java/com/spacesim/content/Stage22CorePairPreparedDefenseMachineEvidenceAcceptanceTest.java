package com.spacesim.content;

import com.spacesim.world.Stage22CorePairPreparedDefenseProbe;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M22.6 B09 prepared-defense machine evidence over ordinary Stage-21D/21E authorities.
 *
 * <p>The test deliberately archives raw readiness and reinforcement/supply outcomes rather than
 * converting doctrine labels into combat bonuses. Exact Stage-22 engineering state is projected by
 * {@code FleetReadinessEvaluator}; the ordinary reinforcement service rejects a reserve before
 * physical arrival; and the ordinary strategic-operation supply review exposes loss of prepared
 * supply as withdrawal pressure.</p>
 *
 * <p>This closes the operational-authority seam only. The longer prepared-system endurance contour
 * and Empire-vs-Union scale/replacement outcome remain separate B09 evidence requirements.</p>
 */
class Stage22CorePairPreparedDefenseMachineEvidenceAcceptanceTest {
    @Test
    void b09RunsEightPairedPreparedDefenseAuthorityCellsWithoutHiddenDefensiveGrants() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B09",
                "prepared_defense_operational_authority",
                "stage21d-stage21e.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(8),
                (scenario, variant, profile, coordinate) -> {
                    var result = Stage22CorePairPreparedDefenseProbe.run(coordinate.permutation());
                    var empire = result.empire();
                    var union = result.union();

                    boolean empireReady = empire.defenderReadinessBps() > 0
                            && empire.reserveReadinessBps() > 0;
                    boolean unionReady = union.defenderReadinessBps() > 0
                            && union.reserveReadinessBps() > 0;
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

                    List<String> breaches = new ArrayList<>();
                    if (!empireReady) breaches.add("b09_empire_exact_fit_not_operation_ready");
                    if (!unionReady) breaches.add("b09_union_exact_fit_not_operation_ready");
                    if (!arrivalGate) breaches.add("b09_reinforcement_physical_arrival_gate_drift");
                    if (!preparedContinuation) breaches.add("b09_prepared_supply_does_not_continue");
                    if (!supplyLossVisible) breaches.add("b09_supply_loss_hidden_from_operation");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.of(
                                    "empire_defender_readiness_bps", (double) empire.defenderReadinessBps(),
                                    "empire_reserve_readiness_bps", (double) empire.reserveReadinessBps(),
                                    "union_defender_readiness_bps", (double) union.defenderReadinessBps(),
                                    "union_reserve_readiness_bps", (double) union.reserveReadinessBps(),
                                    "empire_committed_participants", (double) empire.committedParticipantCount(),
                                    "union_committed_participants", (double) union.committedParticipantCount()),
                            Map.of(
                                    "empire_exact_fit_ready", empireReady ? 1d : 0d,
                                    "union_exact_fit_ready", unionReady ? 1d : 0d,
                                    "reinforcement_physical_arrival_gate", arrivalGate ? 1d : 0d,
                                    "prepared_supply_continues", preparedContinuation ? 1d : 0d,
                                    "supply_loss_visible", supplyLossVisible ? 1d : 0d),
                            breaches);
                });

        assertEquals(8, vector.pairedSeedCount());
        assertEquals(16, vector.runCount());
        assertEquals(2d, vector.metricMeans().get("empire_committed_participants"));
        assertEquals(2d, vector.metricMeans().get("union_committed_participants"));
        assertEquals(1d, vector.guardMetricMeans().get("empire_exact_fit_ready"));
        assertEquals(1d, vector.guardMetricMeans().get("union_exact_fit_ready"));
        assertEquals(1d, vector.guardMetricMeans().get("reinforcement_physical_arrival_gate"));
        assertEquals(1d, vector.guardMetricMeans().get("prepared_supply_continues"));
        assertEquals(1d, vector.guardMetricMeans().get("supply_loss_visible"));
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
                "B09-prepared-defense-operational-paired-8",
                archive,
                "Eight paired/mirrored cells crossing exact Stage-22 engineering payloads into ordinary Stage-21D readiness and Stage-21E reinforcement/supply review. Reserve admission is physical-location gated and loss of prepared supply produces ordinary withdrawal pressure. Prepared-system endurance and Empire-versus-Union scale/replacement outcomes remain open.");
    }
}
