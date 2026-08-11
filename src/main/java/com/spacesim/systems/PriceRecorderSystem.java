package com.spacesim.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.systems.IteratingSystem;
import com.spacesim.components.*;
import com.spacesim.constants.Constants;

public class PriceRecorderSystem extends IteratingSystem {
    private static final float RECORD_INTERVAL_SECONDS = 1.0f;

    private float timer = 0;
    private boolean shouldRecordThisFrame = false;

    private final ComponentMapper<MarketComponent> mm = ComponentMapper.getFor(MarketComponent.class);
    private final ComponentMapper<PriceHistoryComponent> hm = ComponentMapper.getFor(PriceHistoryComponent.class);

    public PriceRecorderSystem() {
        super(Family.all(MarketComponent.class, PriceHistoryComponent.class).get());
    }

    @Override
    public void update(float deltaTime) {
        if (!Float.isFinite(deltaTime) || deltaTime <= 0f) {
            shouldRecordThisFrame = false;
            return;
        }

        timer += deltaTime;
        shouldRecordThisFrame = timer >= RECORD_INTERVAL_SECONDS;

        if (shouldRecordThisFrame) {
            timer %= RECORD_INTERVAL_SECONDS;
        }

        super.update(deltaTime);
        shouldRecordThisFrame = false;
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        if (!shouldRecordThisFrame) {
            return;
        }

        MarketComponent m = mm.get(entity);
        PriceHistoryComponent h = hm.get(entity);

        for(int i=0; i<Constants.MAX_ITEMS; i++) {
            h.history[i].add(m.sellPrices[i]);
            if(h.history[i].size > h.maxPoints) h.history[i].removeIndex(0);
        }
    }
}
