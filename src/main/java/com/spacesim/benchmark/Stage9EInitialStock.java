package com.spacesim.benchmark;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;

final class Stage9EInitialStock {
    private static final String STEEL = "item.steel";
    private static final String ENERGY = "item.energy";
    private static final int STEEL_STOCK = 600;
    private static final int ENERGY_STOCK = 400;

    private Stage9EInitialStock() {
        throw new AssertionError("Utility class");
    }

    static void apply(Entity producer, ContentCatalog content) {
        InventoryComponent inventory = producer.getComponent(InventoryComponent.class);
        if (inventory == null) {
            throw new IllegalStateException("Stage 9E producer requires inventory");
        }
        int steelId = content.findItem(STEEL).runtimeId();
        int energyId = content.findItem(ENERGY).runtimeId();
        int incoming = Math.max(0, STEEL_STOCK - inventory.stock[steelId])
                + Math.max(0, ENERGY_STOCK - inventory.stock[energyId]);
        if (incoming > inventory.getFreeCapacity()) {
            throw new IllegalStateException("Stage 9E initial inventory exceeds capacity");
        }
        inventory.stock[steelId] = Math.max(inventory.stock[steelId], STEEL_STOCK);
        inventory.stock[energyId] = Math.max(inventory.stock[energyId], ENERGY_STOCK);
        MarketComponent market = producer.getComponent(MarketComponent.class);
        if (market != null) {
            market.isDirty = true;
        }
    }
}
