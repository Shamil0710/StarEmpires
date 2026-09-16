package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.presentation.asset.OrdnanceSpriteCatalog.SpriteVariant;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.IntBinaryOperator;

/** GPU owner/drawer for presentation-only tactical projectile and missile sprites. */
public final class OrdnanceTextureRenderer {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, VisibleRegion> visibleRegions = new HashMap<>();
    private boolean disposed;

    /** Loads the complete ordnance pack under an active libGDX graphics context. */
    public OrdnanceTextureRenderer() {
        Map<String, Texture> loadedTextures = new HashMap<>();
        Map<String, VisibleRegion> loadedRegions = new HashMap<>();
        try {
            for (String path : OrdnanceSpriteCatalog.allTexturePaths()) {
                loadedTextures.put(path, load(path));
                loadedRegions.put(path, visibleRegion(path));
            }
            textures.putAll(loadedTextures);
            visibleRegions.putAll(loadedRegions);
        } catch (RuntimeException failure) {
            loadedTextures.values().forEach(Texture::dispose);
            throw failure;
        }
    }

    /**
     * Attempts to create the renderer while preserving schematic fallback if assets cannot load.
     *
     * @return ready renderer, or null when the graphics/resource layer rejects the sprite pack
     */
    public static OrdnanceTextureRenderer tryCreate() {
        try {
            return new OrdnanceTextureRenderer();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /**
     * Draws one supported body at the exact physical screen dimensions supplied by the caller.
     *
     * <p>The source region is cropped to non-transparent pixels before scaling. Generated artwork can
     * therefore retain authoring padding without making the visible projectile or missile smaller than
     * its authoritative physical {@code widthM/lengthM} projection.</p>
     *
     * @param batch active caller-owned batch
     * @param body immutable tactical body projection
     * @param centerX projected screen center x
     * @param centerY projected screen center y
     * @param width positive physical screen width
     * @param length positive physical screen length
     */
    public void draw(
            SpriteBatch batch,
            BodyGlyph body,
            float centerX,
            float centerY,
            float width,
            float length) {
        if (disposed) {
            throw new IllegalStateException("ordnance texture renderer is disposed");
        }
        SpriteBatch target = Objects.requireNonNull(batch, "batch");
        BodyGlyph glyph = Objects.requireNonNull(body, "body");
        if (!OrdnanceSpriteCatalog.supports(glyph.kind())) {
            throw new IllegalArgumentException("body kind has no ordnance sprite: " + glyph.kind());
        }
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || !Float.isFinite(length)
                || width <= 0f || length <= 0f) {
            throw new IllegalArgumentException("render transform must be finite and positive-sized");
        }

        SpriteVariant variant = OrdnanceSpriteCatalog.resolve(
                glyph.kind(), glyph.bodyId(), glyph.lengthM(), glyph.widthM());
        Texture texture = textures.get(variant.texturePath());
        VisibleRegion region = visibleRegions.get(variant.texturePath());
        if (texture == null || region == null) {
            throw new IllegalStateException("ordnance texture is not loaded: " + variant.texturePath());
        }
        target.draw(
                texture,
                centerX - width * 0.5f,
                centerY - length * 0.5f,
                width * 0.5f,
                length * 0.5f,
                width,
                length,
                1f,
                1f,
                OrdnanceSpriteCatalog.rotationDegrees(glyph.headingRad()),
                region.pixelX(),
                region.pixelY(),
                region.pixelWidth(),
                region.pixelHeight(),
                false,
                false);
    }

    /** Releases every renderer-owned texture exactly once. */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        textures.values().forEach(Texture::dispose);
        textures.clear();
        visibleRegions.clear();
    }

    private static Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    // Fit the visible projectile/missile, not its transparent generated-image canvas, to physical dimensions.
    private static VisibleRegion visibleRegion(String path) {
        Pixmap image = new Pixmap(Gdx.files.internal(path));
        try {
            return visibleRegion(
                    image.getWidth(),
                    image.getHeight(),
                    (x, y) -> image.getPixel(x, y) & 255);
        } finally {
            image.dispose();
        }
    }

    static VisibleRegion visibleRegion(int width, int height, IntBinaryOperator alphaAt) {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("image dimensions must be positive");
        }
        IntBinaryOperator alphaReader = Objects.requireNonNull(alphaAt, "alphaAt");
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (alphaReader.applyAsInt(x, y) > 0) {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            return new VisibleRegion(0, 0, width, height);
        }
        return new VisibleRegion(minX, minY, maxX - minX + 1, maxY - minY + 1);
    }

    record VisibleRegion(int pixelX, int pixelY, int pixelWidth, int pixelHeight) {
    }
}
