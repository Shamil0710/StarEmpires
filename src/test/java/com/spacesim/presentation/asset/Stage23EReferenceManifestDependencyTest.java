package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EReferenceManifestDependencyTest {
    private static final Path MANIFEST =
            Path.of("docs/art/reference_freeze/stage23e_reference_manifest.tsv");
    private static final Set<String> STATUSES = Set.of("PLANNED", "SELECTED", "FROZEN");
    private static final Set<String> MODES =
            Set.of("PROMOTE_EXISTING", "GENERATE_5_SELECT_1", "DERIVE_FROM_REFERENCE");

    @Test
    void referenceLifecycleRespectsBaseAndSelectionDependencies() throws Exception {
        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = new LinkedHashMap<>();
        for (int i = 0; i < header.length; i++) {
            index.put(header[i], i);
        }

        Map<String, Row> rows = new LinkedHashMap<>();
        for (int lineNumber = 2; lineNumber <= lines.size(); lineNumber++) {
            String line = lines.get(lineNumber - 1);
            if (line.isBlank()) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            assertEquals(header.length, columns.length, "manifest line " + lineNumber);
            Row row = new Row(
                    value(columns, index, "reference_id"),
                    value(columns, index, "mode"),
                    value(columns, index, "status"),
                    value(columns, index, "prompt_section"),
                    value(columns, index, "base_reference"),
                    value(columns, index, "source_asset_path"),
                    value(columns, index, "source_git_blob_sha"),
                    value(columns, index, "latest_review"),
                    value(columns, index, "selected_candidate"),
                    value(columns, index, "geometry_frozen"));
            assertTrue(STATUSES.contains(row.status()), row.referenceId());
            assertTrue(MODES.contains(row.mode()), row.referenceId());
            assertFalse(row.promptSection().isBlank(), row.referenceId() + " prompt section");
            rows.put(row.referenceId(), row);
        }

        assertEquals(49, rows.size());

        for (Row row : rows.values()) {
            if ("DERIVE_FROM_REFERENCE".equals(row.mode())) {
                assertFalse(row.baseReference().isBlank(), row.referenceId() + " base reference");
                Row base = rows.get(row.baseReference());
                assertNotNull(base, row.referenceId() + " unknown base " + row.baseReference());

                if (!"PLANNED".equals(row.status())) {
                    assertEquals(
                            "FROZEN",
                            base.status(),
                            row.referenceId() + " cannot advance before frozen base " + base.referenceId());
                    assertEquals(
                            "true",
                            base.geometryFrozen(),
                            row.referenceId() + " requires geometry-frozen base");
                }
            } else {
                assertTrue(
                        row.baseReference().isBlank(),
                        row.referenceId() + " non-derived row must not invent a base reference");
            }

            if ("GENERATE_5_SELECT_1".equals(row.mode())
                    && Set.of("SELECTED", "FROZEN").contains(row.status())) {
                assertFalse(row.selectedCandidate().isBlank(), row.referenceId() + " selected candidate");
                assertFalse(row.latestReview().isBlank(), row.referenceId() + " selection review");
            }

            if ("PROMOTE_EXISTING".equals(row.mode())
                    && Set.of("SELECTED", "FROZEN").contains(row.status())) {
                assertFalse(row.sourceAssetPath().isBlank(), row.referenceId() + " promoted source");
                assertFalse(row.sourceGitBlobSha().isBlank(), row.referenceId() + " promoted source SHA");
                assertFalse(row.latestReview().isBlank(), row.referenceId() + " promotion review");
            }

            if ("FROZEN".equals(row.status())) {
                assertEquals("true", row.geometryFrozen(), row.referenceId());
            } else {
                assertEquals("false", row.geometryFrozen(), row.referenceId());
            }
        }
    }

    private static String value(String[] columns, Map<String, Integer> index, String name) {
        Integer position = index.get(name);
        assertNotNull(position, "missing manifest column " + name + " in " + Arrays.toString(columns));
        return columns[position];
    }

    private record Row(
            String referenceId,
            String mode,
            String status,
            String promptSection,
            String baseReference,
            String sourceAssetPath,
            String sourceGitBlobSha,
            String latestReview,
            String selectedCandidate,
            String geometryFrozen) { }
}
