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

    private static MiningBeam active(long extraction) {
        return new MiningBeam("fleet:7", "resource:src.3", true, extraction);
    }

    private static MiningBeam ended(long extraction) {
        return new MiningBeam("fleet:7", "resource:src.3", false, extraction);
    }

    private static MiningBeamEffects.Effect frame(MiningBeamEffects effects, MiningBeam beam) {
        return effects.advance(List.of(beam), FRAME).get(0);
    }

    @Test
    void newOrderFadesInWithoutABurstUntilAnExtractionReceipt() {
        var effects = new MiningBeamEffects();
        // An earlier receipt from the same source is history, not an extraction of this order.
        var first = frame(effects, active(40));
        assertTrue(first.active());
        assertEquals(FRAME / MiningBeamEffects.FADE_IN_SECONDS, first.intensity(), 1e-6f);
        assertFalse(first.bursting());
        for (int i = 0; i < 20; i++) frame(effects, active(40));
        var steady = frame(effects, active(40));
        assertEquals(1f, steady.intensity());
        assertFalse(steady.bursting(), "no extraction yet, no sparks");

        var burst = frame(effects, active(101));
        assertTrue(burst.bursting());
        assertEquals(FRAME, burst.burstAge(), 1e-6f);
    }

    @Test
    void pauseEmitsNothingAndATimeSkipOneBurst() {
        var effects = new MiningBeamEffects();
        frame(effects, active(-1));
        long seed = frame(effects, active(101)).burstSeed();
        MiningBeamEffects.Effect paused = null;
        for (int i = 0; i < 40; i++) paused = frame(effects, active(101));
        assertFalse(paused.bursting(), "the same receipt for longer than a burst");
        assertTrue(paused.active());

        var skipped = frame(effects, active(140));
        assertTrue(skipped.bursting(), "several ticks at once: one burst");
        assertNotEquals(seed, skipped.burstSeed());
        for (int i = 0; i < 40; i++) frame(effects, active(140));
        assertFalse(frame(effects, active(139)).bursting(), "an older receipt is not an extraction");
    }

    @Test
    void stopAndRestartOfTheSameContactIsNotAnExtraction() {
        // The route from the review: mining, a stop, a restart of the same pair, with no new receipt.
        var effects = new MiningBeamEffects();
        for (int i = 0; i < 30; i++) frame(effects, active(100));
        var stopped = effects.advance(List.of(), FRAME);
        assertEquals(1, stopped.size());
        assertFalse(stopped.get(0).active());
        assertEquals(1f - FRAME / MiningBeamEffects.FADE_OUT_SECONDS, stopped.get(0).intensity(), 1e-5f);
        var restarted = frame(effects, active(100));
        assertTrue(restarted.active());
        assertFalse(restarted.bursting(), "the restart's assignment is not a receipt");
        // Even after the old beam has faded away completely.
        for (int i = 0; i < 40; i++) effects.advance(List.of(), FRAME);
        assertTrue(effects.advance(List.of(), FRAME).isEmpty(), "gone after the fade-out");
        assertFalse(frame(effects, active(100)).bursting());
    }

    @Test
    void theExtractionThatDepletesTheSourceStillSparksWhileTheBeamFades() {
        var effects = new MiningBeamEffects();
        for (int i = 0; i < 30; i++) frame(effects, active(100));
        // The depleting tick extracts and removes the order in the same tick.
        var last = frame(effects, ended(101));
        assertFalse(last.active());
        assertTrue(last.bursting());
        assertTrue(last.intensity() < 1f, "fading out");
        // An extraction with no beam ever shown lights nothing up.
        var fresh = new MiningBeamEffects();
        assertTrue(fresh.advance(List.of(ended(101)), FRAME).isEmpty());
    }

    @Test
    void trackedBeamsAreBoundedAndDuplicatesCountOnce() {
        var effects = new MiningBeamEffects();
        var many = new ArrayList<MiningBeam>();
        for (int i = 0; i < 100; i++) many.add(new MiningBeam("fleet:" + i, "resource:r", true, 1));
        many.add(new MiningBeam("fleet:0", "resource:r", true, 1));
        assertEquals(MiningBeamEffects.MAX_TRACKED, effects.advance(many, FRAME).size());
        effects.clear();
        assertTrue(effects.advance(List.of(), FRAME).isEmpty());
        assertEquals(1, effects.advance(List.of(active(1)), FRAME).size());
        assertEquals(1, effects.advance(List.of(active(1)), Float.NaN).size(), "a zero-length frame drops nothing");
        assertThrows(IllegalArgumentException.class, () -> new MiningBeam("fleet:1", "resource:r", true, -2));
    }
}
