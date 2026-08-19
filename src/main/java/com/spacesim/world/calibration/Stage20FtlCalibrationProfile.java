package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Versioned Stage-20 FTL mass/energy/spool/transit/cooldown calibration output.
 *
 * <p>The profile evaluates the current Stage-20 representative departure masses against one
 * accepted-reference jump drive. It does not claim that every representative actually has this
 * drive installed; it measures compatibility and keeps both ship-capability and FTL-reference
 * provenance visible.</p>
 *
 * @param version stable FTL calibration-profile version
 * @param reference accepted FTL calibration reference used by all samples
 * @param samples deterministic per-representative one-edge samples
 */
public record Stage20FtlCalibrationProfile(
        String version,
        Stage20FtlCalibrationReference reference,
        List<JumpEdgeCalibrationSample> samples) {
    /** Current Stage-20A FTL edge-cadence profile version. */
    public static final String CURRENT_VERSION = "stage20a.ftl-edge-cadence.v2";

    /** Reference-drive compatibility of one translated-mass sample. */
    public enum ReferenceDriveCompatibility {
        /** Departure mass lies inside the accepted reference-drive translated-mass envelope. */
        COMPATIBLE,
        /** Departure mass exceeds the accepted reference-drive translated-mass envelope. */
        EXCEEDS_TRANSLATED_MASS_LIMIT
    }

    /**
     * Creates an immutable deterministically ordered FTL calibration profile.
     *
     * @param version stable profile version
     * @param reference accepted FTL calibration reference
     * @param samples one-edge representative samples
     */
    public Stage20FtlCalibrationProfile {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(samples, "samples");
        ArrayList<JumpEdgeCalibrationSample> copy = new ArrayList<>(samples);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must not contain null");
        }
        copy.sort(Comparator.comparing(JumpEdgeCalibrationSample::representativeId));
        samples = List.copyOf(copy);
    }

    /**
     * Derives the current FTL calibration profile from the current Stage-20 representative masses.
     *
     * @return deterministic FTL reference-drive compatibility/cadence profile
     */
    public static Stage20FtlCalibrationProfile deriveCurrent() {
        Stage20FtlCalibrationReference reference = Stage20FtlCalibrationReferenceLoader.loadDefault();
        Stage20ScaleCalibrationProfile scaleProfile = Stage20ScaleCalibrationProfile.deriveCurrent();
        List<JumpEdgeCalibrationSample> samples = scaleProfile.representativeShips().stream()
                .map(representative -> Stage20FtlCalibrationCalculator.derive(reference, representative))
                .toList();
        return new Stage20FtlCalibrationProfile(CURRENT_VERSION, reference, samples);
    }

    /**
     * One representative one-neighbor-edge FTL calibration result.
     *
     * @param representativeId stable Stage-20 representative ID
     * @param shipAuthority authority of the translated-mass source
     * @param shipProvenanceId exact translated-mass provenance
     * @param ftlAuthority authority of the FTL reference law
     * @param ftlProvenanceId exact FTL reference-drive provenance
     * @param translatedMassKg departure mass tested against the reference drive
     * @param referenceMaxTranslatedMassKg accepted mass limit of one reference drive
     * @param translatedMassToLimitRatio translated mass divided by the accepted drive limit
     * @param compatibility compatibility result
     * @param requiredTranslationEnergyJ translation energy when compatible; absent outside the drive domain
     * @param spoolTimeS spool/charge time when compatible; absent outside the drive domain
     * @param referenceEdgeTransitTimeS accepted example transit time for one neighboring topology edge
     * @param cooldownS accepted reference-drive post-jump cooldown
     * @param readyAgainCadenceS spool + one-edge transit + cooldown when compatible
     */
    public record JumpEdgeCalibrationSample(
            String representativeId,
            CalibrationAuthority shipAuthority,
            String shipProvenanceId,
            CalibrationAuthority ftlAuthority,
            String ftlProvenanceId,
            double translatedMassKg,
            double referenceMaxTranslatedMassKg,
            double translatedMassToLimitRatio,
            ReferenceDriveCompatibility compatibility,
            OptionalDouble requiredTranslationEnergyJ,
            OptionalDouble spoolTimeS,
            double referenceEdgeTransitTimeS,
            double cooldownS,
            OptionalDouble readyAgainCadenceS) {
        /**
         * Creates one validated immutable one-edge calibration sample.
         *
         * @param representativeId stable Stage-20 representative ID
         * @param shipAuthority translated-mass authority
         * @param shipProvenanceId translated-mass provenance
         * @param ftlAuthority FTL-law authority
         * @param ftlProvenanceId FTL-law provenance
         * @param translatedMassKg departure mass tested against the reference drive
         * @param referenceMaxTranslatedMassKg accepted reference-drive mass limit
         * @param translatedMassToLimitRatio translated mass divided by the drive limit
         * @param compatibility reference-drive compatibility
         * @param requiredTranslationEnergyJ compatible-domain translation energy
         * @param spoolTimeS compatible-domain spool time
         * @param referenceEdgeTransitTimeS accepted one-edge transit sample
         * @param cooldownS accepted post-jump cooldown
         * @param readyAgainCadenceS compatible-domain ready-again cadence
         */
        public JumpEdgeCalibrationSample {
            requireNonBlank(representativeId, "representativeId");
            Objects.requireNonNull(shipAuthority, "shipAuthority");
            requireNonBlank(shipProvenanceId, "shipProvenanceId");
            Objects.requireNonNull(ftlAuthority, "ftlAuthority");
            requireNonBlank(ftlProvenanceId, "ftlProvenanceId");
            requirePositiveFinite(translatedMassKg, "translatedMassKg");
            requirePositiveFinite(referenceMaxTranslatedMassKg, "referenceMaxTranslatedMassKg");
            requirePositiveFinite(translatedMassToLimitRatio, "translatedMassToLimitRatio");
            Objects.requireNonNull(compatibility, "compatibility");
            Objects.requireNonNull(requiredTranslationEnergyJ, "requiredTranslationEnergyJ");
            Objects.requireNonNull(spoolTimeS, "spoolTimeS");
            requirePositiveFinite(referenceEdgeTransitTimeS, "referenceEdgeTransitTimeS");
            requireNonNegativeFinite(cooldownS, "cooldownS");
            Objects.requireNonNull(readyAgainCadenceS, "readyAgainCadenceS");

            boolean compatible = compatibility == ReferenceDriveCompatibility.COMPATIBLE;
            boolean allDerivedPresent = requiredTranslationEnergyJ.isPresent()
                    && spoolTimeS.isPresent()
                    && readyAgainCadenceS.isPresent();
            boolean allDerivedAbsent = requiredTranslationEnergyJ.isEmpty()
                    && spoolTimeS.isEmpty()
                    && readyAgainCadenceS.isEmpty();
            if (compatible && !allDerivedPresent) {
                throw new IllegalArgumentException("compatible FTL sample requires energy, spool and cadence");
            }
            if (!compatible && !allDerivedAbsent) {
                throw new IllegalArgumentException("out-of-domain FTL sample must not extrapolate derived values");
            }
            requiredTranslationEnergyJ.ifPresent(value -> requirePositiveFinite(value, "requiredTranslationEnergyJ"));
            spoolTimeS.ifPresent(value -> requirePositiveFinite(value, "spoolTimeS"));
            readyAgainCadenceS.ifPresent(value -> requirePositiveFinite(value, "readyAgainCadenceS"));
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
