package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BeamGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.DamageGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShieldGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.TacticalSide;
import com.spacesim.ui.TacticalSidePalette.Rgba;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ResolvedSprite;
import com.spacesim.presentation.asset.Stage20MinimumPlayableTextureRenderer;
import com.spacesim.world.Stage20SpecialLocationWorld.LocationKind;

import java.util.Objects;

/**
 * Top-down renderer for the Stage-17.5I/19J Tactical Prototype Visual Set.
 *
 * <p>The renderer consumes only an immutable {@link TacticalPrototypeVisualSnapshot}. It has no
 * reference to simulation engines, entities, combat services or persistence and therefore cannot
 * become combat authority. The complete renderer may be replaced by sprites/VFX in Stage 23 without
 * changing any authoritative combat physics. The compatibility constructor keeps schematic hulls;
 * {@link #withMinimumPlayableSprites()} replaces only those hull bodies with the Stage-20.5E pack.</p>
 */
public final class TacticalPrototypeRenderer {
    private static final float MIN_SHIP_LENGTH_PX = 18f;
    private static final float MIN_SHIP_WIDTH_PX = 11f;
    private static final float MIN_BODY_LENGTH_PX = 7f;
    private static final float MIN_BODY_WIDTH_PX = 3f;
    private static final float MIN_SHIELD_RADIUS_PX = 12f;
    private static final float IMPACT_RADIUS_PX = 5f;
    private static final float DAMAGE_RADIUS_PX = 4f;
    private static final float INNER_SHIP_SCALE = 0.72f;
    private static final float INNER_BODY_SCALE = 0.62f;

    private static final Color WRECK_FILL_COLOR = new Color(0.28f, 0.30f, 0.34f, 1f);
    private static final Color WRECK_OUTLINE_COLOR = new Color(0.62f, 0.64f, 0.68f, 1f);
    private static final Color THRUSTER_COLOR = new Color(0.45f, 0.75f, 1f, 1f);
    private static final Color KINETIC_COLOR = new Color(1f, 0.82f, 0.42f, 1f);
    private static final Color MISSILE_COLOR = new Color(1f, 0.48f, 0.32f, 1f);
    private static final Color INTERCEPTOR_COLOR = new Color(0.46f, 1f, 0.67f, 1f);
    private static final Color DECOY_COLOR = new Color(0.88f, 0.56f, 1f, 0.92f);
    private static final Color DEBRIS_COLOR = new Color(0.48f, 0.46f, 0.43f, 1f);
    private static final Color BODY_OUTLINE_COLOR = new Color(0.91f, 0.94f, 0.98f, 0.92f);
    private static final Color BEAM_COLOR = new Color(0.42f, 0.94f, 1f, 1f);
    private static final Color SHIELD_COLOR = new Color(0.35f, 0.72f, 1f, 0.9f);
    private static final Color SHIELD_COLLAPSED_COLOR = new Color(0.30f, 0.38f, 0.48f, 0.55f);
    private static final Color SHIELD_IMPACT_COLOR = new Color(0.55f, 0.86f, 1f, 1f);
    private static final Color ARMOR_IMPACT_COLOR = new Color(1f, 0.72f, 0.30f, 1f);
    private static final Color PENETRATION_COLOR = new Color(1f, 0.30f, 0.24f, 1f);
    private static final Color DAMAGE_COLOR = new Color(0.96f, 0.28f, 0.20f, 1f);

    private final ShapeRenderer shapes;
    private final SpriteBatch spriteBatch;
    private final Stage20MinimumPlayableTextureRenderer minimumSprites;
    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();
    private boolean disposed;

    /** Creates libGDX shape resources; call only after a graphics context exists. */
    public TacticalPrototypeRenderer() {
        this(false);
    }

    private TacticalPrototypeRenderer(boolean useMinimumPlayableSprites) {
        this.shapes = new ShapeRenderer();
        this.spriteBatch = useMinimumPlayableSprites ? new SpriteBatch() : null;
        this.minimumSprites = useMinimumPlayableSprites
                ? new Stage20MinimumPlayableTextureRenderer()
                : null;
    }

