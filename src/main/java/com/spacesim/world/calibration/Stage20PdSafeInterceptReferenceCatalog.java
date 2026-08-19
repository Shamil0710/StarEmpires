package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned Stage-20A PD debris-risk evidence and provisional acceptance policy.
 *
 * <p>Physical sweep rows are copied from the v0.7 authoring benchmark with exact provenance. The
 * maximum projected hit fraction is a separate Stage-20 design policy, not a physical law and not a
 * claim of zero residual risk.</p>
 *
 * @param schemaVersion packaged evidence schema version
 * @param version stable Stage-20 calibration version
 * @param status calibration authority
 * @param stage22ReviewRequired whether playable/content review remains required
 * @param sourceBenchmark exact benchmark path
 * @param sourceBenchmarkStatus source benchmark authority/status label
 * @param sourceThreat source benchmark threat ID
 * @param sourceThreatKineticEnergyJ source intact-threat kinetic energy
 * @param projectedTarget projected benchmark target ID
 * @param policyEvidence exact provenance of the provisional risk policy
 * @param maxProjectedHitFraction maximum accepted projected hit fraction under the conservative tested dispersion
 * @param samples immutable benchmark sensitivity rows
 */
public record Stage20PdSafeInterceptReferenceCatalog(
        int schemaVersion,
        String version,
        CalibrationAuthority status,
        boolean stage22ReviewRequired,
        String sourceBenchmark,
        String sourceBenchmarkStatus,
        String sourceThreat,
        double sourceThreatKineticEnergyJ,
        String projectedTarget,
        String policyEvidence,
        double maxProjectedHitFraction,
        List<DebrisRiskSample> samples) {

    /**
     * One copied v0.7 debris-field sensitivity row.
     *
     * @param lateralSigmaMps lateral fragment/debris dispersion sigma
     * @param standOffM intercept stand-off from protected target geometry
     * @param shipHitFraction projected target intersection fraction
     * @param intersectingEnergyJ projected kinetic energy intersecting the reference target
     */
    public record DebrisRiskSample(
            double lateralSigmaMps,
            double standOffM,
            double shipHitFraction,
            double intersectingEnergyJ) {
        /** Validates one copied benchmark row. */
        public DebrisRiskSample {
            requirePositiveFinite(lateralSigmaMps, "lateralSigmaMps");
            requirePositiveFinite(standOffM, "standOffM");
            requireNonNegativeFinite(shipHitFraction, "shipHitFraction");
            requireNonNegativeFinite(intersectingEnergyJ, "intersectingEnergyJ");
            if (shipHitFraction > 1d) {
                throw new IllegalArgumentException("shipHitFraction cannot exceed one");
            }
        }
    }

    /** Creates one immutable deterministic evidence catalog. */
    public Stage20PdSafeInterceptReferenceCatalog {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("schemaVersion must be positive");
        }
        requireText(version, "version");
        Objects.requireNonNull(status, "status");
        requireText(sourceBenchmark, "sourceBenchmark");
        requireText(sourceBenchmarkStatus, "sourceBenchmarkStatus");
        requireText(sourceThreat, "sourceThreat");
        requirePositiveFinite(sourceThreatKineticEnergyJ, "sourceThreatKineticEnergyJ");
        requireText(projectedTarget, "projectedTarget");
        requireText(policyEvidence, "policyEvidence");
        requirePositiveFinite(maxProjectedHitFraction, "maxProjectedHitFraction");
        if (maxProjectedHitFraction > 1d) {
            throw new IllegalArgumentException("maxProjectedHitFraction cannot exceed one");
        }
        Objects.requireNonNull(samples, "samples");
        ArrayList<DebrisRiskSample> copy = new ArrayList<>(samples);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparingDouble(DebrisRiskSample::lateralSigmaMps)
                .thenComparingDouble(DebrisRiskSample::standOffM));
        samples = List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
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
