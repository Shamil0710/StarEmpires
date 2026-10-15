package com.spacesim.presentation.asset;

import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.ScaleAuthority;
import com.spacesim.presentation.asset.Stage20MinimumPlayableSpriteCatalog.VisualRole;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EExistingEmpireStationReferenceFreezeTest {
    @Test
    void promotedEmpireStationsPassReferenceFreezeGate() throws Exception {
        List<ReviewCase> cases = List.of(
                new ReviewCase(
                        "ref.empire.station.industrial_station.v1",
                        "station.infrastructure.industrial_station",
                        VisualRole.INDUSTRIAL_STATION,
                        "assets/stage20_5/stations/imperial_industrial_station_v1.png",
                        768,
                        512,
                        List.of(
                                new Anchor("central_service_core", 0.50f, 0.50f),
                                new Anchor("dock_left", 0.055f, 0.50f),
                                new Anchor("dock_right", 0.945f, 0.50f),
                                new Anchor("production_upper_left", 0.30f, 0.34f),
                                new Anchor("production_lower_left", 0.30f, 0.66f),
                                new Anchor("production_upper_right", 0.70f, 0.34f),
                                new Anchor("production_lower_right", 0.70f, 0.66f))),
                new ReviewCase(
                        "ref.empire.station.trade_logistics_hub.v1",
                        "station.infrastructure.trade_logistics_hub",
                        VisualRole.TRADE_DOCK_STATION,
                        "assets/stage20_5/stations/imperial_trade_hub_v1.png",
                        640,
                        640,
                        List.of(
                                new Anchor("traffic_control_core", 0.50f, 0.50f),
                                new Anchor("dock_top", 0.50f, 0.085f),
                                new Anchor("dock_bottom", 0.50f, 0.915f),
                                new Anchor("dock_left", 0.085f, 0.50f),
                                new Anchor("dock_right", 0.915f, 0.50f),
                                new Anchor("cargo_upper_left", 0.35f, 0.35f),
                                new Anchor("cargo_upper_right", 0.65f, 0.35f),
                                new Anchor("cargo_lower_left", 0.35f, 0.65f),
                                new Anchor("cargo_lower_right", 0.65f, 0.65f))));

        boolean[][] normalizedMasks = new boolean[cases.size()][];
        for (int i = 0; i < cases.size(); i++) {
            ReviewCase review = cases.get(i);
            BufferedImage image = readPng(review.path());
            assertEquals(review.width(), image.getWidth(), review.referenceId());
            assertEquals(review.height(), image.getHeight(), review.referenceId());
            assertTrue(image.getColorModel().hasAlpha(), review.referenceId());
            assertTransparentCorners(image, review.referenceId());

            Bounds bounds = bounds(image);
            assertTrue(bounds.width() >= image.getWidth() * 0.55,
                    review.referenceId() + " visible width=" + bounds.width());
            assertTrue(bounds.height() >= image.getHeight() * 0.52,
                    review.referenceId() + " visible height=" + bounds.height());

            ScaleStats quarter = scaleStats(image, 4);
            assertReadable(review.referenceId(), "25%", quarter, 0.50, 0.45, 0.08, 5, 32);

            ScaleStats eighth = scaleStats(image, 8);
            assertReadable(review.referenceId(), "12.5%", eighth, 0.48, 0.42, 0.07, 4, 24);

            int anchorRadius = Math.max(
                    36,
                    Math.round(Math.min(image.getWidth(), image.getHeight()) * 0.085f));
            for (Anchor anchor : review.anchors()) {
                int x = Math.round(anchor.x() * (image.getWidth() - 1));
                int y = Math.round(anchor.y() * (image.getHeight() - 1));
                assertTrue(
                        hasOpaquePixelNear(image, x, y, anchorRadius),
                        review.referenceId() + " presentation anchor " + anchor.id()
                                + " lacks nearby authored geometry");
            }

            normalizedMasks[i] = normalizedMask(image, 64, 64);

            var binding = Stage20MinimumPlayableSpriteCatalog.binding(review.role());
            assertEquals(review.path(), binding.texturePath(), review.referenceId());

            var resolved = Stage20MinimumPlayableSpriteCatalog.resolveStation(
                    review.archetypeId(),
                    false,
                    Stage20StationPhysicalGeometryProfile.deriveCurrent());
            assertEquals(ScaleAuthority.EXACT_PHYSICAL_CONTENT, resolved.scaleAuthority(), review.referenceId());
            assertEquals(review.role(), resolved.binding().role(), review.referenceId());
        }

        assertTrue(
                intersectionOverUnion(normalizedMasks[0], normalizedMasks[1]) < 0.85d,
                "industrial and trade references must remain silhouette-distinct at normalized review scale");
    }

    private static void assertReadable(
            String id,
            String label,
            ScaleStats stats,
            double minWidthFraction,
            double minHeightFraction,
            double minVisibleFraction,
            int minLumaBins,
            int minLumaRange) {
        assertTrue(stats.spanWidth() >= stats.canvasWidth() * minWidthFraction,
                id + " " + label + " spanWidth=" + stats.spanWidth());
        assertTrue(stats.spanHeight() >= stats.canvasHeight() * minHeightFraction,
                id + " " + label + " spanHeight=" + stats.spanHeight());
        assertTrue(stats.visibleCells() >= stats.canvasWidth() * stats.canvasHeight() * minVisibleFraction,
                id + " " + label + " visibleCells=" + stats.visibleCells());
        assertTrue(stats.lumaBins() >= minLumaBins,
                id + " " + label + " lumaBins=" + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                id + " " + label + " lumaRange=" + (stats.maxLuma() - stats.minLuma()));
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
                        int a = (rgba >>> 24) & 0xff;
                        if (a == 0) {
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
        assertTrue(maxX >= minX && maxY >= minY, "downscale must contain visible pixels");
        return new ScaleStats(
                width,
                height,
                maxX - minX + 1,
                maxY - minY + 1,
                visible,
                binCount,
                minLuma,
                maxLuma);
    }

    private static boolean[] normalizedMask(BufferedImage image, int width, int height) {
        boolean[] mask = new boolean[width * height];
        for (int gy = 0; gy < height; gy++) {
            int y0 = gy * image.getHeight() / height;
            int y1 = Math.max(y0 + 1, (gy + 1) * image.getHeight() / height);
            for (int gx = 0; gx < width; gx++) {
                int x0 = gx * image.getWidth() / width;
                int x1 = Math.max(x0 + 1, (gx + 1) * image.getWidth() / width);
                boolean occupied = false;
                for (int y = y0; y < y1 && !occupied; y++) {
                    for (int x = x0; x < x1; x++) {
                        if (alpha(image, x, y) > 16) {
                            occupied = true;
                            break;
                        }
                    }
                }
                mask[gy * width + gx] = occupied;
            }
        }
        return mask;
    }

    private static double intersectionOverUnion(boolean[] left, boolean[] right) {
        assertEquals(left.length, right.length);
        int intersection = 0;
        int union = 0;
        for (int i = 0; i < left.length; i++) {
            if (left[i] && right[i]) {
                intersection++;
            }
            if (left[i] || right[i]) {
                union++;
            }
        }
        assertTrue(union > 0);
        return intersection / (double) union;
    }

    private static Bounds bounds(BufferedImage image) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (alpha(image, x, y) == 0) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "sprite must contain visible pixels");
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static boolean hasOpaquePixelNear(
            BufferedImage image,
            int centerX,
            int centerY,
            int radius) {
        int minX = Math.max(0, centerX - radius);
        int maxX = Math.min(image.getWidth() - 1, centerX + radius);
        int minY = Math.max(0, centerY - radius);
        int maxY = Math.min(image.getHeight() - 1, centerY + radius);
        int r2 = radius * radius;
        for (int y = minY; y <= maxY; y++) {
            int dy = y - centerY;
            for (int x = minX; x <= maxX; x++) {
                int dx = x - centerX;
                if (dx * dx + dy * dy <= r2 && alpha(image, x, y) > 16) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void assertTransparentCorners(BufferedImage image, String id) {
        assertEquals(0, alpha(image, 0, 0), id);
        assertEquals(0, alpha(image, image.getWidth() - 1, 0), id);
        assertEquals(0, alpha(image, 0, image.getHeight() - 1), id);
        assertEquals(0, alpha(image, image.getWidth() - 1, image.getHeight() - 1), id);
    }

    private static BufferedImage readPng(String path) throws IOException {
        try (InputStream input = Stage23EExistingEmpireStationReferenceFreezeTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(input, path);
            BufferedImage image = ImageIO.read(input);
            assertNotNull(image, path);
            return image;
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private record ReviewCase(
            String referenceId,
            String archetypeId,
            VisualRole role,
            String path,
            int width,
            int height,
            List<Anchor> anchors) { }

    private record Anchor(String id, float x, float y) { }

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