    /**
     * Creates the existing tactical renderer with Stage-20.5E production sprite binding enabled.
     *
     * @return renderer consuming the same immutable tactical snapshots with sprite ship bodies
     */
    public static TacticalPrototypeRenderer withMinimumPlayableSprites() {
        return new TacticalPrototypeRenderer(true);
    }

    /**
     * Renders one immutable tactical presentation frame.
     *
     * @param projectionMatrix current camera/UI projection matrix
     * @param layout world-to-screen mapping used by the tactical view
     * @param snapshot presentation-only tactical snapshot
     */
    public void render(
            Matrix4 projectionMatrix,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot) {
        if (disposed || projectionMatrix == null || layout == null || snapshot == null) {
            return;
        }
        if (minimumSprites != null) {
            drawSpriteShips(projectionMatrix, layout, snapshot);
        }
        shapes.setProjectionMatrix(projectionMatrix);
        drawTrailsAndBeams(layout, snapshot);
        drawShields(layout, snapshot);
        drawShipsAndBodies(layout, snapshot, minimumSprites == null);
        drawShipCues(layout, snapshot);
        drawImpactsAndDamage(layout, snapshot);
    }

    /** Releases renderer-owned libGDX resources. */
    public void dispose() {
        if (!disposed) {
            if (minimumSprites != null) {
                minimumSprites.dispose();
                spriteBatch.dispose();
            }
            shapes.dispose();
            disposed = true;
        }
    }

    private void drawTrailsAndBeams(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (BeamGlyph beam : snapshot.beams()) {
            if (project(layout, beam.startXM(), beam.startYM(), a)
                    && project(layout, beam.endXM(), beam.endYM(), b)) {
                shapes.setColor(BEAM_COLOR);
                shapes.line(a, b);
                shapes.line(a.x, a.y + 1f, b.x, b.y + 1f);
            }
        }
        for (BodyGlyph body : snapshot.bodies()) {
            if (body.trailLengthM() <= 0d || !project(layout, body.xM(), body.yM(), a)) {
                continue;
            }
            double tailX = body.xM() - Math.cos(body.headingRad()) * body.trailLengthM();
            double tailY = body.yM() - Math.sin(body.headingRad()) * body.trailLengthM();
            if (!project(layout, tailX, tailY, b)) {
                continue;
            }
            shapes.setColor(bodyColor(body));
            shapes.line(a, b);
        }
        shapes.end();
    }

