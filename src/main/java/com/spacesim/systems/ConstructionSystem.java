package com.spacesim.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.ConstructionComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.PriceHistoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ArchetypeEntityFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.economy.EconomicLedger;
import com.spacesim.persistence.EntityId;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Завершает физически обеспеченные station construction projects.
 *
 * <p>Construction site остаётся обычной market entity до тех пор, пока каждый required material
 * физически не лежит в её {@link InventoryComponent}. После fulfillment система записывает явные
 * {@code RESOURCE_SINK} для bill of materials и преобразует ту же Ashley {@link Entity} в
 * data-driven station archetype. Persistent {@link EntityId} и оставшийся wallet balance
 * сохраняются; template initial stock/credits archetype не создаются.</p>
 */
public final class ConstructionSystem extends EntitySystem {
    /** Priority после обычных economic systems текущей local session. */
    public static final int PRIORITY = 10;

    private static final Family SITES = Family.all(
            EntityIdComponent.class,
            ConstructionComponent.class,
            IdentityComponent.class,
            TransformComponent.class,
            InventoryComponent.class,
            WalletComponent.class,
            MarketComponent.class,
            FactionComponent.class).get();

    private final ContentCatalog contentCatalog;
    private final EconomicLedger ledger;
    private final ComponentMapper<ConstructionComponent> constructionMapper =
            ComponentMapper.getFor(ConstructionComponent.class);
    private final ComponentMapper<InventoryComponent> inventoryMapper =
            ComponentMapper.getFor(InventoryComponent.class);
    private ImmutableArray<Entity> sites;
    private long completedStations;

    /**
     * Создаёт construction completion system.
     *
     * @param contentCatalog authoritative content catalog local session
     * @param ledger общий economic ledger local session
     */
    public ConstructionSystem(ContentCatalog contentCatalog, EconomicLedger ledger) {
        super(PRIORITY);
        this.contentCatalog = Objects.requireNonNull(contentCatalog, "ContentCatalog не задан");
        this.ledger = Objects.requireNonNull(ledger, "EconomicLedger не задан");
    }

    /** Получает live family construction sites. */
    @Override
    public void addedToEngine(Engine engine) {
        sites = engine.getEntitiesFor(SITES);
    }

    /** Освобождает family reference. */
    @Override
    public void removedFromEngine(Engine engine) {
        sites = null;
    }

    /**
     * Завершает все fully-funded material sites текущего tick в EntityId-порядке.
     *
     * @param deltaTime simulation delta; completion сама не зависит от времени
     */
    @Override
    public void update(float deltaTime) {
        if (sites == null || sites.size() == 0) {
            return;
        }
        List<Entity> ready = new ArrayList<>();
        for (Entity site : sites) {
            ConstructionComponent construction = constructionMapper.get(site);
            InventoryComponent inventory = inventoryMapper.get(site);
            if (construction != null
                    && construction.isFulfilled(inventory)
                    && inventory.getTotalStock() == construction.getTotalRequiredMaterials()) {
                ready.add(site);
            }
        }
        ready.sort((first, second) -> entityId(first).compareTo(entityId(second)));
        for (Entity site : ready) {
            if (constructionMapper.has(site)) {
                complete(site);
            }
        }
    }

    /** @return число station projects, завершённых этой runtime system после создания */
    public long getCompletedStations() {
        return completedStations;
    }

    private void complete(Entity site) {
        ConstructionComponent construction = constructionMapper.get(site);
        InventoryComponent siteInventory = inventoryMapper.get(site);
        WalletComponent siteWallet = site.getComponent(WalletComponent.class);
        TransformComponent siteTransform = site.getComponent(TransformComponent.class);
        FactionComponent siteFaction = site.getComponent(FactionComponent.class);
        IdentityComponent siteIdentity = site.getComponent(IdentityComponent.class);
        EntityId id = entityId(site);

        if (construction == null
                || siteInventory == null
                || siteWallet == null
                || siteTransform == null
                || siteFaction == null
                || siteIdentity == null
                || !construction.isFulfilled(siteInventory)
                || siteInventory.getTotalStock() != construction.getTotalRequiredMaterials()) {
            return;
        }

        Entity template = ArchetypeEntityFactory.createStation(
                contentCatalog,
                construction.targetStationArchetypeContentId,
                construction.targetStationName,
                siteTransform.position.x,
                siteTransform.position.y);
        FactionComponent targetFaction = template.getComponent(FactionComponent.class);
        if (targetFaction == null || targetFaction.factionId != siteFaction.factionId) {
            throw new IllegalStateException("Construction target faction не совпадает с construction site");
        }

        InventoryComponent targetInventory = template.getComponent(InventoryComponent.class);
        WalletComponent targetWallet = template.getComponent(WalletComponent.class);
        if (targetInventory == null || targetWallet == null) {
            throw new IllegalStateException("Target station archetype не содержит inventory/wallet");
        }
        Arrays.fill(targetInventory.stock, 0);
        long retainedBalance = siteWallet.getBalanceMilliCredits();

        for (int itemId = 0; itemId < com.spacesim.constants.Constants.MAX_ITEMS; itemId++) {
            int amount = construction.getRequiredAmount(itemId);
            if (amount <= 0) {
                continue;
            }
            siteInventory.stock[itemId] -= amount;
            ledger.recordResourceSink(
                    siteIdentity.name,
                    itemId,
                    amount,
                    "station-construction:" + construction.targetStationArchetypeContentId);
        }
        if (siteInventory.getTotalStock() != 0) {
            throw new IllegalStateException("Construction completion оставила физические материалы на site");
        }

        replace(site, ConstructionComponent.class, null);
        replace(site, IdentityComponent.class, template.getComponent(IdentityComponent.class));
        replace(site, ArchetypeComponent.class, template.getComponent(ArchetypeComponent.class));
        replace(site, TransformComponent.class, template.getComponent(TransformComponent.class));
        replace(site, InventoryComponent.class, targetInventory);
        replace(site, WalletComponent.class, new WalletComponent(retainedBalance));
        replace(site, MarketComponent.class, template.getComponent(MarketComponent.class));
        replace(site, FactionComponent.class, targetFaction);
        replace(site, PriceHistoryComponent.class, template.getComponent(PriceHistoryComponent.class));
        replace(site, ProductionComponent.class, template.getComponent(ProductionComponent.class));

        if (!entityId(site).equals(id)) {
            throw new IllegalStateException("Construction completion изменила persistent EntityId");
        }
        completedStations++;
    }

    private static EntityId entityId(Entity entity) {
        EntityIdComponent component = entity.getComponent(EntityIdComponent.class);
        if (component == null || component.id == null) {
            throw new IllegalStateException("Construction site не имеет persistent EntityId");
        }
        return component.id;
    }

    private static <T extends com.badlogic.ashley.core.Component> void replace(
            Entity entity,
            Class<T> type,
            T component) {
        entity.remove(type);
        if (component != null) {
            entity.add(component);
        }
    }
}
