package com.spacesim.world.generation;

import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Status;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.ViolationType;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.FailureReason;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.SeedResult;
import com.spacesim.world.calibration.Stage20BootstrapRequirementCalibrationProfileV2;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Measurement-only fixed-corpus evidence for the corrected Stage-20E bootstrap service-cadence v2
 * candidate authority.
 *
 * <p>The evaluator runs the unchanged production probe over the same fixed seeds 1..16 and changes
 * only the input authority supplied by {@link Stage20RepresentativeGeneratedWorldProbeProfileV2}.
 * No pass-rate target is defined. Results are evidence for or against promotion of the corrected
 * semantics, not a hidden repair mechanism.</p>
 */
public final class Stage20BootstrapServiceCadenceV2CorpusEvidence {
    /** Current deterministic candidate-evidence version. */
    public static final String CURRENT_VERSION = "stage20e.bootstrap-service-cadence-v2-corpus-evidence.v1";

    private Stage20BootstrapServiceCadenceV2CorpusEvidence() {
        throw new AssertionError("No instances");
    }

    /**
     * One seed-level candidate measurement.
     *
     * @param rootSeed exact fixed root seed
     * @param wholeSeedStatus whole-seed acceptance status name
     * @param placementStatus faction-start placement status
     * @param acceptedCandidateCount accepted start candidates in this generated world
     * @param rejectedCandidateCount rejected start candidates in this generated world
     * @param unresolvedCandidateCount candidates blocked only by unresolved authority
     */
    public record SeedEvidence(
            long rootSeed,
            String wholeSeedStatus,
            PlacementStatus placementStatus,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int unresolvedCandidateCount) {
        /**
         * Validates one immutable seed measurement.
         *
         * @param rootSeed exact root seed
         * @param wholeSeedStatus whole-seed status name
         * @param placementStatus faction-start placement status
         * @param acceptedCandidateCount accepted candidate count
         * @param rejectedCandidateCount rejected candidate count
         * @param unresolvedCandidateCount unresolved candidate count
         */
        public SeedEvidence {
            wholeSeedStatus = requireText(wholeSeedStatus, "wholeSeedStatus");
            Objects.requireNonNull(placementStatus, "placementStatus");
            requireNonNegative(acceptedCandidateCount, "acceptedCandidateCount");
            requireNonNegative(rejectedCandidateCount, "rejectedCandidateCount");
            requireNonNegative(unresolvedCandidateCount, "unresolvedCandidateCount");
            if (acceptedCandidateCount + rejectedCandidateCount + unresolvedCandidateCount <= 0) {
                throw new IllegalArgumentException("seed evidence must contain candidate systems");
            }
        }
    }

