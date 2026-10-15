package com.spacesim.presentation.asset;

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

class Stage23EResourceReferenceFreezeTest {
    private static final String ATLAS_PATH = "assets/stage20_5/resources/resource_body_atlas_v1.png";
    private static final List<Stage20MinimumPlayableSpriteCatalog.VisualRole> ROLES = List.of(
            Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_CARBONACEOUS,
            Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_WATER_ICE,
            Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_METALLIC,
            Stage20MinimumPlayableSpriteCatalog.VisualRole.RESOURCE_MINERAL);

    @Test
    void promotedResourceBodiesPassStage23EReferenceFreezeGate() throws Exception {
        BufferedImage atlas = readPng(ATLAS_PATH);
        List<RegionResult> results = new ArrayList<>();

        for (var role : ROLES) {
            var region = Stage20MinimumPlayableSpriteCatalog.binding(role).region();
            assertNotNull(region, role.name());
            assertEquals(320, region.pixelWidth(), role.name());
            assertEquals(320, region.pixelHeight(), role.name());

            assertEquals(0, alpha(atlas, region.pixelX(), region.pixelY()), role.name());
            assertEquals(0, alpha(atlas, region.pixelX() + region.pixelWidth() - 1, region.pixelY()), role.name());
            assertEquals(0, alpha(atlas, region.pixelX(), region.pixelY() + region.pixelHeight() - 1), role.name());
            assertEquals(0, alpha(
                    atlas,
                    region.pixelX() + region.pixelWidth() - 1,
                    region.pixelY() + region.pixelHeight() - 1), role.name());

            ScaleStats quarter = scaleStats(atlas, region.pixelX(), region.pixelY(), 320, 320, 4);
            assertReadable(role.name(), "25%", quarter, 48, 48, 700, 6, 32);

            ScaleStats eighth = scaleStats(atlas, region.pixelX(), region.pixelY(), 320, 320, 8);
            assertReadable(role.name(), "12.5%", eighth, 24, 24, 170, 5, 24);

            results.add(new RegionResult(role.name(), eighth.mask(), silhouetteDigest(eighth.mask())));
        }

        Set<String> digests = new HashSet<>();
        for (RegionResult result : results) {
            assertTrue(digests.add(result.digest()), "duplicate resource silhouette: " + result.role());
        }
        for (int i = 0; i < results.size(); i++) {
            for (int j = i + 1; j < results.size(); j++) {
                double iou = intersectionOverUnion(results.get(i).mask(), results.get(j).mask());
                assertTrue(
                        iou < 0.97d,
                        "resource silhouettes are insufficiently distinct at 12.5%: "
                                + results.get(i).role() + " vs " + results.get(j).role() + " IoU=" + iou);
            }
        }
    }

    private static void assertReadable(
            String role,
            String scale,
            ScaleStats stats,
            int minWidth,
            int minHeight,
            int minVisible,
            int minLumaBins,
            int minLumaRange) {
        assertTrue(stats.width() >= minWidth, role + " " + scale + " width=" + stats.width());
        assertTrue(stats.height() >= minHeight, role + " " + scale + " height=" + stats.height());
        assertTrue(stats.visibleCells() >= minVisible, role + " " + scale + " visible=" + stats.visibleCells());
        assertTrue(stats.lumaBins() >= minLumaBins, role + " " + scale + " lumaBins=" + stats.lumaBins());
        assertTrue(stats.maxLuma() - stats.minLuma() >= minLumaRange,
                role + " " + scale + " lumaRange=" + (stats.maxLuma() - stats.minLuma()));
    }

    private static ScaleStats scaleStats(
            BufferedImage atlas,
            int originX,
            int originY,
            int sourceWidth,
            int sourceHeight,
            int factor) {
        int width = sourceWidth / factor;
        int height = sourceHeight / factor;
        boolean[] mask = new boolean[width * height];
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
                long weightedLuma = 0L;
                long alphaWeight = 0L;
                boolean occupied = false;
                for (int sy = 0; sy < factor; sy++) {
                    for (int sx = 0; sx < factor; sx++) {
                        int rgba = atlas.getRGB(
                                originX + dx * factor + sx,
                                originY + dy * factor + sy);
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
                mask[dy * width + dx] = true;
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
        assertTrue(maxX >= minX && maxY >= minY, "resource region must contain visible pixels");
        return new ScaleStats(
                mask,
                maxX - minX + 1,
                maxY - minY + 1,
                visible,
                binCount,
                minLuma,
                maxLuma);
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

    private static BufferedImage readPng(String path) throws IOException {
        try (InputStream input = Stage23EResourceReferenceFreezeTest.class
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

    private record RegionResult(String role, boolean[] mask, String digest) { }

    private record ScaleStats(
            boolean[] mask,
            int width,
            int height,
            int visibleCells,
            int lumaBins,
            int minLuma,
            int maxLuma) { }
}
