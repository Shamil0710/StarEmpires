package com.spacesim.ui;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.world.CarrierWingStrategicReadinessService.CarrierWingAssignment;
import com.spacesim.world.FleetId;
import com.spacesim.world.SmallCraftFlightDeckOperations;
import com.spacesim.world.SmallCraftFlightDeckOperations.ActiveOperation;
import com.spacesim.world.SmallCraftFlightDeckOperations.Request;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.CapacityStatus;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarCapacity.Usage;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftMissionState;
import com.spacesim.world.SmallCraftMissionState.MissionOrder;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;
import com.spacesim.world.FleetReadinessState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * M22.8I read-only player-facing projection of one carrier and its persistent individual craft.
 *
 * <p>The projection never owns or mutates craft, bay, deck or mission state. Every displayed value
 * is recomputed from the accepted A/B/C/D authorities plus current physical bay definitions. Target
 * references pass through an explicit visibility policy so presentation cannot expose hidden world
 * knowledge merely because a mission object exists.</p>
 */
public final class CarrierOperationsUiProjection {
    private final ShipEngineeringCatalog engineering;

    /**
     * Creates the UI projector over the same production engineering catalog used by the craft.
     *
     * @param engineering accepted production engineering catalog
     */
    public CarrierOperationsUiProjection(ShipEngineeringCatalog engineering) {
        this.engineering = Objects.requireNonNull(engineering, "engineering");
    }

