package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.world.SectorId;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** GPU owner/drawer for deterministic sector space backgrounds. */
public final class SectorSpaceBackgroundTextureRenderer {
    private static final float READABILITY_DIM = 0.62f;

    private final Map<String, Texture> textures = new HashMap<>();
    private boolean disposed;

    /** Loads all packaged sector backgrounds under the active libGDX context. */
    public SectorSpaceBackgroundTextureRenderer() {
        for (String path : SectorSpaceBackgroundCatalog.allTexturePaths()) {
            Texture texture = new Texture(Gdx.files.internal(path));
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textures.put(path, texture);
        }
    }

    /**
     * Draws the stable background for one sector, using an aspect-preserving center crop.
     *
     * @param batch active caller-owned sprite batch
     * @param worldSeed persistent generated-world seed
     * @param sectorId stable current-sector identity
     * @param x destination left edge
     * @param y destination bottom edge
     * @param width positive destination width
     * @param height positive destination height
     */
    public void draw(
            SpriteBatch batch,
            long worldSeed,
            SectorId sectorId,
            float x,
            float y,
            float width,
            float height) {
        if (disposed) {
            throw new IllegalStateException("sector background renderer is disposed");
        }
        SpriteBatch target = Objects.requireNonNull(batch, "batch");
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("background destination must be finite and positive-sized");
        }
        String path = SectorSpaceBackgroundCatalog.texturePath(worldSeed, sectorId);
        Texture texture = textures.get(path);
        if (texture == null) {
            throw new IllegalArgumentException("sector background texture is not loaded: " + path);
        }

        int sourceX = 0;
        int sourceY = 0;
        int sourceWidth = texture.getWidth();
        int sourceHeight = texture.getHeight();
        float targetAspect = width / height;
        float sourceAspect = (float) sourceWidth / sourceHeight;
        if (sourceAspect > targetAspect) {
            sourceWidth = Math.max(1, Math.round(sourceHeight * targetAspect));
            sourceX = (texture.getWidth() - sourceWidth) / 2;
        } else if (sourceAspect < targetAspect) {
            sourceHeight = Math.max(1, Math.round(sourceWidth / targetAspect));
            sourceY = (texture.getHeight() - sourceHeight) / 2;
        }

        Color previous = new Color(target.getColor());
        target.setColor(READABILITY_DIM, READABILITY_DIM, READABILITY_DIM, 1f);
        target.draw(texture, x, y, width, height,
                sourceX, sourceY, sourceWidth, sourceHeight, false, false);
        target.setColor(previous);
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
