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

/** GPU owner/drawer for deterministic sector space backgrounds. */
public final class SectorSpaceBackgroundTextureRenderer {
    private static final float READABILITY_DIM = 0.62f;

    private final Map<String, Texture> textures = new HashMap<>();
    private boolean disposed;

    /**
     * Loads all packaged sector backgrounds under the active libGDX context.
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
    }

    /**
     * Draws the stable background for the active system's containing sector, using an
     * aspect-preserving center crop.
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
        if (disposed) {
            throw new IllegalStateException("sector background renderer is disposed");
        }
        SpriteBatch target = Objects.requireNonNull(batch, "batch");
        if (!Float.isFinite(x) || !Float.isFinite(y)
                || !Float.isFinite(width) || !Float.isFinite(height)
                || width <= 0f || height <= 0f) {
            throw new IllegalArgumentException("background destination must be finite and positive-sized");
        }
        String path = SectorSpaceBackgroundCatalog.texturePath(worldSeed, systemId);
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
