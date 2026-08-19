package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TacticalCameraControllerTest {
    @Test
    void scrollZoomAndPanReuseWorldMapLayoutRules() {
        TacticalCameraController camera = new TacticalCameraController(28f);
        camera.resize(900f, 700f);
        WorldMapLayout initial = camera.layout();
        float cursorX = initial.getMapX() + initial.getMapWidth() * 0.70f;
        float cursorY = initial.getMapY() + initial.getMapHeight() * 0.45f;

        camera.zoomByScroll(cursorX, cursorY, -2f);
        WorldMapLayout zoomed = camera.layout();
        assertTrue(zoomed.getZoom() > WorldMapLayout.MIN_ZOOM);
        float centerBeforePanX = zoomed.getCenterWorldX();
        float centerBeforePanY = zoomed.getCenterWorldY();

        camera.panByScreen(80f, -45f);
        WorldMapLayout panned = camera.layout();
        assertTrue(panned.getZoom() > WorldMapLayout.MIN_ZOOM);
        assertTrue(centerBeforePanX != panned.getCenterWorldX()
                || centerBeforePanY != panned.getCenterWorldY());
    }

    @Test
    void resizePreservesCameraStateAndResetReturnsToFullView() {
        TacticalCameraController camera = new TacticalCameraController(28f);
        camera.resize(900f, 700f);
        WorldMapLayout initial = camera.layout();
        camera.zoomByScroll(
                initial.getMapX() + initial.getMapWidth() * 0.65f,
                initial.getMapY() + initial.getMapHeight() * 0.35f,
                -3f);
        camera.panByScreen(55f, 35f);
        WorldMapLayout beforeResize = camera.layout();

        camera.resize(1120f, 760f);
        WorldMapLayout afterResize = camera.layout();
        assertEquals(beforeResize.getZoom(), afterResize.getZoom(), 1e-6f);
        assertEquals(beforeResize.getCenterWorldX(), afterResize.getCenterWorldX(), 1e-3f);
        assertEquals(beforeResize.getCenterWorldY(), afterResize.getCenterWorldY(), 1e-3f);
        assertNotEquals(beforeResize.getWidth(), afterResize.getWidth());

        camera.resetView();
        assertEquals(WorldMapLayout.MIN_ZOOM, camera.layout().getZoom(), 1e-6f);
        assertEquals(WorldMapLayout.WORLD_WIDTH * 0.5f, camera.layout().getCenterWorldX(), 1e-3f);
        assertEquals(WorldMapLayout.WORLD_HEIGHT * 0.5f, camera.layout().getCenterWorldY(), 1e-3f);
    }
}
