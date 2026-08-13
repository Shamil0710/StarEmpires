package com.spacesim.presentation.validation;

import java.util.Arrays;

/**
 * Fixed-size rolling frame-time window for rendering evidence.
 *
 * <p>The collector stores milliseconds rather than FPS so spikes remain visible. Statistics are
 * computed over the currently populated window and intentionally make no assumptions about the
 * rendering backend.</p>
 */
public final class FrameTimeWindow {
    private final double[] milliseconds;
    private int size;
    private int nextIndex;

    /**
     * Creates a rolling window.
     *
     * @param capacity positive number of recent frames to retain
     */
    public FrameTimeWindow(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Frame-time window capacity must be positive");
        }
        milliseconds = new double[capacity];
    }

    /**
     * Records one positive finite frame duration.
     *
     * @param deltaSeconds duration in seconds
     */
    public void recordSeconds(double deltaSeconds) {
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0.0) {
            throw new IllegalArgumentException("Frame duration must be finite and positive");
        }
        milliseconds[nextIndex] = deltaSeconds * 1000.0;
        nextIndex = (nextIndex + 1) % milliseconds.length;
        if (size < milliseconds.length) {
            size++;
        }
    }

    /** @return number of samples currently retained */
    public int size() {
        return size;
    }

    /** @return arithmetic mean frame time in milliseconds, or zero when empty */
    public double averageMilliseconds() {
        if (size == 0) {
            return 0.0;
        }
        double sum = 0.0;
        for (int index = 0; index < size; index++) {
            sum += milliseconds[index];
        }
        return sum / size;
    }

    /** @return nearest-rank p95 frame time in milliseconds, or zero when empty */
    public double p95Milliseconds() {
        if (size == 0) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(milliseconds, size);
        Arrays.sort(sorted);
        int rank = (int) Math.ceil(sorted.length * 0.95);
        int index = Math.max(0, Math.min(sorted.length - 1, rank - 1));
        return sorted[index];
    }

    /** @return maximum retained frame time in milliseconds, or zero when empty */
    public double maxMilliseconds() {
        double max = 0.0;
        for (int index = 0; index < size; index++) {
            max = Math.max(max, milliseconds[index]);
        }
        return max;
    }
}