    /**
     * Captures one immutable carrier-operations view.
     *
     * @param assignment explicit H carrier-to-wing association
     * @param craftRegistry authoritative individual-craft registry
     * @param hangars authoritative physical bay occupancy
     * @param flightDeck authoritative launch/recovery queue and active work
     * @param missions current individual mission state
     * @param bayDefinitions current physical bay projections keyed by exact identity
     * @param targetVisibility actor-bounded target-visibility policy
     * @return immutable read-only carrier view
     */
    public CarrierView capture(
            CarrierWingAssignment assignment,
            SmallCraftRegistry craftRegistry,
            SmallCraftHangarRegistry hangars,
            SmallCraftFlightDeckOperations flightDeck,
            SmallCraftMissionState missions,
            Map<BayId, BayDefinition> bayDefinitions,
            MissionTargetVisibility targetVisibility) {
        CarrierWingAssignment wing = Objects.requireNonNull(assignment, "assignment");
        SmallCraftRegistry craft = Objects.requireNonNull(craftRegistry, "craftRegistry");
        SmallCraftHangarRegistry bays = Objects.requireNonNull(hangars, "hangars");
        SmallCraftFlightDeckOperations deck = Objects.requireNonNull(flightDeck, "flightDeck");
        SmallCraftMissionState missionState = Objects.requireNonNull(missions, "missions");
        Map<BayId, BayDefinition> physicalBays =
                canonicalBays(Objects.requireNonNull(bayDefinitions, "bayDefinitions"));
        MissionTargetVisibility visibility =
                Objects.requireNonNull(targetVisibility, "targetVisibility");

        ArrayList<BayView> bayViews = new ArrayList<>();
        for (BayDefinition bay : physicalBays.values()) {
            if (!bay.id().hostStableId().equals(wing.hostStableId())) {
                continue;
            }
            Usage usage = bays.usage(bay.id());
            int queued = (int) deck.queued().stream()
                    .filter(value -> value.bayId().equals(bay.id()))
                    .count();
            Optional<ActiveOperation> active = deck.active().stream()
                    .filter(value -> value.request().bayId().equals(bay.id()))
                    .findFirst();
            bayViews.add(new BayView(
                    bay.id(),
                    bay.hostKind().name(),
                    bay.conditionFraction(),
                    usage.craftCount(),
                    usage.occupiedMassKg(),
                    bay.effectiveSupportedMassKg(),
                    usage.occupiedEnvelopeVolumeM3(),
                    bay.effectiveUsableVolumeM3(),
                    bays.capacityStatus(bay),
                    queued,
                    active.map(value -> value.request().kind().name()).orElse(""),
                    active.map(value -> value.phase().name()).orElse(""),
                    active.map(ActiveOperation::remainingWorkSeconds).orElse(0d),
                    active.map(value -> value.failureKind() == null
                            ? "" : value.failureKind().name()).orElse("")));
        }

        ArrayList<CraftView> craftViews = new ArrayList<>();
        ArrayList<SmallCraftId> lost = new ArrayList<>();
        for (SmallCraftId id : wing.craftIds()) {
            SmallCraftState state = craft.find(id).orElse(null);
            if (state == null) {
                lost.add(id);
                continue;
            }
            if (!wing.stableFactionId().equals(state.stableFactionId())) {
                throw new IllegalArgumentException(
                        "carrier UI wing contains craft owned by another faction: " + id);
            }
            Optional<SmallCraftHangarRegistry.Assignment> occupancy = bays.find(id);
            occupancy.ifPresent(value -> {
                if (!value.bayId().hostStableId().equals(wing.hostStableId())) {
                    throw new IllegalArgumentException(
                            "carrier UI wing craft occupies another physical host: " + id);
                }
            });
            Optional<MissionOrder> mission = missionState.activeMissionFor(id);
            Optional<Request> queued = deck.queued().stream()
                    .filter(value -> value.craftId().equals(id))
                    .findFirst();
            Optional<ActiveOperation> active = deck.activeFor(id);

            int structure = structuralReadinessBps(state);
            ResourceReadiness ammunition = resourceReadiness(state, InterfaceKind.AMMUNITION);
            ResourceReadiness propellant = resourceReadiness(state, InterfaceKind.REACTION_MASS);
            int maintenance = maintenanceReadinessBps(state);
            boolean repairRequired = structure < FleetReadinessState.FULL;
            boolean serviceRequired = maintenance < FleetReadinessState.FULL;
            MissionView missionView = mission.map(value -> missionView(value, visibility)).orElse(null);
            craftViews.add(new CraftView(
                    id,
                    state.designId(),
                    state.fit().hullId(),
                    state.stableFactionId(),
                    operationalState(occupancy, mission, queued, active, repairRequired),
                    occupancy.map(value -> value.state().name()).orElse(""),
                    occupancy.map(value -> value.bayId().bayStableId()).orElse(""),
                    structure,
                    ammunition,
                    propellant,
                    maintenance,
                    repairRequired,
                    serviceRequired,
                    missionView,
                    queued.map(value -> value.kind().name()).orElse(""),
                    active.map(value -> value.request().kind().name()).orElse(""),
                    active.map(value -> value.phase().name()).orElse(""),
                    active.map(ActiveOperation::remainingWorkSeconds).orElse(0d),
                    active.map(value -> value.failureKind() == null
                            ? "" : value.failureKind().name()).orElse("")));
        }

        return new CarrierView(
                wing.carrierFleetId(),
                wing.hostStableId(),
                wing.stableFactionId(),
                List.copyOf(bayViews),
                List.copyOf(craftViews),
                List.copyOf(lost));
    }

    private MissionView missionView(
            MissionOrder mission,
            MissionTargetVisibility visibility) {
        boolean targetVisible = visibility.mayReveal(mission);
        return new MissionView(
                mission.id(),
                mission.type().name(),
                mission.status().name(),
                mission.source().name(),
                mission.target().kind().name(),
                targetVisible ? mission.target().referenceId() : "",
                targetVisible,
                mission.submittedTick());
    }

