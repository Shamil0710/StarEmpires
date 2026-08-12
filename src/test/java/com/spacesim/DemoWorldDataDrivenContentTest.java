package com.spacesim;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class DemoWorldDataDrivenContentTest {
    @Test
    void demoWorldМатериализуетStationИShipПараметрыИзProductionCatalog() {
        ContentCatalog catalog = ContentCatalogLoader.loadDefault();
        List<Entity> entities = DemoWorldFactory.createEntities();

        Entity powerPlant = byName(entities, "Энергоузел Корона");
        assertEquals("station.power_plant",
                powerPlant.getComponent(ArchetypeComponent.class).contentId);
        assertEquals(
                catalog.findStationArchetype("station.power_plant").inventoryCapacity(),
                powerPlant.getComponent(InventoryComponent.class).capacity);
        ProductionComponent production = powerPlant.getComponent(ProductionComponent.class);
        assertNotNull(production);
        assertEquals(
                catalog.findRecipe("recipe.energy_generation").displayName(),
                production.getActiveRecipe().name);

        Entity atlas = byName(entities, "Материаловоз Атлас");
        assertEquals("ship.ore_hauler", atlas.getComponent(ArchetypeComponent.class).contentId);
        assertEquals(
                catalog.findShipArchetype("ship.ore_hauler").cargoCapacity(),
                atlas.getComponent(InventoryComponent.class).capacity);
        assertEquals(
                catalog.findShipArchetype("ship.ore_hauler").movementSpeed(),
                atlas.getComponent(TradeAIComponent.class).movementSpeed,
                0f);
    }

    private Entity byName(List<Entity> entities, String name) {
        for (Entity entity : entities) {
            IdentityComponent identity = entity.getComponent(IdentityComponent.class);
            if (identity != null && name.equals(identity.name)) {
                return entity;
            }
        }
        throw new AssertionError("Не найдена сущность: " + name);
    }
}
