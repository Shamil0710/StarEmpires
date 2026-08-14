package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;

import java.util.Objects;

/**
 * Compact presentation-only renderer for a local-system minimap.
 *
 * <p>The renderer consumes a read-only {@link LocalMinimapSnapshot}; no Entity references or
 * gameplay controllers are retained. Labels are deliberately limited to the minimap title so the
 * marker field remains readable at small sizes.</p>
 */
public final class LocalMinimapRenderer {
    private static final Color BACKGROUND = new Color(0.015f, 0.025f, 0.045f, 0.94f);
    private static final Color BORDER = new Color(0.32f, 0.52f, 0.7f, 1f);
    private static final Color PLAYER = new Color(1f, 0.86f, 0.2f, 1f);
    private static final Color STATION = new Color(0.36f, 0.76f, 1f, 1f);
    private static final Color FRIENDLY = new Color(0.28f, 0.9f, 0.55f, 1f);
    private static final Color HOSTILE = new Color(1f, 0.28f, 0.3f, 1f);
    private static final Color OTHER = new Color(0.72f, 0.76f, 0.82f, 1f);
    private static final Color ASTEROID = new Color(0.56f, 0.5f, 0.42f, 1f);
    private static final Color SALVAGE = new Color(0.86f, 0.62f, 0.24f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font;
    private final Vector2 point = new Vector2();
    private boolean disposed;

    /**
     * Creates a minimap renderer using a caller-owned font.
     *
     * @param font active UI font; ownership remains with the caller/skin
     */
    public LocalMinimapRenderer(BitmapFont font) {
        this.font = Objects.requireNonNull(font, "Minimap font not set");
    }

    /**
     * Renders one compact local-system minimap pass.
     *
     * @param projectionMatrix current Scene2D projection
     * @param layout minimap world-to-screen layout
     * @param snapshot read-only authoritative marker snapshot
     * @param title short human-readable current-system title
     */
    public void render(
            Matrix4 projectionMatrix,
            WorldMapLayout layout,
            LocalMinimapSnapshot snapshot,
            String title) {
        if (disposed || projectionMatrix == null || layout == null || snapshot == null) {
            return;
        }
        shapes.setProjectionMatrix(projectionMatrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(BACKGROUND);
        shapes.rect(layout.getX(), layout.getY(), layout.getWidth(), layout.getHeight());
        for (LocalMinimapSnapshot.Marker marker : snapshot.markers()) {
            if (!layout.containsVisibleWorldPoint(marker.worldX(), marker.worldY())
                    || !layout.worldToScreen(marker.worldX(), marker.worldY(), point)) {
                continue;
            }
            drawMarker(marker.kind(), point.x, point.y);
        }
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(BORDER);
        shapes.rect(layout.getX(), layout.getY(), layout.getWidth(), layout.getHeight());
        shapes.end();

        batch.setProjectionMatrix(projectionMatrix);
        batch.begin();
        font.setColor(Color.WHITE);
        font.draw(batch, title == null ? "LOCAL" : title, layout.getX() + 8f,
                layout.getY() + layout.getHeight() - 8f);
        batch.end();
    }

    private void drawMarker(LocalMinimapSnapshot.Kind kind, float x, float y) {
        switch (kind) {
            case PLAYER -> {
                shapes.setColor(PLAYER);
                shapes.circle(x, y, 5.5f, 12);
                shapes.rect(x - 1f, y - 8f, 2f, 16f);
                shapes.rect(x - 8f, y - 1f, 16f, 2f);
            }
            case STATION -> {
                shapes.setColor(STATION);
                shapes.rect(x - 4f, y - 4f, 8f, 8f);
            }
            case FRIENDLY_FLEET -> {
                shapes.setColor(FRIENDLY);
                shapes.circle(x, y, 3.5f, 10);
            }
            case HOSTILE_FLEET -> {
                shapes.setColor(HOSTILE);
                shapes.triangle(x, y + 5f, x - 5f, y - 4f, x + 5f, y - 4f);
            }
            case OTHER_FLEET -> {
                shapes.setColor(OTHER);
                shapes.circle(x, y, 3f, 8);
            }
            case ASTEROID -> {
                shapes.setColor(ASTEROID);
                shapes.circle(x, y, 2.5f, 8);
            }
            case SALVAGE -> {
                shapes.setColor(SALVAGE);
                shapes.triangle(x, y + 4f, x - 4f, y, x, y - 4f);
                shapes.triangle(x, y + 4f, x + 4f, y, x, y - 4f);
            }
        }
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
}
