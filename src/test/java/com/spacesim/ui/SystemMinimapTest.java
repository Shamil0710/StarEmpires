package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SystemMinimapTest {
    private static final MapLabelLayout.Box MAP = new MapLabelLayout.Box(12f, 40f, 880f, 556f);
    private static final MapLabelLayout.Box FRAME = new MapLabelLayout.Box(40f, 68f, 824f, 500f);

    @Test
    void shownOnlyWhenZoomedIn() {
        assertFalse(SystemMinimap.shown(1f));
        assertFalse(SystemMinimap.shown(1.4f));
        assertTrue(SystemMinimap.shown(1.5f));
        assertFalse(SystemMinimap.shown(Float.NaN));
    }

    @Test
    void boxSitsTopRightWithTheFrameAspect() {
        var box = SystemMinimap.box(MAP, FRAME, 160f, 10f);
        assertEquals(160f, box.width(), 1e-4f);
        assertEquals(160f * 500f / 824f, box.height(), 1e-3f);
        assertEquals(MAP.right() - 10f, box.right(), 1e-4f);
        assertEquals(MAP.top() - 10f, box.top(), 1e-4f);

        var tall = SystemMinimap.box(MAP, new MapLabelLayout.Box(0f, 0f, 200f, 400f), 160f, 10f);
        assertEquals(160f, tall.height(), 1e-4f);
        assertEquals(80f, tall.width(), 1e-4f);
    }

    @Test
    void frameCornersMapToBoxCornersAndOutsidePointsClamp() {
        var box = SystemMinimap.box(MAP, FRAME, 160f, 10f);
        assertArrayEquals(new float[] {box.x(), box.y()}, SystemMinimap.toMinimap(FRAME.x(), FRAME.y(), FRAME, box), 1e-3f);
        assertArrayEquals(new float[] {box.right(), box.top()},
                SystemMinimap.toMinimap(FRAME.right(), FRAME.top(), FRAME, box), 1e-3f);
        // An object that drifted out of the fitted frame stays on the minimap edge.
        assertArrayEquals(new float[] {box.right(), box.top()},
                SystemMinimap.toMinimap(FRAME.right() + 500d, FRAME.top() + 900d, FRAME, box), 1e-3f);
    }

    @Test
    void cameraRectangleMatchesWhatTheRealCameraShows() {
        // The renderer transforms around the screen centre, not the map centre.
        float screenCx = 640f;
        float screenCy = 360f;
        var box = SystemMinimap.box(MAP, FRAME, 160f, 10f);
        var camera = new MapCameraState();
        camera.inspect(3.5d);
        camera.focus(300d, 250d, screenCx, screenCy);
        camera.pan(-75f, 40f);
        var rect = SystemMinimap.cameraRect(MAP, camera.zoom(), camera.panX(), camera.panY(),
                screenCx, screenCy, FRAME, box);
        assertTrue(rect != null);
        // Sample the fitted frame: a point is on screen exactly when its dot is inside the rectangle.
        for (double bx = FRAME.x(); bx <= FRAME.right(); bx += 37d) {
            for (double by = FRAME.y(); by <= FRAME.top(); by += 23d) {
                float sx = camera.transformX(bx, screenCx);
                float sy = camera.transformY(by, screenCy);
                boolean onScreen = sx > MAP.x() + 0.5f && sx < MAP.right() - 0.5f
                        && sy > MAP.y() + 0.5f && sy < MAP.top() - 0.5f;
                boolean offScreen = sx < MAP.x() - 0.5f || sx > MAP.right() + 0.5f
                        || sy < MAP.y() - 0.5f || sy > MAP.top() + 0.5f;
                float[] dot = SystemMinimap.toMinimap(bx, by, FRAME, box);
                boolean inRect = dot[0] >= rect.x() - 1e-3f && dot[0] <= rect.right() + 1e-3f
                        && dot[1] >= rect.y() - 1e-3f && dot[1] <= rect.top() + 1e-3f;
                if (onScreen) assertTrue(inRect, "visible point " + bx + "," + by + " outside the camera rectangle");
                if (offScreen) assertFalse(inRect, "hidden point " + bx + "," + by + " inside the camera rectangle");
            }
        }
    }

    @Test
    void cameraRectangleShrinksWithZoomAndMovesAgainstPan() {
        var box = SystemMinimap.box(MAP, FRAME, 160f, 10f);
        double cx = MAP.x() + MAP.width() / 2d;
        double cy = MAP.y() + MAP.height() / 2d;
        // Zoom 1: the viewport covers the whole frame (clipped to the box).
        var whole = SystemMinimap.cameraRect(MAP, 1d, 0d, 0d, cx, cy, FRAME, box);
        assertEquals(box.x(), whole.x(), 1e-3f);
        assertEquals(box.y(), whole.y(), 1e-3f);
        assertEquals(box.width(), whole.width(), 1e-3f);
        assertEquals(box.height(), whole.height(), 1e-3f);

        // Zoom 4 around the centre: a quarter of the viewport, centred.
        var zoomed = SystemMinimap.cameraRect(MAP, 4d, 0d, 0d, cx, cy, FRAME, box);
        double scale = box.width() / (double) FRAME.width();
        assertEquals(MAP.width() / 4d * scale, zoomed.width(), 1e-3);
        float[] centre = SystemMinimap.toMinimap(cx, cy, FRAME, box);
        assertEquals(centre[0], zoomed.x() + zoomed.width() / 2f, 1e-3f);
        assertEquals(centre[1], zoomed.y() + zoomed.height() / 2f, 1e-3f);

        // Panning the map right shows what lies left: the rectangle moves left.
        var panned = SystemMinimap.cameraRect(MAP, 4d, 200d, 0d, cx, cy, FRAME, box);
        assertTrue(panned.x() < zoomed.x());
        assertEquals(200d / 4d * scale, zoomed.x() - panned.x(), 1e-3);

        // Looking far outside the frame: no rectangle.
        assertNull(SystemMinimap.cameraRect(MAP, 4d, 1e6d, 0d, cx, cy, FRAME, box));
        assertNull(SystemMinimap.cameraRect(MAP, 0d, 0d, 0d, cx, cy, FRAME, box));
    }
}
