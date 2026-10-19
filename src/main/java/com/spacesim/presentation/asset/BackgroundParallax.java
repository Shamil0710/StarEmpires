package com.spacesim.presentation.asset;

import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.SplittableRandom;

/**
 * Two-layer parallax of the system-map background: the far sector artwork and a near field of
 * sparse stars, each shifted by a share of the camera's offset from the fitted overview.
 *
 * <p>Offsets are measured in the map's fitted base frame (zoom 1), which is bounded by the
 * system, so the shift stays small at any zoom. The far layer is cropped with an overscan
 * reserve and never shows an edge; the near layer is a repeating tile and spreads slightly as
 * the camera zooms in. Pure presentation geometry: it never reads or changes simulation state.</p>
 */
public final class BackgroundParallax {
    /** Share of the camera offset by which the far (sector artwork) layer shifts. */
    public static final float FAR_FACTOR = 0.05f;
    /** Share of the camera offset by which the near (star field) layer shifts. */
    public static final float NEAR_FACTOR = 0.16f;
    /** Share of the visible far-layer crop kept in reserve on each side for the shift. */
    public static final float FAR_OVERSCAN = 0.08f;
    /** Near-layer spread per doubling of the camera zoom. */
    public static final float NEAR_SPREAD_PER_DOUBLING = 0.05f;
    /** Largest near-layer spread. */
    public static final float NEAR_MAX_SPREAD = 1.6f;
    /** Side of the repeating near-layer star tile, in texels and in unscaled screen pixels. */
    public static final int STAR_TILE = 512;
    /** Number of stars in one near-layer tile. */
    public static final int STAR_COUNT = 150;

    private BackgroundParallax() {
    }

    /**
     * Texture rectangle in image space: {@code (u, v)} is the top-left corner, {@code v} grows
     * downwards; {@code u2, v2} is the bottom-right corner. Values may exceed 1 on a repeating
     * texture.
     *
     * @param u left
     * @param v top
     * @param u2 right
     * @param v2 bottom
     */
    public record Uv(float u, float v, float u2, float v2) { }

    /**
     * One star of the near-layer tile.
     *
     * @param x texel column
     * @param y texel row, from the top
     * @param brightness alpha in {@code [0.35, 1]}
     * @param large whether the star gets a one-texel halo
     */
    public record Star(int x, int y, float brightness, boolean large) { }

    /**
     * Camera centre's offset from the fitted overview centre, in base-frame pixels.
     *
     * @param zoom camera zoom
     * @param pan camera pan along one axis, in screen pixels
     * @return offset along the same axis; zero for an invalid zoom or pan
     */
    public static double viewOffset(float zoom, float pan) {
        if (!Float.isFinite(zoom) || zoom <= 0f || !Float.isFinite(pan)) return 0d;
        return -pan / (double) zoom;
    }

    /**
     * Far-layer crop: the aspect-preserving centre crop of the sector artwork, shrunk by the
     * overscan reserve and shifted against the camera offset within that reserve.
     *
     * @param textureWidth artwork width in texels
     * @param textureHeight artwork height in texels
     * @param destWidth destination width in pixels
     * @param destHeight destination height in pixels
     * @param viewOffsetX camera offset along X in base-frame pixels
     * @param viewOffsetY camera offset along Y (up) in base-frame pixels
     * @return normalized texture rectangle, always inside the artwork
     */
    public static Uv farCrop(int textureWidth, int textureHeight, float destWidth, float destHeight,
            double viewOffsetX, double viewOffsetY) {
        if (textureWidth <= 0 || textureHeight <= 0 || !(destWidth > 0f) || !(destHeight > 0f)) {
            throw new IllegalArgumentException("texture and destination must be positive-sized");
        }
        double targetAspect = destWidth / (double) destHeight;
        double cropWidth = textureWidth;
        double cropHeight = textureHeight;
        if ((double) textureWidth / textureHeight > targetAspect) {
            cropWidth = textureHeight * targetAspect;
        } else {
            cropHeight = textureWidth / targetAspect;
        }
        double viewWidth = cropWidth / (1d + 2d * FAR_OVERSCAN);
        double viewHeight = cropHeight / (1d + 2d * FAR_OVERSCAN);
        double texelsPerPixel = viewWidth / destWidth;
        // The image moves on screen by -offset * factor, so the crop moves by +offset * factor;
        // screen Y points up and image V down.
        double slackX = (cropWidth - viewWidth) * 0.5d;
        double slackY = (cropHeight - viewHeight) * 0.5d;
        double shiftX = clamp(finite(viewOffsetX) * FAR_FACTOR * texelsPerPixel, slackX);
        double shiftY = clamp(finite(viewOffsetY) * FAR_FACTOR * texelsPerPixel, slackY);
        double left = (textureWidth - viewWidth) * 0.5d + shiftX;
        double top = (textureHeight - viewHeight) * 0.5d - shiftY;
        return new Uv((float) (left / textureWidth), (float) (top / textureHeight),
                (float) ((left + viewWidth) / textureWidth), (float) ((top + viewHeight) / textureHeight));
    }

