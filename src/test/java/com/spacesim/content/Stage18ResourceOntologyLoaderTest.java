package com.spacesim.content;

import com.spacesim.model.ItemType;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18ResourceOntologyLoaderTest {
    @Test
    void defaultOntologyDefinesCanonicalFamiliesWithoutMutatingLegacyCatalog() {
        ContentCatalog legacy = ContentCatalogLoader.loadDefault();
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();

        assertEquals(1, ontology.getSchemaVersion());
        assertEquals(5, legacy.getItems().size(), "Stage 18A must not silently rewrite legacy inventory content");
        assertEquals(23, ontology.getCommodities().size());
        assertEquals(9, ontology.getOccurrenceTypes().size());

        Set<String> codeNames = ontology.getCommodities().stream()
                .map(Stage18ResourceOntologyCatalog.CommodityDefinition::codeName)
                .collect(Collectors.toSet());
        assertTrue(codeNames.containsAll(Set.of(
                "WATER_ICE", "VOLATILE_FEEDSTOCK", "CARBONACEOUS_FEEDSTOCK",
                "METALLIC_ORE", "LIGHT_METAL_MINERALS", "CONDUCTOR_ORE",
                "STRATEGIC_METAL_ORE", "SILICATE_MINERALS", "FISSILE_MINERALS",
                "PURIFIED_WATER", "INDUSTRIAL_GASES", "INDUSTRIAL_CHEMICALS",
                "STRUCTURAL_ALLOY", "LIGHT_ALLOY", "CONDUCTOR_METAL",
                "REFRACTORY_ALLOY", "CERAMIC_GLASS", "CARBON_MATERIAL",
                "ELECTRONIC_GRADE_MATERIAL", "REACTOR_FUEL",
                "HEAVY_COMPONENTS", "ELECTRICAL_COMPONENTS", "PRECISION_COMPONENTS")));

        for (ItemType legacyType : ItemType.values()) {
            ContentCatalog.ItemDefinition item = legacy.findItem(legacyType.getId());
            assertNotNull(item);
            assertNotNull(ontology.findLegacyMapping(item.id()), "Every legacy runtime item needs an explicit disposition");
        }

        assertEquals(
                "commodity.feedstock.metallic_ore",
                ontology.findLegacyMapping("item.ore").successorCommodityId());
        assertNull(ontology.findLegacyMapping("item.energy").successorCommodityId());
        assertNull(ontology.findLegacyMapping("item.weapons").successorCommodityId());
    }

    @Test
    void repeatedLoadsHaveIdenticalSemanticFingerprint() {
        Stage18ResourceOntologyCatalog first = Stage18ResourceOntologyLoader.loadDefault();
        Stage18ResourceOntologyCatalog second = Stage18ResourceOntologyLoader.loadDefault();

        assertEquals(first.getFingerprint(), second.getFingerprint());
        assertEquals(64, first.getFingerprint().length());
    }

    @Test
    void parserRejectsCommodityWithUnknownStorageClass() {
        String json = minimalOntology(
                "{\"id\":\"commodity.feedstock.test\",\"codeName\":\"TEST\",\"displayName\":\"Test\","
                        + "\"kind\":\"EXTRACTED_FEEDSTOCK\",\"storageClassId\":\"storage.missing\",\"quantityUnit\":\"KILOGRAM\"}",
                "{\"id\":\"occurrence.test\",\"displayName\":\"Test\",\"feedstockCommodityIds\":[\"commodity.feedstock.test\"]}",
                "[]");

        assertThrows(IllegalArgumentException.class, () -> Stage18ResourceOntologyLoader.parse(json));
    }

    @Test
    void parserRejectsOccurrenceThatReferencesRefinedMaterialAsFeedstock() {
        String json = minimalOntology(
                "{\"id\":\"commodity.material.test\",\"codeName\":\"TEST\",\"displayName\":\"Test\","
                        + "\"kind\":\"ENGINEERING_MATERIAL\",\"storageClassId\":\"storage.test\",\"quantityUnit\":\"KILOGRAM\"}",
                "{\"id\":\"occurrence.test\",\"displayName\":\"Test\",\"feedstockCommodityIds\":[\"commodity.material.test\"]}",
                "[]");

        assertThrows(IllegalArgumentException.class, () -> Stage18ResourceOntologyLoader.parse(json));
    }

    @Test
    void parserRejectsLegacySuccessorThatDoesNotExist() {
        String commodity = "{\"id\":\"commodity.feedstock.test\",\"codeName\":\"TEST\",\"displayName\":\"Test\","
                + "\"kind\":\"EXTRACTED_FEEDSTOCK\",\"storageClassId\":\"storage.test\",\"quantityUnit\":\"KILOGRAM\"}";
        String occurrence = "{\"id\":\"occurrence.test\",\"displayName\":\"Test\",\"feedstockCommodityIds\":[\"commodity.feedstock.test\"]}";
        String mappings = "[{\"legacyItemContentId\":\"item.ore\",\"disposition\":\"SEMANTIC_SUCCESSOR\","
                + "\"successorCommodityId\":\"commodity.feedstock.missing\",\"migrationNote\":\"test\"}]";

        assertThrows(
                IllegalArgumentException.class,
                () -> Stage18ResourceOntologyLoader.parse(minimalOntology(commodity, occurrence, mappings)));
    }

    private static String minimalOntology(String commodity, String occurrence, String legacyMappings) {
        return """
                {
                  "schemaVersion": 1,
                  "storageClasses": [
                    {"id":"storage.test","displayName":"Test storage","legacyCategory":"MATERIAL"}
                  ],
                  "capabilityTags": [
                    {"id":"capability.test","displayName":"Test capability"}
                  ],
                  "commodities": [%s],
                  "occurrenceTypes": [%s],
                  "legacyMappings": %s
                }
                """.formatted(commodity, occurrence, legacyMappings);
    }
}
