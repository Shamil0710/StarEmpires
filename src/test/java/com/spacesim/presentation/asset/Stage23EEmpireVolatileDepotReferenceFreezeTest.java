package com.spacesim.presentation.asset;

import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EEmpireVolatileDepotReferenceFreezeTest {
    private static final Path MASTER = Path.of(
            "art_sources/stage23e/references/ref.empire.station.volatile_depot.v1/selected/reference_master.png");
    private static final String SHA256 =
            "846c3cb4f27cc6c115b3959d6d18ee81b59222559c415a120ca8de98ae5e7090";

    @Test
    void selectedVolatileDepotMasterPassesFreezeGate() throws Exception {
        assertTrue(Files.isRegularFile(MASTER), MASTER.toString());
        byte[] bytes = Files.readAllBytes(MASTER);
        assertEquals(SHA256, sha256(bytes));

        BufferedImage image = ImageIO.read(MASTER.toFile());
        assertNotNull(image);
        assertEquals(1024, image.getWidth());
        assertEquals(1024, image.getHeight());
        assertTrue(image.getColorModel().hasAlpha());
        assertTransparentCorners(image);

        Bounds bounds = visibleBounds(image);
        assertEquals(92, bounds.minX());
        assertEquals(229, bounds.minY());
        assertEquals(931, bounds.maxX());
        assertEquals(793, bounds.maxY());
        assertEquals(840, bounds.width());
        assertEquals(565, bounds.height());

        ScaleStats quarter = scaleStats(image, 4);
        assertReadable("25%", quarter, 200, 130, 12000, 8, 80);

        ScaleStats eighth = scaleStats(image, 8);
        assertReadable("12.5%", eighth, 100, 65, 3000, 7, 70);

        for (Anchor anchor : List.of(
                new Anchor("central_service_core", 560, 515),
                new Anchor("tank_upper_left", 300, 455),
                new Anchor("tank_lower_left", 300, 590),
                new Anchor("tank_upper_right", 720, 465),
                new Anchor("tank_lower_right", 720, 585),
                new Anchor("transfer_manifold", 460, 520),
                new Anchor("dock_left", 125, 520),
                new Anchor("dock_right", 890, 520),
                new Anchor("transfer_boom_root", 390, 330),
                new Anchor("engineering_spine", 580, 320))) {
            assertTrue(hasVisibleNear(image, anchor.x(), anchor.y(), 55),
                    anchor.id() + " lacks selected-reference geometry");
        }

        var resolved = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                "station.infrastructure.volatile_depot",
                false,
                Stage20StationPhysicalGeometryProfile.deriveCurrent());
        assertEquals(
                Stage20MinimumPlayableSpriteCatalog.ScaleAuthority.EXACT_PHYSICAL_CONTENT,
                resolved.scaleAuthority());
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
        assertTrue(stats.lumaBins() >= minLumaBins, label + " lumaBins=" + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                label + " lumaRange=" + (stats.maxLuma() - stats.minLuma()));
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
                        if (a <= 16) continue;
                        occupied = true;
                        int r = (rgba >>> 16) & 0xff;
                        int g = (rgba >>> 8) & 0xff;
                        int b = rgba & 0xff;
                        int luma = (54 * r + 183 * g + 19 * b) / 256;
                        weightedLuma += (long) luma * a;
                        alphaWeight += a;
                    }
                }
                if (!occupied) continue;
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
        for (boolean present : bins) if (present) binCount++;
        assertTrue(maxX >= minX && maxY >= minY);
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
                if (alpha(image, x, y) <= 16) continue;
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(maxX >= minX && maxY >= minY);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean hasVisibleNear(BufferedImage image, int cx, int cy, int radius) {
        int r2 = radius * radius;
        for (int y = Math.max(0, cy - radius); y <= Math.min(image.getHeight() - 1, cy + radius); y++) {
            int dy = y - cy;
            for (int x = Math.max(0, cx - radius); x <= Math.min(image.getWidth() - 1, cx + radius); x++) {
                int dx = x - cx;
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

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
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
            int width,
            int height,
            int visibleCells,
            int lumaBins,
            int minLuma,
            int maxLuma) { }
}
