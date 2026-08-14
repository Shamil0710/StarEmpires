package com.spacesim.player;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.MiningComponent;
import com.spacesim.components.PlayerControlledComponent;
import com.spacesim.components.TradeAIComponent;
import com.spacesim.components.TransformComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.systems.PlayerDirectControlSystem;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.StarSystemNode;
import com.spacesim.world.WorldSimulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Runtime owner of the playable layer above an otherwise independent WorldSimulation.
 *
 * <p>Stage-12C installs a transient fixed-tick direct-control system into local sessions. Input
 * changes only {@link PlayerControlledComponent}; physical Transform mutation remains inside the
 * simulation update. The active player fleet's system is kept as the world's full-rate active
 * system, while jump travel delegates to the existing Stage-10 jump FSM.</p>
 */
public final class PlayerRuntime {
    private static final float DEFAULT_DOCKING_RANGE = 10f;

    private final WorldSimulation world;
    private final ContentCatalog content;
    private PlayerState player;

    private PlayerRuntime(WorldSimulation world, ContentCatalog content, PlayerState player) {
        this.world = Objects.requireNonNull(world, "WorldSimulation not set");
        this.content = Objects.requireNonNull(content, "ContentCatalog not set");
        this.player = Objects.requireNonNull(player, "PlayerState not set");
        validateReferences(this.world, this.content, this.player);
        installDirectControlSystems();
        synchronizePlayerLocationAndControl();
    }

    /**
     * Initializes a playable layer around an existing world runtime.
     *
     * @param world current authoritative world
     * @param content semantic content catalog used by that world
     * @param player initial persistent player state
     * @return playable runtime using the same world instance
     */
    public static PlayerRuntime create(
            WorldSimulation world,
            ContentCatalog content,
            PlayerState player) {
        return new PlayerRuntime(world, content, player);
    }

    /**
     * Restores a playable runtime from one atomic save snapshot.
     *
     * @param state playable save state with an initialized player
     * @param content semantic content catalog
     * @param activeSystemId StarSystem requested at full local rate before active-ship reconciliation
     * @return restored playable runtime
     * @throws IllegalStateException if the save is a migrated pre-player world
     */
    public static PlayerRuntime restore(
            PlayableWorldState state,
            ContentCatalog content,
            StarSystemId activeSystemId) {
        PlayableWorldState checked = Objects.requireNonNull(state, "PlayableWorldState not set");
        if (checked.playerState() == null) {
            throw new IllegalStateException("Legacy world has no initialized PlayerState");
        }
        WorldSimulation restoredWorld = WorldSimulation.restore(
                checked.worldState(),
                Objects.requireNonNull(content, "ContentCatalog not set"),
                Objects.requireNonNull(activeSystemId, "Active StarSystemId not set"),
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        return new PlayerRuntime(restoredWorld, content, checked.playerState());
    }

    /** @return current authoritative world runtime */
    public WorldSimulation world() {
        return world;
    }

    /** @return current immutable player state after ownership/docking reconciliation */
    public PlayerState player() {
        reconcileOwnedFleets();
        reconcileDocking();
        return player;
    }

    /**
     * Advances the unchanged fixed-tick world pipeline and follows the active fleet after travel.
     *
     * @param realDeltaSeconds render/frame delta
     * @return ordinary WorldSimulation advance report
     */
    public WorldSimulation.AdvanceReport advanceFrame(float realDeltaSeconds) {
        synchronizeDirectControlBinding();
        WorldSimulation.AdvanceReport report = world.advanceFrame(realDeltaSeconds);
        reconcileOwnedFleets();
        reconcileDocking();
        synchronizePlayerLocationAndControl();
        return report;
    }

    /** @return atomic playable snapshot after ownership/docking reconciliation */
    public PlayableWorldState snapshot() {
        reconcileOwnedFleets();
        reconcileDocking();
        return new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                world.snapshot(),
                player);
    }

    /**
     * Sets normalized direct movement intent without mutating Transform immediately.
     *
     * @param axisX finite horizontal input
     * @param axisY finite vertical input
     * @return true when an undocked local active ship accepted the intent
     */
    public boolean setMovementIntent(float axisX, float axisY) {
        if (player.docked() || player.activeFleetId() == null
                || world.findFleetJump(player.activeFleetId()).isPresent()) {
            return false;
        }
        ActiveShip active = activeLocalShip().orElse(null);
        if (active == null) {
            return false;
        }
        PlayerControlledComponent control = ensureControl(active.entity());
        if (control.movementSpeed <= 0f) {
            return false;
        }
        control.setIntent(axisX, axisY);
        return true;
    }

