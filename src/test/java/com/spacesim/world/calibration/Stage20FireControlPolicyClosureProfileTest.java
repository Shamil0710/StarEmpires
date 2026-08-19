package com.spacesim.world.calibration;

import com.spacesim.ship.TrackState.InformationState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20FireControlPolicyClosureProfileTest {
    @Test
    void currentClosureRejectsUniversalSensorFireControlGate() {
        Stage20FireControlPolicyClosureProfile profile = Stage20FireControlPolicyClosureProfile.deriveCurrent();

        assertEquals(Stage20FireControlPolicyClosureProfile.CURRENT_VERSION, profile.version());
        assertEquals(InformationState.TRACKED, profile.minimumSharedWeaponTrackState());
        assertFalse(profile.universalSensorFireControlThresholdRequired());
        assertTrue(profile.kineticConsumesContinuousTrackUncertainty());
        assertTrue(profile.beamConsumesContinuousTrackUncertainty());
        assertTrue(profile.guidedConsumesContinuousTrackState());
        assertTrue(profile.closesStage20FireControlPolicy());
        assertEquals(3, profile.sources().size());
    }
}
