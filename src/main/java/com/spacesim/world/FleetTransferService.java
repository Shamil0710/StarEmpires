package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.IdentityComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.persistence.FleetTransferStateMapper;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.TradeAISystem;

import java.util.Objects;

/**
 * Local-session boundary for transferring one fleet without creating a resource or money sink.
 *
 * <p>Unlike ordinary structural removal, detach is allowed to move a non-empty fleet because its
 * complete physical value is first captured into an immutable world-owned snapshot. Attach gives
 * the restored fleet a fresh system-local EntityId while preserving cargo, wallet and ship state.
 * Neither operation writes source/sink ledger entries.</p>
 */
final class FleetTransferService {
    private FleetTransferService() {
        throw new AssertionError("Utility class");
    }

    static EntityState detach(SimulationSession session, EntityId localEntityId) {
        SimulationSession checked = Objects.requireNonNull(session, "SimulationSession не задан");
        EntityId id = Objects.requireNonNull(localEntityId, "Local fleet EntityId не задан");
        Entity target = checked.getEntityRegistry().find(id);
        if (target == null) {
            throw new IllegalArgumentException("Fleet отсутствует в local session: " + id);
        }
        requireFleet(target);

        EntityState snapshot = FleetTransferStateMapper.sanitize(EntityStateMapper.capture(target));
        invalidateReferences(checked, id, target);
        checked.getEngine().removeEntity(target);
        if (checked.getEntityRegistry().contains(id)) {
            throw new IllegalStateException("Fleet остался зарегистрирован после detach: " + id);
        }
        return snapshot;
    }

    static EntityId attach(
            SimulationSession session,
            EntityState state,
            float arrivalX,
            float arrivalY) {
        SimulationSession checked = Objects.requireNonNull(session, "SimulationSession не задан");
        Entity entity = FleetTransferStateMapper.restoreDetached(state, arrivalX, arrivalY);
        requireFleet(entity);

        WalletComponent wallet = entity.remove(WalletComponent.class);
        InventoryComponent inventory = entity.remove(InventoryComponent.class);
        EntityId newId;
        try {
            newId = checked.createEntity(entity);
        } catch (RuntimeException exception) {
            if (wallet != null) {
                entity.add(wallet);
            }
            if (inventory != null) {
                entity.add(inventory);
            }
            throw exception;
        }
        if (wallet != null) {
            entity.add(wallet);
        }
        if (inventory != null) {
            entity.add(inventory);
        }
        if (checked.getEntityRegistry().find(newId) != entity) {
            throw new IllegalStateException("Fleet не зарегистрирован после attach: " + newId);
        }
        return newId;
    }

    private static void requireFleet(Entity entity) {
        IdentityComponent identity = entity.getComponent(IdentityComponent.class);
        if (identity == null || identity.kind != IdentityComponent.Kind.FLEET) {
            throw new IllegalArgumentException("World fleet transfer принимает только fleet entity");
        }
    }

    private static void invalidateReferences(
            SimulationSession session,
            EntityId removedId,
            Entity removedEntity) {
        for (Entity candidate : session.getEngine().getEntities()) {
            if (candidate == removedEntity) {
                continue;
            }
            invalidateTrade(candidate.getComponent(TradeAIComponent.class), removedId);
            invalidateMining(candidate.getComponent(MiningComponent.class), removedId);
        }
        TradeAISystem trade = session.getEngine().getSystem(TradeAISystem.class);
        if (trade != null) {
            trade.invalidateAfterEntityRemoval(
                    removedId,
                    removedEntity.getComponent(MarketComponent.class) != null);
        }
    }

    private static void invalidateTrade(TradeAIComponent trade, EntityId removedId) {
        if (trade == null
                || (!removedId.equals(trade.buyStationId)
                && !removedId.equals(trade.sellStationId)
                && !removedId.equals(trade.targetStationId))) {
            return;
        }
        trade.resetRoute();
        trade.state = TradeAIComponent.State.IDLE;
        trade.routeSearchCooldown = 0f;
    }

    private static void invalidateMining(MiningComponent mining, EntityId removedId) {
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
}
