package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import com.spacesim.world.LocalPhysicalPosition;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Uniform SI-to-screen projection whose fitted overview frame remains stable while objects move.
 *
 * <p>The initial object set defines one presentation frame. Later {@link #update(List)} calls
 * reproject authoritative physical positions through that same frame without changing
 * pixels-per-metre or recentering the map. This prevents moving ships from making the whole
 * system view breathe or drift while preserving exact relative physical scale.</p>
 */
final class PhysicalMapProjection {
    record Point(double x, double y) { }

    private final Map<String, Point> points = new HashMap<>();
    private final LocalPhysicalPosition reference;
    private final double centerX;
    private final double centerY;
    private final double midXMetres;
    private final double midYMetres;
    private final double pixelsPerMetre;

    PhysicalMapProjection(List<LocalObjectView> objects, double x, double y,
            double width, double height) {
        if (objects.isEmpty()) {
            reference = LocalPhysicalPosition.origin();
            centerX = x + width / 2d;
            centerY = y + height / 2d;
            midXMetres = 0d;
            midYMetres = 0d;
            pixelsPerMetre = 1d;
            return;
        }

        reference = objects.get(0).position();
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (LocalObjectView object : objects) {
            var offset = reference.displacementTo(object.position());
            double px = offset.deltaXM();
            double py = offset.deltaYM();
            minX = Math.min(minX, px - object.physicalLengthM() / 2d);
            maxX = Math.max(maxX, px + object.physicalLengthM() / 2d);
            minY = Math.min(minY, py - object.physicalWidthM() / 2d);
            maxY = Math.max(maxY, py + object.physicalWidthM() / 2d);
        }

        pixelsPerMetre = Math.min(width / Math.max(1d, maxX - minX),
                height / Math.max(1d, maxY - minY));
        midXMetres = (minX + maxX) / 2d;
        midYMetres = (minY + maxY) / 2d;
        centerX = x + width / 2d;
        centerY = y + height / 2d;
        update(objects);
    }

    /**
     * Reprojects current authoritative positions without changing the fitted presentation frame.
     *
     * @param objects current local-system objects
     */
    void update(List<LocalObjectView> objects) {
        points.clear();
        for (LocalObjectView object : objects) {
            var offset = reference.displacementTo(object.position());
            points.put(object.stableId(), new Point(
                    centerX + (offset.deltaXM() - midXMetres) * pixelsPerMetre,
                    centerY + (offset.deltaYM() - midYMetres) * pixelsPerMetre));
        }
    }

    Point point(String id) {
        return points.get(id);
    }

    double pixelsPerMetre() {
        return pixelsPerMetre;
    }

    double inspectionZoom(LocalObjectView object, double width, double height) {
        if (object.sprite() == null) {
            return 1d;
        }
        return 0.6d * Math.min(width / object.physicalLengthM(),
                height / object.physicalWidthM()) / pixelsPerMetre;
    }
}
