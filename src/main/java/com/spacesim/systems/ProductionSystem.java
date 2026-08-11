package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;

public class ProductionSystem extends IteratingSystem {
    // В реальном коде здесь мапперы и рецепты
    public ProductionSystem() {
        super(Family.all(InventoryComponent.class).get());
    }
    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        // Логика производства: Руда -> Сталь
    }
}
