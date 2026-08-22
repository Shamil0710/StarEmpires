package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** GPU texture owner/drawer for the Stage-20.5E presentation-only sprite catalogue. */
public final class Stage20MinimumPlayableTextureRenderer {
    private final Map<String, Texture> textures = new HashMap<>();
    private boolean disposed;

    /** Loads every distinct minimum-pack texture from the classpath under the active libGDX context. */
    public Stage20MinimumPlayableTextureRenderer() {
        for (String path : Stage20MinimumPlayableSpriteCatalog.allTexturePaths()) {
            Texture texture = new Texture(Gdx.files.internal(path));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textures.put(path, texture);
        }
    }

    /**
     * Draws one already-resolved sprite without reading or mutating simulation state.
     *
     * @param batch active caller-owned sprite batch
     * @param binding immutable presentation binding
     * @param centerX screen/world presentation center X
     * @param centerY screen/world presentation center Y
     * @param width positive rendered width
     * @param height positive rendered height
     * @param rotationDegrees finite counter-clockwise rotation
     */
    public void draw(
            SpriteBatch batch,
            SpriteBinding binding,
            float centerX,
            float centerY,
            float width,
            float height,
            float rotationDegrees) {
        if (disposed) {
            throw new IllegalStateException("minimum sprite renderer is disposed");
        }
        SpriteBatch target = Objects.requireNonNull(batch, "batch");
        SpriteBinding sprite = Objects.requireNonNull(binding, "binding");
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || !Float.isFinite(rotationDegrees) || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("render transform must be finite and positive-sized");
        }
        Texture texture = textures.get(sprite.texturePath());
        if (texture == null) {
            throw new IllegalArgumentException("minimum sprite texture is not loaded: " + sprite.texturePath());
        }
        AtlasRegion region = sprite.region();
        if (region.pixelX() + region.pixelWidth() > texture.getWidth()
                || region.pixelY() + region.pixelHeight() > texture.getHeight()) {
            throw new IllegalArgumentException("sprite region exceeds texture: " + sprite.assetId());
        }
        target.draw(
                texture,
                centerX - width * sprite.pivotX(),
                centerY - height * sprite.pivotY(),
                width * sprite.pivotX(),
                height * sprite.pivotY(),
                width,
                height,
                1f,
                1f,
                rotationDegrees,
                region.pixelX(),
                region.pixelY(),
                region.pixelWidth(),
                region.pixelHeight(),
                sprite.sourceFacing() == SourceFacing.LEFT,
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
}
