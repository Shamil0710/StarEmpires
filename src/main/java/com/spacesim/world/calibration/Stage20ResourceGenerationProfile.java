package com.spacesim.world.calibration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Versioned provisional Stage-20E calibration for correlated resource geography.
 *
 * <p>The profile does not add resource families: every rule references an existing Stage-18
 * occurrence type ID. Numeric reserve/grade bands are aggregate accessible-source calibration,
 * not implied resource-field radii. They remain explicitly provisional until Stage 22 balance
 * review because Stage 20C deliberately canonicalized resource fields as point anchors only.</p>
 */
public final class Stage20ResourceGenerationProfile {
    /** Current Stage-20E resource-geography calibration version. */
    public static final String CURRENT_VERSION = "stage20e.resource-geography.v1";
    /** Number of graph-neighbor smoothing passes used for latent physical conditions. */
    public static final int CORRELATION_PASSES = 2;
    /** Weight retained from the local system during each correlation pass. */
    public static final double LOCAL_CONDITION_WEIGHT = 0.60d;
    /** Minimum host-local deterministic variance multiplier. */
    public static final double LOCAL_VARIANCE_MIN = 0.80d;
    /** Maximum host-local deterministic variance multiplier. */
    public static final double LOCAL_VARIANCE_MAX = 1.20d;
    /** Richness margin above presence threshold before an initial extraction site is eligible. */
    public static final double INITIAL_SITE_SCORE_MARGIN = 0.08d;

    private static final Map<String, OccurrenceBand> OCCURRENCE_BANDS = createBands();

    private Stage20ResourceGenerationProfile() {
        throw new AssertionError("No instances");
    }

    /**
     * Returns immutable occurrence calibration keyed by authoritative Stage-18 occurrence type ID.
     *
     * @return deterministic immutable calibration map
     */
    public static Map<String, OccurrenceBand> occurrenceBands() {
        return OCCURRENCE_BANDS;
    }

    /**
     * Resolves one occurrence calibration row.
     *
     * @param occurrenceTypeId authoritative Stage-18 occurrence type ID
     * @return calibrated row
     * @throws IllegalArgumentException when no Stage-20E row exists
     */
    public static OccurrenceBand requireBand(String occurrenceTypeId) {
        OccurrenceBand band = OCCURRENCE_BANDS.get(occurrenceTypeId);
        if (band == null) {
            throw new IllegalArgumentException("No Stage-20E occurrence calibration for " + occurrenceTypeId);
        }
        return band;
    }

    /**
     * One provisional aggregate source calibration row.
     *
     * @param occurrenceTypeId Stage-18 occurrence type ID
     * @param presenceThreshold minimum correlated host score for a concrete occurrence
     * @param minGradeFraction minimum generated target grade
     * @param maxGradeFraction maximum generated target grade
     * @param minAccessibleMassKg minimum finite accessible gross source mass
     * @param maxAccessibleMassKg maximum finite accessible gross source mass
     * @param minSourceRecoveryFraction minimum source-side recoverability
     * @param maxSourceRecoveryFraction maximum source-side recoverability
     */
    public record OccurrenceBand(
            String occurrenceTypeId,
            double presenceThreshold,
            double minGradeFraction,
            double maxGradeFraction,
            double minAccessibleMassKg,
            double maxAccessibleMassKg,
            double minSourceRecoveryFraction,
            double maxSourceRecoveryFraction) {
        /**
         * Validates one immutable Stage-20E occurrence calibration row.
         *
         * @param occurrenceTypeId Stage-18 occurrence type ID
         * @param presenceThreshold minimum correlated host score for a concrete occurrence
         * @param minGradeFraction minimum generated target grade
         * @param maxGradeFraction maximum generated target grade
         * @param minAccessibleMassKg minimum finite accessible gross source mass
         * @param maxAccessibleMassKg maximum finite accessible gross source mass
         * @param minSourceRecoveryFraction minimum source-side recoverability
         * @param maxSourceRecoveryFraction maximum source-side recoverability
         */
        public OccurrenceBand {
            occurrenceTypeId = requireText(occurrenceTypeId, "occurrenceTypeId");
            requireFractionInclusive(presenceThreshold, "presenceThreshold");
            requirePositiveFraction(minGradeFraction, "minGradeFraction");
            requirePositiveFraction(maxGradeFraction, "maxGradeFraction");
            if (minGradeFraction > maxGradeFraction) {
                throw new IllegalArgumentException("grade minimum exceeds maximum for " + occurrenceTypeId);
            }
            requirePositiveFinite(minAccessibleMassKg, "minAccessibleMassKg");
            requirePositiveFinite(maxAccessibleMassKg, "maxAccessibleMassKg");
            if (minAccessibleMassKg > maxAccessibleMassKg) {
                throw new IllegalArgumentException("reserve minimum exceeds maximum for " + occurrenceTypeId);
            }
            requirePositiveFraction(minSourceRecoveryFraction, "minSourceRecoveryFraction");
            requirePositiveFraction(maxSourceRecoveryFraction, "maxSourceRecoveryFraction");
            if (minSourceRecoveryFraction > maxSourceRecoveryFraction) {
                throw new IllegalArgumentException("recovery minimum exceeds maximum for " + occurrenceTypeId);
            }
        }
    }