    /** Stops direct movement intent without changing physical position outside a fixed tick. */
    public void stopMovement() {
        activeLocalShip().ifPresent(active -> ensureControl(active.entity()).stop());
    }

    /**
     * Docks at a live market only when the active ship is already inside physical docking range.
     *
     * @param stationId system-local persistent market EntityId
     * @return true when docking state was established
     */
    public boolean dockAt(EntityId stationId) {
        EntityId checkedStationId = Objects.requireNonNull(stationId, "Docking station ID not set");
        if (player.activeFleetId() == null || world.findFleetJump(player.activeFleetId()).isPresent()) {
            return false;
        }
        ActiveShip active = activeLocalShip().orElse(null);
        if (active == null) {
            return false;
        }
        Entity station = active.session().getEntityRegistry().find(checkedStationId);
        TransformComponent stationTransform = station == null
                ? null : station.getComponent(TransformComponent.class);
        if (station == null || station.getComponent(MarketComponent.class) == null || stationTransform == null) {
            return false;
        }
        float range = dockingRange(active.entity());
        if (active.transform().position.dst2(stationTransform.position) > range * range) {
            return false;
        }

        DiscoveredObjectRef reference = new DiscoveredObjectRef(active.placement().systemId(), checkedStationId);
        List<StarSystemId> systems = withSystem(player.discoveredSystemIds(), active.placement().systemId());
        List<DiscoveredObjectRef> objects = withObject(player.discoveredObjects(), reference);
        player = copyPlayer(player, player.walletMilliCredits(), player.ownedFleetIds(),
                player.activeFleetId(), systems, objects, reference);
        PlayerControlledComponent control = ensureControl(active.entity());
        control.stop();
        control.docked = true;
        active.transform().velocity.setZero();
        return true;
    }

    /** @return true when an existing persistent docking state was cleared */
    public boolean undock() {
        if (!player.docked()) {
            return false;
        }
        player = copyPlayer(player, player.walletMilliCredits(), player.ownedFleetIds(),
                player.activeFleetId(), player.discoveredSystemIds(), player.discoveredObjects(), null);
        activeLocalShip().ifPresent(active -> {
            PlayerControlledComponent control = ensureControl(active.entity());
            control.docked = false;
            control.stop();
        });
        return true;
    }

    /**
     * Requests one direct Stage-10 jump for the active player fleet.
     *
     * @param destination directly connected destination system
     * @return true when the existing authoritative jump FSM accepted a new request
     */
    public boolean requestJump(StarSystemId destination) {
        StarSystemId target = Objects.requireNonNull(destination, "Jump destination not set");
        FleetId fleetId = player.activeFleetId();
        if (fleetId == null || player.docked() || world.findFleetJump(fleetId).isPresent()) {
            return false;
        }
        FleetPlacementState placement = world.findFleet(fleetId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !world.getTopology().neighbors(placement.systemId()).contains(target)) {
            return false;
        }
        stopMovement();
        world.requestFleetJump(fleetId, target, 0f, 0f);
        return true;
    }

    /**
     * Sets global pause across all local sessions without bypassing their fixed clocks.
     *
     * @param paused new global pause state
     */
    public void setPaused(boolean paused) {
        for (StarSystemNode node : world.getTopology().systems()) {
            world.findSession(node.id()).orElseThrow().getClock().setPaused(paused);
        }
    }

    /**
     * Sets global simulation time scale across all local sessions.
     *
     * @param timeScale finite non-negative multiplier
     */
    public void setTimeScale(double timeScale) {
        for (StarSystemNode node : world.getTopology().systems()) {
            world.findSession(node.id()).orElseThrow().getClock().setTimeScale(timeScale);
        }
    }

    /** @return pause state of the current full-rate system */
    public boolean isPaused() {
        return world.findSession(world.getActiveSystemId()).orElseThrow().getClock().isPaused();
    }

    /** @return time scale of the current full-rate system */
    public double getTimeScale() {
        return world.findSession(world.getActiveSystemId()).orElseThrow().getClock().getTimeScale();
    }

    /**
     * Resolves a read-only active-ship snapshot suitable for camera follow/selection and HUD.
     *
     * @return current local active ship view, or empty while no active ship is locally materialized
     */
    public Optional<PlayerShipView> activeShipView() {
        reconcileOwnedFleets();
        ActiveShip active = activeLocalShip().orElse(null);
        if (active == null || player.activeFleetId() == null) {
            return Optional.empty();
        }
        return Optional.of(new PlayerShipView(
                player.activeFleetId(),
                active.placement().systemId(),
                active.placement().localEntityId(),
                active.transform().position.x,
                active.transform().position.y,
                active.transform().velocity.x,
                active.transform().velocity.y,
                player.docked()));
    }

