package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CommandBarLayoutTest {
    @Test
    void toolbarHoldsOnlyMapAndListToolsInThreeGroups() {
        var slots = CommandBarLayout.toolbar(16f, 1264f, 600f, 28f, 136f, 4f, 24f);
        assertEquals(List.of("back", "search", "sort", "filter", "density", "focus-player"),
                slots.stream().map(CommandBarLayout.Slot::id).toList());
        assertFalse(slots.stream().anyMatch(slot -> List.of("pause", "save", "load").contains(slot.id())),
                "session commands moved to the header and the menu");

        assertEquals(16f, slots.get(0).x());
        assertEquals(slots.get(0).right() + 4f + 24f, slots.get(1).x(), 1e-4f);
        assertEquals(slots.get(1).right() + 4f, slots.get(2).x(), 1e-4f);
        assertEquals(1264f, slots.get(5).right(), 1e-4f);
        assertTrue(slots.get(5).x() - slots.get(4).right() > 24f, "ship return stands apart");
        slots.forEach(slot -> assertEquals(136f, slot.width()));
    }

    @Test
    void narrowToolbarShrinksWithoutOverlap() {
        var slots = CommandBarLayout.toolbar(10f, 410f, 0f, 24f, 136f, 4f, 16f);
        for (int index = 1; index < slots.size(); index++) {
            assertTrue(slots.get(index).x() >= slots.get(index - 1).right(),
                    slots.get(index).id() + " overlaps its neighbour");
        }
        assertEquals(410f, slots.get(5).right(), 1e-4f);
        assertTrue(slots.get(0).width() < 136f);
        assertThrows(IllegalArgumentException.class,
                () -> CommandBarLayout.toolbar(10f, 10f, 0f, 24f, 136f, 4f, 16f));
    }

    @Test
    void timeControlShowsExactlyTheCurrentSessionState() {
        var paused = CommandBarLayout.timeControl(1264f, 700f, 32f, 18f, true, 4d);
        assertEquals(List.of("pause", "speed-1", "speed-2", "speed-4", "speed-8"),
                paused.stream().map(segment -> segment.slot().id()).toList());
        assertEquals(List.of("II", "1x", "2x", "4x", "8x"),
                paused.stream().map(CommandBarLayout.TimeSegment::label).toList());
        assertEquals(List.of(true, false, false, false, false),
                paused.stream().map(CommandBarLayout.TimeSegment::active).toList());
        assertEquals(1264f, paused.get(4).slot().right(), 1e-4f);
        assertEquals(1264f - 5 * 32f, paused.get(0).slot().x(), 1e-4f);

        var running = CommandBarLayout.timeControl(1264f, 700f, 32f, 18f, false, 4d);
        assertEquals(List.of(false, false, false, true, false),
                running.stream().map(CommandBarLayout.TimeSegment::active).toList());
        // A scale outside the offered speeds (for example during a time skip) marks nothing.
        assertTrue(CommandBarLayout.timeControl(1264f, 700f, 32f, 18f, false, 3d).stream()
                .noneMatch(CommandBarLayout.TimeSegment::active));
    }

    @Test
    void headerTimeControlFitsAboveTheTabsAtEveryUiScale() {
        int[][] viewports = {{1280, 720}, {1600, 900}, {1920, 1080}, {3200, 1800}, {3840, 2160}};
        for (int[] viewport : viewports) {
            var metrics = ResponsiveUiMetrics.resolve(viewport[0], viewport[1], 1f);
            String label = viewport[0] + "x" + viewport[1] + " scale " + metrics.scale();
            var segments = CommandBarLayout.headerTimeControl(viewport[0], viewport[1], metrics, false, 1d);
            float tabsTop = CommandBarLayout.tabRowsTop(viewport[1], metrics);
            float left = CommandBarLayout.headerTimeControlLeft(viewport[0], metrics);
            assertEquals(left, segments.get(0).slot().x(), 1e-3f, label);
            assertEquals(viewport[0] - metrics.outerMargin(), segments.get(4).slot().right(), 1e-3f, label);
            for (var segment : segments) {
                var slot = segment.slot();
                assertTrue(slot.y() > tabsTop, label + ": " + slot.id() + " overlaps the tab rows");
                assertTrue(slot.y() + slot.height() <= viewport[1], label + ": " + slot.id() + " leaves the screen");
                assertTrue(slot.height() >= 12f, label + ": " + slot.id() + " is too small to hit");
            }
            // Header text (system and tick) keeps room between 35 % of the width and the control.
            assertTrue(left - 12f * metrics.scale() - viewport[0] * 0.35f > 200f * metrics.scale(), label);
        }
    }

    @Test
    void speedSegmentsMatchTheKeyboardSpeeds() {
        // Keys 1/2/3/4 select 1x/2x/4x/8x; the header highlights the same segment.
        assertEquals("speed-1", CommandBarLayout.activeTimeId(false, 1d));
        assertEquals("speed-2", CommandBarLayout.activeTimeId(false, 2d));
        assertEquals("speed-4", CommandBarLayout.activeTimeId(false, 4d));
        assertEquals("speed-8", CommandBarLayout.activeTimeId(false, 8d));
        assertEquals("pause", CommandBarLayout.activeTimeId(true, 8d));
    }
}
