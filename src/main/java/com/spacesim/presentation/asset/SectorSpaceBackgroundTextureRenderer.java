package com.spacesim.presentation.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spacesim.world.StarSystemId;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * GPU owner/drawer for deterministic sector space backgrounds: the sector artwork as the far
 * layer and a sparse repeating star field as the near layer, both shifted by camera parallax
 * ({@link BackgroundParallax}).
 */
public final class SectorSpaceBackgroundTextureRenderer {
    private static final float READABILITY_DIM = 0.62f;
    /** Opacity of the near star field over the dimmed artwork. */
    private static final float NEAR_ALPHA = 0.7f;
    /** Fixed seed of the near star tile; systems differ by tile phase. */
    private static final long STAR_TILE_SEED = 0x5EC7_0B5EL;

    private final Map<String, Texture> textures = new HashMap<>();
    private final Texture starField;
    private boolean disposed;

    /**
     * Loads all packaged sector backgrounds under the active libGDX context and builds the near
     * star tile.
     *
     * <p>Sector artwork is decoded through the JDK image reader and then copied into a libGDX pixmap.
     * This deliberately avoids passing authored JPEG bitstreams to libGDX's native stb decoder: a bad
     * or unsupported progressive JPEG can terminate the entire process from native code before Java can
     * report a useful exception.</p>
     */
    public SectorSpaceBackgroundTextureRenderer() {
        for (String path : SectorSpaceBackgroundCatalog.allTexturePaths()) {
            Texture texture = loadTexture(path);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            textures.put(path, texture);
        }
        starField = createStarField();
    }

    /**
     * Draws the stable background for the active system's containing sector at the fitted
     * overview (no parallax shift).
     *
     * @param batch active caller-owned sprite batch
     * @param worldSeed persistent generated-world seed
     * @param systemId stable active-system identity; its sector binding comes from topology projection
     * @param x destination left edge
     * @param y destination bottom edge
     * @param width positive destination width
     * @param height positive destination height
     */
    public void draw(
            SpriteBatch batch,
            long worldSeed,
            StarSystemId systemId,
            float x,
            float y,
            float width,
            float height) {
        draw(batch, worldSeed, systemId, x, y, width, height, 1f, 0d, 0d, 1f);
    }

    /**
     * Draws both background layers with camera parallax: the sector artwork (aspect-preserving
     * centre crop with an overscan reserve) and the near star field over it.
     *
     * @param batch active caller-owned sprite batch
     * @param worldSeed persistent generated-world seed
     * @param systemId stable active-system identity; its sector binding comes from topology projection
     * @param x destination left edge
     * @param y destination bottom edge
     * @param width positive destination width
     * @param height positive destination height
     * @param zoom camera zoom
     * @param viewOffsetX camera offset from the fitted overview along X, in base-frame pixels
     * @param viewOffsetY camera offset from the fitted overview along Y (up), in base-frame pixels
     * @param uiScale positive UI scale for the star tile size
     */
    public void draw(
            SpriteBatch batch,
            long worldSeed,
            StarSystemId systemId,
            float x,
            float y,
            float width,
            float height,
            float zoom,
            double viewOffsetX,
            double viewOffsetY,
            float uiScale) {
        if (disposed) {
            throw new IllegalStateException("sector background renderer is disposed");
        }
        SpriteBatch target = Objects.requireNonNull(batch, "batch");
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("background destination must be finite and positive-sized");
        }
        if (!Float.isFinite(uiScale) || uiScale <= 0f) {
            throw new IllegalArgumentException("ui scale must be finite and positive");
        }
        String path = SectorSpaceBackgroundCatalog.texturePath(worldSeed, systemId);
        Texture texture = textures.get(path);
        if (texture == null) {
            throw new IllegalArgumentException("sector background texture is not loaded: " + path);
        }

        BackgroundParallax.Uv far = BackgroundParallax.farCrop(texture.getWidth(), texture.getHeight(),
                width, height, viewOffsetX, viewOffsetY);
        BackgroundParallax.Uv near = BackgroundParallax.nearTile(width, height, uiScale, zoom,
                viewOffsetX, viewOffsetY, BackgroundParallax.phase(worldSeed, systemId));
        Color previous = new Color(target.getColor());
        // Image V grows downwards: the bottom edge takes v2, the top edge v.
        target.setColor(READABILITY_DIM, READABILITY_DIM, READABILITY_DIM, 1f);
        target.draw(texture, x, y, width, height, far.u(), far.v2(), far.u2(), far.v());
        target.setColor(1f, 1f, 1f, NEAR_ALPHA);
        target.draw(starField, x, y, width, height, near.u(), near.v2(), near.u2(), near.v());
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
        starField.dispose();
    }

    private static Texture createStarField() {
        int size = BackgroundParallax.STAR_TILE;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        try {
            pixmap.setBlending(Pixmap.Blending.None);
            pixmap.setColor(0f, 0f, 0f, 0f);
            pixmap.fill();
            for (BackgroundParallax.Star star
                    : BackgroundParallax.stars(STAR_TILE_SEED, size, BackgroundParallax.STAR_COUNT)) {
                // Slightly blue-white, like the authored artwork's stars.
                pixmap.drawPixel(star.x(), star.y(), Color.rgba8888(0.86f, 0.92f, 1f, star.brightness()));
                if (star.large()) {
                    int halo = Color.rgba8888(0.70f, 0.82f, 1f, star.brightness() * 0.35f);
                    pixmap.drawPixel(star.x() - 1, star.y(), halo);
                    pixmap.drawPixel(star.x() + 1, star.y(), halo);
                    pixmap.drawPixel(star.x(), star.y() - 1, halo);
                    pixmap.drawPixel(star.x(), star.y() + 1, halo);
                }
            }
            Texture texture = new Texture(pixmap);
            texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
            texture.setWrap(Texture.TextureWrap.Repeat, Texture.TextureWrap.Repeat);
            return texture;
        } finally {
            pixmap.dispose();
        }
    }

    private static Texture loadTexture(String path) {
        try (InputStream stream = Gdx.files.internal(path).read()) {
            BufferedImage image = ImageIO.read(stream);
            if (image == null) {
                throw new IllegalStateException("Unsupported sector background image: " + path);
            }
            if (image.getWidth() <= 0 || image.getHeight() <= 0) {
                throw new IllegalStateException("Invalid sector background dimensions: " + path);
            }

            Pixmap pixmap = new Pixmap(image.getWidth(), image.getHeight(), Pixmap.Format.RGBA8888);
            try {
                copyPixels(image, pixmap);
                return new Texture(pixmap);
            } finally {
                pixmap.dispose();
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to decode sector background: " + path, exception);
        }
    }

    private static void copyPixels(BufferedImage image, Pixmap pixmap) {
        int width = image.getWidth();
        int[] row = new int[width];
        for (int y = 0; y < image.getHeight(); y++) {
            image.getRGB(0, y, width, 1, row, 0, width);
            for (int x = 0; x < width; x++) {
                int argb = row[x];
                int rgba = (argb << 8) | ((argb >>> 24) & 0xFF);
                pixmap.drawPixel(x, y, rgba);
            }
        }
    }
}
