package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20PdSafeInterceptReferenceCatalog.DebrisRiskSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Machine-readable Stage-20A PD safe-intercept geometry closure.
 *
 * <p>The selected distance is the first authored v0.7 stand-off that satisfies the separate
 * provisional Stage-20 projected-hit-fraction policy under the narrowest (therefore conservative for
 * the centered target) tested debris dispersion. The result is a scheduler policy input, not a claim
 * that debris energy or hit probability becomes zero.</p>
 *
 * @param version stable closure profile version
 * @param authority calibration authority
 * @param stage22ReviewRequired whether playable/content review remains required
 * @param sourceBenchmark exact v0.7 evidence source
 * @param sourceBenchmarkStatus source benchmark status retained without promotion
 * @param policyEvidence exact provenance of the Stage-20 risk policy
 * @param maxProjectedHitFraction provisional maximum projected target intersection fraction
 * @param conservativeLateralSigmaMps narrowest tested debris-dispersion sigma
 * @param selectedMinimumInterceptDistanceM first authored passing stand-off
 * @param selectedProjectedHitFraction projected hit fraction at the selected conservative row
 * @param selectedIntersectingEnergyJ projected intersecting kinetic energy at the selected row
 * @param closestRejectedStandOffM nearest authored row below the selected distance
 * @param closestRejectedProjectedHitFraction hit fraction of that rejected conservative row
 * @param evidenceSampleCount number of copied benchmark sensitivity rows
 * @param residualRiskZero whether the policy claims zero residual debris risk
 * @param physicalLaw whether the acceptance threshold is represented as a physical law
 * @param schedulerInputReady whether the derived distance is ready to feed the existing PD scheduler policy input
 */
