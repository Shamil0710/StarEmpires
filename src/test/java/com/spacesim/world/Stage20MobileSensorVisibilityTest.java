package com.spacesim.world;

import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20MobileSensorVisibility.MobileContactView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MobileSensorVisibilityTest {
    @Test
    void mobileProjectionUsesStage175TrackStatesWithoutGrantingTruthFields() {
        MobileContactView detected = Stage20MobileSensorVisibility.fromTrack(track(
                InformationState.DETECTED, false));
        MobileContactView classified = Stage20MobileSensorVisibility.fromTrack(track(
                InformationState.CLASSIFIED, false));
        MobileContactView tracked = Stage20MobileSensorVisibility.fromTrack(track(
                InformationState.TRACKED, true));
        MobileContactView fireControl = Stage20MobileSensorVisibility.fromTrack(track(
                InformationState.FIRE_CONTROL, true));

        assertEquals(DiscoveryState.DETECTED, detected.discoveryState());
        assertEquals(DiscoveryState.CLASSIFIED, classified.discoveryState());
        assertEquals(DiscoveryState.TRACKED, tracked.discoveryState());
        assertEquals(DiscoveryState.TRACKED, fireControl.discoveryState());
        assertFalse(detected.classified());
        assertTrue(classified.classified());
        assertFalse(classified.positionEstimateAvailable());
        assertTrue(tracked.positionEstimateAvailable());
        assertTrue(tracked.rangeEstimateAvailable());
        assertFalse(tracked.fireControlQualified());
        assertTrue(fireControl.fireControlQualified());

        for (MobileContactView view : new MobileContactView[] {
                detected, classified, tracked, fireControl}) {
            assertFalse(view.exactRangeAvailable());
            assertFalse(view.exactIdentityAvailable());
            assertFalse(view.velocityEstimateAvailable());
            assertFalse(view.loadoutAvailable());
            assertFalse(view.knownStaticLocation());
            assertEquals(70_001L, view.track().targetId());
        }
    }

    @Test
    void callerCannotConstructProjectionThatDivergesFromTrackAuthority() {
        TrackState tracked = track(InformationState.TRACKED, true);

        assertThrows(IllegalArgumentException.class,
                () -> new MobileContactView(tracked, DiscoveryState.CLASSIFIED, false));
        assertThrows(IllegalArgumentException.class,
                () -> new MobileContactView(tracked, DiscoveryState.TRACKED, true));
    }

    private static TrackState track(InformationState state, boolean positionKnown) {
        TrackCovariance covariance = positionKnown
                ? new TrackCovariance(40_000d, 1.0e-8d, 90_000d)
                : new TrackCovariance(null, 1.0e-8d, null);
        return new TrackState(
                70_001L,
                state,
                positionKnown,
                positionKnown ? 4_500_000d : 0d,
                positionKnown ? -2_000_000d : 0d,
                covariance,
                state.ordinal() / 3d,
                100d,
                1,
                1);
    }
}