    /**
     * Aggregate corrected-authority measurement.
     *
     * @param version evidence version
     * @param candidateProfileVersion representative v2 candidate profile version
     * @param bootstrapRequirementVersion corrected bootstrap authority version
     * @param serviceCadenceVersion corrected supplier-service cadence version
     * @param supplierDeliveryBudgetSeconds corrected route-time budget
     * @param referenceBufferCoverageSecondsByCommodity buffer coverage retained as separate evidence
     * @param seeds deterministic per-seed evidence
     * @param acceptedSeedCount whole seeds accepted by existing whole-seed gate
     * @param rejectedSeedCount whole seeds rejected by existing whole-seed gate
     * @param unresolvedSeedCount whole seeds blocked only by unresolved authority
     * @param acceptedPlacementSeedCount seeds whose faction-start placement succeeds
     * @param acceptedCandidateCount total accepted start candidates
     * @param rejectedCandidateCount total rejected start candidates
     * @param unresolvedCandidateCount total unresolved candidates
     * @param candidateViolationCounts hard candidate violations by type
     * @param wholeSeedFailureCounts normalized whole-seed failure rows
     */
    public record Report(
            String version,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            String serviceCadenceVersion,
            double supplierDeliveryBudgetSeconds,
            Map<String, Double> referenceBufferCoverageSecondsByCommodity,
            List<SeedEvidence> seeds,
            int acceptedSeedCount,
            int rejectedSeedCount,
            int unresolvedSeedCount,
            int acceptedPlacementSeedCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int unresolvedCandidateCount,
            Map<ViolationType, Integer> candidateViolationCounts,
            Map<FailureReason, Integer> wholeSeedFailureCounts) {
        /**
         * Validates and freezes one aggregate candidate report.
         *
         * @param version evidence version
         * @param candidateProfileVersion representative candidate profile version
         * @param bootstrapRequirementVersion bootstrap authority version
         * @param serviceCadenceVersion service-cadence authority version
         * @param supplierDeliveryBudgetSeconds corrected supplier time budget
         * @param referenceBufferCoverageSecondsByCommodity separate buffer-coverage evidence
         * @param seeds deterministic seed evidence
         * @param acceptedSeedCount accepted whole seeds
         * @param rejectedSeedCount rejected whole seeds
         * @param unresolvedSeedCount unresolved whole seeds
         * @param acceptedPlacementSeedCount seeds with accepted placement
         * @param acceptedCandidateCount accepted candidates
         * @param rejectedCandidateCount rejected candidates
         * @param unresolvedCandidateCount unresolved candidates
         * @param candidateViolationCounts candidate hard violations
         * @param wholeSeedFailureCounts whole-seed failure rows
         */
        public Report {
            version = requireText(version, "version");
            candidateProfileVersion = requireText(candidateProfileVersion, "candidateProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            serviceCadenceVersion = requireText(serviceCadenceVersion, "serviceCadenceVersion");
            requirePositive(supplierDeliveryBudgetSeconds, "supplierDeliveryBudgetSeconds");
            Objects.requireNonNull(referenceBufferCoverageSecondsByCommodity,
                    "referenceBufferCoverageSecondsByCommodity");
            referenceBufferCoverageSecondsByCommodity = Collections.unmodifiableMap(
                    new TreeMap<>(referenceBufferCoverageSecondsByCommodity));
            Objects.requireNonNull(seeds, "seeds");
            ArrayList<SeedEvidence> seedCopy = new ArrayList<>(seeds);
            if (seedCopy.isEmpty() || seedCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("seeds must be non-empty and contain no nulls");
            }
            seedCopy.sort(java.util.Comparator.comparingLong(SeedEvidence::rootSeed));
            seeds = List.copyOf(seedCopy);
            requireNonNegative(acceptedSeedCount, "acceptedSeedCount");
            requireNonNegative(rejectedSeedCount, "rejectedSeedCount");
            requireNonNegative(unresolvedSeedCount, "unresolvedSeedCount");
            requireNonNegative(acceptedPlacementSeedCount, "acceptedPlacementSeedCount");
            requireNonNegative(acceptedCandidateCount, "acceptedCandidateCount");
            requireNonNegative(rejectedCandidateCount, "rejectedCandidateCount");
            requireNonNegative(unresolvedCandidateCount, "unresolvedCandidateCount");
            if (acceptedSeedCount + rejectedSeedCount + unresolvedSeedCount != seeds.size()) {
                throw new IllegalArgumentException("whole-seed counts must partition fixed corpus");
            }
            if (acceptedPlacementSeedCount > seeds.size()) {
                throw new IllegalArgumentException("accepted placement count exceeds seed count");
            }
            int measuredCandidates = seeds.stream().mapToInt(value -> value.acceptedCandidateCount()
                    + value.rejectedCandidateCount() + value.unresolvedCandidateCount()).sum();
            if (acceptedCandidateCount + rejectedCandidateCount + unresolvedCandidateCount != measuredCandidates) {
                throw new IllegalArgumentException("candidate counts must match seed evidence");
            }
            candidateViolationCounts = immutableEnumCounts(
                    candidateViolationCounts, ViolationType.class, "candidateViolationCounts");
            wholeSeedFailureCounts = immutableEnumCounts(
                    wholeSeedFailureCounts, FailureReason.class, "wholeSeedFailureCounts");
        }
    }

    /**
     * Runs the fixed corpus once under the corrected candidate time authority.
     *
     * @return deterministic measurement report without a pass quota
     */
    public static Report evaluateCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfileV2.DerivedProfile candidate =
                Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapRequirementCalibrationProfileV2.DerivedProfile bootstrap =
                Stage20BootstrapRequirementCalibrationProfileV2.deriveCurrent();
        ArrayList<SeedEvidence> seeds = new ArrayList<>();
        EnumMap<ViolationType, Integer> violations = new EnumMap<>(ViolationType.class);
        EnumMap<FailureReason, Integer> wholeFailures = new EnumMap<>(FailureReason.class);
        int acceptedSeeds = 0;
        int rejectedSeeds = 0;
        int unresolvedSeeds = 0;
        int acceptedPlacements = 0;
        int acceptedCandidates = 0;
        int rejectedCandidates = 0;
        int unresolvedCandidates = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            Stage20GeneratedWorldProductionProbe.ProbeResult probe =
                    Stage20GeneratedWorldProductionProbe.run(rootSeed, candidate.inputs());
            SeedResult whole = probe.seedAcceptance();
            switch (whole.status()) {
                case ACCEPTED -> acceptedSeeds++;
                case REJECTED_SEED -> rejectedSeeds++;
                case UNRESOLVED_AUTHORITY -> unresolvedSeeds++;
            }
            whole.failures().forEach(value -> wholeFailures.merge(value.reason(), 1, Math::addExact));
            PlacementStatus placement = probe.placement().orElseThrow().status();
            if (placement == PlacementStatus.ACCEPTED) {
                acceptedPlacements++;
            }
            int seedAccepted = 0;
            int seedRejected = 0;
            int seedUnresolved = 0;
            for (Evaluation evaluation : probe.candidateEvaluations().orElseThrow()) {
                if (evaluation.status() == Status.ACCEPTED) {
                    seedAccepted++;
                    acceptedCandidates++;
                } else if (evaluation.status() == Status.REJECTED) {
                    seedRejected++;
                    rejectedCandidates++;
                } else {
                    seedUnresolved++;
                    unresolvedCandidates++;
                }
                evaluation.violations().forEach(value ->
                        violations.merge(value.type(), 1, Math::addExact));
            }
            seeds.add(new SeedEvidence(
                    rootSeed,
                    whole.status().name(),
                    placement,
                    seedAccepted,
                    seedRejected,
                    seedUnresolved));
        }

