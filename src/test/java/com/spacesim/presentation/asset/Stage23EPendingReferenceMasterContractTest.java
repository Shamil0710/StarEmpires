package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EPendingReferenceMasterContractTest {
    private static final Path CONTRACT =
            Path.of("docs/art/reference_freeze/stage23e_pending_master_contracts.tsv");
    private static final Path MANIFEST =
            Path.of("docs/art/reference_freeze/stage23e_reference_manifest.tsv");

    @Test
    void acceptedPendingMastersRemainPinnedUntilRepositoryPersistence() throws Exception {
        Map<String, ManifestRow> manifest = loadManifest();
        List<String> lines = Files.readAllLines(CONTRACT, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = index(header);
        assertEquals(4, lines.size() - 1);

        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String[] c = lines.get(lineNumber - 1).split("\\t", -1);
            String referenceId = value(c, index, "reference_id");
            String candidate = value(c, index, "selected_candidate");
            String expectedSha256 = value(c, index, "expected_sha256");
            long expectedSize = Long.parseLong(value(c, index, "expected_size_bytes"));
            int width = Integer.parseInt(value(c, index, "width"));
            int height = Integer.parseInt(value(c, index, "height"));
            int minX = Integer.parseInt(value(c, index, "alpha_min_x"));
            int minY = Integer.parseInt(value(c, index, "alpha_min_y"));
            int maxX = Integer.parseInt(value(c, index, "alpha_max_x"));
            int maxY = Integer.parseInt(value(c, index, "alpha_max_y"));
            Path master = Path.of(value(c, index, "intended_master_path"));

            ManifestRow row = manifest.get(referenceId);
            assertNotNull(row, referenceId);
            assertEquals("SELECTED", row.status(), referenceId);
            assertEquals("false", row.geometryFrozen(), referenceId);
            assertEquals(candidate, row.selectedCandidate(), referenceId);

            if (!Files.exists(master)) {
                continue;
            }

            assertTrue(Files.isRegularFile(master), referenceId);
            byte[] bytes = Files.readAllBytes(master);
            assertEquals(expectedSize, bytes.length, referenceId + " byte size");
            assertEquals(expectedSha256, sha256(bytes), referenceId + " SHA-256");

            BufferedImage image = ImageIO.read(master.toFile());
            assertNotNull(image, referenceId);
            assertEquals(width, image.getWidth(), referenceId);
            assertEquals(height, image.getHeight(), referenceId);
            assertTrue(image.getColorModel().hasAlpha(), referenceId);

            Bounds bounds = bounds(image);
            assertEquals(minX, bounds.minX(), referenceId);
            assertEquals(minY, bounds.minY(), referenceId);
            assertEquals(maxX, bounds.maxX(), referenceId);
            assertEquals(maxY, bounds.maxY(), referenceId);

            assertEquals(0, alpha(image, 0, 0), referenceId);
            assertEquals(0, alpha(image, width - 1, 0), referenceId);
            assertEquals(0, alpha(image, 0, height - 1), referenceId);
            assertEquals(0, alpha(image, width - 1, height - 1), referenceId);
        }
    }

    private static Map<String, ManifestRow> loadManifest() throws Exception {
        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = index(header);
        Map<String, ManifestRow> result = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] c = lines.get(i).split("\\t", -1);
            result.put(
                    value(c, index, "reference_id"),
                    new ManifestRow(
                            value(c, index, "status"),
                            value(c, index, "selected_candidate"),
                            value(c, index, "geometry_frozen")));
        }
        return result;
    }

    private static Map<String, Integer> index(String[] header) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            result.put(header[i], i);
        }
        return result;
    }

    private static String value(String[] columns, Map<String, Integer> index, String name) {
        Integer i = index.get(name);
        assertNotNull(i, "missing column " + name);
        return columns[i];
    }

    private static Bounds bounds(BufferedImage image) {
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
        assertTrue(maxX >= minX && maxY >= minY);
        return new Bounds(minX, minY, maxX, maxY);
    }

    private static int alpha(BufferedImage image, int x, int y) {
        return (image.getRGB(x, y) >>> 24) & 0xff;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private record ManifestRow(String status, String selectedCandidate, String geometryFrozen) { }
    private record Bounds(int minX, int minY, int maxX, int maxY) { }
}
