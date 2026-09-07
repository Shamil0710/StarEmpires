package com.spacesim.content;

import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 Gate C cross-scenario non-dominance evidence.
 *
 * <p>This acceptance slice deliberately does not invent a faction power score. It composes raw
 * physical observations from the accepted B07 Stage-19 patrol authority with finite Stage-21G
 * replacement burden and the authored Industrial Union retool/commonality counter-cost. A package
 * may therefore be better on some dimensions and worse on others, but neither package may become a
 * global Pareto winner once tactical resilience, resource burden, replacement tempo and adaptation
 * exposure are considered together.</p>
 */
class Stage22CorePairCrossScenarioDominanceAcceptanceTest {

    @Test
    void gateCRejectsCrossScenarioParetoCollapseWithoutCompositePowerScore() {
        var empireReplacement = Stage22CorePairReplacementProbe.run(true);
        var unionReplacement = Stage22CorePairReplacementProbe.run(false);
        var pair = Stage22CorePairBalanceEvidence.deriveCurrent();

        assertTrue(empireReplacement.valid(), empireReplacement.toString());
        assertTrue(unionReplacement.valid(), unionReplacement.toString());

        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B07",
                "cross_scenario_non_pareto_review",
                Stage22CorePairTacticalProbe.POLICY,
                Stage22CorePairExperimentProtocol.pairedSchedule(30),
                (scenario, variant, profile, coordinate) -> {
                    var patrol = Stage22CorePairTacticalProbe.run(
                            Stage22CorePairTacticalProbe.Variant.PATROL,
                            coordinate,
                            false);
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

                    boolean ordinaryExchange = patrol.valid()
                            && empireProtection.impactsResolved() > 0L
                            && unionProtection.impactsResolved() > 0L;
                    boolean unionResourceEfficiency = union.dryMassKg() < empire.dryMassKg()
                            && union.crew() < empire.crew();
                    boolean empireSurvivability = empireProtection.totalShieldReserveJ()
                            > unionProtection.totalShieldReserveJ()
                            && empireProtection.meanCompartmentIntegrity()
                            > unionProtection.meanCompartmentIntegrity();
                    boolean unionReplacementTempo = unionReplacement.buildSeconds()
                            < empireReplacement.buildSeconds()
                            && unionReplacement.moduleInputMassKg()
                            < empireReplacement.moduleInputMassKg();
                    boolean matchedHullMaterialBurden = Double.compare(
                            empireReplacement.hullInputMassKg(), unionReplacement.hullInputMassKg()) == 0;
                    boolean unionAdaptationCounterCost = pair.unionDisruption().retoolWorkSeconds() > 0L
                            && pair.unionDisruption().retoolEnergyJ() > 0L
                            && pair.unionDisruption().correlatedDisruption()
                            && pair.unionDisruption().correlatedThroughputDegradation()
                            > pair.unionDisruption().isolatedThroughputDegradation();
                    boolean noGlobalParetoWinner = ordinaryExchange
                            && unionResourceEfficiency
                            && empireSurvivability
                            && unionReplacementTempo
                            && matchedHullMaterialBurden
                            && unionAdaptationCounterCost;

                    ArrayList<String> breaches = new ArrayList<>();
                    if (!ordinaryExchange) breaches.add("cross_scenario_patrol_exchange_invalid");
                    if (!unionResourceEfficiency) breaches.add("union_resource_efficiency_advantage_missing");
                    if (!empireSurvivability) breaches.add("empire_survivability_advantage_missing");
                    if (!unionReplacementTempo) breaches.add("union_replacement_tempo_advantage_missing");
                    if (!matchedHullMaterialBurden) breaches.add("replacement_hull_material_burden_drift");
                    if (!unionAdaptationCounterCost) breaches.add("union_adaptation_countercost_missing");
                    if (!noGlobalParetoWinner) breaches.add("cross_scenario_global_pareto_collapse");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.ofEntries(
                                    Map.entry("empire_dry_mass_kg", empire.dryMassKg()),
                                    Map.entry("union_dry_mass_kg", union.dryMassKg()),
                                    Map.entry("empire_crew", (double) empire.crew()),
                                    Map.entry("union_crew", (double) union.crew()),
                                    Map.entry("empire_final_shield_reserve_j", empireProtection.totalShieldReserveJ()),
                                    Map.entry("union_final_shield_reserve_j", unionProtection.totalShieldReserveJ()),
                                    Map.entry("empire_final_mean_integrity", empireProtection.meanCompartmentIntegrity()),
                                    Map.entry("union_final_mean_integrity", unionProtection.meanCompartmentIntegrity()),
                                    Map.entry("empire_replacement_seconds", empireReplacement.buildSeconds()),
                                    Map.entry("union_replacement_seconds", unionReplacement.buildSeconds()),
                                    Map.entry("empire_replacement_hull_input_kg", empireReplacement.hullInputMassKg()),
                                    Map.entry("union_replacement_hull_input_kg", unionReplacement.hullInputMassKg()),
                                    Map.entry("empire_replacement_module_input_kg", empireReplacement.moduleInputMassKg()),
                                    Map.entry("union_replacement_module_input_kg", unionReplacement.moduleInputMassKg()),
                                    Map.entry("union_retool_work_seconds", (double) pair.unionDisruption().retoolWorkSeconds()),
                                    Map.entry("union_retool_energy_j", (double) pair.unionDisruption().retoolEnergyJ()),
                                    Map.entry("union_isolated_throughput_degradation",
                                            pair.unionDisruption().isolatedThroughputDegradation()),
                                    Map.entry("union_correlated_throughput_degradation",
                                            pair.unionDisruption().correlatedThroughputDegradation())),
                            Map.of(
                                    "ordinary_stage19_exchange", ordinaryExchange ? 1d : 0d,
                                    "union_resource_efficiency_advantage", unionResourceEfficiency ? 1d : 0d,
                                    "empire_survivability_advantage", empireSurvivability ? 1d : 0d,
                                    "union_replacement_tempo_advantage", unionReplacementTempo ? 1d : 0d,
                                    "matched_hull_material_burden", matchedHullMaterialBurden ? 1d : 0d,
                                    "union_adaptation_countercost", unionAdaptationCounterCost ? 1d : 0d,
                                    "no_global_pareto_winner", noGlobalParetoWinner ? 1d : 0d),
                            breaches);
                });

        assertEquals(30, vector.pairedSeedCount());
        assertEquals(60, vector.runCount());
        assertEquals(1d, vector.guardMetricMeans().get("ordinary_stage19_exchange"));
        assertEquals(1d, vector.guardMetricMeans().get("union_resource_efficiency_advantage"));
        assertEquals(1d, vector.guardMetricMeans().get("empire_survivability_advantage"));
        assertEquals(1d, vector.guardMetricMeans().get("union_replacement_tempo_advantage"));
        assertEquals(1d, vector.guardMetricMeans().get("matched_hull_material_burden"));
        assertEquals(1d, vector.guardMetricMeans().get("union_adaptation_countercost"));
        assertEquals(1d, vector.guardMetricMeans().get("no_global_pareto_winner"));
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
                "gate-c-cross-scenario-non-pareto-paired-30",
                archive,
                "Thirty paired/mirrored ordinary B07 Stage-19 patrol coordinates composed with the ordinary paid Stage-21G replacement authority and finite Union retool/commonality exposure. Raw dimensions remain separate: Union retains lower dry-mass/crew and faster/lower-module replacement, Empire retains greater surviving shield/compartment protection, and Union throughput/commonality retains a finite correlated adaptation cost. No composite power score, faction-wide modifier or synthetic outcome authority is introduced; this is the cross-scenario dominance review, not a waiver of still-open campaign scenarios or human B18-B20 gates.");
    }
}
