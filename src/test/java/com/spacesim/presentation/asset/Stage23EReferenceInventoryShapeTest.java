package com.spacesim.presentation.asset;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage23EReferenceInventoryShapeTest {
    private static final Path MANIFEST =
            Path.of("docs/art/reference_freeze/stage23e_reference_manifest.tsv");

    @Test
    void manifestKeepsTheFiniteStage23EReferenceInventory() throws Exception {
        List<String> lines = Files.readAllLines(MANIFEST, StandardCharsets.UTF_8);
        assertFalse(lines.isEmpty());

        String[] header = lines.get(0).split("\\t", -1);
        Map<String, Integer> index = index(header);
        Map<String, Integer> domainCounts = new LinkedHashMap<>();
        Map<String, Integer> factionCounts = new LinkedHashMap<>();

        int total = 0;
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            String[] columns = lines.get(i).split("\\t", -1);
            domainCounts.merge(value(columns, index, "domain"), 1, Integer::sum);
            factionCounts.merge(value(columns, index, "faction"), 1, Integer::sum);
            total++;
        }

        assertEquals(49, total);
        assertEquals(18, domainCounts.getOrDefault("major_ship", 0));
        assertEquals(2, domainCounts.getOrDefault("small_craft", 0));
        assertEquals(6, domainCounts.getOrDefault("small_craft_fit", 0));
        assertEquals(16, domainCounts.getOrDefault("station", 0));
        assertEquals(4, domainCounts.getOrDefault("resource_body", 0));
        assertEquals(3, domainCounts.getOrDefault("special_location", 0));

        assertEquals(21, factionCounts.getOrDefault("empire", 0));
        assertEquals(21, factionCounts.getOrDefault("industrial_union", 0));
        assertEquals(7, factionCounts.getOrDefault("shared", 0));
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
        assertNotNull(i, "missing manifest column " + name);
        return columns[i];
    }
}
