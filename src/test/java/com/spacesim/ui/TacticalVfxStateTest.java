package com.spacesim.ui;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import com.spacesim.ui.TacticalVfxState.Flash;
import com.spacesim.ui.TacticalVfxState.FlashKind;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void stateCollectionsExposeStableReadOnlyViewsWithoutPerReadCopies() {
        TacticalVfxState state = new TacticalVfxState();
        List<TacticalVfxState.Particle> particles = state.particles();
        List<Flash> flashes = state.flashes();
        List<TacticalVfxState.WreckEffect> wreckEffects = state.wreckEffects();

        state.advance(snapshot(
                List.of(aliveShip(10L)),
                List.of(new ImpactGlyph(74L, ImpactKind.PENETRATION, 1d, 2d, 3.0e8d))), 0d);

        assertSame(particles, state.particles());
        assertSame(flashes, state.flashes());
        assertSame(wreckEffects, state.wreckEffects());
        assertFalse(particles.isEmpty());
        assertFalse(flashes.isEmpty());
        assertThrows(UnsupportedOperationException.class, particles::clear);
        assertThrows(UnsupportedOperationException.class, flashes::clear);
        assertThrows(UnsupportedOperationException.class, wreckEffects::clear);
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
        assertEquals(1, state.wreckEffects().size());

        TacticalVfxState materializedWreck = new TacticalVfxState();
        materializedWreck.advance(snapshot(List.of(wreckedShip(12L)), List.of()), 0d);
        assertFalse(materializedWreck.flashes().stream()
                .anyMatch(flash -> flash.kind() == FlashKind.DESTRUCTION));
        assertTrue(materializedWreck.wreckEffects().isEmpty());
    }

    @Test
    void reappearingWreckDoesNotLookLikeAliveToWreckTransition() {
        TacticalVfxState state = new TacticalVfxState();
        state.advance(snapshot(List.of(aliveShip(51L), aliveShip(52L)), List.of()), 0d);
        state.advance(snapshot(List.of(aliveShip(52L)), List.of()), 0d);

        state.advance(snapshot(List.of(wreckedShip(51L), aliveShip(52L)), List.of()), 0d);

        assertFalse(state.flashes().stream()
                .anyMatch(flash -> flash.kind() == FlashKind.DESTRUCTION));

        state.advance(snapshot(List.of(wreckedShip(51L), wreckedShip(52L)), List.of()), 0d);
        assertEquals(1L, state.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.DESTRUCTION)
                .count());
    }

    @Test
    void destructionScaleFollowsPhysicalHullDimensions() {
        TacticalVfxState small = destructionState(ship(21L, 80d, 24d, false), ship(21L, 80d, 24d, true));
        TacticalVfxState capital = destructionState(ship(22L, 620d, 150d, false), ship(22L, 620d, 150d, true));

        double smallRadius = destructionFlash(small).radiusM();
        double capitalRadius = destructionFlash(capital).radiusM();

        assertTrue(capitalRadius > smallRadius * 4d);
        assertTrue(capital.wreckEffects().get(0).radiusM() > small.wreckEffects().get(0).radiusM());
        assertTrue(capital.wreckEffects().get(0).pulseCount() > small.wreckEffects().get(0).pulseCount());
    }

    @Test
    void secondaryDetonationsAreDeterministicForStableEntityIdentity() {
        TacticalVfxState first = destructionState(aliveShip(31L), wreckedShip(31L));
        TacticalVfxState second = destructionState(aliveShip(31L), wreckedShip(31L));
        TacticalPrototypeVisualSnapshot wreck = snapshot(List.of(wreckedShip(31L)), List.of());

        for (int index = 0; index < 5; index++) {
            first.advance(wreck, 0.10d);
            second.advance(wreck, 0.10d);
        }

        List<Flash> firstSecondary = first.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.SECONDARY_DETONATION)
                .toList();
        List<Flash> secondSecondary = second.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.SECONDARY_DETONATION)
                .toList();

        assertFalse(firstSecondary.isEmpty());
        assertEquals(firstSecondary.size(), secondSecondary.size());
        for (int index = 0; index < firstSecondary.size(); index++) {
            Flash a = firstSecondary.get(index);
            Flash b = secondSecondary.get(index);
            assertEquals(a.xM(), b.xM(), 0d);
            assertEquals(a.yM(), b.yM(), 0d);
            assertEquals(a.radiusM(), b.radiusM(), 0d);
            assertEquals(a.remainingFraction(), b.remainingFraction(), 0d);
        }
    }

    @Test
    void lingeringWreckEffectExpiresWithoutRearming() {
        TacticalVfxState state = destructionState(aliveShip(41L), wreckedShip(41L));
        TacticalPrototypeVisualSnapshot wreck = snapshot(List.of(wreckedShip(41L)), List.of());
        assertFalse(state.wreckEffects().isEmpty());

        for (int index = 0; index < 40; index++) {
            state.advance(wreck, 0.10d);
        }

        assertTrue(state.wreckEffects().isEmpty());
        long primaryBursts = state.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.DESTRUCTION)
                .count();
        assertTrue(primaryBursts <= 1L);
    }

    @Test
    void wreckEffectBudgetRemainsBoundedUnderMassDestruction() {
        ArrayList<ShipGlyph> alive = new ArrayList<>();
        ArrayList<ShipGlyph> wrecked = new ArrayList<>();
        for (long id = 100L; id < 180L; id++) {
            alive.add(ship(id, 120d + id, 36d, false));
            wrecked.add(ship(id, 120d + id, 36d, true));
        }
        TacticalVfxState state = new TacticalVfxState();
        state.advance(snapshot(alive, List.of()), 0d);

        state.advance(snapshot(wrecked, List.of()), 0d);

        assertTrue(state.wreckEffects().size() <= TacticalVfxState.MAX_WRECK_EFFECTS);
        assertTrue(state.particles().size() <= TacticalVfxState.MAX_PARTICLES);
        assertTrue(state.flashes().size() <= TacticalVfxState.MAX_FLASHES);
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

    private static TacticalVfxState destructionState(ShipGlyph alive, ShipGlyph wrecked) {
        TacticalVfxState state = new TacticalVfxState();
        state.advance(snapshot(List.of(alive), List.of()), 0d);
        state.advance(snapshot(List.of(wrecked), List.of()), 0d);
        return state;
    }

    private static Flash destructionFlash(TacticalVfxState state) {
        return state.flashes().stream()
                .filter(flash -> flash.kind() == FlashKind.DESTRUCTION)
                .findFirst()
                .orElseThrow();
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
        return ship(id, 120d, 36d, false);
    }

    private static ShipGlyph wreckedShip(long id) {
        return ship(id, 120d, 36d, true);
    }

    private static ShipGlyph ship(long id, double lengthM, double widthM, boolean wreck) {
        return new ShipGlyph(
                id,
                TacticalSide.ALPHA,
                ShipVisualRole.BALANCED,
                0d,
                0d,
                0d,
                lengthM,
                widthM,
                wreck ? 0d : 0.75d,
                wreck ? 0d : 1d,
                wreck);
    }
}
