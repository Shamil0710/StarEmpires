package com.spacesim.combat.acceptance;

import com.spacesim.combat.acceptance.Stage175ICombatTestManifest.Doctrine;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatTestManifestTest {
    private static final String ENGINEERING = "data/content/stage17_5i-combat-test-engineering-v1.json";
    private static final String MANIFEST = "data/content/stage17_5i-combat-test-manifest-v1.json";

    @Test
    void manifestLocksFiveMateriallyDifferentFleetsAndRequiredPairMatrix() {
        Stage175ICombatTestManifest manifest = loadManifest();

        assertEquals("PRODUCTION_VALID_CONTENT_PROVISIONAL", manifest.contentStatus());
        assertTrue(manifest.stage22ReviewRequired());
        assertEquals(5, manifest.fleets().size());
        assertEquals(Set.of(
                Doctrine.KINETIC_LINE,
                Doctrine.MISSILE_STRIKE,
                Doctrine.HIGH_MOBILITY_BEAM,
                Doctrine.DEFENSIVE_EW,
                Doctrine.BALANCED_CONTROL), manifest.fleets().stream()
                .map(Stage175ICombatTestManifest.FleetDefinition::doctrine)
                .collect(Collectors.toSet()));
        assertEquals(11, manifest.matchups().size());
        assertTrue(manifest.variations().size() >= 7);
        assertTrue(manifest.fleets().stream().allMatch(fleet -> fleet.totalShipCount() >= 5));
        assertTrue(manifest.fleets().stream().allMatch(fleet -> fleet.totalShipCount() <= 8));
    }

    @Test
    void manifestIsDeterministicallyFingerprintableAndReferencesOnlyProductionFits() {
        ShipEngineeringCatalog engineering = loadEngineering();
        String json = read(MANIFEST);
        Stage175ICombatTestManifest first = Stage175ICombatTestManifestLoader.parse(json, engineering);
        Stage175ICombatTestManifest second = Stage175ICombatTestManifestLoader.parse(json, engineering);

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(64, first.fingerprint().length());
        assertFalse(first.fingerprint().isBlank());
        first.fleets().forEach(fleet -> fleet.ships().forEach(ship ->
                assertNotNull(engineering.findDemonstratorFit(ship.fitId()), ship.fitId())));
    }

    private static Stage175ICombatTestManifest loadManifest() {
        return Stage175ICombatTestManifestLoader.parse(read(MANIFEST), loadEngineering());
    }

    private static ShipEngineeringCatalog loadEngineering() {
        return ShipEngineeringCatalogLoader.parse(read(ENGINEERING));
    }

    private static String read(String resource) {
        ClassLoader classLoader = Stage175ICombatTestManifestTest.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + resource, exception);
        }
    }
}
