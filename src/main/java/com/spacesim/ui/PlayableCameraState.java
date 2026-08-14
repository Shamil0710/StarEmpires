package com.spacesim.ui;

/**
 * Small presentation-only state holder for the Stage-14C player-follow camera.
 *
 * <p>The value is deliberately independent from Scene2D HUD scaling and never mutates simulation
 * state. The active ship remains the world-space camera center; wheel input changes only the
 * bounded world zoom used to build {@link WorldMapLayout}.</p>
 */
public final class PlayableCameraState {
    /** Default gameplay zoom carried forward from the Stage-12 test harness. */
    public static final float DEFAULT_ZOOM = 2.4f;
    /** One wheel notch uses the same multiplicative step as the existing map interaction model. */
    public static final float ZOOM_STEP = WorldMapLayout.ZOOM_STEP;

    private float zoom;

    /** Creates the default bounded gameplay camera state. */
    public PlayableCameraState() {
        this(DEFAULT_ZOOM);
    }

    /**
     * Creates a bounded camera state.
     *
     * @param initialZoom finite positive requested zoom
     */
    public PlayableCameraState(float initialZoom) {
        setZoom(initialZoom);
    }

    /** @return current world zoom in the shared WorldMapLayout range */
    public float zoom() {
        return zoom;
    }

    /**
     * Applies libGDX vertical scroll convention: negative values zoom in, positive values zoom out.
     *
     * @param scrollAmount vertical wheel amount
     * @return resulting bounded zoom
     */
    public float scroll(float scrollAmount) {
        if (!Float.isFinite(scrollAmount) || scrollAmount == 0f) {
            return zoom;
        }
        double exponent = Math.max(-32d, Math.min(32d, -scrollAmount));
        double target = zoom * Math.pow(ZOOM_STEP, exponent);
        setZoom((float) target);
        return zoom;
    }

    /**
     * Sets an absolute zoom and clamps it to the shared map limits.
     *
     * @param requestedZoom finite positive requested zoom
     * @return resulting bounded zoom
     * @throws IllegalArgumentException if the value is non-finite or non-positive
     */
    public float setZoom(float requestedZoom) {
        if (!Float.isFinite(requestedZoom) || requestedZoom <= 0f) {
            throw new IllegalArgumentException("Playable camera zoom must be finite and positive");
        }
        zoom = Math.max(WorldMapLayout.MIN_ZOOM, Math.min(WorldMapLayout.MAX_ZOOM, requestedZoom));
        return zoom;
    }

    /** Restores the standard follow zoom without affecting the followed FleetId. */
    public void reset() {
        zoom = DEFAULT_ZOOM;
    }
}
