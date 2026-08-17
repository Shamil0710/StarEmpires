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
 * Immutable Stage-18H catalog for kg-native construction of ordinary Stage-18E facilities.
 *
 * <p>Reusable construction profiles define mass composition and engineering capability/work
 * requirements. Facility bindings select a profile and installed mass. Profile input fractions are
 * mass-closed, so every completed facility has an explicit physical bill rather than a hidden station
 * upgrade cost.</p>
 */
public final class Stage18FacilityConstructionCatalog {
    private final int schemaVersion;
    private final List<ConstructionProfileDefinition> profiles;
    private final List<FacilityConstructionDefinition> facilities;
    private final Map<String, ConstructionProfileDefinition> profileById;
    private final Map<String, FacilityConstructionDefinition> facilityById;
    private final String fingerprint;

    Stage18FacilityConstructionCatalog(
            int schemaVersion,
            List<ConstructionProfileDefinition> profiles,
            List<FacilityConstructionDefinition> facilities) {
        this.schemaVersion = schemaVersion;
        this.profiles = sortedCopy(profiles, Comparator.comparing(ConstructionProfileDefinition::id));
        this.facilities = sortedCopy(facilities, Comparator.comparing(FacilityConstructionDefinition::facilityDefinitionId));
        this.profileById = index(this.profiles, ConstructionProfileDefinition::id, "construction profile");
        this.facilityById = index(this.facilities, FacilityConstructionDefinition::facilityDefinitionId, "facility construction binding");
        this.fingerprint = computeFingerprint();
    }

    /** @return supported Stage-18H construction schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return immutable deterministic reusable construction profiles */
    public List<ConstructionProfileDefinition> getProfiles() {
        return profiles;
    }

    /** @return immutable deterministic facility construction bindings */
    public List<FacilityConstructionDefinition> getFacilities() {
        return facilities;
    }

    /** @return lowercase SHA-256 fingerprint of construction semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds one reusable construction profile.
     *
     * @param id stable profile ID
     * @return profile, or {@code null}
     */
    public ConstructionProfileDefinition findProfile(String id) {
        return profileById.get(id);
    }

    /**
     * Finds the physical construction definition for one Stage-18E facility.
     *
     * @param facilityDefinitionId Stage-18E facility definition ID
     * @return construction definition, or {@code null}
     */
    public FacilityConstructionDefinition findFacility(String facilityDefinitionId) {
        return facilityById.get(facilityDefinitionId);
    }

