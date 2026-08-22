package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MapCameraStateTest {
    @Test
    void wheelZoomPreservesCursorAnchorAndClamps() {
        MapCameraState camera = new MapCameraState();
        float cursorX = 740f;
        float cursorY = 420f;
        float centerX = 500f;
        float centerY = 300f;

        camera.zoomAt(-3f, cursorX, cursorY, centerX, centerY);
        float baseX = centerX + (cursorX - centerX - camera.panX()) / camera.zoom();
        float baseY = centerY + (cursorY - centerY - camera.panY()) / camera.zoom();
        assertEquals(cursorX, camera.transformX(baseX, centerX), 0.0001f);
        assertEquals(cursorY, camera.transformY(baseY, centerY), 0.0001f);

        camera.zoomAt(-10_000f, cursorX, cursorY, centerX, centerY);
        assertEquals(MapCameraState.MAX_ZOOM, camera.zoom());
        camera.zoomAt(10_000f, cursorX, cursorY, centerX, centerY);
        assertEquals(MapCameraState.MIN_ZOOM, camera.zoom());
    }

    @Test
    void middleDragAndFocusRemainPresentationOnly() {
        MapCameraState camera = new MapCameraState();
        camera.pan(80f, -35f);
        assertEquals(80f, camera.panX());
        assertEquals(-35f, camera.panY());

        camera.zoomAt(-2f, 500f, 300f, 500f, 300f);
        camera.focus(125f, 725f, 500f, 300f);
        assertEquals(500f, camera.transformX(125f, 500f), 0.0001f);
        assertEquals(300f, camera.transformY(725f, 300f), 0.0001f);
        assertTrue(camera.zoom() > 1f);

        camera.reset();
        assertEquals(1f, camera.zoom());
        assertEquals(0f, camera.panX());
        assertEquals(0f, camera.panY());
    }
}
