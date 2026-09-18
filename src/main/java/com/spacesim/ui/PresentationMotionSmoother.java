package com.spacesim.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Frame-rate-independent presentation smoothing for fixed-tick local-object positions.
 *
 * <p>This class never writes back to simulation state. It only eases the rendered projection
 * toward each newest authoritative point, removing fixed-tick stepping without inventing gameplay
 * motion or changing physical scale.</p>
 */
final class PresentationMotionSmoother {
    private static final double RESPONSE_PER_SECOND = 16d;

    private final Map<String, Point> positions = new HashMap<>();

    /**
     * Advances one stable object's displayed point toward its newest authoritative projection.
     *
     * @param stableId stable presentation identity
     * @param targetX newest projected X
     * @param targetY newest projected Y
     * @param deltaSeconds presentation-frame delta
     * @return smoothed display point
     */
    Point update(String stableId, double targetX, double targetY, float deltaSeconds) {
        if (stableId == null || stableId.isBlank()) {
            throw new IllegalArgumentException("stableId must be present");
        }
        requireFinite(targetX, "targetX");
        requireFinite(targetY, "targetY");
        requireFinite(deltaSeconds, "deltaSeconds");
        if (deltaSeconds < 0f) {
            throw new IllegalArgumentException("deltaSeconds cannot be negative");
        }

        Point previous = positions.get(stableId);
        if (previous == null) {
            Point initial = new Point(targetX, targetY);
            positions.put(stableId, initial);
            return initial;
        }
        if (deltaSeconds == 0f) {
            return previous;
        }

        double alpha = 1d - Math.exp(-RESPONSE_PER_SECOND * deltaSeconds);
        Point next = new Point(
                previous.x() + (targetX - previous.x()) * alpha,
                previous.y() + (targetY - previous.y()) * alpha);
        positions.put(stableId, next);
        return next;
    }

    /**
     * Returns the current displayed point without advancing interpolation.
     *
     * @param stableId stable presentation identity
     * @return current displayed point, or {@code null} when no history exists
     */
    Point current(String stableId) {
        return positions.get(stableId);
    }

    /**
     * Removes presentation state for objects no longer present in the active local view.
     *
     * @param stableIds currently visible stable identities
     */
    void retain(Set<String> stableIds) {
        positions.keySet().retainAll(stableIds);
    }

    /** Clears all interpolation history, for example after a projection re-fit. */
    void reset() {
        positions.clear();
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    /** Immutable presentation-only point. */
    record Point(double x, double y) { }
}
