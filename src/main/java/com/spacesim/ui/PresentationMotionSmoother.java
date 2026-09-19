package com.spacesim.ui;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Presentation interpolation for authoritative fixed-tick local-object positions.
 *
 * <p>This class never writes back to simulation state. It retains the previous and current
 * authoritative projected samples and uses the active {@code SimulationClock} interpolation alpha
 * to draw the in-between point. Unlike exponential target chasing, constant fixed-tick motion
 * therefore keeps a constant presentation velocity instead of repeatedly accelerating and
 * decelerating between ticks.</p>
 */
final class PresentationMotionSmoother {
    private final Map<String, MotionState> states = new HashMap<>();

    /**
     * Resolves one stable object's presentation point from the authoritative fixed-tick samples.
     *
     * @param stableId stable presentation identity
     * @param authoritativeTick current accepted world tick
     * @param interpolationAlpha active clock fraction accumulated toward the next fixed tick
     * @param targetX newest authoritative projected X
     * @param targetY newest authoritative projected Y
     * @return interpolated display point
     */
    Point update(
            String stableId,
            long authoritativeTick,
            double interpolationAlpha,
            double targetX,
            double targetY) {
        if (stableId == null || stableId.isBlank()) {
            throw new IllegalArgumentException("stableId must be present");
        }
        if (authoritativeTick < 0L) {
            throw new IllegalArgumentException("authoritativeTick cannot be negative");
        }
        requireFinite(interpolationAlpha, "interpolationAlpha");
        if (interpolationAlpha < 0d || interpolationAlpha > 1d) {
            throw new IllegalArgumentException("interpolationAlpha must be in [0,1]");
        }
        requireFinite(targetX, "targetX");
        requireFinite(targetY, "targetY");

        Point target = new Point(targetX, targetY);
        MotionState state = states.get(stableId);
        if (state == null || authoritativeTick < state.authoritativeTick) {
            MotionState reset = new MotionState(authoritativeTick, target, target, target);
            states.put(stableId, reset);
            return target;
        }

        if (authoritativeTick > state.authoritativeTick) {
            state.previous = state.current;
            state.current = target;
            state.authoritativeTick = authoritativeTick;
        } else if (!samePoint(state.current, target)) {
            // A same-tick replacement is a discontinuity outside ordinary fixed-step movement
            // (for example materialization or an externally replaced projection). Do not invent
            // an in-between physical path for it.
            state.previous = target;
            state.current = target;
            state.displayed = target;
            return target;
        }

        Point displayed = interpolate(state.previous, state.current, interpolationAlpha);
        state.displayed = displayed;
        return displayed;
    }

    /**
     * Returns the point used on the most recent presentation frame without advancing anything.
     *
     * @param stableId stable presentation identity
     * @return current displayed point, or {@code null} when no history exists
     */
    Point current(String stableId) {
        MotionState state = states.get(stableId);
        return state == null ? null : state.displayed;
    }

    /**
     * Removes presentation state for objects no longer present in the active local view.
     *
     * @param stableIds currently visible stable identities
     */
    void retain(Set<String> stableIds) {
        states.keySet().retainAll(stableIds);
    }

    /** Clears all interpolation history, for example after a projection re-fit. */
    void reset() {
        states.clear();
    }

    private static Point interpolate(Point previous, Point current, double alpha) {
        return new Point(
                previous.x() + (current.x() - previous.x()) * alpha,
                previous.y() + (current.y() - previous.y()) * alpha);
    }

    private static boolean samePoint(Point first, Point second) {
        return Double.doubleToLongBits(first.x()) == Double.doubleToLongBits(second.x())
                && Double.doubleToLongBits(first.y()) == Double.doubleToLongBits(second.y());
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }

    /** Immutable presentation-only point. */
    record Point(double x, double y) { }

    private static final class MotionState {
        private long authoritativeTick;
        private Point previous;
        private Point current;
        private Point displayed;

        private MotionState(long authoritativeTick, Point previous, Point current, Point displayed) {
            this.authoritativeTick = authoritativeTick;
            this.previous = previous;
            this.current = current;
            this.displayed = displayed;
        }
    }
}
