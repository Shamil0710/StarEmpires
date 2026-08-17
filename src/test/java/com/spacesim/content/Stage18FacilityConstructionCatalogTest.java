package com.spacesim.content;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18FacilityConstructionCatalogTest {
    @Test
    void productionCatalogCoversEveryStage18eFacilityAndClosesInstalledMass() {
        Stage18FacilityConstructionCatalog catalog = Stage18FacilityConstructionCatalogLoader.loadDefault();
        Stage18FacilityCatalog facilities = Stage18FacilityCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();

        assertEquals(facilities.getFacilities().size(), catalog.getFacilities().size());
        assertEquals(14, catalog.getFacilities().size());
        assertEquals(5, catalog.getProfiles().size());
        assertEquals(64, catalog.getFingerprint().length());

        for (Stage18FacilityConstructionCatalog.FacilityConstructionDefinition definition : catalog.getFacilities()) {
            assertNotNull(facilities.findFacility(definition.facilityDefinitionId()));
            double inputMass = catalog.requiredMassByCommodityKg(definition.facilityDefinitionId())
                    .values().stream().mapToDouble(Double::doubleValue).sum();
            assertEquals(definition.installedMassKg(), inputMass, 1e-6d);
            assertTrue(catalog.totalWorkSeconds(definition.facilityDefinitionId()) > 0d);
        }

        for (Stage18FacilityConstructionCatalog.ConstructionProfileDefinition profile : catalog.getProfiles()) {
            profile.inputs().forEach(input -> {
                Stage18ResourceOntologyCatalog.CommodityDefinition commodity = ontology.findCommodity(input.commodityId());
                assertNotNull(commodity);
                assertTrue(commodity.kind() != Stage18ResourceOntologyCatalog.CommodityKind.EXTRACTED_FEEDSTOCK);
            });
        }
    }

    @Test
    void recyclingProcessorHasOrdinaryPhysicalConstructionBill() {
        Stage18FacilityConstructionCatalog catalog = Stage18FacilityConstructionCatalogLoader.loadDefault();
        var definition = catalog.findFacility("facility.processing.recycling");

        assertNotNull(definition);
        assertEquals(18_000_000d, definition.installedMassKg(), 0d);
        assertEquals(18_000_000d,
                catalog.requiredMassByCommodityKg(definition.facilityDefinitionId())
                        .values().stream().mapToDouble(Double::doubleValue).sum(),
                1e-6d);
        assertTrue(catalog.requiredMassByCommodityKg(definition.facilityDefinitionId())
                .containsKey("commodity.component.heavy_components"));
    }
}
