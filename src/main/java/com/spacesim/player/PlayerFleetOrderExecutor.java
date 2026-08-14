package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.flight.FlightDynamics;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Stage-15 runtime bridge from durable player fleet intent to ordinary physical simulation APIs.
 *
 * <p>Inactive player-owned fleets are removed from legacy autonomous movement and default to HOLD.
 * HOLD/MOVE/TRADE/MINE all use transient {@link FlightCommandComponent}; the shared
 * {@link com.spacesim.systems.AutonomousFlightSystem} and {@link FlightDynamics} exclusively own
 * velocity/Transform integration. Inter-system navigation reuses the existing Stage-10 jump FSM.
 * TRADE uses {@link PlayerFleetEconomyService} and the ordinary TradeController. MINE drives the
 * ordinary MiningSystem through {@link MiningCommandComponent}, so finite asteroid depletion and
 * real cargo remain one physical extraction boundary.</p>
 *
 * <p>ESCORT/PATROL/FOLLOW remain safe HOLD in this Stage-15C slice and are executed by later
 * survival/convoy work on top of the same navigation primitive.</p>
 */
final class PlayerFleetOrderExecutor {
    private static final float ARRIVAL_RADIUS = 2f;
    private static final float STOP_SPEED = 0.25f;
    private static final float MIN_APPROACH_SPEED_CAP = 1f;

    private final PlayerRuntime runtime;
    private final WorldSimulation world;
    private final ContentCatalog content;
    private final PlayerFleetEconomyService economy;
    private boolean preparing;

