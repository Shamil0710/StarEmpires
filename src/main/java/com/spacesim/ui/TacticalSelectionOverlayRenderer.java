package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;

import java.util.Objects;

/** Presentation-only highlight renderer for the currently selected tactical ship. */
public final class TacticalSelectionOverlayRenderer {
    private static final Color NEUTRAL_SELECTION = new Color(0.96f, 0.96f, 0.98f, 1f);
    private static final Color INNER_SELECTION = new Color(1f, 1f, 1f, 0.95f);
    private static final float OUTER_PADDING_PX = 5f;
    private static final float INNER_GAP_PX = 3f;

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final Vector2 center = new Vector2();
    private boolean disposed;

    /**
     * Draws a high-contrast selection ring around one selected ship if that entity is still visible.
     *
     * @param projectionMatrix current camera projection matrix
     * @param layout current world-to-screen mapping
     * @param snapshot current immutable tactical snapshot
     * @param selectedEntityId selected stable entity id; non-positive means no selection
     */
    public void render(
            Matrix4 projectionMatrix,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot,
            long selectedEntityId) {
        if (disposed || projectionMatrix == null || layout == null || snapshot == null || selectedEntityId <= 0L) {
            return;
        }
        ShipGlyph selected = snapshot.ships().stream()
                .filter(ship -> ship.entityId() == selectedEntityId)
                .findFirst()
                .orElse(null);
        if (selected == null
                || !layout.containsVisibleWorldPoint((float) selected.xM(), (float) selected.yM())
                || !layout.worldToScreen((float) selected.xM(), (float) selected.yM(), center)) {
            return;
        }

        TacticalShipMarkerMetrics.Bounds bounds = TacticalShipMarkerMetrics.bounds(layout, selected);
        float outerRadius = bounds.radiusPx() + OUTER_PADDING_PX;
        float innerRadius = Math.max(outerRadius - INNER_GAP_PX, 2f);

        shapes.setProjectionMatrix(projectionMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Line);
        setSideColor(selected.side());
        shapes.circle(center.x, center.y, outerRadius, 48);
        shapes.circle(center.x, center.y, outerRadius + 1.5f, 48);
        shapes.setColor(INNER_SELECTION);
        shapes.circle(center.x, center.y, innerRadius, 48);
        drawHeadingTick(selected, outerRadius);
        shapes.end();
    }

    /** Releases renderer-owned graphics resources. */
    public void dispose() {
        if (!disposed) {
            shapes.dispose();
            disposed = true;
        }
    }

    private void setSideColor(TacticalSide side) {
        Objects.requireNonNull(side, "side");
        if (side == TacticalSide.NEUTRAL) {
            shapes.setColor(NEUTRAL_SELECTION);
            return;
        }
        TacticalSidePalette.Rgba color = TacticalSidePalette.outline(side);
        shapes.setColor(color.r(), color.g(), color.b(), color.a());
    }

    private void drawHeadingTick(ShipGlyph ship, float radius) {
        float cos = (float) Math.cos(ship.headingRad());
        float sin = (float) Math.sin(ship.headingRad());
        float start = radius + 2f;
        float end = radius + 10f;
        shapes.line(
                center.x + cos * start,
                center.y + sin * start,
                center.x + cos * end,
                center.y + sin * end);
    }
}
