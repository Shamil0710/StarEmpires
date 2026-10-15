package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EUnionSmallCraftReferenceFreezeTest {
    private static final Path MASTER = Path.of(
            "art_sources/stage23e/references/ref.industrial_union.small_craft.base.v1/selected/reference_master.png");
    private static final String EXPECTED_SHA256 =
            "f2c635b1302a9e17d906d64555602615df73fb5efbdfee5c115c692879fc4780";
    private static final double PHYSICAL_ASPECT = 29d / 14d;

    @Test
    void frozenUnionSmallCraftMasterPassesExactRepositoryReferenceGate() throws Exception {
        assertTrue(Files.isRegularFile(MASTER), MASTER.toString());
        byte[] bytes = Files.readAllBytes(MASTER);
        assertEquals(EXPECTED_SHA256, sha256(bytes), "canonical master drift");

        BufferedImage image = ImageIO.read(MASTER.toFile());
        assertNotNull(image, MASTER.toString());
        assertEquals(512, image.getWidth());
        assertEquals(256, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());

        assertEquals(0, alpha(image, 0, 0));
        assertEquals(0, alpha(image, image.getWidth() - 1, 0));
        assertEquals(0, alpha(image, 0, image.getHeight() - 1));
        assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1));

        Bounds bounds = visibleBounds(image);
        assertTrue(bounds.minX() >= 16 && image.getWidth() - 1 - bounds.maxX() >= 16,
                "512-class safe horizontal alpha padding");
        assertTrue(bounds.minY() >= 16 && image.getHeight() - 1 - bounds.maxY() >= 16,
                "512-class safe vertical alpha padding");
        assertTrue(bounds.width() >= 470 && bounds.width() <= 490,
                "unexpected visible width " + bounds.width());
        assertTrue(bounds.height() >= 195 && bounds.height() <= 220,
                "unexpected visible height " + bounds.height());

        assertEquals((image.getWidth() - 1) / 2d, (bounds.minX() + bounds.maxX()) / 2d, 2d);
        assertEquals((image.getHeight() - 1) / 2d, (bounds.minY() + bounds.maxY()) / 2d, 2d);

        double visualAspect = bounds.width() / (double) bounds.height();
        double relativeError = Math.abs(visualAspect - PHYSICAL_ASPECT) / PHYSICAL_ASPECT;
        assertTrue(relativeError <= 0.12d,
                "frozen small-craft visual aspect drifts from 29x14 m authority: " + relativeError);

        ScaleStats quarter = scaleStats(image, 4);
        assertReadable("25%", quarter, 116, 48, 2500, 6, 70);

        ScaleStats eighth = scaleStats(image, 8);
        assertReadable("12.5%", eighth, 56, 23, 650, 6, 65);

        for (Anchor anchor : List.of(
                new Anchor("engine_a", 64, 78),
                new Anchor("engine_b", 64, 178),
                new Anchor("mission", 430, 128),
                new Anchor("fit_a", 286, 76),
                new Anchor("fit_b", 286, 180),
                new Anchor("service", 225, 128))) {
            assertTrue(hasVisibleNear(image, anchor.x(), anchor.y(), 24),
                    "review zone lacks nearby visible hull support: " + anchor.id());
        }
    }

    private static void assertReadable(
            String label,
            ScaleStats stats,
            int minWidth,
            int minHeight,
            int minVisible,
            int minLumaBins,
            int minLumaRange) {
        assertTrue(stats.width() >= minWidth, label + " width=" + stats.width());
        assertTrue(stats.height() >= minHeight, label + " height=" + stats.height());
        assertTrue(stats.visibleCells() >= minVisible, label + " visible=" + stats.visibleCells());
        assertTrue(stats.lumaBins() >= minLumaBins, label + " luma bins=" + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                label + " luma range=" + (stats.maxLuma() - stats.minLuma()));
    }

    private static ScaleStats scaleStats(BufferedImage source, int factor) {
        int width = source.getWidth() / factor;
        int height = source.getHeight() / factor;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int visible = 0;
        int minLuma = 255;
        int maxLuma = 0;
        boolean[] bins = new boolean[16];

        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                long weightedLuma = 0L;
                long alphaWeight = 0L;
                boolean occupied = false;
                for (int sy = 0; sy < factor; sy++) {
                    for (int sx = 0; sx < factor; sx++) {
                        int rgba = source.getRGB(dx * factor + sx, dy * factor + sy);
                        int a = (rgba >>> 24) & 0xff;
                        if (a <= 16) {
                            continue;
                        }
                        occupied = true;
                        int r = (rgba >>> 16) & 0xff;
                        int g = (rgba >>> 8) & 0xff;
                        int b = rgba & 0xff;
                        int luma = (54 * r + 183 * g + 19 * b) / 256;
                        weightedLuma += (long) luma * a;
                        alphaWeight += a;
                    }
                }
                if (!occupied) {
                    continue;
                }

                visible++;
                minX = Math.min(minX, dx);
                minY = Math.min(minY, dy);
                maxX = Math.max(maxX, dx);
                maxY = Math.max(maxY, dy);

                int luma = alphaWeight == 0L ? 0 : (int) (weightedLuma / alphaWeight);
                minLuma = Math.min(minLuma, luma);
                maxLuma = Math.max(maxLuma, luma);
                bins[Math.min(15, luma / 16)] = true;
            }
        }

        int binCount = 0;
        for (boolean present : bins) {
            if (present) {
                binCount++;
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "downscaled reference must remain visible");
        return new ScaleStats(
                maxX - minX + 1,
                maxY - minY + 1,
                visible,
                binCount,
                minLuma,
                maxLuma);
    }

    private static Bounds visibleBounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image, x, y) <= 16) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "reference must contain visible pixels");
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean hasVisibleNear(BufferedImage image, int centerX, int centerY, int radius) {
        int radiusSquared = radius * radius;
        for (int y = Math.max(0, centerY - radius); y <= Math.min(image.getHeight() - 1, centerY + radius); y++) {
            int dy = y - centerY;
            for (int x = Math.max(0, centerX - radius); x <= Math.min(image.getWidth() - 1, centerX + radius); x++) {
                int dx = x - centerX;
                if (dx * dx + dy * dy <= radiusSquared && alpha(image, x, y) > 16) {
                    return true;
                }
            }
        }
        return false;
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }

    private record ScaleStats(
            int width,
            int height,
            int visibleCells,
            int lumaBins,
            int minLuma,
            int maxLuma) { }

    private record Anchor(String id, int x, int y) { }
}
