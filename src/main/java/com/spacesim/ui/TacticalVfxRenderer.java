package com.spacesim.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BeamGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.DamageGlyph;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ShipGlyph;
import com.spacesim.ui.TacticalVfxState.Flash;
import com.spacesim.ui.TacticalVfxState.FlashKind;
import com.spacesim.ui.TacticalVfxState.Particle;
import com.spacesim.ui.TacticalVfxState.ParticleKind;

/** Presentation-only additive bloom and bounded particle pass for the tactical view. */
final class TacticalVfxRenderer {
    private static final Color ENGINE_GLOW = new Color(0.42f, 0.76f, 1.00f, 1f);
    private static final Color ENGINE_CORE = new Color(0.82f, 0.94f, 1.00f, 1f);
    private static final Color MISSILE_GLOW = new Color(1.00f, 0.45f, 0.20f, 1f);
    private static final Color INTERCEPTOR_GLOW = new Color(0.42f, 1.00f, 0.72f, 1f);
    private static final Color DECOY_GLOW = new Color(0.82f, 0.48f, 1.00f, 1f);
    private static final Color BEAM_GLOW = new Color(0.42f, 0.94f, 1.00f, 1f);
    private static final Color SHIELD_GLOW = new Color(0.48f, 0.84f, 1.00f, 1f);
    private static final Color ARMOR_GLOW = new Color(1.00f, 0.68f, 0.24f, 1f);
    private static final Color PENETRATION_GLOW = new Color(1.00f, 0.25f, 0.16f, 1f);
    private static final Color DESTRUCTION_GLOW = new Color(1.00f, 0.46f, 0.12f, 1f);
    private static final Color DESTRUCTION_CORE = new Color(1.00f, 0.93f, 0.70f, 1f);
    private static final Color DAMAGE_GLOW = new Color(1.00f, 0.20f, 0.12f, 1f);
    private static final Color FRAGMENT_BODY = new Color(0.78f, 0.56f, 0.34f, 0.90f);

    private static final float BLOOM_MIN_PX = 4f;
    private static final float BLOOM_MAX_PX = 180f;
    private static final float PARTICLE_MAX_PX = 18f;

    private final SpriteBatch batch = new SpriteBatch();
    private final Texture radialGlow = createRadialGlowTexture(64);
    private final Texture pixel = createPixelTexture();
    private final TacticalVfxState state = new TacticalVfxState();
    private final Vector2 projected = new Vector2();
    private boolean disposed;

    void render(
            Matrix4 projectionMatrix,
            WorldMapLayout layout,
            TacticalPrototypeVisualSnapshot snapshot) {
        if (disposed || projectionMatrix == null || layout == null || snapshot == null) {
            return;
        }
        state.advance(snapshot, frameSeconds());
        batch.setProjectionMatrix(projectionMatrix);

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        batch.begin();
        drawContinuousBloom(layout, snapshot);
        drawFlashes(layout);
        drawParticleGlow(layout);
        batch.end();

        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.begin();
        drawHotFragments(layout);
        batch.end();
        batch.setColor(Color.WHITE);
    }

