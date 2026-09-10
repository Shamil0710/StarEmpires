package com.spacesim.content;

import com.spacesim.world.Stage22CorePairDistributedRaidProbe;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B06 machine evidence for distributed low-intensity raid sustainment.
 *
 * <p>Each mirrored cell crosses three exact-core physical raid payloads per faction through the
 * ordinary Stage-21E supply/readiness authority. Two independently supplied lanes continue; one
 * lane with lost observed supply access submits the ordinary withdrawal decision. A missing physical
 * FleetId fails closed instead of leaving a detached strategic raid alive. The same coordinate also
 * runs the existing common-policy Stage-19 patrol contest to prove actor-bounded tactical coverage
 * without adding a raid-specific combat modifier.</p>
 *
 * <p>This is an operational B06 slice, not the final campaign-resilience closure: multi-system patrol
 * scheduling, repeated physical encounter consequences and recovery curves remain coupled to the
 * wider B06/B13/B14 campaign evidence.</p>
 */
class Stage22CorePairDistributedRaidMachineEvidenceAcceptanceTest {

    @Test
    void b06RunsThirtyPairedDistributedRaidSupplyAndPatrolCells() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B06",
                "distributed_raid_supply_patrol_authority",
                "stage19-stage21.current",
                Stage22CorePairEvidenceProfile.schedule(30),
                (scenario, variant, profile, coordinate) -> {
                    var raids = Stage22CorePairDistributedRaidProbe.run(coordinate.permutation());
                    var repeat = Stage22CorePairDistributedRaidProbe.run(coordinate.permutation());
                    var patrol = Stage22CorePairTacticalProbe.run(
                            Stage22CorePairTacticalProbe.Variant.PATROL,
                            coordinate,
                            false);

                    boolean distributedShape = raids.empire().decisions().size() == 3
                            && raids.union().decisions().size() == 3;
                    boolean suppliedLanesContinue = raids.empire().continuingRaids() == 2
                            && raids.union().continuingRaids() == 2;
                    boolean unsupportedLaneWithdraws = raids.empire().withdrawingRaids() == 1
                            && raids.union().withdrawingRaids() == 1;
                    boolean missingForceFailsClosed = raids.empire().missingForceDecision()
                            == SupplyDecision.FAIL_NO_SURVIVORS
                            && raids.union().missingForceDecision() == SupplyDecision.FAIL_NO_SURVIVORS;
                    boolean patrolCoverageObserved = patrol.valid()
                            && patrol.empireVisibleTicks() > 0L
                            && patrol.unionVisibleTicks() > 0L
                            && patrol.unauthorizedTargetTicks() == 0L;
                    boolean deterministicRepeat = raids.equals(repeat);

                    List<String> breaches = new ArrayList<>();
                    if (!distributedShape) breaches.add("b06_not_three_independent_raid_lanes");
                    if (!suppliedLanesContinue) breaches.add("b06_supplied_raid_lane_does_not_continue");
                    if (!unsupportedLaneWithdraws) breaches.add("b06_lost_supply_not_visible_to_raid_authority");
                    if (!missingForceFailsClosed) breaches.add("b06_missing_physical_force_leaves_raid_alive");
                    if (!patrolCoverageObserved) breaches.add("b06_common_patrol_not_actor_bounded_or_observing");
                    if (!deterministicRepeat) breaches.add("b06_same_physical_input_not_deterministic");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.ofEntries(
                                    Map.entry("empire_continuing_raids", (double) raids.empire().continuingRaids()),
                                    Map.entry("union_continuing_raids", (double) raids.union().continuingRaids()),
                                    Map.entry("empire_withdrawing_raids", (double) raids.empire().withdrawingRaids()),
                                    Map.entry("union_withdrawing_raids", (double) raids.union().withdrawingRaids()),
                                    Map.entry("empire_patrol_visible_ticks", (double) patrol.empireVisibleTicks()),
                                    Map.entry("union_patrol_visible_ticks", (double) patrol.unionVisibleTicks())),
                            Map.of(
                                    "three_distributed_lanes", distributedShape ? 1d : 0d,
                                    "supplied_lanes_continue", suppliedLanesContinue ? 1d : 0d,
                                    "unsupported_lane_withdraws", unsupportedLaneWithdraws ? 1d : 0d,
                                    "missing_force_fails_closed", missingForceFailsClosed ? 1d : 0d,
                                    "actor_bounded_patrol_coverage", patrolCoverageObserved ? 1d : 0d,
                                    "deterministic_repeat", deterministicRepeat ? 1d : 0d),
                            breaches);
                });

        assertEquals(Stage22CorePairEvidenceProfile.seedCount(30), vector.pairedSeedCount());
        assertEquals(Stage22CorePairEvidenceProfile.seedCount(30) * 2, vector.runCount());
        assertEquals(2d, vector.metricMeans().get("empire_continuing_raids"));
        assertEquals(2d, vector.metricMeans().get("union_continuing_raids"));
        assertEquals(1d, vector.metricMeans().get("empire_withdrawing_raids"));
        assertEquals(1d, vector.metricMeans().get("union_withdrawing_raids"));
        assertTrue(vector.metricMeans().get("empire_patrol_visible_ticks") > 0d);
        assertTrue(vector.metricMeans().get("union_patrol_visible_ticks") > 0d);
        assertEquals(1d, vector.guardMetricMeans().get("three_distributed_lanes"));
        assertEquals(1d, vector.guardMetricMeans().get("supplied_lanes_continue"));
        assertEquals(1d, vector.guardMetricMeans().get("unsupported_lane_withdraws"));
        assertEquals(1d, vector.guardMetricMeans().get("missing_force_fails_closed"));
        assertEquals(1d, vector.guardMetricMeans().get("actor_bounded_patrol_coverage"));
        assertEquals(1d, vector.guardMetricMeans().get("deterministic_repeat"));
        assertEquals(0, vector.hardRuleBreachCount());

        Stage22CorePairCausalSamples.archive("DistributedRaidMachineEvidenceAcceptanceTest", vector, "union_patrol_visible_ticks",
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
                "B06-distributed-raids-supply-patrol-paired-" + Stage22CorePairEvidenceProfile.seedCount(30),
                archive,
                "Paired/mirrored B06 cells. Each faction commits three exact Stage-22 physical raid payloads to independent objectives through ordinary Stage-21E supply/readiness authority: two supplied lanes continue, one supply-denied lane submits ordinary withdrawal, and a missing FleetId fails closed. The same coordinate runs the common Stage-19 patrol policy with actor-bounded contacts. No raid damage scalar, income penalty or faction combat bonus is introduced. Multi-system campaign scheduling, repeated encounter consequences and recovery remain open B06/B13/B14 evidence.");
    }
}
