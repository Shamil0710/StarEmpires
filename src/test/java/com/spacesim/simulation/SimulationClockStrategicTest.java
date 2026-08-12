package com.spacesim.simulation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SimulationClockStrategicTest {
    @Test
    void strategicStepsПродвигаютGameTimeНоСохраняютRenderAccumulator() {
        SimulationClock clock = new SimulationClock(0.1f);
        clock.addFrameTime(0.05f);
        SimulationClock.State before = clock.snapshotState();

        float delta = clock.advanceStrategicSteps(10);
        SimulationClock.State after = clock.snapshotState();

        assertEquals(1f, delta, 0f);
        assertEquals(10L, clock.getTick());
        assertEquals(1d, clock.getSimulationTimeSeconds(), 0d);
        assertEquals(before.accumulatorNanos(), after.accumulatorNanos());
        assertEquals(before.fractionalNanos(), after.fractionalNanos(), 0d);
        assertEquals(0.5d, clock.getInterpolationAlpha(), 1e-12d);
    }

    @Test
    void strategicStepsОтклоняютНеположительныйПакет() {
        SimulationClock clock = new SimulationClock(0.1f);

        assertThrows(IllegalArgumentException.class, () -> clock.advanceStrategicSteps(0));
        assertThrows(IllegalArgumentException.class, () -> clock.advanceStrategicSteps(-1));
    }
}
