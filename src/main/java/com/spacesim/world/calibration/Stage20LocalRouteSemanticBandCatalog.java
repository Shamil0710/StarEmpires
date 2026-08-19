package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned Stage-20 operational local-route distance-band authoring catalog.
 *
 * @param schemaVersion resource schema version
 * @param version stable content version
 * @param status calibration authority
 * @param stage22ReviewRequired whether later balance/content review remains required
 * @param policyEvidence catalog-level authority statement
 * @param bands semantic distance bands
 */
public record Stage20LocalRouteSemanticBandCatalog(
        int schemaVersion,
        String version,
        CalibrationAuthority status,
        boolean stage22ReviewRequired,
        String policyEvidence,
        List<BandDefinition> bands) {

    /** Required Stage-20A local route semantics. */
    public enum BandId {
        /** Major local facilities / stations. */ STATION_TO_STATION,
        /** Industrial hub to resource occurrence/field. */ STATION_TO_RESOURCE_FIELD,
        /** Jump-arrival operational region to major hub. */ JUMP_ARRIVAL_TO_MAJOR_HUB,
        /** Representative inner-system to outer-system operational leg. */ INNER_TO_OUTER_SYSTEM
    }

    /**
     * Creates one immutable deterministic authored catalog.
     *
     * @param schemaVersion resource schema version
     * @param version stable content version
     * @param status calibration authority
     * @param stage22ReviewRequired whether later balance review remains required
     * @param policyEvidence authority statement
     * @param bands semantic distance bands
     */
    public Stage20LocalRouteSemanticBandCatalog {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNonBlank(version, "version");
        Objects.requireNonNull(status, "status");
        requireNonBlank(policyEvidence, "policyEvidence");
        Objects.requireNonNull(bands, "bands");
        ArrayList<BandDefinition> copy = new ArrayList<>(bands);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("bands must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparing(value -> value.id().name()));
        bands = List.copyOf(copy);
    }

    /**
     * One authored operational distance interval.
     *
     * @param id semantic route ID
     * @param minDistanceM lower operational distance in meters
     * @param maxDistanceM upper operational distance in meters
     * @param sourceEvidenceId exact authoring provenance
     */
    public record BandDefinition(
            BandId id,
            double minDistanceM,
            double maxDistanceM,
            String sourceEvidenceId) {
        /**
         * Validates one operational band.
         *
         * @param id semantic route ID
         * @param minDistanceM lower operational distance in meters
         * @param maxDistanceM upper operational distance in meters
         * @param sourceEvidenceId exact authoring provenance
         */
        public BandDefinition {
            Objects.requireNonNull(id, "id");
            requirePositiveFinite(minDistanceM, "minDistanceM");
            requirePositiveFinite(maxDistanceM, "maxDistanceM");
            if (minDistanceM > maxDistanceM) {
                throw new IllegalArgumentException("minDistanceM cannot exceed maxDistanceM");
            }
            requireNonBlank(sourceEvidenceId, "sourceEvidenceId");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
