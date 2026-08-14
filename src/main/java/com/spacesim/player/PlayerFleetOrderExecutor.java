package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.components.CombatCommandComponent;
import com.spacesim.components.CombatComponent;
import com.spacesim.components.DelegatedFleetComponent;
import com.spacesim.components.EntityIdComponent;
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
import com.spacesim.world.ConstructionMaterialState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.ConstructionProjectState;
import com.spacesim.world.ConstructionProjectStatus;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpState;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalSystemCoordinates;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Runtime bridge from durable player fleet intent to ordinary physical simulation APIs.
 *
 * <p>Inactive player-owned fleets receive a transient {@link DelegatedFleetComponent}. Generic
 * TradeAI/autonomous Mining must not own movement while that marker is present; the persistent
 * player order is the sole decision source and writes ordinary {@link FlightCommandComponent}
 * intent. {@link com.spacesim.systems.AutonomousFlightSystem} and {@link FlightDynamics} remain the
 * only normal-flight Transform integrators.</p>
 *
 * <p>TRADE reuses {@link PlayerFleetEconomyService} and the ordinary TradeController. MINE drives
 * the ordinary MiningSystem through {@link MiningCommandComponent}. FOLLOW and ESCORT resolve the
 * live target FleetId on every decision and maintain a physical separation radius. PATROL cycles
 * its persistent system list, dwells physically at each waypoint, then uses the same cumulative
 * route-risk planner and Stage-10 jump FSM as other delegated movement.</p>
 *
 * <p>Stage-16 SUPPLY_PROJECT dynamically chooses a known physical supplier with
 * {@link PlayerSupplyProjectPlanner}, purchases only real remaining project demand through the
 * ordinary trade boundary, then transfers the same cargo into the owned site through
 * {@link PlayerConstructionService}. No self-sale, cargo reservation or virtual delivery exists.</p>
 *
 * <p>Civilian survival interrupts but never overwrites a durable economic order when a live
 * non-owned combatant is actually targeting that FleetId. The ship accelerates away through the
 * same inertial flight controller and resumes the original order after bounded hysteresis.</p>
 */
final class PlayerFleetOrderExecutor {
    private static final float ARRIVAL_RADIUS = 2f;
    private static final float FOLLOW_RADIUS = 28f;
    private static final float ESCORT_RADIUS = 38f;
    private static final float STOP_SPEED = 0.25f;
    private static final float MIN_APPROACH_SPEED_CAP = 1f;
    private static final long THREAT_CLEAR_HYSTERESIS_TICKS = 30L;
    private static final long PATROL_DWELL_TICKS = 20L;

    private final PlayerRuntime runtime;
    private final WorldSimulation world;
    private final ContentCatalog content;
    private final PlayerFleetEconomyService economy;
    private final PlayerFleetRoutePlanner routePlanner;
    private final PlayerSupplyProjectPlanner supplyPlanner;
    private final PlayerConstructionService construction;
    private final PlayerThreatObserver threatObserver;
    private final Map<FleetId, SurvivalState> survival = new HashMap<>();
    private final Map<FleetId, PatrolState> patrolStates = new HashMap<>();
    private boolean preparing;

    PlayerFleetOrderExecutor(PlayerRuntime runtime, ContentCatalog content) {
        this.runtime = Objects.requireNonNull(runtime, "PlayerRuntime not set");
        this.world = runtime.world();
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
        this.economy = new PlayerFleetEconomyService(runtime);
        this.routePlanner = new PlayerFleetRoutePlanner(runtime);
        this.supplyPlanner = new PlayerSupplyProjectPlanner(runtime);
        this.construction = new PlayerConstructionService(runtime);
        this.threatObserver = new PlayerThreatObserver(runtime);
    }

