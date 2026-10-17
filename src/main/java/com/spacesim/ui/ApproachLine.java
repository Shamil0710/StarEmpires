package com.spacesim.ui;

import java.util.Locale;

/**
 * Approach readout from the player's active ship to the selected object on the system map.
 *
 * <p>All inputs come from the authoritative snapshot: the metre displacement to the target and the
 * ship's physical velocity. The closing speed is the velocity component toward the target; the
 * time is a straight-line estimate at that speed, labelled with "~", not a flight plan. The docking
 * hint repeats the ordinary docking limits (within 1 km, at most 1 m/s) without deciding whether a
 * dock is actually offered. Pure presentation arithmetic: it never reads or changes simulation
 * state.</p>
 */
final class ApproachLine {
    /** Ordinary docking range in metres. */
    static final double DOCK_RANGE_M = 1_000d;
    /** Ordinary docking speed limit in metres per second. */
    static final double DOCK_SPEED_MPS = 1d;
    private static final double MIN_CLOSING_MPS = 1e-3d;

    /** Docking hint for a station target. */
    enum DockHint {
        /** Target is not a station. */ NONE,
        /** Outside 1 km. */ OUT_OF_RANGE,
        /** Within 1 km but faster than 1 m/s. */ TOO_FAST,
        /** Within 1 km and at most 1 m/s. */ WITHIN_LIMITS
    }

    /**
     * Readout of one approach.
     *
     * @param distanceM straight-line distance to the target
     * @param closingMps velocity component toward the target; negative when moving away
     * @param etaSeconds straight-line time at the closing speed, or NaN when not closing
     * @param dockHint docking hint for a station target
     * @param label player-facing readout
     */
    record Readout(double distanceM, double closingMps, double etaSeconds, DockHint dockHint, String label) { }

    private ApproachLine() {
    }

    /**
     * @param dxM metre displacement from ship to target along X
     * @param dyM metre displacement from ship to target along Y
     * @param velocityXMps ship velocity along X
     * @param velocityYMps ship velocity along Y
     * @param stationTarget whether the target is a station (docking hint shown)
     * @return approach readout
     */
    static Readout of(double dxM, double dyM, double velocityXMps, double velocityYMps, boolean stationTarget) {
        if (!Double.isFinite(dxM) || !Double.isFinite(dyM)
                || !Double.isFinite(velocityXMps) || !Double.isFinite(velocityYMps)) {
            throw new IllegalArgumentException("approach inputs must be finite");
        }
        double distance = Math.hypot(dxM, dyM);
        double speed = Math.hypot(velocityXMps, velocityYMps);
        double closing = distance > 0d ? (velocityXMps * dxM + velocityYMps * dyM) / distance : 0d;
        double eta = closing > MIN_CLOSING_MPS ? distance / closing : Double.NaN;
        DockHint hint = !stationTarget ? DockHint.NONE
                : distance > DOCK_RANGE_M ? DockHint.OUT_OF_RANGE
                : speed > DOCK_SPEED_MPS ? DockHint.TOO_FAST : DockHint.WITHIN_LIMITS;
        StringBuilder label = new StringBuilder(MetricMapGrid.formatLength(distance));
        label.append("  •  ").append(Double.isNaN(eta) ? "не сближается" : "~" + formatDuration(eta));
        switch (hint) {
            case TOO_FAST -> label.append("  •  для стыковки сбросьте скорость до 1 м/с");
            case WITHIN_LIMITS -> label.append("  •  в пределах стыковки");
            default -> { }
        }
        return new Readout(distance, closing, eta, hint, label.toString());
    }

    /**
     * Shortens a screen segment so it runs between the two objects' on-screen edges instead of
     * across their hulls.
     *
     * @param x1 start centre X
     * @param y1 start centre Y
     * @param x2 end centre X
     * @param y2 end centre Y
     * @param startGap clearance of the start object
     * @param endGap clearance of the end object
     * @return trimmed segment {x1, y1, x2, y2}, or null when the objects' clearances meet
     */
    static float[] trim(float x1, float y1, float x2, float y2, float startGap, float endGap) {
        double dx = (double) x2 - x1;
        double dy = (double) y2 - y1;
        double length = Math.hypot(dx, dy);
        if (!(length > startGap + endGap) || !Double.isFinite(length)) return null;
        double ux = dx / length;
        double uy = dy / length;
        return new float[] {(float) (x1 + ux * startGap), (float) (y1 + uy * startGap),
                (float) (x2 - ux * endGap), (float) (y2 - uy * endGap)};
    }

