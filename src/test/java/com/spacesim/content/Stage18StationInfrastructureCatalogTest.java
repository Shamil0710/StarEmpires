package com.spacesim.content;

import com.spacesim.economy.Stage18StationIndustrialNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18StationInfrastructureCatalogTest {
    @Test
    void productionCatalogDefinesEightComposableRolesWithoutHiddenLogisticsHubProduction() {
        Stage18StationInfrastructureCatalog catalog = Stage18StationInfrastructureCatalogLoader.loadDefault();

        assertEquals(8, catalog.getArchetypes().size());
        assertEquals(64, catalog.getFingerprint().length());
        var logistics = catalog.findArchetype("station.infrastructure.trade_logistics_hub");
        var industrial = catalog.findArchetype("station.infrastructure.industrial_station");
        assertNotNull(logistics);
        assertNotNull(industrial);
        assertTrue(logistics.installedFacilityDefinitionIds().isEmpty());
        assertTrue(industrial.installedFacilityDefinitionIds().contains("facility.processing.bulk_refinery"));
        assertTrue(industrial.installedFacilityDefinitionIds().contains("facility.fabrication.heavy"));
        assertTrue(industrial.installedFacilityDefinitionIds().contains("facility.fabrication.electrical"));
        assertTrue(industrial.installedFacilityDefinitionIds().contains("facility.fabrication.assembly"));
    }

    @Test
    void instantiatedNodeMaterializesExplicitFacilitiesStorageAndHandlingOnly() {
        Stage18StationInfrastructureCatalog catalog = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        var archetype = catalog.findArchetype("station.infrastructure.industrial_station");

        Stage18StationIndustrialNode node = Stage18StationIndustrialNode.instantiate(
                "station.test.industrial",
                "location.orbital_station",
                archetype,
                ontology,
                products);

        assertEquals(archetype.installedFacilityDefinitionIds().size(), node.installedFacilities().size());
        assertEquals(80_000_000d, node.storage().snapshotCapacityByStorageClassKg().get("storage.dry_bulk"));
        assertEquals(800_000d, node.handlingCapability().massRateKgPerSecond());
        assertTrue(node.storage().snapshotCommodityMassByIdKg().isEmpty());
        assertTrue(node.storage().snapshotProductCountById().isEmpty());
    }

    @Test
    void archetypeCannotBeInstantiatedAtPhysicallyIncompatibleLocation() {
        Stage18StationInfrastructureCatalog catalog = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        var archetype = catalog.findArchetype("station.infrastructure.refinery_complex");

        assertThrows(IllegalArgumentException.class, () -> Stage18StationIndustrialNode.instantiate(
                "station.test.bad",
                "location.free_body",
                archetype,
                ontology,
                products));
    }

    @Test
    void parserRejectsUnknownFacilityAndUnknownStorageClass() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        String unknownFacility = json("[\"facility.nonexistent\"]", "{\"storage.dry_bulk\":1.0}");
        String unknownStorage = json("[]", "{\"storage.nonexistent\":1.0}");

        assertThrows(IllegalArgumentException.class,
                () -> Stage18StationInfrastructureCatalogLoader.parse(unknownFacility, ontology, facilities));
        assertThrows(IllegalArgumentException.class,
                () -> Stage18StationInfrastructureCatalogLoader.parse(unknownStorage, ontology, facilities));
    }

    private static String json(String facilities, String capacities) {
        return """
                {
                  "schemaVersion":1,
                  "archetypes":[{
                    "id":"station.infrastructure.test",
                    "displayName":"test",
                    "installedFacilityDefinitionIds":%s,
                    "storageCapacityByClassKg":%s,
                    "transferStorageClassIds":["storage.dry_bulk"],
                    "transferMassRateKgPerSecond":1.0,
                    "maxTransferUnitMassKg":1.0,
                    "allowedLocationTags":["location.orbital_station"]
                  }]
                }
                """.formatted(facilities, capacities);
    }
}
