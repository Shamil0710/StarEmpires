package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Repository-wide Stage-22.0 reverse-reference guard for literal faction IDs. */
class Stage22RepositoryFactionReferenceAuditTest {
    private static final Pattern FACTION_LITERAL = Pattern.compile(
            "faction\\.[a-z0-9]+(?:[._-][a-z0-9]+)*");
    private static final List<Path> AUDIT_ROOTS = List.of(
            Path.of("src/main"),
            Path.of("src/test"),
            Path.of("docs"));

    @Test
    void everyProductionFactionLiteralIsGovernedAndCoreFixtureAliasesStayOutOfRuntimeAuthority() throws IOException {
        Stage22ContentGovernanceCatalog governance = Stage22ContentGovernanceLoader.loadDefault();
        Map<String, Set<String>> productionReferences = new LinkedHashMap<>();
        Map<String, Set<String>> testReferences = new LinkedHashMap<>();
        Map<String, Set<String>> documentationReferences = new LinkedHashMap<>();

        for (Path root : AUDIT_ROOTS) {
            if (!Files.exists(root)) {
                throw new IllegalStateException("Stage-22 repository audit root is missing: " + root);
            }
            try (var paths = Files.walk(root)) {
                for (Path file : paths.filter(Files::isRegularFile).sorted().toList()) {
                    if (!isTextSource(file)) continue;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    Matcher matcher = FACTION_LITERAL.matcher(text);
                    while (matcher.find()) {
                        Map<String, Set<String>> target = file.startsWith(Path.of("src/main"))
                                ? productionReferences
                                : file.startsWith(Path.of("src/test")) ? testReferences : documentationReferences;
                        target.computeIfAbsent(matcher.group(), ignored -> new LinkedHashSet<>())
                                .add(file.toString().replace('\\', '/'));
                    }
                }
            }
        }

        List<String> ungovernedProduction = new ArrayList<>();
        productionReferences.forEach((id, paths) -> {
            if (governance.findFactionIdentity(id) == null) {
                ungovernedProduction.add(id + " -> " + paths);
            }
        });
        ungovernedProduction.sort(Comparator.naturalOrder());
        assertTrue(ungovernedProduction.isEmpty(),
                () -> "Production faction literals without Stage-22.0 identity disposition: " + ungovernedProduction);

        assertFalse(productionReferences.containsKey("faction.empire"),
                "Stage-21 core-pair fixture name must not become a second production state owner");
        assertFalse(productionReferences.containsKey("faction.industrial-union"),
                "Stage-21 core-pair fixture name must not become a second production state owner");
        assertTrue(testReferences.containsKey("faction.empire") || documentationReferences.containsKey("faction.empire"),
                "Core package fixture/display reference should remain visible outside runtime authority");
        assertTrue(testReferences.containsKey("faction.industrial-union")
                        || documentationReferences.containsKey("faction.industrial-union"),
                "Industrial Union fixture/display reference should remain visible outside runtime authority");
    }

    private static boolean isTextSource(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".java")
                || name.endsWith(".json")
                || name.endsWith(".md")
                || name.endsWith(".txt")
                || name.endsWith(".xml")
                || name.endsWith(".properties");
    }
}
