package com.spacesim.presentation.asset;

import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrdnanceSpriteCatalogTest {
    @Test
    void packContainsTenDistinctTransparentSprites() throws IOException {
        assertEquals(10, OrdnanceSpriteCatalog.allVariants().size());
        assertEquals(10, OrdnanceSpriteCatalog.allTexturePaths().size());
        Set<String> assetIds = new HashSet<>();

        for (var variant : OrdnanceSpriteCatalog.allVariants()) {
            assertTrue(assetIds.add(variant.assetId()), variant.assetId());
            BufferedImage image = image(variant.texturePath());
            assertTrue(image.getColorModel().hasAlpha(), variant.texturePath());
            assertTrue(image.getWidth() > 0 && image.getHeight() > 0, variant.texturePath());
            assertEquals(0, alpha(image.getRGB(0, 0)), variant.texturePath());
            assertEquals(0, alpha(image.getRGB(image.getWidth() - 1, image.getHeight() - 1)),
                    variant.texturePath());
            assertTrue(hasVisiblePixel(image), variant.texturePath());
        }
    }

    @Test
    void supportedKindsKeepStableVariantSelection() {
        assertTrue(OrdnanceSpriteCatalog.supports(BodyKind.KINETIC_PROJECTILE));
        assertTrue(OrdnanceSpriteCatalog.supports(BodyKind.GUIDED_MISSILE));
        assertTrue(OrdnanceSpriteCatalog.supports(BodyKind.INTERCEPTOR));
        assertFalse(OrdnanceSpriteCatalog.supports(BodyKind.DECOY));
        assertFalse(OrdnanceSpriteCatalog.supports(BodyKind.DEBRIS));

        var first = OrdnanceSpriteCatalog.resolve(BodyKind.KINETIC_PROJECTILE, 3L);
        assertEquals(first, OrdnanceSpriteCatalog.resolve(BodyKind.KINETIC_PROJECTILE, 3L));
        assertNotEquals(first, OrdnanceSpriteCatalog.resolve(BodyKind.KINETIC_PROJECTILE, 4L));
        assertEquals(BodyKind.INTERCEPTOR,
                OrdnanceSpriteCatalog.resolve(BodyKind.INTERCEPTOR, 99L).kind());
        assertThrows(IllegalArgumentException.class,
                () -> OrdnanceSpriteCatalog.resolve(BodyKind.DECOY, 1L));
    }

    @Test
    void authoredUpFacingSpritesRotateIntoRuntimeHeading() {
        assertEquals(-90f, OrdnanceSpriteCatalog.rotationDegrees(0d), 0.0001f);
        assertEquals(0f, OrdnanceSpriteCatalog.rotationDegrees(Math.PI / 2d), 0.0001f);
        assertEquals(90f, OrdnanceSpriteCatalog.rotationDegrees(Math.PI), 0.0001f);
        assertThrows(IllegalArgumentException.class,
                () -> OrdnanceSpriteCatalog.rotationDegrees(Double.NaN));
    }

    private static BufferedImage image(String path) throws IOException {
        try (InputStream stream = OrdnanceSpriteCatalogTest.class.getClassLoader()
                .getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage result = ImageIO.read(stream);
            assertNotNull(result, path);
            return result;
        }
    }

    private static boolean hasVisiblePixel(BufferedImage image) {
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image.getRGB(x, y)) > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int alpha(int argb) {
        return (argb >>> 24) & 0xff;
    }
}
