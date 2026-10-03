package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage23EReferenceWorkQueueTest {
    private static final Path MANIFEST =
            Path.of("docs/art/reference_freeze/stage23e_reference_manifest.tsv");
    private static final Path QUEUE =
            Path.of("docs/art/reference_freeze/stage23e_reference_work_queue.tsv");

    @Test
    void workQueueCoversEveryAndOnlyUnfrozenReferenceExactlyOnce() throws Exception {
        Map<String, String> manifestStatus = manifestStatuses();
        List<String> lines = Files.readAllLines(QUEUE, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = index(header);
        Set<String> queued = new HashSet<>();
        int previousSequence = -1;

        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] c = lines.get(i).split("\\t", -1);
            assertEquals(header.length, c.length, "queue line " + (i + 1));

            int sequence = Integer.parseInt(value(c, index, "sequence"));
            String referenceId = value(c, index, "reference_id");
            String expectedStatus = value(c, index, "expected_status");
            String nextAction = value(c, index, "next_action");
            String proofGate = value(c, index, "proof_gate");
            String doneWhen = value(c, index, "done_when");

            assertTrue(sequence > previousSequence, referenceId + " queue sequence");
            previousSequence = sequence;
            assertTrue(queued.add(referenceId), "duplicate queue reference " + referenceId);

            String actualStatus = manifestStatus.get(referenceId);
            assertNotNull(actualStatus, "unknown queue reference " + referenceId);
            assertEquals(expectedStatus, actualStatus, referenceId);
            assertFalse("FROZEN".equals(actualStatus), referenceId + " must not remain queued");
            assertFalse(nextAction.isBlank(), referenceId + " next action");
            assertFalse(proofGate.isBlank(), referenceId + " proof gate");
            assertFalse(doneWhen.isBlank(), referenceId + " done condition");
        }

        Set<String> unresolved = new HashSet<>();
        for (Map.Entry<String, String> entry : manifestStatus.entrySet()) {
            if (!"FROZEN".equals(entry.getValue())) {
                unresolved.add(entry.getKey());
            }
        }

        assertEquals(22, unresolved.size());
        assertEquals(unresolved, queued);
    }

    private static Map<String, String> manifestStatuses() throws Exception {
        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = index(header);
        Map<String, String> result = new LinkedHashMap<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] c = lines.get(i).split("\\t", -1);
            result.put(value(c, index, "reference_id"), value(c, index, "status"));
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
}
