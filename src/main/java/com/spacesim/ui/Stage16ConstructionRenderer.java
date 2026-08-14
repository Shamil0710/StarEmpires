package com.spacesim.ui;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.player.ConstructionPlacementPolicy;
import com.spacesim.player.PlayerConstructionPlacementView;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.LocalSystemCoordinates;

import java.util.Objects;

/**
 * Thin local Stage-16 construction placement overlay.
 *
 * <p>The renderer draws only the currently materialized local session plus an authoritative
 * {@link PlayerConstructionPlacementView}. It never decides whether placement is legal and never
 * creates a project. Clearance radii are the same public gameplay parameters owned by
 * {@link ConstructionPlacementPolicy}.</p>
 */
public final class Stage16ConstructionRenderer {
    private static final float PREVIEW_BODY_RADIUS = 8f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Vector2 first = new Vector2();
    private final Vector2 second = new Vector2();

    /** Creates renderer-owned GPU state. */
    public Stage16ConstructionRenderer() {
    }

    /**
     * Draws local blockers, jump exclusion and the current construction ghost.
     *
     * @param camera screen-space camera
     * @param layout current local world-to-screen layout
     * @param session currently materialized local SimulationSession
     * @param preview authoritative placement preview, or null outside placement mode
     */
    public void render(
            OrthographicCamera camera,
            WorldMapLayout layout,
            SimulationSession session,
            PlayerConstructionPlacementView preview) {
        Objects.requireNonNull(camera, "Construction camera not set");
        WorldMapLayout checkedLayout = Objects.requireNonNull(layout, "Construction map layout not set");
        SimulationSession checkedSession = Objects.requireNonNull(session, "Construction session not set");
        shapes.setProjectionMatrix(camera.combined);

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(0.2f, 0.3f, 0.45f, 1f);
        shapes.rect(checkedLayout.mapX(), checkedLayout.mapY(), checkedLayout.mapWidth(), checkedLayout.mapHeight());
        drawWorldCircle(
                checkedLayout,
                LocalSystemCoordinates.ARRIVAL_X,
                LocalSystemCoordinates.ARRIVAL_Y,
                ConstructionPlacementPolicy.JUMP_ARRIVAL_CLEARANCE,
                Color.FIREBRICK);
        for (Entity entity : checkedSession.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (identity == null || transform == null) {
                continue;
            }
            if (identity.kind == IdentityComponent.Kind.STATION) {
                drawWorldCircle(
                        checkedLayout,
                        transform.position.x,
                        transform.position.y,
                        ConstructionPlacementPolicy.STATION_CLEARANCE,
                        Color.SLATE);
            } else if (identity.kind == IdentityComponent.Kind.ASTEROID) {
                drawWorldCircle(
                        checkedLayout,
                        transform.position.x,
                        transform.position.y,
                        ConstructionPlacementPolicy.RESOURCE_CLEARANCE,
                        Color.DARK_GRAY);
            }
        }
        if (preview != null) {
            drawWorldCircle(
                    checkedLayout,
                    preview.x(),
                    preview.y(),
                    ConstructionPlacementPolicy.STATION_CLEARANCE,
                    preview.allowed() ? Color.GREEN : Color.RED);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (Entity entity : checkedSession.getEngine().getEntities()) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (identity == null || transform == null
                    || !checkedLayout.worldToScreen(transform.position.x, transform.position.y, first)) {
                continue;
            }
            if (identity.kind == IdentityComponent.Kind.STATION) {
                shapes.setColor(Color.LIGHT_GRAY);
                shapes.rect(first.x - 4f, first.y - 4f, 8f, 8f);
            } else if (identity.kind == IdentityComponent.Kind.ASTEROID) {
                shapes.setColor(Color.GRAY);
                shapes.circle(first.x, first.y, 3f, 12);
            } else if (identity.kind == IdentityComponent.Kind.FLEET) {
                shapes.setColor(Color.CYAN);
                shapes.triangle(first.x, first.y + 4f, first.x - 3f, first.y - 3f, first.x + 3f, first.y - 3f);
            }
        }
        if (preview != null && checkedLayout.worldToScreen(preview.x(), preview.y(), first)) {
            shapes.setColor(preview.allowed() ? Color.LIME : Color.SCARLET);
            shapes.circle(first.x, first.y, PREVIEW_BODY_RADIUS, 20);
        }
        shapes.end();
    }

    /** Releases renderer-owned GPU resources. */
    public void dispose() {
        shapes.dispose();
    }

    private void drawWorldCircle(
            WorldMapLayout layout,
            float worldX,
            float worldY,
            float worldRadius,
            Color color) {
        if (!layout.worldToScreen(worldX, worldY, first)
                || !layout.worldToScreen(worldX + worldRadius, worldY, second)) {
            return;
        }
        float radius = Math.abs(second.x - first.x);
        if (!Float.isFinite(radius) || radius <= 0f) {
            return;
        }
        shapes.setColor(color);
        shapes.circle(first.x, first.y, radius, 36);
    }
}