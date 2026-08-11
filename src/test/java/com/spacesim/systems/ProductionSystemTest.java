package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class ProductionSystemTest {
    @Test
    void productionConsumesInputsAndAddsOutputsAfterCycleCompletion() {
        ProductionFixture fixture = createFixture(4, 2);

        fixture.engine.update(1.0f);

        assertEquals(2, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(1, fixture.inventory.stock[Constants.ITEM_ENERGY]);
        assertEquals(1, fixture.inventory.stock[Constants.ITEM_STEEL]);
    }

    @Test
    void largeDeltaIsEquivalentToSeveralSmallUpdates() {
        ProductionFixture largeUpdate = createFixture(20, 10);
        ProductionFixture smallUpdates = createFixture(20, 10);

        largeUpdate.engine.update(5.5f);
        for (int update = 0; update < 11; update++) {
            smallUpdates.engine.update(0.5f);
        }

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            assertEquals(largeUpdate.inventory.stock[itemId], smallUpdates.inventory.stock[itemId]);
        }
        assertEquals(largeUpdate.production.progressSeconds, smallUpdates.production.progressSeconds, 0.0001f);
        assertEquals(10, largeUpdate.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(5, largeUpdate.inventory.stock[Constants.ITEM_ENERGY]);
        assertEquals(5, largeUpdate.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0.5f, largeUpdate.production.progressSeconds, 0.0001f);
    }

    @Test
    void resourceExhaustionStopsProductionAndDiscardsElapsedDebt() {
        ProductionFixture fixture = createFixture(4, 2);

        fixture.engine.update(10f);

        assertEquals(0, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_ENERGY]);
        assertEquals(2, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0f, fixture.production.progressSeconds);

        fixture.inventory.stock[Constants.ITEM_ORE] = 2;
        fixture.inventory.stock[Constants.ITEM_ENERGY] = 1;
        fixture.engine.update(0.5f);

        assertEquals(2, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0.5f, fixture.production.progressSeconds, 0.0001f);
    }

    @Test
    void insufficientResourcesResetExistingProgress() {
        ProductionFixture fixture = createFixture(1, 1);
        fixture.production.progressSeconds = 0.75f;

        fixture.engine.update(1f);

        assertEquals(0, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0f, fixture.production.progressSeconds);
    }

    @Test
    void capacityIsRecheckedAfterEveryCompletedCycle() {
        ProductionFixture fixture = createFixture(10, 0);
        fixture.inventory.capacity = 13;
        fixture.production.recipes.clear();
        fixture.production.recipes.add(new Recipe("Capacity limited production", 1f)
                .input(Constants.ITEM_ORE, 1)
                .output(Constants.ITEM_STEEL, 2));

        fixture.engine.update(10f);

        assertEquals(7, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(6, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0f, fixture.production.progressSeconds);
    }

    @Test
    void veryLargeFiniteDeltaCompletesQuicklyForNetZeroRecipe() {
        ProductionFixture fixture = createFixture(1, 0);
        fixture.production.recipes.clear();
        fixture.production.recipes.add(new Recipe("Catalytic cycle", 11f)
                .input(Constants.ITEM_ORE, 1)
                .output(Constants.ITEM_ORE, 1));

        assertTimeoutPreemptively(
                Duration.ofSeconds(1),
                () -> fixture.engine.update(Float.MAX_VALUE));

        assertEquals(1, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(9f, fixture.production.progressSeconds);
    }

    @Test
    void invalidDeltaDoesNotChangeProductionState() {
        ProductionFixture fixture = createFixture(4, 2);
        fixture.production.progressSeconds = 0.25f;

        fixture.engine.update(0f);
        fixture.engine.update(-1f);
        fixture.engine.update(Float.NaN);
        fixture.engine.update(Float.POSITIVE_INFINITY);

        assertEquals(4, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0.25f, fixture.production.progressSeconds);
    }

    @Test
    void invalidRecipeIndexMeansNoActiveRecipe() {
        ProductionFixture fixture = createFixture(4, 2);
        fixture.production.progressSeconds = 0.5f;
        fixture.production.activeRecipeIndex = -1;

        assertDoesNotThrow(() -> fixture.engine.update(5f));
        assertEquals(4, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_STEEL]);
        assertEquals(0f, fixture.production.progressSeconds);

        fixture.production.activeRecipeIndex = fixture.production.recipes.size();
        assertDoesNotThrow(() -> fixture.engine.update(5f));
        assertEquals(4, fixture.inventory.stock[Constants.ITEM_ORE]);
        assertEquals(0, fixture.inventory.stock[Constants.ITEM_STEEL]);
    }

    @Test
    void recipeRejectsInvalidConfiguration() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(null, 1f));
        assertThrows(IllegalArgumentException.class, () -> new Recipe("   ", 1f));
        assertThrows(IllegalArgumentException.class, () -> new Recipe("test", 0f));
        assertThrows(IllegalArgumentException.class, () -> new Recipe("test", -1f));
        assertThrows(IllegalArgumentException.class, () -> new Recipe("test", Float.NaN));
        assertThrows(IllegalArgumentException.class, () -> new Recipe("test", Float.POSITIVE_INFINITY));

        Recipe recipe = new Recipe("test", 1f);
        assertThrows(IllegalArgumentException.class, () -> recipe.input(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe.input(Constants.MAX_ITEMS, 1));
        assertThrows(IllegalArgumentException.class, () -> recipe.input(Constants.ITEM_ORE, 0));
        assertThrows(IllegalArgumentException.class, () -> recipe.output(Constants.ITEM_STEEL, -1));
        assertThrows(IllegalArgumentException.class, () -> recipe.getOutputAmount(Constants.MAX_ITEMS));
    }

    private ProductionFixture createFixture(int ore, int energy) {
        Engine engine = new Engine();
        engine.addSystem(new ProductionSystem());

        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_ORE] = ore;
        inventory.stock[Constants.ITEM_ENERGY] = energy;
        station.add(inventory);

        ProductionComponent production = new ProductionComponent();
        production.recipes.add(new Recipe("Test smelting", 1f)
                .input(Constants.ITEM_ORE, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_STEEL, 1));
        station.add(production);

        engine.addEntity(station);
        return new ProductionFixture(engine, inventory, production);
    }

    private record ProductionFixture(
            Engine engine,
            InventoryComponent inventory,
            ProductionComponent production) {
    }
}
