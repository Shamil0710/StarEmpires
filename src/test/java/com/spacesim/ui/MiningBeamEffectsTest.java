package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MiningBeamEffectsTest {
    private static final float FRAME = 1f / 60f;

    private static MiningBeam beam(long tick) {
        return new MiningBeam("fleet:7", "resource:src.3", tick);
    }

    @Test
    void beamFadesInWithoutABurstUntilTheWatermarkAdvances() {
        var effects = new MiningBeamEffects();
        // The order's first watermark is its assignment tick, not an extraction.
        var first = effects.advance(List.of(beam(100)), FRAME).get(0);
        assertTrue(first.active());
        assertEquals(FRAME / MiningBeamEffects.FADE_IN_SECONDS, first.intensity(), 1e-6f);
        assertFalse(first.bursting());
        for (int i = 0; i < 20; i++) effects.advance(List.of(beam(100)), FRAME);
        var steady = effects.advance(List.of(beam(100)), FRAME).get(0);
        assertEquals(1f, steady.intensity());
        assertFalse(steady.bursting(), "no extraction yet, no sparks");
    }

    @Test
    void eachObservedExtractionStartsOneBurstAndAPauseEmitsNothing() {
        var effects = new MiningBeamEffects();
        effects.advance(List.of(beam(100)), FRAME);
        var burst = effects.advance(List.of(beam(101)), FRAME).get(0);
        assertTrue(burst.bursting());
        assertEquals(FRAME, burst.burstAge(), 1e-6f);
        long seed = burst.burstSeed();

        // Paused: the same watermark for longer than a burst, so the burst ends and none follows.
        MiningBeamEffects.Effect paused = null;
        for (int i = 0; i < 40; i++) paused = effects.advance(List.of(beam(101)), FRAME).get(0);
        assertFalse(paused.bursting());
        assertTrue(paused.active());

        // Time-skip jumps several ticks at once: one burst, with a new seed.
        var skipped = effects.advance(List.of(beam(140)), FRAME).get(0);
        assertTrue(skipped.bursting());
        assertNotEquals(seed, skipped.burstSeed());
        // An older or equal watermark is not an extraction.
        for (int i = 0; i < 40; i++) effects.advance(List.of(beam(140)), FRAME);
        assertFalse(effects.advance(List.of(beam(139)), FRAME).get(0).bursting());
    }

    @Test
    void stoppedOrderFadesOutAndARestartIsNotAnExtraction() {
        var effects = new MiningBeamEffects();
        for (int i = 0; i < 20; i++) effects.advance(List.of(beam(100)), FRAME);
        var fading = effects.advance(List.of(), FRAME);
        assertEquals(1, fading.size());
        assertFalse(fading.get(0).active());
        assertEquals(1f - FRAME / MiningBeamEffects.FADE_OUT_SECONDS, fading.get(0).intensity(), 1e-5f);

        // A new order on the same pair while fading: its assignment watermark starts no burst.
        var restarted = effects.advance(List.of(beam(500)), FRAME).get(0);
        assertTrue(restarted.active());
        assertFalse(restarted.bursting());

        List<MiningBeamEffects.Effect> gone = List.of();
        for (int i = 0; i < 30; i++) gone = effects.advance(List.of(), FRAME);
        assertTrue(gone.isEmpty(), "the beam disappears after the fade-out");
        // A zero-length frame changes nothing and drops nothing.
        effects.advance(List.of(beam(1)), FRAME);
        assertEquals(1, effects.advance(List.of(beam(1)), Float.NaN).size());
    }

    @Test
    void trackedBeamsAreBoundedAndDuplicatesCountOnce() {
        var effects = new MiningBeamEffects();
        var many = new ArrayList<MiningBeam>();
        for (int i = 0; i < 100; i++) many.add(new MiningBeam("fleet:" + i, "resource:r", 1));
        many.add(new MiningBeam("fleet:0", "resource:r", 1));
        assertEquals(MiningBeamEffects.MAX_TRACKED, effects.advance(many, FRAME).size());
        effects.clear();
        assertTrue(effects.advance(List.of(), FRAME).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new MiningBeam("fleet:1", "resource:r", -1));
    }
}
