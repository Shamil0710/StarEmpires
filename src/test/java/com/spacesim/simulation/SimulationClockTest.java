package com.spacesim.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationClockTest {
    @Test
    void одинаковоеВремяДаётОдинаковоеЧислоTicksПриРазномРазбиенииКадров() {
        SimulationClock coarse = new SimulationClock(0.1f);
        SimulationClock fine = new SimulationClock(0.1f);

        coarse.addFrameTime(1f);
        for (int frame = 0; frame < 10; frame++) {
            fine.addFrameTime(0.1f);
        }

        assertEquals(10, drain(coarse));
        assertEquals(10, drain(fine));
        assertEquals(coarse.getTick(), fine.getTick());
        assertEquals(coarse.getSimulationTimeSeconds(), fine.getSimulationTimeSeconds(), 1e-8);
    }

    @Test
    void pauseНеНакапливаетПропущенноеВремя() {
        SimulationClock clock = new SimulationClock(0.1f);
        clock.setPaused(true);
        clock.addFrameTime(10f);

        assertFalse(clock.hasPendingStep());
        assertEquals(0L, clock.getTick());

        clock.setPaused(false);
        clock.addFrameTime(0.1f);
        assertTrue(clock.hasPendingStep());
        assertEquals(0.1f, clock.consumeStep());
        assertEquals(1L, clock.getTick());
    }

    @Test
    void timeScaleУправляетСкоростьюИгровогоВремени() {
        SimulationClock clock = new SimulationClock(0.1f);
        clock.setTimeScale(4d);
        clock.addFrameTime(0.25f);

        assertEquals(10, drain(clock));
        assertEquals(1d, clock.getSimulationTimeSeconds(), 1e-7);

        clock.setTimeScale(0d);
        clock.addFrameTime(100f);
        assertFalse(clock.hasPendingStep());
    }

    @Test
    void interpolationAlphaОтражаетНеполныйШаг() {
        SimulationClock clock = new SimulationClock(0.2f);
        clock.addFrameTime(0.05f);

        assertEquals(0.25d, clock.getInterpolationAlpha(), 1e-6);
    }

    @Test
    void snapshotRestoreСохраняетДажеНеполныйTickPauseИТimeScale() {
        SimulationClock original = new SimulationClock(0.1f);
        original.setTimeScale(1.5d);
        original.addFrameTime(0.15f);
        assertEquals(2, drain(original));
        original.addFrameTime(0.025f);
        original.setPaused(true);

        SimulationClock.State saved = original.snapshotState();
        SimulationClock restored = new SimulationClock(saved);

        assertEquals(saved, restored.snapshotState());
        assertEquals(original.getInterpolationAlpha(), restored.getInterpolationAlpha(), 0d);
        assertTrue(restored.isPaused());
        assertEquals(1.5d, restored.getTimeScale(), 0d);

        original.setPaused(false);
        restored.setPaused(false);
        original.addFrameTime(0.1f);
        restored.addFrameTime(0.1f);
        assertEquals(drain(original), drain(restored));
        assertEquals(original.snapshotState(), restored.snapshotState());
    }

    @Test
    void stateОтклоняетПовреждённыеClockДанные() {
        assertThrows(NullPointerException.class, () -> new SimulationClock((SimulationClock.State) null));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationClock.State(0.1f, -1L, 0d, 1d, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationClock.State(0.1f, 100_000_000L, 0d, 1d, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationClock.State(0.1f, 0L, 1d, 1d, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationClock.State(0.1f, 0L, 0d, -1d, false, 0L));
        assertThrows(IllegalArgumentException.class,
                () -> new SimulationClock.State(0.1f, 0L, 0d, 1d, false, -1L));
    }

    @Test
    void отклоняетНекорректныеПараметрыИПустоеИзвлечение() {
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(0f));
        assertThrows(IllegalArgumentException.class, () -> new SimulationClock(Float.NaN));

        SimulationClock clock = new SimulationClock(0.1f);
        assertThrows(IllegalArgumentException.class, () -> clock.addFrameTime(-0.1f));
        assertThrows(IllegalArgumentException.class, () -> clock.addFrameTime(Float.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> clock.setTimeScale(-1d));
        assertThrows(IllegalArgumentException.class, () -> clock.setTimeScale(Double.NaN));
        assertThrows(IllegalStateException.class, clock::consumeStep);
    }

    private int drain(SimulationClock clock) {
        int ticks = 0;
        while (clock.hasPendingStep()) {
            clock.consumeStep();
            ticks++;
        }
        return ticks;
    }
}
