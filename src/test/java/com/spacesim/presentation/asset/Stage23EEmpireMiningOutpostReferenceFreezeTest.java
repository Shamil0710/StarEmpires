package com.spacesim.presentation.asset;

import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EEmpireMiningOutpostReferenceFreezeTest {
    private static final String PATH =
            "art_sources/stage23e/references/ref.empire.station.mining_outpost.v1/selected/reference_master.png";
    private static final String SHA256 =
            "160f5d5779eb762a43feb43109619a14260e587ee068512860a4154eb1863811";

    @Test
    void selectedMiningOutpostMasterPassesFreezeGate() throws Exception {
        byte[] bytes = readBytes(PATH);
        assertEquals(SHA256, sha256(bytes));

        BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        assertNotNull(image);
        assertEquals(1024, image.getWidth());
        assertEquals(1024, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertTransparentCorners(image);

        Bounds bounds = bounds(image, 16);
        assertEquals(92, bounds.minX());
        assertEquals(182, bounds.minY());
        assertEquals(931, bounds.maxX());
        assertEquals(840, bounds.maxY());
        assertEquals(840, bounds.width());
        assertEquals(659, bounds.height());

        ScaleStats quarter = scaleStats(image, 4);
        assertReadable("25%", quarter, 0.78, 0.61, 0.28, 8, 80);

        ScaleStats eighth = scaleStats(image, 8);
        assertReadable("12.5%", eighth, 0.78, 0.61, 0.28, 7, 70);

        for (Anchor anchor : List.of(
                new Anchor("service_core", 590, 520),
                new Anchor("extractor_root", 395, 365),
                new Anchor("handling_arm_root", 650, 690),
                new Anchor("docking_service_interface", 150, 520),
                new Anchor("survey_sensor_cluster", 600, 260),
                new Anchor("engineering_power_block", 600, 700),
                new Anchor("ore_handling_storage", 790, 500))) {
            assertTrue(
                    hasOpaquePixelNear(image, anchor.x(), anchor.y(), 55),
                    anchor.id() + " lacks selected-reference geometry");
        }

        var resolved = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                "station.infrastructure.mining_outpost",
                false,
                Stage20StationPhysicalGeometryProfile.deriveCurrent());
        assertEquals(
                Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                resolved.scaleAuthority());
        assertTrue(resolved.worldLengthM() > 0d);
        assertTrue(resolved.worldWidthM() > 0d);
    }

    private static void assertReadable(
            String label,
            ScaleStats stats,
            double minWidthFraction,
            double minHeightFraction,
            double minVisibleFraction,
            int minLumaBins,
            int minLumaRange) {
        assertTrue(stats.spanWidth() >= stats.canvasWidth() * minWidthFraction,
                label + " spanWidth=" + stats.spanWidth());
        assertTrue(stats.spanHeight() >= stats.canvasHeight() * minHeightFraction,
                label + " spanHeight=" + stats.spanHeight());
        assertTrue(stats.visibleCells() >= stats.canvasWidth() * stats.canvasHeight() * minVisibleFraction,
                label + " visibleCells=" + stats.visibleCells());
        assertTrue(stats.lumaBins() >= minLumaBins, label + " lumaBins=" + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                label + " lumaRange=" + (stats.maxLuma() - stats.minLuma()));
    }

    private static ScaleStats scaleStats(BufferedImage source, int factor) {
        int width = source.getWidth() / factor;
        int height = source.getHeight() / factor;
        boolean[] bins = new boolean[16];
        int visible = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int minLuma = 255;
        int maxLuma = 0;

        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                boolean occupied = false;
                long weightedLuma = 0L;
                long alphaWeight = 0L;
                for (int sy = dy * factor; sy < (dy + 1) * factor; sy++) {
                    for (int sx = dx * factor; sx < (dx + 1) * factor; sx++) {
                        int rgba = source.getRGB(sx, sy);
                        int alpha = (rgba >>> 24) & 0xff;
                        if (alpha == 0) {
                            continue;
                        }
                        occupied = true;
                        int red = (rgba >>> 16) & 0xff;
                        int green = (rgba >>> 8) & 0xff;
                        int blue = rgba & 0xff;
                        int luma = (54 * red + 183 * green + 19 * blue) / 256;
                        weightedLuma += (long) luma * alpha;
                        alphaWeight += alpha;
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

        int lumaBins = 0;
        for (boolean present : bins) {
            if (present) lumaBins++;
        }
        assertTrue(maxX >= minX && maxY >= minY);
        return new ScaleStats(
                width,
                height,
                maxX - minX + 1,
                maxY - minY + 1,
                visible,
                lumaBins,
                minLuma,
                maxLuma);
    }

    private static Bounds bounds(BufferedImage image, int alphaThreshold) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image, x, y) <= alphaThreshold) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(maxX >= minX && maxY >= minY);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean hasOpaquePixelNear(
            BufferedImage image,
            int centerX,
            int centerY,
            int radius) {
        int r2 = radius * radius;
        for (int y = Math.max(0, centerY - radius);
             y <= Math.min(image.getHeight() - 1, centerY + radius); y++) {
            int dy = y - centerY;
            for (int x = Math.max(0, centerX - radius);
                 x <= Math.min(image.getWidth() - 1, centerX + radius); x++) {
                int dx = x - centerX;
                if (dx * dx + dy * dy <= r2 && alpha(image, x, y) > 16) return true;
            }
        }
        return false;
    }

    private static void assertTransparentCorners(BufferedImage image) {
        assertEquals(0, alpha(image, 0, 0));
        assertEquals(0, alpha(image, image.getWidth() - 1, 0));
        assertEquals(0, alpha(image, 0, image.getHeight() - 1));
        assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1));
    }

    private static byte[] readBytes(String path) throws IOException {
        try (InputStream input =
                     Stage23EEmpireMiningOutpostReferenceFreezeTest.class
                             .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private record Anchor(String id, int x, int y) { }
    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
    }
    private record ScaleStats(
            int canvasWidth,
            int canvasHeight,
            int spanWidth,
            int spanHeight,
            int visibleCells,
            int lumaBins,
            int minLuma,
            int maxLuma) { }
}
