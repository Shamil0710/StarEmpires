package com.spacesim.systems;

import com.badlogic.ashley.systems.IteratingSystem;
import com.badlogic.ashley.core.*;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

public class PriceRecorderSystem extends IteratingSystem {
    private float timer = 0;
    private ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private ComponentMapper<PriceHistoryComponent> hm = ComponentMapper.getFor(PriceHistoryComponent.class);

    public PriceRecorderSystem() {
        super(Family.all(MarketComponent.class, PriceHistoryComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        timer += deltaTime;
        if(timer > 1.0f) { // Раз в секунду
            MarketComponent m = mm.get(entity);
            PriceHistoryComponent h = hm.get(entity);

            for(int i=0; i<Constants.MAX_ITEMS; i++) {
                h.history[i].add(m.sellPrices[i]);
                if(h.history[i].size > h.maxPoints) h.history[i].removeIndex(0);
            }
            timer = 0;
        }
    }
}