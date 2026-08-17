package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ShipyardCatalogTest {
    @Test
    void productionCatalogClosesBareHullMassAndCoversCurrentShipyardVocabulary() {
        Stage18ShipyardCatalog catalog = Stage18ShipyardCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();

        assertEquals(1, catalog.getYards().size());
        assertEquals(1, catalog.getHullProfiles().size());
        assertEquals(5, catalog.getModuleProfiles().size());
        assertEquals(64, catalog.getFingerprint().length());

        var hull = catalog.findHullProfile("hull.escort_destroyer_v1");
        assertNotNull(hull);
        assertEquals(12_000_000d,
                hull.buildInputsKg().stream().mapToDouble(Stage18ShipyardCatalog.PhysicalInputDefinition::massKg).sum(),
                1e-6d);
        assertEquals(3, hull.compartmentRepairs().size());
        hull.buildInputsKg().forEach(input -> {
            var commodity = ontology.findCommodity(input.commodityId());
            assertNotNull(commodity);
            assertTrue(commodity.kind() != Stage18ResourceOntologyCatalog.CommodityKind.EXTRACTED_FEEDSTOCK);
        });
    }

    @Test
    void yardIsExplicitPhysicalInfrastructureRatherThanStationClassBonus() {
        Stage18ShipyardCatalog catalog = Stage18ShipyardCatalogLoader.loadDefault();
        var yard = catalog.findYard("yard.orbital_escort_v1");

        assertNotNull(yard);
        assertTrue(yard.requiredSupportFacilityDefinitionIds().contains("facility.fabrication.heavy"));
        assertTrue(yard.requiredSupportFacilityDefinitionIds().contains("facility.fabrication.assembly"));
        assertEquals(300d, yard.berthDimensionsM().lengthM(), 0d);
        assertEquals(30_000_000d, yard.maxServiceMassKg(), 0d);
        assertEquals(1_200_000_000d, yard.ratedIntegrationPowerW(), 0d);
        assertTrue(yard.handledStorageClassIds().contains("storage.oversized"));
    }

    @Test
    void moduleServiceProfilesUseBoundedPhysicalReplacementMass() {
        Stage18ShipyardCatalog catalog = Stage18ShipyardCatalogLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();

        for (var profile : catalog.getModuleProfiles()) {
            var product = products.findProduct(profile.moduleId());
            assertNotNull(product);
            double repairMass = profile.repairInputsAtFullLossKg().stream()
                    .mapToDouble(Stage18ShipyardCatalog.PhysicalInputDefinition::massKg)
                    .sum();
            double maintenanceMass = profile.maintenanceInputsKg().stream()
                    .mapToDouble(Stage18ShipyardCatalog.PhysicalInputDefinition::massKg)
                    .sum();
            assertTrue(repairMass <= product.unitMassKg());
            assertTrue(maintenanceMass < repairMass);
        }
    }
}
