package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingProductRegistry.ProductKind;
import com.spacesim.content.Stage18ManufacturingProductRegistry.Provenance;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ManufacturingCatalogTest {
    @Test
    void productionCatalogCoversAllComponentsProfilesAndExistingFinishedProducts() {
        Stage18ManufacturingCatalog catalog = Stage18ManufacturingCatalogLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();

        assertEquals(3, catalog.getComponentRecipes().size());
        assertEquals(13, catalog.getProductProfiles().size());
        assertEquals(29, catalog.getProductBindings().size());
        assertEquals(29, products.getProducts().size());
        assertEquals(64, catalog.getFingerprint().length());

        Set<String> bound = new HashSet<>();
        catalog.getProductBindings().forEach(binding -> {
            assertNotNull(products.findProduct(binding.productContentId()));
            assertNotNull(catalog.findProductProfile(binding.profileId()));
            assertTrue(bound.add(binding.productContentId()));
        });
        assertEquals(products.getProducts().stream().map(
                Stage18ManufacturingProductRegistry.ProductDefinition::contentId).collect(java.util.stream.Collectors.toSet()), bound);
    }

    @Test
    void productRegistryPreservesPhysicalMassKindAndProvisionalProvenance() {
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();

        var reactor = products.findProduct("module.reactor_5gw_v1");
        assertEquals(ProductKind.MODULE, reactor.kind());
        assertEquals(2_200_000d, reactor.unitMassKg());
        assertEquals(Provenance.STAGE17_5_PRODUCTION_SCHEMA_DEMONSTRATOR, reactor.provenance());

        var interceptor = products.findProduct("ammo.interceptor_1t_v1");
        assertEquals(ProductKind.AMMUNITION, interceptor.kind());
        assertEquals(1_000d, interceptor.unitMassKg());

        var provisional = products.findProduct("ammo.test_anti_ship_missile_2t_v1");
        assertEquals(2_000d, provisional.unitMassKg());
        assertEquals(Provenance.STAGE17_5I_CONTENT_PROVISIONAL, provisional.provenance());
    }

    @Test
    void ontologyExposesAssemblyAndOrdnanceAsCapabilitiesNotStationClassBonuses() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();

        assertNotNull(ontology.findCapabilityTag("capability.fabrication.assembly"));
        assertNotNull(ontology.findCapabilityTag("capability.fabrication.ordnance"));
    }

    @Test
    void componentRecipesCannotConsumeRawFeedstockDirectly() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        String malformed = """
                {
                  "schemaVersion":1,
                  "componentRecipes":[{
                    "id":"manufacturing.component.bad",
                    "displayName":"bad",
                    "inputs":[{"commodityId":"commodity.feedstock.metallic_ore","fractionOfOutputMass":1.0}],
                    "outputCommodityId":"commodity.component.heavy_components",
                    "requiredCapabilityTags":["capability.fabrication.heavy"],
                    "energyJPerOutputKg":1.0,
                    "workSecondsPerOutputKg":1.0,
                    "maintenanceWorkSecondsPerOutputKg":1.0
                  }],
                  "productProfiles":[],
                  "productBindings":[]
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> Stage18ManufacturingCatalogLoader.parse(malformed, ontology, products));
    }

    @Test
    void manufacturingInputMassFractionsMustCloseExactly() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ManufacturingProductRegistry products = Stage18ManufacturingProductRegistry.loadDefault();
        String malformed = """
                {
                  "schemaVersion":1,
                  "componentRecipes":[{
                    "id":"manufacturing.component.bad",
                    "displayName":"bad",
                    "inputs":[
                      {"commodityId":"commodity.material.structural_alloy","fractionOfOutputMass":0.5},
                      {"commodityId":"commodity.material.light_alloy","fractionOfOutputMass":0.4}
                    ],
                    "outputCommodityId":"commodity.component.heavy_components",
                    "requiredCapabilityTags":["capability.fabrication.heavy"],
                    "energyJPerOutputKg":1.0,
                    "workSecondsPerOutputKg":1.0,
                    "maintenanceWorkSecondsPerOutputKg":1.0
                  }],
                  "productProfiles":[],
                  "productBindings":[]
                }
                """;

        assertThrows(IllegalArgumentException.class,
                () -> Stage18ManufacturingCatalogLoader.parse(malformed, ontology, products));
    }
}
