package com.spacesim.ui;

import com.spacesim.ship.LiveTacticalSimulationSession;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class LiveTacticalSimulationProjectionTest {
    @Test
    void projectionIsReadOnlyAndShowsCurrentPhysicalBodies() {
        LiveTacticalSimulationSession session = new LiveTacticalSimulationSession();
        LiveTacticalSimulationProjection projection = new LiveTacticalSimulationProjection();
        var before = session.fingerprint();

        var initialVisual = projection.project(session.snapshot());

        assertEquals(before, session.fingerprint(), "projection must not advance or mutate simulation state");
        assertEquals(2, initialVisual.ships().size());
        assertEquals(0, initialVisual.bodies().size());

        for (int index = 0; index < 300 && session.snapshot().projectiles().isEmpty(); index++) {
            session.advanceOneTick();
        }
        var beforeBodyProjection = session.fingerprint();
        var liveVisual = projection.project(session.snapshot());

        assertEquals(beforeBodyProjection, session.fingerprint());
        assertFalse(liveVisual.bodies().isEmpty(),
                "a projectile that exists in authoritative live state must be visible to the presentation adapter");
    }
}
