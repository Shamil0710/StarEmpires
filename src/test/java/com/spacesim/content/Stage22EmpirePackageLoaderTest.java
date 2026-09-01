package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage22EmpirePackageLoaderTest {
    @Test
    void loadsRequiredEmpireContentFloorDeterministically() {
        Stage22EmpirePackageCatalog first = Stage22EmpirePackageLoader.loadDefault();
        Stage22EmpirePackageCatalog second = Stage22EmpirePackageLoader.loadDefault();

        assertEquals(Stage22EmpirePackageCatalog.PACKAGE_KEY, first.packageKey());
        assertEquals(Stage22EmpirePackageCatalog.STABLE_FACTION_ID, first.stableFactionId());
        assertEquals(9, first.shipFamilies().size());
        assertEquals(3, first.stations().size());
        assertEquals(6, first.recurringNpcs().size());
        assertEquals(10, first.missions().size());
        assertEquals(2, first.storyChains().size());
        assertTrue(first.visualRules().size() >= 4);
        assertEquals(64, first.fingerprint().length());
        assertEquals(first.fingerprint(), second.fingerprint());
        assertNotNull(first.findShipForRole("role.military.battleship"));
        assertNotNull(first.findMission("mission.empire.formal_market_access"));
    }

    @Test
    void rejectsWrongStableIdentityAndUnsupportedMissionAuthority() {
        String source = resourceText();
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(
                source.replace("faction.imperial_directorate", "faction.empire")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(
                source.replace("\"authority\":\"DIPLOMACY\"", "\"authority\":\"EMPIRE_ONLY\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(
                source.replace("\"authority\":\"DIPLOMACY\"", "\"authority\":\"FLEET\"")));
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(
                source.replace("\"runtimeTemplate\":\"IMPERIAL_ACCESS_NEGOTIATION\"",
                        "\"runtimeTemplate\":\"CONVOY_ESCORT\"")));
    }

    @Test
    void rejectsStoryChainDanglingMissionAndFloorReduction() {
        String source = resourceText();
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(
                source.replace("mission.empire.formal_market_access\",\"mission.empire.reserve_delivery",
                        "mission.empire.missing\",\"mission.empire.reserve_delivery")));

        String withoutOneMission = source.replace(
                "    {\"id\":\"mission.empire.readiness_muster\",\"issuerNpcId\":\"npc.imperial.mikhail-orlov\",\"runtimeTemplate\":\"CONVOY_ESCORT\",\"authority\":\"FLEET\",\"objectiveKind\":\"ESCORT_FLEETS_PRESENT_IN_SYSTEM\",\"semanticIntent\":\"Assemble a named readiness group and its escort at the designated staging system through ordinary fleet movement.\"}\n",
                "");
        assertThrows(IllegalArgumentException.class, () -> Stage22EmpirePackageLoader.parse(withoutOneMission));
    }

    private static String resourceText() {
        try (var stream = Stage22EmpirePackageLoaderTest.class.getClassLoader()
                .getResourceAsStream(Stage22EmpirePackageLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource");
            }
            return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
