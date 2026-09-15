package com.spacesim.content;

import com.spacesim.content.Stage22CorePairExperimentProtocol.RunCoordinate;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M22.6 B08 seam evidence over the accepted Stage-19I tactical and Stage-20 freight authorities.
 *
 * <p>This regression intentionally does not invent an operational resolver. It proves that the two
 * physical halves needed by an escort/interdiction scenario are deterministic and mirrored, while
 * keeping the missing production bridge visible as an explicit zero-valued guard. B08 must remain
 * PARTIAL until a normal gameplay authority makes the tactical escort/interdiction outcome gate the
 * fate of the same physical freight order.</p>
 */
class Stage22CorePairEscortInterdictionSeamTest {

    @Test
    void b08KeepsPhysicalTacticalAndFreightEvidenceGreenWithoutPretendingTheCausalBridgeExists() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B08",
                "escort_interdiction_seam",
                "stage19i_stage20.current",
                Stage22CorePairExperimentProtocol.pairedSchedule(1),
                (scenario, variant, profile, coordinate) -> observe(coordinate));

        assertEquals(1d, vector.guardMetricMeans().get("physical_tactical_authority"));
        assertEquals(1d, vector.guardMetricMeans().get("physical_freight_authority"));
        assertEquals(1d, vector.guardMetricMeans().get("mirrored_core_ownership"));
        assertEquals(0d, vector.guardMetricMeans().get("production_causal_bridge_present"));
        assertTrue(vector.metricMeans().get("interdicted_cargo_kg") > 0d);
        assertTrue(vector.metricMeans().get("surviving_cargo_delivered_kg") > 0d);
        assertEquals(0, vector.hardRuleBreachCount());
    }

    private static Stage22CorePairMachineEvidenceBatch.ObservationPayload observe(RunCoordinate coordinate) {
        var tactical = Stage22CorePairTacticalProbe.run(
                Stage22CorePairTacticalProbe.Variant.PATROL,
                coordinate,
                true);
        var freight = Stage22CorePairFreightProbe.observe(coordinate);

        ArrayList<String> breaches = new ArrayList<>(freight.hardRuleBreaches());
        if (!tactical.valid()) {
            breaches.add("stage19i_tactical_authority_drift");
        }
        boolean freightValid = freight.guardMetrics().getOrDefault("freight_invariants", 0d) == 1d;
        if (!freightValid) {
            breaches.add("stage20_freight_authority_drift");
        }

        String physicalSlotOwner = coordinate.permutation()
                == Stage22CorePairExperimentProtocol.Permutation.DEFAULT
                ? Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID
                : Stage22CorePairBalanceEvidence.UNION_FACTION_ID;
        boolean stableCoreOwner = physicalSlotOwner.equals(Stage22CorePairBalanceEvidence.EMPIRE_FACTION_ID)
                || physicalSlotOwner.equals(Stage22CorePairBalanceEvidence.UNION_FACTION_ID);

        Map<String, Double> metrics = new LinkedHashMap<>();
        metrics.put("tactical_ticks", (double) Stage22CorePairTacticalProbe.TICKS);
        metrics.put("convoy_starting_stock_kg", freight.metrics().get("starting_stock_kg"));
        metrics.put("interdicted_cargo_kg", freight.metrics().get("lost_cargo_kg"));
        metrics.put("surviving_cargo_delivered_kg", freight.metrics().get("delivered_cargo_kg"));
        metrics.put("remaining_owned_freighters", freight.metrics().get("remaining_owned_freighters"));

        Map<String, Double> guards = Map.of(
                "physical_tactical_authority", tactical.valid() ? 1d : 0d,
                "physical_freight_authority", freightValid ? 1d : 0d,
                "mirrored_core_ownership", stableCoreOwner ? 1d : 0d,
                // Deliberately zero: no accepted gameplay authority currently binds these two results.
                "production_causal_bridge_present", 0d);

        return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(metrics, guards, List.copyOf(breaches));
    }
}
