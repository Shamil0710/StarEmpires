package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import com.spacesim.ui.TacticalVfxState.FlashKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalVfxStateTest {
    @Test
    void repeatedImpactEventSpawnsParticlesOnlyOnce() {
        TacticalVfxState state = new TacticalVfxState();
        TacticalPrototypeVisualSnapshot snapshot = snapshot(
                List.of(aliveShip(7L)),
                List.of(new ImpactGlyph(41L, ImpactKind.PENETRATION, 10d, 20d, 8.0e8d)));

        state.advance(snapshot, 0d);
        int particleCount = state.particles().size();
        int flashCount = state.flashes().size();

        state.advance(snapshot, 0d);

        assertTrue(particleCount >= 10);
        assertEquals(particleCount, state.particles().size());
        assertEquals(flashCount, state.flashes().size());
        assertEquals(1, state.seenImpactCount());
    }

    @Test
    void identicalEventIdentityProducesIdenticalParticlePattern() {
        TacticalPrototypeVisualSnapshot snapshot = snapshot(
                List.of(aliveShip(9L)),
                List.of(new ImpactGlyph(73L, ImpactKind.ARMOR, -12d, 33d, 2.5e7d)));
        TacticalVfxState first = new TacticalVfxState();
        TacticalVfxState second = new TacticalVfxState();

        first.advance(snapshot, 0d);
        second.advance(snapshot, 0d);

        assertEquals(first.particles().size(), second.particles().size());
        for (int index = 0; index < first.particles().size(); index++) {
            TacticalVfxState.Particle a = first.particles().get(index);
            TacticalVfxState.Particle b = second.particles().get(index);
            assertEquals(a.kind(), b.kind());
            assertEquals(a.xM(), b.xM(), 0d);
            assertEquals(a.yM(), b.yM(), 0d);
            assertEquals(a.vxMps(), b.vxMps(), 0d);
            assertEquals(a.vyMps(), b.vyMps(), 0d);
            assertEquals(a.radiusM(), b.radiusM(), 0d);
            assertEquals(a.remainingFraction(), b.remainingFraction(), 0d);
        }
    }

    @Test
    void wreckTransitionCreatesOneDestructionBurstButMaterializedWreckDoesNot() {
        TacticalVfxState state = new TacticalVfxState();
        state.advance(snapshot(List.of(aliveShip(11L)), List.of()), 0d);

        state.advance(snapshot(List.of(wreckedShip(11L)), List.of()), 0d);
        long destructionFlashes = state.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.DESTRUCTION)
                .count();
        int particlesAfterTransition = state.particles().size();

        state.advance(snapshot(List.of(wreckedShip(11L)), List.of()), 0d);

        assertEquals(1L, destructionFlashes);
        assertEquals(destructionFlashes, state.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.DESTRUCTION)
                .count());
        assertEquals(particlesAfterTransition, state.particles().size());

        TacticalVfxState materializedWreck = new TacticalVfxState();
        materializedWreck.advance(snapshot(List.of(wreckedShip(12L)), List.of()), 0d);
        assertFalse(materializedWreck.flashes().stream()
                .anyMatch(flash -> flash.kind() == FlashKind.DESTRUCTION));
    }

    @Test
    void particleAndFlashBudgetsRemainBoundedUnderSaturation() {
        ArrayList<ImpactGlyph> impacts = new ArrayList<>();
        for (long id = 1L; id <= 2200L; id++) {
            impacts.add(new ImpactGlyph(id, ImpactKind.PENETRATION, id, -id, 1.0e9d));
        }
        TacticalVfxState state = new TacticalVfxState();

        state.advance(snapshot(List.of(aliveShip(15L)), impacts), 0d);

        assertTrue(state.particles().size() <= TacticalVfxState.MAX_PARTICLES);
        assertTrue(state.flashes().size() <= TacticalVfxState.MAX_FLASHES);
        assertTrue(state.seenImpactCount() <= TacticalVfxState.MAX_SEEN_IMPACTS);
    }

    @Test
    void presentationFrameDeltaIsCappedBeforeParticleIntegration() {
        TacticalVfxState state = new TacticalVfxState();
        TacticalPrototypeVisualSnapshot impact = snapshot(
                List.of(aliveShip(17L)),
                List.of(new ImpactGlyph(91L, ImpactKind.PENETRATION, 0d, 0d, 1.0e9d)));
        state.advance(impact, 0d);
        assertFalse(state.particles().isEmpty());
        TacticalVfxState.Particle particle = state.particles().get(0);
        double startX = particle.xM();
        double velocityX = particle.vxMps();

        state.advance(snapshot(List.of(aliveShip(17L)), List.of()), 100d);

        assertFalse(state.particles().isEmpty());
        TacticalVfxState.Particle advanced = state.particles().get(0);
        assertEquals(startX + velocityX * 0.10d, advanced.xM(), 1e-9d);
    }

    private static TacticalPrototypeVisualSnapshot snapshot(
            List<ShipGlyph> ships,
            List<ImpactGlyph> impacts) {
        return new TacticalPrototypeVisualSnapshot(
                ships,
                List.of(),
                List.of(),
                List.of(),
                impacts,
                List.of());
    }

    private static ShipGlyph aliveShip(long id) {
        return new ShipGlyph(
                id,
                TacticalSide.ALPHA,
                ShipVisualRole.BALANCED,
                0d,
                0d,
                0d,
                120d,
                36d,
                0.75d,
                1d,
                false);
    }

    private static ShipGlyph wreckedShip(long id) {
        return new ShipGlyph(
                id,
                TacticalSide.ALPHA,
                ShipVisualRole.BALANCED,
                0d,
                0d,
                0d,
                120d,
                36d,
                0d,
                0d,
                true);
    }
}
