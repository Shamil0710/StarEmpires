package com.spacesim.world.generation;

import com.spacesim.world.Stage20GeneratedWorldBatchAcceptance;
import com.spacesim.world.Stage20GeneratedWorldBatchAcceptance.BatchReport;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.FailureReason;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance.SeedResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Fixed deterministic Stage-20E representative root-seed corpus and evidence serializer.
 *
 * <p>The v1 corpus is the contiguous seed interval {@code 1..16}. It is intentionally fixed before
 * observing individual world outcomes, preventing result-aware cherry-picking. The corpus runner
 * evaluates every seed exactly once through {@link Stage20GeneratedWorldProductionProbe} using the
 * same versioned representative input profile. It reports measured acceptance only and defines no
 * minimum pass fraction.</p>
 */
public final class Stage20RepresentativeSeedCorpus {
    /** Machine-readable evidence schema version. */
    public static final int EVIDENCE_SCHEMA_VERSION = 1;
    /** Current fixed representative corpus version. */
    public static final String CURRENT_VERSION = "stage20e.representative-seed-corpus.v1";
    /** Main-only CI evidence file name. */
    public static final String EVIDENCE_FILE_NAME = "stage20e-representative-seed-corpus-v1.json";

    private static final List<Long> ROOT_SEEDS = List.of(
            1L, 2L, 3L, 4L,
            5L, 6L, 7L, 8L,
            9L, 10L, 11L, 12L,
            13L, 14L, 15L, 16L);

    private Stage20RepresentativeSeedCorpus() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns the immutable root seeds selected before outcome inspection.
     *
     * @return contiguous roots {@code 1..16}
     */
    public static List<Long> seeds() {
        return ROOT_SEEDS;
    }

    /**
     * Immutable representative corpus evidence.
     *
     * @param schemaVersion evidence schema version
     * @param corpusVersion fixed corpus version
     * @param representativeProfileVersion exact probe-input profile version
     * @param productionProbeVersion exact whole-world probe implementation version
     * @param batchVersion exact batch-observability version
     * @param seedAcceptanceVersion exact whole-seed acceptance version
     * @param bootstrapRequirementVersion exact bootstrap-demand authority version
     * @param factionStartProfileVersion exact faction-start acceptance profile version
     * @param stage22ReviewRequired whether representative policy remains provisional
     * @param batch measured result for every fixed seed
     */
    public record Evidence(
            int schemaVersion,
            String corpusVersion,
            String representativeProfileVersion,
            String productionProbeVersion,
            String batchVersion,
            String seedAcceptanceVersion,
            String bootstrapRequirementVersion,
            String factionStartProfileVersion,
            boolean stage22ReviewRequired,
            BatchReport batch) {
        /**
         * Validates one deterministic evidence snapshot.
         *
         * @param schemaVersion evidence schema version
         * @param corpusVersion corpus version
         * @param representativeProfileVersion representative input profile version
         * @param productionProbeVersion production-probe version
         * @param batchVersion batch-observability version
         * @param seedAcceptanceVersion whole-seed acceptance version
         * @param bootstrapRequirementVersion bootstrap requirement version
         * @param factionStartProfileVersion faction-start profile version
         * @param stage22ReviewRequired Stage-22 review boundary
         * @param batch measured batch report
         */
        public Evidence {
            if (schemaVersion <= 0) {
                throw new IllegalArgumentException("schemaVersion must be positive");
            }
            corpusVersion = requireText(corpusVersion, "corpusVersion");
            representativeProfileVersion = requireText(representativeProfileVersion, "representativeProfileVersion");
            productionProbeVersion = requireText(productionProbeVersion, "productionProbeVersion");
            batchVersion = requireText(batchVersion, "batchVersion");
            seedAcceptanceVersion = requireText(seedAcceptanceVersion, "seedAcceptanceVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            factionStartProfileVersion = requireText(factionStartProfileVersion, "factionStartProfileVersion");
            Objects.requireNonNull(batch, "batch");
            if (!batch.version().equals(batchVersion)) {
                throw new IllegalArgumentException("batchVersion does not match batch report");
            }
            if (!batch.requestedSeeds().equals(ROOT_SEEDS)) {
                throw new IllegalArgumentException("representative evidence must cover the exact fixed corpus");
            }
            if (!stage22ReviewRequired) {
                throw new IllegalArgumentException("representative corpus v1 must retain Stage-22 review boundary");
            }
        }
    }

    /**
     * Evaluates every fixed v1 root seed exactly once using the current representative profile.
     *
     * @return immutable measured evidence; no global pass-rate requirement is applied
     */
    public static Evidence evaluateCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile profile =
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        BatchReport batch = Stage20GeneratedWorldBatchAcceptance.run(
                ROOT_SEEDS,
                seed -> Stage20GeneratedWorldProductionProbe.run(seed, profile.inputs()).seedAcceptance());
        return new Evidence(
                EVIDENCE_SCHEMA_VERSION,
                CURRENT_VERSION,
                profile.version(),
                Stage20GeneratedWorldProductionProbe.CURRENT_VERSION,
                batch.version(),
                Stage20GeneratedWorldSeedAcceptance.CURRENT_VERSION,
                profile.bootstrapRequirementVersion(),
                profile.factionStartProfileVersion(),
                profile.stage22ReviewRequired(),
                batch);
    }

