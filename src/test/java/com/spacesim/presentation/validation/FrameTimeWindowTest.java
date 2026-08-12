package com.spacesim.presentation.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class FrameTimeWindowTest {
    private static final double EPSILON = 1.0e-9;

    @Test
    void reportsRollingAverageP95AndMaximum() {
        FrameTimeWindow window = new FrameTimeWindow(4);
        window.recordSeconds(0.010);
        window.recordSeconds(0.020);
        window.recordSeconds(0.030);
        window.recordSeconds(0.040);

        assertEquals(4, window.size());
        assertEquals(25.0, window.averageMilliseconds(), EPSILON);
        assertEquals(40.0, window.p95Milliseconds(), EPSILON);
        assertEquals(40.0, window.maxMilliseconds(), EPSILON);

        window.recordSeconds(0.005);

        assertEquals(4, window.size());
        assertEquals(23.75, window.averageMilliseconds(), EPSILON);
        assertEquals(40.0, window.p95Milliseconds(), EPSILON);
        assertEquals(40.0, window.maxMilliseconds(), EPSILON);
    }

    @Test
    void emptyWindowReportsZeros() {
        FrameTimeWindow window = new FrameTimeWindow(3);

        assertEquals(0, window.size());
        assertEquals(0.0, window.averageMilliseconds(), EPSILON);
        assertEquals(0.0, window.p95Milliseconds(), EPSILON);
        assertEquals(0.0, window.maxMilliseconds(), EPSILON);
    }

    @Test
    void rejectsInvalidCapacityAndSamples() {
        assertThrows(IllegalArgumentException.class, () -> new FrameTimeWindow(0));

        FrameTimeWindow window = new FrameTimeWindow(2);
        assertThrows(IllegalArgumentException.class, () -> window.recordSeconds(0.0));
        assertThrows(IllegalArgumentException.class, () -> window.recordSeconds(-0.1));
        assertThrows(IllegalArgumentException.class, () -> window.recordSeconds(Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> window.recordSeconds(Double.POSITIVE_INFINITY));
    }
}
