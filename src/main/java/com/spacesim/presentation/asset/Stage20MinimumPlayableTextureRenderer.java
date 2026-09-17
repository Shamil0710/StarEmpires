package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.AtlasRegion;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.SpriteBinding;
import com.spacesim.presentation.asset.Stage22EnginePlumeGeometry.PlumeTransform;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** GPU texture owner/drawer for generated-world sprites and validated Stage-22 production ship art. */
public final class Stage20MinimumPlayableTextureRenderer {
    private final Map<String, Texture> textures = new HashMap<>();
    private final Map<String, Texture> effectTextures = new HashMap<>();
    private final Set<String> missingEffectTextures = new HashSet<>();
    private final Map<String, AtlasRegion> visibleRegions = new HashMap<>();
    private final Texture enginePlumeTexture;
    private final Texture engineBloomTexture;
    private boolean disposed;

    /** Loads every distinct minimum-pack texture from the classpath under the active libGDX context. */
    public Stage20MinimumPlayableTextureRenderer() {
        for (String path : Stage20MinimumPlayableSpriteCatalog.allTexturePaths()) {
            textures.put(path, load(path));
        }
        enginePlumeTexture = createEnginePlumeTexture();
        engineBloomTexture = createEngineBloomTexture();
    }

    /**
     * Draws one already-resolved sprite without reading or mutating simulation state.
     * Runtime propulsion effects are read only from presentation metadata attached to the binding.
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
        String authoredAssetId = Stage22RuntimeSpriteEffects.authoredAssetId(sprite);
        AtlasRegion region = visibleRegions.computeIfAbsent(
                authoredAssetId, ignored -> visibleRegion(sprite));
        if (region.pixelX() + region.pixelWidth() > texture.getWidth()
                || region.pixelY() + region.pixelHeight() > texture.getHeight()) {
            throw new IllegalArgumentException("sprite region exceeds texture: " + authoredAssetId);
        }

        double propulsionFraction = Stage22RuntimeSpriteEffects.propulsionFraction(sprite);
        if (propulsionFraction > 0d) {
            drawEngineEffects(target, centerX, centerY, width, height, rotationDegrees, propulsionFraction);
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

        if (propulsionFraction > 0d) {
            drawEmissiveOverlay(target, sprite, region, centerX, centerY, width, height,
                    rotationDegrees, propulsionFraction);
        }
    }

    private void drawEngineEffects(
            SpriteBatch target,
            float centerX,
            float centerY,
            float hullLength,
            float hullWidth,
            float rotationDegrees,
            double propulsionFraction) {
        PlumeTransform plume = Stage22EnginePlumeGeometry.resolve(
                centerX, centerY, hullLength, hullWidth, rotationDegrees, propulsionFraction)
                .orElseThrow();
        Color previous = new Color(target.getColor());
        target.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        try {
            target.setColor(0.44f, 0.72f, 1f, plume.alpha() * 0.58f);
            drawCentered(target, enginePlumeTexture,
                    plume.centerX(), plume.centerY(), plume.lengthPixels(), plume.widthPixels(),
                    plume.rotationDegrees());

            double radians = Math.toRadians(rotationDegrees);
            float forwardX = (float) Math.cos(radians);
            float forwardY = (float) Math.sin(radians);
            float coreLength = plume.lengthPixels() * 0.70f;
            float coreCenterX = plume.centerX() + forwardX * plume.lengthPixels() * 0.12f;
            float coreCenterY = plume.centerY() + forwardY * plume.lengthPixels() * 0.12f;
            target.setColor(0.72f, 0.90f, 1f, Math.min(1f, plume.alpha() * 0.86f));
            drawCentered(target, enginePlumeTexture,
                    coreCenterX, coreCenterY, coreLength,
                    Math.max(1f, plume.widthPixels() * 0.42f), plume.rotationDegrees());

            float sternX = centerX - forwardX * hullLength * 0.50f;
            float sternY = centerY - forwardY * hullLength * 0.50f;
            float glowSize = Math.max(3f,
                    hullWidth * (0.44f + 0.24f * (float) propulsionFraction));
            target.setColor(0.54f, 0.82f, 1f,
                    0.34f + 0.34f * (float) propulsionFraction);
            drawCentered(target, engineBloomTexture,
                    sternX, sternY, glowSize, glowSize, rotationDegrees);
        } finally {
            target.setColor(previous);
            target.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void drawEmissiveOverlay(
            SpriteBatch target,
            SpriteBinding sprite,
            AtlasRegion region,
            float centerX,
            float centerY,
            float width,
            float height,
            float rotationDegrees,
            double propulsionFraction) {
        String path = emissivePath(sprite.texturePath());
        if (path == null) {
            return;
        }
        Texture emissive = optionalEffectTexture(path);
        if (emissive == null) {
            return;
        }
        if (region.pixelX() + region.pixelWidth() > emissive.getWidth()
                || region.pixelY() + region.pixelHeight() > emissive.getHeight()) {
            throw new IllegalArgumentException("emissive region exceeds texture: " + path);
        }
        Color previous = new Color(target.getColor());
        target.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        try {
            target.setColor(1f, 1f, 1f, 0.46f + 0.44f * (float) propulsionFraction);
            target.draw(
                    emissive,
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
        } finally {
            target.setColor(previous);
            target.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private static void drawCentered(
            SpriteBatch target,
            Texture texture,
            float centerX,
            float centerY,
            float width,
            float height,
            float rotationDegrees) {
        target.draw(
                texture,
                centerX - width * 0.5f,
                centerY - height * 0.5f,
                width * 0.5f,
                height * 0.5f,
                width,
                height,
                1f,
                1f,
                rotationDegrees,
                0,
                0,
                texture.getWidth(),
                texture.getHeight(),
                false,
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

    private Texture optionalEffectTexture(String path) {
        Texture existing = effectTextures.get(path);
        if (existing != null) {
            return existing;
        }
        if (missingEffectTextures.contains(path) || !Gdx.files.internal(path).exists()) {
            missingEffectTextures.add(path);
            return null;
        }
        Texture loaded = load(path);
        effectTextures.put(path, loaded);
        return loaded;
    }

    private static String emissivePath(String basePath) {
        if (!Stage22ProductionShipSpriteAdapter.isProductionPath(basePath)
                || !basePath.endsWith("_base.png")) {
            return null;
        }
        return basePath.substring(0, basePath.length() - "_base.png".length()) + "_emissive.png";
    }

    private static Texture load(String path) {
        Texture texture = new Texture(Gdx.files.internal(path));
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        return texture;
    }

    private static Texture createEnginePlumeTexture() {
        int width = 96;
        int height = 48;
        Pixmap image = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            for (int y = 0; y < height; y++) {
                float lateral = Math.abs(((y + 0.5f) / height) * 2f - 1f);
                for (int x = 0; x < width; x++) {
                    float longitudinal = (x + 0.5f) / width;
                    float envelope = 0.22f + 0.78f * longitudinal;
                    if (lateral >= envelope) {
                        continue;
                    }
                    float transverse = 1f - lateral / envelope;
                    float alpha = (float) (Math.pow(transverse, 1.55d)
                            * Math.pow(0.12f + longitudinal * 0.88f, 0.72d));
                    image.setColor(1f, 1f, 1f, alpha);
                    image.drawPixel(x, y);
                }
            }
            Texture texture = new Texture(image);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            image.dispose();
        }
    }

    private static Texture createEngineBloomTexture() {
        int size = 64;
        Pixmap image = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            for (int y = 0; y < size; y++) {
                float dy = ((y + 0.5f) / size) * 2f - 1f;
                for (int x = 0; x < size; x++) {
                    float dx = ((x + 0.5f) / size) * 2f - 1f;
                    float radius = (float) Math.sqrt(dx * dx + dy * dy);
                    if (radius >= 1f) {
                        continue;
                    }
                    float alpha = (1f - radius);
                    alpha *= alpha;
                    image.setColor(1f, 1f, 1f, alpha);
                    image.drawPixel(x, y);
                }
            }
            Texture texture = new Texture(image);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            return texture;
        } finally {
            image.dispose();
        }
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
        effectTextures.values().forEach(Texture::dispose);
        enginePlumeTexture.dispose();
        engineBloomTexture.dispose();
        textures.clear();
        effectTextures.clear();
        missingEffectTextures.clear();
        visibleRegions.clear();
    }
}
