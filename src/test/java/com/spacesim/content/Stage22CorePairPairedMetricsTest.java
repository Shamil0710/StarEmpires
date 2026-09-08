package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22CorePairPairedMetricsTest {
    @Test
    void materiallyStochasticComparisonKeepsIndividualInversionButReducesMirrorsPerSeed() {
        var vector = Stage22CorePairMachineEvidenceBatch.runScenario(
                "B00",
                "paired_statistics_contract_fixture",
                "test-only",
                Stage22CorePairExperimentProtocol.pairedSchedule(2),
                (scenario, variant, profile, coordinate) -> {
                    int seedIndex = (int) (coordinate.seed() - Stage22CorePairExperimentProtocol.FIRST_SEED);
                    double left;
                    double right;
                    if (seedIndex == 0 && coordinate.permutation()
                            == Stage22CorePairExperimentProtocol.Permutation.DEFAULT) {
                        left = 10d;
                        right = 0d;
                    } else if (seedIndex == 0) {
                        left = 0d;
                        right = 2d;
                    } else if (coordinate.permutation()
                            == Stage22CorePairExperimentProtocol.Permutation.DEFAULT) {
                        // Deliberate individual-run inversion: retained as raw evidence, not excluded.
                        left = 0d;
                        right = 1d;
                    } else {
                        left = 10d;
                        right = 0d;
                    }
                    return new Stage22CorePairMachineEvidenceBatch.ObservationPayload(
                            Map.of("left", left, "right", right), Map.of(), List.of());
                });

        Map<Long, Double> bySeed = Stage22CorePairPairedMetrics.differencesBySeed(vector, "left", "right");
        assertEquals(4d, bySeed.get(Stage22CorePairExperimentProtocol.FIRST_SEED), 1e-12d);
        assertEquals(4.5d, bySeed.get(Stage22CorePairExperimentProtocol.FIRST_SEED + 1L), 1e-12d);
        assertEquals(4.25d, Stage22CorePairPairedMetrics.meanDifference(vector, "left", "right"), 1e-12d);
        assertTrue(vector.observations().stream().anyMatch(row ->
                row.metrics().get("left") < row.metrics().get("right")),
                "fixture must retain an individual stochastic inversion instead of deleting it");
        assertEquals(0, vector.hardRuleBreachCount());
    }
}