    private void drawShields(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ShieldGlyph shield : snapshot.shields()) {
            if (!project(layout, shield.xM(), shield.yM(), a)) {
                continue;
            }
            float centerX = a.x;
            float centerY = a.y;
            float radius = Math.max(MIN_SHIELD_RADIUS_PX, screenLength(layout, shield.radiusM()));
            Color base = shield.collapsed() ? SHIELD_COLLAPSED_COLOR : SHIELD_COLOR;
            float alpha = shield.collapsed() ? base.a : (float) (0.25d + 0.75d * shield.reserveFraction());
            shapes.setColor(base.r, base.g, base.b, alpha);
            float startDegrees = (float) Math.toDegrees(shield.centerRad() - shield.halfArcRad());
            float sweepDegrees = (float) Math.toDegrees(shield.halfArcRad() * 2d);
            shapes.arc(centerX, centerY, radius, startDegrees, sweepDegrees, 48);
        }
        shapes.end();
    }

    private void drawShipsAndBodies(
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot,
            boolean drawSchematicShips) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        if (drawSchematicShips) {
            for (ShipGlyph ship : snapshot.ships()) {
                if (!project(layout, ship.xM(), ship.yM(), a)) {
                    continue;
                }
                drawShip(layout, ship, a.x, a.y);
            }
        }
        for (BodyGlyph body : snapshot.bodies()) {
            if (!project(layout, body.xM(), body.yM(), a)) {
                continue;
            }
            drawBody(layout, body, a.x, a.y);
        }
        shapes.end();
    }

    private void drawSpriteShips(
            Matrix4 projectionMatrix,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot) {
        spriteBatch.setProjectionMatrix(projectionMatrix);
        spriteBatch.setColor(Color.WHITE);
        spriteBatch.begin();
        for (ShipGlyph ship : snapshot.ships()) {
            if (!project(layout, ship.xM(), ship.yM(), a)) {
                continue;
            }
            ResolvedSprite resolved = ship.wreck()
                    ? Stage20MinimumPlayableSpriteCatalog.resolveSpecialLocation(LocationKind.DERELICT)
                    : Stage20MinimumPlayableSpriteCatalog.resolveCombatRole(ship.role());
            float length = Math.max(MIN_SHIP_LENGTH_PX, screenLength(layout, ship.lengthM()))
                    * roleLengthScale(ship.role());
            float width = Math.max(MIN_SHIP_WIDTH_PX, screenLength(layout, ship.widthM()))
                    * roleWidthScale(ship.role());
            minimumSprites.draw(
                    spriteBatch,
                    resolved.binding(),
                    a.x,
                    a.y,
                    length,
                    width,
                    (float) Math.toDegrees(ship.headingRad()));
        }
        spriteBatch.end();
        spriteBatch.setColor(Color.WHITE);
    }

    private void drawShip(WorldMapLayout layout, ShipGlyph ship, float x, float y) {
        float baseLength = Math.max(MIN_SHIP_LENGTH_PX, screenLength(layout, ship.lengthM()));
        float baseWidth = Math.max(MIN_SHIP_WIDTH_PX, screenLength(layout, ship.widthM()));
        float length = baseLength * roleLengthScale(ship.role());
        float width = baseWidth * roleWidthScale(ship.role());
        float cos = (float) Math.cos(ship.headingRad());
        float sin = (float) Math.sin(ship.headingRad());
        float rearX = x - cos * length * 0.45f;
        float rearY = y - sin * length * 0.45f;
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;

        if (!ship.wreck() && ship.thrustFraction() > 0d) {
            float plume = (float) (length * (0.18d + 0.42d * ship.thrustFraction()));
            shapes.setColor(THRUSTER_COLOR.r, THRUSTER_COLOR.g, THRUSTER_COLOR.b,
                    (float) (0.35d + 0.65d * ship.thrustFraction()));
            shapes.triangle(
                    rearX + sideX * 0.45f,
                    rearY + sideY * 0.45f,
                    rearX - sideX * 0.45f,
                    rearY - sideY * 0.45f,
                    rearX - cos * plume,
                    rearY - sin * plume);
        }

        if (ship.wreck()) {
            shapes.setColor(WRECK_OUTLINE_COLOR);
        } else {
            setColor(TacticalSidePalette.outline(ship.side()));
        }
        drawRoleHull(x, y, cos, sin, length, width, ship.role());

        if (ship.wreck()) {
            shapes.setColor(WRECK_FILL_COLOR);
        } else {
            setColor(TacticalSidePalette.fill(ship.side()));
        }
        drawRoleHull(
                x,
                y,
                cos,
                sin,
                length * INNER_SHIP_SCALE,
                width * INNER_SHIP_SCALE,
                ship.role());
    }

    private void drawRoleHull(
            float x,
            float y,
            float cos,
            float sin,
            float length,
            float width,
            ShipVisualRole role) {
        switch (role) {
            case KINETIC -> drawDiamondHull(x, y, cos, sin, length, width * 0.72f);
            case MISSILE -> {
                drawTriangleHull(x, y, cos, sin, length * 0.92f, width * 0.78f);
                drawMissilePods(x, y, cos, sin, length, width);
            }
            case BEAM -> {
                drawDiamondHull(x, y, cos, sin, length, width * 0.62f);
                drawBeamProngs(x, y, cos, sin, length, width);
            }
            case DEFENSIVE_EW -> {
                drawDiamondHull(x, y, cos, sin, length * 0.78f, width * 0.92f);
                drawDefensiveNodes(x, y, cos, sin, width);
            }
            case BALANCED, UNCLASSIFIED -> drawTriangleHull(x, y, cos, sin, length, width);
        }
    }

    private void drawShipCues(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        shapes.begin(ShapeRenderer.ShapeType.Line);
        for (ShipGlyph ship : snapshot.ships()) {
            if (ship.wreck() || !project(layout, ship.xM(), ship.yM(), a)) {
                continue;
            }
            float centerX = a.x;
            float centerY = a.y;
            float baseLength = Math.max(MIN_SHIP_LENGTH_PX, screenLength(layout, ship.lengthM()));
            float baseWidth = Math.max(MIN_SHIP_WIDTH_PX, screenLength(layout, ship.widthM()));
            float length = baseLength * roleLengthScale(ship.role());
            float width = baseWidth * roleWidthScale(ship.role());
            float cos = (float) Math.cos(ship.headingRad());
            float sin = (float) Math.sin(ship.headingRad());
            setColor(TacticalSidePalette.outline(ship.side()));
            if (ship.side() != TacticalSide.NEUTRAL) {
                drawTransverseCue(centerX, centerY, cos, sin, length, width, -0.19f);
                if (ship.side() == TacticalSide.BETA) {
                    drawTransverseCue(centerX, centerY, cos, sin, length, width, -0.05f);
                }
            }
            drawRoleCue(centerX, centerY, cos, sin, length, width, ship.role());
        }
        shapes.end();
    }

    private void drawRoleCue(
            float x,
            float y,
            float cos,
            float sin,
            float length,
            float width,
            ShipVisualRole role) {
        float sideX = -sin;
        float sideY = cos;
        switch (role) {
            case KINETIC -> shapes.line(
                    x + cos * length * 0.12f,
                    y + sin * length * 0.12f,
                    x + cos * length * 0.70f,
                    y + sin * length * 0.70f);
            case MISSILE -> {
                float alongX = x - cos * length * 0.02f;
                float alongY = y - sin * length * 0.02f;
                shapes.circle(alongX + sideX * width * 0.54f, alongY + sideY * width * 0.54f,
                        Math.max(1.8f, width * 0.12f), 10);
                shapes.circle(alongX - sideX * width * 0.54f, alongY - sideY * width * 0.54f,
                        Math.max(1.8f, width * 0.12f), 10);
            }
            case BEAM -> {
                float startForward = length * 0.12f;
                float endForward = length * 0.68f;
                float offset = width * 0.26f;
                shapes.line(
                        x + cos * startForward + sideX * offset,
                        y + sin * startForward + sideY * offset,
                        x + cos * endForward + sideX * offset,
                        y + sin * endForward + sideY * offset);
                shapes.line(
                        x + cos * startForward - sideX * offset,
                        y + sin * startForward - sideY * offset,
                        x + cos * endForward - sideX * offset,
                        y + sin * endForward - sideY * offset);
            }
            case DEFENSIVE_EW -> {
                float offset = width * 0.58f;
                float radius = Math.max(2f, width * 0.13f);
                shapes.circle(x + sideX * offset, y + sideY * offset, radius, 12);
                shapes.circle(x - sideX * offset, y - sideY * offset, radius, 12);
            }
            case BALANCED -> {
                float radius = Math.max(1.6f, width * 0.09f);
                shapes.circle(x, y, radius, 10);
            }
            case UNCLASSIFIED -> {
                // No role cue: legacy callers retain the generic silhouette without inferred semantics.
            }
        }
    }

    private void drawTransverseCue(
            float x,
            float y,
            float cos,
            float sin,
            float length,
            float width,
            float longitudinalFraction) {
        float centerX = x + cos * length * longitudinalFraction;
        float centerY = y + sin * length * longitudinalFraction;
        float sideX = -sin * width * 0.30f;
        float sideY = cos * width * 0.30f;
        shapes.line(centerX + sideX, centerY + sideY, centerX - sideX, centerY - sideY);
    }

    private void drawTriangleHull(float x, float y, float cos, float sin, float length, float width) {
        float noseX = x + cos * length * 0.55f;
        float noseY = y + sin * length * 0.55f;
        float rearX = x - cos * length * 0.45f;
        float rearY = y - sin * length * 0.45f;
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;
        shapes.triangle(noseX, noseY, rearX + sideX, rearY + sideY, rearX - sideX, rearY - sideY);
    }

    private void drawDiamondHull(float x, float y, float cos, float sin, float length, float width) {
        float noseX = x + cos * length * 0.56f;
        float noseY = y + sin * length * 0.56f;
        float rearX = x - cos * length * 0.44f;
        float rearY = y - sin * length * 0.44f;
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;
        shapes.triangle(noseX, noseY, x + sideX, y + sideY, x - sideX, y - sideY);
        shapes.triangle(rearX, rearY, x - sideX, y - sideY, x + sideX, y + sideY);
    }

    private void drawMissilePods(float x, float y, float cos, float sin, float length, float width) {
        float sideX = -sin;
        float sideY = cos;
        float podCenterX = x - cos * length * 0.03f;
        float podCenterY = y - sin * length * 0.03f;
        float podLength = length * 0.42f;
        float podWidth = Math.max(2.4f, width * 0.20f);
        float offset = width * 0.48f;
        drawOrientedBox(
                podCenterX + sideX * offset,
                podCenterY + sideY * offset,
                podLength,
                podWidth,
                (float) Math.toDegrees(Math.atan2(sin, cos)));
        drawOrientedBox(
                podCenterX - sideX * offset,
                podCenterY - sideY * offset,
                podLength,
                podWidth,
                (float) Math.toDegrees(Math.atan2(sin, cos)));
    }

    private void drawBeamProngs(float x, float y, float cos, float sin, float length, float width) {
        float sideX = -sin;
        float sideY = cos;
        float prongLength = length * 0.46f;
        float prongWidth = Math.max(1.8f, width * 0.12f);
        float forward = length * 0.32f;
        float offset = width * 0.26f;
        float centerX = x + cos * forward;
        float centerY = y + sin * forward;
        float rotation = (float) Math.toDegrees(Math.atan2(sin, cos));
        drawOrientedBox(centerX + sideX * offset, centerY + sideY * offset, prongLength, prongWidth, rotation);
        drawOrientedBox(centerX - sideX * offset, centerY - sideY * offset, prongLength, prongWidth, rotation);
    }

    private void drawDefensiveNodes(float x, float y, float cos, float sin, float width) {
        float sideX = -sin;
        float sideY = cos;
        float offset = width * 0.56f;
        float radius = Math.max(2.4f, width * 0.16f);
        shapes.circle(x + sideX * offset, y + sideY * offset, radius, 14);
        shapes.circle(x - sideX * offset, y - sideY * offset, radius, 14);
    }

    private void drawOrientedBox(float centerX, float centerY, float length, float width, float rotationDeg) {
        shapes.rect(
                centerX - length * 0.5f,
                centerY - width * 0.5f,
                length * 0.5f,
                width * 0.5f,
                length,
                width,
                1f,
                1f,
                rotationDeg);
    }

    private static float roleLengthScale(ShipVisualRole role) {
        return switch (role) {
            case KINETIC -> 1.18f;
            case MISSILE -> 0.96f;
            case BEAM -> 1.22f;
            case DEFENSIVE_EW -> 0.84f;
            case BALANCED, UNCLASSIFIED -> 1f;
        };
    }

    private static float roleWidthScale(ShipVisualRole role) {
        return switch (role) {
            case KINETIC -> 0.72f;
            case MISSILE -> 1.22f;
            case BEAM -> 0.74f;
            case DEFENSIVE_EW -> 1.34f;
            case BALANCED, UNCLASSIFIED -> 1f;
        };
    }

    private void drawBody(WorldMapLayout layout, BodyGlyph body, float x, float y) {
        float length = Math.max(MIN_BODY_LENGTH_PX, screenLength(layout, body.lengthM()));
        float width = Math.max(MIN_BODY_WIDTH_PX, screenLength(layout, body.widthM()));
        shapes.setColor(BODY_OUTLINE_COLOR);
        drawBodyShape(body, x, y, length, width);
        shapes.setColor(bodyColor(body));
        drawBodyShape(body, x, y, length * INNER_BODY_SCALE, width * INNER_BODY_SCALE);
    }

    private void drawBodyShape(BodyGlyph body, float x, float y, float length, float width) {
        float cos = (float) Math.cos(body.headingRad());
        float sin = (float) Math.sin(body.headingRad());
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;
        switch (body.kind()) {
            case KINETIC_PROJECTILE -> shapes.circle(x, y, Math.max(1.8f, width * 0.5f), 10);
            case GUIDED_MISSILE, INTERCEPTOR -> shapes.triangle(
                    x + cos * length * 0.55f,
                    y + sin * length * 0.55f,
                    x - cos * length * 0.45f + sideX,
                    y - sin * length * 0.45f + sideY,
                    x - cos * length * 0.45f - sideX,
                    y - sin * length * 0.45f - sideY);
            case DECOY -> {
                shapes.circle(x, y, Math.max(3.4f, width * 0.45f), 16);
                shapes.circle(x, y, Math.max(1.8f, width * 0.18f), 12);
            }
            case DEBRIS -> shapes.rect(
                    x - length * 0.5f,
                    y - width * 0.5f,
                    length * 0.5f,
                    width * 0.5f,
                    length,
                    width,
                    1f,
                    1f,
                    (float) Math.toDegrees(body.headingRad()));
        }
    }

    private void drawImpactsAndDamage(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (ImpactGlyph impact : snapshot.impacts()) {
            if (!project(layout, impact.xM(), impact.yM(), a)) {
                continue;
            }
            shapes.setColor(switch (impact.kind()) {
                case SHIELD -> SHIELD_IMPACT_COLOR;
                case ARMOR -> ARMOR_IMPACT_COLOR;
                case PENETRATION -> PENETRATION_COLOR;
            });
            shapes.circle(a.x, a.y, IMPACT_RADIUS_PX, 18);
        }
        for (DamageGlyph marker : snapshot.damage()) {
            if (!project(layout, marker.xM(), marker.yM(), a)) {
                continue;
            }
            shapes.setColor(DAMAGE_COLOR.r, DAMAGE_COLOR.g, DAMAGE_COLOR.b,
                    (float) (0.25d + 0.75d * marker.severity()));
            shapes.circle(a.x, a.y, DAMAGE_RADIUS_PX + (float) (3d * marker.severity()), 16);
        }
        shapes.end();
    }

    private void setColor(Rgba color) {
        shapes.setColor(color.r(), color.g(), color.b(), color.a());
    }

    private static Color bodyColor(BodyGlyph body) {
        return switch (body.kind()) {
            case KINETIC_PROJECTILE -> KINETIC_COLOR;
            case GUIDED_MISSILE -> MISSILE_COLOR;
            case INTERCEPTOR -> INTERCEPTOR_COLOR;
            case DECOY -> DECOY_COLOR;
            case DEBRIS -> DEBRIS_COLOR;
        };
    }

    private static boolean project(WorldMapLayout layout, double xM, double yM, Vector2 output) {
        if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
            return false;
        }
        return layout.worldToScreen((float) xM, (float) yM, output);
    }

    private float screenLength(WorldMapLayout layout, double worldLengthM) {
        Objects.requireNonNull(layout, "layout");
        if (!Double.isFinite(worldLengthM) || worldLengthM <= 0d) {
            return 0f;
        }
        if (!layout.worldToScreen(0f, 0f, a)
                || !layout.worldToScreen((float) worldLengthM, 0f, b)) {
            return 0f;
        }
        return Math.abs(b.x - a.x);
    }
}
