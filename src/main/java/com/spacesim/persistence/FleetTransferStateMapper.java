package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.components.TransformComponent;

import java.util.Objects;

/** Builds a session-independent fleet snapshot for world travel. */
public final class FleetTransferStateMapper {
    private FleetTransferStateMapper() {
        throw new AssertionError("Utility class");
    }

    /** Clears references that are valid only inside the origin SimulationSession. */
    public static EntityState sanitize(EntityState state) {
        EntityState checked = Objects.requireNonNull(state, "Fleet EntityState не задан");
        if (checked.identity() == null || !"FLEET".equals(checked.identity().kindName())) {
            throw new IllegalArgumentException("State должен описывать fleet");
        }
        EntityState.TransformState transform = checked.transform() == null
                ? null
                : new EntityState.TransformState(checked.transform().x(), checked.transform().y(), 0f, 0f);
        EntityState.TradeAiState trade = checked.tradeAi();
        if (trade != null) {
            trade = new EntityState.TradeAiState(
                    "IDLE", null, null, null, -1, trade.specializedItem(), 0,
                    trade.cargoSpace(), trade.movementSpeed(), 0L, 0f);
        }
        EntityState.MiningState mining = checked.mining();
        if (mining != null) {
            mining = new EntityState.MiningState(
                    mining.resourceItem(), mining.extractionPerSecond(), mining.movementSpeed(),
                    mining.extractionRange(), mining.dockingRange(), 0d,
                    mining.totalMined(), mining.totalDelivered(), mining.active(),
                    mining.active() ? "SEARCHING" : "PAUSED", null, null);
        }
        return new EntityState(
                checked.id(), checked.identity(), transform, checked.inventory(), checked.wallet(),
                checked.market(), checked.production(), checked.priceHistory(), checked.faction(),
                checked.reputation(), checked.ship(), trade, mining, checked.combat(),
                checked.asteroid(), checked.archetype());
    }

    /** Restores a fleet as a detached Entity and applies destination-local coordinates. */
    public static Entity restoreDetached(EntityState state, float arrivalX, float arrivalY) {
        if (!Float.isFinite(arrivalX) || !Float.isFinite(arrivalY)) {
            throw new IllegalArgumentException("Fleet coordinates должны быть конечными");
        }
        Entity entity = EntityStateMapper.restore(sanitize(state));
        entity.remove(EntityIdComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        if (transform != null) {
            transform.position.set(arrivalX, arrivalY);
            transform.velocity.setZero();
        }
        return entity;
    }
}