    void replacePlayerState(PlayerState replacement) {
        PlayerState checked = Objects.requireNonNull(replacement, "Replacement PlayerState not set");
        validateReferences(world, content, checked);
        player = checked;
        synchronizePlayerLocationAndControl();
    }

    private void installDirectControlSystems() {
        for (StarSystemNode node : world.getTopology().systems()) {
            SimulationSession session = world.findSession(node.id()).orElseThrow();
            if (session.getEngine().getSystem(PlayerDirectControlSystem.class) == null) {
                session.getEngine().addSystem(new PlayerDirectControlSystem());
            }
        }
    }

    private void synchronizePlayerLocationAndControl() {
        FleetId activeId = player.activeFleetId();
        if (activeId != null) {
            FleetPlacementState placement = world.findFleet(activeId).orElse(null);
            if (placement != null && placement.locationKind() == FleetLocationKind.IN_SYSTEM) {
                world.activateSystem(placement.systemId());
                if (!player.discoveredSystemIds().contains(placement.systemId())) {
                    player = copyPlayer(player, player.walletMilliCredits(), player.ownedFleetIds(), activeId,
                            withSystem(player.discoveredSystemIds(), placement.systemId()),
                            player.discoveredObjects(), null);
                }
            }
        }
        synchronizeDirectControlBinding();
    }

    private void synchronizeDirectControlBinding() {
        FleetId activeId = player.activeFleetId();
        for (StarSystemNode node : world.getTopology().systems()) {
            SimulationSession session = world.findSession(node.id()).orElseThrow();
            for (Entity entity : session.getEngine().getEntities()) {
                PlayerControlledComponent existing = entity.getComponent(PlayerControlledComponent.class);
                FleetId fleetId = null;
                var localId = entity.getComponent(com.spacesim.components.EntityIdComponent.class);
                if (localId != null) {
                    fleetId = world.findFleetByLocal(node.id(), localId.id).orElse(null);
                }
                if (activeId != null && activeId.equals(fleetId)) {
                    PlayerControlledComponent control = existing == null
                            ? ensureControl(entity) : existing;
                    control.docked = player.docked();
                    suppressAutonomousBehavior(entity);
                } else if (existing != null) {
                    entity.remove(PlayerControlledComponent.class);
                }
            }
        }
    }

    private PlayerControlledComponent ensureControl(Entity entity) {
        PlayerControlledComponent control = entity.getComponent(PlayerControlledComponent.class);
        if (control == null) {
            control = new PlayerControlledComponent();
            control.movementSpeed = movementSpeed(entity);
            entity.add(control);
        }
        return control;
    }

    private static void suppressAutonomousBehavior(Entity entity) {
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

    private static float movementSpeed(Entity entity) {
        TradeAIComponent trade = entity.getComponent(TradeAIComponent.class);
        if (trade != null && Float.isFinite(trade.movementSpeed) && trade.movementSpeed > 0f) {
            return trade.movementSpeed;
        }
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null && Float.isFinite(mining.movementSpeed) && mining.movementSpeed > 0f) {
            return mining.movementSpeed;
        }
        return 0f;
    }

    private static float dockingRange(Entity entity) {
        MiningComponent mining = entity.getComponent(MiningComponent.class);
        if (mining != null && Float.isFinite(mining.dockingRange) && mining.dockingRange > 0f) {
            return mining.dockingRange;
        }
        return DEFAULT_DOCKING_RANGE;
    }

    private Optional<ActiveShip> activeLocalShip() {
        FleetId activeId = player.activeFleetId();
        if (activeId == null) {
            return Optional.empty();
        }
        FleetPlacementState placement = world.findFleet(activeId).orElse(null);
        if (placement == null || placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return Optional.empty();
        }
        SimulationSession session = world.findSession(placement.systemId()).orElseThrow();
        Entity entity = session.getEntityRegistry().find(placement.localEntityId());
        TransformComponent transform = entity == null ? null : entity.getComponent(TransformComponent.class);
        return entity == null || transform == null
                ? Optional.empty() : Optional.of(new ActiveShip(placement, session, entity, transform));
    }

    private void reconcileOwnedFleets() {
        List<FleetId> survivors = new ArrayList<>();
        for (FleetId fleetId : player.ownedFleetIds()) {
            if (world.findFleet(fleetId).isPresent()) {
                survivors.add(fleetId);
            }
        }
        if (survivors.size() == player.ownedFleetIds().size()) {
            return;
        }
        FleetId active = player.activeFleetId();
        if (active != null && !survivors.contains(active)) {
            active = survivors.isEmpty() ? null : survivors.get(0);
        }
        player = copyPlayer(player, player.walletMilliCredits(), survivors, active,
                player.discoveredSystemIds(), player.discoveredObjects(), null);
    }

