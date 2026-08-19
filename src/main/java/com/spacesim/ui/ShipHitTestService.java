package com.spacesim.ui;

import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.Objects;
import java.util.OptionalLong;

/**
 * Read-only screen-space hit testing for tactical ship markers.
 *
 * <p>The service consumes only the current immutable visual snapshot and camera layout. It never
 * changes simulation, orders, targets or any other authoritative state.</p>
 */
public final class ShipHitTestService {
    /**
     * Finds the nearest visible ship marker containing the supplied screen point.
     *
     * @param screenX screen-space x coordinate in the same bottom-left origin used by the layout
     * @param screenY screen-space y coordinate in the same bottom-left origin used by the layout
     * @param layout current world-to-screen mapping
     * @param snapshot current immutable tactical visual snapshot
     * @return selected stable entity id, or empty when the point hits no visible ship marker
     */
    public OptionalLong hitTest(
            float screenX,
            float screenY,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot) {
        Objects.requireNonNull(layout, "layout");
        Objects.requireNonNull(snapshot, "snapshot");
        if (!layout.containsMapPoint(screenX, screenY)) {
            return OptionalLong.empty();
        }

        Vector2 center = new Vector2();
        long bestEntityId = -1L;
        double bestDistanceSquared = Double.POSITIVE_INFINITY;
        for (ShipGlyph ship : snapshot.ships()) {
            if (!layout.containsVisibleWorldPoint((float) ship.xM(), (float) ship.yM())
                    || !layout.worldToScreen((float) ship.xM(), (float) ship.yM(), center)) {
                continue;
            }
            TacticalShipMarkerMetrics.Bounds bounds = TacticalShipMarkerMetrics.bounds(layout, ship);
            double dx = screenX - center.x;
            double dy = screenY - center.y;
            double cos = Math.cos(ship.headingRad());
            double sin = Math.sin(ship.headingRad());
            double forward = dx * cos + dy * sin;
            double lateral = -dx * sin + dy * cos;
            if (Math.abs(forward) > bounds.halfLengthPx()
                    || Math.abs(lateral) > bounds.halfWidthPx()) {
                continue;
            }
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < bestDistanceSquared
                    || distanceSquared == bestDistanceSquared && ship.entityId() < bestEntityId) {
                bestDistanceSquared = distanceSquared;
                bestEntityId = ship.entityId();
            }
        }
        return bestEntityId > 0L ? OptionalLong.of(bestEntityId) : OptionalLong.empty();
    }
}
