package com.spacesim.content;

import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 B07 canonical paired machine evidence over ordinary Stage-19 combat authorities. */
class Stage22CorePairEqualBurdenMachineEvidenceAcceptanceTest {
    private static final double MAX_DRY_MASS_KG = 33_000_000d;
    private static final double MAX_LOADED_MASS_KG = 34_000_000d;
    private static final int MAX_CREW = 200;
    private static final double MAX_CONTINUOUS_POWER_W = 3_000_000_000d;
    private static final double MAX_AMMUNITION_MASS_KG = 20_000d;
    private static final double AUTHORIZED_REACTION_MASS_KG = 1_000_000d;
    private static final long AUTHORIZED_ROUNDS = 120L;

    @Test
    void b07ArchivesThirtyPairedEqualBurdenPatrolsWithoutScalarPowerScore() {
        var empireReplacement = Stage22CorePairReplacementProbe.run(true);
        var unionReplacement = Stage22CorePairReplacementProbe.run(false);
        assertTrue(empireReplacement.valid());
        assertTrue(unionReplacement.valid());
        var pair = Stage22CorePairBalanceEvidence.deriveCurrent();

        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B07",
                "equal_burden_patrol_raw_dimensions",
                Stage22CorePairTacticalProbe.POLICY,
                Stage22CorePairExperimentProtocol.pairedSchedule(30),
                (scenario, variant, profile, coordinate) -> {
                    var patrol = Stage22CorePairTacticalProbe.run(
                            Stage22CorePairTacticalProbe.Variant.PATROL,
                            coordinate,
                            false);
                    var restoredPatrol = Stage22CorePairTacticalProbe.run(
                            Stage22CorePairTacticalProbe.Variant.PATROL,
                            coordinate,
                            true);
                    Map<String, Stage22CorePairTacticalProbe.StartingBurden> burdens = patrol.startingBurden().stream()
                            .collect(Collectors.toMap(
                                    Stage22CorePairTacticalProbe.StartingBurden::factionId,
                                    Function.identity()));
                    var empire = burdens.get(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID);
                    var union = burdens.get(Stage22CorePairBalanceEvidence.UNION_FACTION_ID);
                    var empireProtection = patrol.last().protection().stream()
                            .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                            .findFirst()
                            .orElseThrow();
                    var unionProtection = patrol.last().protection().stream()
                            .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                            .findFirst()
                            .orElseThrow();
                    var empireWeapons = patrol.last().weapons().stream()
                            .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                            .findFirst()
                            .orElseThrow();
                    var unionWeapons = patrol.last().weapons().stream()
                            .filter(value -> value.entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                            .findFirst()
                            .orElseThrow();

                    boolean authorization = fitsAuthorization(empire) && fitsAuthorization(union)
                            && Double.compare(empire.reactionMassKg(), union.reactionMassKg()) == 0
                            && empire.rounds() == union.rounds();
                    boolean commonPolicy = patrol.valid()
                            && Stage22CorePairTacticalProbe.POLICY.equals(patrol.policyId())
                            && patrol.unauthorizedTargetTicks() == 0L;
                    boolean saveContinuationStable = patrol.equals(restoredPatrol);
                    boolean bothEngaged = empireProtection.impactsResolved() > 0L
                            && unionProtection.impactsResolved() > 0L
                            && empireWeapons.shotsFired() > 0L
                            && unionWeapons.shotsFired() > 0L
                            && patrol.empireVisibleTicks() > 0L
                            && patrol.unionVisibleTicks() > 0L;
                    boolean ammunitionConserved = empireWeapons.shotsFired() + empireWeapons.ammunitionRounds()
                            == empire.rounds()
                            && unionWeapons.shotsFired() + unionWeapons.ammunitionRounds() == union.rounds();
                    boolean nonPareto = union.dryMassKg() < empire.dryMassKg()
                            && union.crew() < empire.crew()
                            && empireProtection.totalShieldReserveJ() > unionProtection.totalShieldReserveJ()
                            && empireProtection.meanCompartmentIntegrity() > unionProtection.meanCompartmentIntegrity();
                    boolean replacementPaid = empireReplacement.buildSeconds() > unionReplacement.buildSeconds()
                            && empireReplacement.moduleInputMassKg() > unionReplacement.moduleInputMassKg()
                            && Double.compare(empireReplacement.hullInputMassKg(), unionReplacement.hullInputMassKg()) == 0;
                    boolean unionCounterCost = pair.unionDisruption().retoolWorkSeconds() > 0L
                            && pair.unionDisruption().retoolEnergyJ() > 0L
                            && pair.unionDisruption().correlatedDisruption()
                            && pair.unionDisruption().correlatedThroughputDegradation()
                            > pair.unionDisruption().isolatedThroughputDegradation();

                    List<String> breaches = new ArrayList<>();
                    if (!authorization) breaches.add("b07_authorization_envelope_drift");
                    if (!commonPolicy) breaches.add("b07_common_policy_or_actor_bound_drift");
                    if (!saveContinuationStable) breaches.add("b07_physical_start_save_continuation_drift");
                    if (!bothEngaged) breaches.add("b07_no_observed_patrol_exchange");
                    if (!ammunitionConserved) breaches.add("b07_ammunition_not_physically_conserved");
                    if (!nonPareto) breaches.add("b07_raw_dimension_pareto_collapse");
                    if (!replacementPaid) breaches.add("b07_replacement_burden_not_paid");
                    if (!unionCounterCost) breaches.add("b07_union_commonality_countercost_missing");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.ofEntries(
                                    Map.entry("empire_dry_mass_kg", empire.dryMassKg()),
                                    Map.entry("union_dry_mass_kg", union.dryMassKg()),
                                    Map.entry("empire_loaded_mass_kg", empire.loadedMassKg()),
                                    Map.entry("union_loaded_mass_kg", union.loadedMassKg()),
                                    Map.entry("empire_crew", (double) empire.crew()),
                                    Map.entry("union_crew", (double) union.crew()),
                                    Map.entry("empire_continuous_power_w", empire.continuousPowerW()),
                                    Map.entry("union_continuous_power_w", union.continuousPowerW()),
                                    Map.entry("empire_ammunition_mass_kg", empire.ammunitionMassKg()),
                                    Map.entry("union_ammunition_mass_kg", union.ammunitionMassKg()),
                                    Map.entry("empire_reaction_mass_kg", empire.reactionMassKg()),
                                    Map.entry("union_reaction_mass_kg", union.reactionMassKg()),
                                    Map.entry("empire_acceleration_mps2", empire.accelerationMps2()),
                                    Map.entry("union_acceleration_mps2", union.accelerationMps2()),
                                    Map.entry("empire_visible_ticks", (double) patrol.empireVisibleTicks()),
                                    Map.entry("union_visible_ticks", (double) patrol.unionVisibleTicks()),
                                    Map.entry("empire_shots_fired", (double) empireWeapons.shotsFired()),
                                    Map.entry("union_shots_fired", (double) unionWeapons.shotsFired()),
                                    Map.entry("empire_rounds_remaining", (double) empireWeapons.ammunitionRounds()),
                                    Map.entry("union_rounds_remaining", (double) unionWeapons.ammunitionRounds()),
                                    Map.entry("empire_final_shield_reserve_j", empireProtection.totalShieldReserveJ()),
                                    Map.entry("union_final_shield_reserve_j", unionProtection.totalShieldReserveJ()),
                                    Map.entry("empire_final_mean_integrity", empireProtection.meanCompartmentIntegrity()),
                                    Map.entry("union_final_mean_integrity", unionProtection.meanCompartmentIntegrity()),
                                    Map.entry("empire_replacement_seconds", empireReplacement.buildSeconds()),
                                    Map.entry("union_replacement_seconds", unionReplacement.buildSeconds()),
                                    Map.entry("empire_replacement_module_input_kg", empireReplacement.moduleInputMassKg()),
                                    Map.entry("union_replacement_module_input_kg", unionReplacement.moduleInputMassKg()),
                                    Map.entry("union_retool_work_seconds", (double) pair.unionDisruption().retoolWorkSeconds()),
                                    Map.entry("union_retool_energy_j", (double) pair.unionDisruption().retoolEnergyJ())),
                            Map.of(
                                    "authorization_envelope", authorization ? 1d : 0d,
                                    "common_actor_bounded_policy", commonPolicy ? 1d : 0d,
                                    "physical_start_save_continuation", saveContinuationStable ? 1d : 0d,
                                    "both_sides_observed_exchange", bothEngaged ? 1d : 0d,
                                    "ammunition_conserved", ammunitionConserved ? 1d : 0d,
                                    "two_sided_non_pareto", nonPareto ? 1d : 0d,
                                    "replacement_burden_paid", replacementPaid ? 1d : 0d,
                                    "union_commonality_countercost", unionCounterCost ? 1d : 0d),
                            breaches);
                });