    private OperationalState operationalState(
            Optional<SmallCraftHangarRegistry.Assignment> occupancy,
            Optional<MissionOrder> mission,
            Optional<Request> queued,
            Optional<ActiveOperation> active,
            boolean repairRequired) {
        if (active.isPresent()) {
            return switch (active.orElseThrow().request().kind()) {
                case LAUNCH -> OperationalState.LAUNCHING;
                case RECOVERY -> OperationalState.RECOVERING;
            };
        }
        if (queued.isPresent()) {
            return switch (queued.orElseThrow().kind()) {
                case LAUNCH -> OperationalState.LAUNCH_QUEUED;
                case RECOVERY -> OperationalState.RECOVERY_QUEUED;
            };
        }
        if (mission.isPresent()) {
            return switch (mission.orElseThrow().status()) {
                case ACTIVE -> OperationalState.MISSION;
                case RETURNING -> OperationalState.RETURNING;
                case RECOVERY_PENDING -> OperationalState.RECOVERY_QUEUED;
                case LAUNCH_QUEUED -> OperationalState.LAUNCH_QUEUED;
                case COMPLETE, CANCELLED, FAILED -> occupancyState(occupancy, repairRequired);
            };
        }
        return occupancyState(occupancy, repairRequired);
    }

    private static OperationalState occupancyState(
            Optional<SmallCraftHangarRegistry.Assignment> occupancy,
            boolean repairRequired) {
        if (occupancy.isEmpty()) {
            return OperationalState.DEPLOYED_UNASSIGNED;
        }
        OccupancyState state = occupancy.orElseThrow().state();
        return switch (state) {
            case PARKED -> OperationalState.PARKED;
            case SERVICING -> repairRequired
                    ? OperationalState.REPAIR_REQUIRED : OperationalState.SERVICING;
            case READY -> OperationalState.READY;
            case LAUNCHING -> OperationalState.LAUNCHING;
            case RECOVERING -> OperationalState.RECOVERING;
        };
    }

    private int structuralReadinessBps(SmallCraftState state) {
        var hull = engineering.findHull(state.fit().hullId());
        if (hull == null) {
            throw new IllegalStateException(
                    "carrier UI cannot resolve craft hull: " + state.fit().hullId());
        }
        double minimum = 1d;
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

    private ResourceReadiness resourceReadiness(
            SmallCraftState state,
            InterfaceKind kind) {
        double capacity = 0d;
        for (InstalledModuleDefinition installed : state.fit().installedModules()) {
            ModuleDefinition module = engineering.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException(
                        "carrier UI cannot resolve craft module: " + installed.moduleId());
            }
            for (InterfaceDefinition candidate : module.interfaces()) {
                if (candidate.kind() == kind) {
                    capacity += candidate.capacity();
                }
            }
        }
        double amount = 0d;
        double massKg = 0d;
        long itemCount = 0L;
        for (ConsumableLoad load : state.runtimeState().consumables().interfaceLoads()) {
            if (load.kind() == kind) {
                amount += load.amount();
                massKg += load.massKg();
                itemCount = Math.addExact(itemCount, load.itemCount());
            }
        }
        if (capacity <= 0d) {
            return new ResourceReadiness(false, amount, 0d, massKg, itemCount, FleetReadinessState.FULL);
        }
        return new ResourceReadiness(
                true,
                amount,
                capacity,
                massKg,
                itemCount,
                fractionBps(Math.min(1d, amount / capacity)));
    }

    private int maintenanceReadinessBps(SmallCraftState state) {
        double minimum = 1d;
        Map<String, Double> ages =
                state.instanceState().maintenance().secondsSinceServiceByMount();
        for (InstalledModuleDefinition installed : state.fit().installedModules()) {
            ModuleDefinition module = engineering.findModule(installed.moduleId());
            if (module == null) {
                throw new IllegalStateException(
                        "carrier UI cannot resolve craft module: " + installed.moduleId());
            }
            double interval = module.maintenance().serviceIntervalSeconds();
            if (interval <= 0d) {
                continue;
            }
            double age = ages.getOrDefault(installed.mountId(), 0d);
            minimum = Math.min(minimum, Math.max(0d, 1d - age / interval));
        }
        return fractionBps(minimum);
    }

