package com.spacesim.ui;

import java.util.List;

/**
 * Small price line of observed quotes inside one table cell.
 *
 * <p>X follows the observation ticks, not their index, so a long gap between visits looks long;
 * Y spans the observed price range. Only observed points are drawn and joined; nothing is
 * interpolated beyond them. Pure presentation geometry.</p>
 */
final class PriceSparkline {
    /**
     * One observed price.
     *
     * @param tick authoritative tick of the observation
     * @param priceMilliPerKg observed price
     */
    record Point(long tick, long priceMilliPerKg) { }

    private PriceSparkline() {
    }

    /**
     * @param points observed prices, oldest first, ticks strictly increasing
     * @param box cell area
     * @return {x0, y0, x1, y1, ...}; one point is drawn at the right edge, mid-height; empty when there are none
     */
    static float[] polyline(List<Point> points, MapLabelLayout.Box box) {
        if (points.isEmpty()) return new float[0];
        long firstTick = points.get(0).tick();
        long lastTick = points.get(points.size() - 1).tick();
        long low = Long.MAX_VALUE;
        long high = Long.MIN_VALUE;
        for (Point point : points) {
            low = Math.min(low, point.priceMilliPerKg());
            high = Math.max(high, point.priceMilliPerKg());
        }
        float[] xy = new float[points.size() * 2];
        for (int index = 0; index < points.size(); index++) {
            Point point = points.get(index);
            double fx = lastTick == firstTick ? 1d : (double) (point.tick() - firstTick) / (lastTick - firstTick);
            double fy = high == low ? 0.5d : (double) (point.priceMilliPerKg() - low) / (high - low);
            xy[2 * index] = (float) (box.x() + fx * box.width());
            xy[2 * index + 1] = (float) (box.y() + fy * box.height());
        }
        return xy;
    }
}
