package com.spacesim.ui;

/**
 * Corner minimap of the system map: the whole fitted overview frame with a camera rectangle.
 *
 * <p>Works in the system map's fitted base frame (screen coordinates of the overview, zoom 1),
 * so it shows exactly the objects the map shows and needs no world data of its own. The camera
 * rectangle is the map viewport mapped back through the camera. Shown only when zoomed in, where
 * part of the system is out of view. Pure presentation geometry.</p>
 */
final class SystemMinimap {
    /** Camera zoom from which the minimap is shown. */
    static final float MIN_ZOOM_SHOWN = 1.5f;

    private SystemMinimap() {
    }

    /**
     * @param zoom current camera zoom
     * @return whether the minimap is worth showing
     */
    static boolean shown(float zoom) {
        return Float.isFinite(zoom) && zoom >= MIN_ZOOM_SHOWN;
    }

    /**
     * Minimap box in the top-right corner of the map, keeping the frame's aspect ratio.
     *
     * @param map map viewport
     * @param frame fitted overview frame
     * @param maxSide longest side of the minimap
     * @param margin distance from the map's top-right corner
     * @return minimap box
     */
    static MapLabelLayout.Box box(MapLabelLayout.Box map, MapLabelLayout.Box frame, float maxSide, float margin) {
        float aspect = frame.width() / Math.max(1e-6f, frame.height());
        float width = aspect >= 1f ? maxSide : maxSide * aspect;
        float height = aspect >= 1f ? maxSide / aspect : maxSide;
        return new MapLabelLayout.Box(map.right() - margin - width, map.top() - margin - height, width, height);
    }

    /**
     * Maps a base-frame point into the minimap, clamped to its edges.
     *
     * @param baseX base-frame X
     * @param baseY base-frame Y
     * @param frame fitted overview frame
     * @param box minimap box with the frame's aspect ratio
     * @return {x, y} inside the box
     */
    static float[] toMinimap(double baseX, double baseY, MapLabelLayout.Box frame, MapLabelLayout.Box box) {
        double scale = box.width() / (double) frame.width();
        float x = (float) (box.x() + (baseX - frame.x()) * scale);
        float y = (float) (box.y() + (baseY - frame.y()) * scale);
        return new float[] {clamp(x, box.x(), box.right()), clamp(y, box.y(), box.top())};
    }

    /**
     * Camera rectangle: the map viewport mapped back to the base frame, then into the minimap.
     *
     * @param viewport map viewport on screen
     * @param zoom camera zoom
     * @param panX camera pan X
     * @param panY camera pan Y
     * @param centerX camera transform centre X
     * @param centerY camera transform centre Y
     * @param frame fitted overview frame
     * @param box minimap box
     * @return camera rectangle clipped to the box, or null when the view lies outside the frame
     */
    static MapLabelLayout.Box cameraRect(MapLabelLayout.Box viewport, double zoom, double panX, double panY,
            double centerX, double centerY, MapLabelLayout.Box frame, MapLabelLayout.Box box) {
        if (!(zoom > 0d) || !Double.isFinite(zoom)) return null;
        double left = centerX + (viewport.x() - centerX - panX) / zoom;
        double right = centerX + (viewport.right() - centerX - panX) / zoom;
        double bottom = centerY + (viewport.y() - centerY - panY) / zoom;
        double top = centerY + (viewport.top() - centerY - panY) / zoom;
        double scale = box.width() / (double) frame.width();
        double x1 = Math.max(box.x(), box.x() + (left - frame.x()) * scale);
        double x2 = Math.min(box.right(), box.x() + (right - frame.x()) * scale);
        double y1 = Math.max(box.y(), box.y() + (bottom - frame.y()) * scale);
        double y2 = Math.min(box.top(), box.y() + (top - frame.y()) * scale);
        if (x2 <= x1 || y2 <= y1) return null;
        return new MapLabelLayout.Box((float) x1, (float) y1, (float) (x2 - x1), (float) (y2 - y1));
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
