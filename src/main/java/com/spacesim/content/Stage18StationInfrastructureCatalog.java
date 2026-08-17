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
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Immutable Stage-18F catalog of composable station infrastructure archetypes.
 *
 * <p>An archetype is a bootstrap composition of explicit Stage-18E facility definitions, physical
 * storage capacities and cargo-handling limits. It never contains recipe bonuses or implicit
 * production multipliers; a storage-only logistics hub is therefore a valid archetype with no
 * installed industrial facilities.</p>
 */
public final class Stage18StationInfrastructureCatalog {
    private final int schemaVersion;
    private final List<StationArchetypeDefinition> archetypes;
    private final Map<String, StationArchetypeDefinition> archetypesById;
    private final String fingerprint;

    Stage18StationInfrastructureCatalog(int schemaVersion, List<StationArchetypeDefinition> archetypes) {
        this.schemaVersion = schemaVersion;
        List<StationArchetypeDefinition> copy = new ArrayList<>(Objects.requireNonNull(archetypes, "archetypes"));
        copy.sort(Comparator.comparing(StationArchetypeDefinition::id));
        Map<String, StationArchetypeDefinition> index = new LinkedHashMap<>();
        for (StationArchetypeDefinition archetype : copy) {
            if (index.putIfAbsent(archetype.id(), archetype) != null) {
                throw new IllegalArgumentException("Duplicate Stage-18 station archetype: " + archetype.id());
            }
        }
        this.archetypes = List.copyOf(copy);
        this.archetypesById = Collections.unmodifiableMap(index);
        this.fingerprint = computeFingerprint();
    }

    /** @return Stage-18F station-infrastructure schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable station archetypes */
    public List<StationArchetypeDefinition> getArchetypes() {
        return archetypes;
    }

    /** @return lowercase SHA-256 fingerprint of station-infrastructure semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds a station infrastructure archetype by stable ID.
     *
     * @param id stable Stage-18F archetype ID
     * @return archetype definition, or {@code null} when unknown
     */
    public StationArchetypeDefinition findArchetype(String id) {
        return archetypesById.get(id);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(12_288);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (StationArchetypeDefinition archetype : archetypes) {
            canonical.append("station|").append(archetype.id()).append('|')
                    .append(archetype.displayName()).append('|')
                    .append(String.join(",", archetype.installedFacilityDefinitionIds())).append('|');
            for (Map.Entry<String, Double> capacity : archetype.storageCapacityByClassKg().entrySet()) {
                canonical.append(capacity.getKey()).append('=')
                        .append(Double.toHexString(capacity.getValue())).append(',');
            }
            canonical.append('|').append(String.join(",", archetype.transferStorageClassIds())).append('|')
                    .append(Double.toHexString(archetype.transferMassRateKgPerSecond())).append('|')
                    .append(Double.toHexString(archetype.maxTransferUnitMassKg())).append('|')
                    .append(String.join(",", archetype.allowedLocationTags())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /**
     * One readable station role expressed entirely as installed physical infrastructure.
     *
     * @param id stable Stage-18F archetype ID
     * @param displayName diagnostic/display name
     * @param installedFacilityDefinitionIds Stage-18E facility definitions instantiated by the template
     * @param storageCapacityByClassKg physical storage capacity by Stage-18 storage class
     * @param transferStorageClassIds storage classes supported by the template's cargo-handling interface
     * @param transferMassRateKgPerSecond total cargo-handling throughput in kilograms per second
     * @param maxTransferUnitMassKg maximum single finished-product unit mass handled by the interface
     * @param allowedLocationTags physical station/outpost locations where the template may be instantiated
     */
    public record StationArchetypeDefinition(
            String id,
            String displayName,
            List<String> installedFacilityDefinitionIds,
            Map<String, Double> storageCapacityByClassKg,
            Set<String> transferStorageClassIds,
            double transferMassRateKgPerSecond,
            double maxTransferUnitMassKg,
            Set<String> allowedLocationTags) {
        /**
         * Validates and freezes one station-infrastructure archetype.
         *
         * @param id stable archetype ID
         * @param displayName diagnostic/display name
         * @param installedFacilityDefinitionIds explicit installed Stage-18E facility definitions
         * @param storageCapacityByClassKg capacity by storage class
         * @param transferStorageClassIds cargo classes supported by handling equipment
         * @param transferMassRateKgPerSecond cargo-handling mass rate
         * @param maxTransferUnitMassKg maximum handled finished unit mass
         * @param allowedLocationTags allowed physical station locations
         */
        public StationArchetypeDefinition {
            requireText(id, "station archetype id");
            requireText(displayName, "station archetype displayName");
            installedFacilityDefinitionIds = immutableIdList(
                    installedFacilityDefinitionIds, "installedFacilityDefinitionIds");
            storageCapacityByClassKg = immutableCapacityMap(storageCapacityByClassKg);
            transferStorageClassIds = immutableIdSet(transferStorageClassIds, "transferStorageClassIds", true);
            requirePositive(transferMassRateKgPerSecond, "transferMassRateKgPerSecond");
            requirePositive(maxTransferUnitMassKg, "maxTransferUnitMassKg");
            allowedLocationTags = immutableIdSet(allowedLocationTags, "allowedLocationTags", true);
            if (!storageCapacityByClassKg.keySet().containsAll(transferStorageClassIds)) {
                throw new IllegalArgumentException("Transfer interface references storage class without capacity: " + id);
            }
        }
    }

    private static List<String> immutableIdList(List<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> unique = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            if (!unique.add(value)) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + value);
            }
        }
        return List.copyOf(unique);
    }

    private static Set<String> immutableIdSet(Set<String> source, String name, boolean requireNonEmpty) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            if (!copy.add(value)) {
                throw new IllegalArgumentException("Duplicate " + name + " entry: " + value);
            }
        }
        if (requireNonEmpty && copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Collections.unmodifiableSet(copy);
    }

    private static Map<String, Double> immutableCapacityMap(Map<String, Double> source) {
        Objects.requireNonNull(source, "storageCapacityByClassKg");
        TreeMap<String, Double> copy = new TreeMap<>();
        for (Map.Entry<String, Double> entry : source.entrySet()) {
            String id = requireText(entry.getKey(), "storage class ID");
            double capacity = Objects.requireNonNull(entry.getValue(), "storage capacity");
            requireNonNegative(capacity, "storage capacity");
            if (capacity > 0d) {
                copy.put(id, capacity);
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("Station archetype requires positive physical storage capacity");
        }
        return Collections.unmodifiableMap(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }

    private static void requirePositive(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(name + " must be finite and positive");
        }
    }

    private static void requireNonNegative(double value, String name) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(name + " must be finite and non-negative");
        }
    }
}
