package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;

import java.util.Map;
import java.util.Objects;

/** Creates economically-empty physical ECS markets representing active construction sites. */
final class ConstructionSiteFactory {
    private ConstructionSiteFactory() {
        throw new AssertionError("ConstructionSiteFactory не создаёт экземпляров");
    }

    static Entity create(
            ContentCatalog catalog,
            ConstructionProjectId projectId,
            ContentCatalog.StationArchetypeDefinition target,
            Integer legalFactionRuntimeId,
            float x,
            float y) {
        ContentCatalog checked = Objects.requireNonNull(catalog, "ContentCatalog construction site не задан");
        Objects.requireNonNull(projectId, "ConstructionProjectId не задан");
        ContentCatalog.StationArchetypeDefinition station = Objects.requireNonNull(
                target, "Target station archetype не задан");
        ContentCatalog.ConstructionDefinition construction = station.construction();
        if (construction == null) {
            throw new IllegalArgumentException("Station archetype не имеет construction definition: " + station.id());
        }
        if (legalFactionRuntimeId != null && legalFactionRuntimeId < 0) {
            throw new IllegalArgumentException("Legal faction runtime ID cannot be negative");
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Construction site coordinates должны быть конечными");
        }

        long requiredUnits = 0L;
        for (int amount : construction.materials().values()) {
            requiredUnits = Math.addExact(requiredUnits, amount);
        }
        if (requiredUnits > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Construction site inventory capacity переполнен");
        }

        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = (int) requiredUnits;
        MarketComponent market = new MarketComponent();
        for (Map.Entry<String, Integer> requirement : construction.materials().entrySet()) {
            ContentCatalog.ItemDefinition item = checked.findItem(requirement.getKey());
            if (item == null) {
                throw new IllegalStateException("Construction requirement потерял item: " + requirement.getKey());
            }
            market.configureTradableItem(item.runtimeId(), requirement.getValue(), 0f);
        }

        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);
        Entity result = new Entity()
                .add(new IdentityComponent(
                        "Стройплощадка " + station.displayName() + " #" + projectId.value(),
                        IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent())
                .add(market)
                .add(ConstructionBidPolicy.create(checked, station))
                .add(new PriceHistoryComponent());
        if (legalFactionRuntimeId != null) {
            result.add(new FactionComponent(legalFactionRuntimeId));
        }
        return result;
    }

    static void restoreDerivedPolicy(
            ContentCatalog catalog,
            ContentCatalog.StationArchetypeDefinition target,
            Entity site) {
        Entity checkedSite = Objects.requireNonNull(site, "Construction site не задан");
        checkedSite.add(ConstructionBidPolicy.create(catalog, target));
        MarketComponent market = checkedSite.getComponent(MarketComponent.class);
        if (market != null) {
            market.isDirty = true;
        }
    }
}
