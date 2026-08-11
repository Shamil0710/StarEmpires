package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

public class ConsumptionSystem extends IteratingSystem {
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);

    public ConsumptionSystem() {
        super(Family.all(InventoryComponent.class, MarketComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InventoryComponent inv = im.get(entity);
        MarketComponent market = mm.get(entity);

        for (int i = 0; i < Constants.MAX_ITEMS; i++) {
            if (inv.stock[i] > 0 && market.baseConsumption[i] > 0) {
                float consume = market.baseConsumption[i] * deltaTime;
                inv.stock[i] = Math.max(0, (int)(inv.stock[i] - consume));
                market.isDirty = true;
            }
        }
    }
}
