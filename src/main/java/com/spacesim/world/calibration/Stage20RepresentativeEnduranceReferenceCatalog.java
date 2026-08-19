package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned calibration-only sustained-thrust and operational-endurance reference catalog.
 *
 * @param schemaVersion resource schema version
 * @param version stable content version
 * @param status calibration authority
 * @param stage22ReviewRequired whether content review remains required
 * @param policyEvidence catalog-level authority statement
 * @param references deterministic role references
 */
public record Stage20RepresentativeEnduranceReferenceCatalog(
        int schemaVersion,
        String version,
        CalibrationAuthority status,
        boolean stage22ReviewRequired,
        String policyEvidence,
        List<ReferenceDefinition> references) {

    /**
     * Creates an immutable deterministically ordered endurance reference catalog.
     *
     * @param schemaVersion resource schema version
     * @param version stable content version
     * @param status calibration authority
     * @param stage22ReviewRequired whether content review remains required
     * @param policyEvidence catalog-level authority statement
     * @param references deterministic role references
     */
    public Stage20RepresentativeEnduranceReferenceCatalog {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNonBlank(version, "version");
        Objects.requireNonNull(status, "status");
        requireNonBlank(policyEvidence, "policyEvidence");
        Objects.requireNonNull(references, "references");
        ArrayList<ReferenceDefinition> copy = new ArrayList<>(references);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("references must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparing(ReferenceDefinition::representativeClass));
        references = List.copyOf(copy);
    }

    /**
     * Finds one role reference.
     *
     * @param representativeClass stable representative role ID
     * @return matching reference or {@code null}
     */
    public ReferenceDefinition findByRepresentativeClass(String representativeClass) {
        requireNonBlank(representativeClass, "representativeClass");
        return references.stream()
                .filter(value -> value.representativeClass().equals(representativeClass))
                .findFirst()
                .orElse(null);
    }

    /**
     * One calibration-only sustained-thrust and mission-endurance policy row.
     *
     * @param representativeClass stable representative role ID
     * @param sustainedThrustN accepted sustained thrust seed
     * @param sustainedThrustSourceEvidenceId exact thrust provenance
     * @param missionStoresEnduranceS nominal operational stores endurance
     * @param missionStoresSourceEvidenceId exact mission-policy provenance
     */
    public record ReferenceDefinition(
            String representativeClass,
            double sustainedThrustN,
            String sustainedThrustSourceEvidenceId,
            double missionStoresEnduranceS,
            String missionStoresSourceEvidenceId) {
        /**
         * Validates one endurance reference row.
         *
         * @param representativeClass stable representative role ID
         * @param sustainedThrustN accepted sustained thrust seed
         * @param sustainedThrustSourceEvidenceId exact thrust provenance
         * @param missionStoresEnduranceS nominal operational stores endurance
         * @param missionStoresSourceEvidenceId exact mission-policy provenance
         */
        public ReferenceDefinition {
            requireNonBlank(representativeClass, "representativeClass");
            requirePositiveFinite(sustainedThrustN, "sustainedThrustN");
            requireNonBlank(sustainedThrustSourceEvidenceId, "sustainedThrustSourceEvidenceId");
            requirePositiveFinite(missionStoresEnduranceS, "missionStoresEnduranceS");
            requireNonBlank(missionStoresSourceEvidenceId, "missionStoresSourceEvidenceId");
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
