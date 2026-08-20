package com.spacesim.world.generation;

import com.spacesim.world.Stage20FactionStartCandidateEvaluator;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Violation;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Read-only Stage-20E causal diagnostics over the fixed representative generated-world corpus.
 *
 * <p>This layer exists because whole-seed rejection only says that faction-start placement had too
 * few accepted candidates. It preserves every candidate evaluation emitted by the production probe
 * and aggregates the already-existing hard-gate violations without changing topology, resources,
 * route capacity, demand, acceptance thresholds or placement behavior.</p>
 */
public final class Stage20RepresentativeFactionStartCorpusDiagnostics {
    /** Current deterministic diagnostic schema/version. */
    public static final String CURRENT_VERSION = "stage20e.faction-start-corpus-causal-diagnostics.v1";

    private Stage20RepresentativeFactionStartCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /**
     * One seed-level candidate-status summary.
     *
     * @param rootSeed measured root seed
     * @param candidateCount total evaluated systems
     * @param acceptedCandidateCount candidates accepted by the current faction-start hard gates
     * @param rejectedCandidateCount candidates rejected by physical/economic hard gates
     * @param unresolvedAuthorityCandidateCount candidates blocked only by required missing authority
     * @param violationCountsByType deterministic violation histogram by hard-gate type
     * @param violationCountsByTypeAndSubject deterministic violation histogram by type and commodity/subject
     */
    public record SeedSummary(
            long rootSeed,
            int candidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int unresolvedAuthorityCandidateCount,
            Map<String, Integer> violationCountsByType,
            Map<String, Integer> violationCountsByTypeAndSubject) {
        /**
         * Validates and freezes one seed-level summary.
         *
         * @param rootSeed measured root seed
         * @param candidateCount total evaluated systems
         * @param acceptedCandidateCount accepted candidate count
         * @param rejectedCandidateCount rejected candidate count
         * @param unresolvedAuthorityCandidateCount unresolved-authority candidate count
         * @param violationCountsByType hard-gate violation histogram
         * @param violationCountsByTypeAndSubject violation histogram including subject
         */
        public SeedSummary {
            requireNonNegative(candidateCount, "candidateCount");
            requireNonNegative(acceptedCandidateCount, "acceptedCandidateCount");
            requireNonNegative(rejectedCandidateCount, "rejectedCandidateCount");
            requireNonNegative(unresolvedAuthorityCandidateCount, "unresolvedAuthorityCandidateCount");
            if (acceptedCandidateCount + rejectedCandidateCount + unresolvedAuthorityCandidateCount != candidateCount) {
                throw new IllegalArgumentException("candidate status counts must equal candidateCount");
            }
            violationCountsByType = immutableCounts(violationCountsByType, "violationCountsByType");
            violationCountsByTypeAndSubject = immutableCounts(
                    violationCountsByTypeAndSubject, "violationCountsByTypeAndSubject");
        }
    }

