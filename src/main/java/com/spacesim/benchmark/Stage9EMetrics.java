package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.simulation.SimulationSession;

final class Stage9EMetrics {
    private static final String FOUNDRY = "station.foundry";

    private Stage9EMetrics() {
        throw new AssertionError("Utility class");
    }

    static long unmetDemand(SimulationSession session, int itemId) {
        long deficit = 0L;
        long surplus = 0L;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null || !market.isTradable(itemId)) {
                continue;
            }
            long delta = (long) market.targetStock[itemId] - inventory.stock[itemId];
            if (delta > 0L) {
                deficit = Math.addExact(deficit, delta);
            } else {
                surplus = Math.addExact(surplus, -delta);
            }
        }
        return Math.max(0L, deficit - surplus);
    }

    static int structuralPressureBasisPoints(SimulationSession session, int itemId) {
        int pressure = 10_000;
        for (Entity entity : session.getEngine().getEntities()) {
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (market == null || inventory == null || !market.isTradable(itemId)) {
                continue;
            }
            int target = Math.max(0, market.targetStock[itemId]);
            int stock = inventory.stock[itemId];
            if (stock >= target || target <= 0) {
                continue;
            }
            long value = (long) target * 10_000L / Math.max(1, stock);
            pressure = Math.max(pressure, value >= Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value);
        }
        return pressure;
    }

    static int countFoundries(SimulationSession session) {
        int count = 0;
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && FOUNDRY.equals(archetype.contentId)) {
                count++;
            }
        }
        return count;
    }

    static Entity requireFoundry(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (archetype != null && FOUNDRY.equals(archetype.contentId)) {
                return entity;
            }
        }
        throw new IllegalStateException("Stage 9E requires initial Corona foundry");
    }
}
