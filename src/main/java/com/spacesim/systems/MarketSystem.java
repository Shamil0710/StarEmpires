package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;
import com.spacesim.events.EconomyEvent;
import com.spacesim.events.GlobalEventManager;

public class MarketSystem extends EntitySystem {
    private ImmutableArray<Entity> entities;
    private final GlobalEventManager eventManager;

    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    public MarketSystem(GlobalEventManager em) { this.eventManager = em; }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(MarketComponent.class, InventoryComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        for (Entity entity : entities) {
            MarketComponent m = mm.get(entity);
            InventoryComponent inv = im.get(entity);
            TransformComponent pos = tm.get(entity);

            // Проверка событий
            float priceMult = 1.0f;
            for(EconomyEvent e : eventManager.activeEvents) {
                if(pos.position.dst(e.location) < e.radius) priceMult *= e.priceMultiplier;
            }

            if (m.isDirty) {
                for (int i = 0; i < Constants.MAX_ITEMS; i++) {
                    float ratio = (float)m.targetStock[i] / Math.max(1, inv.stock[i]);
                    float base = Constants.BASE_PRICES[i];

                    // Формула цены с множителем событий
                    m.sellPrices[i] = base * (float)Math.pow(ratio, 1.2) * priceMult;
                    m.buyPrices[i] = m.sellPrices[i] * 0.9f;
                }
                m.isDirty = false;
            }
        }
    }
}