    /**
     * @param zoom camera zoom
     * @return near-layer spread: 1 at the overview, growing with each zoom doubling up to the cap
     */
    public static float nearSpread(float zoom) {
        if (!Float.isFinite(zoom) || zoom <= 1f) return 1f;
        double doublings = Math.log(zoom) / Math.log(2d);
        return (float) Math.min(NEAR_MAX_SPREAD, 1d + NEAR_SPREAD_PER_DOUBLING * doublings);
    }

    /**
     * Near-layer tile rectangle: the repeating star tile, spread around the destination centre
     * and shifted against the camera offset.
     *
     * @param destWidth destination width in pixels
     * @param destHeight destination height in pixels
     * @param uiScale UI scale applied to the tile size
     * @param zoom camera zoom
     * @param viewOffsetX camera offset along X in base-frame pixels
     * @param viewOffsetY camera offset along Y (up) in base-frame pixels
     * @param phase per-system tile phase {u, v} in {@code [0, 1)}
     * @return texture rectangle on a repeating texture; its left and top edges are in {@code [0, 1)}
     */
    public static Uv nearTile(float destWidth, float destHeight, float uiScale, float zoom,
            double viewOffsetX, double viewOffsetY, float[] phase) {
        if (!(destWidth > 0f) || !(destHeight > 0f) || !(uiScale > 0f)) {
            throw new IllegalArgumentException("destination and scale must be positive");
        }
        double tilePixels = STAR_TILE * (double) uiScale * nearSpread(zoom);
        double width = destWidth / tilePixels;
        double height = destHeight / tilePixels;
        double centreU = phase[0] + finite(viewOffsetX) * NEAR_FACTOR / tilePixels;
        double centreV = phase[1] - finite(viewOffsetY) * NEAR_FACTOR / tilePixels;
        double u = centreU - width * 0.5d;
        double v = centreV - height * 0.5d;
        // Keep the coordinates small so float texture coordinates stay exact.
        u -= Math.floor(u);
        v -= Math.floor(v);
        return new Uv((float) u, (float) v, (float) (u + width), (float) (v + height));
    }

    /**
     * Deterministic per-system phase of the star tile, so neighbouring systems differ.
     *
     * @param worldSeed persistent generated-world seed
     * @param systemId active system
     * @return {u, v} in {@code [0, 1)}
     */
    public static float[] phase(long worldSeed, StarSystemId systemId) {
        long mixed = mix64(worldSeed ^ mix64(Objects.requireNonNull(systemId, "systemId").value()));
        float u = (mixed >>> 40) / (float) (1L << 24);
        float v = ((mixed >>> 16) & 0xFFFFFFL) / (float) (1L << 24);
        return new float[] {u, v};
    }

    /**
     * Stars of the near-layer tile.
     *
     * @param seed tile seed
     * @param size tile side in texels
     * @param count number of stars
     * @return deterministic stars inside the tile
     */
    public static List<Star> stars(long seed, int size, int count) {
        if (size <= 2 || count < 0) throw new IllegalArgumentException("tile must be larger than 2 texels");
        SplittableRandom random = new SplittableRandom(seed);
        List<Star> stars = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            // Halo stars keep a one-texel margin so the halo never wraps across the tile edge.
            boolean large = random.nextInt(100) < 12;
            int margin = large ? 1 : 0;
            int x = margin + random.nextInt(size - 2 * margin);
            int y = margin + random.nextInt(size - 2 * margin);
            float brightness = 0.35f + 0.65f * (float) Math.pow(random.nextDouble(), 2d);
            stars.add(new Star(x, y, brightness, large));
        }
        return List.copyOf(stars);
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    private static double finite(double value) {
        return Double.isFinite(value) ? value : 0d;
    }

    private static long mix64(long value) {
        long z = value + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