public record Stage20PdSafeInterceptCalibrationProfile(
        String version,
        CalibrationAuthority authority,
        boolean stage22ReviewRequired,
        String sourceBenchmark,
        String sourceBenchmarkStatus,
        String policyEvidence,
        double maxProjectedHitFraction,
        double conservativeLateralSigmaMps,
        double selectedMinimumInterceptDistanceM,
        double selectedProjectedHitFraction,
        double selectedIntersectingEnergyJ,
        double closestRejectedStandOffM,
        double closestRejectedProjectedHitFraction,
        int evidenceSampleCount,
        boolean residualRiskZero,
        boolean physicalLaw,
        boolean schedulerInputReady) {
    /** Current Stage-20A PD safe-intercept closure version. */
    public static final String CURRENT_VERSION = "stage20a.pd-safe-intercept.v1";

    /**
     * Creates one validated immutable closure profile.
     *
     * @param version stable profile version
     * @param authority calibration authority
     * @param stage22ReviewRequired whether Stage-22 review is required
     * @param sourceBenchmark exact benchmark source
     * @param sourceBenchmarkStatus retained source authority/status
     * @param policyEvidence exact policy provenance
     * @param maxProjectedHitFraction provisional risk threshold
     * @param conservativeLateralSigmaMps conservative tested dispersion
     * @param selectedMinimumInterceptDistanceM selected minimum stand-off
     * @param selectedProjectedHitFraction projected hit fraction at selected row
     * @param selectedIntersectingEnergyJ projected intersecting energy at selected row
     * @param closestRejectedStandOffM nearest rejected smaller stand-off
     * @param closestRejectedProjectedHitFraction projected hit fraction at rejected row
     * @param evidenceSampleCount copied evidence-row count
     * @param residualRiskZero whether the policy claims zero residual risk
     * @param physicalLaw whether the policy threshold is a physical law
     * @param schedulerInputReady whether the derived distance is ready for scheduler input
     */
    public Stage20PdSafeInterceptCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(sourceBenchmark, "sourceBenchmark");
        requireText(sourceBenchmarkStatus, "sourceBenchmarkStatus");
        requireText(policyEvidence, "policyEvidence");
        requireFraction(maxProjectedHitFraction, "maxProjectedHitFraction");
        requirePositiveFinite(conservativeLateralSigmaMps, "conservativeLateralSigmaMps");
        requirePositiveFinite(selectedMinimumInterceptDistanceM, "selectedMinimumInterceptDistanceM");
        requireFraction(selectedProjectedHitFraction, "selectedProjectedHitFraction");
        requirePositiveFinite(selectedIntersectingEnergyJ, "selectedIntersectingEnergyJ");
        requirePositiveFinite(closestRejectedStandOffM, "closestRejectedStandOffM");
        requireFraction(closestRejectedProjectedHitFraction, "closestRejectedProjectedHitFraction");
        if (evidenceSampleCount <= 0) {
            throw new IllegalArgumentException("evidenceSampleCount must be positive");
        }
        if (selectedProjectedHitFraction > maxProjectedHitFraction) {
            throw new IllegalArgumentException("selected row must satisfy projected-hit policy");
        }
        if (closestRejectedProjectedHitFraction <= maxProjectedHitFraction
                || closestRejectedStandOffM >= selectedMinimumInterceptDistanceM) {
            throw new IllegalArgumentException("closest rejected row must be smaller and fail the policy");
        }
        if (residualRiskZero || physicalLaw || !schedulerInputReady) {
            throw new IllegalArgumentException(
                    "PD closure must retain non-zero residual risk, policy semantics and scheduler readiness");
        }
    }

    /**
     * Derives the current policy result from the packaged v0.7 evidence matrix.
     *
     * @return deterministic current PD safe-intercept closure
     */
    public static Stage20PdSafeInterceptCalibrationProfile deriveCurrent() {
        Stage20PdSafeInterceptReferenceCatalog catalog =
                Stage20PdSafeInterceptReferenceCatalogLoader.loadDefault();
        double conservativeSigma = catalog.samples().stream()
                .mapToDouble(DebrisRiskSample::lateralSigmaMps)
                .min()
                .orElseThrow();
        List<DebrisRiskSample> conservativeRows = catalog.samples().stream()
                .filter(value -> Double.compare(value.lateralSigmaMps(), conservativeSigma) == 0)
                .sorted(Comparator.comparingDouble(DebrisRiskSample::standOffM))
                .toList();
        DebrisRiskSample selected = conservativeRows.stream()
                .filter(value -> value.shipHitFraction() <= catalog.maxProjectedHitFraction())
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "No authored v0.7 stand-off satisfies current Stage-20 PD risk policy"));
        DebrisRiskSample closestRejected = conservativeRows.stream()
                .filter(value -> value.standOffM() < selected.standOffM())
                .max(Comparator.comparingDouble(DebrisRiskSample::standOffM))
                .orElseThrow(() -> new IllegalStateException(
                        "PD policy requires an authored rejected row below the selected minimum"));

        return new Stage20PdSafeInterceptCalibrationProfile(
                CURRENT_VERSION,
                catalog.status(),
                catalog.stage22ReviewRequired(),
                catalog.sourceBenchmark(),
                catalog.sourceBenchmarkStatus(),
                catalog.policyEvidence(),
                catalog.maxProjectedHitFraction(),
                conservativeSigma,
                selected.standOffM(),
                selected.shipHitFraction(),
                selected.intersectingEnergyJ(),
                closestRejected.standOffM(),
                closestRejected.shipHitFraction(),
                catalog.samples().size(),
                false,
                false,
                true);
    }

    /**
     * Returns whether this profile closes the Stage-20B entry requirement without promoting the
     * benchmark or claiming zero residual risk.
     *
     * @return true when the current version has a conservative passing row and explicit policy semantics
     */
    public boolean closesStage20BEntryCoverage() {
        return CURRENT_VERSION.equals(version)
                && authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                && stage22ReviewRequired
                && "authoring-benchmark-only".equals(sourceBenchmarkStatus)
                && sourceBenchmark.endsWith("protection_debris_reference_v0_7.json")
                && Double.compare(maxProjectedHitFraction, 0.02d) == 0
                && Double.compare(conservativeLateralSigmaMps, 50d) == 0
                && selectedProjectedHitFraction <= maxProjectedHitFraction
                && closestRejectedProjectedHitFraction > maxProjectedHitFraction
                && closestRejectedStandOffM < selectedMinimumInterceptDistanceM
                && selectedIntersectingEnergyJ > 0d
                && evidenceSampleCount == 12
                && !residualRiskZero
                && !physicalLaw
                && schedulerInputReady;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireFraction(double value, String field) {
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException(field + " must be finite and within [0,1]");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
