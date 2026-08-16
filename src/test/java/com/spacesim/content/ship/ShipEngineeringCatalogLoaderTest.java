package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointSize;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class ShipEngineeringCatalogLoaderTest {
    @Test
    void defaultCatalogLoadsMachineReadableDemonstratorFit() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(1, catalog.getMigrationVersion());
        assertEquals(2, catalog.getMaterials().size());
        assertEquals(1, catalog.getResponseSurfaces().size());
        assertEquals(1, catalog.getProtectionStacks().size());
        assertEquals(1, catalog.getHulls().size());
        assertEquals(5, catalog.getModules().size());
        assertEquals(1, catalog.getDemonstratorFits().size());
        assertEquals(64, catalog.getFingerprint().length());

        ShipEngineeringCatalog.HullDefinition hull = catalog.findHull("hull.escort_destroyer_v1");
        assertNotNull(hull);
        assertEquals(4, hull.slots().size());
        assertEquals(1, hull.hardpoints().size());
        assertEquals(3, hull.compartments().size());
        assertTrue(hull.maxOperationalMassKg() > hull.bareHullMassKg());
        assertTrue(hull.thrustMountCompatibility().contains(ModuleFamily.MAIN_DRIVE));

        ShipEngineeringCatalog.ModuleDefinition drive = catalog.findModule("module.main_drive_escort_v1");
        assertNotNull(drive);
        assertEquals(ModuleFamily.MAIN_DRIVE, drive.family());
        assertTrue(drive.integrationCategories().contains(IntegrationCategory.CORE));
        assertTrue(drive.capabilityParameters().get("thrust_n") > 0d);
        assertFalse(drive.interfaces().isEmpty());

        ShipEngineeringCatalog.ModuleDefinition weapon = catalog.findModule("module.railgun_large_v1");
        assertTrue(weapon.compatibleHardpointSizes().contains(HardpointSize.LARGE));
        assertNotNull(catalog.findMaterial("material.high_strength_steel_v1"));
        assertNotNull(catalog.findProtectionStack("protection.escort_structural_v1"));
        assertNotNull(catalog.findResponseSurface("response.synthetic_heavy_v1"));
        assertNotNull(catalog.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
    }

    @Test
    void allFrozenModuleFamiliesExistInSchemaLanguage() {
        assertEquals(15, ModuleFamily.values().length);
        assertNotNull(ModuleFamily.valueOf("REACTOR_POWER"));
        assertNotNull(ModuleFamily.valueOf("ENERGY_STORAGE"));
        assertNotNull(ModuleFamily.valueOf("MAIN_DRIVE"));
        assertNotNull(ModuleFamily.valueOf("MANEUVER_THRUSTERS"));
        assertNotNull(ModuleFamily.valueOf("FTL_JUMP"));
        assertNotNull(ModuleFamily.valueOf("THERMAL_CONTROL"));
        assertNotNull(ModuleFamily.valueOf("SENSOR_EW_FIRE_CONTROL"));
        assertNotNull(ModuleFamily.valueOf("COMMUNICATION_DATALINK"));
        assertNotNull(ModuleFamily.valueOf("SHIELD_FIELD"));
        assertNotNull(ModuleFamily.valueOf("ARMOR_PROTECTION"));
        assertNotNull(ModuleFamily.valueOf("WEAPON_AMMUNITION"));
        assertNotNull(ModuleFamily.valueOf("CREW_LIFE_SUPPORT_AUTOMATION"));
        assertNotNull(ModuleFamily.valueOf("CARGO_TANK_STORES"));
        assertNotNull(ModuleFamily.valueOf("HANGAR_SMALL_CRAFT"));
        assertNotNull(ModuleFamily.valueOf("MINING_SALVAGE_REPAIR_INDUSTRIAL_SCIENCE"));
    }

    @Test
    void repeatedReloadHasStableSemanticFingerprint() {
        String json = defaultJson();
        ShipEngineeringCatalog first = ShipEngineeringCatalogLoader.parse(json);
        ShipEngineeringCatalog second = ShipEngineeringCatalogLoader.parse("\n  " + json + "\n");
        ShipEngineeringCatalog third = ShipEngineeringCatalogLoader.loadDefault();

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(first.getFingerprint(), third.getFingerprint());
    }

    @Test
    void returnedCollectionsAreImmutable() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        assertThrows(UnsupportedOperationException.class, () -> catalog.getModules().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.findModule("module.main_drive_escort_v1").capabilityParameters().clear());
        assertThrows(UnsupportedOperationException.class,
                () -> catalog.findHull("hull.escort_destroyer_v1").slots().clear());
    }

    @Test
    void rejectsUnsupportedOrMissingMigrationContract() {
        String json = defaultJson();
        assertThrows(IllegalArgumentException.class,
                () -> ShipEngineeringCatalogLoader.parse(json.replace("\"schemaVersion\": 1", "\"schemaVersion\": 2")));
        assertThrows(IllegalArgumentException.class,
                () -> ShipEngineeringCatalogLoader.parse(json.replace("\"migrationVersion\": 1", "\"migrationVersion\": 0")));
        assertThrows(IllegalArgumentException.class,
                () -> ShipEngineeringCatalogLoader.parse(json.replace("\"migrationVersion\": 1,", "")));
    }

    @Test
    void rejectsMalformedAndBlankDocuments() {
        assertThrows(NullPointerException.class, () -> ShipEngineeringCatalogLoader.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse("  "));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse("[1,2,3]"));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse("{broken"));
    }

    @Test
    void rejectsUnknownReferencesAndDuplicateIds() {
        String json = defaultJson();
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"response.synthetic_heavy_v1\",\n      \"constructionMaterialFamilyId\"",
                "\"response.missing\",\n      \"constructionMaterialFamilyId\"")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"material.ceramic_strike_face_v1\",\n          \"thicknessM\"",
                "\"material.missing\",\n          \"thicknessM\"")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"hullId\": \"hull.escort_destroyer_v1\"",
                "\"hullId\": \"hull.missing\"")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"moduleId\": \"module.radiator_escort_v1\"",
                "\"moduleId\": \"module.missing\"")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"id\": \"module.main_drive_escort_v1\"",
                "\"id\": \"module.reactor_5gw_v1\"")));
    }

    @Test
    void rejectsNegativeNanLikeAndInvalidBoundedPhysics() {
        String json = defaultJson();
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replaceFirst(
                "\"massKg\": 2200000\\.0", "\"massKg\": -1.0")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"coverageFraction\": 1.0", "\"coverageFraction\": 1.5")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"maxImpactVelocityMps\": 20000.0", "\"maxImpactVelocityMps\": 500.0")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"lifeSupportCapacity\": 240", "\"lifeSupportCapacity\": 100")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"maxOperationalMassKg\": 26000000.0", "\"maxOperationalMassKg\": 10000000.0")));
    }

    @Test
    void rejectsIncompatibleFitInsteadOfApplyingClassBonusOrIgnoringEnvelope() {
        String json = defaultJson();
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"compatibleHardpointSizes\": [\"LARGE\"]",
                "\"compatibleHardpointSizes\": []")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"mountId\": \"weapon_spinal\"",
                "\"mountId\": \"mount_missing\"")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"integrationCategories\": [\"UTILITY\"],\n      \"compatibleHardpointSizes\": [],\n      \"physicalDimensionsM\": {\"lengthM\": 14.0",
                "\"integrationCategories\": [\"MISSION\"],\n      \"compatibleHardpointSizes\": [],\n      \"physicalDimensionsM\": {\"lengthM\": 14.0")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"physicalDimensionsM\": {\"lengthM\": 68.0, \"widthM\": 10.0, \"heightM\": 10.0}",
                "\"physicalDimensionsM\": {\"lengthM\": 100.0, \"widthM\": 10.0, \"heightM\": 10.0}")));
    }

    @Test
    void rejectsDuplicateMountUseAndHiddenPerformanceBonusFields() {
        String json = defaultJson();
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "{\"mountId\": \"core_drive\", \"moduleId\": \"module.main_drive_escort_v1\"}",
                "{\"mountId\": \"core_reactor\", \"moduleId\": \"module.main_drive_escort_v1\"}")));
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(json.replace(
                "\"family\": \"REACTOR_POWER\"",
                "\"classBonus\": 1.25,\n      \"family\": \"REACTOR_POWER\"")));
    }

    @Test
    void rejectsUnboundedCollections() {
        String json = defaultJson();
        List<String> tags = new ArrayList<>();
        for (int index = 0; index < 129; index++) {
            tags.add("tag_" + index);
        }
        String oversizedTags = "\"tags\": [\"" + String.join("\",\"", tags) + "\"]";
        String changed = json.replace("\"tags\": [\"structural\", \"metal\"]", oversizedTags);
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(changed));
    }

    @Test
    void responseSurfaceCannotExistWithoutExplicitCalibrationDomain() {
        String json = defaultJson();
        int start = json.indexOf("\"calibrationDomain\": {");
        int end = json.indexOf("      }", start) + "      }".length();
        if (start < 0 || end <= start) {
            fail("fixture calibration domain not found");
        }
        String changed = json.substring(0, start) + "\"calibrationDomain\": null" + json.substring(end);
        assertThrows(IllegalArgumentException.class, () -> ShipEngineeringCatalogLoader.parse(changed));
    }

    private static String defaultJson() {
        try (InputStream stream = ShipEngineeringCatalogLoaderTest.class.getClassLoader()
                .getResourceAsStream(ShipEngineeringCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
