package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BeamGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.DamageGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShieldGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;

import java.util.Objects;

/**
 * Shape-based top-down renderer for the Stage-17.5I Tactical Prototype Visual Set.
 *
 * <p>The renderer consumes only an immutable {@link TacticalPrototypeVisualSnapshot}. It has no
 * reference to simulation engines, entities, combat services or persistence and therefore cannot
 * become combat authority. The complete renderer may be replaced by sprites/VFX in Stage 23 without
 * changing any authoritative Stage-17.5 physics.</p>
 */
public final class TacticalPrototypeRenderer {
    private static final float MIN_SHIP_LENGTH_PX = 14f;
    private static final float MIN_SHIP_WIDTH_PX = 8f;
    private static final float MIN_BODY_LENGTH_PX = 5f;
    private static final float MIN_BODY_WIDTH_PX = 2f;
    private static final float MIN_SHIELD_RADIUS_PX = 12f;
    private static final float IMPACT_RADIUS_PX = 5f;
    private static final float DAMAGE_RADIUS_PX = 4f;

    private static final Color SHIP_COLOR = new Color(0.72f, 0.82f, 0.92f, 1f);
    private static final Color WRECK_COLOR = new Color(0.34f, 0.36f, 0.40f, 1f);
    private static final Color THRUSTER_COLOR = new Color(0.45f, 0.75f, 1f, 1f);
    private static final Color KINETIC_COLOR = new Color(1f, 0.82f, 0.42f, 1f);
    private static final Color MISSILE_COLOR = new Color(1f, 0.48f, 0.32f, 1f);
    private static final Color INTERCEPTOR_COLOR = new Color(0.46f, 1f, 0.67f, 1f);
    private static final Color DECOY_COLOR = new Color(0.88f, 0.56f, 1f, 0.92f);
    private static final Color DEBRIS_COLOR = new Color(0.48f, 0.46f, 0.43f, 1f);
    private static final Color BEAM_COLOR = new Color(0.42f, 0.94f, 1f, 1f);
    private static final Color SHIELD_COLOR = new Color(0.35f, 0.72f, 1f, 0.9f);
    private static final Color SHIELD_COLLAPSED_COLOR = new Color(0.30f, 0.38f, 0.48f, 0.55f);
    private static final Color SHIELD_IMPACT_COLOR = new Color(0.55f, 0.86f, 1f, 1f);
    private static final Color ARMOR_IMPACT_COLOR = new Color(1f, 0.72f, 0.30f, 1f);
    private static final Color PENETRATION_COLOR = new Color(1f, 0.30f, 0.24f, 1f);
    private static final Color DAMAGE_COLOR = new Color(0.96f, 0.28f, 0.20f, 1f);

    private final ShapeRenderer shapes;
    private final Vector2 a = new Vector2();
    private final Vector2 b = new Vector2();
    private boolean disposed;

    /** Creates libGDX shape resources; call only after a graphics context exists. */
    public TacticalPrototypeRenderer() {
        this.shapes = new ShapeRenderer();
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
        shapes.setProjectionMatrix(projectionMatrix);
        drawTrailsAndBeams(layout, snapshot);
        drawShields(layout, snapshot);
        drawShipsAndBodies(layout, snapshot);
        drawImpactsAndDamage(layout, snapshot);
    }

    /** Releases renderer-owned libGDX resources. */
    public void dispose() {
        if (!disposed) {
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
            float radius = Math.max(MIN_SHIELD_RADIUS_PX, screenLength(layout, shield.radiusM()));
            Color base = shield.collapsed() ? SHIELD_COLLAPSED_COLOR : SHIELD_COLOR;
            float alpha = shield.collapsed() ? base.a : (float) (0.25d + 0.75d * shield.reserveFraction());
            shapes.setColor(base.r, base.g, base.b, alpha);
            float startDegrees = (float) Math.toDegrees(shield.centerRad() - shield.halfArcRad());
            float sweepDegrees = (float) Math.toDegrees(shield.halfArcRad() * 2d);
            shapes.arc(a.x, a.y, radius, startDegrees, sweepDegrees, 48);
        }
        shapes.end();
    }

    private void drawShipsAndBodies(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (ShipGlyph ship : snapshot.ships()) {
            if (!project(layout, ship.xM(), ship.yM(), a)) {
                continue;
            }
            drawShip(layout, ship, a.x, a.y);
        }
        for (BodyGlyph body : snapshot.bodies()) {
            if (!project(layout, body.xM(), body.yM(), a)) {
                continue;
            }
            drawBody(layout, body, a.x, a.y);
        }
        shapes.end();
    }

    private void drawShip(WorldMapLayout layout, ShipGlyph ship, float x, float y) {
        float length = Math.max(MIN_SHIP_LENGTH_PX, screenLength(layout, ship.lengthM()));
        float width = Math.max(MIN_SHIP_WIDTH_PX, screenLength(layout, ship.widthM()));
        float cos = (float) Math.cos(ship.headingRad());
        float sin = (float) Math.sin(ship.headingRad());
        float noseX = x + cos * length * 0.55f;
        float noseY = y + sin * length * 0.55f;
        float rearX = x - cos * length * 0.45f;
        float rearY = y - sin * length * 0.45f;
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;

        shapes.setColor(ship.wreck() ? WRECK_COLOR : SHIP_COLOR);
        shapes.triangle(noseX, noseY, rearX + sideX, rearY + sideY, rearX - sideX, rearY - sideY);

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
    }

    private void drawBody(WorldMapLayout layout, BodyGlyph body, float x, float y) {
        float length = Math.max(MIN_BODY_LENGTH_PX, screenLength(layout, body.lengthM()));
        float width = Math.max(MIN_BODY_WIDTH_PX, screenLength(layout, body.widthM()));
        float cos = (float) Math.cos(body.headingRad());
        float sin = (float) Math.sin(body.headingRad());
        float sideX = -sin * width * 0.5f;
        float sideY = cos * width * 0.5f;
        shapes.setColor(bodyColor(body));
        switch (body.kind()) {
            case KINETIC_PROJECTILE -> shapes.circle(x, y, Math.max(1.5f, width * 0.5f), 10);
            case GUIDED_MISSILE, INTERCEPTOR -> shapes.triangle(
                    x + cos * length * 0.55f,
                    y + sin * length * 0.55f,
                    x - cos * length * 0.45f + sideX,
                    y - sin * length * 0.45f + sideY,
                    x - cos * length * 0.45f - sideX,
                    y - sin * length * 0.45f - sideY);
            case DECOY -> {
                shapes.circle(x, y, Math.max(3f, width * 0.45f), 16);
                shapes.circle(x, y, Math.max(1.5f, width * 0.18f), 12);
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
