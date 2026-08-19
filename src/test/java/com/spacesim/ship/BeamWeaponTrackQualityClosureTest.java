package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamWeaponTrackQualityClosureTest {
    private static final BeamWeapon BEAM = new BeamWeapon(
            5_000_000d,
            0.85d,
            5e-7d,
            2e-6d,
            2d,
            6_000_000d,
            1_000_000d);

    @Test
    void trackedCartesianSolutionIsAdmittedWithoutGlobalFireControlThreshold() {
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();

        var tracked = runtime.plan(BEAM, track(InformationState.TRACKED, 25d), 0d, 0d, 1d);
        var classified = runtime.plan(BEAM, track(InformationState.CLASSIFIED, 25d), 0d, 0d, 1d);

        assertTrue(tracked.allowed());
        assertFalse(classified.allowed());
    }

    @Test
    void worseTrackedCovarianceDegradesBeamUsefulnessContinuously() {
        BeamWeaponRuntime runtime = new BeamWeaponRuntime();

        var precise = runtime.plan(BEAM, track(InformationState.TRACKED, 10d), 0d, 0d, 1d);
        var uncertain = runtime.plan(BEAM, track(InformationState.TRACKED, 2_000d), 0d, 0d, 1d);

        assertTrue(precise.allowed());
        assertTrue(uncertain.allowed());
        assertTrue(uncertain.effectiveSpotRadiusM() > precise.effectiveSpotRadiusM());
        assertTrue(uncertain.meanIrradianceWPerM2() < precise.meanIrradianceWPerM2());
    }

    private static TrackState track(InformationState state, double positionSigmaM) {
        double variance = positionSigmaM * positionSigmaM;
        return new TrackState(
                77L,
                state,
                true,
                1_000_000d,
                0d,
                new TrackCovariance(variance, 1e-12d, variance),
                1d,
                0d,
                1,
                1);
    }
}
