package com.spacesim.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Layout of the command toolbar and the header time control.
 *
 * <p>The toolbar holds secondary map/list tools in three groups: navigation back, list tools
 * (search, sort, filter, density) and the right-aligned return to the active ship. Session
 * commands (pause, speed) live in the header time control; save and load live in the menu.
 * Pure geometry: it never reads or changes simulation state.</p>
 */
final class CommandBarLayout {
    /** Toolbar command ids in drawing order. */
    static final List<String> TOOLBAR_IDS =
            List.of("back", "search", "sort", "filter", "density", "focus-player");
    /** Header time-control command ids in drawing order. */
    static final List<String> TIME_IDS =
            List.of("pause", "speed-1", "speed-2", "speed-4", "speed-8");

    private static final List<String> TIME_LABELS = List.of("II", "1x", "2x", "4x", "8x");
    private static final double[] SPEEDS = {1d, 2d, 4d, 8d};

    /**
     * One laid-out control.
     *
     * @param id command id
     * @param x left edge
     * @param y bottom edge
     * @param width width
     * @param height height
     */
    record Slot(String id, float x, float y, float width, float height) {
        float right() {
            return x + width;
        }
    }

    /**
     * One header time-control segment.
     *
     * @param slot geometry and command id
     * @param label segment label
     * @param active true when the segment shows the current session state
     */
    record TimeSegment(Slot slot, String label, boolean active) { }

    private static final float TAB_ROW_HEIGHT = 25f;
    private static final int TAB_ROWS = 2;
    private static final float SEGMENT_WIDTH = 34f;

    private CommandBarLayout() {
    }

    /**
     * @param metrics resolved UI metrics
     * @return height of one tab row in the top bar
     */
    static float tabRowHeight(ResponsiveUiMetrics metrics) {
        return TAB_ROW_HEIGHT * metrics.scale();
    }

    /**
     * @param viewportHeight logical viewport height
     * @param metrics resolved UI metrics
     * @return top edge of the upper tab row's hit target
     */
    static float tabRowsTop(float viewportHeight, ResponsiveUiMetrics metrics) {
        float topY = viewportHeight - metrics.topBarHeight();
        return topY + TAB_ROWS * tabRowHeight(metrics) - 2f * metrics.scale();
    }

    /**
     * Lays out the time control in the header strip above the tab rows.
     *
     * @param viewportWidth logical viewport width
     * @param viewportHeight logical viewport height
     * @param metrics resolved UI metrics
     * @param paused whether the simulation is paused
     * @param timeScale current simulation time scale
     * @return segments ending at the right margin, vertically centred in the header strip
     */
    static List<TimeSegment> headerTimeControl(float viewportWidth, float viewportHeight,
            ResponsiveUiMetrics metrics, boolean paused, double timeScale) {
        float scale = metrics.scale();
        float stripHeight = metrics.topBarHeight() - TAB_ROWS * tabRowHeight(metrics);
        float segmentHeight = Math.max(12f, stripHeight - 4f * scale);
        float y = viewportHeight - stripHeight + (stripHeight - segmentHeight) / 2f;
        return timeControl(viewportWidth - metrics.outerMargin(), y, SEGMENT_WIDTH * scale,
                segmentHeight, paused, timeScale);
    }

    /**
     * @param viewportWidth logical viewport width
     * @param metrics resolved UI metrics
     * @return left edge of the header time control, where header text must end
     */
    static float headerTimeControlLeft(float viewportWidth, ResponsiveUiMetrics metrics) {
        return viewportWidth - metrics.outerMargin() - SEGMENT_WIDTH * metrics.scale() * TIME_IDS.size();
    }

    /**
     * Lays out the toolbar groups between two edges.
     *
     * @param left left edge
     * @param right right edge
     * @param y bottom edge of the row
     * @param height row height
     * @param preferredWidth preferred button width
     * @param gap gap between neighbouring buttons
     * @param groupGap extra gap between groups
     * @return slots in {@link #TOOLBAR_IDS} order; buttons shrink when the row is narrow
     */
    static List<Slot> toolbar(float left, float right, float y, float height,
            float preferredWidth, float gap, float groupGap) {
        if (!(right > left) || !(height > 0f) || !(preferredWidth > 0f) || gap < 0f || groupGap < 0f) {
            throw new IllegalArgumentException("toolbar geometry must be positive");
        }
        int count = TOOLBAR_IDS.size();
        float available = right - left - 2f * groupGap - (count - 1) * gap;
        float width = Math.max(1f, Math.min(preferredWidth, available / count));
        List<Slot> slots = new ArrayList<>(count);
        slots.add(new Slot("back", left, y, width, height));
        float x = left + width + gap + groupGap;
        for (int index = 1; index < count - 1; index++) {
            slots.add(new Slot(TOOLBAR_IDS.get(index), x, y, width, height));
            x += width + gap;
        }
        slots.add(new Slot("focus-player", right - width, y, width, height));
        return List.copyOf(slots);
    }

    /**
     * Lays out the header time control ending at the right edge.
     *
     * @param right right edge
     * @param y bottom edge
     * @param segmentWidth width of one segment
     * @param height segment height
     * @param paused whether the simulation is paused
     * @param timeScale current simulation time scale
     * @return segments in {@link #TIME_IDS} order; exactly one is active unless the scale is
     *         not one of the offered speeds
     */
    static List<TimeSegment> timeControl(float right, float y, float segmentWidth, float height,
            boolean paused, double timeScale) {
        if (!(segmentWidth > 0f) || !(height > 0f)) {
            throw new IllegalArgumentException("time control geometry must be positive");
        }
        String active = activeTimeId(paused, timeScale);
        float x = right - segmentWidth * TIME_IDS.size();
        List<TimeSegment> segments = new ArrayList<>(TIME_IDS.size());
        for (int index = 0; index < TIME_IDS.size(); index++) {
            String id = TIME_IDS.get(index);
            segments.add(new TimeSegment(new Slot(id, x, y, segmentWidth, height),
                    TIME_LABELS.get(index), id.equals(active)));
            x += segmentWidth;
        }
        return List.copyOf(segments);
    }

    /**
     * @param paused whether the simulation is paused
     * @param timeScale current simulation time scale
     * @return id of the segment showing the current state, or an empty string
     */
    static String activeTimeId(boolean paused, double timeScale) {
        if (paused) {
            return "pause";
        }
        for (int index = 0; index < SPEEDS.length; index++) {
            if (SPEEDS[index] == timeScale) {
                return TIME_IDS.get(index + 1);
            }
        }
        return "";
    }
}
