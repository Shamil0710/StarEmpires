package com.spacesim.ui;

import com.spacesim.ship.Stage19ScaledLiveTacticalFactory;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaledLiveTacticalSimulationParityTest {
    @Test
    void repeatedProjectionReadsDoNotAdvanceOrMutateAuthoritativeState() {
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();
        for (int index = 0; index < 24; index++) {
            live.advanceOneTick();
        }
        long tickBefore = live.tick();
        var fingerprintBefore = live.fingerprint();

        TacticalPrototypeVisualSnapshot snapshot = null;
        for (int index = 0; index < 40; index++) {
            snapshot = live.snapshot();
        }

        assertEquals(tickBefore, live.tick());
        assertEquals(fingerprintBefore, live.fingerprint());
        assertEquals(32, snapshot.ships().size());
        assertEquals(live.runtime().ordnanceRuntime().weaponRuntime().projectiles().size(),
                snapshot.bodies().stream().filter(value -> value.kind() == BodyKind.KINETIC_PROJECTILE).count());
        assertEquals(live.runtime().ordnanceRuntime().guidedBodies().size(),
                snapshot.bodies().stream().filter(value -> value.kind() == BodyKind.GUIDED_MISSILE).count());
        assertEquals(live.runtime().defenseRuntime().interceptorBodies().size(),
                snapshot.bodies().stream().filter(value -> value.kind() == BodyKind.INTERCEPTOR).count());
        assertEquals(live.runtime().decoyRuntime().decoyBodies().size(),
                snapshot.bodies().stream().filter(value -> value.kind() == BodyKind.DECOY).count());
    }

    @Test
    void headlessAndLiveReplayRemainIdenticalDespiteArbitrarySnapshotReads() {
        var headless = Stage19ScaledLiveTacticalFactory.createSaturation32();
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();

        for (int tick = 0; tick < 80; tick++) {
            if (tick % 2 == 0) {
                live.snapshot();
                live.snapshot();
            }
            headless.advanceOneTick();
            live.advanceOneTick();
            if (tick % 3 == 0) {
                live.snapshot();
            }
        }

        assertEquals(headless.tick(), live.tick());
        assertEquals(headless.fingerprint(), live.fingerprint(),
                "live projection reads must not alter the authoritative scaled replay");
    }

    @Test
    void scaledProjectionCanRepresentAllFourSaturationBodyClassesAtOnce() {
        ScaledLiveTacticalSimulationSession live = new ScaledLiveTacticalSimulationSession();
        Set<BodyKind> kinds = Set.of();
        for (int tick = 0; tick < 240; tick++) {
            live.advanceOneTick();
            TacticalPrototypeVisualSnapshot snapshot = live.snapshot();
            kinds = snapshot.bodies().stream().map(TacticalPrototypeVisualSnapshot.BodyGlyph::kind)
                    .collect(Collectors.toSet());
            if (kinds.containsAll(Set.of(
                    BodyKind.KINETIC_PROJECTILE,
                    BodyKind.GUIDED_MISSILE,
                    BodyKind.INTERCEPTOR,
                    BodyKind.DECOY))) {
                break;
            }
        }

        assertEquals(32, live.snapshot().ships().size());
        assertTrue(kinds.containsAll(Set.of(
                BodyKind.KINETIC_PROJECTILE,
                BodyKind.GUIDED_MISSILE,
                BodyKind.INTERCEPTOR,
                BodyKind.DECOY)));
    }
}
