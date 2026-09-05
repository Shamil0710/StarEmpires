package com.spacesim.content;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * M22.6 B13 rolling-attrition evidence over the committed Stage-19 authority.
 *
 * <p>Every paired coordinate executes three sequential exact-core encounters. The previous committed
 * physical exit state is the next encounter input, so damage and finite stores can only persist or be
 * consumed. A second run inserts the ordinary entity save/restore boundary between encounters and
 * must produce the identical result vector.</p>
 *
 * <p>This test intentionally stops at the combat-to-committed-state boundary. It does not invent the
 * still-missing campaign production/replacement scheduler and therefore cannot close B13 by itself.</p>
 */
class Stage22CorePairRollingAttritionMachineEvidenceAcceptanceTest {
    @Test
    void b13RunsEightPairedThreeEncounterAttritionCellsWithByteStableContinuation() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B13",
                "three_committed_exact_core_encounters",
                "core_pair.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(8),
                (scenario, variant, profile, coordinate) -> {
                    var baseline = Stage22CorePairTacticalFactory.createDestroyerDuel(coordinate.permutation());
                    var initialEmpire = baseline.weapons().battleState().combatants().stream()
                            .filter(actor -> actor.spec().entityId() == Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Missing initial Empire core combatant"));
                    var initialUnion = baseline.weapons().battleState().combatants().stream()
                            .filter(actor -> actor.spec().entityId() == Stage22CorePairTacticalFactory.UNION_ENTITY_ID)
                            .findFirst()
                            .orElseThrow(() -> new AssertionError("Missing initial Union core combatant"));
                    long empireInitialRounds = initialEmpire.engineering().runtimeState.consumables().ammunitionCount();
                    long unionInitialRounds = initialUnion.engineering().runtimeState.consumables().ammunitionCount();
                    double empireInitialIntegrity = meanCompartmentIntegrity(initialEmpire.engineering());
                    double unionInitialIntegrity = meanCompartmentIntegrity(initialUnion.engineering());

                    var direct = Stage22CorePairEncounterContinuationProbe.run(coordinate, false);
                    var reloaded = Stage22CorePairEncounterContinuationProbe.run(coordinate, true);
                    boolean continuationStable = direct.equals(reloaded);
                    boolean threeCommitted = direct.size() == 3;

                    var firstEmpire = combatant(direct.get(0), Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
                    var lastEmpire = combatant(direct.get(direct.size() - 1), Stage22CorePairTacticalFactory.EMPIRE_ENTITY_ID);
                    var firstUnion = combatant(direct.get(0), Stage22CorePairTacticalFactory.UNION_ENTITY_ID);
                    var lastUnion = combatant(direct.get(direct.size() - 1), Stage22CorePairTacticalFactory.UNION_ENTITY_ID);

                    long empireFirstRounds = firstEmpire.runtimeState().consumables().ammunitionCount();
                    long empireLastRounds = lastEmpire.runtimeState().consumables().ammunitionCount();
                    long unionFirstRounds = firstUnion.runtimeState().consumables().ammunitionCount();
                    long unionLastRounds = lastUnion.runtimeState().consumables().ammunitionCount();
                    double empireFirstIntegrity = meanCompartmentIntegrity(firstEmpire);
                    double empireLastIntegrity = meanCompartmentIntegrity(lastEmpire);
                    double unionFirstIntegrity = meanCompartmentIntegrity(firstUnion);
                    double unionLastIntegrity = meanCompartmentIntegrity(lastUnion);

                    boolean finiteStoresPersist = empireLastRounds <= empireFirstRounds
                            && unionLastRounds <= unionFirstRounds;
                    boolean physicalDamagePersists = empireLastIntegrity <= empireFirstIntegrity + 1e-12d
                            && unionLastIntegrity <= unionFirstIntegrity + 1e-12d;
                    // Rolling attrition is measured over the complete declared three-contact chain. The
                    // previous version compared encounter-one exit to encounter-three exit and therefore
                    // discarded all material expenditure/damage from the first committed contact.
                    boolean materialAttritionObserved = empireLastRounds < empireInitialRounds
                            || unionLastRounds < unionInitialRounds
                            || empireLastIntegrity < empireInitialIntegrity - 1e-12d
                            || unionLastIntegrity < unionInitialIntegrity - 1e-12d;

                    List<String> breaches = new ArrayList<>();
                    if (!continuationStable) breaches.add("b13_save_continuation_drift");
                    if (!threeCommitted) breaches.add("b13_three_encounter_chain_not_completed");
                    if (!finiteStoresPersist) breaches.add("b13_finite_stores_refilled_between_encounters");
                    if (!physicalDamagePersists) breaches.add("b13_damage_recovered_without_repair_authority");
                    if (!materialAttritionObserved) breaches.add("b13_no_material_attrition_observed");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.ofEntries(
                                    Map.entry("encounter_count", (double) direct.size()),
                                    Map.entry("empire_initial_rounds", (double) empireInitialRounds),
                                    Map.entry("empire_first_rounds", (double) empireFirstRounds),
                                    Map.entry("empire_last_rounds", (double) empireLastRounds),
                                    Map.entry("union_initial_rounds", (double) unionInitialRounds),
                                    Map.entry("union_first_rounds", (double) unionFirstRounds),
                                    Map.entry("union_last_rounds", (double) unionLastRounds),
                                    Map.entry("empire_initial_mean_integrity", empireInitialIntegrity),
                                    Map.entry("empire_first_mean_integrity", empireFirstIntegrity),
                                    Map.entry("empire_last_mean_integrity", empireLastIntegrity),
                                    Map.entry("union_initial_mean_integrity", unionInitialIntegrity),
                                    Map.entry("union_first_mean_integrity", unionFirstIntegrity),
                                    Map.entry("union_last_mean_integrity", unionLastIntegrity)),
                            Map.of(
                                    "save_continuation_stable", continuationStable ? 1d : 0d,
                                    "three_committed_encounters", threeCommitted ? 1d : 0d,
                                    "finite_stores_persist", finiteStoresPersist ? 1d : 0d,
                                    "physical_damage_persists", physicalDamagePersists ? 1d : 0d,
                                    "material_attrition_observed", materialAttritionObserved ? 1d : 0d),
                            breaches);
                });

