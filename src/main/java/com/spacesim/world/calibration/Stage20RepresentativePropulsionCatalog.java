package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned Stage-20 calibration-only propulsion reference catalog.
 *
 * <p>Entries in this catalog are evidence inputs for spatial calibration, not automatically
 * production ship content. Their status and provenance remain explicit so later world-generation
 * code cannot silently promote a benchmark seed into a canonical hull.</p>
 *
 * @param schemaVersion data schema version
 * @param version catalog content version
 * @param status authority status shared by the catalog entries
 * @param sourceBaselineId accepted architecture/baseline that bounds the reference values
 * @param sourceEvidence catalog-level evidence/decision description
 * @param stage22ReviewRequired whether the entries require Stage-22 content review before promotion
 * @param references immutable deterministically ordered reference definitions
 */
public record Stage20RepresentativePropulsionCatalog(
        int schemaVersion,
        String version,
        CalibrationAuthority status,
        String sourceBaselineId,
        String sourceEvidence,
        boolean stage22ReviewRequired,
        List<ReferenceDefinition> references) {
    /** Distinguishes production engineering authority from calibration-only accepted references. */
    public enum CalibrationAuthority {
        /** Values come from the current production ship-engineering pipeline. */
        PRODUCTION_ENGINEERING,
        /** Values come from an accepted benchmark/design reference and still require content review. */
        PROVISIONAL_ACCEPTED_REFERENCE
    }

    /**
     * Creates an immutable deterministically ordered catalog.
     *
     * @param schemaVersion data schema version
     * @param version catalog content version
     * @param status authority status shared by the catalog entries
     * @param sourceBaselineId accepted architecture/baseline boundary
     * @param sourceEvidence catalog-level evidence/decision description
     * @param stage22ReviewRequired whether Stage-22 review is required
     * @param references reference definitions
     */
    public Stage20RepresentativePropulsionCatalog {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNonBlank(version, "version");
        Objects.requireNonNull(status, "status");
        requireNonBlank(sourceBaselineId, "sourceBaselineId");
        requireNonBlank(sourceEvidence, "sourceEvidence");
        Objects.requireNonNull(references, "references");
        ArrayList<ReferenceDefinition> copy = new ArrayList<>(references);
        if (copy.isEmpty()) {
            throw new IllegalArgumentException("references must not be empty");
        }
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("references must not contain null");
        }
        copy.sort(Comparator.comparing(ReferenceDefinition::representativeClass)
                .thenComparing(ReferenceDefinition::id));
        references = List.copyOf(copy);
    }

    /**
     * Finds a reference by stable representative class.
     *
     * @param representativeClass stable class label
     * @return matching reference or {@code null}
     */
    public ReferenceDefinition findByRepresentativeClass(String representativeClass) {
        requireNonBlank(representativeClass, "representativeClass");
        return references.stream()
                .filter(reference -> reference.representativeClass().equals(representativeClass))
                .findFirst()
                .orElse(null);
    }

    /**
     * One accepted physical reference design used only for Stage-20 calibration until promoted.
     *
     * @param id stable calibration reference ID
     * @param representativeClass stable role/class label used by Stage-20 calibration
     * @param sourceEvidenceId exact per-reference numeric/authoring provenance
     * @param designDryMassKg dry design mass
     * @param ammunitionMassKg carried ammunition mass
     * @param missionCargoStoresMassKg carried mission payload, cargo and stores mass
     * @param reactionMassKg carried propulsion reaction mass
     * @param departureMassKg composed departure mass
     * @param thrustN maximum reference thrust
     * @param exhaustVelocityMps reference effective exhaust velocity
     * @param expectedAccelerationMps2 accepted derived departure acceleration
     * @param expectedDeltaVMps accepted derived nominal delta-v
     */
    public record ReferenceDefinition(
            String id,
            String representativeClass,
            String sourceEvidenceId,
            double designDryMassKg,
            double ammunitionMassKg,
            double missionCargoStoresMassKg,
            double reactionMassKg,
            double departureMassKg,
            double thrustN,
            double exhaustVelocityMps,
            double expectedAccelerationMps2,
            double expectedDeltaVMps) {
        /**
         * Ensures programmatically constructed references cannot lose identity/provenance.
         *
         * @param id stable calibration reference ID
         * @param representativeClass stable role/class label used by Stage-20 calibration
         * @param sourceEvidenceId exact per-reference numeric/authoring provenance
         * @param designDryMassKg dry design mass
         * @param ammunitionMassKg carried ammunition mass
         * @param missionCargoStoresMassKg carried mission payload, cargo and stores mass
         * @param reactionMassKg carried propulsion reaction mass
         * @param departureMassKg composed departure mass
         * @param thrustN maximum reference thrust
         * @param exhaustVelocityMps reference effective exhaust velocity
         * @param expectedAccelerationMps2 accepted derived departure acceleration
         * @param expectedDeltaVMps accepted derived nominal delta-v
         */
        public ReferenceDefinition {
            requireNonBlank(id, "id");
            requireNonBlank(representativeClass, "representativeClass");
            requireNonBlank(sourceEvidenceId, "sourceEvidenceId");
        }
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
