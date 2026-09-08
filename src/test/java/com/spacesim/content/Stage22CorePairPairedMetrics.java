package com.spacesim.content;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Test-only reducer for M22.6 materially stochastic comparisons.
 *
 * <p>The canonical validation contract declares the seed pair, not an individual mirrored run, as
 * the independent sampling unit. {@link Stage22CorePairMachineEvidenceBatch} already guarantees one
 * DEFAULT and one MIRRORED observation per seed; this reducer therefore averages the two physical
 * observations before aggregating across seeds. It never changes gameplay state, drops an outlier or
 * relaxes an acceptance threshold.</p>
 */
final class Stage22CorePairPairedMetrics {
    private Stage22CorePairPairedMetrics() { }

    static double meanDifference(
            Stage22CorePairMachineEvidenceBatch.ResultVector vector,
            String leftMetric,
            String rightMetric) {
        LinkedHashMap<Long, PairAccumulator> pairs = new LinkedHashMap<>();
        for (Stage22CorePairMachineEvidenceBatch.RunObservation observation : vector.observations()) {
            Double left = observation.metrics().get(leftMetric);
            Double right = observation.metrics().get(rightMetric);
            if (left == null || right == null) {
                throw new IllegalArgumentException(
                        "Missing paired metric(s): " + leftMetric + ", " + rightMetric);
            }
            pairs.computeIfAbsent(observation.seed(), ignored -> new PairAccumulator())
                    .add(observation.permutation(), left, right);
        }
        if (pairs.size() != vector.pairedSeedCount()) {
            throw new IllegalArgumentException("Paired metric reducer observed incomplete seed set");
        }
        return pairs.values().stream()
                .mapToDouble(PairAccumulator::meanDifference)
                .average()
                .orElseThrow();
    }

    static Map<Long, Double> differencesBySeed(
            Stage22CorePairMachineEvidenceBatch.ResultVector vector,
            String leftMetric,
            String rightMetric) {
        LinkedHashMap<Long, PairAccumulator> pairs = new LinkedHashMap<>();
        for (Stage22CorePairMachineEvidenceBatch.RunObservation observation : vector.observations()) {
            Double left = observation.metrics().get(leftMetric);
            Double right = observation.metrics().get(rightMetric);
            if (left == null || right == null) {
                throw new IllegalArgumentException(
                        "Missing paired metric(s): " + leftMetric + ", " + rightMetric);
            }
            pairs.computeIfAbsent(observation.seed(), ignored -> new PairAccumulator())
                    .add(observation.permutation(), left, right);
        }
        LinkedHashMap<Long, Double> result = new LinkedHashMap<>();
        pairs.forEach((seed, pair) -> result.put(seed, pair.meanDifference()));
        return Map.copyOf(result);
    }

    private static final class PairAccumulator {
        private double leftSum;
        private double rightSum;
        private boolean defaultSeen;
        private boolean mirroredSeen;

        private void add(
                Stage22CorePairExperimentProtocol.Permutation permutation,
                double left,
                double right) {
            switch (permutation) {
                case DEFAULT -> {
                    if (defaultSeen) throw new IllegalArgumentException("Duplicate DEFAULT observation in pair");
                    defaultSeen = true;
                }
                case MIRRORED -> {
                    if (mirroredSeen) throw new IllegalArgumentException("Duplicate MIRRORED observation in pair");
                    mirroredSeen = true;
                }
            }
            leftSum += left;
            rightSum += right;
        }

        private double meanDifference() {
            if (!defaultSeen || !mirroredSeen) {
                throw new IllegalArgumentException("Incomplete mirrored seed pair");
            }
            return (leftSum / 2d) - (rightSum / 2d);
        }
    }
}
