package com.spacesim.presentation.asset;

import com.spacesim.content.Stage22IndustrialUnionPackageLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EMajorShipReferenceFreezeTest {
    private static final int CANVAS_WIDTH = 768;
    private static final int CANVAS_HEIGHT = 512;

    @Test
    void promotedMajorShipBasesPassStage23EReferenceFreezeGate() throws Exception {
        List<ReviewResult> empire = new ArrayList<>();
        for (ReviewCase review : empireCases()) {
            empire.add(validate(review));
        }

        List<ReviewResult> union = new ArrayList<>();
        for (ReviewCase review : industrialUnionCases()) {
            union.add(validate(review));
        }

        assertEquals(9, empire.size());
        assertEquals(9, union.size());
        assertDistinctSilhouettes("empire", empire);
        assertDistinctSilhouettes("industrial_union", union);
    }

    private static List<ReviewCase> empireCases() {
        List<ReviewCase> result = new ArrayList<>();
        for (var visual : Stage22EmpireShipVisualCatalog.loadDefault().families()) {
            List<ReviewAnchor> anchors = visual.sprite().hardpoints().stream()
                    .map(value -> new ReviewAnchor(
                            value.id(),
                            value.normalizedX(),
                            value.normalizedY()))
                    .toList();
            result.add(new ReviewCase(
                    "empire",
                    visual.familyId(),
                    visual.assets().baseTexturePath(),
                    anchors));
        }
        return List.copyOf(result);
    }

    private static List<ReviewCase> industrialUnionCases() {
        var packageCatalog = Stage22IndustrialUnionPackageLoader.loadDefault();
        var engineering = Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault();
        List<ReviewCase> result = new ArrayList<>();

        for (var family : packageCatalog.shipFamilies()) {
            var fit = engineering.findDemonstratorFit(family.primaryFitId());
            assertNotNull(fit, family.primaryFitId());
            var hull = engineering.findHull(fit.hullId());
            assertNotNull(hull, fit.hullId());

            List<ReviewAnchor> anchors = new ArrayList<>();
            for (var hardpoint : hull.hardpoints()) {
                anchors.add(new ReviewAnchor(
                        "engineering_" + hardpoint.id(),
                        normalizedForward(hardpoint.positionM().yM(), hull.boundingDimensionsM().lengthM()),
                        normalizedTransverse(hardpoint.positionM().xM(), hull.boundingDimensionsM().widthM())));
            }
            anchors.add(new ReviewAnchor("engine_main", 0.06f, 0.50f));
            for (var compartment : hull.compartments()) {
                anchors.add(new ReviewAnchor(
                        "service_" + compartment.id(),
                        normalizedForward(compartment.centerM().yM(), hull.boundingDimensionsM().lengthM()),
                        normalizedTransverse(compartment.centerM().xM(), hull.boundingDimensionsM().widthM())));
            }

            String suffix = family.familyId().substring("ship_family.industrial_union.".length());
            result.add(new ReviewCase(
                    "industrial_union",
                    family.familyId(),
                    "assets/ships/industrial_union/production/" + suffix + "/" + suffix + "_base.png",
                    List.copyOf(anchors)));
        }
        return List.copyOf(result);
    }

    private static ReviewResult validate(ReviewCase review) throws Exception {
        BufferedImage image = readPng(review.path());
        assertEquals(CANVAS_WIDTH, image.getWidth(), review.path());
        assertEquals(CANVAS_HEIGHT, image.getHeight(), review.path());
        assertTrue(image.getColorModel().hasAlpha(), review.path());
        assertEquals(0, alpha(image, 0, 0), review.path());
        assertEquals(0, alpha(image, CANVAS_WIDTH - 1, 0), review.path());
        assertEquals(0, alpha(image, 0, CANVAS_HEIGHT - 1), review.path());
        assertEquals(0, alpha(image, CANVAS_WIDTH - 1, CANVAS_HEIGHT - 1), review.path());

        Bounds bounds = visibleBounds(image);
        assertTrue(bounds.minX() >= 40 && bounds.maxX() < CANVAS_WIDTH - 40, review.path());
        assertTrue(bounds.minY() >= 40 && bounds.maxY() < CANVAS_HEIGHT - 40, review.path());

        ScaleStats quarter = scaleStats(image, 4);
        assertReadableScale(review, quarter, 150, 38, 500, 7, 32, "25%");
        ScaleStats eighth = scaleStats(image, 8);
        assertReadableScale(review, eighth, 75, 18, 120, 5, 24, "12.5%");

        int anchorRadius = Math.max(40, Math.round(bounds.height() * 0.18f));
        for (ReviewAnchor anchor : review.anchors()) {
            int x = bounds.minX() + Math.round(anchor.normalizedX() * (bounds.width() - 1));
            int y = bounds.maxY() - Math.round(anchor.normalizedY() * (bounds.height() - 1));
            assertTrue(
                    hasOpaquePixelNear(image, x, y, anchorRadius),
                    review.familyId() + " anchor " + anchor.id()
                            + " is not visually supported near [" + x + "," + y + "]");
        }

        return new ReviewResult(
                review.familyId(),
                eighth.mask(),
                silhouetteDigest(eighth.mask()),
                eighth.width(),
                eighth.height());
    }

    private static void assertReadableScale(
            ReviewCase review,
            ScaleStats stats,
            int minSpanWidth,
            int minSpanHeight,
            int minVisibleCells,
            int minLumaBins,
            int minLumaRange,
            String label) {
        assertTrue(stats.width() >= minSpanWidth,
                review.familyId() + " " + label + " silhouette width " + stats.width());
        assertTrue(stats.height() >= minSpanHeight,
                review.familyId() + " " + label + " silhouette height " + stats.height());
        assertTrue(stats.visibleCells() >= minVisibleCells,
                review.familyId() + " " + label + " visible cells " + stats.visibleCells());
        assertTrue(stats.lumaBins() >= minLumaBins,
                review.familyId() + " " + label + " grayscale bins " + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                review.familyId() + " " + label + " grayscale range "
                        + (stats.maxLuma() - stats.minLuma()));
    }

    private static void assertDistinctSilhouettes(String faction, List<ReviewResult> results) {
        Set<String> digests = new HashSet<>();
        for (ReviewResult result : results) {
            assertTrue(digests.add(result.digest()),
                    faction + " duplicate 12.5% silhouette: " + result.familyId());
        }
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                ReviewResult left = results.get(i);
                ReviewResult right = results.get(j);
                double iou = intersectionOverUnion(left.mask(), right.mask());
                assertTrue(
                        iou < 0.97d,
                        faction + " silhouettes are insufficiently distinct at 12.5%: "
                                + left.familyId() + " vs " + right.familyId() + " IoU=" + iou);
            }
        }
    }

    private static ScaleStats scaleStats(BufferedImage source, int factor) {
        int width = source.getWidth() / factor;
        int height = source.getHeight() / factor;
        boolean[] mask = new boolean[width * height];
        boolean[] lumaBins = new boolean[16];
        int visibleCells = 0;
        int minX = width;
        int minY = height;
        int maxX = -1;
        int maxY = -1;
        int minLuma = 255;
        int maxLuma = 0;

        for (int dy = 0; dy < height; dy++) {
            for (int dx = 0; dx < width; dx++) {
                long weightedLuma = 0L;
                long alphaWeight = 0L;
                boolean visible = false;
                for (int sy = dy * factor; sy < (dy + 1) * factor; sy++) {
                    for (int sx = dx * factor; sx < (dx + 1) * factor; sx++) {
                        int rgba = source.getRGB(sx, sy);
                        int a = (rgba >>> 24) & 0xff;
                        if (a == 0) {
                            continue;
                        }
                        visible = true;
                        int r = (rgba >>> 16) & 0xff;
                        int g = (rgba >>> 8) & 0xff;
                        int b = rgba & 0xff;
                        int luma = (54 * r + 183 * g + 19 * b) / 256;
                        weightedLuma += (long) luma * a;
                        alphaWeight += a;
                    }
                }
                if (!visible) {
                    continue;
                }
                int index = dy * width + dx;
                mask[index] = true;
                visibleCells++;
                minX = Math.min(minX, dx);
                minY = Math.min(minY, dy);
                maxX = Math.max(maxX, dx);
                maxY = Math.max(maxY, dy);
                int luma = alphaWeight == 0L ? 0 : (int) (weightedLuma / alphaWeight);
                minLuma = Math.min(minLuma, luma);
                maxLuma = Math.max(maxLuma, luma);
                lumaBins[Math.min(15, luma / 16)] = true;
            }
        }

        int bins = 0;
        for (boolean present : lumaBins) {
            if (present) {
                bins++;
            }
        }
        assertTrue(maxX >= minX && maxY >= minY, "downscaled sprite must contain visible pixels");
        return new ScaleStats(
                mask,
                maxX - minX + 1,
                maxY - minY + 1,
                visibleCells,
                bins,
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

    private static boolean hasOpaquePixelNear(BufferedImage image, int centerX, int centerY, int radius) {
        int minX = Math.max(0, centerX - radius);
        int maxX = Math.min(image.getWidth() - 1, centerX + radius);
        int minY = Math.max(0, centerY - radius);
        int maxY = Math.min(image.getHeight() - 1, centerY + radius);
        int radiusSquared = radius * radius;
        for (int y = minY; y <= maxY; y++) {
            int dy = y - centerY;
            for (int x = minX; x <= maxX; x++) {
                int dx = x - centerX;
                if (dx * dx + dy * dy <= radiusSquared && alpha(image, x, y) > 16) {
                    return true;
                }
            }
        }
        return false;
    }

    private static String silhouetteDigest(boolean[] mask) {
        byte[] packed = new byte[(mask.length + 7) / 8];
        for (int i = 0; i < mask.length; i++) {
            if (mask[i]) {
                packed[i >>> 3] |= (byte) (1 << (i & 7));
            }
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(packed));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
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

    private static float normalizedForward(double yM, double lengthM) {
        return clamp01((float) (0.5d + yM / lengthM));
    }

    private static float normalizedTransverse(double xM, double widthM) {
        return clamp01((float) (0.5d + xM / widthM));
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static BufferedImage readPng(String path) throws IOException {
        try (InputStream stream = Stage23EMajorShipReferenceFreezeTest.class
                .getClassLoader().getResourceAsStream(path)) {
            assertNotNull(stream, path);
            BufferedImage image = ImageIO.read(stream);
            assertNotNull(image, path);
            return image;
        }
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private record ReviewCase(
            String faction,
            String familyId,
            String path,
            List<ReviewAnchor> anchors) { }

    private record ReviewAnchor(String id, float normalizedX, float normalizedY) { }

    private record ReviewResult(
            String familyId,
            boolean[] mask,
            String digest,
            int width,
            int height) { }

    private record Bounds(int minX, int minY, int maxX, int maxY) {
        int width() {
            return maxX - minX + 1;
        }

        int height() {
            return maxY - minY + 1;
        }
    }

    private record ScaleStats(
            boolean[] mask,
            int width,
            int height,
            int visibleCells,
            int lumaBins,
            int minLuma,
            int maxLuma) { }
}