    void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        radialGlow.dispose();
        pixel.dispose();
        batch.dispose();
    }

    TacticalVfxState stateForTesting() {
        return state;
    }

    private void drawContinuousBloom(WorldMapLayout layout, TacticalPrototypeVisualSnapshot snapshot) {
        for (ShipGlyph ship : snapshot.ships()) {
            if (ship.wreck() || ship.thrustFraction() <= 1e-9d) {
                continue;
            }
            double cos = Math.cos(ship.headingRad());
            double sin = Math.sin(ship.headingRad());
            double rearX = ship.xM() - cos * ship.lengthM() * 0.49d;
            double rearY = ship.yM() - sin * ship.lengthM() * 0.49d;
            double intensity = Math.sqrt(ship.thrustFraction());
            double radiusM = Math.max(ship.widthM() * 0.38d, ship.lengthM() * 0.055d)
                    * (0.75d + intensity * 0.85d);
            drawWorldGlow(layout, rearX, rearY, radiusM * 1.75d,
                    ENGINE_GLOW, (float) (0.12d + 0.26d * intensity), 6f, BLOOM_MAX_PX);
            drawWorldGlow(layout, rearX, rearY, radiusM * 0.62d,
                    ENGINE_CORE, (float) (0.30d + 0.45d * intensity), 3f, 72f);
        }

        for (BodyGlyph body : snapshot.bodies()) {
            if (body.kind() != BodyKind.GUIDED_MISSILE
                    && body.kind() != BodyKind.INTERCEPTOR
                    && body.kind() != BodyKind.DECOY) {
                continue;
            }
            double cos = Math.cos(body.headingRad());
            double sin = Math.sin(body.headingRad());
            double tailX = body.xM() - cos * body.lengthM() * 0.46d;
            double tailY = body.yM() - sin * body.lengthM() * 0.46d;
            Color color = switch (body.kind()) {
                case GUIDED_MISSILE -> MISSILE_GLOW;
                case INTERCEPTOR -> INTERCEPTOR_GLOW;
                case DECOY -> DECOY_GLOW;
                default -> MISSILE_GLOW;
            };
            double radiusM = Math.max(body.widthM() * 1.4d, body.lengthM() * 0.18d);
            drawWorldGlow(layout, tailX, tailY, radiusM, color,
                    body.kind() == BodyKind.DECOY ? 0.16f : 0.34f, 4f, 54f);
        }

        for (BeamGlyph beam : snapshot.beams()) {
            double energyScale = clamp(Math.log10(1d + beam.deliveredEnergyJ()) / 9d, 0.20d, 1.0d);
            double radiusM = Math.max(beam.spotRadiusM() * 2.2d, 1.5d + 4.0d * energyScale);
            drawWorldGlow(layout, beam.startXM(), beam.startYM(), radiusM,
                    BEAM_GLOW, (float) (0.10d + 0.22d * energyScale), 3f, 42f);
            drawWorldGlow(layout, beam.endXM(), beam.endYM(), radiusM * 1.35d,
                    BEAM_GLOW, (float) (0.12d + 0.28d * energyScale), 4f, 58f);
        }

        for (DamageGlyph damage : snapshot.damage()) {
            if (damage.severity() < 0.60d) {
                continue;
            }
            double normalized = (damage.severity() - 0.60d) / 0.40d;
            drawWorldGlow(layout, damage.xM(), damage.yM(), 3.5d + normalized * 5.5d,
                    DAMAGE_GLOW, (float) (0.06d + normalized * 0.13d), 4f, 34f);
        }
    }

    private void drawFlashes(WorldMapLayout layout) {
        for (Flash flash : state.flashes()) {
            double remaining = flash.remainingFraction();
            if (remaining <= 0d) {
                continue;
            }
            Color color = flashColor(flash.kind());
            float alpha = (float) (remaining * remaining * flashBaseAlpha(flash.kind()));
            drawWorldGlow(layout, flash.xM(), flash.yM(), flash.radiusM(), color,
                    alpha, 6f, flash.kind() == FlashKind.DESTRUCTION ? 220f : 92f);
            if (flash.kind() == FlashKind.DESTRUCTION) {
                drawWorldGlow(layout, flash.xM(), flash.yM(), flash.radiusM() * 0.38d,
                        DESTRUCTION_CORE, (float) (0.78d * remaining), 5f, 110f);
            }
        }
    }

    private void drawParticleGlow(WorldMapLayout layout) {
        for (Particle particle : state.particles()) {
            double remaining = particle.remainingFraction();
            if (remaining <= 0d) {
                continue;
            }
            Color color = particleColor(particle.kind());
            float alpha = (float) (particleBaseAlpha(particle.kind()) * remaining);
            double radius = particle.radiusM() * (particle.kind() == ParticleKind.PLASMA ? 2.8d : 1.8d);
            drawWorldGlow(layout, particle.xM(), particle.yM(), radius,
                    color, alpha, 2f, PARTICLE_MAX_PX);
        }
    }

    private void drawHotFragments(WorldMapLayout layout) {
        for (Particle particle : state.particles()) {
            if (particle.kind() != ParticleKind.HOT_FRAGMENT
                    || !project(layout, particle.xM(), particle.yM())) {
                continue;
            }
            float size = clampScreenSize(screenLength(layout, particle.radiusM()) * 0.85f, 1.2f, 5.5f);
            float length = size * 2.4f;
            float rotation = (float) Math.toDegrees(Math.atan2(particle.vyMps(), particle.vxMps()));
            batch.setColor(FRAGMENT_BODY.r, FRAGMENT_BODY.g, FRAGMENT_BODY.b,
                    (float) (FRAGMENT_BODY.a * particle.remainingFraction()));
            batch.draw(
                    pixel,
                    projected.x - length * 0.5f,
                    projected.y - size * 0.5f,
                    length * 0.5f,
                    size * 0.5f,
                    length,
                    size,
                    1f,
                    1f,
                    rotation,
                    0,
                    0,
                    pixel.getWidth(),
                    pixel.getHeight(),
                    false,
                    false);
        }
    }

    private void drawWorldGlow(
            WorldMapLayout layout,
            double xM,
            double yM,
            double radiusM,
            Color color,
            float alpha,
            float minPixels,
            float maxPixels) {
        if (alpha <= 0f || radiusM <= 0d || !project(layout, xM, yM)) {
            return;
        }
        float radiusPixels = clampScreenSize(screenLength(layout, radiusM), minPixels, maxPixels);
        float size = radiusPixels * 2f;
        batch.setColor(color.r, color.g, color.b, Math.min(1f, alpha));
        batch.draw(radialGlow, projected.x - radiusPixels, projected.y - radiusPixels, size, size);
    }

    private boolean project(WorldMapLayout layout, double xM, double yM) {
        if (!Double.isFinite(xM) || !Double.isFinite(yM)) {
            return false;
        }
        return layout.worldToScreen((float) xM, (float) yM, projected);
    }

    private static float screenLength(WorldMapLayout layout, double worldLengthM) {
        if (!Double.isFinite(worldLengthM) || worldLengthM <= 0d) {
            return 0f;
        }
        return (float) (worldLengthM * layout.getScale());
    }

    private static float frameSeconds() {
        if (Gdx.graphics == null) {
            return 0f;
        }
        float delta = Gdx.graphics.getDeltaTime();
        if (!Float.isFinite(delta) || delta <= 0f) {
            return 0f;
        }
        return Math.min(0.10f, delta);
    }

    private static Color flashColor(FlashKind kind) {
        return switch (kind) {
            case SHIELD -> SHIELD_GLOW;
            case ARMOR -> ARMOR_GLOW;
            case PENETRATION -> PENETRATION_GLOW;
            case DESTRUCTION -> DESTRUCTION_GLOW;
        };
    }

    private static float flashBaseAlpha(FlashKind kind) {
        return switch (kind) {
            case SHIELD -> 0.42f;
            case ARMOR -> 0.50f;
            case PENETRATION -> 0.62f;
            case DESTRUCTION -> 0.72f;
        };
    }

    private static Color particleColor(ParticleKind kind) {
        return switch (kind) {
            case SHIELD_ARC -> SHIELD_GLOW;
            case SPARK -> ARMOR_GLOW;
            case HOT_FRAGMENT -> PENETRATION_GLOW;
            case PLASMA -> DESTRUCTION_GLOW;
        };
    }

    private static float particleBaseAlpha(ParticleKind kind) {
        return switch (kind) {
            case SHIELD_ARC -> 0.34f;
            case SPARK -> 0.52f;
            case HOT_FRAGMENT -> 0.62f;
            case PLASMA -> 0.32f;
        };
    }

    private static Texture createRadialGlowTexture(int size) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            float center = size * 0.5f;
            float radius = size * 0.5f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x + 0.5f - center) / radius;
                    float dy = (y + 0.5f - center) / radius;
                    float distance = (float) Math.sqrt(dx * dx + dy * dy);
                    float alpha = Math.max(0f, 1f - distance);
                    alpha = alpha * alpha * (3f - 2f * alpha);
                    pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha));
                }
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private static Texture createPixelTexture() {
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(Color.WHITE);
            pixmap.fill();
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private static float clampScreenSize(float value, float minimum, float maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