    private void reconcileDocking() {
        DiscoveredObjectRef docked = player.dockedAt();
        if (docked == null) {
            return;
        }
        FleetPlacementState placement = player.activeFleetId() == null
                ? null : world.findFleet(player.activeFleetId()).orElse(null);
        SimulationSession session = world.findSession(docked.systemId()).orElse(null);
        Entity station = session == null ? null : session.getEntityRegistry().find(docked.entityId());
        if (placement == null
                || placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !docked.systemId().equals(placement.systemId())
                || station == null
                || station.getComponent(MarketComponent.class) == null) {
            player = copyPlayer(player, player.walletMilliCredits(), player.ownedFleetIds(),
                    player.activeFleetId(), player.discoveredSystemIds(), player.discoveredObjects(), null);
        }
    }

    static PlayerState copyWithOwnershipAndWallet(
            PlayerState source,
            long walletMilliCredits,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId) {
        DiscoveredObjectRef docked = activeFleetId == null ? null : source.dockedAt();
        return copyPlayer(source, walletMilliCredits, ownedFleetIds, activeFleetId,
                source.discoveredSystemIds(), source.discoveredObjects(), docked);
    }

    private static PlayerState copyPlayer(
            PlayerState source,
            long walletMilliCredits,
            List<FleetId> ownedFleetIds,
            FleetId activeFleetId,
            List<StarSystemId> discoveredSystems,
            List<DiscoveredObjectRef> discoveredObjects,
            DiscoveredObjectRef dockedAt) {
        return new PlayerState(
                walletMilliCredits,
                source.factionContentId(),
                source.reputations(),
                ownedFleetIds,
                activeFleetId,
                discoveredSystems,
                discoveredObjects,
                source.homeSystemId(),
                dockedAt);
    }

    private static List<StarSystemId> withSystem(List<StarSystemId> current, StarSystemId added) {
        if (current.contains(added)) {
            return current;
        }
        List<StarSystemId> result = new ArrayList<>(current);
        result.add(added);
        return result;
    }

    private static List<DiscoveredObjectRef> withObject(
            List<DiscoveredObjectRef> current,
            DiscoveredObjectRef added) {
        if (current.contains(added)) {
            return current;
        }
        List<DiscoveredObjectRef> result = new ArrayList<>(current);
        result.add(added);
        return result;
    }

    private static void validateReferences(
            WorldSimulation world,
            ContentCatalog content,
            PlayerState player) {
        if (player.factionContentId() != null && content.findFaction(player.factionContentId()) == null) {
            throw new IllegalArgumentException("Player affiliation references unknown faction: "
                    + player.factionContentId());
        }
        for (PlayerReputationState reputation : player.reputations()) {
            if (content.findFaction(reputation.factionContentId()) == null) {
                throw new IllegalArgumentException("Player reputation references unknown faction: "
                        + reputation.factionContentId());
            }
        }
        for (FleetId fleetId : player.ownedFleetIds()) {
            if (world.findFleet(fleetId).isEmpty()) {
                throw new IllegalArgumentException("Player owns unknown FleetId: " + fleetId);
            }
        }
        for (StarSystemId systemId : player.discoveredSystemIds()) {
            if (world.getTopology().findSystem(systemId).isEmpty()) {
                throw new IllegalArgumentException("Player discovered unknown StarSystem: " + systemId);
            }
        }
        for (DiscoveredObjectRef reference : player.discoveredObjects()) {
            if (world.getTopology().findSystem(reference.systemId()).isEmpty()) {
                throw new IllegalArgumentException("Player discovery references unknown StarSystem: "
                        + reference.systemId());
            }
        }
        if (player.homeSystemId() != null
                && world.getTopology().findSystem(player.homeSystemId()).isEmpty()) {
            throw new IllegalArgumentException("Player home references unknown StarSystem: "
                    + player.homeSystemId());
        }
        if (player.dockedAt() != null) {
            SimulationSession session = world.findSession(player.dockedAt().systemId()).orElse(null);
            Entity station = session == null ? null : session.getEntityRegistry().find(player.dockedAt().entityId());
            if (station == null || station.getComponent(MarketComponent.class) == null) {
                throw new IllegalArgumentException("Player dockedAt references a non-live market");
            }
        }
    }

    private record ActiveShip(
            FleetPlacementState placement,
            SimulationSession session,
            Entity entity,
            TransformComponent transform) {
    }
}
