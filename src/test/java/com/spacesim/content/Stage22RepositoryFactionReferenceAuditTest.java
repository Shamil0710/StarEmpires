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
    private static final Pattern QUOTED_LITERAL = Pattern.compile(
            "\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final List<Path> AUDIT_ROOTS = List.of(
            Path.of("src/main"),
            Path.of("src/test"),
            Path.of("docs"));
    private static final Map<String, String> EXPLICIT_NON_STATE_PRINCIPALS = Map.of(
            "faction.acceptance.actor",
            "Stage19AggregateWarfareAcceptanceHarness deterministic acceptance principal",
            "faction.acceptance.opponent",
            "Stage19AggregateWarfareAcceptanceHarness deterministic acceptance principal",
            "faction.playable-generated-world.observer",
            "Stage20 discovery-knowledge observer principal; not a mutable world faction identity");

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
                    Map<String, Set<String>> target = file.startsWith(Path.of("src/main"))
                            ? productionReferences
                            : file.startsWith(Path.of("src/test")) ? testReferences : documentationReferences;
                    Set<String> references = file.startsWith(Path.of("src/main"))
                            ? productionFactionLiterals(file, text)
                            : allFactionReferences(text);
                    for (String reference : references) {
                        target.computeIfAbsent(reference, ignored -> new LinkedHashSet<>())
                                .add(file.toString().replace('\\', '/'));
                    }
                }
            }
        }

        List<String> ungovernedProduction = new ArrayList<>();
        productionReferences.forEach((id, paths) -> {
            if (governance.findFactionIdentity(id) == null && !EXPLICIT_NON_STATE_PRINCIPALS.containsKey(id)) {
                ungovernedProduction.add(id + " -> " + paths);
            }
        });
        ungovernedProduction.sort(Comparator.naturalOrder());
        assertTrue(ungovernedProduction.isEmpty(),
                () -> "Production faction literals without Stage-22.0 identity disposition: " + ungovernedProduction);

        EXPLICIT_NON_STATE_PRINCIPALS.forEach((id, reason) -> {
            assertTrue(productionReferences.containsKey(id),
                    () -> "Explicit non-state principal quarantine drifted or disappeared: " + id + " (" + reason + ")");
            assertTrue(governance.findFactionIdentity(id) == null,
                    () -> "Technical/scenario principal must not become a governed mutable faction identity: " + id);
        });

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

    private static Set<String> productionFactionLiterals(Path file, String text) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        Matcher quoted = QUOTED_LITERAL.matcher(text);
        while (quoted.find()) {
            String literal = quoted.group(1);
            if (FACTION_LITERAL.matcher(literal).matches()) {
                references.add(literal);
            }
        }

        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if (name.endsWith(".properties")) {
            for (String line : text.lines().toList()) {
                String stripped = line.strip();
                if (stripped.isEmpty() || stripped.startsWith("#") || stripped.startsWith("!")) continue;
                int equals = stripped.indexOf('=');
                int colon = stripped.indexOf(':');
                int separator = equals < 0 ? colon : colon < 0 ? equals : Math.min(equals, colon);
                String value = separator < 0 ? stripped : stripped.substring(separator + 1).strip();
                if (FACTION_LITERAL.matcher(value).matches()) {
                    references.add(value);
                }
            }
        }
        return references;
    }

    private static Set<String> allFactionReferences(String text) {
        LinkedHashSet<String> references = new LinkedHashSet<>();
        Matcher matcher = FACTION_LITERAL.matcher(text);
        while (matcher.find()) {
            references.add(matcher.group());
        }
        return references;
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
