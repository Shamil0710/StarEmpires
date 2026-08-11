package com.spacesim.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProductionSystemTest {
    @Test
    void производствоСписываетВходыИДобавляетВыходыПослеЗавершенияЦикла() {
        Engine engine = new Engine();
        engine.addSystem(new ProductionSystem());

        Entity station = new Entity();
        InventoryComponent inventory = new InventoryComponent();
        inventory.stock[Constants.ITEM_ORE] = 4;
        inventory.stock[Constants.ITEM_ENERGY] = 2;
        station.add(inventory);

        ProductionComponent production = new ProductionComponent();
        production.recipes.add(new Recipe("Тестовая выплавка", 1.0f)
                .input(Constants.ITEM_ORE, 2)
                .input(Constants.ITEM_ENERGY, 1)
                .output(Constants.ITEM_STEEL, 1));
        station.add(production);

        engine.addEntity(station);
        engine.update(1.0f);

        assertEquals(2, inventory.stock[Constants.ITEM_ORE]);
        assertEquals(1, inventory.stock[Constants.ITEM_ENERGY]);
        assertEquals(1, inventory.stock[Constants.ITEM_STEEL]);
    }
}
