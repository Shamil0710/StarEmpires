package com.spacesim.ship;

import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeamWeaponTrackQualityClosureTest {
    private static final BeamWeapon BEAM = new BeamWeapon(
            "weapon.stage20_track_quality_closure_beam_v1",
            1.06e-6d,
            4d,
            2e-6d,
            500_000_000d,
            700_000_000d,
            220_000_000d,
            3d);

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