    private static Map<BayId, BayDefinition> canonicalBays(
            Map<BayId, BayDefinition> source) {
        TreeMap<BayId, BayDefinition> result = new TreeMap<>();
        for (Map.Entry<BayId, BayDefinition> entry : source.entrySet()) {
            BayId id = Objects.requireNonNull(entry.getKey(), "bay id");
            BayDefinition bay = Objects.requireNonNull(entry.getValue(), "bay definition");
            if (!id.equals(bay.id())) {
                throw new IllegalArgumentException(
                        "carrier UI bay map key differs from physical bay identity");
            }
            result.put(id, bay);
        }
        return Collections.unmodifiableMap(result);
    }

    private static int fractionBps(double fraction) {
        if (!Double.isFinite(fraction) || fraction < 0d || fraction > 1d) {
            throw new IllegalArgumentException("readiness fraction must be in [0,1]");
        }
        return (int) Math.floor(fraction * FleetReadinessState.FULL + 1e-9d);
    }

    /** Player-facing finite small-craft lifecycle derived from physical authorities. */
    public enum OperationalState {
        PARKED,
        SERVICING,
        REPAIR_REQUIRED,
        READY,
        LAUNCH_QUEUED,
        LAUNCHING,
        MISSION,
        RETURNING,
        RECOVERY_QUEUED,
        RECOVERING,
        DEPLOYED_UNASSIGNED
    }

    /** Current finite physical resource readiness for one interface family. */
    public record ResourceReadiness(
            boolean applicable,
            double amount,
            double capacity,
            double massKg,
            long itemCount,
            int readinessBps) {
        /** Validates immutable resource readiness. */
        public ResourceReadiness {
            if (!Double.isFinite(amount) || amount < 0d
                    || !Double.isFinite(capacity) || capacity < 0d
                    || !Double.isFinite(massKg) || massKg < 0d
                    || itemCount < 0L
                    || readinessBps < 0 || readinessBps > FleetReadinessState.FULL) {
                throw new IllegalArgumentException("invalid carrier UI resource readiness");
            }
        }
    }

    /** Actor-bounded mission projection; hidden targets remain intentionally blank. */
    public record MissionView(
            long missionId,
            String type,
            String status,
            String source,
            String targetKind,
            String targetReferenceId,
            boolean targetVisible,
            long submittedTick) {
        /** Validates one immutable mission view. */
        public MissionView {
            if (missionId <= 0L || submittedTick < 0L) {
                throw new IllegalArgumentException("invalid carrier UI mission identity/tick");
            }
            type = requireText(type, "type");
            status = requireText(status, "status");
            source = requireText(source, "source");
            targetKind = requireText(targetKind, "targetKind");
            targetReferenceId = targetReferenceId == null ? "" : targetReferenceId.strip();
            if (targetVisible && targetReferenceId.isEmpty()) {
                throw new IllegalArgumentException(
                        "visible mission target must retain actor-known reference");
            }
            if (!targetVisible && !targetReferenceId.isEmpty()) {
                throw new IllegalArgumentException(
                        "hidden mission target reference must not leak into UI");
            }
        }
    }

    /** One physical bay projection including capacity and deck queue state. */
    public record BayView(
            BayId id,
            String hostKind,
            double conditionFraction,
            int craftCount,
            double occupiedMassKg,
            double supportedMassKg,
            double occupiedVolumeM3,
            double usableVolumeM3,
            CapacityStatus capacityStatus,
            int queuedOperations,
            String activeOperationKind,
            String activeOperationPhase,
            double activeRemainingWorkSeconds,
            String activeFailure) {
        /** Validates one immutable bay view. */
        public BayView {
            Objects.requireNonNull(id, "id");
            hostKind = requireText(hostKind, "hostKind");
            if (!Double.isFinite(conditionFraction)
                    || conditionFraction < 0d || conditionFraction > 1d
                    || craftCount < 0
                    || !Double.isFinite(occupiedMassKg) || occupiedMassKg < 0d
                    || !Double.isFinite(supportedMassKg) || supportedMassKg < 0d
                    || !Double.isFinite(occupiedVolumeM3) || occupiedVolumeM3 < 0d
                    || !Double.isFinite(usableVolumeM3) || usableVolumeM3 < 0d
                    || queuedOperations < 0
                    || !Double.isFinite(activeRemainingWorkSeconds)
                    || activeRemainingWorkSeconds < 0d) {
                throw new IllegalArgumentException("invalid carrier UI bay state");
            }
            Objects.requireNonNull(capacityStatus, "capacityStatus");
            activeOperationKind = normalizeOptional(activeOperationKind);
            activeOperationPhase = normalizeOptional(activeOperationPhase);
            activeFailure = normalizeOptional(activeFailure);
        }
    }

