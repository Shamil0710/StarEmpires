package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

/**
 * GPU owner/drawer of system-map mining beams: an additive warm band with a bright core, glows at
 * the emitter and the contact spot, and the extraction spark burst. Uses the tactical VFX glow
 * sprite ({@link TacticalVfxRenderer#createRadialGlowTexture(int)}) and its additive blending.
 */
final class MiningBeamRenderer {
    private static final Color BAND = new Color(1.00f, 0.66f, 0.26f, 1f);
    private static final Color CORE = new Color(1.00f, 0.93f, 0.72f, 1f);
    private static final Color CONTACT = new Color(1.00f, 0.58f, 0.20f, 1f);
    private static final Color SPARK = new Color(1.00f, 0.84f, 0.48f, 1f);

    private final Texture glow = TacticalVfxRenderer.createRadialGlowTexture(64);
    private final Texture band = createBandTexture();
    private final Texture pixel = TacticalVfxRenderer.createPixelTexture();
    private boolean disposed;

    /**
     * Draws one beam inside an active batch; the batch's tint and blend factors are restored after.
     *
     * @param batch active sprite batch
     * @param segment beam {x1, y1, x2, y2} from hull edge to source surface, or null when they overlap
     * @param contactX contact spot X (the source surface, or its centre without a segment)
     * @param contactY contact spot Y
     * @param effect beam state of this frame
     * @param sparkReachPx distance sparks travel over a burst
     * @param scale UI scale for widths
     */
    void draw(SpriteBatch batch, float[] segment, float contactX, float contactY, MiningBeamEffects.Effect effect,
            float sparkReachPx, float scale) {
        if (disposed) throw new IllegalStateException("mining beam renderer is disposed");
        float intensity = effect.intensity() * MiningBeamGeometry.pulse(effect.ageSeconds());
        BatchState entry = BatchState.capture(batch);
        try {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            double beamX = 1d;
            double beamY = 0d;
            if (segment != null) {
                beamX = segment[2] - segment[0];
                beamY = segment[3] - segment[1];
                float length = (float) Math.hypot(beamX, beamY);
                float rotation = (float) Math.toDegrees(Math.atan2(beamY, beamX));
                stretch(batch, band, segment[0], segment[1], length, 8f * scale, rotation, BAND, 0.42f * intensity);
                stretch(batch, band, segment[0], segment[1], length, 2.4f * scale, rotation, CORE, 0.85f * intensity);
                glow(batch, segment[0], segment[1], 6f * scale, CORE, 0.45f * intensity);
            }
            glow(batch, contactX, contactY, 18f * scale, CONTACT, 0.7f * intensity);
            glow(batch, contactX, contactY, 6f * scale, CORE, 0.9f * intensity);
            if (effect.bursting()) {
                for (var spark : MiningBeamGeometry.sparks(effect.burstSeed(), effect.burstAge(),
                        MiningBeamEffects.BURST_SECONDS, contactX, contactY, beamX, beamY, sparkReachPx)) {
                    float alpha = spark.alpha() * effect.intensity();
                    stretch(batch, pixel, spark.x(), spark.y(), 4.5f * scale, 1.5f * scale, spark.directionDegrees(), SPARK, alpha);
                    glow(batch, spark.x(), spark.y(), 3.5f * scale, SPARK, 0.6f * alpha);
                }
            }
        } finally {
            entry.restore(batch);
        }
    }

    /** Releases the renderer-owned textures exactly once. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        glow.dispose();
        band.dispose();
        pixel.dispose();
    }

    private void glow(SpriteBatch batch, float x, float y, float radius, Color color, float alpha) {
        if (!(alpha > 0f) || !(radius > 0f)) return;
        batch.setColor(color.r, color.g, color.b, Math.min(1f, alpha));
        batch.draw(glow, x - radius, y - radius, radius * 2f, radius * 2f);
    }

    /** Draws a texture as a rotated strip starting at (x, y), centred across its width. */
    private static void stretch(SpriteBatch batch, Texture texture, float x, float y, float length, float width,
            float rotationDegrees, Color color, float alpha) {
        if (!(alpha > 0f) || !(length > 0f) || !(width > 0f)) return;
        batch.setColor(color.r, color.g, color.b, Math.min(1f, alpha));
        batch.draw(texture, x, y - width * 0.5f, 0f, width * 0.5f, length, width, 1f, 1f, rotationDegrees,
                0, 0, texture.getWidth(), texture.getHeight(), false, false);
    }

    /** Across-the-beam profile: opaque in the middle, smoothly transparent at both edges. */
    private static Texture createBandTexture() {
        int height = 32;
        Pixmap pixmap = new Pixmap(4, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            for (int y = 0; y < height; y++) {
                float distance = Math.abs((y + 0.5f) / height * 2f - 1f);
                float alpha = Math.max(0f, 1f - distance);
                alpha = alpha * alpha * (3f - 2f * alpha);
                for (int x = 0; x < 4; x++) pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, alpha));
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }
}
