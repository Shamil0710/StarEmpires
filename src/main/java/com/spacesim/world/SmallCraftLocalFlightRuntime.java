package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.systems.PlayerDirectControlSystem;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftLocalFlightState.Flight;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Physical handoff and exact coasting for existing individual craft on completed world intervals. */
public final class SmallCraftLocalFlightRuntime {
    private final SmallCraftRegistry craft;
    private final SmallCraftHangarRegistry hangars;
    private final SmallCraftFlightDeckOperations deck;
    private final SmallCraftMissionCommandService commands;
    private final PlayerDirectControlSystem movement = new PlayerDirectControlSystem();
    private final TreeMap<SmallCraftId, Flight> flights = new TreeMap<>();
    private long lastProcessedTick;

    /**
     * Restores physical values; no mission, asset or position is inferred for a legacy deployment.
     * @param craft actual individual registry
     * @param hangars actual physical occupancy
     * @param deck actual finite deck work
     * @param catalog craft command-validation vocabulary
     * @param missions actual existing mission authority
     * @param saved exact local-flight state
     */
    public SmallCraftLocalFlightRuntime(SmallCraftRegistry craft, SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations deck, ShipEngineeringCatalog catalog, SmallCraftMissionState missions,
            SmallCraftLocalFlightState saved) {
        this.craft = Objects.requireNonNull(craft); this.hangars = Objects.requireNonNull(hangars);
        this.deck = Objects.requireNonNull(deck);
        Objects.requireNonNull(catalog);
        if (!craft.engineeringCatalogFingerprint().equals(catalog.getFingerprint()))
            throw new IllegalArgumentException("Local flight and craft registry require the same accepted engineering vocabulary");
        commands = new SmallCraftMissionCommandService(craft, hangars, deck, catalog);
        Objects.requireNonNull(saved); Objects.requireNonNull(missions); lastProcessedTick = saved.lastProcessedTick();
        for (var flight : saved.flights()) {
            if (craft.find(flight.craftId()).isEmpty() || hangars.find(flight.craftId()).isPresent())
                throw new IllegalArgumentException("Local flight requires an existing deployed craft");
            validateMission(flight, missions, lastProcessedTick);
            flights.put(flight.craftId(), flight);
        }
    }

    /**
     * One current actual launch aperture supplied by physical world integration, never by UI input.
     * @param bay installed physical bay
     * @param systemId current host system
     * @param stableFactionId actual legal host operator
     * @param aperture actual launch mouth kinematics after the completed host tick
     * @param observedTick exact world interval of the host projection
     */
    public record LaunchAperture(BayDefinition bay, StarSystemId systemId, String stableFactionId,
            LocalPhysicalKinematics aperture, long observedTick) {
        /**
         * @param bay actual installed bay
         * @param systemId actual host system
         * @param stableFactionId actual legal operator
         * @param aperture physical launch-mouth state
         * @param observedTick completed world tick
         */
        public LaunchAperture {
            Objects.requireNonNull(bay); Objects.requireNonNull(systemId); Objects.requireNonNull(aperture);
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            if (observedTick < 0) throw new IllegalArgumentException("Negative aperture tick");
        }
    }

    /** @return pure exact physical snapshot; no simulation step or asset creation */
    public SmallCraftLocalFlightState capture() { return new SmallCraftLocalFlightState(lastProcessedTick, new ArrayList<>(flights.values())); }

    /** @return whether this physical domain owns any currently deployed position */
    public boolean hasFlights() { return !flights.isEmpty(); }

    /**
     * Removes positions only after the physical registry has committed a loss, without elapsed time.
     * @param missions actual mission authority after the physical consequence
     * @return reconciled terminal loss statuses; no replacement, motion or allocator change
     */
    public SmallCraftMissionState reconcileCommittedLosses(SmallCraftMissionState missions) {
        Objects.requireNonNull(missions);
        var lost = flights.keySet().stream().filter(id -> craft.find(id).isEmpty()).toList();
        for (var id : lost) {
            var mission = missions.activeMissionFor(id);
            if (mission.isPresent()) missions = missions.replace(mission.orElseThrow().withStatus(SmallCraftMissionState.MissionStatus.FAILED));
            flights.remove(id);
        }
        return missions;
    }

