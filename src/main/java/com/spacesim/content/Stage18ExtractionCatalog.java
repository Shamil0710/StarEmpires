package com.spacesim.content;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Immutable Stage-18B catalog of physical extraction methods.
 *
 * <p>Method definitions express source compatibility and per-source-mass engineering costs. They do
 * not create reserves, power, work, maintenance or cargo capacity. Runtime settlement must provide
 * those finite quantities explicitly.</p>
 */
public final class Stage18ExtractionCatalog {
    private final int schemaVersion;
    private final List<ExtractionMethodDefinition> methods;
    private final Map<String, ExtractionMethodDefinition> methodsById;
    private final String fingerprint;

    Stage18ExtractionCatalog(int schemaVersion, List<ExtractionMethodDefinition> methods) {
        this.schemaVersion = schemaVersion;
        List<ExtractionMethodDefinition> copy = new ArrayList<>(Objects.requireNonNull(methods, "methods"));
        copy.sort(Comparator.comparing(ExtractionMethodDefinition::id));
        this.methods = List.copyOf(copy);
        Map<String, ExtractionMethodDefinition> index = new LinkedHashMap<>();
        for (ExtractionMethodDefinition method : this.methods) {
            index.put(method.id(), method);
        }
        this.methodsById = Collections.unmodifiableMap(index);
        this.fingerprint = computeFingerprint();
    }

    /** @return extraction schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable method definitions */
    public List<ExtractionMethodDefinition> getMethods() {
        return methods;
    }

    /** @return lowercase SHA-256 fingerprint of extraction semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds one extraction method by stable ID.
     *
     * @param id stable method ID
     * @return method definition or {@code null}
     */
    public ExtractionMethodDefinition findMethod(String id) {
        return methodsById.get(id);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(4096);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (ExtractionMethodDefinition method : methods) {
            canonical.append("method|").append(method.id()).append('|')
                    .append(method.displayName()).append('|')
                    .append(method.sourceKind().name()).append('|')
                    .append(method.environment().name()).append('|')
                    .append(String.join(",", method.compatibleOccurrenceTypeIds())).append('|')
                    .append(String.join(",", method.requiredCapabilityTags())).append('|')
                    .append(Double.toHexString(method.workSecondsPerSourceKg())).append('|')
                    .append(Double.toHexString(method.energyJPerSourceKg())).append('|')
                    .append(Double.toHexString(method.maintenanceWorkSecondsPerSourceKg())).append('|')
                    .append(Double.toHexString(method.maxSourceKgPerSecond())).append('|')
                    .append(Double.toHexString(method.recoveryFraction())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                    digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** Kind of finite physical source consumed by an extraction method. */
    public enum SourceKind {
        /** A natural Stage-18 resource occurrence. */
        NATURAL_OCCURRENCE,
        /** A bounded salvage stream pre-accounted from manufactured physical assets. */
        SALVAGE_STREAM
    }

    /** Physical operating environment required by an extraction method. */
    public enum ExtractionEnvironment {
        /** Free-flying asteroid, fragment or analogous small body. */
        FREE_BODY,
        /** Surface or regolith operation on a larger body. */
        SURFACE,
        /** Deep subsurface or hard-rock operation. */
        DEEP_SUBSURFACE,
        /** Ice/regolith/feed exposed to thermal volatile recovery. */
        VOLATILE_BEARING,
        /** Wreck/debris/infrastructure recovery site. */
        SALVAGE_SITE
    }

    /**
     * One data-driven extraction method.
     *
     * @param id stable method ID
     * @param displayName diagnostic/display name
     * @param sourceKind compatible source kind
     * @param environment required physical environment
     * @param compatibleOccurrenceTypeIds natural occurrence types accepted by this method
     * @param requiredCapabilityTags capabilities that must be physically present
     * @param workSecondsPerSourceKg engineering work-seconds required per kilogram removed from source
     * @param energyJPerSourceKg electrical/process energy required per kilogram removed from source
     * @param maintenanceWorkSecondsPerSourceKg finite service-work consumption per kilogram removed
     * @param maxSourceKgPerSecond method throughput before facility/unit limits
     * @param recoveryFraction method-side recovery fraction in {@code (0, 1]}
     */
    public record ExtractionMethodDefinition(
            String id,
            String displayName,
            SourceKind sourceKind,
            ExtractionEnvironment environment,
            Set<String> compatibleOccurrenceTypeIds,
            Set<String> requiredCapabilityTags,
            double workSecondsPerSourceKg,
            double energyJPerSourceKg,
            double maintenanceWorkSecondsPerSourceKg,
            double maxSourceKgPerSecond,
            double recoveryFraction) {
        /**
         * Validates and freezes one extraction method.
         *
         * @param id stable method ID
         * @param displayName diagnostic/display name
         * @param sourceKind source kind
         * @param environment physical environment
         * @param compatibleOccurrenceTypeIds compatible natural occurrence types
         * @param requiredCapabilityTags required capabilities
         * @param workSecondsPerSourceKg work requirement
         * @param energyJPerSourceKg energy requirement
         * @param maintenanceWorkSecondsPerSourceKg maintenance requirement
         * @param maxSourceKgPerSecond method throughput
         * @param recoveryFraction recovery fraction
         */
        public ExtractionMethodDefinition {
            requireText(id, "method id");
            requireText(displayName, "method displayName");
            Objects.requireNonNull(sourceKind, "sourceKind");
            Objects.requireNonNull(environment, "environment");
            compatibleOccurrenceTypeIds = immutableSortedSet(
                    compatibleOccurrenceTypeIds, "compatibleOccurrenceTypeIds");
            requiredCapabilityTags = immutableSortedSet(requiredCapabilityTags, "requiredCapabilityTags");
            requirePositive(workSecondsPerSourceKg, "workSecondsPerSourceKg");
            requirePositive(energyJPerSourceKg, "energyJPerSourceKg");
            requirePositive(maintenanceWorkSecondsPerSourceKg, "maintenanceWorkSecondsPerSourceKg");
            requirePositive(maxSourceKgPerSecond, "maxSourceKgPerSecond");
            requireFraction(recoveryFraction, "recoveryFraction");
            if (requiredCapabilityTags.isEmpty()) {
                throw new IllegalArgumentException("Extraction method must require at least one capability: " + id);
            }
            if (sourceKind == SourceKind.NATURAL_OCCURRENCE && compatibleOccurrenceTypeIds.isEmpty()) {
                throw new IllegalArgumentException("Natural extraction method must reference occurrences: " + id);
            }
            if (sourceKind == SourceKind.SALVAGE_STREAM && !compatibleOccurrenceTypeIds.isEmpty()) {
                throw new IllegalArgumentException("Salvage method must not masquerade as a geological occurrence: " + id);
            }
        }
    }

    private static Set<String> immutableSortedSet(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            if (!copy.add(value)) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + value);
            }
        }
        return Collections.unmodifiableSet(copy);
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0, 1]");
        }
    }
}
