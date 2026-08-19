package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.spacesim.ship.LiveTacticalBattleScenario.Side;

import java.util.Locale;
import java.util.Optional;

/** Presentation-only right-side inspection panel with an enlarged schematic selected-ship preview. */
public final class ShipInspectionPanelRenderer {
    private static final float MIN_PANEL_WIDTH_PX = 260f;
    private static final float MAX_PANEL_WIDTH_PX = 390f;
    private static final float PANEL_FRACTION = 0.30f;
    private static final float PAD = 14f;
    private static final Color PANEL_BACKGROUND = new Color(0.018f, 0.028f, 0.044f, 0.97f);
    private static final Color PANEL_BORDER = new Color(0.30f, 0.42f, 0.54f, 0.90f);
    private static final Color TEXT_PRIMARY = new Color(0.88f, 0.94f, 0.98f, 1f);
    private static final Color TEXT_SECONDARY = new Color(0.66f, 0.76f, 0.84f, 1f);
    private static final Color PREVIEW_FILL = new Color(0.15f, 0.20f, 0.26f, 1f);

    private final ShapeRenderer shapes = new ShapeRenderer();
    private final SpriteBatch batch = new SpriteBatch();
    private final BitmapFont font = new BitmapFont();
    private boolean disposed;

    /** Creates the panel resources and a compact readable debug font scale. */
    public ShipInspectionPanelRenderer() {
        font.getData().setScale(0.86f);
    }

    /**
     * Returns the stable right-column width that the tactical map should reserve.
     *
     * @param viewportWidth current window width
     * @return panel width in screen pixels
     */
    public static float panelWidth(float viewportWidth) {
        if (!Float.isFinite(viewportWidth) || viewportWidth <= 0f) {
            return MIN_PANEL_WIDTH_PX;
        }
        return Math.min(MAX_PANEL_WIDTH_PX, Math.max(MIN_PANEL_WIDTH_PX, viewportWidth * PANEL_FRACTION));
    }

    /**
     * Renders the inspection column without mutating tactical state.
     *
     * @param projectionMatrix current UI/camera projection
     * @param viewportWidth current viewport width
     * @param viewportHeight current viewport height
     * @param inspection selected-ship read-only snapshot, or empty when nothing is selected
     */
    public void render(
            Matrix4 projectionMatrix,
            float viewportWidth,
            float viewportHeight,
            Optional<ShipInspectionSnapshot> inspection) {
        if (disposed || projectionMatrix == null || inspection == null) {
            return;
        }
        float width = panelWidth(viewportWidth);
        float left = viewportWidth - width;
        drawPanelBackground(projectionMatrix, left, width, viewportHeight);
        if (inspection.isPresent()) {
            drawPreview(projectionMatrix, inspection.get(), left, width, viewportHeight);
            drawText(projectionMatrix, inspection.get(), left, width, viewportHeight);
        } else {
            drawEmptyText(projectionMatrix, left, viewportHeight);
        }
    }

    /** Releases panel-owned graphics resources. */
    public void dispose() {
        if (!disposed) {
            font.dispose();
            batch.dispose();
            shapes.dispose();
            disposed = true;
        }
    }

