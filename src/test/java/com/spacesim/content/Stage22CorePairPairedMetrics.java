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
    private static final double APPROXIMATE_95_PERCENT_Z = 1.96d;

    private Stage22CorePairPairedMetrics() { }

    static double meanDifference(
            Stage22CorePairMachineEvidenceBatch.ResultVector vector,
            String leftMetric,
            String rightMetric) {
        return summarizeDifference(vector, leftMetric, rightMetric).meanDifference();
    }

    static DifferenceSummary summarizeDifference(
            Stage22CorePairMachineEvidenceBatch.ResultVector vector,
            String leftMetric,
            String rightMetric) {
        Map<Long, Double> differences = differencesBySeed(vector, leftMetric, rightMetric);
        int pairCount = differences.size();
        if (pairCount < 2) {
            throw new IllegalArgumentException("At least two complete mirrored seed pairs are required");
        }

        double mean = differences.values().stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElseThrow();
        double squaredDeviationSum = differences.values().stream()
                .mapToDouble(value -> {
                    double deviation = value - mean;
                    return deviation * deviation;
                })
                .sum();
        double sampleStandardDeviation = Math.sqrt(squaredDeviationSum / (pairCount - 1d));
        double approximate95PercentHalfWidth = APPROXIMATE_95_PERCENT_Z
                * sampleStandardDeviation / Math.sqrt(pairCount);
        return new DifferenceSummary(
                pairCount,
                mean,
                sampleStandardDeviation,
                approximate95PercentHalfWidth,
                mean - approximate95PercentHalfWidth,
                mean + approximate95PercentHalfWidth);
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
        if (pairs.size() != vector.pairedSeedCount()) {
            throw new IllegalArgumentException("Paired metric reducer observed incomplete seed set");
        }
        LinkedHashMap<Long, Double> result = new LinkedHashMap<>();
        pairs.forEach((seed, pair) -> result.put(seed, pair.meanDifference()));
        return Map.copyOf(result);
    }

    record DifferenceSummary(
            int pairedSeedCount,
            double meanDifference,
            double sampleStandardDeviation,
            double approximate95PercentHalfWidth,
            double approximate95PercentLowerBound,
            double approximate95PercentUpperBound) { }

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
