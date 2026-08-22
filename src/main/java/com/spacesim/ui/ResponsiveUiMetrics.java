package com.spacesim.ui;

/** Deterministic resolution-aware typography and panel geometry policy for the desktop UI. */
@SuppressWarnings("doclint:missing")
public record ResponsiveUiMetrics(
        float scale,
        int titleFontPixels,
        int bodyFontPixels,
        int smallFontPixels,
        float outerMargin,
        float topBarHeight,
        float statusBarHeight,
        float inspectorWidth,
        float listWidth,
        float markerSize,
        float hitRadius) {
    private static final float REFERENCE_WIDTH = 1600f;
    private static final float REFERENCE_HEIGHT = 900f;

    /** Validates a complete positive UI metrics set. */
    public ResponsiveUiMetrics {
        if (!Float.isFinite(scale) || scale <= 0f
                || titleFontPixels <= 0 || bodyFontPixels <= 0 || smallFontPixels <= 0
                || !positive(outerMargin) || !positive(topBarHeight) || !positive(statusBarHeight)
                || !positive(inspectorWidth) || !positive(listWidth)
                || !positive(markerSize) || !positive(hitRadius)) {
            throw new IllegalArgumentException("Responsive UI metrics must be finite and positive");
        }
    }

    /**
     * Resolves readable UI scale independently from world zoom.
     *
     * @param viewportWidth current logical viewport width
     * @param viewportHeight current logical viewport height
     * @param density platform-reported display density
     * @return bounded metrics for compact through high-DPI desktop viewports
     */
    public static ResponsiveUiMetrics resolve(int viewportWidth, int viewportHeight, float density) {
        if (viewportWidth <= 0 || viewportHeight <= 0 || !Float.isFinite(density) || density <= 0f) {
            throw new IllegalArgumentException("Viewport and density must be positive");
        }
        float resolutionScale = Math.min(
                viewportWidth / REFERENCE_WIDTH,
                viewportHeight / REFERENCE_HEIGHT);
        float densityAssist = clamp((float) Math.sqrt(Math.max(1f, density)), 1f, 1.22f);
        float scale = clamp(resolutionScale * densityAssist, 0.80f, 2.0f);
        float inspector = clamp(viewportWidth * 0.285f, 350f * scale, 520f * scale);
        float list = clamp(viewportWidth * 0.30f, 360f * scale, 560f * scale);
        return new ResponsiveUiMetrics(
                scale,
                Math.round(24f * scale),
                Math.round(17f * scale),
                Math.round(14f * scale),
                16f * scale,
                76f * scale,
                34f * scale,
                inspector,
                list,
                36f * scale,
                Math.max(18f * scale, 24f));
    }

    /** @return true when the viewport needs the compact navigation/header arrangement */
    public boolean compact(int viewportWidth) {
        return viewportWidth < 1180f * scale;
    }

    private static float clamp(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static boolean positive(float value) {
        return Float.isFinite(value) && value > 0f;
    }
}