    /** One individual physical craft projection for the carrier UI. */
    public record CraftView(
            SmallCraftId craftId,
            String designId,
            String hullId,
            String stableFactionId,
            OperationalState operationalState,
            String occupancyState,
            String bayStableId,
            int structuralReadinessBps,
            ResourceReadiness ammunition,
            ResourceReadiness propellant,
            int maintenanceReadinessBps,
            boolean repairRequired,
            boolean serviceRequired,
            MissionView mission,
            String queuedDeckOperation,
            String activeDeckOperation,
            String activeDeckPhase,
            double activeDeckRemainingWorkSeconds,
            String deckFailure) {
        /** Validates one immutable craft view. */
        public CraftView {
            Objects.requireNonNull(craftId, "craftId");
            designId = requireText(designId, "designId");
            hullId = requireText(hullId, "hullId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(operationalState, "operationalState");
            occupancyState = normalizeOptional(occupancyState);
            bayStableId = normalizeOptional(bayStableId);
            requireBps(structuralReadinessBps, "structuralReadinessBps");
            Objects.requireNonNull(ammunition, "ammunition");
            Objects.requireNonNull(propellant, "propellant");
            requireBps(maintenanceReadinessBps, "maintenanceReadinessBps");
            queuedDeckOperation = normalizeOptional(queuedDeckOperation);
            activeDeckOperation = normalizeOptional(activeDeckOperation);
            activeDeckPhase = normalizeOptional(activeDeckPhase);
            if (!Double.isFinite(activeDeckRemainingWorkSeconds)
                    || activeDeckRemainingWorkSeconds < 0d) {
                throw new IllegalArgumentException(
                        "activeDeckRemainingWorkSeconds must be finite and non-negative");
            }
            deckFailure = normalizeOptional(deckFailure);
        }
    }

    /** Complete read-only player-facing state for one ordinary carrier FleetId. */
    public record CarrierView(
            FleetId carrierFleetId,
            String hostStableId,
            String stableFactionId,
            List<BayView> bays,
            List<CraftView> craft,
            List<SmallCraftId> lostCraftIds) {
        /** Validates and freezes one carrier view. */
        public CarrierView {
            Objects.requireNonNull(carrierFleetId, "carrierFleetId");
            hostStableId = requireText(hostStableId, "hostStableId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            bays = List.copyOf(Objects.requireNonNull(bays, "bays"));
            craft = List.copyOf(Objects.requireNonNull(craft, "craft"));
            lostCraftIds = List.copyOf(Objects.requireNonNull(lostCraftIds, "lostCraftIds"));
        }
    }

    /** Visibility seam supplied from actor-known information, never hidden world truth. */
    @FunctionalInterface
    public interface MissionTargetVisibility {
        /**
         * @param mission current persistent mission
         * @return whether its target reference may be displayed to this UI actor
         */
        boolean mayReveal(MissionOrder mission);

        /** @return policy that reveals actor-known target references supplied by trusted caller */
        static MissionTargetVisibility revealAll() {
            return ignored -> true;
        }

        /** @return fail-closed policy that hides every target reference */
        static MissionTargetVisibility hideAll() {
            return ignored -> false;
        }
    }

    private static void requireBps(int value, String label) {
        if (value < 0 || value > FleetReadinessState.FULL) {
            throw new IllegalArgumentException(label + " must be in 0..10000");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }

    private static String normalizeOptional(String value) {
        return value == null ? "" : value.strip();
    }
}
