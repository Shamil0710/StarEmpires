package com.spacesim.ui;

import java.util.Objects;

/**
 * Presentation-only camera state for the Stage-19J tactical validation map.
 *
 * <p>The controller delegates all coordinate rules, zoom limits and world-bound clamping to
 * {@link WorldMapLayout}. It owns no tactical simulation state.</p>
 */
public final class TacticalCameraController {
    private final float paddingPx;
    private WorldMapLayout layout;

    /**
     * Creates a camera controller with the requested map padding.
     *
     * @param paddingPx non-negative map padding in screen pixels
     */
    public TacticalCameraController(float paddingPx) {
        if (!Float.isFinite(paddingPx) || paddingPx < 0f) {
            throw new IllegalArgumentException("paddingPx must be finite and non-negative");
        }
        this.paddingPx = paddingPx;
    }

    /**
     * Creates or resizes the tactical map while preserving current center/zoom where possible.
     *
     * @param mapWidthPx positive tactical-map column width
     * @param mapHeightPx positive tactical-map height
     */
    public void resize(float mapWidthPx, float mapHeightPx) {
        if (!Float.isFinite(mapWidthPx) || mapWidthPx <= 0f
                || !Float.isFinite(mapHeightPx) || mapHeightPx <= 0f) {
            throw new IllegalArgumentException("map dimensions must be finite and positive");
        }
        if (layout == null) {
            layout = new WorldMapLayout(
                    0f,
                    0f,
                    mapWidthPx,
                    mapHeightPx,
                    paddingPx,
                    WorldMapLayout.WORLD_WIDTH * 0.5f,
                    WorldMapLayout.WORLD_HEIGHT * 0.5f,
                    WorldMapLayout.MIN_ZOOM);
        } else {
            layout = layout.resize(0f, 0f, mapWidthPx, mapHeightPx, paddingPx);
        }
    }

    /**
     * Applies a libGDX scroll step around the current cursor position when it is over the map.
     *
     * @param screenX bottom-left-origin screen x coordinate
     * @param screenY bottom-left-origin screen y coordinate
     * @param scrollAmount vertical libGDX scroll amount; negative zooms in
     */
    public void zoomByScroll(float screenX, float screenY, float scrollAmount) {
        requireLayout();
        layout = layout.zoomByScroll(screenX, screenY, scrollAmount);
    }

    /**
     * Pans the map by a screen-space pointer delta.
     *
     * @param screenDeltaX horizontal pointer movement in screen pixels
     * @param screenDeltaY vertical pointer movement in screen pixels
     */
    public void panByScreen(float screenDeltaX, float screenDeltaY) {
        requireLayout();
        layout = layout.panByScreen(screenDeltaX, screenDeltaY);
    }

    /** Restores centered full-world presentation framing without changing simulation state. */
    public void resetView() {
        WorldMapLayout current = requireLayout();
        layout = new WorldMapLayout(
                current.getX(),
                current.getY(),
                current.getWidth(),
                current.getHeight(),
                current.getPadding(),
                WorldMapLayout.WORLD_WIDTH * 0.5f,
                WorldMapLayout.WORLD_HEIGHT * 0.5f,
                WorldMapLayout.MIN_ZOOM);
    }

    /**
     * Returns the current immutable world/screen mapping.
     *
     * @return current tactical map layout
     */
    public WorldMapLayout layout() {
        return requireLayout();
    }

    private WorldMapLayout requireLayout() {
        return Objects.requireNonNull(layout, "tactical camera must be resized before use");
    }
}
