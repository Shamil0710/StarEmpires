package com.spacesim.ui;

/**
 * Resolution-independent presentation camera applied after authoritative map projection.
 *
 * <p>The camera changes only screen-space scale and offset. It never writes to simulation
 * transforms or Stage-20 physical coordinates.</p>
 */
public final class MapCameraState {
    /** Minimum useful overview zoom. */
    public static final float MIN_ZOOM = 0.60f;
    /** Maximum inspection zoom. */
    public static final float MAX_ZOOM = 12f;
    private static final float WHEEL_FACTOR = 1.18f;

    private float zoom = 1f;
    private float panX;
    private float panY;

    /** Creates a fitted overview camera with unit zoom and zero pan. */
    public MapCameraState() {
    }

    /** @return current bounded presentation zoom */
    public float zoom() {
        return zoom;
    }

    /** @return current horizontal screen-space pan */
    public float panX() {
        return panX;
    }

    /** @return current vertical screen-space pan */
    public float panY() {
        return panY;
    }

    /** Restores the fitted overview. */
    public void reset() {
        zoom = 1f;
        panX = 0f;
        panY = 0f;
    }

    /**
     * Pans the rendered map by a finite logical-pixel displacement.
     *
     * @param deltaX horizontal drag displacement
     * @param deltaY vertical drag displacement
     */
    public void pan(float deltaX, float deltaY) {
        requireFinite(deltaX, "deltaX");
        requireFinite(deltaY, "deltaY");
        panX += deltaX;
        panY += deltaY;
    }

    /**
     * Applies wheel zoom while preserving the map point beneath the cursor.
     *
     * @param amountY LibGDX vertical wheel amount; negative zooms in
     * @param cursorX cursor X in UI coordinates
     * @param cursorY cursor Y in UI coordinates
     * @param centerX map viewport center X
     * @param centerY map viewport center Y
     */
    public void zoomAt(
            float amountY,
            float cursorX,
            float cursorY,
            float centerX,
            float centerY) {
        requireFinite(amountY, "amountY");
        requireFinite(cursorX, "cursorX");
        requireFinite(cursorY, "cursorY");
        float previous = zoom;
        float requested = (float) (previous * Math.pow(WHEEL_FACTOR, -amountY));
        zoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, requested));
        float ratio = zoom / previous;
        panX = cursorX - centerX - (cursorX - centerX - panX) * ratio;
        panY = cursorY - centerY - (cursorY - centerY - panY) * ratio;
    }

    /**
     * Centers one already fitted base-projection point in the viewport.
     *
     * @param pointX base-projection point X
     * @param pointY base-projection point Y
     * @param centerX viewport center X
     * @param centerY viewport center Y
     */
    public void focus(float pointX, float pointY, float centerX, float centerY) {
        requireFinite(pointX, "pointX");
        requireFinite(pointY, "pointY");
        panX = -(pointX - centerX) * zoom;
        panY = -(pointY - centerY) * zoom;
    }

    /**
     * @param pointX fitted base-projection X
     * @param centerX viewport center X
     * @return transformed screen X for one fitted base-projection point
     */
    public float transformX(float pointX, float centerX) {
        return centerX + (pointX - centerX) * zoom + panX;
    }

    /**
     * @param pointY fitted base-projection Y
     * @param centerY viewport center Y
     * @return transformed screen Y for one fitted base-projection point
     */
    public float transformY(float pointY, float centerY) {
        return centerY + (pointY - centerY) * zoom + panY;
    }

    private static void requireFinite(float value, String label) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