        assertEquals(30, vector.pairedSeedCount());
        assertEquals(60, vector.runCount());
        assertEquals(1d, vector.guardMetricMeans().get("authorization_envelope"));
        assertEquals(1d, vector.guardMetricMeans().get("common_actor_bounded_policy"));
        assertEquals(1d, vector.guardMetricMeans().get("physical_start_save_continuation"));
        assertEquals(1d, vector.guardMetricMeans().get("both_sides_observed_exchange"));
        assertEquals(1d, vector.guardMetricMeans().get("ammunition_conserved"));
        assertEquals(1d, vector.guardMetricMeans().get("two_sided_non_pareto"));
        assertEquals(1d, vector.guardMetricMeans().get("replacement_burden_paid"));
        assertEquals(1d, vector.guardMetricMeans().get("union_commonality_countercost"));
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
                "B07-equal-burden-patrol-paired-30",
                archive,
                "Thirty paired/mirrored B07 patrol coordinates using the same Stage-19 tactical policy and exact Stage-22 destroyer fits. Every coordinate is also rerun from an ordinary EntityStateMapper physical-start round trip and must produce the exact same complete sampled evidence. Raw mass, crew, power, ammunition, reaction mass, acceleration, visibility, exchange, surviving protection and paid replacement/retool burdens remain separate dimensions; no scalar power score or faction-wide combat modifier is introduced. This proves scenario-start persistence, not a synthetic mid-flight battle save authority.");
    }

    private static boolean fitsAuthorization(Stage22CorePairTacticalProbe.StartingBurden burden) {
        return burden != null
                && burden.dryMassKg() <= MAX_DRY_MASS_KG
                && burden.loadedMassKg() <= MAX_LOADED_MASS_KG
                && burden.crew() <= MAX_CREW
                && burden.continuousPowerW() <= MAX_CONTINUOUS_POWER_W
                && burden.ammunitionMassKg() <= MAX_AMMUNITION_MASS_KG
                && Double.compare(burden.reactionMassKg(), AUTHORIZED_REACTION_MASS_KG) == 0
                && burden.rounds() == AUTHORIZED_ROUNDS;
    }
}
