package com.spacesim.events;

import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import java.util.ArrayList;
import java.util.List;

public class GlobalEventManager {
    public List<EconomyEvent> activeEvents = new ArrayList<>();

    public void update(float delta) {
        activeEvents.removeIf(e -> {
            e.duration -= delta;
            return e.duration <= 0;
        });

        if (MathUtils.randomBoolean(0.001f)) { // Шанс события каждый кадр
            EconomyEvent e = new EconomyEvent();
            e.name = "CRISIS";
            e.targetItemId = 2; // Food
            e.priceMultiplier = 3.0f;
            e.consumptionMultiplier = 2.0f;
            e.duration = 30f;
            e.location = new Vector2(100, 100);
            e.radius = 500;
            activeEvents.add(e);
        }
    }
}