package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

public class ConsumptionSystem extends IteratingSystem {
    private final GlobalEventManager eventManager;

    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    public ConsumptionSystem(GlobalEventManager eventManager) {
        super(Family.all(InventoryComponent.class, MarketComponent.class, TransformComponent.class).get());
        this.eventManager = eventManager;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        InventoryComponent inv = im.get(entity);
        MarketComponent market = mm.get(entity);
        TransformComponent transform = tm.get(entity);

        for (int i = 0; i < Constants.MAX_ITEMS; i++) {
            if (inv.stock[i] > 0 && market.baseConsumption[i] > 0) {
                float consume = market.baseConsumption[i] * getConsumptionMultiplier(i, transform) * deltaTime;
                inv.stock[i] = Math.max(0, (int)(inv.stock[i] - consume));
                market.isDirty = true;
            }
        }
    }

    private float getConsumptionMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.activeEvents) {
            if (event.targetItemId == itemId && transform.position.dst(event.location) < event.radius) {
                multiplier *= event.consumptionMultiplier;
            }
        }
        return multiplier;
    }
}
