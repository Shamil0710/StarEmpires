package com.spacesim.ui;

/**
 * Portrait of the selected object in the inspector header: the same sprite as on the map, drawn
 * level (nose to the right) and fitted into a square frame in the header's top-right corner.
 *
 * <p>The frame takes at most a fixed share of the inspector width and is dropped when the
 * inspector is too narrow for a readable portrait; the header text then keeps the full width.
 * Pure presentation geometry: it never reads or changes simulation state.</p>
 */
final class InspectorPortrait {
    /** Largest share of the header content width a square portrait frame may take. */
    static final float SQUARE_WIDTH_SHARE = 0.32f;
    /** Largest share of the header content width a frame widened for a long hull may take. */
    static final float MAX_WIDTH_SHARE = 0.42f;
    /** Widest frame, as a multiple of its height, for long hulls. */
    static final float MAX_FRAME_ASPECT = 2f;
    /** Share of the frame side kept free around the hull on each side. */
    static final float INSET_SHARE = 0.08f;

    private InspectorPortrait() {
    }

    /**
     * Portrait frame in the top-right corner of the header content: square for compact hulls,
     * widened up to {@link #MAX_FRAME_ASPECT} for long ones so a freighter is not a thin strip.
     *
     * @param contentRight right edge of the header content (inspector minus padding)
     * @param contentTop top edge of the header content
     * @param contentWidth width of the header content
     * @param preferredSide frame height (and square side) in a wide inspector
     * @param minSide smallest side worth drawing
     * @param hullAspect hull length divided by width; non-finite or non-positive counts as square
     * @return frame, or null when the inspector is too narrow for one
     */
    static MapLabelLayout.Box frame(float contentRight, float contentTop, float contentWidth, float preferredSide,
            float minSide, double hullAspect) {
        float side = Math.min(preferredSide, contentWidth * SQUARE_WIDTH_SHARE);
        if (!Float.isFinite(side) || side < minSide || side <= 0f) return null;
        float aspect = Double.isFinite(hullAspect) && hullAspect > 1d ? (float) Math.min(hullAspect, MAX_FRAME_ASPECT) : 1f;
        float width = Math.max(side, Math.min(side * aspect, contentWidth * MAX_WIDTH_SHARE));
        return new MapLabelLayout.Box(contentRight - width, contentTop - side, width, side);
    }

    /**
     * Fits the hull into the frame, keeping its length-to-width ratio.
     *
     * @param lengthM physical hull length along the nose axis
     * @param widthM physical hull width
     * @param frame portrait frame
     * @return {length, width} in pixels with the sprite drawn level, or null without positive dimensions
     */
    static float[] fit(double lengthM, double widthM, MapLabelLayout.Box frame) {
        if (frame == null || !(lengthM > 0d) || !(widthM > 0d) || !Double.isFinite(lengthM) || !Double.isFinite(widthM)) {
            return null;
        }
        // The inset is a share of the frame height on every side, so a widened frame keeps even margins.
        double inset = frame.height() * INSET_SHARE;
        double scale = Math.min((frame.width() - 2d * inset) / lengthM, (frame.height() - 2d * inset) / widthM);
        return new float[] {(float) (lengthM * scale), (float) (widthM * scale)};
    }

    /**
     * @param contentWidth width of the header content
     * @param frame portrait frame, or null without one
     * @param gap space between the header text and the frame
     * @return width left for the header text
     */
    static float textWidth(float contentWidth, MapLabelLayout.Box frame, float gap) {
        return frame == null ? contentWidth : Math.max(0f, contentWidth - frame.width() - gap);
    }

    /**
     * @param textBottom lowest baseline the header text reached
     * @param frame portrait frame, or null without one
     * @param gap space kept below the frame
     * @return where the inspector body starts, below both the header text and the portrait
     */
    static float bodyTop(float textBottom, MapLabelLayout.Box frame, float gap) {
        return frame == null ? textBottom : Math.min(textBottom, frame.y() - gap);
    }
}
