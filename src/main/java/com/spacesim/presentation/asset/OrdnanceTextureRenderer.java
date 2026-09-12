package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.presentation.asset.OrdnanceSpriteCatalog.SpriteVariant;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyGlyph;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** GPU owner/drawer for presentation-only tactical projectile and missile sprites. */
public final class OrdnanceTextureRenderer {
    private final Map<String, Texture> textures = new HashMap<>();
    private boolean disposed;

    /** Loads the complete ordnance pack under an active libGDX graphics context. */
    public OrdnanceTextureRenderer() {
        Map<String, Texture> loaded = new HashMap<>();
        try {
            for (String path : OrdnanceSpriteCatalog.allTexturePaths()) {
                loaded.put(path, load(path));
            }
            textures.putAll(loaded);
        } catch (RuntimeException failure) {
            loaded.values().forEach(Texture::dispose);
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

        SpriteVariant variant = OrdnanceSpriteCatalog.resolve(glyph.kind(), glyph.bodyId());
        Texture texture = textures.get(variant.texturePath());
        if (texture == null) {
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
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
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
    }

    private static Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }
}
