package com.spacesim.world;

import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.FailureReason;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.SeedResult;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.Status;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Deterministic Stage-20E batch observability over whole generated-world seed acceptance.
 *
 * <p>The batch layer does not define an arbitrary acceptance-rate target. The canonical Stage-20
 * roadmap requires representative seed evidence but currently provides no numeric pass fraction.
 * Therefore this class reports the measured distribution and machine-readable failure reasons only.
 * A future versioned calibration may turn that evidence into a quantitative batch gate once a target
 * is justified by observed generation behavior rather than invented here.</p>
 */
public final class Stage20GeneratedWorldBatchAcceptance {
    /** Current immutable batch report version. */
    public static final String CURRENT_VERSION = "stage20e.generated-world-batch-observability.v1";

    private Stage20GeneratedWorldBatchAcceptance() {
        throw new AssertionError("No instances");
    }

    /**
     * Whole-seed evaluator used by a batch harness.
     *
     * <p>The implementation is responsible for running the real generation/acceptance pipeline for
     * the supplied seed. It must not retry a different seed internally or mutate a rejected seed into
     * an accepted one.</p>
     */
    @FunctionalInterface
    public interface SeedEvaluator {
        /**
         * Evaluates exactly one requested root seed.
         *
         * @param rootSeed root seed requested by the deterministic batch
         * @return composed whole-seed acceptance for exactly the same seed
         */
        SeedResult evaluate(long rootSeed);
    }

    /**
     * Immutable deterministic batch report.
     *
     * @param version stable report version
     * @param requestedSeeds sorted unique requested root seeds
     * @param seedResults sorted whole-seed results
     * @param acceptedSeedCount number of accepted seeds
     * @param rejectedSeedCount number of authoritatively rejected seeds
     * @param unresolvedAuthoritySeedCount number of seeds blocked only by missing authority
     * @param acceptedFraction measured accepted share of the requested batch
     * @param rejectedFraction measured rejected share of the requested batch
     * @param unresolvedAuthorityFraction measured unresolved-authority share
     * @param failureReasonCounts count of normalized failure rows by reason
     */
    public record BatchReport(
            String version,
            List<Long> requestedSeeds,
            List<SeedResult> seedResults,
            int acceptedSeedCount,
            int rejectedSeedCount,
            int unresolvedAuthoritySeedCount,
            double acceptedFraction,
            double rejectedFraction,
            double unresolvedAuthorityFraction,
            Map<FailureReason, Integer> failureReasonCounts) {
        /**
         * Validates and freezes one deterministic batch report.
         *
         * @param version stable report version
         * @param requestedSeeds sorted requested root seeds
         * @param seedResults whole-seed results
         * @param acceptedSeedCount accepted seed count
         * @param rejectedSeedCount rejected seed count
         * @param unresolvedAuthoritySeedCount unresolved-authority seed count
         * @param acceptedFraction accepted share
         * @param rejectedFraction rejected share
         * @param unresolvedAuthorityFraction unresolved-authority share
         * @param failureReasonCounts normalized failure counts
         */
        public BatchReport {
            version = requireText(version, "version");
            Objects.requireNonNull(requestedSeeds, "requestedSeeds");
            Objects.requireNonNull(seedResults, "seedResults");
            Objects.requireNonNull(failureReasonCounts, "failureReasonCounts");
            requestedSeeds = List.copyOf(requestedSeeds);
            seedResults = List.copyOf(seedResults);
            if (requestedSeeds.isEmpty() || requestedSeeds.size() != seedResults.size()) {
                throw new IllegalArgumentException("batch must contain one result per non-empty requested seed set");
            }
            for (int index = 0; index < requestedSeeds.size(); index++) {
                if (requestedSeeds.get(index) == null || seedResults.get(index) == null) {
                    throw new IllegalArgumentException("batch seeds/results cannot contain null");
                }
                if (seedResults.get(index).rootSeed() != requestedSeeds.get(index)) {
                    throw new IllegalArgumentException("batch result seed does not match requested seed order");
                }
                if (index > 0 && requestedSeeds.get(index - 1) >= requestedSeeds.get(index)) {
                    throw new IllegalArgumentException("requestedSeeds must be strictly increasing and unique");
                }
            }
            if (acceptedSeedCount < 0 || rejectedSeedCount < 0 || unresolvedAuthoritySeedCount < 0
                    || acceptedSeedCount + rejectedSeedCount + unresolvedAuthoritySeedCount != requestedSeeds.size()) {
                throw new IllegalArgumentException("batch status counts must partition requested seeds");
            }
            requireUnitFraction(acceptedFraction, "acceptedFraction");
            requireUnitFraction(rejectedFraction, "rejectedFraction");
            requireUnitFraction(unresolvedAuthorityFraction, "unresolvedAuthorityFraction");
            double expectedAccepted = (double) acceptedSeedCount / requestedSeeds.size();
            double expectedRejected = (double) rejectedSeedCount / requestedSeeds.size();
            double expectedUnresolved = (double) unresolvedAuthoritySeedCount / requestedSeeds.size();
            if (Double.compare(acceptedFraction, expectedAccepted) != 0
                    || Double.compare(rejectedFraction, expectedRejected) != 0
                    || Double.compare(unresolvedAuthorityFraction, expectedUnresolved) != 0) {
                throw new IllegalArgumentException("batch fractions must exactly match status counts");
            }
            EnumMap<FailureReason, Integer> counts = new EnumMap<>(FailureReason.class);
            for (Map.Entry<FailureReason, Integer> entry : failureReasonCounts.entrySet()) {
                Objects.requireNonNull(entry.getKey(), "failure reason");
                Integer value = Objects.requireNonNull(entry.getValue(), "failure count");
                if (value <= 0) {
                    throw new IllegalArgumentException("failure reason counts must be positive");
                }
                counts.put(entry.getKey(), value);
            }
            failureReasonCounts = Collections.unmodifiableMap(counts);
        }
    }

