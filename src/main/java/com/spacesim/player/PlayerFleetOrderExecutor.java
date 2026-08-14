package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FlightCommandComponent;
import com.spacesim.components.MiningCommandComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.flight.FlightDynamics;
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
 * Explicit HOLD and MOVE orders write only {@link FlightCommandComponent}; the shared
 * {@link com.spacesim.systems.AutonomousFlightSystem} and {@link FlightDynamics} own velocity and
 * Transform integration. Inter-system MOVE reuses the existing Stage-10 jump FSM and never moves a
 * FleetId directly between sessions.</p>
 *
 * <p>TRADE/MINE/ESCORT/PATROL/FOLLOW are already persistable Stage-15A intent shapes but remain
 * physically held by this first 15A/15B execution slice until their dedicated later executors are
 * added. This prevents accidental fallback to legacy direct-position movement.</p>
 */
final class PlayerFleetOrderExecutor {
    private static final float ARRIVAL_RADIUS = 2f;
    private static final float STOP_SPEED = 0.25f;
    private static final float MIN_APPROACH_SPEED_CAP = 1f;

    private final PlayerRuntime runtime;
    private final WorldSimulation world;
    private final ContentCatalog content;

    PlayerFleetOrderExecutor(PlayerRuntime runtime, ContentCatalog content) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.world = runtime.world();
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
    }

    /**
     * Reconciles delegated control before the next authoritative world update.
     *
     * <p>The active FleetId always remains under direct player control. Every other owned FleetId
     * is deterministic by stable ID and either executes its explicit order or defaults to HOLD.</p>
     */
    void prepare() {
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
                hold(entity);
                continue;
            }
            switch (order.type()) {
                case MOVE -> executeMove(order, placement, entity, player);
                case HOLD -> hold(entity);
                case TRADE, MINE, ESCORT, PATROL, FOLLOW -> hold(entity);
            }
        }
    }

    private void executeMove(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        if (!placement.systemId().equals(order.targetSystemId())) {
            hold(entity);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform == null || transform.velocity.len2() > STOP_SPEED * STOP_SPEED
                    || world.findFleetJump(order.fleetId()).isPresent()) {
                return;
            }
            StarSystemId nextHop = nextKnownHop(player, placement.systemId(), order.targetSystemId());
            if (nextHop != null) {
                entity.remove(FlightCommandComponent.class);
                world.requestFleetJump(order.fleetId(), nextHop, 0f, 0f);
            }
            return;
        }

        TransformComponent transform = entity.getComponent(TransformComponent.class);
        float baseSpeed = movementSpeed(entity);
        if (transform == null || baseSpeed <= 0f) {
            hold(entity);
            return;
        }
        float dx = order.targetX() - transform.position.x;
        float dy = order.targetY() - transform.position.y;
        float distanceSquared = dx * dx + dy * dy;
        if (distanceSquared <= ARRIVAL_RADIUS * ARRIVAL_RADIUS) {
            hold(entity);
            return;
        }
        float distance = (float) Math.sqrt(distanceSquared);
        FlightDynamics.Profile profile = FlightDynamics.profile(entity, baseSpeed);
        float stoppingLimited = FlightDynamics.stoppingLimitedSpeed(
                Math.max(0f, distance - ARRIVAL_RADIUS), profile);
        float requestedSpeedCap = Math.min(
                baseSpeed,
                Math.max(MIN_APPROACH_SPEED_CAP, stoppingLimited));
        FlightCommandComponent command = ensureFlightCommand(entity);
        command.set(dx / distance, dy / distance, requestedSpeedCap);
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
        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        return session == null ? null : session.getEntityRegistry().find(placement.localEntityId());
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
        MiningCommandComponent miningCommand = entity.getComponent(MiningCommandComponent.class);
        if (miningCommand != null) {
            miningCommand.miningRequested = false;
        }
    }
}
