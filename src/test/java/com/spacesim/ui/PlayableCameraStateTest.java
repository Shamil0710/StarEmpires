package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayableCameraStateTest {
    @Test
    void scrollIsBoundedAndResetRestoresFollowDefault() {
        PlayableCameraState camera = new PlayableCameraState();

        camera.scroll(-100f);
        assertEquals(WorldMapLayout.MAX_ZOOM, camera.zoom());

        camera.scroll(100f);
        assertEquals(WorldMapLayout.MIN_ZOOM, camera.zoom());

        camera.reset();
        assertEquals(PlayableCameraState.DEFAULT_ZOOM, camera.zoom());
    }

    @Test
    void invalidAbsoluteZoomIsRejected() {
        PlayableCameraState camera = new PlayableCameraState(2f);

        assertThrows(IllegalArgumentException.class, () -> camera.setZoom(0f));
        assertThrows(IllegalArgumentException.class, () -> camera.setZoom(Float.NaN));
    }
}
