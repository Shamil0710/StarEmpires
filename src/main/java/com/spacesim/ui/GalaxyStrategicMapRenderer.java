package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.spacesim.economy.Money;
import com.spacesim.world.StarSystemId;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/** Full-screen read-only strategic overlay for topology, system control and faction summaries. */
public final class GalaxyStrategicMapRenderer {
    private static final Color BACKGROUND = new Color(0.012f, 0.018f, 0.035f, 0.985f);
    private static final Color MAP_PANEL = new Color(0.025f, 0.04f, 0.07f, 0.97f);
    private static final Color INFO_PANEL = new Color(0.035f, 0.05f, 0.08f, 0.98f);
    private static final Color BORDER = new Color(0.28f, 0.47f, 0.68f, 1f);
    private static final Color EDGE = new Color(0.29f, 0.36f, 0.46f, 0.78f);
    private static final Color ACTIVE_EDGE = new Color(0.7f, 0.82f, 1f, 0.95f);
    private static final Color UNCLAIMED = new Color(0.52f, 0.56f, 0.63f, 1f);
    private static final Color ACTIVE = new Color(1f, 0.9f, 0.28f, 1f);
    private static final Color SELECTED = new Color(0.35f, 0.95f, 1f, 1f);
    private static final Color[] FACTION_COLORS = {
            new Color(0.38f, 0.72f, 1f, 1f),
            new Color(0.95f, 0.48f, 0.34f, 1f),
            new Color(0.37f, 0.86f, 0.5f, 1f),
            new Color(0.78f, 0.52f, 1f, 1f),
            new Color(1f, 0.78f, 0.3f, 1f),
            new Color(0.3f, 0.86f, 0.83f, 1f),
            new Color(1f, 0.44f, 0.7f, 1f),
            new Color(0.68f, 0.76f, 0.34f, 1f),
            new Color(0.66f, 0.66f, 1f, 1f),
            new Color(0.9f, 0.64f, 0.42f, 1f)
    };

    private static final float OUTER_MARGIN = 18f;
    private static final float PANEL_GAP = 14f;
    private static final float INNER_PADDING = 18f;
    private static final float MAP_WIDTH_SHARE = 0.68f;
    private static final float SYSTEM_RADIUS = 5.2f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private boolean disposed;

    /**
     * Creates a strategic renderer using a caller-owned UI font.
     *
     * @param font active UI font; ownership remains with the caller
     */
    public GalaxyStrategicMapRenderer(BitmapFont font) {
        this.font = Objects.requireNonNull(font, "Galaxy map font not set");
    }

