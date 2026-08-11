package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.model.Recipe;

public class ProductionSystem extends IteratingSystem {
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<ProductionComponent> pm = ComponentMapper.getFor(ProductionComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);

    public ProductionSystem() {
        super(Family.all(InventoryComponent.class, ProductionComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InventoryComponent inventory = im.get(entity);
        ProductionComponent production = pm.get(entity);

        if (production.recipes.isEmpty()) {
            return;
        }

        Recipe recipe = production.recipes.get(production.activeRecipeIndex % production.recipes.size());
        if (!canProduce(inventory, recipe)) {
            production.progressSeconds = 0f;
            return;
        }

        production.progressSeconds += deltaTime;
        if (production.progressSeconds < recipe.durationSeconds) {
            return;
        }

        production.progressSeconds -= recipe.durationSeconds;
        consumeInputs(inventory, recipe);
        addOutputs(inventory, recipe);

        if (mm.has(entity)) {
            mm.get(entity).isDirty = true;
        }
    }

    private boolean canProduce(InventoryComponent inventory, Recipe recipe) {
        int totalStock = 0;
        int totalInputs = 0;
        int totalOutputs = 0;

        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            if (inventory.stock[itemId] < recipe.inputItems[itemId]) {
                return false;
            }

            totalStock += inventory.stock[itemId];
            totalInputs += recipe.inputItems[itemId];
            totalOutputs += recipe.outputItems[itemId];
        }

        return totalStock - totalInputs + totalOutputs <= inventory.capacity;
    }

    private void consumeInputs(InventoryComponent inventory, Recipe recipe) {
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            inventory.stock[itemId] -= recipe.inputItems[itemId];
        }
    }

    private void addOutputs(InventoryComponent inventory, Recipe recipe) {
        for (int itemId = 0; itemId < Constants.MAX_ITEMS; itemId++) {
            inventory.stock[itemId] += recipe.outputItems[itemId];
        }
    }
}
