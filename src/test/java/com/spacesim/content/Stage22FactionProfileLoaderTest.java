package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22FactionProfileCatalog.PackageScope;
import com.spacesim.world.StrategicGoalType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22FactionProfileLoaderTest {
    @Test
    void defaultCatalogBindsExactlyTwoDeterministicCoreProfilesToGovernedStableIds() {
        Stage22FactionProfileCatalog first = Stage22FactionProfileLoader.loadDefault();
        Stage22FactionProfileCatalog second = Stage22FactionProfileLoader.loadDefault();

        assertEquals(1, first.schemaVersion());
        assertEquals("stage22.faction_profiles.v1", first.catalogVersion());
        assertEquals(2, first.systemicProfiles().size());
        assertEquals(2, first.doctrineProfiles().size());
        assertEquals(16, first.policyBindings().size());
        assertEquals(2, first.manifestReferences().size());
        assertEquals(4, first.visualProfiles().size());
        assertEquals(2, first.localizations().size());
        assertEquals(64, first.fingerprint().length());
        assertEquals(first.fingerprint(), second.fingerprint());

        assertEquals(
                Set.of("faction.imperial_directorate", "faction.industrial_combine"),
                first.systemicProfiles().stream()
                        .map(Stage22FactionProfileCatalog.SystemicProfileDefinition::stableFactionId)
                        .collect(Collectors.toSet()));
        assertTrue(first.manifestReferences().stream().allMatch(manifest ->
                manifest.scope() == PackageScope.CORE
                        && manifest.maturity() == ContentMaturity.SEED
                        && manifest.roleBindings().isEmpty()));
        assertTrue(first.localizations().stream().allMatch(localization ->
                localization.namespace().startsWith("localization.core.")));

        var empire = first.findProfileForFaction("faction.imperial_directorate");
        var union = first.findProfileForFaction("faction.industrial_combine");
        var empireDoctrine = first.findDoctrine(empire.doctrineProfileRef());
        var unionDoctrine = first.findDoctrine(union.doctrineProfileRef());
        assertEquals(45, empireDoctrine.institutionalDoctrine().tradeOpenness());
        assertEquals(80, empireDoctrine.institutionalDoctrine().securityPosture());
        assertEquals(90, unionDoctrine.institutionalDoctrine().economicResiliencePriority());
        assertEquals(9_500, empireDoctrine.strategicDoctrine().preferenceBasisPoints(StrategicGoalType.DEFEND));
        assertEquals(9_500, unionDoctrine.strategicDoctrine().preferenceBasisPoints(StrategicGoalType.STOCKPILE));
        assertNotEquals(
                empireDoctrine.strategicDoctrine().preferenceBasisPoints(StrategicGoalType.DEFEND),
                unionDoctrine.strategicDoctrine().preferenceBasisPoints(StrategicGoalType.DEFEND));
    }

    @Test
    void parserRejectsFutureSchemaMissingReferenceCircularDependencyAndPostCoreLeak() {
        String baseline = defaultJson();

        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replaceFirst("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replace(
                        "\"doctrineProfileRef\":\"doctrine.core.empire.v1\"",
                        "\"doctrineProfileRef\":\"doctrine.core.empire.missing\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replace(
                        "\"id\":\"policy.core.empire.industry.v1\",\"kind\":\"INDUSTRIAL\",\"authoritySeam\":\"STAGE18_INDUSTRY\",\"dependsOn\":[]",
                        "\"id\":\"policy.core.empire.industry.v1\",\"kind\":\"INDUSTRIAL\",\"authoritySeam\":\"STAGE18_INDUSTRY\",\"dependsOn\":[\"policy.core.empire.fleet.v1\"]")));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replaceFirst("\"scope\":\"CORE\"", "\"scope\":\"POST_CORE\"")));
    }

    @Test
    void parserRejectsDuplicateIdsVisualWithoutSystemicMatchAndRoleWithoutPhysicalPath() {
        String baseline = defaultJson();

        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replace("doctrine.core.industrial_union.v1", "doctrine.core.empire.v1")));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replace(
                        "\"shipVisualProfileRef\":\"visual.core.empire.ship.v1\"",
                        "\"shipVisualProfileRef\":\"visual.core.empire.character.v1\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage22FactionProfileLoader.parse(
                baseline.replaceFirst(
                        "\"roleBindings\":\\[\\]",
                        "\"roleBindings\":[{\"roleId\":\"role.core.empire.guard\","
                                + "\"fitId\":\"fit.core.empire.missing\","
                                + "\"productionPathRef\":\"hull.core.empire.missing\","
                                + "\"visualProfileRef\":\"visual.core.empire.ship.v1\"}]")));
    }

    private static String defaultJson() {
        try (InputStream stream = Stage22FactionProfileLoaderTest.class.getClassLoader()
                .getResourceAsStream(Stage22FactionProfileLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test profile resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read test profile resource", exception);
        }
    }
}
