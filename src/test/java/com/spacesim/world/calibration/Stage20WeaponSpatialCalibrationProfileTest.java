package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.BeamSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.DefenseSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.GuidedSample;
import com.spacesim.world.calibration.Stage20WeaponSpatialCalibrationProfile.KineticSample;
import org.junit.jupiter.api.Test;

import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20WeaponSpatialCalibrationProfileTest {
    @Test
    void calibrationIsDeterministicAndUsesExpectedProductionProbeFamilies() {
        Stage20WeaponSpatialCalibrationProfile first = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        Stage20WeaponSpatialCalibrationProfile second = Stage20WeaponSpatialCalibrationCalculator.calibrate();

        assertEquals(first, second);
        assertEquals(Stage20WeaponSpatialCalibrationProfile.CURRENT_VERSION, first.version());
        assertEquals(6, first.kineticSamples().size());
        assertEquals(4, first.beamSamples().size());
        assertEquals(6, first.guidedSamples().size());
        assertEquals(6, first.defenseSamples().size());
        assertFalse(first.unresolvedConstraints().isEmpty());
        assertTrue(first.kineticSamples().stream().allMatch(value -> value.source().contains("WeaponFireControl")));
        assertTrue(first.beamSamples().stream().allMatch(value -> value.source().contains("BeamWeaponRuntime")));
        assertTrue(first.guidedSamples().stream().allMatch(value -> value.source().contains("GuidanceRuntime")));
        assertTrue(first.defenseSamples().stream().allMatch(value -> value.source().contains("LayeredDefenseScheduler")));
    }

    @Test
    void kineticTimeOfFlightAndMotionEnvelopeGrowWithGeometry() {
        Stage20WeaponSpatialCalibrationProfile profile = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        List<KineticSample> stationary = profile.kineticSamples().stream()
                .filter(value -> value.targetLateralVelocityMps() == 0d)
                .sorted(Comparator.comparingDouble(KineticSample::rangeM))
                .toList();
        List<KineticSample> maneuvering = profile.kineticSamples().stream()
                .filter(value -> value.maneuverAccelerationMps2() > 0d)
                .sorted(Comparator.comparingDouble(KineticSample::rangeM))
                .toList();

        assertEquals(3, stationary.size());
        assertTrue(stationary.stream().allMatch(KineticSample::allowed));
        assertTrue(stationary.get(0).timeOfFlightSeconds() < stationary.get(1).timeOfFlightSeconds());
        assertTrue(stationary.get(1).timeOfFlightSeconds() < stationary.get(2).timeOfFlightSeconds());
        assertEquals(
                stationary.get(0).projectileKineticEnergyJ(),
                stationary.get(2).projectileKineticEnergyJ());

        assertEquals(3, maneuvering.size());
        assertTrue(maneuvering.stream().allMatch(KineticSample::allowed));
        assertTrue(maneuvering.get(0).maneuverEnvelopeRadiusM() < maneuvering.get(1).maneuverEnvelopeRadiusM());
        assertTrue(maneuvering.get(1).maneuverEnvelopeRadiusM() < maneuvering.get(2).maneuverEnvelopeRadiusM());
    }

    @Test
    void beamGeometryDegradesContinuouslyWithoutInventedHardRangeWall() {
        Stage20WeaponSpatialCalibrationProfile profile = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        List<BeamSample> samples = profile.beamSamples().stream()
                .sorted(Comparator.comparingDouble(BeamSample::rangeM))
                .toList();

        assertTrue(samples.stream().allMatch(BeamSample::allowed));
        for (int index = 1; index < samples.size(); index++) {
            assertTrue(samples.get(index - 1).effectiveSpotRadiusM() < samples.get(index).effectiveSpotRadiusM());
            assertTrue(samples.get(index - 1).meanIrradianceWPerM2() > samples.get(index).meanIrradianceWPerM2());
            assertEquals(samples.get(0).deliveredBeamEnergyJ(), samples.get(index).deliveredBeamEnergyJ());
        }
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("beam_runtime_has_no_hard_range_wall")));
    }

    @Test
    void guidedProbeConsumesPhysicalPropellantAndProtectsTerminalReserve() {
        Stage20WeaponSpatialCalibrationProfile profile = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        List<GuidedSample> samples = profile.guidedSamples();

        assertTrue(samples.stream().allMatch(GuidedSample::guidanceAllowed));
        assertTrue(samples.stream().allMatch(value -> value.commandedBurnSeconds() > 0d));
        assertTrue(samples.stream().allMatch(value -> value.propellantConsumedKg() > 0d));
        assertTrue(samples.stream().allMatch(value -> value.initialRemainingDeltaVMps() > value.terminalReserveMps()));
        assertTrue(samples.stream().allMatch(value -> value.predictedInterceptSeconds() > 0d));
    }

    @Test
    void layeredDefenseAssignmentsRespectConfiguredSafeInterceptGeometry() {
        Stage20WeaponSpatialCalibrationProfile profile = Stage20WeaponSpatialCalibrationCalculator.calibrate();
        List<DefenseSample> assigned = profile.defenseSamples().stream()
                .filter(DefenseSample::assigned)
                .toList();

        assertFalse(assigned.isEmpty());
        assertTrue(assigned.stream().allMatch(value -> value.plannedInterceptSeconds() <= value.predictedImpactSeconds()));
        assertTrue(assigned.stream().allMatch(value ->
                value.interceptDistanceFromProtectedCenterM() + 1e-9d
                        >= Math.max(1_500d, value.safeMinimumInterceptDistanceM())));
        assertFalse(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("safe_intercept_distance_is_scheduler_input")));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("safe_intercept_distance_superseded_by="
                        + Stage20PdSafeInterceptCalibrationProfile.CURRENT_VERSION)));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("scheduler_input_m=100000.0")));
        assertTrue(profile.unresolvedConstraints().stream()
                .anyMatch(value -> value.contains("residual_risk_zero=false")));
    }
}