    /**
     * Aggregate fixed-corpus faction-start causal report.
     *
     * @param version exact diagnostics version
     * @param corpusVersion exact fixed corpus version
     * @param representativeProfileVersion exact representative probe profile version
     * @param totalCandidateCount all evaluated systems across the corpus
     * @param acceptedCandidateCount accepted candidates across the corpus
     * @param rejectedCandidateCount rejected candidates across the corpus
     * @param unresolvedAuthorityCandidateCount unresolved-authority candidates across the corpus
     * @param violationCountsByType aggregate hard-gate violation histogram
     * @param violationCountsByTypeAndSubject aggregate hard-gate + subject histogram
     * @param seeds deterministic seed-level summaries
     */
    public record Report(
            String version,
            String corpusVersion,
            String representativeProfileVersion,
            int totalCandidateCount,
            int acceptedCandidateCount,
            int rejectedCandidateCount,
            int unresolvedAuthorityCandidateCount,
            Map<String, Integer> violationCountsByType,
            Map<String, Integer> violationCountsByTypeAndSubject,
            List<SeedSummary> seeds) {
        /**
         * Validates and freezes one aggregate report.
         *
         * @param version exact diagnostics version
         * @param corpusVersion corpus version
         * @param representativeProfileVersion representative probe profile version
         * @param totalCandidateCount all candidates
         * @param acceptedCandidateCount accepted candidates
         * @param rejectedCandidateCount rejected candidates
         * @param unresolvedAuthorityCandidateCount unresolved-authority candidates
         * @param violationCountsByType aggregate violation histogram
         * @param violationCountsByTypeAndSubject aggregate violation+subject histogram
         * @param seeds seed-level summaries
         */
        public Report {
            version = requireText(version, "version");
            corpusVersion = requireText(corpusVersion, "corpusVersion");
            representativeProfileVersion = requireText(representativeProfileVersion, "representativeProfileVersion");
            requireNonNegative(totalCandidateCount, "totalCandidateCount");
            requireNonNegative(acceptedCandidateCount, "acceptedCandidateCount");
            requireNonNegative(rejectedCandidateCount, "rejectedCandidateCount");
            requireNonNegative(unresolvedAuthorityCandidateCount, "unresolvedAuthorityCandidateCount");
            if (acceptedCandidateCount + rejectedCandidateCount + unresolvedAuthorityCandidateCount != totalCandidateCount) {
                throw new IllegalArgumentException("aggregate candidate status counts must equal totalCandidateCount");
            }
            violationCountsByType = immutableCounts(violationCountsByType, "violationCountsByType");
            violationCountsByTypeAndSubject = immutableCounts(
                    violationCountsByTypeAndSubject, "violationCountsByTypeAndSubject");
            Objects.requireNonNull(seeds, "seeds");
            ArrayList<SeedSummary> copy = new ArrayList<>(seeds);
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("seeds must be non-empty and contain no nulls");
            }
            copy.sort(java.util.Comparator.comparingLong(SeedSummary::rootSeed));
            seeds = List.copyOf(copy);
            int seedCandidates = seeds.stream().mapToInt(SeedSummary::candidateCount).sum();
            if (seedCandidates != totalCandidateCount) {
                throw new IllegalArgumentException("seed candidate total does not match aggregate total");
            }
        }
    }

    /**
     * Replays the exact fixed representative corpus and aggregates existing candidate violations.
     *
     * @return deterministic read-only causal report
     */
    public static Report evaluateCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile profile =
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        TreeMap<String, Integer> aggregateTypes = new TreeMap<>();
        TreeMap<String, Integer> aggregateSubjects = new TreeMap<>();
        ArrayList<SeedSummary> seeds = new ArrayList<>();
        int accepted = 0;
        int rejected = 0;
        int unresolved = 0;
        int total = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            Stage20GeneratedWorldProductionProbe.ProbeResult probe =
                    Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            List<Evaluation> evaluations = probe.candidateEvaluations().orElse(List.of());
            TreeMap<String, Integer> seedTypes = new TreeMap<>();
            TreeMap<String, Integer> seedSubjects = new TreeMap<>();
            int seedAccepted = 0;
            int seedRejected = 0;
            int seedUnresolved = 0;
            for (Evaluation evaluation : evaluations) {
                switch (evaluation.status()) {
                    case ACCEPTED -> seedAccepted++;
                    case REJECTED -> seedRejected++;
                    case UNRESOLVED_AUTHORITY -> seedUnresolved++;
                }
                for (Violation violation : evaluation.violations()) {
                    increment(seedTypes, violation.type().name());
                    increment(aggregateTypes, violation.type().name());
                    String subjectKey = violation.type().name() + "|" + violation.subject();
                    increment(seedSubjects, subjectKey);
                    increment(aggregateSubjects, subjectKey);
                }
            }
            total += evaluations.size();
            accepted += seedAccepted;
            rejected += seedRejected;
            unresolved += seedUnresolved;
            seeds.add(new SeedSummary(
                    rootSeed,
                    evaluations.size(),
                    seedAccepted,
                    seedRejected,
                    seedUnresolved,
                    seedTypes,
                    seedSubjects));
        }

        return new Report(
                CURRENT_VERSION,
                Stage20RepresentativeSeedCorpus.CURRENT_VERSION,
                profile.version(),
                total,
                accepted,
                rejected,
                unresolved,
                aggregateTypes,
                aggregateSubjects,
                seeds);
    }

    /**
     * Serializes the compact causal histogram for CI/log inspection.
     *
     * @param report causal report
     * @return deterministic text report ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(8_192);
        text.append("version=").append(value.version()).append('\n');
        text.append("corpusVersion=").append(value.corpusVersion()).append('\n');
        text.append("representativeProfileVersion=").append(value.representativeProfileVersion()).append('\n');
        text.append("totalCandidateCount=").append(value.totalCandidateCount()).append('\n');
        text.append("acceptedCandidateCount=").append(value.acceptedCandidateCount()).append('\n');
        text.append("rejectedCandidateCount=").append(value.rejectedCandidateCount()).append('\n');
        text.append("unresolvedAuthorityCandidateCount=").append(value.unresolvedAuthorityCandidateCount()).append('\n');
        text.append("violationCountsByType:\n");
        value.violationCountsByType().forEach((key, count) ->
                text.append("  ").append(key).append('=').append(count).append('\n'));
        text.append("violationCountsByTypeAndSubject:\n");
        value.violationCountsByTypeAndSubject().forEach((key, count) ->
                text.append("  ").append(key).append('=').append(count).append('\n'));
        text.append("seeds:\n");
        for (SeedSummary seed : value.seeds()) {
            text.append("  seed=").append(seed.rootSeed())
                    .append(" candidates=").append(seed.candidateCount())
                    .append(" accepted=").append(seed.acceptedCandidateCount())
                    .append(" rejected=").append(seed.rejectedCandidateCount())
                    .append(" unresolved=").append(seed.unresolvedAuthorityCandidateCount())
                    .append('\n');
        }
        return text.toString();
    }

    private static void increment(Map<String, Integer> counts, String key) {
        counts.merge(requireText(key, "histogram key"), 1, Math::addExact);
    }

    private static Map<String, Integer> immutableCounts(Map<String, Integer> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Integer> copy = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), field + " key");
            int value = Objects.requireNonNull(entry.getValue(), field + " value");
            requireNonNegative(value, field + " value");
            copy.put(key, value);
        }
        return Collections.unmodifiableMap(copy);
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
