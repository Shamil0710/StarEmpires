package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.spacesim.player.GlobalFleetMapSnapshot;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thin GPU renderer for the player-known Stage-15 strategic map.
 *
 * <p>All visibility/classification is prepared by {@link com.spacesim.player.GlobalFleetMapModel};
 * the renderer therefore has no access to WorldSimulation or hidden entities.</p>
 */
public final class GlobalFleetMapRenderer {
    private static final float PADDING = 70f;
    private static final float SYSTEM_RADIUS = 8f;
    private static final float SELECTED_RADIUS = 13f;
    private static final float FLEET_OFFSET = 14f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();

    /** Creates isolated renderer-owned libGDX resources. */
    public GlobalFleetMapRenderer() {
    }

    /**
     * Draws one known-world snapshot in screen coordinates.
     *
     * @param camera screen-space orthographic camera
     * @param snapshot immutable player-known map snapshot
     * @param selectedSystem selected destination, or null
     * @param selectedFleet selected owned FleetId, or null
     * @param width current viewport width
     * @param height current viewport height
     */
    public void render(
            OrthographicCamera camera,
            GlobalFleetMapSnapshot snapshot,
            StarSystemId selectedSystem,
            FleetId selectedFleet,
            float width,
            float height) {
        Objects.requireNonNull(camera, "Global map camera not set");
        GlobalFleetMapSnapshot checked = Objects.requireNonNull(snapshot, "Global map snapshot not set");
        Map<StarSystemId, Point> points = layout(checked, width, height);

        shapes.setProjectionMatrix(camera.combined);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (GlobalFleetMapSnapshot.LinkMarker link : checked.links()) {
            Point first = points.get(link.first());
            Point second = points.get(link.second());
            if (first == null || second == null) {
                continue;
            }
            float intensity = (float) Math.min(1d, link.observedDanger() * Math.max(0.15f, link.intelConfidence()) / 25d);
            shapes.setColor(0.35f + intensity * 0.45f, 0.45f - intensity * 0.15f, 0.55f - intensity * 0.25f, 1f);
            shapes.line(first.x(), first.y(), second.x(), second.y());
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (GlobalFleetMapSnapshot.SystemMarker system : checked.systems()) {
            Point point = points.get(system.systemId());
            if (point == null) {
                continue;
            }
            boolean selected = system.systemId().equals(selectedSystem);
            float danger = (float) Math.min(1d,
                    system.observedDanger() * Math.max(0.15f, system.intelConfidence()) / 25d);
            if (selected) {
                shapes.setColor(Color.WHITE);
            } else {
                shapes.setColor(0.25f + danger * 0.55f, 0.65f - danger * 0.35f, 0.9f - danger * 0.45f, 1f);
            }
            shapes.circle(point.x(), point.y(), selected ? SELECTED_RADIUS : SYSTEM_RADIUS, 24);
        }
        for (GlobalFleetMapSnapshot.FleetMarker fleet : checked.fleets()) {
            StarSystemId anchor = fleet.systemId() != null ? fleet.systemId() : fleet.transitDestination();
            Point point = points.get(anchor);
            if (point == null) {
                continue;
            }
            float y = point.y() - FLEET_OFFSET - (fleet.fleetId().value() % 4L) * 5f;
            if (fleet.fleetId().equals(selectedFleet)) {
                shapes.setColor(Color.YELLOW);
            } else if (fleet.activeDirectControl()) {
                shapes.setColor(Color.CYAN);
            } else {
                shapes.setColor(Color.LIGHT_GRAY);
            }
            shapes.rect(point.x() - 4f, y - 3f, 8f, 6f);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.begin();
        for (GlobalFleetMapSnapshot.SystemMarker system : checked.systems()) {
            Point point = points.get(system.systemId());
            if (point == null) {
                continue;
            }
            font.draw(batch, system.name(), point.x() + 12f, point.y() + 16f);
            if (system.intelConfidence() > 0f) {
                font.draw(batch,
                        String.format("risk %.1f @ %.0f%%", system.observedDanger(), system.intelConfidence() * 100f),
                        point.x() + 12f,
                        point.y());
            }
        }
        for (GlobalFleetMapSnapshot.FleetMarker fleet : checked.fleets()) {
            StarSystemId anchor = fleet.systemId() != null ? fleet.systemId() : fleet.transitDestination();
            Point point = points.get(anchor);
            if (point == null) {
                continue;
            }
            float y = point.y() - FLEET_OFFSET - (fleet.fleetId().value() % 4L) * 5f;
            font.draw(batch,
                    "F#" + fleet.fleetId().value() + " " + fleet.orderType(),
                    point.x() + 7f,
                    y + 5f);
        }
        batch.end();
    }

    /** Releases renderer-owned GPU resources. */
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }

    private static Map<StarSystemId, Point> layout(
            GlobalFleetMapSnapshot snapshot,
            float width,
            float height) {
        Map<StarSystemId, Point> result = new HashMap<>();
        if (snapshot.systems().isEmpty()) {
            return result;
        }
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (GlobalFleetMapSnapshot.SystemMarker marker : snapshot.systems()) {
            minX = Math.min(minX, marker.galaxyX());
            maxX = Math.max(maxX, marker.galaxyX());
            minY = Math.min(minY, marker.galaxyY());
            maxY = Math.max(maxY, marker.galaxyY());
        }
        double spanX = Math.max(1d, maxX - minX);
        double spanY = Math.max(1d, maxY - minY);
        float usableWidth = Math.max(1f, width - PADDING * 2f);
        float usableHeight = Math.max(1f, height - PADDING * 2f);
        for (GlobalFleetMapSnapshot.SystemMarker marker : snapshot.systems()) {
            float x = PADDING + (float) ((marker.galaxyX() - minX) / spanX) * usableWidth;
            float y = PADDING + (float) ((marker.galaxyY() - minY) / spanY) * usableHeight;
            result.put(marker.systemId(), new Point(x, y));
        }
        return result;
    }

    private record Point(float x, float y) {
    }
}
