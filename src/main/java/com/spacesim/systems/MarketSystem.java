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
    private long lastEventRevision = Long.MIN_VALUE;

    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<InventoryComponent> im = ComponentMapper.getFor(InventoryComponent.class);
    private ComponentMapper<TransformComponent> tm = ComponentMapper.getFor(TransformComponent.class);

    public MarketSystem(GlobalEventManager em) { this.eventManager = em; }

    @Override
    public void addedToEngine(Engine engine) {
        entities = engine.getEntitiesFor(Family.all(MarketComponent.class, InventoryComponent.class, TransformComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        long eventRevision = eventManager.getEventRevision();
        boolean eventsChanged = eventRevision != lastEventRevision;

        for (Entity entity : entities) {
            MarketComponent m = mm.get(entity);
            InventoryComponent inv = im.get(entity);
            TransformComponent pos = tm.get(entity);

            if (m.isDirty || eventsChanged) {
                for (int i = 0; i < Constants.MAX_ITEMS; i++) {
                    if (!m.isTradable(i)) {
                        m.sellPrices[i] = 0f;
                        m.buyPrices[i] = 0f;
                        continue;
                    }

                    float ratio = (float)m.targetStock[i] / Math.max(1, inv.stock[i]);
                    float base = Constants.BASE_PRICES[i];
                    float priceMultiplier = getPriceMultiplier(i, pos);

                    m.sellPrices[i] = base * (float)Math.pow(ratio, 1.2) * priceMultiplier;
                    m.buyPrices[i] = m.sellPrices[i] * 0.9f;
                }
                m.isDirty = false;
            }
        }

        lastEventRevision = eventRevision;
    }

    private float getPriceMultiplier(int itemId, TransformComponent transform) {
        float multiplier = 1.0f;
        for (EconomyEvent event : eventManager.getActiveEvents()) {
            if (event.affects(itemId, transform.position)) {
                multiplier *= event.getPriceMultiplier();
            }
        }
        return multiplier;
    }
}