    /**
     * Reconciles delegated control before the next authoritative world update.
     *
     * <p>The active FleetId remains under direct player control. Every other owned FleetId is
     * processed in stable ID order. A reentrancy guard allows real trade/intel mutations to replace
     * PlayerState without recursively executing another order pass.</p>
     */
    void prepare() {
        if (preparing) {
            return;
        }
        preparing = true;
        try {
            threatObserver.observe();
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
        Set<EntityId> ownedLocalIds = ownedLocalEntityIds(player);
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
                survival.remove(fleetId);
                patrolStates.remove(fleetId);
                entity.remove(DelegatedFleetComponent.class);
                entity.remove(FlightCommandComponent.class);
                continue;
            }
            ensureDelegatedMarker(entity);
            suppressLegacyAutonomy(entity);
            PlayerFleetOrderState order = explicitOrders.get(fleetId);
            if (order == null || order.type() != FleetOrderType.PATROL) {
                patrolStates.remove(fleetId);
            }
            if (handleCivilianSurvival(fleetId, placement, entity, ownedLocalIds)) {
                clearMiningRequest(entity);
                continue;
            }
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
                case FOLLOW -> executeFollow(order, placement, entity, player, FOLLOW_RADIUS);
                case ESCORT -> executeFollow(order, placement, entity, player, ESCORT_RADIUS);
                case PATROL -> executePatrol(order, placement, entity, player);
                case SUPPLY_PROJECT -> executeSupplyProject(order, placement, entity, player);
                case HOLD -> hold(entity);
            }
        }
        survival.keySet().removeIf(fleetId -> !player.ownedFleetIds().contains(fleetId));
        patrolStates.keySet().removeIf(fleetId -> !player.ownedFleetIds().contains(fleetId));
    }

    private boolean handleCivilianSurvival(
            FleetId fleetId,
            FleetPlacementState placement,
            Entity entity,
            Set<EntityId> ownedLocalIds) {
        if (!isCivilian(entity)) {
            survival.remove(fleetId);
            return false;
        }
        List<Entity> attackers = attackersTargeting(placement, ownedLocalIds);
        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        long currentTick = session == null ? 0L : session.getClock().getTick();
        SurvivalState state = survival.get(fleetId);
        if (!attackers.isEmpty()) {
            EscapeVector vector = escapeVector(entity, attackers);
            state = new SurvivalState(currentTick, vector.axisX(), vector.axisY());
            survival.put(fleetId, state);
            flee(entity, state);
            return true;
        }
        if (state != null && currentTick - state.lastThreatTick() < THREAT_CLEAR_HYSTERESIS_TICKS) {
            flee(entity, state);
            return true;
        }
        survival.remove(fleetId);
        return false;
    }

    private List<Entity> attackersTargeting(FleetPlacementState placement, Set<EntityId> ownedLocalIds) {
        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        if (session == null) {
            return List.of();
        }
        List<Entity> result = new ArrayList<>();
        for (Entity candidate : session.getEngine().getEntities()) {
            EntityIdComponent candidateId = candidate.getComponent(EntityIdComponent.class);
            CombatCommandComponent command = candidate.getComponent(CombatCommandComponent.class);
            CombatComponent combat = candidate.getComponent(CombatComponent.class);
            TransformComponent transform = candidate.getComponent(TransformComponent.class);
            if (candidateId == null || command == null || combat == null || transform == null
                    || command.targetId == null || ownedLocalIds.contains(candidateId.id)
                    || !placement.localEntityId().equals(command.targetId)
                    || !combat.isOperational()) {
                continue;
            }
            result.add(candidate);
        }
        return List.copyOf(result);
    }

    private static EscapeVector escapeVector(Entity civilian, List<Entity> attackers) {
        TransformComponent shipTransform = civilian.getComponent(TransformComponent.class);
        float x = 0f;
        float y = 0f;
        for (Entity attacker : attackers) {
            TransformComponent attackerTransform = attacker.getComponent(TransformComponent.class);
            float dx = shipTransform.position.x - attackerTransform.position.x;
            float dy = shipTransform.position.y - attackerTransform.position.y;
            float length = (float) Math.sqrt(dx * dx + dy * dy);
            if (length > 0.0001f) {
                x += dx / length;
                y += dy / length;
            }
        }
        float length = (float) Math.sqrt(x * x + y * y);
        if (length <= 0.0001f) {
            return new EscapeVector(1f, 0f);
        }
        return new EscapeVector(x / length, y / length);
    }

    private void flee(Entity entity, SurvivalState state) {
        float speed = movementSpeed(entity);
        if (speed <= 0f) {
            hold(entity);
            return;
        }
        ensureFlightCommand(entity).set(state.axisX(), state.axisY(), speed);
    }

    private static boolean isCivilian(Entity entity) {
        return entity.getComponent(TradeAIComponent.class) != null
                || entity.getComponent(MiningComponent.class) != null;
    }

    private void executeMove(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        navigateTo(order.fleetId(), placement, entity, player, order.targetSystemId(),
                order.targetX(), order.targetY(), ARRIVAL_RADIUS);
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
        boolean arrived = navigateTo(order.fleetId(), placement, entity, player, target.systemId(),
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

    private void executeSupplyProject(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        ConstructionProjectState project = resolveOwnedSupplyProject(player, order);
        ContentCatalog.ItemDefinition item = content.findItem(order.itemContentId());
        InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
        if (project == null || item == null || inventory == null) {
            hold(entity);
            return;
        }
        int remaining = remainingRequired(project, item.id());
        if (remaining <= 0) {
            hold(entity);
            return;
        }
        int cargo = inventory.stock[item.runtimeId()];
        if (cargo > 0) {
            Entity site = resolveEntity(project.systemId(), project.constructionSiteEntityId());
            TransformComponent siteTransform = site == null ? null : site.getComponent(TransformComponent.class);
            if (siteTransform == null) {
                hold(entity);
                return;
            }
            boolean arrived = navigateTo(
                    order.fleetId(),
                    placement,
                    entity,
                    player,
                    project.systemId(),
                    siteTransform.position.x,
                    siteTransform.position.y,
                    ARRIVAL_RADIUS);
            if (arrived) {
                construction.deliverMaterial(
                        project.id(),
                        order.fleetId(),
                        item.id(),
                        Math.min(cargo, remaining));
            }
            return;
        }

        DiscoveredObjectRef siteRef = new DiscoveredObjectRef(
                project.systemId(), project.constructionSiteEntityId());
        PlayerSupplyProjectPlan plan = supplyPlanner.plan(order.fleetId(), siteRef, item.id()).orElse(null);
        if (plan == null) {
            hold(entity);
            return;
        }
        Entity supplier = resolveEntity(plan.supplier().systemId(), plan.supplier().entityId());
        TransformComponent supplierTransform = supplier == null ? null : supplier.getComponent(TransformComponent.class);
        if (supplierTransform == null) {
            hold(entity);
            return;
        }
        boolean arrived = navigateTo(
                order.fleetId(),
                placement,
                entity,
                player,
                plan.supplier().systemId(),
                supplierTransform.position.x,
                supplierTransform.position.y,
                ARRIVAL_RADIUS);
        if (arrived && economy.isBerthed(order.fleetId(), plan.supplier())) {
            economy.buyUpTo(order.fleetId(), plan.supplier(), item.id(), remaining);
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
        boolean inMiningPosition = navigateTo(order.fleetId(), placement, entity, player,
                order.targetSystemId(), targetTransform.position.x, targetTransform.position.y,
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
        boolean arrived = navigateTo(order.fleetId(), placement, entity, player, delivery.systemId(),
                marketTransform.position.x, marketTransform.position.y, ARRIVAL_RADIUS);
        if (!arrived || !economy.isBerthed(order.fleetId(), delivery)) {
            return;
        }
        int sold = economy.sellMaximum(order.fleetId(), delivery, order.itemContentId());
        if (sold > 0) {
            mining.totalDelivered = saturatedAdd(Math.max(0L, mining.totalDelivered), sold);
        }
    }

    private void executeFollow(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player,
            float separationRadius) {
        FleetPlacementState targetPlacement = world.findFleet(order.targetFleetId()).orElse(null);
        if (targetPlacement == null) {
            hold(entity);
            return;
        }
        if (targetPlacement.locationKind() == FleetLocationKind.IN_SYSTEM) {
            Entity target = resolveEntity(targetPlacement);
            TransformComponent targetTransform = target == null ? null : target.getComponent(TransformComponent.class);
            if (targetTransform == null) {
                hold(entity);
                return;
            }
            navigateTo(order.fleetId(), placement, entity, player, targetPlacement.systemId(),
                    targetTransform.position.x, targetTransform.position.y, separationRadius);
            return;
        }
        FleetJumpState jump = world.findFleetJump(order.targetFleetId()).orElse(null);
        if (jump == null) {
            hold(entity);
            return;
        }
        navigateTo(order.fleetId(), placement, entity, player, jump.destinationSystemId(),
                LocalSystemCoordinates.ARRIVAL_X, LocalSystemCoordinates.ARRIVAL_Y, separationRadius);
    }

    private void executePatrol(
            PlayerFleetOrderState order,
            FleetPlacementState placement,
            Entity entity,
            PlayerState player) {
        List<StarSystemId> route = order.patrolSystemIds();
        if (route.isEmpty()) {
            hold(entity);
            return;
        }
        int currentIndex = route.indexOf(placement.systemId());
        if (currentIndex < 0) {
            patrolStates.remove(order.fleetId());
            navigateTo(order.fleetId(), placement, entity, player, route.get(0),
                    LocalSystemCoordinates.ARRIVAL_X, LocalSystemCoordinates.ARRIVAL_Y, ARRIVAL_RADIUS);
            return;
        }

        SimulationSession session = world.findSession(placement.systemId()).orElse(null);
        long tick = session == null ? 0L : session.getClock().getTick();
        PatrolState state = patrolStates.get(order.fleetId());
        if (state == null || !state.systemId().equals(placement.systemId())) {
            state = new PatrolState(placement.systemId(), saturatedTickAdd(tick, PATROL_DWELL_TICKS));
            patrolStates.put(order.fleetId(), state);
        }
        if (tick < state.dwellUntilTick()) {
            hold(entity);
            return;
        }
        StarSystemId next = route.get((currentIndex + 1) % route.size());
        navigateTo(order.fleetId(), placement, entity, player, next,
                LocalSystemCoordinates.ARRIVAL_X, LocalSystemCoordinates.ARRIVAL_Y, ARRIVAL_RADIUS);
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
        if (targetSystem == null) {
            hold(entity);
            return false;
        }
        if (!placement.systemId().equals(targetSystem)) {
            hold(entity);
            TransformComponent transform = entity.getComponent(TransformComponent.class);
            if (transform == null || transform.velocity.len2() > STOP_SPEED * STOP_SPEED
                    || world.findFleetJump(fleetId).isPresent()) {
                return false;
            }
            StarSystemId nextHop = nextRiskAwareHop(fleetId, placement.systemId(), targetSystem);
            if (nextHop != null) {
                entity.remove(FlightCommandComponent.class);
                world.requestFleetJump(fleetId, nextHop, 0f, 0f);
            }
            return false;
        }

        TransformComponent transform = entity.getComponent(TransformComponent.class);
        float baseSpeed = movementSpeed(entity);
        if (transform == null || baseSpeed <= 0f || !Float.isFinite(arrivalRange) || arrivalRange < 0f
                || !Float.isFinite(targetX) || !Float.isFinite(targetY)) {
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
        float requestedSpeedCap = Math.min(baseSpeed, Math.max(MIN_APPROACH_SPEED_CAP, stoppingLimited));
        ensureFlightCommand(entity).set(dx / distance, dy / distance, requestedSpeedCap);
        return false;
    }

    private StarSystemId nextRiskAwareHop(FleetId fleetId, StarSystemId source, StarSystemId destination) {
        PlayerRouteRiskView route = routePlanner.plan(fleetId, source, destination).orElse(null);
        return route == null || route.path().size() < 2 ? null : route.path().get(1);
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

    private Set<EntityId> ownedLocalEntityIds(PlayerState player) {
        Set<EntityId> result = new HashSet<>();
        for (FleetPlacementState placement : world.getFleetPlacements()) {
            if (placement.locationKind() == FleetLocationKind.IN_SYSTEM
                    && player.ownedFleetIds().contains(placement.id())) {
                result.add(placement.localEntityId());
            }
        }
        return result;
    }

    private ConstructionProjectState resolveOwnedSupplyProject(
            PlayerState player,
            PlayerFleetOrderState order) {
        for (ConstructionProjectId projectId : player.ownedConstructionProjectIds()) {
            ConstructionProjectState project = world.findConstructionProject(projectId).orElse(null);
            if (project != null
                    && project.systemId().equals(order.targetSystemId())
                    && project.constructionSiteEntityId().equals(order.targetEntityId())
                    && project.status() != ConstructionProjectStatus.BUILDING
                    && project.status() != ConstructionProjectStatus.COMPLETED
                    && project.status() != ConstructionProjectStatus.CANCELLED
                    && project.status() != ConstructionProjectStatus.FAILED
                    && remainingRequired(project, order.itemContentId()) > 0) {
                return project;
            }
        }
        return null;
    }

    private static int remainingRequired(ConstructionProjectState project, String itemContentId) {
        for (ConstructionMaterialState material : project.materials()) {
            if (material.itemContentId().equals(itemContentId)) {
                return material.remainingAmount();
            }
        }
        return 0;
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

    private static void ensureDelegatedMarker(Entity entity) {
        if (entity.getComponent(DelegatedFleetComponent.class) == null) {
            entity.add(new DelegatedFleetComponent());
        }
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

    private static long saturatedTickAdd(long tick, long duration) {
        return Long.MAX_VALUE - tick < duration ? Long.MAX_VALUE : tick + duration;
    }

    private record SurvivalState(long lastThreatTick, float axisX, float axisY) {
    }

    private record EscapeVector(float axisX, float axisY) {
    }

    private record PatrolState(StarSystemId systemId, long dwellUntilTick) {
    }
}
