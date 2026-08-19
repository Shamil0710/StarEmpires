package com.spacesim.ship;

import com.spacesim.ship.BeamWeaponRuntime.Failure;
import com.spacesim.ship.GuidanceRuntime.TrackSource;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamAndGuidanceRuntimeTest {
    @Test
    void beamSpotAndIrradianceChangeContinuouslyWithRangeWithoutHardWall() {
        BeamWeapon weapon = new BeamWeapon(
                "weapon.laser_test_v1",
                1.06e-6d,
                4d,
                2e-6d,
                500_000_000d,
                700_000_000d,
                220_000_000d,
                3d);
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();
        TrackState near = track(2_000_000d, 0d, InformationState.FIRE_CONTROL, 25d);
        TrackState far = track(8_000_000d, 0d, InformationState.FIRE_CONTROL, 25d);

        BeamWeaponRuntime.BeamSolution nearSolution = runtime.plan(weapon, near, 0d, 0d, 1.5d);
        BeamWeaponRuntime.BeamSolution farSolution = runtime.plan(weapon, far, 0d, 0d, 1.5d);

        assertTrue(nearSolution.allowed());
        assertTrue(farSolution.allowed());
        assertTrue(farSolution.effectiveSpotRadiusM() > nearSolution.effectiveSpotRadiusM());
        assertTrue(farSolution.meanIrradianceWPerM2() < nearSolution.meanIrradianceWPerM2());
        assertEquals(750_000_000d, nearSolution.deliveredBeamEnergyJ(), 1e-3d);
        assertEquals(1_050_000_000d, nearSolution.electricalEnergyDemandJ(), 1e-3d);
        assertEquals(330_000_000d, nearSolution.wasteHeatJ(), 1e-3d);
    }

    @Test
    void beamRequiresTrackedCartesianStateAndRespectsPhysicalDwellDuty() {
        BeamWeapon weapon = new BeamWeapon(
                "weapon.laser_test_v1", 1e-6d, 2d, 1e-6d,
                100_000_000d, 150_000_000d, 50_000_000d, 2d);
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();

        assertEquals(
                Failure.FIRE_CONTROL_INSUFFICIENT,
                runtime.plan(weapon, track(10_000d, 0d, InformationState.CLASSIFIED, 1d), 0d, 0d, 1d).failure());
        assertTrue(
                runtime.plan(weapon, track(10_000d, 0d, InformationState.TRACKED, 1d), 0d, 0d, 1d).allowed());
        assertEquals(
                Failure.DWELL_LIMIT_EXCEEDED,
                runtime.plan(weapon, track(10_000d, 0d, InformationState.TRACKED, 1d), 0d, 0d, 3d).failure());
    }

    @Test
    void guidanceUsesPhysicalTrackAndPropellantRatherThanHitChance() {
        GuidedWeaponBody body = body();
        TrackState target = track(100_000d, 20_000d, InformationState.TRACKED, 10d);
        GuidanceRuntime runtime = new GuidanceRuntime();
        GuidanceRuntime.GuidanceCommand command = runtime.planLeadPursuit(
                body,
                target,
                new TargetMotionEstimate(-100d, 50d, 5d, 0.5d),
                TrackSource.ONBOARD_SEEKER,
                5d);

        assertTrue(command.allowed());
        assertTrue(command.predictedInterceptSeconds() > 0d);
        GuidedWeaponBody burned = runtime.execute(body, command);
        assertTrue(burned.remainingPropellantKg() < body.remainingPropellantKg());
        assertTrue(burned.speedMps() > body.speedMps());
    }

    @Test
    void datalinkCanContinueGuidanceAfterSeekerLossButCannotBypassDestroyedGuidance() {
        GuidedWeaponBody seekerKilled = body().disableSeeker();
        TrackState target = track(50_000d, 0d, InformationState.TRACKED, 5d);
        TargetMotionEstimate motion = new TargetMotionEstimate(0d, 0d, 0d, 0d);
        GuidanceRuntime runtime = new GuidanceRuntime();

        GuidanceRuntime.GuidanceCommand onboard = runtime.planLeadPursuit(
                seekerKilled, target, motion, TrackSource.ONBOARD_SEEKER, 1d);
        GuidanceRuntime.GuidanceCommand datalink = runtime.planLeadPursuit(
                seekerKilled, target, motion, TrackSource.DATALINK, 1d);
        GuidanceRuntime.GuidanceCommand destroyed = runtime.planLeadPursuit(
                seekerKilled.disableGuidance(), target, motion, TrackSource.DATALINK, 1d);

        assertEquals(GuidanceRuntime.Failure.SEEKER_DISABLED, onboard.failure());
        assertTrue(datalink.allowed());
        assertEquals(GuidanceRuntime.Failure.GUIDANCE_DISABLED, destroyed.failure());
    }

    @Test
    void terminalReservePolicyPreventsGuidanceFromSpendingReservedDeltaV() {
        double fullIdealDeltaV = 5_000d * Math.log(1_000d / 800d);
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.reserve_test_v1",
                "seeker.radar_v1",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                fullIdealDeltaV);
        GuidedWeaponBody body = GuidedWeaponBody.launch(
                9001L, 44L, 77L, definition,
                "material.high_strength_steel_v1", ProjectileShape.SHELL,
                3d, 0.4d, null, 0d, 0d, 500d, 0d);

        GuidanceRuntime.GuidanceCommand command = new GuidanceRuntime().planLeadPursuit(
                body,
                track(50_000d, 0d, InformationState.TRACKED, 0d),
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                TrackSource.ONBOARD_SEEKER,
                5d);

        assertFalse(command.allowed());
        assertEquals(GuidanceRuntime.Failure.TERMINAL_RESERVE_PROTECTED, command.failure());
    }

    private static GuidedWeaponBody body() {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.interceptor_test_v1",
                "seeker.radar_v1",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                300d);
        return GuidedWeaponBody.launch(
                8001L,
                44L,
                77L,
                definition,
                "material.high_strength_steel_v1",
                ProjectileShape.SHELL,
                3.2d,
                0.48d,
                null,
                0d,
                0d,
                700d,
                0d);
    }

    private static TrackState track(double xM, double yM, InformationState state, double timestamp) {
        return new TrackState(
                77L,
                state,
                true,
                xM,
                yM,
                new TrackCovariance(100d, 1e-10d, 100d),
                0.9d,
                timestamp,
                2,
                3);
    }
}