    /**
     * Advances old local flights once, then hands off completed launches at their actual aperture.
     * Missing/currently unusable apertures keep LAUNCHING occupancy and pending work intact.
     * @param tick one completed world tick, strictly after the physical watermark
     * @param seconds exact positive fixed interval
     * @param missions actual existing missions
     * @param apertures current physical world projections
     * @return existing mission authority with only lawful launch status changes
     */
    public SmallCraftMissionState advanceFixedTick(long tick, double seconds, SmallCraftMissionState missions,
            Map<BayId, LaunchAperture> apertures) {
        if (tick <= lastProcessedTick || !Double.isFinite(seconds) || seconds <= 0)
            throw new IllegalArgumentException("Invalid or repeated local-flight interval");
        if (!flights.isEmpty() && tick - lastProcessedTick != 1)
            throw new IllegalArgumentException("A deployed craft cannot skip physical world intervals");
        Objects.requireNonNull(missions); Objects.requireNonNull(apertures);
        if (flights.isEmpty() && apertures.isEmpty()) {
            missions = completeRecoveredMissions(missions, tick);
            lastProcessedTick = tick;
            return missions;
        }
        var next = new TreeMap<SmallCraftId, Flight>();
        var engineering = new TreeMap<SmallCraftId, EngineeringComponent>();
        var lost = new ArrayList<SmallCraftId>();
        for (var flight : flights.values()) {
            var current = craft.find(flight.craftId()).orElse(null);
            if (current == null) {
                lost.add(flight.craftId());
                continue; // A committed physical loss cannot retain a moving ghost.
            }
            validateMission(flight, missions, tick);
            var occupancy = hangars.find(flight.craftId());
            if (occupancy.isPresent()) {
                var mission = missions.activeMissionFor(flight.craftId()).orElseThrow();
                var state = occupancy.orElseThrow().state();
                if (mission.status() != SmallCraftMissionState.MissionStatus.RECOVERY_PENDING
                        || deck.lastProcessedTick() != tick
                        || (state != SmallCraftHangarCapacity.OccupancyState.RECOVERING
                            && state != SmallCraftHangarCapacity.OccupancyState.SERVICING))
                    throw new IllegalStateException("Local flight and mission/occupancy disagree");
                continue; // The existing physical deck owner has taken the same craft into its bay.
            }
            var component = SmallCraftEngineeringMaterializationBridge.materialize(current);
            var physical = movement.advanceExact(new Entity().add(component), flight.physical(), 0, 0, false, seconds);
            next.put(flight.craftId(), new Flight(flight.craftId(), flight.systemId(), flight.hostStableId(),
                    flight.missionId(), physical, flight.deployedTick()));
            engineering.put(flight.craftId(), component);
        }
        var launches = new ArrayList<Flight>();
        for (var operation : deck.active()) {
            if (operation.phase() != SmallCraftFlightDeckOperations.OperationPhase.AWAITING_HANDOFF) continue;
            var aperture = apertures.get(operation.request().bayId());
            if (aperture == null || aperture.bay().conditionFraction() <= 0) continue;
            if (deck.lastProcessedTick() != tick || aperture.observedTick() != tick
                    || !aperture.bay().id().equals(operation.request().bayId()))
                throw new IllegalArgumentException("Stale or mismatched physical launch aperture");
            var mission = missions.activeMissionFor(operation.request().craftId()).orElseThrow();
            var individual = craft.find(mission.craftId()).orElseThrow();
            if (mission.status() != SmallCraftMissionState.MissionStatus.LAUNCH_QUEUED
                    || !individual.stableFactionId().equals(aperture.stableFactionId())
                    || hangars.find(mission.craftId()).filter(a -> a.bayId().equals(aperture.bay().id())
                        && a.state() == SmallCraftHangarCapacity.OccupancyState.LAUNCHING).isEmpty()
                    || next.containsKey(mission.craftId()))
                throw new IllegalStateException("Launch mission/ownership/occupancy disagree");
            launches.add(new Flight(mission.craftId(), aperture.systemId(), aperture.bay().id().hostStableId(),
                    mission.id(), aperture.aperture(), tick));
        }
        if ((long) next.size() + launches.size() > SmallCraftLocalFlightState.MAX_FLIGHTS)
            throw new IllegalStateException("Local-flight roster exceeds physical bound");
        missions = completeRecoveredMissions(missions, tick);
        // All physical calculations and aperture checks finish before mutating registry/deck owners.
        engineering.forEach(craft::commitMaterializedEngineering);
        for (var id : lost) {
            var mission = missions.activeMissionFor(id);
            if (mission.isPresent()) missions = missions.replace(mission.orElseThrow().withStatus(SmallCraftMissionState.MissionStatus.FAILED));
        }
        for (var launch : launches) {
            missions = commands.confirmPhysicalLaunch(missions, launch.missionId());
            next.put(launch.craftId(), launch);
        }
        flights.clear(); flights.putAll(next); lastProcessedTick = tick;
        return missions;
    }

    private void validateMission(Flight flight, SmallCraftMissionState missions, long tick) {
        var launch = missions.requireMission(flight.missionId());
        var current = missions.activeMissionFor(flight.craftId()).orElseThrow();
        if (!launch.craftId().equals(flight.craftId()) || launch.submittedTick() > flight.deployedTick()
                || current.submittedTick() > tick || (current.status() != SmallCraftMissionState.MissionStatus.ACTIVE
                    && current.status() != SmallCraftMissionState.MissionStatus.RETURNING
                    && current.status() != SmallCraftMissionState.MissionStatus.RECOVERY_PENDING))
            throw new IllegalArgumentException("Local flight lacks its physical launch and current mission");
        if (current.status() == SmallCraftMissionState.MissionStatus.RECOVERY_PENDING
                && hangars.find(flight.craftId()).isEmpty()
                && deck.queued().stream().noneMatch(r -> r.craftId().equals(flight.craftId())
                    && r.kind() == SmallCraftFlightDeckOperations.OperationKind.RECOVERY))
            throw new IllegalArgumentException("Pending local recovery lacks its physical deck request");
    }

    private SmallCraftMissionState completeRecoveredMissions(SmallCraftMissionState missions, long tick) {
        if (deck.lastProcessedTick() != tick) return missions;
        for (var mission : missions.missions()) {
            if (mission.status() == SmallCraftMissionState.MissionStatus.RECOVERY_PENDING
                    && hangars.find(mission.craftId()).filter(a -> a.state() == SmallCraftHangarCapacity.OccupancyState.SERVICING).isPresent()
                    && deck.activeFor(mission.craftId()).isEmpty())
                missions = commands.completePhysicalRecovery(missions, mission.id());
        }
        return missions;
    }
}