        assertEquals(8, vector.pairedSeedCount());
        assertEquals(16, vector.runCount());
        assertEquals(3d, vector.metricMeans().get("encounter_count"));
        assertEquals(1d, vector.guardMetricMeans().get("save_continuation_stable"));
        assertEquals(1d, vector.guardMetricMeans().get("three_committed_encounters"));
        assertEquals(1d, vector.guardMetricMeans().get("finite_stores_persist"));
        assertEquals(1d, vector.guardMetricMeans().get("physical_damage_persists"));
        assertEquals(1d, vector.guardMetricMeans().get("material_attrition_observed"));
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
                "B13-rolling-attrition-paired-8",
                archive,
                "Three committed exact-core Stage-19 encounters with seeded mirrored contact geometry, save/restore continuation and finite stores. Material attrition is measured from the declared pre-contact baseline through the final committed exit. Production, paid replacement cadence, backlog and long-war campaign trajectory remain open.");
    }

    private static Stage19ExactTacticalEncounterResolver.CombatantResult combatant(
            Stage19ExactTacticalEncounterResolver.Result result,
            long entityId) {
        return result.combatants().stream()
                .filter(actor -> actor.entityId() == entityId)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing core combatant " + entityId));
    }

    private static double meanCompartmentIntegrity(Stage19ExactTacticalEncounterResolver.CombatantResult actor) {
        var values = actor.instanceState().damage().compartmentIntegrityById().values();
        if (values.isEmpty()) {
            throw new AssertionError("Core combatant has no compartment integrity state");
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    private static double meanCompartmentIntegrity(EngineeringComponent component) {
        var values = component.instanceState.damage().compartmentIntegrityById().values();
        if (values.isEmpty()) {
            throw new AssertionError("Initial core combatant has no compartment integrity state");
        }
        return values.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }
}
