package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.spacesim.content.Stage22EmpirePackageCatalog;
import com.spacesim.content.Stage22IndustrialUnionPackageCatalog;
import com.spacesim.player.GlobalFleetMapSnapshot;
import com.spacesim.presentation.asset.Stage20MinimumPlayableTextureRenderer;
import com.spacesim.presentation.asset.Stage22ProductionShipSpriteAdapter;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver;
import com.spacesim.presentation.asset.Stage22ProductionShipVisualResolver.RuntimeVisualState;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Thin GPU renderer for the player-known strategic map.
 *
 * <p>All visibility/classification is prepared by {@link com.spacesim.player.GlobalFleetMapModel};
 * the renderer therefore has no access to WorldSimulation or hidden entities. Stage-16 project and
 * owned-station markers are presentation-only projections of the authoritative construction
 * management snapshot. When an owned fleet already carries a canonical Stage-22 core faction and an
 * exact installed engineering fit, its icon is resolved through the same M22.7C production authority
 * used by the system map; no player-affiliation or order-type heuristic is used.</p>
 */
public final class GlobalFleetMapRenderer {
    private static final float PADDING = 70f;
    private static final float SYSTEM_RADIUS = 8f;
    private static final float SELECTED_RADIUS = 13f;
    private static final float FLEET_OFFSET = 14f;
    private static final float PRODUCTION_FLEET_ICON_LENGTH = 18f;
    private static final float MIN_PRODUCTION_FLEET_ICON_WIDTH = 6f;
    private static final float PROJECT_OFFSET_X = -18f;
    private static final float PROJECT_OFFSET_Y = 20f;
    private static final float STATION_OFFSET_X = 18f;
    private static final float STATION_OFFSET_Y = 20f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private Stage20MinimumPlayableTextureRenderer shipSprites;

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
        for (GlobalFleetMapSnapshot.ConstructionProjectMarker project : checked.projects()) {
            Point point = points.get(project.systemId());
            if (point == null) {
                continue;
            }
            float offset = (project.projectId().value() % 3L) * 6f;
            float x = point.x() + PROJECT_OFFSET_X - offset;
            float y = point.y() + PROJECT_OFFSET_Y + offset;
            if (!project.territorialAccessCurrentlyAllowed()) {
                shapes.setColor(Color.RED);
            } else if (project.fundingShortfallMilliCredits() > 0L || project.missingMaterialUnits() > 0L) {
                shapes.setColor(Color.ORANGE);
            } else {
                shapes.setColor(Color.GOLD);
            }
            shapes.triangle(x, y + 5f, x - 5f, y - 4f, x + 5f, y - 4f);
        }
        for (GlobalFleetMapSnapshot.OwnedStationMarker station : checked.stations()) {
            Point point = points.get(station.systemId());
            if (point == null) {
                continue;
            }
            long stableOffset = station.reference().stationEntityId().value() % 3L;
            float x = point.x() + STATION_OFFSET_X + stableOffset * 7f;
            float y = point.y() + STATION_OFFSET_Y + stableOffset * 5f;
            shapes.setColor(Color.GREEN);
            shapes.rect(x - 5f, y - 5f, 10f, 10f);
        }
        shapes.end();

        batch.setProjectionMatrix(camera.combined);
        batch.setColor(Color.WHITE);
        batch.begin();
        drawProductionFleetIcons(checked, points);
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
        for (GlobalFleetMapSnapshot.ConstructionProjectMarker project : checked.projects()) {
            Point point = points.get(project.systemId());
            if (point == null) {
                continue;
            }
            float offset = (project.projectId().value() % 3L) * 6f;
            float x = point.x() + PROJECT_OFFSET_X - offset;
            float y = point.y() + PROJECT_OFFSET_Y + offset;
            font.draw(batch,
                    String.format("P#%d %s %.0f%% miss %d",
                            project.projectId().value(),
                            project.status(),
                            project.buildProgress() * 100d,
                            project.missingMaterialUnits()),
                    x - 10f,
                    y + 18f);
        }
        for (GlobalFleetMapSnapshot.OwnedStationMarker station : checked.stations()) {
            Point point = points.get(station.systemId());
            if (point == null) {
                continue;
            }
            long stableOffset = station.reference().stationEntityId().value() % 3L;
            float x = point.x() + STATION_OFFSET_X + stableOffset * 7f;
            float y = point.y() + STATION_OFFSET_Y + stableOffset * 5f;
            font.draw(batch, "Owned " + station.stationDisplayName(), x + 7f, y + 5f);
        }
        batch.end();
        batch.setColor(Color.WHITE);
    }

    private void drawProductionFleetIcons(
            GlobalFleetMapSnapshot snapshot,
            Map<StarSystemId, Point> points) {
        for (GlobalFleetMapSnapshot.FleetMarker fleet : snapshot.fleets()) {
            if (fleet.stableFactionId() == null
                    || fleet.installedFit() == null
                    || !isCoreProductionFaction(fleet.stableFactionId())) {
                continue;
            }
            StarSystemId anchor = fleet.systemId() != null ? fleet.systemId() : fleet.transitDestination();
            Point point = points.get(anchor);
            if (point == null) {
                continue;
            }
            var visual = Stage22ProductionShipVisualResolver.resolveInstalledFit(
                    "fleet:" + fleet.fleetId().value(),
                    fleet.stableFactionId(),
                    fleet.installedFit(),
                    RuntimeVisualState.IDLE);
            var sprite = Stage22ProductionShipSpriteAdapter.adapt(visual);
            float y = point.y() - FLEET_OFFSET - (fleet.fleetId().value() % 4L) * 5f;
            float iconWidth = Math.max(
                    MIN_PRODUCTION_FLEET_ICON_WIDTH,
                    PRODUCTION_FLEET_ICON_LENGTH * (float) (visual.worldWidthM() / visual.worldLengthM()));
            shipSprites().draw(
                    batch,
                    sprite.binding(),
                    point.x(),
                    y,
                    PRODUCTION_FLEET_ICON_LENGTH,
                    iconWidth,
                    0f);
        }
    }

    private Stage20MinimumPlayableTextureRenderer shipSprites() {
        if (shipSprites == null) {
            shipSprites = new Stage20MinimumPlayableTextureRenderer();
        }
        return shipSprites;
    }

    private static boolean isCoreProductionFaction(String factionId) {
        return Stage22EmpirePackageCatalog.STABLE_FACTION_ID.equals(factionId)
                || Stage22IndustrialUnionPackageCatalog.STABLE_FACTION_ID.equals(factionId);
    }

    /** Releases renderer-owned GPU resources. */
    public void dispose() {
        if (shipSprites != null) {
            shipSprites.dispose();
            shipSprites = null;
        }
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
