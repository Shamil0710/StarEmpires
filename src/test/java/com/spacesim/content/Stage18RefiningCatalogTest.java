package com.spacesim.content;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18RefiningCatalogTest {
    @Test
    void productionCatalogCoversEveryStage18cMaterialAndConsumableOutput() {
        Stage18RefiningCatalog catalog = Stage18RefiningCatalogLoader.loadDefault();

        assertEquals(1, catalog.getSchemaVersion());
        assertEquals(11, catalog.getRecipes().size());
        assertNotNull(catalog.findRecipe("refining.structural_alloy"));
        assertNotNull(catalog.findRecipe("refining.reactor_fuel"));
        assertEquals(64, catalog.getFingerprint().length());

        Set<String> outputs = new HashSet<>();
        catalog.getRecipes().forEach(recipe -> {
            assertTrue(outputs.add(recipe.outputCommodityId()));
            assertEquals(1d, recipe.inputs().stream()
                    .mapToDouble(Stage18RefiningCatalog.RecipeInputDefinition::fractionOfInputMass)
                    .sum(), 1e-9d);
            assertEquals(1d, recipe.outputMassFraction() + recipe.discardedMassFraction(), 1e-9d);
        });
        assertEquals(11, outputs.size());
    }

    @Test
    void parserRejectsComponentOutputAndBrokenMassClosure() {
        Stage18ResourceOntologyCatalog ontology = Stage18ResourceOntologyLoader.loadDefault();
        String componentOutput = """
                {"schemaVersion":1,"recipes":[{
                  "id":"refining.invalid_component","displayName":"Invalid",
                  "inputs":[{"commodityId":"commodity.feedstock.metallic_ore","fractionOfInputMass":1.0}],
                  "outputCommodityId":"commodity.component.heavy_components",
                  "outputMassFraction":0.7,"discardedMassFraction":0.3,
                  "requiredCapabilityTags":["capability.process.bulk_refining"],
                  "energyJPerInputKg":1.0,"workSecondsPerInputKg":1.0,
                  "maintenanceWorkSecondsPerInputKg":1.0}]}
                """;
        assertThrows(IllegalArgumentException.class,
                () -> Stage18RefiningCatalogLoader.parse(componentOutput, ontology));

        String brokenClosure = """
                {"schemaVersion":1,"recipes":[{
                  "id":"refining.invalid_closure","displayName":"Invalid",
                  "inputs":[{"commodityId":"commodity.feedstock.metallic_ore","fractionOfInputMass":1.0}],
                  "outputCommodityId":"commodity.material.structural_alloy",
                  "outputMassFraction":0.7,"discardedMassFraction":0.4,
                  "requiredCapabilityTags":["capability.process.bulk_refining"],
                  "energyJPerInputKg":1.0,"workSecondsPerInputKg":1.0,
                  "maintenanceWorkSecondsPerInputKg":1.0}]}
                """;
        assertThrows(IllegalArgumentException.class,
                () -> Stage18RefiningCatalogLoader.parse(brokenClosure, ontology));
    }
}
