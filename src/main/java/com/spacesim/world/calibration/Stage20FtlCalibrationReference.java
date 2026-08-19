package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Versioned accepted-reference FTL input for Stage-20 spatial calibration.
 *
 * <p>This model is deliberately calibration-only until an FTL module is authored in production ship
 * engineering. The topology contract remains explicit: one jump traverses one neighboring system
 * edge; the reference edge-transit sample is evidence for cadence calibration, not a universal
 * direct-jump range or a hidden strategic distance unit.</p>
 *
 * @param schemaVersion resource schema version
 * @param version calibration reference version
 * @param status authority of the accepted FTL reference
 * @param sourceBaselineId accepted design-baseline provenance
 * @param sourceEvidence executable evidence provenance
 * @param stage22ReviewRequired whether Stage-22 content review is required before production promotion
 * @param topologyMode topology semantics of one jump operation
 * @param referenceDrive accepted reference-drive capability
 * @param referenceClosure accepted destroyer closure sample
 * @param unresolvedGaps known capability/data gaps that Stage 20 must not silently fill
 */
public record Stage20FtlCalibrationReference(
        int schemaVersion,
        String version,
        CalibrationAuthority status,
        String sourceBaselineId,
        String sourceEvidence,
        boolean stage22ReviewRequired,
        JumpTopologyMode topologyMode,
        ReferenceDrive referenceDrive,
        ReferenceClosure referenceClosure,
        List<CalibrationGap> unresolvedGaps) {
    /** One ordinary FTL operation traverses exactly one neighboring-system topology edge. */
    public enum JumpTopologyMode {
        /** Ordinary strategic travel may only traverse one neighboring-system edge per jump. */
        NEIGHBOR_EDGE_ONLY
    }

    /** Explicit Stage-20 gaps retained instead of inventing unsupported FTL capability. */
    public enum CalibrationGap {
        /** No production FTL module has yet replaced the accepted design reference. */
        PRODUCTION_FTL_MODULE_NOT_AUTHORED,
        /** Generated-world edge transit distributions have not yet been authored/calibrated. */
        EDGE_TRANSIT_DISTRIBUTION_NOT_YET_WORLD_AUTHORED,
        /** The v1.0 FTL reference requires heat accounting but does not provide a numeric heat coefficient. */
        DRIVE_HEAT_COEFFICIENT_NOT_NUMERIC_IN_V1_BASELINE
    }

    /**
     * Creates one immutable validated FTL calibration reference.
     *
     * @param schemaVersion resource schema version
     * @param version calibration reference version
     * @param status authority of the accepted FTL reference
     * @param sourceBaselineId accepted design-baseline provenance
     * @param sourceEvidence executable evidence provenance
     * @param stage22ReviewRequired whether Stage-22 review is required
     * @param topologyMode one-jump topology semantics
     * @param referenceDrive accepted reference-drive capability
     * @param referenceClosure accepted destroyer closure sample
     * @param unresolvedGaps known unresolved capability/data gaps
     */
    public Stage20FtlCalibrationReference {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireNonBlank(version, "version");
        Objects.requireNonNull(status, "status");
        requireNonBlank(sourceBaselineId, "sourceBaselineId");
        requireNonBlank(sourceEvidence, "sourceEvidence");
        Objects.requireNonNull(topologyMode, "topologyMode");
        Objects.requireNonNull(referenceDrive, "referenceDrive");
        Objects.requireNonNull(referenceClosure, "referenceClosure");
        Objects.requireNonNull(unresolvedGaps, "unresolvedGaps");
        ArrayList<CalibrationGap> copy = new ArrayList<>(unresolvedGaps);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("unresolvedGaps must not contain null");
        }
        if (new LinkedHashSet<>(copy).size() != copy.size()) {
            throw new IllegalArgumentException("unresolvedGaps must not contain duplicates");
        }
        unresolvedGaps = List.copyOf(copy);
    }

    /**
     * Accepted reference-drive capability values.
     *
     * @param id stable calibration drive ID
     * @param maxTranslatedMassKg maximum translated mass supported by this one reference drive
     * @param translationEnergyPerKgJ translation energy required per translated kilogram
     * @param chargePowerW electrical charge input power
     * @param chargeEfficiency fraction of charge input power that becomes useful translation energy
     * @param cooldownS post-jump cooldown before the reference drive is ready again
     */
    public record ReferenceDrive(
            String id,
            double maxTranslatedMassKg,
            double translationEnergyPerKgJ,
            double chargePowerW,
            double chargeEfficiency,
            double cooldownS) {
        /**
         * Creates one validated reference-drive capability record.
         *
         * @param id stable calibration drive ID
         * @param maxTranslatedMassKg maximum translated mass
         * @param translationEnergyPerKgJ translation energy per translated kilogram
         * @param chargePowerW electrical charge input power
         * @param chargeEfficiency useful charge efficiency in the interval {@code (0, 1]}
         * @param cooldownS non-negative post-jump cooldown
         */
        public ReferenceDrive {
            requireNonBlank(id, "id");
            requirePositiveFinite(maxTranslatedMassKg, "maxTranslatedMassKg");
            requirePositiveFinite(translationEnergyPerKgJ, "translationEnergyPerKgJ");
            requirePositiveFinite(chargePowerW, "chargePowerW");
            requirePositiveFinite(chargeEfficiency, "chargeEfficiency");
            if (chargeEfficiency > 1d) {
                throw new IllegalArgumentException("chargeEfficiency must not exceed one");
            }
            requireNonNegativeFinite(cooldownS, "cooldownS");
        }
    }

    /**
     * Accepted reference-destroyer closure used to detect drift in FTL equations/data.
     *
     * @param translatedMassKg reference translated mass
     * @param requiredTranslationEnergyJ accepted translation-energy result
     * @param spoolTimeS accepted charge/spool result
     * @param exampleEdgeTransitTimeS accepted example transit time for one topology edge
     */
    public record ReferenceClosure(
            double translatedMassKg,
            double requiredTranslationEnergyJ,
            double spoolTimeS,
            double exampleEdgeTransitTimeS) {
        /**
         * Creates one validated closure sample.
         *
         * @param translatedMassKg reference translated mass
         * @param requiredTranslationEnergyJ accepted translation-energy result
         * @param spoolTimeS accepted charge/spool result
         * @param exampleEdgeTransitTimeS accepted example one-edge transit time
         */
        public ReferenceClosure {
            requirePositiveFinite(translatedMassKg, "translatedMassKg");
            requirePositiveFinite(requiredTranslationEnergyJ, "requiredTranslationEnergyJ");
            requirePositiveFinite(spoolTimeS, "spoolTimeS");
            requirePositiveFinite(exampleEdgeTransitTimeS, "exampleEdgeTransitTimeS");
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

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
