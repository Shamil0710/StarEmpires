package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22EmpireCharacterLineupTest {
    private static final Set<String> REQUIRED_M22_3_ROLES = Set.of(
            "industrial_worker_technician",
            "fleet_enlisted_specialist",
            "line_officer",
            "senior_officer",
            "civil_administrator",
            "noble_high_official",
            "field_damaged_tired_variant");

    @Test
    void canonicalLineupComposesSharedMasterAndEmpireVisualAuthorityDeterministically() {
        var first = Stage22EmpireCharacterLineup.loadDefault();
        var second = Stage22EmpireCharacterLineup.loadDefault();

        assertEquals(1, first.schemaVersion());
        assertEquals("docs/characters/character_master_prompt.md", first.masterPromptRef());
        assertEquals("docs/factions/empire_visual_bible.md", first.factionVisualRef());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertTrue(first.overlays().size() >= REQUIRED_M22_3_ROLES.size());

        Set<String> actualRoles = first.overlays().stream()
                .map(Stage22EmpireCharacterLineup.OverlayDefinition::roleKey)
                .collect(Collectors.toSet());
        assertTrue(actualRoles.containsAll(REQUIRED_M22_3_ROLES));
        first.overlays().forEach(overlay -> {
            assertTrue(overlay.id().startsWith("character_overlay.empire."), overlay.id());
            assertFalse(overlay.roleBrief().isBlank(), overlay.id());
            assertFalse(overlay.statusReadability().isBlank(), overlay.id());
            assertFalse(overlay.practicalGear().isBlank(), overlay.id());
            assertFalse(overlay.condition().isBlank(), overlay.id());
        });
    }

    @Test
    void requiredStatusHierarchyIsExpressedInsideOneSharedArtStyle() {
        var lineup = Stage22EmpireCharacterLineup.loadDefault();
        var enlisted = lineup.findOverlay("character_overlay.empire.fleet_enlisted_specialist");
        var officer = lineup.findOverlay("character_overlay.empire.senior_officer");
        var official = lineup.findOverlay("character_overlay.empire.service_noble");
        var field = lineup.findOverlay("character_overlay.empire.field_worn_variant");

        assertTrue(enlisted.statusReadability().contains("subordinate"));
        assertTrue(officer.statusReadability().contains("ivory-grey"));
        assertTrue(official.statusReadability().contains("Best material quality"));
        assertTrue(field.statusReadability().contains("rank remain legible"));
        assertTrue(field.condition().contains("never a different art style"));
    }

    @Test
    void parserRejectsBrokenAuthorityNamespaceDuplicateAndRequiredRoleLoss() {
        String source = resourceText();

        assertThrows(IllegalArgumentException.class, () -> Stage22EmpireCharacterLineup.parse(
                source.replace("docs/characters/character_master_prompt.md", "docs/characters/other_prompt.md")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpireCharacterLineup.parse(
                source.replace("docs/factions/empire_visual_bible.md", "docs/factions/other_visual_bible.md")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpireCharacterLineup.parse(
                source.replace("character_overlay.empire.line_officer", "character_overlay.union.line_officer")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpireCharacterLineup.parse(
                source.replace("\"roleKey\": \"line_officer\"", "\"roleKey\": \"fleet_enlisted_specialist\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpireCharacterLineup.parse(
                source.replace("character_overlay.empire.recon_officer", "character_overlay.empire.line_officer")));
    }

    private static String resourceText() {
        try (var stream = Stage22EmpireCharacterLineupTest.class.getClassLoader()
                .getResourceAsStream(Stage22EmpireCharacterLineup.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Empire character-lineup resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
