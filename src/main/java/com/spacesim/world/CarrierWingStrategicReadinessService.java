package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftMissionState.MissionStatus;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * M22.8H read-only bridge from individual small-craft state into ordinary Stage-21 fleet readiness.
 *
 * <p>The service deliberately owns no wing hit points, ammunition pool or replacement counter.
 * Every projected value is recomputed from the current {@link SmallCraftRegistry}, physical hangar
 * occupancy and mission lifecycle. The returned {@link FleetForceRegistry} keeps the same ordinary
 * {@link FleetId} entries and exact entity payloads while conservatively bounding the carrier fleet's
 * Stage-21 readiness by the physical wing prerequisites.</p>
 */
public final class CarrierWingStrategicReadinessService {
    private final ShipEngineeringCatalog engineering;

    /**
     * Creates the strategic projection over the same production engineering catalog used by the craft.
     *
     * @param engineering accepted Stage-17.5/22 engineering content
     */
    public CarrierWingStrategicReadinessService(ShipEngineeringCatalog engineering) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
    }

    /**
     * Projects carrier-wing state into Stage-21 readiness without mutating any source authority.
     *
     * @param base current ordinary Stage-21 force reconstruction
     * @param craftRegistry individual physical craft authority
     * @param hangars physical embarked occupancy authority
     * @param missions persistent individual mission authority
     * @param identities stable/runtime faction identity authority
     * @param assignments explicit carrier-to-wing association for the current strategic review
     * @return ordinary force registry with carrier readiness conservatively bounded by wing state
     */
    public ProjectionResult project(
            FleetForceRegistry base,
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            SmallCraftMissionState missions,
            FactionIdentityResolver identities,
            Collection<CarrierWingAssignment> assignments) {
        FleetForceRegistry source = Objects.requireNonNull(base, "base");
        SmallCraftRegistry craft = Objects.requireNonNull(craftRegistry, "craftRegistry");
        SmallCraftHangarRegistry bays = Objects.requireNonNull(hangars, "hangars");
        SmallCraftMissionState missionState = Objects.requireNonNull(missions, "missions");
        FactionIdentityResolver factionIdentities = Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(assignments, "assignments");

        TreeMap<SmallCraftId, MissionOrder> activeMissionByCraft = new TreeMap<>();
        for (MissionOrder mission : missionState.missions()) {
            if (!mission.status().active()) {
                continue;
            }
            if (activeMissionByCraft.putIfAbsent(mission.craftId(), mission) != null) {
                throw new IllegalStateException(
                        "duplicate active mission in strategic readiness snapshot: "
                                + mission.craftId());
            }
        }

        TreeMap<FleetId, WingProjection> projections = new TreeMap<>();
        Set<SmallCraftId> globallyBoundCraft = new HashSet<>();
        for (CarrierWingAssignment assignment : assignments) {
            CarrierWingAssignment checked = Objects.requireNonNull(assignment, "assignment");
            if (projections.containsKey(checked.carrierFleetId())) {
                throw new IllegalArgumentException(
                        "carrier FleetId has multiple strategic wing assignments: " + checked.carrierFleetId());
            }
            FleetForceRegistry.Entry carrier = source.find(checked.carrierFleetId())
                    .orElseThrow(() -> new IllegalArgumentException(
                            "carrier FleetId is absent from Stage-21 force registry: " + checked.carrierFleetId()));
            String carrierFaction = factionIdentities.stableId(carrier.factionId())
                    .orElseThrow(() -> new IllegalStateException(
                            "carrier runtime faction has no stable identity: " + carrier.factionId()));
            if (!carrierFaction.equals(checked.stableFactionId())) {
                throw new IllegalArgumentException(
                        "carrier wing stable faction differs from ordinary FleetId allegiance");
            }

            int readyOrActive = 0;
            int lostCraft = 0;
            long structuralTotal = 0L;
            long ammunitionTotal = 0L;
            long propellantTotal = 0L;
            long maintenanceTotal = 0L;
            for (SmallCraftId craftId : checked.craftIds()) {
                if (!globallyBoundCraft.add(craftId)) {
                    throw new IllegalArgumentException(
                            "small craft is assigned to multiple strategic carrier wings: " + craftId);
                }
                SmallCraftState state = craft.find(craftId).orElse(null);
                if (state == null) {
                    lostCraft++;
                    continue;
                }
                if (!checked.stableFactionId().equals(state.stableFactionId())) {
                    throw new IllegalArgumentException(
                            "strategic wing contains craft owned by another faction: " + craftId);
                }
                Optional<SmallCraftHangarRegistry.Assignment> occupancy = bays.find(craftId);
                Optional<MissionOrder> mission =
                        Optional.ofNullable(activeMissionByCraft.get(craftId));
                if (occupancy.isPresent()) {
                    SmallCraftHangarRegistry.Assignment embarked = occupancy.orElseThrow();
                    if (!checked.hostStableId().equals(embarked.bayId().hostStableId())) {
                        throw new IllegalArgumentException(
                                "strategic wing craft occupies another physical host: " + craftId);
                    }
                    if (embarked.state() == OccupancyState.READY
                            || embarked.state() == OccupancyState.LAUNCHING) {
                        readyOrActive++;
                    }
                } else {
                    MissionOrder active = mission.orElseThrow(() -> new IllegalStateException(
                            "deployed strategic wing craft lacks an active physical mission: " + craftId));
                    if (active.status() == MissionStatus.LAUNCH_QUEUED) {
                        throw new IllegalStateException(
                                "launch-queued craft cannot be detached from physical bay occupancy: " + craftId);
                    }
                    if (active.status() == MissionStatus.ACTIVE) {
                        readyOrActive++;
                    }
                }

                structuralTotal += structuralReadinessBps(state);
                ammunitionTotal += interfaceReadinessBps(state, InterfaceKind.AMMUNITION);
                propellantTotal += interfaceReadinessBps(state, InterfaceKind.REACTION_MASS);
                maintenanceTotal += maintenanceReadinessBps(state);
            }

            int wingSize = checked.craftIds().size();
            int availabilityBps = ratioBps(readyOrActive, wingSize);
            FleetReadinessState wingReadiness = new FleetReadinessState(
                    averageBps(structuralTotal, wingSize),
                    averageBps(ammunitionTotal, wingSize),
                    averageBps(propellantTotal, wingSize),
                    FleetReadinessState.FULL,
                    FleetReadinessState.FULL,
                    Math.min(averageBps(maintenanceTotal, wingSize), availabilityBps),
                    FleetReadinessState.FULL);
            FleetReadinessState projectedCarrier = combine(carrier.readiness(), wingReadiness);
            projections.put(checked.carrierFleetId(), new WingProjection(
                    checked.carrierFleetId(),
                    checked.hostStableId(),
                    checked.stableFactionId(),
                    checked.craftIds(),
                    readyOrActive,
                    lostCraft,
                    availabilityBps,
                    wingReadiness,
                    projectedCarrier));
        }

        ArrayList<FleetForceRegistry.Entry> projectedEntries = new ArrayList<>();
        for (FleetForceRegistry.Entry entry : source.entries()) {
            WingProjection wing = projections.get(entry.fleetId());
            projectedEntries.add(wing == null
                    ? entry
                    : new FleetForceRegistry.Entry(
                            entry.fleetId(),
                            entry.factionId(),
                            entry.locationKind(),
                            entry.systemId(),
                            entry.transitOriginSystemId(),
                            entry.transitDestinationSystemId(),
                            entry.entityState(),
                            wing.projectedCarrierReadiness()));
        }
        return new ProjectionResult(
                new FleetForceRegistry(projectedEntries),
                List.copyOf(projections.values()));
    }

    private FleetReadinessState combine(FleetReadinessState carrier, FleetReadinessState wing) {
        return new FleetReadinessState(
                Math.min(carrier.structuralBps(), wing.structuralBps()),
                Math.min(carrier.ammunitionBps(), wing.ammunitionBps()),
                Math.min(carrier.propellantBps(), wing.propellantBps()),
                carrier.crewBps(),
                carrier.sensorsBps(),
                Math.min(carrier.maintenanceBps(), wing.maintenanceBps()),
                carrier.supplyAccessBps());
    }

    private int structuralReadinessBps(SmallCraftState state) {
        double minimum = 1d;
        var hull = engineering.findHull(state.fit().hullId());
        if (hull == null) {
            throw new IllegalStateException("small-craft hull is absent from projection catalog: " + state.fit().hullId());
        }
        for (var compartment : hull.compartments()) {
            minimum = Math.min(minimum,
                    state.instanceState().damage().compartmentIntegrityById()
                            .getOrDefault(compartment.id(), 1d));
        }
        for (InstalledModuleDefinition installed : state.fit().installedModules()) {
            minimum = Math.min(minimum,
                    state.instanceState().damage().moduleDamage().moduleIntegrityByMount()
                            .getOrDefault(installed.mountId(), 1d));
        }
        return fractionBps(minimum);
    }

    private int interfaceReadinessBps(SmallCraftState state, InterfaceKind kind) {
        double capacity = 0d;
        for (InstalledModuleDefinition installed : state.fit().installedModules()) {
            ModuleDefinition module = engineering.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException(
                        "small-craft installed module is absent from projection catalog: " + installed.moduleId());
            }
            for (InterfaceDefinition candidate : module.interfaces()) {
                if (candidate.kind() == kind) {
                    capacity += candidate.capacity();
                }
            }
        }
        if (capacity <= 0d) {
            return FleetReadinessState.FULL;
        }
        double loaded = 0d;
        for (ConsumableLoad load : state.runtimeState().consumables().interfaceLoads()) {
            if (load.kind() == kind) {
                loaded += load.amount();
            }
        }
        return fractionBps(Math.min(1d, loaded / capacity));
    }

    private int maintenanceReadinessBps(SmallCraftState state) {
        double minimumRemaining = 1d;
        Map<String, Double> ages = state.instanceState().maintenance().secondsSinceServiceByMount();
        for (InstalledModuleDefinition installed : state.fit().installedModules()) {
            ModuleDefinition module = engineering.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException(
                        "small-craft installed module is absent from projection catalog: " + installed.moduleId());
            }
            double interval = module.maintenance().serviceIntervalSeconds();
            double age = ages.getOrDefault(installed.mountId(), 0d);
            minimumRemaining = Math.min(minimumRemaining, Math.max(0d, 1d - age / interval));
        }
        return fractionBps(minimumRemaining);
    }

    private static int ratioBps(int numerator, int denominator) {
        if (denominator <= 0 || numerator < 0 || numerator > denominator) {
            throw new IllegalArgumentException("invalid strategic wing readiness ratio");
        }
        return (int) (((long) numerator * FleetReadinessState.FULL) / denominator);
    }

    private static int averageBps(long totalBps, int denominator) {
        if (denominator <= 0 || totalBps < 0L
                || totalBps > (long) denominator * FleetReadinessState.FULL) {
            throw new IllegalArgumentException("invalid strategic wing readiness aggregate");
        }
        return (int) (totalBps / denominator);
    }

    private static int fractionBps(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0d || fraction > 1d) {
            throw new IllegalArgumentException("readiness fraction must be finite and in [0,1]");
        }
        return (int) Math.floor(fraction * FleetReadinessState.FULL + 1.0e-9d);
    }

    /**
     * Explicit association between one ordinary carrier FleetId and the individual craft it currently commands.
     *
     * <p>The association owns no physical state. It is a bounded strategic observation input; every listed
     * craft must resolve either to the named physical host or to an active deployed mission.</p>
     *
     * @param carrierFleetId ordinary Stage-21 fleet identity
     * @param hostStableId stable physical hangar host identity
     * @param stableFactionId stable owning faction identity
     * @param craftIds individual physical craft associated with this carrier group
     */
    public record CarrierWingAssignment(
            FleetId carrierFleetId,
            String hostStableId,
            String stableFactionId,
            List<SmallCraftId> craftIds) {
        /**
         * Validates and canonicalizes one strategic association.
         *
         * @param carrierFleetId ordinary Stage-21 carrier FleetId
         * @param hostStableId stable physical hangar host identity
         * @param stableFactionId stable owning faction identity
         * @param craftIds persistent individual craft associated with the carrier
         */
        public CarrierWingAssignment {
            Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            hostStableId = requireText(hostStableId, "hostStableId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(craftIds, "craftIds");
            TreeSet<SmallCraftId> canonical = new TreeSet<>();
            for (SmallCraftId id : craftIds) {
                if (!canonical.add(Objects.requireNonNull(id, "craftId"))) {
                    throw new IllegalArgumentException("duplicate small craft in carrier wing: " + id);
                }
            }
            if (canonical.isEmpty()) {
                throw new IllegalArgumentException("carrier strategic wing cannot be empty");
            }
            craftIds = List.copyOf(canonical);
        }
    }

    /** One immutable derived wing/readiness diagnostic row. */
    public record WingProjection(
            FleetId carrierFleetId,
            String hostStableId,
            String stableFactionId,
            List<SmallCraftId> craftIds,
            int readyOrActiveCraft,
            int lostCraft,
            int availabilityBps,
            FleetReadinessState wingReadiness,
            FleetReadinessState projectedCarrierReadiness) {
        /**
         * Validates immutable diagnostics.
         *
         * @param carrierFleetId ordinary Stage-21 carrier FleetId
         * @param hostStableId stable physical hangar host identity
         * @param stableFactionId stable owning faction identity
         * @param craftIds persistent carrier-wing roster
         * @param readyOrActiveCraft physically ready or lawfully active craft count
         * @param lostCraft roster craft no longer present in the physical registry
         * @param availabilityBps surviving availability in basis points
         * @param wingReadiness readiness derived only from persistent small-craft state
         * @param projectedCarrierReadiness ordinary carrier readiness with wing constraint applied
         */
        public WingProjection {
            Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            hostStableId = requireText(hostStableId, "hostStableId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            craftIds = List.copyOf(Objects.requireNonNull(craftIds, "craftIds"));
            if (craftIds.isEmpty()
                    || readyOrActiveCraft < 0
                    || lostCraft < 0
                    || readyOrActiveCraft + lostCraft > craftIds.size()) {
                throw new IllegalArgumentException("invalid strategic wing counts");
            }
            if (availabilityBps < 0 || availabilityBps > FleetReadinessState.FULL) {
                throw new IllegalArgumentException("availabilityBps must be in 0..10000");
            }
            Objects.requireNonNull(wingReadiness, "wingReadiness");
            Objects.requireNonNull(projectedCarrierReadiness, "projectedCarrierReadiness");
        }
    }

    /** Complete read-only M22.8H projection result. */
    public record ProjectionResult(
            FleetForceRegistry forces,
            List<WingProjection> wings) {
        /**
         * Validates and freezes the result.
         *
         * @param forces ordinary fleet-force registry with projected carrier readiness
         * @param wings immutable derived carrier-wing diagnostics
         */
        public ProjectionResult {
            Objects.requireNonNull(forces, "forces");
            wings = List.copyOf(Objects.requireNonNull(wings, "wings"));
        }

        /**
         * Resolves the wing projected for one ordinary carrier fleet.
         *
         * @param carrierFleetId ordinary carrier FleetId
         * @return derived wing projection when the carrier has an explicit association
         */
        public Optional<WingProjection> wing(FleetId carrierFleetId) {
            FleetId checked = Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            return wings.stream().filter(value -> value.carrierFleetId().equals(checked)).findFirst();
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
