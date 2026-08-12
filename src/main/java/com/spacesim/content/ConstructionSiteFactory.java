package com.spacesim.content;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ConstructionComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.constants.Constants;

import java.util.Map;
import java.util.Objects;

/** Создаёт временный market construction site из data-driven target station archetype. */
public final class ConstructionSiteFactory {
    private ConstructionSiteFactory() {
        throw new AssertionError("ConstructionSiteFactory не создаёт экземпляров");
    }

    /**
     * Создаёт пустой construction site без EntityId и без денег.
     *
     * <p>Каждый material requirement становится обычным tradable target stock с нулевым
     * consumption. Inventory capacity равна сумме bill of materials, поэтому site не может
     * завершиться при наличии скрытых лишних ресурсов. Деньги должны быть физически переведены в
     * site отдельным financing decision до начала закупок.</p>
     *
     * @param catalog authoritative content catalog
     * @param targetStationArchetypeId stable target station archetype ID
     * @param targetStationName отображаемое имя будущей станции
     * @param x координата X
     * @param y координата Y
     * @param requiredMaterials stable item IDs -> строго положительные amounts
     * @return новый construction-site Entity без persistent ID
     */
    public static Entity createStationSite(
            ContentCatalog catalog,
            String targetStationArchetypeId,
            String targetStationName,
            float x,
            float y,
            Map<String, Integer> requiredMaterials) {
        ContentCatalog content = Objects.requireNonNull(catalog, "ContentCatalog не задан");
        ContentCatalog.StationArchetypeDefinition target =
                content.findStationArchetype(targetStationArchetypeId);
        if (target == null) {
            throw new IllegalArgumentException("Неизвестный target station archetype: " + targetStationArchetypeId);
        }
        ContentCatalog.FactionDefinition faction = content.findFaction(target.factionId());
        if (faction == null) {
            throw new IllegalStateException("Target station потеряла faction: " + target.factionId());
        }
        if (!Float.isFinite(x) || !Float.isFinite(y)) {
            throw new IllegalArgumentException("Construction coordinates должны быть конечными");
        }
        Objects.requireNonNull(requiredMaterials, "Construction materials не заданы");
        if (requiredMaterials.isEmpty()) {
            throw new IllegalArgumentException("Construction materials не могут быть пустыми");
        }

        int[] requirements = new int[Constants.MAX_ITEMS];
        long total = 0L;
        for (Map.Entry<String, Integer> entry : requiredMaterials.entrySet()) {
            ContentCatalog.ItemDefinition item = content.findItem(entry.getKey());
            if (item == null) {
                throw new IllegalArgumentException("Неизвестный construction item: " + entry.getKey());
            }
            Integer amount = entry.getValue();
            if (amount == null || amount <= 0) {
                throw new IllegalArgumentException("Construction amount должен быть положительным");
            }
            if (requirements[item.runtimeId()] != 0) {
                throw new IllegalArgumentException("Construction item повторён: " + entry.getKey());
            }
            requirements[item.runtimeId()] = amount;
            total += amount;
            if (total > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Construction bill of materials слишком велик");
            }
        }

        ConstructionComponent construction = new ConstructionComponent(
                target.id(),
                targetStationName,
                requirements);
        InventoryComponent inventory = new InventoryComponent();
        inventory.capacity = construction.getTotalRequiredMaterials();
        MarketComponent market = new MarketComponent();
        for (ContentCatalog.ItemDefinition item : content.getItems()) {
            int required = construction.getRequiredAmount(item.runtimeId());
            if (required > 0) {
                market.configureTradableItem(item.runtimeId(), required, 0f);
            }
        }
        TransformComponent transform = new TransformComponent();
        transform.position.set(x, y);

        return new Entity()
                .add(new IdentityComponent(targetStationName + " — стройплощадка", IdentityComponent.Kind.STATION))
                .add(transform)
                .add(inventory)
                .add(new WalletComponent(0L))
                .add(market)
                .add(new FactionComponent(faction.runtimeId()))
                .add(new PriceHistoryComponent())
                .add(construction);
    }
}