        return new Report(
                CURRENT_VERSION,
                candidate.version(),
                bootstrap.version(),
                bootstrap.serviceCadence().version(),
                bootstrap.serviceCadence().maximumSupplierDeliveryTimeSeconds(),
                bootstrap.referenceBufferCoverageSecondsByCommodity(),
                seeds,
                acceptedSeeds,
                rejectedSeeds,
                unresolvedSeeds,
                acceptedPlacements,
                acceptedCandidates,
                rejectedCandidates,
                unresolvedCandidates,
                violations,
                wholeFailures);
    }

    /**
     * Serializes compact deterministic measurement evidence for CI inspection.
     *
     * @param report measured candidate report
     * @return deterministic text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(8_192);
        text.append("version=").append(value.version()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("serviceCadenceVersion=").append(value.serviceCadenceVersion()).append('\n');
        text.append("supplierDeliveryBudgetSeconds=").append(value.supplierDeliveryBudgetSeconds()).append('\n');
        text.append("referenceBufferCoverageSecondsByCommodity=")
                .append(new TreeMap<>(value.referenceBufferCoverageSecondsByCommodity())).append('\n');
        text.append("seedCounts accepted=").append(value.acceptedSeedCount())
                .append(" rejected=").append(value.rejectedSeedCount())
                .append(" unresolved=").append(value.unresolvedSeedCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("candidateCounts accepted=").append(value.acceptedCandidateCount())
                .append(" rejected=").append(value.rejectedCandidateCount())
                .append(" unresolved=").append(value.unresolvedCandidateCount()).append('\n');
        text.append("candidateViolationCounts=").append(new TreeMap<>(value.candidateViolationCounts())).append('\n');
        text.append("wholeSeedFailureCounts=").append(new TreeMap<>(value.wholeSeedFailureCounts())).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" whole=").append(seed.wholeSeedStatus())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" candidatesAccepted=").append(seed.acceptedCandidateCount())
                    .append(" candidatesRejected=").append(seed.rejectedCandidateCount())
                    .append(" candidatesUnresolved=").append(seed.unresolvedCandidateCount())
                    .append('\n');
        }
        return text.toString();
    }

    private static <E extends Enum<E>> Map<E, Integer> immutableEnumCounts(
            Map<E, Integer> source,
            Class<E> enumType,
            String field) {
        Objects.requireNonNull(source, field);
        EnumMap<E, Integer> result = new EnumMap<>(enumType);
        for (Map.Entry<E, Integer> entry : source.entrySet()) {
            E key = Objects.requireNonNull(entry.getKey(), field + " key");
            int value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (value <= 0) {
                throw new IllegalArgumentException(field + " values must be positive");
            }
            result.put(key, value);
        }
        return Collections.unmodifiableMap(result);
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
