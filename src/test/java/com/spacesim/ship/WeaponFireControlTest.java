package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.KineticRound;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import com.spacesim.ship.WeaponFireControl.FireFailure;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponFireControlTest {
    private static final WeaponFireControl FIRE_CONTROL = new WeaponFireControl();
    private static final KineticRound ROUND = new KineticRound(
            "ammo.rail_dart_150kg_v1",
            "material.high_density_penetrator_v1",
            ProjectileShape.DART,
            1.8d,
            0.12d,
            150d,
            9_000d);

    @Test
    void kineticSolutionUsesPhysicalLeadAndHasNoArbitraryRangeWall() {
        TrackState track = track(3_000_000d, 0d, 400d * 400d, 100d);
        KinematicState shooter = new KinematicState(0d, 0d, 0d, 0d);
        TargetMotionEstimate motion = new TargetMotionEstimate(0d, 250d, 5d, 0.4d);

        WeaponFireControl.KineticFireSolution solution = FIRE_CONTROL.planKinetic(
                ROUND, track, shooter, motion, 0.00002d, 100d);

        assertTrue(solution.allowed());
        assertEquals(FireFailure.NONE, solution.failure());
        assertTrue(solution.timeOfFlightSeconds() > 333d);
        assertTrue(solution.aimYM() > 80_000d);
        assertTrue(solution.oneSigmaAimUncertaintyM() > 0d);
        assertTrue(solution.maneuverEnvelopeRadiusM() > 0d);
    }

    @Test
    void positionUnknownTrackCannotBecomeFakeExactFireSolution() {
        TrackState bearingOnly = new TrackState(
                77L,
                InformationState.DETECTED,
                false,
                0d,
                0d,
                new TrackCovariance(null, 0.0001d, null),
                0.2d,
                50d,
                1,
                1);

        WeaponFireControl.KineticFireSolution solution = FIRE_CONTROL.planKinetic(
                ROUND,
                bearingOnly,
                new KinematicState(0d, 0d, 0d, 0d),
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                0d,
                50d);

        assertFalse(solution.allowed());
        assertEquals(FireFailure.TRACK_POSITION_UNKNOWN, solution.failure());
    }

    @Test
    void staleTrackAndManeuverIncreaseSpatialEnvelopeInsteadOfChangingHitChance() {
        TrackState track = track(900_000d, 200_000d, 50d * 50d, 10d);
        KinematicState shooter = new KinematicState(0d, 0d, 100d, 0d);
        TargetMotionEstimate motion = new TargetMotionEstimate(-80d, 40d, 4d, 0.8d);

        WeaponFireControl.KineticFireSolution fresh = FIRE_CONTROL.planKinetic(
                ROUND, track, shooter, motion, 0.00001d, 10d);
        WeaponFireControl.KineticFireSolution stale = FIRE_CONTROL.planKinetic(
                ROUND, track, shooter, motion, 0.00001d, 70d);

        assertTrue(fresh.allowed());
        assertTrue(stale.allowed());
        assertTrue(stale.oneSigmaAimUncertaintyM() > fresh.oneSigmaAimUncertaintyM());
        assertTrue(stale.maneuverEnvelopeRadiusM() > fresh.maneuverEnvelopeRadiusM());
    }

    @Test
    void acceptedSolutionMaterializesIndependentProjectileBodyThatContinuesAfterMiss() {
        TrackState track = track(90_000d, 0d, 25d, 0d);
        KinematicState shooter = new KinematicState(1_000d, 2_000d, 10d, -5d);
        WeaponFireControl.KineticFireSolution solution = FIRE_CONTROL.planKinetic(
                ROUND,
                track,
                shooter,
                new TargetMotionEstimate(0d, 0d, 0d, 0d),
                0d,
                0d);

        ProjectileBody body = FIRE_CONTROL.materializeKineticProjectile(901L, 12L, 4_200L, ROUND, shooter, solution);
        ProjectileBody later = body.advance(12d);

        assertEquals(150d, body.massKg(), 1e-12d);
        assertEquals(4_200L, body.spawnTick());
        assertEquals("material.high_density_penetrator_v1", body.materialId());
        assertTrue(body.kineticEnergyJ() > 6.0e9d);
        assertEquals(body.velocityXMps(), later.velocityXMps(), 1e-12d);
        assertEquals(body.velocityYMps(), later.velocityYMps(), 1e-12d);
        assertEquals(body.spawnTick(), later.spawnTick());
        assertTrue(Math.hypot(later.xM() - body.xM(), later.yM() - body.yM()) > 100_000d);
    }

    private static TrackState track(double xM, double yM, double positionVarianceM2, double timeSeconds) {
        return new TrackState(
                77L,
                InformationState.FIRE_CONTROL,
                true,
                xM,
                yM,
                new TrackCovariance(positionVarianceM2, 1e-10d, positionVarianceM2),
                0.95d,
                timeSeconds,
                2,
                4);
    }
}
