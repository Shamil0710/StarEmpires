package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.ShipComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.constants.Constants;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;

import java.util.Objects;
import java.util.Optional;

/**
 * Player-facing command/read adapter for Stage-14A manual mining.
 *
 * <p>The service never changes asteroid reserves, ship cargo, money or Transform directly. It only
 * writes transient {@link MiningCommandComponent} intent to the active physical ship and reads
 * authoritative ECS state for diagnostics. {@link com.spacesim.systems.MiningSystem} remains the
 * sole extraction executor shared with autonomous miners.</p>
 */
public final class PlayerMiningService {
    private final PlayerRuntime runtime;

    /**
     * Creates a manual-mining adapter around the current playable runtime.
     *
     * @param runtime authoritative playable runtime
     */
    public PlayerMiningService(PlayerRuntime runtime) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
    }

    /**
     * Selects one live local asteroid without moving the ship or starting extraction by itself.
     *
     * <p>A live asteroid containing a different mineable resource may still be selected so the
     * authoritative mining system can expose {@link MiningCommandComponent.Status#INVALID_RESOURCE}
     * rather than silently rejecting the UI selection.</p>
     *
     * @param targetId persistent local EntityId of the asteroid
     * @return true when a mining-capable undocked active ship accepted the live target selection
     */
    public boolean selectTarget(EntityId targetId) {
        EntityId checked = Objects.requireNonNull(targetId, "Mining targetId not set");
        ActiveContext active = activeContext().orElse(null);
        if (active == null || runtime.player().docked() || isJumping(active.fleetId())
                || !isMiningCapable(active.entity())) {
            return false;
        }
        Entity target = active.session().getEntityRegistry().find(checked);
        if (target == null
                || target.getComponent(AsteroidComponent.class) == null
                || target.getComponent(TransformComponent.class) == null) {
            return false;
        }
        MiningCommandComponent command = ensureCommand(active.entity());
        command.targetAsteroidId = checked;
        command.status = MiningCommandComponent.Status.IDLE;
        command.extractedLastTick = 0;
        return true;
    }

    /**
     * Enables or disables continuous extraction intent for the current selected target.
     *
     * @param requested true to request extraction every fixed tick while constraints allow
     * @return true when a mining-capable local active ship accepted the intent
     */
    public boolean setMiningRequested(boolean requested) {
        ActiveContext active = activeContext().orElse(null);
        if (active == null || runtime.player().docked() || isJumping(active.fleetId())
                || !isMiningCapable(active.entity())) {
            return false;
        }
        MiningCommandComponent command = ensureCommand(active.entity());
        if (requested && command.targetAsteroidId == null) {
            command.status = MiningCommandComponent.Status.NO_TARGET;
            return false;
        }
        command.miningRequested = requested;
        if (!requested && command.targetAsteroidId == null) {
            command.status = MiningCommandComponent.Status.IDLE;
        }
        return true;
    }

    /** Clears manual target/intent without changing already mined cargo or asteroid reserves. */
    public void clear() {
        activeContext().ifPresent(active -> {
            MiningCommandComponent command = active.entity().getComponent(MiningCommandComponent.class);
            if (command != null) {
                command.clear();
            }
        });
    }

    /**
     * Builds a read-only status view from the active physical ship and selected asteroid.
     *
     * @return local active-ship mining view, or empty while the active fleet is not materialized
     */
    public Optional<PlayerMiningView> view() {
        ActiveContext active = activeContext().orElse(null);
        if (active == null) {
            return Optional.empty();
        }

        Entity ship = active.entity();
        MiningComponent mining = ship.getComponent(MiningComponent.class);
        InventoryComponent inventory = ship.getComponent(InventoryComponent.class);
        MiningCommandComponent command = ship.getComponent(MiningCommandComponent.class);
        boolean compatible = isMiningCapable(ship);

        MiningCommandComponent.Status status;
        if (!compatible) {
            status = MiningCommandComponent.Status.INCOMPATIBLE_SHIP;
        } else if (runtime.player().docked()) {
            status = MiningCommandComponent.Status.DOCKED;
        } else if (command == null) {
            status = MiningCommandComponent.Status.IDLE;
        } else {
            status = command.status;
        }

        EntityId targetId = command == null ? null : command.targetAsteroidId;
        Entity target = targetId == null ? null : active.session().getEntityRegistry().find(targetId);
        AsteroidComponent asteroid = target == null ? null : target.getComponent(AsteroidComponent.class);
        TransformComponent targetTransform = target == null ? null : target.getComponent(TransformComponent.class);
        TransformComponent shipTransform = ship.getComponent(TransformComponent.class);

        Long remaining = asteroid == null ? null : asteroid.remainingResource;
        Float distance = targetTransform == null || shipTransform == null
                ? null : shipTransform.position.dst(targetTransform.position);
        int resourceItem = mining == null ? -1 : mining.resourceItem;
        int cargoUnits = inventory == null
                || resourceItem < 0
                || resourceItem >= Constants.MAX_ITEMS
                ? 0 : inventory.stock[resourceItem];
        int freeCapacity = inventory == null ? 0 : inventory.getFreeCapacity();
        float extractionRange = mining == null || !Float.isFinite(mining.extractionRange)
                ? 0f : Math.max(0f, mining.extractionRange);
        boolean requested = command != null && command.miningRequested;
        int extracted = command == null ? 0 : Math.max(0, command.extractedLastTick);

        return Optional.of(new PlayerMiningView(
                status,
                targetId,
                requested,
                resourceItem,
                cargoUnits,
                freeCapacity,
                remaining,
                distance,
                extractionRange,
                extracted));
    }

    private Optional<ActiveContext> activeContext() {
        PlayerShipView shipView = runtime.activeShipView().orElse(null);
        FleetId activeFleetId = runtime.player().activeFleetId();
        if (shipView == null || activeFleetId == null) {
            return Optional.empty();
        }
        SimulationSession session = runtime.world().findSession(shipView.systemId()).orElse(null);
        Entity entity = session == null ? null : session.getEntityRegistry().find(shipView.localEntityId());
        return entity == null ? Optional.empty() : Optional.of(new ActiveContext(activeFleetId, session, entity));
    }

    private boolean isJumping(FleetId fleetId) {
        return runtime.world().findFleetJump(fleetId).isPresent();
    }

    private static boolean isMiningCapable(Entity entity) {
        ShipComponent ship = entity.getComponent(ShipComponent.class);
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        return ship != null
                && ship.type != null
                && ship.type.isMining()
                && mining != null
                && inventory != null
                && transform != null;
    }

    private static MiningCommandComponent ensureCommand(Entity entity) {
        MiningCommandComponent command = entity.getComponent(MiningCommandComponent.class);
        if (command == null) {
            command = new MiningCommandComponent();
            entity.add(command);
        }
        return command;
    }

    private record ActiveContext(FleetId fleetId, SimulationSession session, Entity entity) {
    }
}
