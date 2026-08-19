package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned Stage-20A.8 numerical-precision evidence for unbounded local-system coordinates.
 *
 * <p>The profile distinguishes authoritative hierarchical physical coordinates from legacy global
 * float transforms and camera-relative float presentation. Probe magnitudes are numerical stress
 * coordinates only; they are not generated-system extents or gameplay boundaries.</p>
 *
 * @param version stable calibration-profile version
 * @param policy accepted numerical representation/error policy
 * @param magnitudeSamples deterministic global-coordinate precision probes
 * @param rebaseSamples deterministic camera-relative rebasing probes
 * @param unresolvedConstraints legacy/runtime migrations still required before Stage-20 physical geometry consumes them
 */
public record Stage20FarCoordinatePrecisionCalibrationProfile(
        String version,
        PrecisionPolicy policy,
        List<MagnitudePrecisionSample> magnitudeSamples,
        List<RebasePrecisionSample> rebaseSamples,
        List<String> unresolvedConstraints) {
    /** Current Stage-20A.8 far-coordinate precision profile version. */
    public static final String CURRENT_VERSION = "stage20a.far-coordinate-precision.v1";

    /**
     * Creates an immutable deterministically ordered precision profile.
     *
     * @param version stable calibration-profile version
     * @param policy accepted numerical representation/error policy
     * @param magnitudeSamples global-coordinate precision probes
     * @param rebaseSamples camera-relative rebasing probes
     * @param unresolvedConstraints remaining migration/closure gaps
     */
    public Stage20FarCoordinatePrecisionCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(policy, "policy");
        magnitudeSamples = sortedCopy(
                magnitudeSamples,
                Comparator.comparingDouble(MagnitudePrecisionSample::globalMagnitudeM),
                "magnitudeSamples");
        rebaseSamples = sortedCopy(
                rebaseSamples,
                Comparator.comparing(RebasePrecisionSample::probeId),
                "rebaseSamples");
        unresolvedConstraints = sortedStrings(unresolvedConstraints, "unresolvedConstraints");
    }

    /**
     * Accepted numerical representation policy for Stage-20 local physical space.
     *
     * @param cellSizeM exact hierarchical numerical-cell width in meters
     * @param maximumOffsetMagnitudeM normalized local-offset magnitude bound in meters
     * @param absoluteErrorBudgetM maximum accepted local numerical quantization/rebase error in meters
     * @param maximumHierarchicalOffsetUlpM worst ULP of a normalized authoritative double offset
     * @param maximumHierarchicalHalfUlpErrorM worst half-ULP quantization error of the authoritative local offset
     * @param hierarchicalPhysicalCoordinatesRequired whether Stage-20 far coordinates require the hierarchical seam
     * @param legacyGlobalFloatPhysicalAuthorityAllowed whether global float transforms may own Stage-20 far-coordinate physics
     * @param cameraRelativeFloatPresentationAllowed whether nearby presentation may cast relative meters to float
     */
    public record PrecisionPolicy(
            double cellSizeM,
            double maximumOffsetMagnitudeM,
            double absoluteErrorBudgetM,
            double maximumHierarchicalOffsetUlpM,
            double maximumHierarchicalHalfUlpErrorM,
            boolean hierarchicalPhysicalCoordinatesRequired,
            boolean legacyGlobalFloatPhysicalAuthorityAllowed,
            boolean cameraRelativeFloatPresentationAllowed) {
        /**
         * Validates one numerical precision policy.
         *
         * @param cellSizeM exact hierarchical numerical-cell width in meters
         * @param maximumOffsetMagnitudeM normalized local-offset magnitude bound in meters
         * @param absoluteErrorBudgetM maximum accepted local numerical error in meters
         * @param maximumHierarchicalOffsetUlpM worst normalized authoritative-offset ULP
         * @param maximumHierarchicalHalfUlpErrorM worst normalized authoritative-offset half-ULP error
         * @param hierarchicalPhysicalCoordinatesRequired whether hierarchical coordinates are required
         * @param legacyGlobalFloatPhysicalAuthorityAllowed whether global float physical authority is allowed
         * @param cameraRelativeFloatPresentationAllowed whether nearby float presentation is allowed
         */
        public PrecisionPolicy {
            requirePositiveFinite(cellSizeM, "cellSizeM");
            requirePositiveFinite(maximumOffsetMagnitudeM, "maximumOffsetMagnitudeM");
            requirePositiveFinite(absoluteErrorBudgetM, "absoluteErrorBudgetM");
            requirePositiveFinite(maximumHierarchicalOffsetUlpM, "maximumHierarchicalOffsetUlpM");
            requirePositiveFinite(maximumHierarchicalHalfUlpErrorM, "maximumHierarchicalHalfUlpErrorM");
            if (maximumOffsetMagnitudeM * 2d != cellSizeM) {
                throw new IllegalArgumentException("maximumOffsetMagnitudeM must be half of cellSizeM");
            }
            if (maximumHierarchicalHalfUlpErrorM > maximumHierarchicalOffsetUlpM) {
                throw new IllegalArgumentException("half-ULP error cannot exceed full ULP");
            }
        }

        /**
         * Reports whether the authoritative normalized-offset representation meets the accepted budget.
         *
         * @return true when worst half-ULP error is inside the absolute error budget
         */
        public boolean hierarchicalRepresentationWithinBudget() {
            return maximumHierarchicalHalfUlpErrorM <= absoluteErrorBudgetM;
        }
    }

    /**
     * Precision loss of naive single-value global coordinates at one numerical stress magnitude.
     *
     * @param globalMagnitudeM absolute numerical probe magnitude in meters
     * @param naiveDoubleUlpM one ULP of a global double at that magnitude
     * @param naiveDoubleHalfUlpErrorM half-ULP double quantization bound
     * @param legacyFloatUlpM one ULP of a global float at that magnitude
     * @param legacyFloatHalfUlpErrorM half-ULP float quantization bound
     * @param naiveDoubleWithinBudget whether naive global double stays within the accepted error budget
     * @param legacyFloatWithinBudget whether naive global float stays within the accepted error budget
     */
    public record MagnitudePrecisionSample(
            double globalMagnitudeM,
            double naiveDoubleUlpM,
            double naiveDoubleHalfUlpErrorM,
            double legacyFloatUlpM,
            double legacyFloatHalfUlpErrorM,
            boolean naiveDoubleWithinBudget,
            boolean legacyFloatWithinBudget) {
        /**
         * Validates one global-coordinate precision probe.
         *
         * @param globalMagnitudeM absolute numerical probe magnitude in meters
         * @param naiveDoubleUlpM one ULP of a global double at that magnitude
         * @param naiveDoubleHalfUlpErrorM half-ULP double quantization bound
         * @param legacyFloatUlpM one ULP of a global float at that magnitude
         * @param legacyFloatHalfUlpErrorM half-ULP float quantization bound
         * @param naiveDoubleWithinBudget whether global double is inside budget
         * @param legacyFloatWithinBudget whether global float is inside budget
         */
        public MagnitudePrecisionSample {
            requireNonNegativeFinite(globalMagnitudeM, "globalMagnitudeM");
            requirePositiveFinite(naiveDoubleUlpM, "naiveDoubleUlpM");
            requirePositiveFinite(naiveDoubleHalfUlpErrorM, "naiveDoubleHalfUlpErrorM");
            requirePositiveFinite(legacyFloatUlpM, "legacyFloatUlpM");
            requirePositiveFinite(legacyFloatHalfUlpErrorM, "legacyFloatHalfUlpErrorM");
        }
    }

    /**
     * Camera-relative rebasing evidence at a very large hierarchical physical coordinate.
     *
     * @param probeId stable precision-probe identifier
     * @param sourceCellX numerical source cell X
     * @param sourceCellY numerical source cell Y
     * @param physicalDeltaXM physical pairwise X separation in meters
     * @param physicalDeltaYM physical pairwise Y separation in meters
     * @param physicalDistanceM physical pairwise Euclidean distance in meters
     * @param firstFramePairwiseErrorM presentation pairwise error under the first nearby origin
     * @param rebasedFramePairwiseErrorM presentation pairwise error after presentation-origin rebasing
     * @param authoritativeStateChanged whether presentation rebasing changed either physical coordinate
     */
    public record RebasePrecisionSample(
            String probeId,
            long sourceCellX,
            long sourceCellY,
            double physicalDeltaXM,
            double physicalDeltaYM,
            double physicalDistanceM,
            double firstFramePairwiseErrorM,
            double rebasedFramePairwiseErrorM,
            boolean authoritativeStateChanged) {
        /**
         * Validates one presentation-rebase precision sample.
         *
         * @param probeId stable precision-probe identifier
         * @param sourceCellX numerical source cell X
         * @param sourceCellY numerical source cell Y
         * @param physicalDeltaXM physical pairwise X separation in meters
         * @param physicalDeltaYM physical pairwise Y separation in meters
         * @param physicalDistanceM physical pairwise Euclidean distance in meters
         * @param firstFramePairwiseErrorM pairwise presentation error before rebasing
         * @param rebasedFramePairwiseErrorM pairwise presentation error after rebasing
         * @param authoritativeStateChanged whether rebasing mutated physical state
         */
        public RebasePrecisionSample {
            requireText(probeId, "probeId");
            requireFinite(physicalDeltaXM, "physicalDeltaXM");
            requireFinite(physicalDeltaYM, "physicalDeltaYM");
            requireNonNegativeFinite(physicalDistanceM, "physicalDistanceM");
            requireNonNegativeFinite(firstFramePairwiseErrorM, "firstFramePairwiseErrorM");
            requireNonNegativeFinite(rebasedFramePairwiseErrorM, "rebasedFramePairwiseErrorM");
        }

        /**
         * Checks both presentation frames against an explicit numerical error budget.
         *
         * @param absoluteErrorBudgetM accepted absolute pairwise error in meters
         * @return true when both frames are within budget and physical state did not change
         */
        public boolean withinBudget(double absoluteErrorBudgetM) {
            requirePositiveFinite(absoluteErrorBudgetM, "absoluteErrorBudgetM");
            return !authoritativeStateChanged
                    && firstFramePairwiseErrorM <= absoluteErrorBudgetM
                    && rebasedFramePairwiseErrorM <= absoluteErrorBudgetM;
        }
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static List<String> sortedStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, field + " entry"));
        }
        copy.sort(String::compareTo);
        return List.copyOf(copy);
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

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
