package com.spacesim.world.calibration;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.LocalPresentationFrame;
import com.spacesim.world.LocalPresentationFrame.PresentationPoint;
import com.spacesim.world.calibration.Stage20FarCoordinatePrecisionCalibrationProfile.MagnitudePrecisionSample;
import com.spacesim.world.calibration.Stage20FarCoordinatePrecisionCalibrationProfile.PrecisionPolicy;
import com.spacesim.world.calibration.Stage20FarCoordinatePrecisionCalibrationProfile.RebasePrecisionSample;

import java.util.ArrayList;
import java.util.List;

/** Derives Stage-20A.8 far-coordinate precision evidence from IEEE-754 behavior and the Stage-20 coordinate seam. */
public final class Stage20FarCoordinatePrecisionCalibrationCalculator {
    /** Accepted Stage-20 v1 local numerical-error budget: one centimeter. */
    public static final double ABSOLUTE_ERROR_BUDGET_M = 0.01d;

    private static final List<Double> GLOBAL_MAGNITUDE_PROBES_M = List.of(
            1_000_000d,
            1_000_000_000d,
            30_000_000_000d,
            1_000_000_000_000d,
            10_000_000_000_000d,
            100_000_000_000_000d,
            1_000_000_000_000_000d,
            1_000_000_000_000_000_000d);

    private Stage20FarCoordinatePrecisionCalibrationCalculator() {
        throw new AssertionError("No instances");
    }

    /**
     * Builds the deterministic Stage-20A.8 precision profile.
     *
     * <p>The one-centimeter budget is a versioned numerical engineering constraint, not world
     * geometry. It provides headroom for meter-scale collision, docking and formation geometry while
     * remaining far above the sub-micrometer worst normalized-offset ULP produced by the hierarchical
     * coordinate seam.</p>
     *
     * @return current immutable precision calibration profile
     */
    public static Stage20FarCoordinatePrecisionCalibrationProfile calibrate() {
        double offsetUlp = Math.ulp(LocalPhysicalPosition.HALF_CELL_SIZE_M);
        PrecisionPolicy policy = new PrecisionPolicy(
                LocalPhysicalPosition.CELL_SIZE_M,
                LocalPhysicalPosition.HALF_CELL_SIZE_M,
                ABSOLUTE_ERROR_BUDGET_M,
                offsetUlp,
                offsetUlp / 2d,
                true,
                false,
                true);

        List<MagnitudePrecisionSample> magnitudeSamples = new ArrayList<>();
        for (double magnitudeM : GLOBAL_MAGNITUDE_PROBES_M) {
            double doubleUlp = Math.ulp(magnitudeM);
            float floatMagnitude = (float) magnitudeM;
            if (!Float.isFinite(floatMagnitude)) {
                throw new IllegalStateException("Precision probe exceeds finite float domain: " + magnitudeM);
            }
            double floatUlp = Math.ulp(floatMagnitude);
            magnitudeSamples.add(new MagnitudePrecisionSample(
                    magnitudeM,
                    doubleUlp,
                    doubleUlp / 2d,
                    floatUlp,
                    floatUlp / 2d,
                    doubleUlp / 2d <= ABSOLUTE_ERROR_BUDGET_M,
                    floatUlp / 2d <= ABSOLUTE_ERROR_BUDGET_M));
        }

        List<RebasePrecisionSample> rebaseSamples = List.of(
                deriveRebaseProbe(
                        "far_positive_negative_cells",
                        new LocalPhysicalPosition(8_000_000_000L, -7_000_000_000L, 12_345.25d, -9_876.5d),
                        120d,
                        -75d),
                deriveRebaseProbe(
                        "cross_numerical_cell_boundary",
                        new LocalPhysicalPosition(
                                4_000_000_000L,
                                5_000_000_000L,
                                LocalPhysicalPosition.HALF_CELL_SIZE_M - 2d,
                                -LocalPhysicalPosition.HALF_CELL_SIZE_M + 3d),
                        5d,
                        -7d));

        return new Stage20FarCoordinatePrecisionCalibrationProfile(
                Stage20FarCoordinatePrecisionCalibrationProfile.CURRENT_VERSION,
                policy,
                magnitudeSamples,
                rebaseSamples,
                List.of(
                        "legacy_transform_component_vector2_float_is_not_stage20_far_coordinate_authority",
                        "legacy_flight_dynamics_integrates_float_transform_and_requires_stage20_physical_state_adapter_before_far_world_execution",
                        "stage20b_generated_entities_must_store_local_physical_position_or_deterministic_equivalent_not_global_float_coordinates",
                        "materialization_and_lod_distance_bands_remain_stage20a9_work"));
    }

    /**
     * Measures camera-relative pairwise error before and after changing only the presentation origin.
     *
     * @param probeId stable precision-probe identifier
     * @param source first authoritative physical coordinate
     * @param deltaXM physical X separation to the second coordinate in meters
     * @param deltaYM physical Y separation to the second coordinate in meters
     * @return deterministic rebasing precision sample
     */
    public static RebasePrecisionSample deriveRebaseProbe(
            String probeId,
            LocalPhysicalPosition source,
            double deltaXM,
            double deltaYM) {
        if (probeId == null || probeId.isBlank()) {
            throw new IllegalArgumentException("probeId must not be blank");
        }
        if (!Double.isFinite(deltaXM) || !Double.isFinite(deltaYM)) {
            throw new IllegalArgumentException("Physical probe delta must be finite");
        }
        LocalPhysicalPosition first = java.util.Objects.requireNonNull(source, "source");
        LocalPhysicalPosition second = first.translated(deltaXM, deltaYM);
        LocalPhysicalPosition firstSnapshot = first;
        LocalPhysicalPosition secondSnapshot = second;
        LocalPhysicalPosition.Displacement physical = first.displacementTo(second);
        double physicalDistance = Math.hypot(physical.deltaXM(), physical.deltaYM());

        LocalPresentationFrame initialFrame = new LocalPresentationFrame(first.translated(-500d, 250d));
        LocalPresentationFrame rebasedFrame = initialFrame.rebased(first.translated(1_000d, -800d));
        double initialError = pairwisePresentationError(initialFrame, first, second, physical);
        double rebasedError = pairwisePresentationError(rebasedFrame, first, second, physical);
        boolean stateChanged = !first.equals(firstSnapshot) || !second.equals(secondSnapshot);

        return new RebasePrecisionSample(
                probeId,
                first.cellX(),
                first.cellY(),
                physical.deltaXM(),
                physical.deltaYM(),
                physicalDistance,
                initialError,
                rebasedError,
                stateChanged);
    }

    private static double pairwisePresentationError(
            LocalPresentationFrame frame,
            LocalPhysicalPosition first,
            LocalPhysicalPosition second,
            LocalPhysicalPosition.Displacement physical) {
        PresentationPoint projectedFirst = frame.project(first);
        PresentationPoint projectedSecond = frame.project(second);
        double presentationDeltaX = (double) projectedSecond.xM() - projectedFirst.xM();
        double presentationDeltaY = (double) projectedSecond.yM() - projectedFirst.yM();
        return Math.hypot(
                presentationDeltaX - physical.deltaXM(),
                presentationDeltaY - physical.deltaYM());
    }
}
