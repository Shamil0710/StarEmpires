package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ITacticalAcceptancePlaybackTest {
    @Test
    void playbackIsDeterministicPhysicalAndPresentationOnly() {
        var first = Stage175ITacticalAcceptancePlayback.create();
        var second = Stage175ITacticalAcceptancePlayback.create();

        assertEquals(first, second);
        assertEquals(3, first.frames().size());
        assertEquals(first.initialKineticRounds() - first.kineticRoundsConsumed(), first.remainingKineticRounds());
        assertTrue(first.kineticRoundsConsumed() > 0L);
        assertEquals(1L, first.missileRoundsConsumed());
        assertEquals(1, first.defenseAssignments());
        assertEquals(0d, first.finalAccelerationMps2(), 1e-12d);

        var engagement = first.frames().get(0).snapshot();
        assertTrue(engagement.bodies().stream().anyMatch(value -> value.kind() == BodyKind.KINETIC_PROJECTILE));
        assertTrue(engagement.bodies().stream().anyMatch(value -> value.kind() == BodyKind.GUIDED_MISSILE));
        assertTrue(engagement.bodies().stream().anyMatch(value -> value.kind() == BodyKind.INTERCEPTOR));
        assertTrue(engagement.bodies().stream().anyMatch(value -> value.kind() == BodyKind.DECOY));
        assertEquals(1, engagement.beams().size());
        assertTrue(engagement.shields().stream().anyMatch(value -> value.reserveFraction() > 0d));
        assertTrue(engagement.ships().stream().anyMatch(value -> value.thrustFraction() > 0d));

        var penetration = first.frames().get(1).snapshot();
        assertTrue(penetration.impacts().stream().anyMatch(value -> value.kind() == ImpactKind.ARMOR));
        assertTrue(penetration.impacts().stream().anyMatch(value -> value.kind() == ImpactKind.PENETRATION));
        assertTrue(penetration.damage().stream().anyMatch(value -> value.severity() > 0d));

        var wreck = first.frames().get(2).snapshot();
        assertTrue(wreck.ships().stream().anyMatch(TacticalPrototypeVisualSnapshot.ShipGlyph::wreck));
        assertEquals(6L, wreck.bodies().stream().filter(value -> value.kind() == BodyKind.DEBRIS).count());
    }
}