    /**
     * Clips a screen segment to a box (Liang-Barsky), so a line to a far target draws only its
     * visible part.
     *
     * @param x1 start X
     * @param y1 start Y
     * @param x2 end X
     * @param y2 end Y
     * @param box clip box
     * @return clipped segment {x1, y1, x2, y2}, or null when no part is inside the box
     */
    static float[] clip(float x1, float y1, float x2, float y2, MapLabelLayout.Box box) {
        if (!Float.isFinite(x1) || !Float.isFinite(y1) || !Float.isFinite(x2) || !Float.isFinite(y2)) return null;
        double dx = (double) x2 - x1;
        double dy = (double) y2 - y1;
        double[] p = {-dx, dx, -dy, dy};
        // In double before subtracting: a start far off-screen at inspection zoom would lose the box.
        double[] q = {(double) x1 - box.x(), (double) box.right() - x1, (double) y1 - box.y(), (double) box.top() - y1};
        double enter = 0d;
        double exit = 1d;
        for (int index = 0; index < 4; index++) {
            if (p[index] == 0d) {
                if (q[index] < 0d) return null;
                continue;
            }
            double t = q[index] / p[index];
            if (p[index] < 0d) enter = Math.max(enter, t);
            else exit = Math.min(exit, t);
        }
        if (enter > exit) return null;
        return new float[] {(float) (x1 + enter * dx), (float) (y1 + enter * dy),
                (float) (x1 + exit * dx), (float) (y1 + exit * dy)};
    }

    /**
     * Places the readout plate beside the visible line: either side of its middle, then either side
     * of its quarter points, stepping further from the line when it runs under an overlay; the
     * plate stays clear of the fixed map overlays and inside the map.
     *
     * @param visible visible segment {x1, y1, x2, y2}
     * @param width plate width
     * @param height plate height
     * @param offset distance between the line and the plate's near edge
     * @param map map bounds
     * @param overlays fixed overlays to avoid (map title, scale bar)
     * @return plate box; the middle position clamped into the map when no clear position exists
     */
    static MapLabelLayout.Box placeReadout(float[] visible, float width, float height, float offset,
            MapLabelLayout.Box map, java.util.List<MapLabelLayout.Box> overlays) {
        float dx = visible[2] - visible[0];
        float dy = visible[3] - visible[1];
        float length = (float) Math.hypot(dx, dy);
        float normalX = length > 0f ? -dy / length : 0f;
        float normalY = length > 0f ? dx / length : 1f;
        if (normalY < 0f || normalY == 0f && normalX < 0f) {
            normalX = -normalX;
            normalY = -normalY;
        }
        MapLabelLayout.Box first = null;
        // Nearest first; further out only when a line runs under an overlay.
        for (float extra : new float[] {0f, 1.5f * height, 3f * height}) {
            float distance = offset + height / 2f + extra;
            for (float along : new float[] {0.5f, 0.25f, 0.75f}) {
                float px = visible[0] + dx * along;
                float py = visible[1] + dy * along;
                for (float side : new float[] {1f, -1f}) {
                    float x = clamp(px + side * normalX * distance - width / 2f, map.x(), map.right() - width);
                    float y = clamp(py + side * normalY * distance - height / 2f, map.y(), map.top() - height);
                    MapLabelLayout.Box plate = new MapLabelLayout.Box(x, y, width, height);
                    if (first == null) first = plate;
                    if (plate.inside(map) && overlays.stream().noneMatch(plate::overlaps)) return plate;
                }
            }
        }
        return first;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * @param seconds non-negative finite duration
     * @return {@code 43 с}, {@code 12 мин}, {@code 3 ч 20 мин} or {@code 5 сут}
     */
    static String formatDuration(double seconds) {
        if (!Double.isFinite(seconds) || seconds < 0d) {
            throw new IllegalArgumentException("duration must be finite and non-negative");
        }
        long total = Math.round(seconds);
        if (total < 60L) return total + " с";
        long minutes = Math.round(seconds / 60d);
        if (minutes < 60L) return minutes + " мин";
        long hours = minutes / 60L;
        if (hours < 48L) {
            long rest = minutes % 60L;
            return rest == 0L ? hours + " ч" : String.format(Locale.ROOT, "%d ч %d мин", hours, rest);
        }
        return Math.round(seconds / 86_400d) + " сут";
    }
}