    PlayerFleetOrderExecutor(PlayerRuntime runtime, ContentCatalog content) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.world = runtime.world();
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
        this.economy = new PlayerFleetEconomyService(runtime);
    }

    /**
     * Reconciles delegated control before the next authoritative world update.
     *
     * <p>The active FleetId always remains under direct player control. Every other owned FleetId
     * is processed in stable ID order. The reentrancy guard allows a real delegated trade to update
     * PlayerState wallet/reputation without recursively executing another fleet-order pass.</p>
     */
    void prepare() {
        if (preparing) {
            return;
        }
        preparing = true;
        try {
            prepareOrders();
        } finally {
            preparing = false;
        }
    }

    private void prepareOrders() {
        PlayerState player = runtime.player();
        Map<FleetId, PlayerFleetOrderState> explicitOrders = new HashMap<>();
        for (PlayerFleetOrderState order : player.fleetOrders()) {
            explicitOrders.put(order.fleetId(), order);
        }
        List<FleetId> owned = new ArrayList<>(player.ownedFleetIds());
        owned.sort(FleetId::compareTo);
        for (FleetId fleetId : owned) {
            FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
            if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            Entity entity = resolveEntity(placement);
            if (entity == null) {
                continue;
            }
            if (fleetId.equals(player.activeFleetId())) {
                entity.remove(FlightCommandComponent.class);
                continue;
            }
            suppressLegacyAutonomy(entity);
            PlayerFleetOrderState order = explicitOrders.get(fleetId);
            if (order == null || order.type() == FleetOrderType.HOLD) {
                clearMiningCommand(entity);
                hold(entity);
                continue;
            }
            if (order.type() != FleetOrderType.MINE) {
                clearMiningCommand(entity);
            }
            switch (order.type()) {
                case MOVE -> executeMove(order, placement, entity, player);
                case TRADE -> executeTrade(order, placement, entity, player);
                case MINE -> executeMine(order, placement, entity, player);
                case HOLD -> hold(entity);
                case ESCORT, PATROL, FOLLOW -> hold(entity);
            }
        }
    }

    private void executeMove(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        navigateTo(
                order.fleetId(),
                placement,
                entity,
                player,
                order.targetSystemId(),
                order.targetX(),
                order.targetY(),
                ARRIVAL_RADIUS);
    }

    private void executeTrade(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        ContentCatalog.ItemDefinition item = content.findItem(order.itemContentId());
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (item == null || inventory == null) {
            hold(entity);
            return;
        }
        int itemId = item.runtimeId();
        boolean carrying = inventory.stock[itemId] > 0;
        DiscoveredObjectRef target = carrying
                ? new DiscoveredObjectRef(order.secondarySystemId(), order.secondaryEntityId())
                : new DiscoveredObjectRef(order.targetSystemId(), order.targetEntityId());
        Entity market = resolveEntity(target.systemId(), target.entityId());
        TransformComponent marketTransform = market == null ? null : market.getComponent(TransformComponent.class);
        if (marketTransform == null) {
            hold(entity);
            return;
        }
        boolean arrived = navigateTo(
                order.fleetId(), placement, entity, player, target.systemId(),
                marketTransform.position.x, marketTransform.position.y, ARRIVAL_RADIUS);
        if (!arrived || !economy.isBerthed(order.fleetId(), target)) {
            return;
        }
        if (carrying) {
            economy.sellMaximum(order.fleetId(), target, order.itemContentId());
        } else {
            economy.buyMaximum(order.fleetId(), target, order.itemContentId());
        }
    }

    private void executeMine(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        ContentCatalog.ItemDefinition item = content.findItem(order.itemContentId());
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (item == null || mining == null || inventory == null || mining.resourceItem != item.runtimeId()) {
            clearMiningCommand(entity);
            hold(entity);
            return;
        }

        int cargo = inventory.stock[item.runtimeId()];
        Entity asteroid = resolveEntity(order.targetSystemId(), order.targetEntityId());
        AsteroidComponent asteroidState = asteroid == null ? null : asteroid.getComponent(AsteroidComponent.class);
        boolean asteroidUsable = asteroidState != null
                && asteroidState.resourceItem == item.runtimeId()
                && asteroidState.remainingResource > 0L;
        if (inventory.getFreeCapacity() <= 0 || !asteroidUsable) {
            clearMiningRequest(entity);
            if (cargo > 0 && order.secondarySystemId() != null) {
                deliverMinedCargo(order, placement, entity, player, mining);
            } else {
                hold(entity);
            }
            return;
        }

        TransformComponent targetTransform = asteroid.getComponent(TransformComponent.class);
        if (targetTransform == null) {
            clearMiningRequest(entity);
            hold(entity);
            return;
        }
        boolean inMiningPosition = navigateTo(
                order.fleetId(), placement, entity, player, order.targetSystemId(),
                targetTransform.position.x, targetTransform.position.y,
                Math.max(0f, mining.extractionRange));
        MiningCommandComponent command = ensureMiningCommand(entity);
        command.targetAsteroidId = order.targetEntityId();
        command.miningRequested = inMiningPosition;
    }

    private void deliverMinedCargo(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player,
            MiningComponent mining) {
        DiscoveredObjectRef delivery = new DiscoveredObjectRef(
                order.secondarySystemId(), order.secondaryEntityId());
        Entity market = resolveEntity(delivery.systemId(), delivery.entityId());
        TransformComponent marketTransform = market == null ? null : market.getComponent(TransformComponent.class);
        if (marketTransform == null) {
            hold(entity);
            return;
        }
        boolean arrived = navigateTo(
                order.fleetId(), placement, entity, player, delivery.systemId(),
                marketTransform.position.x, marketTransform.position.y, ARRIVAL_RADIUS);
        if (!arrived || !economy.isBerthed(order.fleetId(), delivery)) {
            return;
        }
        int sold = economy.sellMaximum(order.fleetId(), delivery, order.itemContentId());
        if (sold > 0) {
            mining.totalDelivered = saturatedAdd(Math.max(0L, mining.totalDelivered), sold);
        }
    }

    private boolean navigateTo(
            FleetId fleetId,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player,
            StarSystemId targetSystem,
            float targetX,
            float targetY,
            float arrivalRange) {
        if (!placement.systemId().equals(targetSystem)) {
            hold(entity);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform == null || transform.velocity.len2() > STOP_SPEED * STOP_SPEED
                    || world.findFleetJump(fleetId).isPresent()) {
                return false;
            }
            StarSystemId nextHop = nextKnownHop(player, placement.systemId(), targetSystem);
            if (nextHop != null) {
                entity.remove(FlightCommandComponent.class);
                world.requestFleetJump(fleetId, nextHop, 0f, 0f);
            }
            return false;
        }

        TransformComponent transform = entity.getComponent(TransformComponent.class);
        float baseSpeed = movementSpeed(entity);
        if (transform == null || baseSpeed <= 0f || !Float.isFinite(arrivalRange) || arrivalRange < 0f) {
            hold(entity);
            return false;
        }
        float dx = targetX - transform.position.x;
        float dy = targetY - transform.position.y;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared <= arrivalRange * arrivalRange) {
            hold(entity);
            return transform.velocity.len2() <= STOP_SPEED * STOP_SPEED;
        }
        float distance = (float) Math.sqrt(distanceSquared);
        FlightDynamics.Profile profile = FlightDynamics.profile(entity, baseSpeed);
        float stoppingLimited = FlightDynamics.stoppingLimitedSpeed(
                Math.max(0f, distance - arrivalRange), profile);
        float requestedSpeedCap = Math.min(
                baseSpeed,
                Math.max(MIN_APPROACH_SPEED_CAP, stoppingLimited));
        FlightCommandComponent command = ensureFlightCommand(entity);
        command.set(dx / distance, dy / distance, requestedSpeedCap);
        return false;
    }

    private void hold(Entity entity) {
        float speed = movementSpeed(entity);
        if (speed <= 0f) {
            entity.remove(FlightCommandComponent.class);
            return;
        }
        FlightCommandComponent command = ensureFlightCommand(entity);
        if (command.speedCap <= 0f || !Float.isFinite(command.speedCap)) {
            command.set(0f, 0f, speed);
        } else {
            command.stop();
        }
    }

    private StarSystemId nextKnownHop(PlayerState player, StarSystemId source, StarSystemId destination) {
        if (source.equals(destination)) {
            return destination;
        }
        Set<StarSystemId> known = new HashSet<>(player.discoveredSystemIds());
        if (!known.contains(source) || !known.contains(destination)) {
            return null;
        }
        ArrayDeque<StarSystemId> queue = new ArrayDeque<>();
        Map<StarSystemId, StarSystemId> previous = new HashMap<>();
        queue.add(source);
        previous.put(source, source);
        while (!queue.isEmpty()) {
            StarSystemId current = queue.removeFirst();
            List<StarSystemId> neighbors = new ArrayList<>(world.getTopology().neighbors(current));
            neighbors.sort(Comparator.naturalOrder());
            for (StarSystemId neighbor : neighbors) {
                if (!known.contains(neighbor) || previous.containsKey(neighbor)) {
                    continue;
                }
                previous.put(neighbor, current);
                if (neighbor.equals(destination)) {
                    return firstHop(previous, source, destination);
                }
                queue.addLast(neighbor);
            }
        }
        return null;
    }

    private static StarSystemId firstHop(
            Map<StarSystemId, StarSystemId> previous,
            StarSystemId source,
            StarSystemId destination) {
        StarSystemId cursor = destination;
        StarSystemId parent = previous.get(cursor);
        while (parent != null && !parent.equals(source)) {
            cursor = parent;
            parent = previous.get(cursor);
        }
        return parent == null ? null : cursor;
    }

    private Entity resolveEntity(FleetPlacementState placement) {
        return resolveEntity(placement.systemId(), placement.localEntityId());
    }

    private Entity resolveEntity(StarSystemId systemId, EntityId entityId) {
        SimulationSession session = world.findSession(systemId).orElse(null);
        return session == null || entityId == null ? null : session.getEntityRegistry().find(entityId);
    }

    private float movementSpeed(Entity entity) {
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        if (trade != null && Float.isFinite(trade.movementSpeed) && trade.movementSpeed > 0f) {
            return trade.movementSpeed;
        }
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null && Float.isFinite(mining.movementSpeed) && mining.movementSpeed > 0f) {
            return mining.movementSpeed;
        }
        ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
        ContentCatalog.ShipArchetypeDefinition ship = archetype == null
                ? null : content.findShipArchetype(archetype.contentId);
        return ship != null && Float.isFinite(ship.movementSpeed()) && ship.movementSpeed() > 0f
                ? ship.movementSpeed() : 0f;
    }

    private static FlightCommandComponent ensureFlightCommand(Entity entity) {
        FlightCommandComponent command = entity.getComponent(FlightCommandComponent.class);
        if (command == null) {
            command = new FlightCommandComponent();
            entity.add(command);
        }
        return command;
    }

    private static MiningCommandComponent ensureMiningCommand(Entity entity) {
        MiningCommandComponent command = entity.getComponent(MiningCommandComponent.class);
        if (command == null) {
            command = new MiningCommandComponent();
            entity.add(command);
        }
        return command;
    }

    private static void clearMiningRequest(Entity entity) {
        MiningCommandComponent command = entity.getComponent(MiningCommandComponent.class);
        if (command != null) {
            command.miningRequested = false;
        }
    }

    private static void clearMiningCommand(Entity entity) {
        MiningCommandComponent command = entity.getComponent(MiningCommandComponent.class);
        if (command != null) {
            command.clear();
            entity.remove(MiningCommandComponent.class);
        }
    }

    private static void suppressLegacyAutonomy(Entity entity) {
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        if (trade != null) {
            trade.state = TradeAIComponent.State.IDLE;
            trade.resetRoute();
            trade.routeSearchCooldown = Float.MAX_VALUE;
        }
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null) {
            mining.active = false;
            mining.state = MiningComponent.State.PAUSED;
        }
    }

    private static long saturatedAdd(long counter, int amount) {
        return Long.MAX_VALUE - counter < amount ? Long.MAX_VALUE : counter + amount;
    }
}
