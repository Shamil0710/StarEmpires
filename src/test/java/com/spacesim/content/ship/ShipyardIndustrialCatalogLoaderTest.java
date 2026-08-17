package com.spacesim.content.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ShipyardIndustrialCatalogLoaderTest {
    @Test
    void defaultCatalogResolvesEngineeringHullModulesAndCompartments() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog catalog = ShipyardIndustrialCatalogLoader.loadDefault(engineering);

        assertEquals(1, catalog.getSchemaVersion());
        ShipyardIndustrialCatalog.HullIndustrialProfile hull =
                catalog.findHullProfile("hull.escort_destroyer_v1");
        assertNotNull(hull);
        assertEquals(engineering.findHull("hull.escort_destroyer_v1").compartments().size(),
                hull.compartmentRepairs().size());
        for (ShipEngineeringCatalog.ModuleDefinition module : engineering.getModules()) {
            assertNotNull(catalog.findModuleProfile(module.id()), module.id());
        }
    }

    @Test
    void loaderRejectsUnknownHullAndIncompleteCompartmentRepairTopology() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        String unknownHull = """
                {
                  "schemaVersion": 1,
                  "hullProfiles": [{
                    "hullId": "hull.missing",
                    "constructionInputs": [{"contentId":"component.heavy","amount":1}],
                    "fabricationCapabilities": ["structure"],
                    "toolingTags": ["fixture"],
                    "precisionRequirement": 0.1,
                    "industrialPowerW": 1,
                    "laborRequirement": 1,
                    "automationRequirement": 1,
                    "assemblyWorkSeconds": 1,
                    "compartmentRepairs": []
                  }],
                  "moduleProfiles": []
                }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> ShipyardIndustrialCatalogLoader.parse(unknownHull, engineering));

        String incomplete = """
                {
                  "schemaVersion": 1,
                  "hullProfiles": [{
                    "hullId": "hull.escort_destroyer_v1",
                    "constructionInputs": [{"contentId":"component.heavy","amount":1}],
                    "fabricationCapabilities": ["structure"],
                    "toolingTags": ["fixture"],
                    "precisionRequirement": 0.1,
                    "industrialPowerW": 1,
                    "laborRequirement": 1,
                    "automationRequirement": 1,
                    "assemblyWorkSeconds": 1,
                    "compartmentRepairs": [{
                      "compartmentId":"engineering",
                      "repairInputsAtFullLoss":[{"contentId":"component.heavy","amount":1}],
                      "repairWorkSecondsAtFullLoss":1
                    }]
                  }],
                  "moduleProfiles": [{
                    "moduleId":"module.reactor_5gw_v1",
                    "fabricationCapabilities":["power"],
                    "toolingTags":["reactor"],
                    "precisionRequirement":0.1,
                    "industrialPowerW":1,
                    "laborRequirement":1,
                    "automationRequirement":1,
                    "manufacturingWorkSeconds":1,
                    "installationWorkSeconds":1,
                    "removalWorkSeconds":1
                  }]
                }
                """;
        assertThrows(IllegalArgumentException.class,
                () -> ShipyardIndustrialCatalogLoader.parse(incomplete, engineering));
    }
}
