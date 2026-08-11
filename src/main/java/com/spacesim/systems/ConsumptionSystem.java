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
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            return;
        }

        InventoryComponent inv = im.get(entity);
        MarketComponent market = mm.get(entity);
        TransformComponent transform = tm.get(entity);

        for (int i = 0; i < Constants.MAX_ITEMS; i++) {
            if (inv.stock[i] <= 0) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            float baseConsumption = market.baseConsumption[i];
            if (!Float.isFinite(baseConsumption) || baseConsumption <= 0f) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            float multiplier = getConsumptionMultiplier(i, transform);
            if (!Float.isFinite(multiplier) || multiplier <= 0f) {
                continue;
            }

            double accumulatedConsumption = market.consumptionRemainder[i]
                    + (double) baseConsumption * multiplier * deltaTime;
            if (!Double.isFinite(accumulatedConsumption)) {
                market.consumptionRemainder[i] = 0d;
                continue;
            }

            double wholeConsumption = Math.floor(accumulatedConsumption);
            if (wholeConsumption < 1d) {
                market.consumptionRemainder[i] = accumulatedConsumption;
                continue;
            }

            int unitsToConsume = wholeConsumption >= Integer.MAX_VALUE
                    ? Integer.MAX_VALUE
                    : (int) wholeConsumption;
            int consumedUnits = Math.min(inv.stock[i], unitsToConsume);
            inv.stock[i] -= consumedUnits;
            market.consumptionRemainder[i] = accumulatedConsumption - wholeConsumption;

            if (consumedUnits > 0) {
                market.isDirty = true;
            }
        }
    }

    private float getConsumptionMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.activeEvents) {
            if (event != null
                    && event.location != null
                    && event.targetItemId == itemId
                    && transform.position.dst(event.location) < event.radius) {
                multiplier *= event.consumptionMultiplier;
            }
        }
        return multiplier;
    }
}
