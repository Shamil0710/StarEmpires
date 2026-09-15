package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** GPU texture owner/drawer for generated-world sprites and validated Stage-22 production ship art. */
public final class Stage20MinimumPlayableTextureRenderer {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, AtlasRegion> visibleRegions = new HashMap<>();
    private boolean disposed;

    /** Loads every distinct minimum-pack texture from the classpath under the active libGDX context. */
    public Stage20MinimumPlayableTextureRenderer() {
        for (String path : Stage20MinimumPlayableSpriteCatalog.allTexturePaths()) {
            textures.put(path, load(path));
        }
    }

    /**
     * Draws one already-resolved sprite without reading or mutating simulation state.
     *
     * <p>Stage-20.5 textures remain eagerly loaded. A Stage-22 production base PNG can be loaded lazily
     * only when its path passes {@link Stage22ProductionShipSpriteAdapter#isProductionPath(String)};
     * every other unknown path is still rejected rather than silently substituted.</p>
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
        Texture texture = texture(sprite.texturePath());
        AtlasRegion region = visibleRegions.computeIfAbsent(sprite.assetId(), ignored -> visibleRegion(sprite));
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

    private Texture texture(String path) {
        Texture existing = textures.get(path);
        if (existing != null) {
            return existing;
        }
        if (!Stage22ProductionShipSpriteAdapter.isProductionPath(path)) {
            throw new IllegalArgumentException("minimum sprite texture is not loaded: " + path);
        }
        Texture loaded = load(path);
        textures.put(path, loaded);
        return loaded;
    }

    private static Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    // Fit the visible hull, not the transparent authoring canvas, to physical dimensions.
    private static AtlasRegion visibleRegion(SpriteBinding sprite) {
        Pixmap image = new Pixmap(Gdx.files.internal(sprite.texturePath()));
        try {
            AtlasRegion source = Stage22ProductionShipSpriteAdapter.isProductionPath(sprite.texturePath())
                    ? new AtlasRegion(0, 0, image.getWidth(), image.getHeight())
                    : sprite.region();
            int minX = source.pixelX() + source.pixelWidth(), minY = source.pixelY() + source.pixelHeight();
            int maxX = -1, maxY = -1;
            for (int y = source.pixelY(); y < source.pixelY() + source.pixelHeight(); y++) {
                for (int x = source.pixelX(); x < source.pixelX() + source.pixelWidth(); x++) {
                    if ((image.getPixel(x, y) & 255) > 0) {
                        minX = Math.min(minX, x);
                        minY = Math.min(minY, y);
                        maxX = Math.max(maxX, x);
                        maxY = Math.max(maxY, y);
                    }
                }
            }
            return maxX < 0 ? source : new AtlasRegion(minX, minY, maxX - minX + 1, maxY - minY + 1);
        } finally {
            image.dispose();
        }
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
}