    /**
     * Expands one facility binding into its exact physical kg bill.
     *
     * @param facilityDefinitionId Stage-18E facility definition ID
     * @return immutable commodity mass requirements summing to installed mass
     */
    public Map<String, Double> requiredMassByCommodityKg(String facilityDefinitionId) {
        FacilityConstructionDefinition definition = facilityById.get(facilityDefinitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Stage-18H facility construction binding: " + facilityDefinitionId);
        }
        ConstructionProfileDefinition profile = profileById.get(definition.profileId());
        if (profile == null) {
            throw new IllegalStateException("Missing construction profile: " + definition.profileId());
        }
        LinkedHashMap<String, Double> result = new LinkedHashMap<>();
        for (ConstructionInputDefinition input : profile.inputs()) {
            result.put(input.commodityId(), definition.installedMassKg() * input.fractionOfInstalledMass());
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * Returns total engineering work required for one facility.
     *
     * @param facilityDefinitionId Stage-18E facility definition ID
     * @return positive work-seconds
     */
    public double totalWorkSeconds(String facilityDefinitionId) {
        FacilityConstructionDefinition definition = facilityById.get(facilityDefinitionId);
        if (definition == null) {
            throw new IllegalArgumentException("Unknown Stage-18H facility construction binding: " + facilityDefinitionId);
        }
        ConstructionProfileDefinition profile = profileById.get(definition.profileId());
        double work = definition.installedMassKg() * profile.workSecondsPerInstalledKg();
        if (!Double.isFinite(work) || work <= 0d) {
            throw new IllegalStateException("Facility construction work overflow: " + facilityDefinitionId);
        }
        return work;
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(12_288);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (ConstructionProfileDefinition profile : profiles) {
            canonical.append("profile|").append(profile.id()).append('|').append(profile.displayName()).append('|');
            for (ConstructionInputDefinition input : profile.inputs()) {
                canonical.append(input.commodityId()).append('=')
                        .append(Double.toHexString(input.fractionOfInstalledMass())).append(',');
            }
            canonical.append('|').append(String.join(",", profile.requiredCapabilityTags())).append('|')
                    .append(Double.toHexString(profile.workSecondsPerInstalledKg())).append('\n');
        }
        for (FacilityConstructionDefinition facility : facilities) {
            canonical.append("facility|").append(facility.facilityDefinitionId()).append('|')
                    .append(facility.profileId()).append('|')
                    .append(Double.toHexString(facility.installedMassKg())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /**
     * One commodity share of installed facility mass.
     *
     * @param commodityId Stage-18 material/component/industrial-consumable commodity
     * @param fractionOfInstalledMass fraction in {@code (0,1]}; profile inputs sum to one
     */
    public record ConstructionInputDefinition(String commodityId, double fractionOfInstalledMass) {
        /**
         * Validates one physical construction input share.
         *
         * @param commodityId Stage-18 commodity ID
         * @param fractionOfInstalledMass positive mass fraction
         */
        public ConstructionInputDefinition {
            commodityId = requireText(commodityId, "commodityId");
            requireFraction(fractionOfInstalledMass, "fractionOfInstalledMass");
        }
    }

    /**
     * Reusable mass/work recipe for a physical facility family.
     *
     * @param id stable construction profile ID
     * @param displayName diagnostic/display name
     * @param inputs mass shares summing exactly to one
     * @param requiredCapabilityTags Stage-18 fabrication capabilities required for installation work
     * @param workSecondsPerInstalledKg engineering work-seconds per installed kilogram
     */
    public record ConstructionProfileDefinition(
            String id,
            String displayName,
            List<ConstructionInputDefinition> inputs,
            Set<String> requiredCapabilityTags,
            double workSecondsPerInstalledKg) {
        /**
         * Validates and freezes one construction profile.
         *
         * @param id stable profile ID
         * @param displayName display name
         * @param inputs physical mass shares
         * @param requiredCapabilityTags construction capabilities
         * @param workSecondsPerInstalledKg work per installed kilogram
         */
        public ConstructionProfileDefinition {
            id = requireText(id, "construction profile id");
            displayName = requireText(displayName, "construction profile displayName");
            inputs = freezeInputs(inputs, id);
            requiredCapabilityTags = immutableNonEmptySet(requiredCapabilityTags, "requiredCapabilityTags");
            requirePositive(workSecondsPerInstalledKg, "workSecondsPerInstalledKg");
        }
    }

    /**
     * Physical construction binding for one Stage-18E facility.
     *
     * @param facilityDefinitionId Stage-18E facility definition ID
     * @param profileId reusable Stage-18H construction profile ID
     * @param installedMassKg final installed facility mass and total delivered input mass
     */
    public record FacilityConstructionDefinition(
            String facilityDefinitionId,
            String profileId,
            double installedMassKg) {
        /**
         * Validates one facility construction binding.
         *
         * @param facilityDefinitionId Stage-18E facility ID
         * @param profileId construction profile ID
         * @param installedMassKg positive installed mass
         */
        public FacilityConstructionDefinition {
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            profileId = requireText(profileId, "profileId");
            requirePositive(installedMassKg, "installedMassKg");
        }
    }

    private static List<ConstructionInputDefinition> freezeInputs(
            List<ConstructionInputDefinition> source, String subject) {
        Objects.requireNonNull(source, "inputs");
        if (source.isEmpty()) {
            throw new IllegalArgumentException("Construction inputs must not be empty: " + subject);
        }
        List<ConstructionInputDefinition> copy = new ArrayList<>(source);
        copy.sort(Comparator.comparing(ConstructionInputDefinition::commodityId));
        TreeSet<String> ids = new TreeSet<>();
        double total = 0d;
        for (ConstructionInputDefinition input : copy) {
            Objects.requireNonNull(input, "construction input");
            if (!ids.add(input.commodityId())) {
                throw new IllegalArgumentException("Duplicate construction input " + input.commodityId() + " for " + subject);
            }
            total += input.fractionOfInstalledMass();
        }
        if (Math.abs(total - 1d) > 1e-9d) {
            throw new IllegalArgumentException("Construction input fractions must sum to 1: " + subject);
        }
        return List.copyOf(copy);
    }

    private static Set<String> immutableNonEmptySet(Set<String> source, String name) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            copy.add(requireText(value, name + " entry"));
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
        return Collections.unmodifiableSet(copy);
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<T> comparator) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, "source"));
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(
            List<T> source,
            java.util.function.Function<T, String> id,
            String label) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : source) {
            String key = id.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + key);
            }
        }
        return Collections.unmodifiableMap(result);
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

    private static void requireFraction(double value, String name) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in (0,1]");
        }
    }
}