    /**
     * Serializes representative evidence into deterministic human-readable JSON.
     *
     * <p>No wall-clock timestamp is included because evidence identity must depend only on code,
     * profiles and the fixed corpus.</p>
     *
     * @param evidence representative corpus evidence
     * @return deterministic JSON document ending with a newline
     */
    public static String toJson(Evidence evidence) {
        Evidence value = Objects.requireNonNull(evidence, "evidence");
        StringBuilder json = new StringBuilder(16_384);
        json.append("{\n");
        field(json, 1, "schemaVersion", Integer.toString(value.schemaVersion()), false);
        field(json, 1, "corpusVersion", quote(value.corpusVersion()), false);
        field(json, 1, "representativeProfileVersion", quote(value.representativeProfileVersion()), false);
        field(json, 1, "productionProbeVersion", quote(value.productionProbeVersion()), false);
        field(json, 1, "batchVersion", quote(value.batchVersion()), false);
        field(json, 1, "seedAcceptanceVersion", quote(value.seedAcceptanceVersion()), false);
        field(json, 1, "bootstrapRequirementVersion", quote(value.bootstrapRequirementVersion()), false);
        field(json, 1, "factionStartProfileVersion", quote(value.factionStartProfileVersion()), false);
        field(json, 1, "stage22ReviewRequired", Boolean.toString(value.stage22ReviewRequired()), false);

        indent(json, 1).append("\"requestedSeeds\": [");
        for (int index = 0; index < value.batch().requestedSeeds().size(); index++) {
            if (index > 0) json.append(", ");
            json.append(value.batch().requestedSeeds().get(index));
        }
        json.append("],\n");
        field(json, 1, "acceptedSeedCount", Integer.toString(value.batch().acceptedSeedCount()), false);
        field(json, 1, "rejectedSeedCount", Integer.toString(value.batch().rejectedSeedCount()), false);
        field(json, 1, "unresolvedAuthoritySeedCount",
                Integer.toString(value.batch().unresolvedAuthoritySeedCount()), false);
        field(json, 1, "acceptedFraction", Double.toString(value.batch().acceptedFraction()), false);
        field(json, 1, "rejectedFraction", Double.toString(value.batch().rejectedFraction()), false);
        field(json, 1, "unresolvedAuthorityFraction",
                Double.toString(value.batch().unresolvedAuthorityFraction()), false);

        indent(json, 1).append("\"failureReasonCounts\": {\n");
        List<Map.Entry<FailureReason, Integer>> failureCounts = new ArrayList<>();
        for (FailureReason reason : FailureReason.values()) {
            Integer count = value.batch().failureReasonCounts().get(reason);
            if (count != null) failureCounts.add(Map.entry(reason, count));
        }
        for (int index = 0; index < failureCounts.size(); index++) {
            Map.Entry<FailureReason, Integer> entry = failureCounts.get(index);
            indent(json, 2).append(quote(entry.getKey().name())).append(": ").append(entry.getValue());
            json.append(index + 1 < failureCounts.size() ? ",\n" : "\n");
        }
        indent(json, 1).append("},\n");

        indent(json, 1).append("\"seedResults\": [\n");
        for (int index = 0; index < value.batch().seedResults().size(); index++) {
            appendSeed(json, value.batch().seedResults().get(index), 2);
            json.append(index + 1 < value.batch().seedResults().size() ? ",\n" : "\n");
        }
        indent(json, 1).append("]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void appendSeed(StringBuilder json, SeedResult seed, int depth) {
        indent(json, depth).append("{\n");
        field(json, depth + 1, "rootSeed", Long.toString(seed.rootSeed()), false);
        field(json, depth + 1, "status", quote(seed.status().name()), false);
        field(json, depth + 1, "topologyStatus", quote(seed.topologyStatus().name()), false);
        field(json, depth + 1, "topologyRepairPasses", Integer.toString(seed.topologyRepairPasses()), false);
        field(json, depth + 1, "economicAcceptancePresent",
                Boolean.toString(seed.economicAcceptancePresent()), false);
        field(json, depth + 1, "placementStatus",
                seed.placementStatus().map(value -> quote(value.name())).orElse("null"), false);
        indent(json, depth + 1).append("\"failures\": [");
        if (seed.failures().isEmpty()) {
            json.append("]\n");
        } else {
            json.append("\n");
            for (int index = 0; index < seed.failures().size(); index++) {
                var failure = seed.failures().get(index);
                indent(json, depth + 2).append("{\n");
                field(json, depth + 3, "reason", quote(failure.reason().name()), false);
                field(json, depth + 3, "subject", quote(failure.subject()), false);
                field(json, depth + 3, "detail", quote(failure.detail()), true);
                indent(json, depth + 2).append("}");
                json.append(index + 1 < seed.failures().size() ? ",\n" : "\n");
            }
            indent(json, depth + 1).append("]\n");
        }
        indent(json, depth).append("}");
    }

    private static void field(
            StringBuilder json,
            int depth,
            String name,
            String encodedValue,
            boolean last) {
        indent(json, depth).append(quote(name)).append(": ").append(encodedValue);
        json.append(last ? "\n" : ",\n");
    }

    private static StringBuilder indent(StringBuilder json, int depth) {
        return json.append("  ".repeat(depth));
    }

    private static String quote(String value) {
        StringBuilder escaped = new StringBuilder(value.length() + 8);
        escaped.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> {
                    if (character < 0x20) {
                        escaped.append(String.format(java.util.Locale.ROOT, "\\u%04x", (int) character));
                    } else {
                        escaped.append(character);
                    }
                }
            }
        }
        return escaped.append('"').toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
