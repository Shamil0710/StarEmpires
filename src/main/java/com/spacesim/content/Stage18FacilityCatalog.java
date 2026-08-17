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
 * Immutable Stage-18E catalog of physical industrial facility capabilities.
 *
 * <p>A facility definition exposes explicit process tags and finite rated resources. Station roles
 * do not appear here: a refinery station, industrial hub or naval base receives industrial ability
 * only from the actual facility definitions installed there.</p>
 */
public final class Stage18FacilityCatalog {
    private final int schemaVersion;
    private final List<FacilityDefinition> facilities;
    private final Map<String, FacilityDefinition> facilitiesById;
    private final String fingerprint;

    Stage18FacilityCatalog(int schemaVersion, List<FacilityDefinition> facilities) {
        this.schemaVersion = schemaVersion;
        List<FacilityDefinition> copy = new ArrayList<>(Objects.requireNonNull(facilities, "facilities"));
        copy.sort(Comparator.comparing(FacilityDefinition::id));
        Map<String, FacilityDefinition> index = new LinkedHashMap<>();
        for (FacilityDefinition facility : copy) {
            if (index.putIfAbsent(facility.id(), facility) != null) {
                throw new IllegalArgumentException("Duplicate facility definition: " + facility.id());
            }
        }
        this.facilities = List.copyOf(copy);
        this.facilitiesById = Collections.unmodifiableMap(index);
        this.fingerprint = computeFingerprint();
    }

    /** @return Stage-18E facility schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return deterministic immutable facility definitions */
    public List<FacilityDefinition> getFacilities() {
        return facilities;
    }

    /** @return lowercase SHA-256 fingerprint of facility semantics */
    public String getFingerprint() {
        return fingerprint;
    }

    /**
     * Finds a facility definition by stable ID.
     *
     * @param id facility definition ID
     * @return facility definition, or {@code null} when unknown
     */
    public FacilityDefinition findFacility(String id) {
        return facilitiesById.get(id);
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(16_384);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (FacilityDefinition facility : facilities) {
            canonical.append("facility|").append(facility.id()).append('|')
                    .append(facility.displayName()).append('|').append(facility.family().name()).append('|')
                    .append(String.join(",", facility.capabilityTags())).append('|')
                    .append(Double.toHexString(facility.ratedProcessPowerW())).append('|')
                    .append(Double.toHexString(facility.engineeringWorkRate())).append('|')
                    .append(Double.toHexString(facility.maintenanceWorkRate())).append('|')
                    .append(Double.toHexString(facility.heatRejectionWPerProcessW())).append('|')
                    .append(Double.toHexString(facility.requiredLaborUnitsAtFullRate())).append('|')
                    .append(Double.toHexString(facility.automationFloorFraction())).append('|')
                    .append(String.join(",", facility.storageClassInterfaces())).append('|')
                    .append(Double.toHexString(facility.maxHandledUnitMassKg())).append('|')
                    .append(String.join(",", facility.allowedLocationTags())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** Broad physical role of an industrial facility. */
    public enum FacilityFamily {
        /** Facility that removes finite physical mass from a natural/salvage source. */
        EXTRACTION,
        /** Facility that refines, separates, processes or recycles physical feedstock. */
        PROCESSING,
        /** Facility that fabricates components, ammunition or finished equipment. */
        FABRICATION
    }

    /**
     * One data-driven installed-facility archetype.
     *
     * @param id stable facility ID
     * @param displayName diagnostic/display name
     * @param family broad physical facility role
     * @param capabilityTags Stage-18 process/fabrication capabilities exposed while operational
     * @param ratedProcessPowerW maximum process power at pristine condition
     * @param engineeringWorkRate engineering work-seconds completed per simulation second at full rate
     * @param maintenanceWorkRate maintenance work-seconds supplied per simulation second at full rate
     * @param heatRejectionWPerProcessW required heat rejection watts per watt of process power
     * @param requiredLaborUnitsAtFullRate abstract staffed labor-equivalent units for full non-automated rate
     * @param automationFloorFraction fraction of work rate retained with zero staffed labor
     * @param storageClassInterfaces Stage-18 storage classes the facility can physically exchange with
     * @param maxHandledUnitMassKg largest single handled finished/source unit mass
     * @param allowedLocationTags physical installation-location tags accepted by this facility
     */
    public record FacilityDefinition(
            String id,
            String displayName,
            FacilityFamily family,
            Set<String> capabilityTags,
            double ratedProcessPowerW,
            double engineeringWorkRate,
            double maintenanceWorkRate,
            double heatRejectionWPerProcessW,
            double requiredLaborUnitsAtFullRate,
            double automationFloorFraction,
            Set<String> storageClassInterfaces,
            double maxHandledUnitMassKg,
            Set<String> allowedLocationTags) {
        /**
         * Validates and freezes one facility definition.
         *
         * @param id stable facility ID
         * @param displayName diagnostic/display name
         * @param family broad facility role
         * @param capabilityTags exposed process/fabrication capabilities
         * @param ratedProcessPowerW pristine rated process power
         * @param engineeringWorkRate pristine engineering work rate
         * @param maintenanceWorkRate pristine maintenance work rate
         * @param heatRejectionWPerProcessW heat rejection demand per process watt
         * @param requiredLaborUnitsAtFullRate staffed labor units for full non-automated output
         * @param automationFloorFraction zero-staff automation floor in {@code [0,1]}
         * @param storageClassInterfaces compatible storage-class interfaces
         * @param maxHandledUnitMassKg largest handled single-unit mass
         * @param allowedLocationTags accepted physical installation locations
         */
        public FacilityDefinition {
            requireText(id, "facility id");
            requireText(displayName, "facility displayName");
            Objects.requireNonNull(family, "family");
            capabilityTags = immutableNonEmptySet(capabilityTags, "capabilityTags", id);
            requirePositive(ratedProcessPowerW, "ratedProcessPowerW");
            requirePositive(engineeringWorkRate, "engineeringWorkRate");
            requirePositive(maintenanceWorkRate, "maintenanceWorkRate");
            requirePositive(heatRejectionWPerProcessW, "heatRejectionWPerProcessW");
            requireNonNegative(requiredLaborUnitsAtFullRate, "requiredLaborUnitsAtFullRate");
            requireFractionInclusive(automationFloorFraction, "automationFloorFraction");
            storageClassInterfaces = immutableNonEmptySet(storageClassInterfaces, "storageClassInterfaces", id);
            requirePositive(maxHandledUnitMassKg, "maxHandledUnitMassKg");
            allowedLocationTags = immutableNonEmptySet(allowedLocationTags, "allowedLocationTags", id);
        }
    }

    private static Set<String> immutableNonEmptySet(Set<String> source, String name, String subject) {
        Objects.requireNonNull(source, name);
        TreeSet<String> copy = new TreeSet<>();
        for (String value : source) {
            requireText(value, name + " entry");
            if (!copy.add(value)) {
                throw new IllegalArgumentException("Duplicate " + name + " entry " + value + " for " + subject);
            }
        }
        if (copy.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty for " + subject);
        }
        return Collections.unmodifiableSet(copy);
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

    private static void requireFractionInclusive(double value, String name) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(name + " must be in [0, 1]");
        }
    }
}
