package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22IndustrialUnionProductionCatalogs;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22IndustrialUnionProductionSpriteTest {
    private static final int CANVAS_WIDTH = 768;
    private static final int CANVAS_HEIGHT = 512;

    private static final Map<String, Double> EXPECTED_PHYSICAL_ASPECT_RATIOS = Map.of(
            asset("battleship"), 540d / 190d,
            asset("carrier"), 500d / 210d,
            asset("corvette"), 105d / 38d,
            asset("cruiser"), 330d / 115d,
            asset("destroyer"), 225d / 78d,
            asset("fleet_support"), 340d / 120d,
            asset("freight"), 300d / 100d,
            asset("frigate"), 160d / 52d,
            asset("tanker"), 320d / 105d);

    @Test
    void productionSpritesAreDistinctDetailedTransparentAndPhysicallyProportional() throws Exception {
        Set<String> bindingAssets = Stage22IndustrialUnionProductionCatalogs.loadVisualBindings().stream()
                .map(binding -> binding.assetRef())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(EXPECTED_PHYSICAL_ASPECT_RATIOS.keySet(), bindingAssets);

        Set<String> contentDigests = new HashSet<>();
        for (String path : bindingAssets) {
            byte[] bytes = resourceBytes(path);
            assertTrue(bytes.length >= 100_000, path + " must not regress to placeholder-level detail");
            assertTrue(contentDigests.add(sha256(bytes)), path + " duplicates another production sprite");

            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            assertNotNull(image, path);
            assertEquals(CANVAS_WIDTH, image.getWidth(), path);
            assertEquals(CANVAS_HEIGHT, image.getHeight(), path);
            assertTrue(image.getColorModel().hasAlpha(), path);
            assertTransparentCorners(image, path);

            SpriteStats stats = stats(image);
            int pixels = CANVAS_WIDTH * CANVAS_HEIGHT;
            assertTrue(stats.visiblePixels() > pixels * 0.06, path);
            assertTrue(stats.visiblePixels() < pixels * 0.60, path);
            assertTrue(stats.transparentPixels() > pixels * 0.35, path);
            assertTrue(stats.visibleColors() >= 4_096,
                    path + " must preserve material, panel and wear detail");
            assertTrue(stats.width() >= 620 && stats.width() <= 700, path);
            assertTrue(stats.height() >= 160 && stats.height() <= 400, path);
            assertEquals((CANVAS_WIDTH - 1) / 2d, stats.centerX(), 8d, path);
            assertEquals((CANVAS_HEIGHT - 1) / 2d, stats.centerY(), 8d, path);

            double expectedRatio = EXPECTED_PHYSICAL_ASPECT_RATIOS.get(path);
            double actualRatio = stats.width() / (double) stats.height();
            assertEquals(expectedRatio, actualRatio, expectedRatio * 0.30d,
                    path + " must remain visually proportional to authored hull geometry");
        }
        assertEquals(EXPECTED_PHYSICAL_ASPECT_RATIOS.size(), contentDigests.size());
    }

    private static SpriteStats stats(BufferedImage image) {
        int visible = 0;
        int transparent = 0;
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        Set<Integer> visibleColors = new HashSet<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int rgba = image.getRGB(x, y);
                int alpha = rgba >>> 24;
                if (alpha == 0) {
                    transparent++;
                } else {
                    visible++;
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                    if (alpha > 16) {
                        visibleColors.add(rgba & 0x00ff_ffff);
                    }
                }
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "sprite must contain visible pixels");
        return new SpriteStats(
                visible,
                transparent,
                visibleColors.size(),
                maxX - minX + 1,
                maxY - minY + 1,
                (minX + maxX) / 2d,
                (minY + maxY) / 2d);
    }

    private static void assertTransparentCorners(BufferedImage image, String path) {
        assertEquals(0, alpha(image, 0, 0), path);
        assertEquals(0, alpha(image, image.getWidth() - 1, 0), path);
        assertEquals(0, alpha(image, 0, image.getHeight() - 1), path);
        assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1), path);
    }

    private static byte[] resourceBytes(String path) throws Exception {
        try (InputStream stream = Stage22IndustrialUnionProductionSpriteTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            return stream.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return image.getRGB(x, y) >>> 24;
    }

    private static String asset(String family) {
        return "assets/ships/industrial_union/production/" + family + "/" + family + "_base.png";
    }

    private record SpriteStats(
            int visiblePixels,
            int transparentPixels,
            int visibleColors,
            int width,
            int height,
            double centerX,
            double centerY) { }
}
