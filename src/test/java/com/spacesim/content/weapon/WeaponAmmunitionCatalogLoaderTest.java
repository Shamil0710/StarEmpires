package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeaponAmmunitionCatalogLoaderTest {
    @Test
    void defaultCatalogLoadsPhysicalKineticAndGuidedAmmunition() {
        WeaponAmmunitionCatalog catalog = WeaponAmmunitionCatalogLoader.loadDefault();

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(1, catalog.getMigrationVersion());
        assertEquals(1, catalog.getKineticAmmunition().size());
        assertEquals(1, catalog.getGuidedAmmunition().size());
        assertEquals(64, catalog.getFingerprint().length());
        assertEquals(ProjectileShape.DART, catalog.findKinetic("ammo.rail_dart_150kg_v1").shape());
        assertEquals(150d, catalog.findKinetic("ammo.rail_dart_150kg_v1").massKg(), 1e-12d);
        assertEquals(1000d, catalog.findGuided("ammo.interceptor_1t_v1").wetMassKg(), 1e-12d);
    }

    @Test
    void reloadFingerprintIsStableAndMaterialReferencesUseEngineeringCatalog() {
        String json = defaultJson();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog first = WeaponAmmunitionCatalogLoader.parse(json, engineering);
        WeaponAmmunitionCatalog second = WeaponAmmunitionCatalogLoader.parse("\n" + json + "\n", engineering);

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertNotNull(engineering.findMaterial(first.findKinetic("ammo.rail_dart_150kg_v1").materialId()));
        assertNotNull(engineering.findMaterial(first.findGuided("ammo.interceptor_1t_v1").materialId()));
    }

    @Test
    void rejectsUnknownMaterialDuplicateIdsAndProbabilityOrHardRangeAbstractions() {
        String json = defaultJson();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("material.high_strength_steel_v1", "material.missing_v1"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("ammo.interceptor_1t_v1", "ammo.rail_dart_150kg_v1"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("\"massKg\": 150.0", "\"weaponAccuracy\": 0.95, \"massKg\": 150.0"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("\"massKg\": 150.0", "\"hardRangeM\": 3000000.0, \"massKg\": 150.0"), engineering));
    }

    @Test
    void rejectsInvalidGuidedPropellantClosureThroughSharedRuntimeDefinition() {
        String json = defaultJson();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("\"propellantMassKg\": 200.0", "\"propellantMassKg\": 10.0"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponAmmunitionCatalogLoader.parse(
                json.replace("\"terminalReserveMps\": 300.0", "\"terminalReserveMps\": 5000.0"), engineering));
    }

    private static String defaultJson() {
        try (InputStream stream = WeaponAmmunitionCatalogLoaderTest.class.getClassLoader()
                .getResourceAsStream(WeaponAmmunitionCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing ammunition test resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