    private void drawPanelBackground(Matrix4 matrix, float left, float width, float height) {
        shapes.setProjectionMatrix(matrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(PANEL_BACKGROUND);
        shapes.rect(left, 0f, width, height);
        shapes.end();
        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(PANEL_BORDER);
        shapes.line(left, 0f, left, height);
        shapes.rect(left + 5f, 5f, width - 10f, height - 10f);
        shapes.end();
    }

    private void drawPreview(
            Matrix4 matrix,
            ShipInspectionSnapshot data,
            float left,
            float width,
            float height) {
        float centerX = left + width * 0.5f;
        float centerY = height - 105f;
        float length = Math.min(150f, width * 0.46f);
        float bodyWidth = length * previewWidthScale(data.role());
        TacticalSidePalette.Rgba sideColor = sideColor(data.side());

        shapes.setProjectionMatrix(matrix);
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(sideColor.r(), sideColor.g(), sideColor.b(), 0.95f);
        drawPreviewHull(centerX, centerY, length + 10f, bodyWidth + 8f, data.role());
        shapes.setColor(PREVIEW_FILL);
        drawPreviewHull(centerX, centerY, length, bodyWidth, data.role());
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(sideColor.r(), sideColor.g(), sideColor.b(), 1f);
        shapes.circle(centerX, centerY, Math.max(length, bodyWidth) * 0.63f, 48);
        shapes.line(centerX, centerY + length * 0.62f, centerX, centerY + length * 0.74f);
        shapes.end();
    }

    private void drawPreviewHull(float x, float y, float length, float width, ShipVisualRole role) {
        switch (role) {
            case KINETIC -> {
                shapes.triangle(x, y + length * 0.55f, x - width * 0.28f, y, x, y - length * 0.45f);
                shapes.triangle(x, y + length * 0.55f, x, y - length * 0.45f, x + width * 0.28f, y);
            }
            case MISSILE -> {
                shapes.triangle(x, y + length * 0.50f, x - width * 0.36f, y - length * 0.40f,
                        x + width * 0.36f, y - length * 0.40f);
                shapes.rect(x - width * 0.62f, y - length * 0.20f, width * 0.18f, length * 0.42f);
                shapes.rect(x + width * 0.44f, y - length * 0.20f, width * 0.18f, length * 0.42f);
            }
            case BEAM -> {
                shapes.triangle(x, y + length * 0.52f, x - width * 0.26f, y, x, y - length * 0.46f);
                shapes.triangle(x, y + length * 0.52f, x, y - length * 0.46f, x + width * 0.26f, y);
                shapes.rect(x - width * 0.30f, y + length * 0.04f, width * 0.10f, length * 0.44f);
                shapes.rect(x + width * 0.20f, y + length * 0.04f, width * 0.10f, length * 0.44f);
            }
            case DEFENSIVE_EW -> {
                shapes.triangle(x, y + length * 0.38f, x - width * 0.40f, y, x, y - length * 0.38f);
                shapes.triangle(x, y + length * 0.38f, x, y - length * 0.38f, x + width * 0.40f, y);
                shapes.circle(x - width * 0.55f, y, width * 0.12f, 16);
                shapes.circle(x + width * 0.55f, y, width * 0.12f, 16);
            }
            case BALANCED, UNCLASSIFIED -> shapes.triangle(
                    x,
                    y + length * 0.52f,
                    x - width * 0.46f,
                    y - length * 0.42f,
                    x + width * 0.46f,
                    y - length * 0.42f);
        }
    }

    private void drawText(
            Matrix4 matrix,
            ShipInspectionSnapshot data,
            float left,
            float width,
            float height) {
        batch.setProjectionMatrix(matrix);
        batch.begin();
        float x = left + PAD;
        float y = height - 198f;
        float line = 18f;

        setSideFontColor(data.side());
        font.draw(batch, String.format(Locale.ROOT, "SHIP %d  [%s]", data.entityId(), data.side()), x, y);
        y -= line;
        font.setColor(TEXT_PRIMARY);
        font.draw(batch, data.role() + "  |  " + data.doctrineId(), x, y);
        y -= line;
        font.setColor(TEXT_SECONDARY);
        font.draw(batch, crop("Hull: " + data.hullId(), width), x, y);
        y -= line;
        font.draw(batch, crop("Fit: " + data.fitId(), width), x, y);
        y -= line + 4f;

        font.setColor(TEXT_PRIMARY);
        font.draw(batch, "CONDITION", x, y);
        y -= line;
        font.setColor(TEXT_SECONDARY);
        font.draw(batch, String.format(Locale.ROOT, "Integrity mean/min module: %.3f / %.3f",
                data.meanIntegrity(), data.minimumModuleIntegrity()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Shield: %d emitters, %d collapsed",
                data.shields().emitterCount(), data.shields().collapsedCount()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Shield reserve: %.3e J | min emitter %.3f",
                data.shields().totalReserveJ(), data.shields().minimumEmitterIntegrity()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Bus: %.3e J | heat ship/local %.3e / %.3e J",
                data.sharedBusEnergyJ(), data.shipHeatStoredJ(), data.localHeatStoredJ()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Reaction mass: %.1f kg | ammo: %d",
                data.reactionMassKg(), data.ammunitionCount()), x, y);
        y -= line + 4f;

        font.setColor(TEXT_PRIMARY);
        font.draw(batch, "KINEMATICS", x, y);
        y -= line;
        font.setColor(TEXT_SECONDARY);
        font.draw(batch, String.format(Locale.ROOT, "Pos: %.1f, %.1f m", data.xM(), data.yM()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Vel: %.2f, %.2f m/s | speed %.2f",
                data.velocityXMps(), data.velocityYMps(), data.speedMps()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Heading: %.1f deg", Math.toDegrees(data.headingRad())), x, y);
        y -= line;
        font.draw(batch, crop("Acceleration: " + data.acceleration(), width), x, y);
        y -= line + 4f;

        font.setColor(TEXT_PRIMARY);
        font.draw(batch, "COMBAT / INFORMATION", x, y);
        y -= line;
        font.setColor(TEXT_SECONDARY);
        font.draw(batch, String.format(Locale.ROOT, "Target: %s | fire req/auth %s/%s",
                data.currentTargetId() == 0L ? "NONE" : Long.toString(data.currentTargetId()),
                data.fireRequested(), data.fireAuthorized()), x, y);
        y -= line;
        font.draw(batch, String.format(Locale.ROOT, "Tracks: %d | weapon feeds: %d",
                data.tracks().size(), data.weaponFeeds().size()), x, y);
        y -= line;
        font.draw(batch, crop("AI: " + data.survivalAction() + " / " + data.survivalReason(), width), x, y);
        y -= line;
        font.draw(batch, crop("Formation: " + data.formation(), width), x, y);
        y -= line;
        font.draw(batch, crop("ECM/ECCM: " + data.ecmEccm(), width), x, y);
        y -= line;
        if (!data.weaponFeeds().isEmpty()) {
            WeaponLine first = weaponLine(data.weaponFeeds().get(0));
            font.draw(batch, crop("Feed: " + first.text(), width), x, y);
            y -= line;
        }
        if (!data.tracks().isEmpty()) {
            var track = data.tracks().get(0);
            font.draw(batch, crop(String.format(Locale.ROOT, "Track: %d %s pos=%s",
                    track.targetId(), track.informationState(), track.positionKnown()), width), x, y);
        }
        batch.end();
    }

    private void drawEmptyText(Matrix4 matrix, float left, float height) {
        batch.setProjectionMatrix(matrix);
        batch.begin();
        font.setColor(TEXT_PRIMARY);
        font.draw(batch, "SHIP INSPECTION", left + PAD, height - 30f);
        font.setColor(TEXT_SECONDARY);
        font.draw(batch, "NO SHIP SELECTED", left + PAD, height - 58f);
        font.draw(batch, "Left-click a tactical ship", left + PAD, height - 82f);
        font.draw(batch, "to inspect authoritative state.", left + PAD, height - 100f);
        batch.end();
    }

    private static float previewWidthScale(ShipVisualRole role) {
        return switch (role) {
            case KINETIC -> 0.34f;
            case MISSILE -> 0.68f;
            case BEAM -> 0.38f;
            case DEFENSIVE_EW -> 0.76f;
            case BALANCED, UNCLASSIFIED -> 0.52f;
        };
    }

    private static TacticalSidePalette.Rgba sideColor(Side side) {
        return TacticalSidePalette.outline(side == Side.ALPHA
                ? TacticalPrototypeVisualSnapshot.TacticalSide.ALPHA
                : TacticalPrototypeVisualSnapshot.TacticalSide.BETA);
    }

    private void setSideFontColor(Side side) {
        TacticalSidePalette.Rgba color = sideColor(side);
        font.setColor(color.r(), color.g(), color.b(), color.a());
    }

    private static String crop(String text, float width) {
        int max = Math.max(24, (int) ((width - PAD * 2f) / 6.5f));
        return text.length() <= max ? text : text.substring(0, Math.max(0, max - 3)) + "...";
    }

    private static WeaponLine weaponLine(ShipInspectionSnapshot.WeaponFeed feed) {
        return new WeaponLine(feed.mountId() + "/" + feed.interfaceId() + " -> " + feed.ammunitionContentId());
    }

    private record WeaponLine(String text) { }
}
