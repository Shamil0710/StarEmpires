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
    public static final float MAX_ZOOM = 1.0e12f;
    private static final float WHEEL_FACTOR = 1.18f;

    private final float maximumZoom;
    private float zoom = 1f;
    private double panX;
    private double panY;

    /** Creates a fitted overview camera with unit zoom and zero pan. */
    public MapCameraState() {
        this(MAX_ZOOM);
    }

    MapCameraState(float maximumZoom) {
        requireFinite(maximumZoom, "maximumZoom");
        if (maximumZoom < 1f) throw new IllegalArgumentException("maximum zoom below overview");
        this.maximumZoom = maximumZoom;
    }

    /** @return current bounded presentation zoom */
    public float zoom() {
        return zoom;
    }

    /** @return current horizontal screen-space pan */
    public float panX() {
        return (float) panX;
    }

    /** @return current vertical screen-space pan */
    public float panY() {
        return (float) panY;
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
        zoom = Math.max(MIN_ZOOM, Math.min(maximumZoom, requested));
        double ratio = (double) zoom / previous;
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
    public void focus(double pointX, double pointY, float centerX, float centerY) {
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
    public float transformX(double pointX, float centerX) {
        return (float) (centerX + (pointX - centerX) * zoom + panX);
    }

    /**
     * @param pointY fitted base-projection Y
     * @param centerY viewport center Y
     * @return transformed screen Y for one fitted base-projection point
     */
    public float transformY(double pointY, float centerY) {
        return (float) (centerY + (pointY - centerY) * zoom + panY);
    }

    /** Sets an inspection zoom before focusing a physical object.
     * @param requested positive pixels-per-metre-derived zoom
     */
    public void inspect(double requested) {
        requireFinite(requested, "requested");
        if (requested <= 0d) throw new IllegalArgumentException("zoom must be positive");
        zoom = (float) Math.max(MIN_ZOOM, Math.min(maximumZoom, requested));
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(label + " must be finite");
        }
    }
}