    private static Map<String, OccurrenceBand> createBands() {
        LinkedHashMap<String, OccurrenceBand> rows = new LinkedHashMap<>();
        // Aggregate accessible-mass ranges are intentionally broad and provisional. They describe
        // finite Stage-18 source state, not the un-authored physical radius of a Stage-20C anchor.
        add(rows, new OccurrenceBand("occurrence.water_ice", 0.43d, 0.18d, 0.75d, 2.0e8d, 3.0e11d, 0.70d, 0.97d));
        add(rows, new OccurrenceBand("occurrence.volatiles", 0.52d, 0.08d, 0.55d, 8.0e7d, 1.2e11d, 0.62d, 0.94d));
        add(rows, new OccurrenceBand("occurrence.carbonaceous", 0.47d, 0.12d, 0.70d, 1.5e8d, 2.2e11d, 0.68d, 0.96d));
        add(rows, new OccurrenceBand("occurrence.metallic", 0.42d, 0.14d, 0.68d, 2.5e8d, 4.0e11d, 0.72d, 0.98d));
        add(rows, new OccurrenceBand("occurrence.light_metals", 0.50d, 0.10d, 0.54d, 1.2e8d, 2.0e11d, 0.66d, 0.95d));
        add(rows, new OccurrenceBand("occurrence.conductors", 0.55d, 0.06d, 0.36d, 5.0e7d, 7.0e10d, 0.60d, 0.92d));
        add(rows, new OccurrenceBand("occurrence.strategic_metals", 0.68d, 0.015d, 0.18d, 8.0e6d, 1.2e10d, 0.48d, 0.86d));
        add(rows, new OccurrenceBand("occurrence.silicates", 0.38d, 0.22d, 0.88d, 5.0e8d, 7.0e11d, 0.78d, 0.99d));
        add(rows, new OccurrenceBand("occurrence.fissiles", 0.73d, 0.004d, 0.065d, 2.0e6d, 2.5e9d, 0.35d, 0.78d));
        return Collections.unmodifiableMap(new TreeMap<>(rows));
    }

    private static void add(Map<String, OccurrenceBand> rows, OccurrenceBand band) {
        Objects.requireNonNull(band, "band");
        if (rows.putIfAbsent(band.occurrenceTypeId(), band) != null) {
            throw new IllegalStateException("Duplicate Stage-20E occurrence band: " + band.occurrenceTypeId());
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static void requireFractionInclusive(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in [0,1]");
        }
    }

    private static void requirePositiveFraction(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be in (0,1]");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
