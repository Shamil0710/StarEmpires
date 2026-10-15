package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EReferenceManifestIntegrityTest {
    private static final Path MANIFEST =
            Path.of("docs/art/reference_freeze/stage23e_reference_manifest.tsv");

    @Test
    void frozenReferencesHaveRealPinnedRepositorySourcesAndReviews() throws Exception {
        assertTrue(Files.isRegularFile(MANIFEST), MANIFEST.toString());

        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String[] header = lines.get(0).split("\\t", -1);
        assertEquals(14, header.length);
        assertEquals("reference_id", header[0]);
        assertEquals("source_asset_path", header[8]);
        assertEquals("source_git_blob_sha", header[9]);
        assertEquals("latest_review", header[10]);
        assertEquals("geometry_frozen", header[12]);

        Set<String> ids = new HashSet<>();
        int rowCount = 0;
        int frozenCount = 0;

        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            if (line.isBlank()) {
                continue;
            }

            String[] columns = line.split("\\t", -1);
            assertEquals(14, columns.length, "manifest line " + lineNumber);

            String referenceId = columns[0];
            String status = columns[5];
            String sourceAssetPath = columns[8];
            String sourceGitBlobSha = columns[9];
            String latestReview = columns[10];
            String geometryFrozen = columns[12];

            assertFalse(referenceId.isBlank(), "manifest line " + lineNumber);
            assertTrue(ids.add(referenceId), "duplicate reference_id " + referenceId);
            rowCount++;

            if ("FROZEN".equals(status)) {
                frozenCount++;
                assertEquals("true", geometryFrozen, referenceId);
                assertFalse(sourceAssetPath.isBlank(), referenceId + " source path");
                assertFalse(sourceGitBlobSha.isBlank(), referenceId + " source blob SHA");
                assertFalse(latestReview.isBlank(), referenceId + " latest review");

                Path source = repositoryPath(sourceAssetPath);
                assertTrue(Files.isRegularFile(source),
                        referenceId + " missing frozen source " + source);
                assertTrue(source.getFileName().toString().endsWith(".png"),
                        referenceId + " frozen artistic source must be PNG: " + source);
                assertPngSignature(source, referenceId);

                String actualBlobSha = gitBlobSha(source);
                assertEquals(sourceGitBlobSha, actualBlobSha,
                        referenceId + " frozen source blob mismatch");

                Path review = repositoryPath(latestReview);
                assertTrue(Files.isRegularFile(review),
                        referenceId + " missing freeze review " + review);
            }

            if ("true".equals(geometryFrozen)) {
                assertEquals("FROZEN", status,
                        referenceId + " cannot set geometry_frozen outside FROZEN");
            }

            if ("SELECTED".equals(status) && sourceAssetPath.isBlank()) {
                assertEquals("false", geometryFrozen,
                        referenceId + " selected row without source cannot be frozen");
            }
        }

        assertEquals(49, rowCount, "Stage-23E reference inventory must stay explicit");
        assertTrue(frozenCount > 0);
    }

    private static Path repositoryPath(String manifestPath) {
        int fragment = manifestPath.indexOf('#');
        String clean = fragment >= 0 ? manifestPath.substring(0, fragment) : manifestPath;
        return Path.of(clean);
    }

    private static void assertPngSignature(Path path, String referenceId) throws IOException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] signature = new byte[] {
                (byte) 0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        };
        assertTrue(bytes.length >= signature.length, referenceId + " truncated PNG");
        for (int i = 0; i < signature.length; i++) {
            assertEquals(signature[i], bytes[i],
                    referenceId + " invalid PNG signature at byte " + i);
        }
    }

    private static String gitBlobSha(Path path) throws IOException, NoSuchAlgorithmException {
        byte[] bytes = Files.readAllBytes(path);
        byte[] prefix = ("blob " + bytes.length + "\0").getBytes(StandardCharsets.UTF_8);

        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        digest.update(prefix);
        digest.update(bytes);
        return HexFormat.of().formatHex(digest.digest());
    }
}
