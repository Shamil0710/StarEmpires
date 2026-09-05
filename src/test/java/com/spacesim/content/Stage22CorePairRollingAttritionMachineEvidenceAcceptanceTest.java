package com.spacesim.content;

import com.spacesim.ship.Stage19ExactTacticalEncounterResolver;
import com.spacesim.ship.Stage22CorePairTacticalFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
                    var direct = Stage22CorePairEncounterContinuationProbe.run(coordinate.permutation(), false);
                    var reloaded = Stage22CorePairEncounterContinuationProbe.run(coordinate.permutation(), true);
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
                    boolean materialAttritionObserved = empireLastRounds < empireFirstRounds
                            || unionLastRounds < unionFirstRounds
                            || empireLastIntegrity < empireFirstIntegrity - 1e-12d
                            || unionLastIntegrity < unionFirstIntegrity - 1e-12d;

                    List<String> breaches = new ArrayList<>();
                    if (!continuationStable) breaches.add("b13_save_continuation_drift");
                    if (!threeCommitted) breaches.add("b13_three_encounter_chain_not_completed");
                    if (!finiteStoresPersist) breaches.add("b13_finite_stores_refilled_between_encounters");
                    if (!physicalDamagePersists) breaches.add("b13_damage_recovered_without_repair_authority");
                    if (!materialAttritionObserved) breaches.add("b13_no_material_attrition_observed");

                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.of(
                                    "encounter_count", (double) direct.size(),
                                    "empire_first_rounds", (double) empireFirstRounds,
                                    "empire_last_rounds", (double) empireLastRounds,
                                    "union_first_rounds", (double) unionFirstRounds,
                                    "union_last_rounds", (double) unionLastRounds,
                                    "empire_first_mean_integrity", empireFirstIntegrity,
                                    "empire_last_mean_integrity", empireLastIntegrity,
                                    "union_first_mean_integrity", unionFirstIntegrity,
                                    "union_last_mean_integrity", unionLastIntegrity),
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
                "Three committed exact-core Stage-19 encounters with save/restore continuation and finite stores. Production, paid replacement cadence, backlog and long-war campaign trajectory remain open.");
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
}
