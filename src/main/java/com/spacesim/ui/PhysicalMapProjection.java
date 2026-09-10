package com.spacesim.ui;

import com.spacesim.ui.GeneratedWorldUiSnapshot.LocalObjectView;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

/** Uniform SI-to-screen projection; double precision survives system-to-hull zoom. */
final class PhysicalMapProjection {
    record Point(double x, double y) { }
    private final Map<String, Point> points;
    private final double pixelsPerMetre;

    PhysicalMapProjection(List<LocalObjectView> objects, double x, double y,
            double width, double height) {
        points = new HashMap<>();
        if (objects.isEmpty()) {
            pixelsPerMetre = 1d;
            return;
        }
        var reference = objects.get(0).position();
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (var object : objects) {
            var offset = reference.displacementTo(object.position());
            double px = offset.deltaXM(), py = offset.deltaYM();
            points.put(object.stableId(), new Point(px, py));
            minX = Math.min(minX, px - object.physicalLengthM() / 2d);
            maxX = Math.max(maxX, px + object.physicalLengthM() / 2d);
            minY = Math.min(minY, py - object.physicalWidthM() / 2d);
            maxY = Math.max(maxY, py + object.physicalWidthM() / 2d);
        }
        pixelsPerMetre = Math.min(width / Math.max(1d, maxX - minX),
                height / Math.max(1d, maxY - minY));
        double midX = (minX + maxX) / 2d, midY = (minY + maxY) / 2d;
        points.replaceAll((id, point) -> new Point(
                x + width / 2d + (point.x() - midX) * pixelsPerMetre,
                y + height / 2d + (point.y() - midY) * pixelsPerMetre));
    }

    Point point(String id) { return points.get(id); }
    double pixelsPerMetre() { return pixelsPerMetre; }

    double inspectionZoom(LocalObjectView object, double width, double height) {
        if (object.sprite() == null) return 1d;
        return 0.6d * Math.min(width / object.physicalLengthM(),
                height / object.physicalWidthM()) / pixelsPerMetre;
    }
}