    /**
     * Draws the full-screen strategic topology and faction overlay without mutating simulation state.
     *
     * @param projectionMatrix current screen-space projection
     * @param snapshot immutable authoritative presentation snapshot
     * @param viewportWidth current viewport width in pixels
     * @param viewportHeight current viewport height in pixels
     */
    public void render(
            Matrix4 projectionMatrix,
            GalaxyStrategicMapSnapshot snapshot,
            float viewportWidth,
            float viewportHeight) {
        if (disposed || projectionMatrix == null || snapshot == null || viewportWidth <= 0f || viewportHeight <= 0f) {
            return;
        }
        float contentX = OUTER_MARGIN;
        float contentY = OUTER_MARGIN;
        float contentWidth = Math.max(200f, viewportWidth - OUTER_MARGIN * 2f);
        float contentHeight = Math.max(160f, viewportHeight - OUTER_MARGIN * 2f);
        float mapWidth = Math.max(260f, contentWidth * MAP_WIDTH_SHARE - PANEL_GAP * 0.5f);
        float infoX = contentX + mapWidth + PANEL_GAP;
        float infoWidth = Math.max(240f, contentWidth - mapWidth - PANEL_GAP);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapes.setProjectionMatrix(projectionMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BACKGROUND);
        shapes.rect(0f, 0f, viewportWidth, viewportHeight);
        shapes.setColor(MAP_PANEL);
        shapes.rect(contentX, contentY, mapWidth, contentHeight);
        shapes.setColor(INFO_PANEL);
        shapes.rect(infoX, contentY, infoWidth, contentHeight);
        shapes.end();

        Map<StarSystemId, Point> points = projectSystems(
                snapshot, contentX + INNER_PADDING, contentY + INNER_PADDING + 24f,
                mapWidth - INNER_PADDING * 2f, contentHeight - INNER_PADDING * 2f - 44f);

        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (GalaxyStrategicMapSnapshot.EdgeView edge : snapshot.edges()) {
            Point first = points.get(edge.first());
            Point second = points.get(edge.second());
            if (first == null || second == null) {
                continue;
            }
            shapes.setColor(edge.touchesActiveSystem() ? ACTIVE_EDGE : EDGE);
            shapes.line(first.x, first.y, second.x, second.y);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            Point point = points.get(system.id());
            if (point == null) {
                continue;
            }
            shapes.setColor(colorForFaction(snapshot, system.controllerFactionId()));
            shapes.circle(point.x, point.y, SYSTEM_RADIUS, 14);
            if (system.active()) {
                shapes.setColor(ACTIVE);
                drawCross(point.x, point.y, 10f);
            } else if (system.selectedNeighbor()) {
                shapes.setColor(SELECTED);
                shapes.circle(point.x, point.y, 2.2f, 10);
            }
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(contentX, contentY, mapWidth, contentHeight);
        shapes.rect(infoX, contentY, infoWidth, contentHeight);
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            if (!system.active() && !system.selectedNeighbor()) {
                continue;
            }
            Point point = points.get(system.id());
            if (point != null) {
                shapes.setColor(system.active() ? ACTIVE : SELECTED);
                shapes.circle(point.x, point.y, system.active() ? 9f : 8f, 16);
            }
        }
        shapes.end();

        batch.setProjectionMatrix(projectionMatrix);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, "GALAXY MAP — " + snapshot.galaxyName(),
                contentX + 10f, contentY + contentHeight - 10f);
        font.draw(batch, "explicit jump graph | node color = controller | yellow = current | cyan = selected jump",
                contentX + 10f, contentY + contentHeight - 30f);
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            Point point = points.get(system.id());
            if (point == null) {
                continue;
            }
            font.setColor(system.active() ? ACTIVE : Color.LIGHT_GRAY);
            font.draw(batch, "#" + system.id().value(), point.x + 7f, point.y + 5f);
        }
        drawInfoPanel(batch, snapshot, infoX + INNER_PADDING,
                contentY + contentHeight - INNER_PADDING, infoWidth - INNER_PADDING * 2f);
        batch.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawInfoPanel(
            SpriteBatch targetBatch,
            GalaxyStrategicMapSnapshot snapshot,
            float x,
            float topY,
            float width) {
        float y = topY;
        font.setColor(Color.WHITE);
        font.draw(targetBatch, "CURRENT SYSTEM", x, y);
        y -= 22f;
        GalaxyStrategicMapSnapshot.SystemView current = findSystem(snapshot, snapshot.activeSystemId());
        if (current == null) {
            font.draw(targetBatch, "No active in-system location", x, y);
            y -= 22f;
        } else {
            font.setColor(ACTIVE);
            font.draw(targetBatch, "#" + current.id().value() + "  " + current.name(), x, y);
            y -= 19f;
            font.setColor(Color.LIGHT_GRAY);
            font.draw(targetBatch, "Sector: " + current.sectorName(), x, y);
            y -= 19f;
            font.draw(targetBatch, "Controller: " + current.controllerDisplayName(), x, y);
            y -= 19f;
            font.draw(targetBatch, "Direct links: " + current.neighborCount(), x, y);
            y -= 22f;
            String links = directLinksSummary(snapshot, current.id());
            if (!links.isEmpty()) {
                font.draw(targetBatch, links, x, y, width, com.badlogic.gdx.utils.Align.left, true);
                y -= 40f;
            }
        }

        y -= 4f;
        font.setColor(Color.WHITE);
        font.draw(targetBatch, "FACTIONS — authoritative Stage 17 state", x, y);
        y -= 22f;
        for (int index = 0; index < snapshot.factions().size(); index++) {
            GalaxyStrategicMapSnapshot.FactionView faction = snapshot.factions().get(index);
            font.setColor(colorForFaction(snapshot, faction.factionId()));
            font.draw(targetBatch,
                    faction.displayName() + "  [" + faction.controlledSystems() + " systems]",
                    x, y);
            y -= 17f;
            font.setColor(Color.LIGHT_GRAY);
            String economy = String.format(Locale.ROOT,
                    "Treasury %,.0f cr | tax %.1f%% | transit %.1f%% | customs %.1f%%",
                    Money.toCredits(faction.treasuryMilliCredits()),
                    faction.stationTaxBasisPoints() / 100.0,
                    faction.territorialTariffBasisPoints() / 100.0,
                    faction.customsTariffBasisPoints() / 100.0);
            font.draw(targetBatch, economy, x + 10f, y, width - 10f, com.badlogic.gdx.utils.Align.left, true);
            y -= 17f;
            font.draw(targetBatch,
                    "claims " + faction.activeClaims()
                            + " | goals " + faction.strategicGoals()
                            + " | treaty records " + faction.treatyRecords()
                            + " | embargo records " + faction.embargoRecords(),
                    x + 10f, y, width - 10f, com.badlogic.gdx.utils.Align.left, true);
            y -= 23f;
            if (y < 38f) {
                font.setColor(Color.GRAY);
                font.draw(targetBatch, "… additional factions omitted at this resolution", x, y);
                break;
            }
        }
        font.setColor(Color.GRAY);
        font.draw(targetBatch, "[G / ESC] close strategic map", x, 38f);
    }

    private static String directLinksSummary(
            GalaxyStrategicMapSnapshot snapshot,
            StarSystemId activeSystemId) {
        StringBuilder text = new StringBuilder("Links: ");
        int shown = 0;
        for (GalaxyStrategicMapSnapshot.EdgeView edge : snapshot.edges()) {
            StarSystemId neighbor = null;
            if (edge.first().equals(activeSystemId)) {
                neighbor = edge.second();
            } else if (edge.second().equals(activeSystemId)) {
                neighbor = edge.first();
            }
            if (neighbor == null) {
                continue;
            }
            GalaxyStrategicMapSnapshot.SystemView view = findSystem(snapshot, neighbor);
            if (shown > 0) {
                text.append(" | ");
            }
            text.append('#').append(neighbor.value());
            if (view != null) {
                text.append(' ').append(view.name()).append(" {").append(view.controllerDisplayName()).append('}');
            }
            shown++;
            if (shown >= 5) {
                if (shown < findSystem(snapshot, activeSystemId).neighborCount()) {
                    text.append(" | …");
                }
                break;
            }
        }
        return shown == 0 ? "" : text.toString();
    }

    private static GalaxyStrategicMapSnapshot.SystemView findSystem(
            GalaxyStrategicMapSnapshot snapshot,
            StarSystemId id) {
        if (id == null) {
            return null;
        }
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            if (system.id().equals(id)) {
                return system;
            }
        }
        return null;
    }

    private static Map<StarSystemId, Point> projectSystems(
            GalaxyStrategicMapSnapshot snapshot,
            float x,
            float y,
            float width,
            float height) {
        double minX = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            minX = Math.min(minX, system.galaxyX());
            maxX = Math.max(maxX, system.galaxyX());
            minY = Math.min(minY, system.galaxyY());
            maxY = Math.max(maxY, system.galaxyY());
        }
        double spanX = Math.max(1.0, maxX - minX);
        double spanY = Math.max(1.0, maxY - minY);
        Map<StarSystemId, Point> result = new HashMap<>();
        for (GalaxyStrategicMapSnapshot.SystemView system : snapshot.systems()) {
            float sx = x + (float) ((system.galaxyX() - minX) / spanX) * Math.max(1f, width);
            float sy = y + (float) ((system.galaxyY() - minY) / spanY) * Math.max(1f, height);
            result.put(system.id(), new Point(sx, sy));
        }
        return result;
    }

    private void drawCross(float x, float y, float size) {
        shapes.rect(x - 1.2f, y - size, 2.4f, size * 2f);
        shapes.rect(x - size, y - 1.2f, size * 2f, 2.4f);
    }

    private static Color colorForFaction(GalaxyStrategicMapSnapshot snapshot, String factionId) {
        if (factionId == null) {
            return UNCLAIMED;
        }
        for (int index = 0; index < snapshot.factions().size(); index++) {
            if (snapshot.factions().get(index).factionId().equals(factionId)) {
                return FACTION_COLORS[index % FACTION_COLORS.length];
            }
        }
        return UNCLAIMED;
    }

    /** Releases graphics resources owned by this renderer. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        shapes.dispose();
        batch.dispose();
    }

    private record Point(float x, float y) {
    }
}