    /**
     * Runs a deterministic batch over exactly the supplied unique seed set.
     *
     * <p>Input ordering is deliberately ignored: seeds are canonicalized ascending before
     * evaluation, ensuring the same seed set produces the same evaluation order. Duplicate seeds are
     * rejected rather than silently changing sample weighting.</p>
     *
     * @param seeds unique root seeds to evaluate
     * @param evaluator real whole-seed evaluation callback
     * @return immutable measured batch distribution
     */
    public static BatchReport run(List<Long> seeds, SeedEvaluator evaluator) {
        Objects.requireNonNull(seeds, "seeds");
        SeedEvaluator checkedEvaluator = Objects.requireNonNull(evaluator, "evaluator");
        if (seeds.isEmpty()) {
            throw new IllegalArgumentException("seed batch must not be empty");
        }
        ArrayList<Long> orderedSeeds = new ArrayList<>(seeds);
        if (orderedSeeds.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("seed batch cannot contain null");
        }
        HashSet<Long> unique = new HashSet<>(orderedSeeds);
        if (unique.size() != orderedSeeds.size()) {
            throw new IllegalArgumentException("seed batch cannot contain duplicates");
        }
        orderedSeeds.sort(Long::compareTo);

        ArrayList<SeedResult> results = new ArrayList<>();
        EnumMap<FailureReason, Integer> failures = new EnumMap<>(FailureReason.class);
        int accepted = 0;
        int rejected = 0;
        int unresolved = 0;
        for (long seed : orderedSeeds) {
            SeedResult result = Objects.requireNonNull(checkedEvaluator.evaluate(seed), "seed evaluator result");
            if (!Stage20GeneratedWorldSeedAcceptance.CURRENT_VERSION.equals(result.version())) {
                throw new IllegalArgumentException("seed evaluator returned incompatible acceptance version");
            }
            if (result.rootSeed() != seed) {
                throw new IllegalArgumentException("seed evaluator returned a different root seed");
            }
            results.add(result);
            if (result.status() == Status.ACCEPTED) {
                accepted++;
            } else if (result.status() == Status.REJECTED_SEED) {
                rejected++;
            } else {
                unresolved++;
            }
            result.failures().forEach(value -> failures.merge(value.reason(), 1, Integer::sum));
        }

        int total = orderedSeeds.size();
        return new BatchReport(
                CURRENT_VERSION,
                List.copyOf(orderedSeeds),
                List.copyOf(results),
                accepted,
                rejected,
                unresolved,
                (double) accepted / total,
                (double) rejected / total,
                (double) unresolved / total,
                failures);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireUnitFraction(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be finite and in [0,1]");
        }
    }
}
