package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityIdAllocator;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.systems.TradeAISystem;

import java.util.Objects;

/**
 * Authoritative runtime boundary for persistent local-entity creation and structural removal.
 *
 * <p>The service owns the synchronous lifecycle sequence used by a {@link SimulationSession}:
 * allocate persistent ID, attach it, add the entity to Ashley and let {@link EntityRegistry}
 * register it; or validate removal, invalidate persistent references/runtime planning caches,
 * remove the entity from Ashley and let the registry unregister it.</p>
 *
 * <p>Stage 9A deliberately keeps structural removal separate from economic destruction. A newly
 * created or structurally removed entity must be economically empty: no wallet balance, inventory
 * stock or remaining asteroid resource. This prevents lifecycle plumbing from silently becoming a
 * money/resource source or sink. Stage 9C adds explicit destruction policies for non-empty assets,
 * including resource accounting and salvage/transfer semantics.</p>
 *
 * <p>Reference invalidation happens before Ashley removal and before a caller can take the next
 * save snapshot. Therefore a successful removal cannot leave {@link TradeAIComponent} or
 * {@link MiningComponent} pointing at an entity that has already disappeared from persistence.</p>
 */
public final class EntityLifecycleService {
    private final Engine engine;
    private final EntityIdAllocator idAllocator;
    private final EntityRegistry registry;

    /**
     * Creates a lifecycle boundary for one local simulation engine.
     *
     * @param engine authoritative Ashley engine
     * @param idAllocator deterministic session-wide persistent-ID allocator
     * @param registry registry tracking the same engine
     * @throws NullPointerException if any dependency is missing
     */
    public EntityLifecycleService(
            Engine engine,
            EntityIdAllocator idAllocator,
            EntityRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "Ashley Engine не задан");
        this.idAllocator = Objects.requireNonNull(idAllocator, "EntityIdAllocator не задан");
        this.registry = Objects.requireNonNull(registry, "EntityRegistry не задан");
    }

    /**
     * Adds a new economically-empty persistent entity to the running session.
     *
     * <p>The entity must not already belong to the engine and must not already carry an
     * {@link EntityIdComponent}. Runtime IDs are always allocated here so repeated deterministic
     * continuations produce the same sequence.</p>
     *
     * @param entity new detached runtime entity
     * @return newly allocated persistent ID
     * @throws NullPointerException if entity is missing
     * @throws IllegalArgumentException if entity is already live or already has a persistent ID
     * @throws IllegalStateException if the entity is economically non-empty or registry tracking
     *                               fails
     */
    public EntityId create(Entity entity) {
        Entity checked = Objects.requireNonNull(entity, "Создаваемая Entity не задана");
        if (isLive(checked)) {
            throw new IllegalArgumentException("Нельзя повторно создать уже живую Entity");
        }
        if (checked.getComponent(EntityIdComponent.class) != null) {
            throw new IllegalArgumentException("Runtime create принимает Entity без EntityIdComponent");
        }
        requireEconomicallyEmpty(checked, "создание");

        EntityId id = idAllocator.allocate();
        if (registry.contains(id)) {
            throw new IllegalStateException("EntityIdAllocator выдал уже зарегистрированный ID: " + id);
        }
        checked.add(new EntityIdComponent(id));
        engine.addEntity(checked);
        if (registry.find(id) != checked) {
            throw new IllegalStateException("EntityRegistry не зарегистрировал созданную Entity: " + id);
        }
        return id;
    }

    /**
     * Structurally removes an economically-empty persistent entity.
     *
     * <p>Before removal, all known persistent AI references to {@code id} are reset and transient
     * trade-planning caches are invalidated. The operation does not create ledger entries because
     * it is forbidden to remove economic value. Non-empty destruction belongs to Stage 9C.</p>
     *
     * @param id persistent entity ID; {@code null} is treated as absent
     * @return {@code true} when a live entity was removed; {@code false} when ID was absent
     * @throws IllegalStateException if the entity still owns money/resources or registry cleanup
     *                               fails
     */
    public boolean remove(EntityId id) {
        Entity target = registry.find(id);
        if (target == null) {
            return false;
        }
        requireEconomicallyEmpty(target, "удаление");

        invalidatePersistentReferences(id, target);
        engine.removeEntity(target);
        if (registry.contains(id)) {
            throw new IllegalStateException("EntityRegistry сохранил удалённую Entity: " + id);
        }
        return true;
    }

    private void invalidatePersistentReferences(EntityId removedId, Entity removedEntity) {
        for (Entity candidate : engine.getEntities()) {
            if (candidate == removedEntity) {
                continue;
            }
            invalidateTradeReference(candidate.getComponent(TradeAIComponent.class), removedId);
            invalidateMiningReference(candidate.getComponent(MiningComponent.class), removedId);
        }

        TradeAISystem tradeSystem = engine.getSystem(TradeAISystem.class);
        if (tradeSystem != null) {
            tradeSystem.invalidateAfterEntityRemoval(
                    removedId,
                    removedEntity.getComponent(MarketComponent.class) != null);
        }
    }

    private static void invalidateTradeReference(TradeAIComponent trade, EntityId removedId) {
        if (trade == null) {
            return;
        }
        if (!removedId.equals(trade.buyStationId)
                && !removedId.equals(trade.sellStationId)
                && !removedId.equals(trade.targetStationId)) {
            return;
        }
        trade.resetRoute();
        trade.state = TradeAIComponent.State.IDLE;
        trade.routeSearchCooldown = 0f;
    }

    private static void invalidateMiningReference(MiningComponent mining, EntityId removedId) {
        if (mining == null) {
            return;
        }
        if (removedId.equals(mining.targetAsteroidId)) {
            mining.targetAsteroidId = null;
            mining.extractionRemainder = 0d;
            if (mining.active
                    && (mining.state == MiningComponent.State.TRAVEL_TO_ASTEROID
                    || mining.state == MiningComponent.State.MINING)) {
                mining.state = MiningComponent.State.SEARCHING;
            }
        }
        if (removedId.equals(mining.homeBaseId)) {
            mining.homeBaseId = null;
            if (mining.active && mining.state == MiningComponent.State.UNLOADING) {
                mining.state = MiningComponent.State.RETURNING_TO_BASE;
            }
        }
    }

    private void requireEconomicallyEmpty(Entity entity, String operation) {
        WalletComponent wallet = entity.getComponent(WalletComponent.class);
        if (wallet != null && wallet.getBalanceMilliCredits() != 0L) {
            throw new IllegalStateException(
                    "Lifecycle " + operation + " требует пустой WalletComponent");
        }

        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (inventory != null) {
            for (int amount : inventory.stock) {
                if (amount != 0) {
                    throw new IllegalStateException(
                            "Lifecycle " + operation + " требует пустой InventoryComponent");
                }
            }
        }

        AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
        if (asteroid != null && asteroid.remainingResource != 0L) {
            throw new IllegalStateException(
                    "Lifecycle " + operation + " требует исчерпанный AsteroidComponent");
        }
    }

    private boolean isLive(Entity entity) {
        for (Entity live : engine.getEntities()) {
            if (live == entity) {
                return true;
            }
        }
        return false;
    }
}
