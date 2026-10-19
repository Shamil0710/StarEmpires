package com.spacesim.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

import java.util.List;

/**
 * GPU owner/drawer of the system-map halos ({@link MapHaloLayout}). Both textures are baked once
 * at start-up and drawn with additive blending, so the glow costs one textured quad per halo and
 * no frame buffer or shader; a real bloom pass waits for the GPU smoke on target machines.
 */
final class MapHaloRenderer {
    private static final int HALO_SIZE = 128;
    private static final int FLARE_SIZE = 64;

    private final Texture halo = createHaloTexture(HALO_SIZE);
    private final Texture flare = createFlareTexture(FLARE_SIZE);
    private boolean disposed;

    /**
     * Draws the halos of one layer inside an active batch; the batch's tint and blend factors are
     * restored after.
     *
     * @param batch active sprite batch
     * @param halos halos of this frame
     * @param layer layer to draw
     */
    void draw(SpriteBatch batch, List<MapHaloLayout.Halo> halos, MapHaloLayout.Layer layer) {
        if (disposed) throw new IllegalStateException("map halo renderer is disposed");
        BatchState entry = BatchState.capture(batch);
        try {
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
            for (MapHaloLayout.Halo item : halos) {
                if (item.layer() != layer) continue;
                Color color = color(item.tint());
                batch.setColor(color.r, color.g, color.b, Math.min(1f, item.alpha()));
                float radius = item.radiusPx();
                batch.draw(item.flare() ? flare : halo, item.x() - radius, item.y() - radius, radius * 2f, radius * 2f);
            }
        } finally {
            entry.restore(batch);
        }
    }

    /** Releases the baked textures exactly once. */
    void dispose() {
        if (disposed) return;
        disposed = true;
        halo.dispose();
        flare.dispose();
    }

    private static Color color(MapHaloLayout.Tint tint) {
        return switch (tint) {
            case STATION -> STATION;
            case OUTPOST -> OUTPOST;
            case JUMP -> JUMP;
            case ANOMALY -> ANOMALY;
            case BEACON_RED -> BEACON_RED;
            case BEACON_WHITE -> BEACON_WHITE;
        };
    }

    // Brighter than the UI palette: additive light over near-black space needs full colour.
    private static final Color STATION = new Color(1f, 0.94f, 0.82f, 1f);
    private static final Color OUTPOST = new Color(1f, 0.70f, 0.32f, 1f);
    private static final Color JUMP = new Color(0.42f, 0.86f, 1f, 1f);
    private static final Color ANOMALY = new Color(0.72f, 0.46f, 1f, 1f);
    private static final Color BEACON_RED = new Color(1f, 0.28f, 0.22f, 1f);
    private static final Color BEACON_WHITE = new Color(0.92f, 0.96f, 1f, 1f);

    /**
     * Soft halo with a bright core and a long tail, the shape a bloom blur would give a point
     * light, faded to zero at the texture edge.
     *
     * @param distance distance from the centre as a share of the radius
     * @return alpha in {@code [0, 1]}, zero at and beyond the edge
     */
    static float haloProfile(float distance) {
        if (!(distance < 1f)) return 0f;
        float d = Math.max(0f, distance);
        float core = (float) Math.exp(-(d * d) / (2f * 0.12f * 0.12f));
        float tail = (float) Math.exp(-(d * d) / (2f * 0.38f * 0.38f));
        float edge = 1f - d * d;
        return Math.min(1f, (0.55f * core + 0.45f * tail) * edge);
    }

    /**
     * Point-light flare: a small core with thin horizontal and vertical spikes.
     *
     * @param dx horizontal offset from the centre as a share of the radius
     * @param dy vertical offset from the centre as a share of the radius
     * @return alpha in {@code [0, 1]}, zero at and beyond the edge
     */
    static float flareProfile(float dx, float dy) {
        float distance = (float) Math.hypot(dx, dy);
        if (!(distance < 1f)) return 0f;
        float core = (float) Math.exp(-(distance * distance) / (2f * 0.14f * 0.14f));
        float spikeX = (float) (Math.exp(-(dy * dy) / (2f * 0.025f * 0.025f)) * (1f - Math.abs(dx)));
        float spikeY = (float) (Math.exp(-(dx * dx) / (2f * 0.025f * 0.025f)) * (1f - Math.abs(dy)));
        return Math.min(1f, core + 0.55f * (spikeX + spikeY) * (1f - distance));
    }

    private static Texture createHaloTexture(int size) {
        return bake(size, (dx, dy) -> haloProfile((float) Math.hypot(dx, dy)));
    }

    private static Texture createFlareTexture(int size) {
        return bake(size, MapHaloRenderer::flareProfile);
    }

    private interface Profile {
        float alpha(float dx, float dy);
    }

    private static Texture bake(int size, Profile profile) {
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            float half = size * 0.5f;
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float dx = (x + 0.5f - half) / half;
                    float dy = (y + 0.5f - half) / half;
                    pixmap.drawPixel(x, y, Color.rgba8888(1f, 1f, 1f, profile.alpha(dx, dy)));
                }
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }
}
