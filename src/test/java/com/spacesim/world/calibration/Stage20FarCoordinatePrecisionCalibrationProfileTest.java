package com.spacesim.world.calibration;

import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.LocalPresentationFrame;
import com.spacesim.world.calibration.Stage20FarCoordinatePrecisionCalibrationProfile.MagnitudePrecisionSample;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FarCoordinatePrecisionCalibrationProfileTest {
    @Test
    void currentProfileIsDeterministicAndHierarchicalOffsetsMeetBudget() {
        Stage20FarCoordinatePrecisionCalibrationProfile first =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();
        Stage20FarCoordinatePrecisionCalibrationProfile second =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();

        assertEquals(first, second);
        assertEquals(Stage20FarCoordinatePrecisionCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(LocalPhysicalPosition.CELL_SIZE_M, first.policy().cellSizeM());
        assertEquals(LocalPhysicalPosition.HALF_CELL_SIZE_M, first.policy().maximumOffsetMagnitudeM());
        assertTrue(first.policy().hierarchicalPhysicalCoordinatesRequired());
        assertFalse(first.policy().legacyGlobalFloatPhysicalAuthorityAllowed());
        assertTrue(first.policy().cameraRelativeFloatPresentationAllowed());
        assertTrue(first.policy().hierarchicalRepresentationWithinBudget());
        assertTrue(first.policy().maximumHierarchicalHalfUlpErrorM()
                < Stage20FarCoordinatePrecisionCalibrationCalculator.ABSOLUTE_ERROR_BUDGET_M);
    }

    @Test
    void globalFloatAndEventuallyGlobalDoubleLoseCentimeterLocalPrecision() {
        Stage20FarCoordinatePrecisionCalibrationProfile profile =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();

        MagnitudePrecisionSample oneBillion = sample(profile, 1_000_000_000d);
        MagnitudePrecisionSample oneQuadrillion = sample(profile, 1_000_000_000_000_000d);

        assertFalse(oneBillion.legacyFloatWithinBudget());
        assertTrue(oneBillion.naiveDoubleWithinBudget());
        assertFalse(oneQuadrillion.legacyFloatWithinBudget());
        assertFalse(oneQuadrillion.naiveDoubleWithinBudget());
        assertTrue(oneQuadrillion.naiveDoubleHalfUlpErrorM()
                > Stage20FarCoordinatePrecisionCalibrationCalculator.ABSOLUTE_ERROR_BUDGET_M);
    }

    @Test
    void localTranslationAcrossNumericalCellBoundaryPreservesPhysicalDistance() {
        LocalPhysicalPosition start = new LocalPhysicalPosition(
                4_000_000_000L,
                -5_000_000_000L,
                LocalPhysicalPosition.HALF_CELL_SIZE_M - 2d,
                -LocalPhysicalPosition.HALF_CELL_SIZE_M + 3d);
        LocalPhysicalPosition moved = start.translated(5d, -7d);
        LocalPhysicalPosition.Displacement delta = start.displacementTo(moved);

        assertEquals(4_000_000_001L, moved.cellX());
        assertEquals(-5_000_000_001L, moved.cellY());
        assertEquals(5d, delta.deltaXM(), 1e-9d);
        assertEquals(-7d, delta.deltaYM(), 1e-9d);
        assertEquals(Math.hypot(5d, 7d), start.distanceTo(moved), 1e-9d);
    }

    @Test
    void cameraRebasingDoesNotMutateFarAuthoritativeCoordinates() {
        LocalPhysicalPosition first = new LocalPhysicalPosition(
                8_000_000_000L,
                -7_000_000_000L,
                12_345.25d,
                -9_876.5d);
        LocalPhysicalPosition second = first.translated(120d, -75d);
        LocalPhysicalPosition firstSnapshot = first;
        LocalPhysicalPosition secondSnapshot = second;

        LocalPresentationFrame initial = new LocalPresentationFrame(first.translated(-500d, 250d));
        LocalPresentationFrame rebased = initial.rebased(first.translated(1_000d, -800d));
        LocalPresentationFrame.PresentationPoint firstInitial = initial.project(first);
        LocalPresentationFrame.PresentationPoint secondInitial = initial.project(second);
        LocalPresentationFrame.PresentationPoint firstRebased = rebased.project(first);
        LocalPresentationFrame.PresentationPoint secondRebased = rebased.project(second);

        assertEquals(120d, (double) secondInitial.xM() - firstInitial.xM(), 0.001d);
        assertEquals(-75d, (double) secondInitial.yM() - firstInitial.yM(), 0.001d);
        assertEquals(120d, (double) secondRebased.xM() - firstRebased.xM(), 0.001d);
        assertEquals(-75d, (double) secondRebased.yM() - firstRebased.yM(), 0.001d);
        assertEquals(firstSnapshot, first);
        assertEquals(secondSnapshot, second);
    }

    @Test
    void calibratedRebaseSamplesStayInsideAcceptedBudgetWithoutStateMutation() {
        Stage20FarCoordinatePrecisionCalibrationProfile profile =
                Stage20FarCoordinatePrecisionCalibrationCalculator.calibrate();

        assertEquals(2, profile.rebaseSamples().size());
        assertTrue(profile.rebaseSamples().stream()
                .allMatch(value -> value.withinBudget(profile.policy().absoluteErrorBudgetM())));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("legacy_transform_component_vector2_float")));
    }

    private static MagnitudePrecisionSample sample(
            Stage20FarCoordinatePrecisionCalibrationProfile profile,
            double magnitudeM) {
        return profile.magnitudeSamples().stream()
                .filter(value -> Double.compare(value.globalMagnitudeM(), magnitudeM) == 0)
                .findFirst()
                .orElseThrow();
    }
}
