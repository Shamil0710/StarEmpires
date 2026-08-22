package com.spacesim.persistence;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18LogisticsRuntime;
import com.spacesim.economy.Stage18LogisticsRuntime.HandlingCapability;
import com.spacesim.economy.Stage18LogisticsRuntime.Status;
import com.spacesim.economy.Stage18LogisticsRuntime.TransferBudget;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.CargoLotState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Ordinary mutable Stage-20.5B freight runtime over Stage-18 physical storage transfers.
 *
 * <p>Orders authorize movement but never create cargo. Loading and unloading call the same
 * {@link Stage18LogisticsRuntime} used by station logistics, so source inventory decreases before a
 * provenance lot can exist aboard. Route progress is exact and neighbor-by-neighbor according to
 * the persisted order. Destruction removes the physical hold and its lots without replacement,
 * making loss directly reduce future deliverable supply.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20FreightRuntime {
    private static final double EPSILON = 1.0e-9d;

    private final Stage18ResourceOntologyCatalog ontology;
    private final Stage18ManufacturingProductRegistry products;
    private final Stage18LogisticsRuntime logistics;
    private final long rootSeed;
    private final String generatorVersion;
    private final String worldFingerprint;
    private final String materializationVersion;
    private final String compatibilityAuthorityVersion;
    private final long nextFleetIdValue;
    private long nextCargoLotOrdinal;
    private final TreeMap<FleetId, FreighterState> freighters = new TreeMap<>();
    private final TreeMap<FleetId, Stage18StationStorage> holds = new TreeMap<>();
    private final TreeMap<String, CargoLotState> lots = new TreeMap<>();
    private final TreeMap<String, TransportOrderState> orders = new TreeMap<>();

    private Stage20FreightRuntime(
            Stage20FreightPersistentState state,
            Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products) {
        Stage20FreightPersistentState saved = Objects.requireNonNull(state, "state");
        this.ontology = Objects.requireNonNull(ontology, "ontology");
        this.products = Objects.requireNonNull(products, "products");
        this.logistics = new Stage18LogisticsRuntime(ontology, products);
        rootSeed = saved.rootSeed();
        generatorVersion = saved.generatorVersion();
        worldFingerprint = saved.worldFingerprint();
        materializationVersion = saved.materializationVersion();
        compatibilityAuthorityVersion = saved.compatibilityAuthorityVersion();
        nextFleetIdValue = saved.nextFleetIdValue();
        nextCargoLotOrdinal = saved.nextCargoLotOrdinal();
        saved.freighters().forEach(value -> {
            freighters.put(value.fleetId(), value);
            holds.put(value.fleetId(), Stage18StationStorage.restore(
                    ontology, products, value.cargoStorage()));
        });
        saved.cargoLots().forEach(value -> lots.put(value.lotId(), value));
        saved.orders().forEach(value -> orders.put(value.orderId(), value));
    }

    /**
     * Restores an independently validated physical freight sidecar.
     *
     * @param state exact persistent freight sidecar
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(Stage20FreightPersistentState state) {
        return new Stage20FreightRuntime(
                state,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault());
    }

    /**
     * Validates all generated authority before restoring the mutable runtime.
     *
     * @param campaign exact saved generated campaign
     * @param specialization exact matching closed Stage-20F authority
     * @param state exact persistent freight sidecar
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact named engineering catalog
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(
            Stage20GeneratedCampaignPersistentState campaign,
            OperationalSpecializationReport specialization,
            Stage20FreightPersistentState state,
            Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority compatibility,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering) {
        Stage20FreightPersistentState checked = Stage20FreightRuntimeMaterializer.validateRestore(
                campaign, specialization, state, compatibility, engineering);
        return restore(checked);
    }

    /**
     * Restores from the canonical saved authority without rerunning the Stage-20F planner.
     *
     * @param campaign exact saved generated campaign
     * @param state exact persistent freight sidecar
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact named engineering catalog
     * @return mutable ordinary freight runtime
     */
    public static Stage20FreightRuntime restore(
            Stage20GeneratedCampaignPersistentState campaign,
            Stage20FreightPersistentState state,
            Stage20FreightRuntimeMaterializer.FreighterCompatibilityAuthority compatibility,
            com.spacesim.content.ship.ShipEngineeringCatalog engineering) {
        Stage20FreightPersistentState checked = Stage20FreightRuntimeMaterializer.validateRestore(
                campaign, state, compatibility, engineering);
        return restore(checked);
    }

    /**
     * Captures exact fleet, hold, lot, route and deadline identity without regeneration.
     *
     * @return complete deterministic Stage-20.5B freight sidecar
     */
    public Stage20FreightPersistentState capture() {
        ArrayList<FreighterState> fleetRows = new ArrayList<>();
        for (FreighterState state : freighters.values()) {
            fleetRows.add(copyFreighter(state, holds.get(state.fleetId()).snapshot()));
        }
        return new Stage20FreightPersistentState(
                Stage20FreightPersistentState.CURRENT_VERSION,
                rootSeed,
                generatorVersion,
                worldFingerprint,
                materializationVersion,
                compatibilityAuthorityVersion,
                nextFleetIdValue,
                nextCargoLotOrdinal,
                fleetRows,
                List.copyOf(lots.values()),
                List.copyOf(orders.values()));
    }

    /**
     * Finds one immutable current fleet state.
     *
     * @param fleetId stable real fleet identity
     * @return current fleet state or empty when unknown
     */
    public Optional<FreighterState> findFreighter(FleetId fleetId) {
        FreighterState state = freighters.get(fleetId);
        return state == null ? Optional.empty() : Optional.of(copyFreighter(
                state, holds.get(state.fleetId()).snapshot()));
    }

    /**
     * Returns one immutable physical cargo-hold snapshot.
     *
     * @param fleetId stable real fleet identity
     * @return exact current Stage-18 hold snapshot
     */
    public StationStorageSnapshot cargoHoldSnapshot(FleetId fleetId) {
        return requireHold(fleetId).snapshot();
    }

    /**
     * Finds one immutable current order state.
     *
     * @param orderId stable transport order identity
     * @return current order or empty when unknown
     */
    public Optional<TransportOrderState> findOrder(String orderId) {
        return Optional.ofNullable(orders.get(orderId));
    }

    /** @return exact current lots, deterministically ordered by lot identity */
    public List<CargoLotState> cargoLots() {
        return List.copyOf(lots.values());
    }

    /**
     * Loads physical commodity mass from an ordinary Stage-18 source storage and creates provenance
     * only after the atomic transfer succeeds.
     *
     * @param fleetId stable carrying fleet identity
     * @param source ordinary source storage
     * @param massKg requested positive physical mass
     * @param sourceProvenanceId exact accepted source provenance
     * @param simulationSeconds authoritative loading time
     * @param handling compatible endpoint handling authority
     * @param budget finite current transfer budget
     * @return committed or rejected physical cargo operation
     */
    public CargoOperationResult loadCommodity(
            FleetId fleetId,
            Stage18StationStorage source,
            double massKg,
            String sourceProvenanceId,
            double simulationSeconds,
            HandlingCapability handling,
            TransferBudget budget) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_SOURCE);
        Stage18StationStorage sourceStorage = Objects.requireNonNull(source, "source");
        String provenance = requireText(sourceProvenanceId, "sourceProvenanceId");
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (!sourceStorage.stationId().equals(order.sourceEndpointId())) {
            throw new IllegalArgumentException("loading storage differs from order source endpoint");
        }
        if (!order.sourceProvenanceId().equals(provenance)) {
            throw new IllegalArgumentException("loading provenance differs from order authority");
        }
        if (!Double.isFinite(massKg) || massKg <= 0d
                || fleet.cargoMassKg() + massKg > fleet.cargoCapacityKg() + EPSILON) {
            return CargoOperationResult.rejected(Status.DESTINATION_FULL);
        }
        Stage18LogisticsRuntime.TransferResult transfer = logistics.transferCommodity(
                sourceStorage,
                requireHold(fleetId),
                order.commodityId(),
                massKg,
                handling,
                budget);
        if (!transfer.transferred()) {
            return CargoOperationResult.rejected(transfer.status());
        }
        String lotId = "freight-lot:" + nextCargoLotOrdinal++;
        CargoLotState lot = new CargoLotState(
                lotId,
                fleetId,
                order.orderId(),
                order.commodityId(),
                massKg,
                order.sourceEndpointId(),
                provenance,
                simulationSeconds);
        lots.put(lotId, lot);
        refreshFreighterHold(fleetId);
        return new CargoOperationResult(Status.TRANSFERRED, lotId, massKg);
    }

    /**
     * Starts the persisted loaded producer-to-consumer route.
     *
     * @param fleetId stable carrying fleet identity
     * @param simulationSeconds authoritative dispatch time
     */
    public void dispatchOutbound(FleetId fleetId, double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_SOURCE);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (fleet.cargoMassKg() <= EPSILON) {
            throw new IllegalStateException("outbound freight dispatch requires physical cargo");
        }
        if (fleet.routeIndex() != 0) {
            throw new IllegalStateException("outbound dispatch must begin at route origin");
        }
        orders.put(order.orderId(), copyOrder(
                order,
                simulationSeconds + order.oneWayDeliverySeconds(),
                order.deliveredMassKg(),
                order.delayedDeliveryCount()));
        freighters.put(fleetId, copyFreighter(
                fleet, fleet.currentSystemId(), fleet.physicalState(), FreightPhase.OUTBOUND,
                fleet.routeIndex(), requireHold(fleetId).snapshot()));
    }

    /**
     * Completes exactly the next persisted outbound neighbor hop using caller-supplied physical
     * arrival kinematics. Stage-20.5D supplies that edge-authoritative state.
     *
     * @param fleetId stable carrying fleet identity
     * @param nextSystemId exact next persisted neighbor
     * @param arrivalState exact destination-local physical state
     * @return updated immutable fleet state
     */
    public FreighterState completeNextOutboundHop(
            FleetId fleetId,
            StarSystemId nextSystemId,
            LocalPhysicalKinematics arrivalState) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.OUTBOUND);
        int nextIndex = fleet.routeIndex() + 1;
        if (nextIndex >= order.orderedSystems().size()
                || !order.orderedSystems().get(nextIndex).equals(nextSystemId)) {
            throw new IllegalArgumentException("outbound progress must follow the next exact route hop");
        }
        FreightPhase phase = nextIndex == order.orderedSystems().size() - 1
                ? FreightPhase.AT_DESTINATION : FreightPhase.OUTBOUND;
        FreighterState updated = copyFreighter(
                fleet,
                nextSystemId,
                Objects.requireNonNull(arrivalState, "arrivalState"),
                phase,
                nextIndex,
                requireHold(fleetId).snapshot());
        freighters.put(fleetId, updated);
        return updated;
    }

    /**
     * Unloads physical cargo into the exact ordinary Stage-18 destination storage.
     *
     * @param fleetId stable carrying fleet identity
     * @param destination ordinary destination storage
     * @param massKg requested positive physical mass
     * @param handling compatible endpoint handling authority
     * @param budget finite current transfer budget
     * @return committed or rejected physical cargo operation
     */
    public CargoOperationResult unloadCommodity(
            FleetId fleetId,
            Stage18StationStorage destination,
            double massKg,
            HandlingCapability handling,
            TransferBudget budget) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_DESTINATION);
        Stage18StationStorage destinationStorage = Objects.requireNonNull(destination, "destination");
        if (!destinationStorage.stationId().equals(order.destinationEndpointId())) {
            throw new IllegalArgumentException("unloading storage differs from order destination endpoint");
        }
        Stage18LogisticsRuntime.TransferResult transfer = logistics.transferCommodity(
                requireHold(fleetId),
                destinationStorage,
                order.commodityId(),
                massKg,
                handling,
                budget);
        if (!transfer.transferred()) {
            return CargoOperationResult.rejected(transfer.status());
        }
        consumeLots(fleetId, order.orderId(), order.commodityId(), massKg);
        orders.put(order.orderId(), copyOrder(
                order,
                order.deliveryDeadlineSeconds(),
                order.deliveredMassKg() + massKg,
                order.delayedDeliveryCount()));
        refreshFreighterHold(fleetId);
        return new CargoOperationResult(Status.TRANSFERRED, "", massKg);
    }

    /**
     * Starts the empty consumer-to-producer return route.
     *
     * @param fleetId stable returning fleet identity
     */
    public void dispatchReturn(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.AT_DESTINATION);
        if (fleet.cargoMassKg() > EPSILON) {
            throw new IllegalStateException("return dispatch requires an empty physical hold");
        }
        freighters.put(fleetId, copyFreighter(
                fleet,
                fleet.currentSystemId(),
                fleet.physicalState(),
                FreightPhase.RETURNING,
                order.orderedSystems().size() - 1,
                requireHold(fleetId).snapshot()));
    }

    /**
     * Completes exactly the next reverse neighbor hop; origin completion reopens loading.
     *
     * @param fleetId stable returning fleet identity
     * @param nextSystemId exact next persisted reverse-route neighbor
     * @param arrivalState exact destination-local physical state
     * @param simulationSeconds authoritative arrival time
     * @return updated immutable fleet state
     */
    public FreighterState completeNextReturnHop(
            FleetId fleetId,
            StarSystemId nextSystemId,
            LocalPhysicalKinematics arrivalState,
            double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requirePhase(fleet, FreightPhase.RETURNING);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        int nextIndex = fleet.routeIndex() - 1;
        if (nextIndex < 0 || !order.orderedSystems().get(nextIndex).equals(nextSystemId)) {
            throw new IllegalArgumentException("return progress must follow the next exact reverse route hop");
        }
        FreightPhase phase = nextIndex == 0 ? FreightPhase.AT_SOURCE : FreightPhase.RETURNING;
        FreighterState updated = copyFreighter(
                fleet,
                nextSystemId,
                Objects.requireNonNull(arrivalState, "arrivalState"),
                phase,
                nextIndex,
                requireHold(fleetId).snapshot());
        freighters.put(fleetId, updated);
        if (phase == FreightPhase.AT_SOURCE) {
            orders.put(order.orderId(), copyOrder(
                    order,
                    simulationSeconds + order.oneWayDeliverySeconds(),
                    order.deliveredMassKg(),
                    order.delayedDeliveryCount()));
        }
        return updated;
    }

    /**
     * Records every newly crossed physical delivery deadline exactly once.
     *
     * @param fleetId stable active fleet identity
     * @param simulationSeconds authoritative observation time
     * @return cumulative missed-delivery count
     */
    public long observeDeliveryDelay(FleetId fleetId, double simulationSeconds) {
        FreighterState fleet = requireFreighter(fleetId);
        TransportOrderState order = requireOrder(fleet);
        requireNonNegativeFinite(simulationSeconds, "simulationSeconds");
        if (fleet.phase() == FreightPhase.AT_SOURCE
                || fleet.phase() == FreightPhase.IDLE
                || fleet.phase() == FreightPhase.DESTROYED
                || simulationSeconds <= order.deliveryDeadlineSeconds()) {
            return order.delayedDeliveryCount();
        }
        long misses = (long) Math.floor(
                (simulationSeconds - order.deliveryDeadlineSeconds()) / order.roundTripCycleSeconds()) + 1L;
        double deadline = order.deliveryDeadlineSeconds() + misses * order.roundTripCycleSeconds();
        long delayed = Math.addExact(order.delayedDeliveryCount(), misses);
        orders.put(order.orderId(), copyOrder(order, deadline, order.deliveredMassKg(), delayed));
        return delayed;
    }

    /**
     * Permanently destroys one physical freight asset and its aboard mass. No ID, order or reserve
     * slot is created as a replacement.
     *
     * @param fleetId stable physical fleet identity
     * @return exact lost cargo and provenance result
     */
    public DestructionResult destroy(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        if (fleet.phase() == FreightPhase.DESTROYED) {
            return new DestructionResult(fleetId, 0d, List.of(), false);
        }
        Stage18StationStorage hold = requireHold(fleetId);
        double lostMass = fleet.cargoMassKg();
        List<CargoLotState> lostLots = lots.values().stream()
                .filter(value -> value.fleetId().equals(fleetId))
                .sorted(Comparator.comparing(CargoLotState::lotId))
                .toList();
        lostLots.forEach(value -> lots.remove(value.lotId()));
        holds.put(fleetId, new Stage18StationStorage(
                ontology,
                products,
                hold.stationId(),
                hold.snapshotCapacityByStorageClassKg(),
                Map.of(),
                Map.of()));
        freighters.put(fleetId, copyFreighter(
                fleet,
                fleet.currentSystemId(),
                fleet.physicalState(),
                FreightPhase.DESTROYED,
                fleet.routeIndex(),
                holds.get(fleetId).snapshot()));
        return new DestructionResult(fleetId, lostMass, lostLots, true);
    }

    /** Result of one physical cargo operation. */
    public record CargoOperationResult(Status status, String lotId, double transferredMassKg) {
        /**
         * Validates one physical cargo-operation result.
         *
         * @param status exact Stage-18 transfer status
         * @param lotId created lot identity or empty when none
         * @param transferredMassKg physical mass committed by the operation
         */
        public CargoOperationResult {
            Objects.requireNonNull(status, "status");
            lotId = lotId == null ? "" : lotId;
            if (!Double.isFinite(transferredMassKg) || transferredMassKg < 0d) {
                throw new IllegalArgumentException("transferredMassKg must be non-negative and finite");
            }
            if ((status == Status.TRANSFERRED) != (transferredMassKg > 0d)) {
                throw new IllegalArgumentException("cargo operation status and mass differ");
            }
        }

        static CargoOperationResult rejected(Status status) {
            return new CargoOperationResult(status, "", 0d);
        }

        /** @return whether physical storage transfer committed */
        public boolean transferred() {
            return status == Status.TRANSFERRED;
        }
    }

    /** Physical destruction result retaining exact lost lot provenance. */
    public record DestructionResult(
            FleetId fleetId,
            double lostCargoMassKg,
            List<CargoLotState> lostLots,
            boolean destroyedNow) {
        /**
         * Validates one physical freight-destruction result.
         *
         * @param fleetId stable destroyed fleet identity
         * @param lostCargoMassKg physical mass removed with the asset
         * @param lostLots exact removed provenance lots
         * @param destroyedNow whether this call performed the destruction
         */
        public DestructionResult {
            Objects.requireNonNull(fleetId, "fleetId");
            if (!Double.isFinite(lostCargoMassKg) || lostCargoMassKg < 0d) {
                throw new IllegalArgumentException("lostCargoMassKg must be non-negative and finite");
            }
            lostLots = List.copyOf(Objects.requireNonNull(lostLots, "lostLots"));
        }
    }

    private void consumeLots(FleetId fleetId, String orderId, String commodityId, double massKg) {
        double remaining = massKg;
        ArrayList<CargoLotState> matching = lots.values().stream()
                .filter(value -> value.fleetId().equals(fleetId)
                        && value.orderId().equals(orderId)
                        && value.commodityId().equals(commodityId))
                .sorted(Comparator.comparingDouble(CargoLotState::loadedAtSimulationSeconds)
                        .thenComparing(CargoLotState::lotId))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (CargoLotState lot : matching) {
            if (remaining <= EPSILON) {
                break;
            }
            double consumed = Math.min(remaining, lot.massKg());
            double retained = lot.massKg() - consumed;
            if (retained <= EPSILON) {
                lots.remove(lot.lotId());
            } else {
                lots.put(lot.lotId(), new CargoLotState(
                        lot.lotId(), lot.fleetId(), lot.orderId(), lot.commodityId(), retained,
                        lot.sourceEndpointId(), lot.sourceProvenanceId(),
                        lot.loadedAtSimulationSeconds()));
            }
            remaining -= consumed;
        }
        if (remaining > EPSILON) {
            throw new IllegalStateException("physical hold mass exceeded cargo-lot provenance");
        }
    }

    private void refreshFreighterHold(FleetId fleetId) {
        FreighterState fleet = requireFreighter(fleetId);
        freighters.put(fleetId, copyFreighter(fleet, requireHold(fleetId).snapshot()));
    }

    private FreighterState requireFreighter(FleetId fleetId) {
        FreighterState result = freighters.get(Objects.requireNonNull(fleetId, "fleetId"));
        if (result == null) {
            throw new IllegalArgumentException("unknown physical freight fleet: " + fleetId);
        }
        return copyFreighter(result, requireHold(result.fleetId()).snapshot());
    }

    private Stage18StationStorage requireHold(FleetId fleetId) {
        Stage18StationStorage result = holds.get(Objects.requireNonNull(fleetId, "fleetId"));
        if (result == null) {
            throw new IllegalArgumentException("unknown freight hold: " + fleetId);
        }
        return result;
    }

    private TransportOrderState requireOrder(FreighterState fleet) {
        if (fleet.activeOrderId().isEmpty()) {
            throw new IllegalStateException("freighter has no active transport order");
        }
        TransportOrderState result = orders.get(fleet.activeOrderId());
        if (result == null || !result.fleetId().equals(fleet.fleetId())) {
            throw new IllegalStateException("freighter order identity is inconsistent");
        }
        return result;
    }

    private static void requirePhase(FreighterState fleet, FreightPhase required) {
        if (fleet.phase() != required) {
            throw new IllegalStateException("freighter phase must be " + required + ", was " + fleet.phase());
        }
    }

    private static FreighterState copyFreighter(
            FreighterState source,
            StationStorageSnapshot storage) {
        return copyFreighter(
                source,
                source.currentSystemId(),
                source.physicalState(),
                source.phase(),
                source.routeIndex(),
                storage);
    }

    private static FreighterState copyFreighter(
            FreighterState source,
            StarSystemId systemId,
            LocalPhysicalKinematics physical,
            FreightPhase phase,
            int routeIndex,
            StationStorageSnapshot storage) {
        return new FreighterState(
                source.fleetId(),
                source.stableFactionId(),
                source.ownershipOrdinal(),
                source.hullId(),
                source.fitId(),
                source.cargoCapacityKg(),
                systemId,
                physical,
                phase,
                source.activeOrderId(),
                routeIndex,
                storage);
    }

    private static TransportOrderState copyOrder(
            TransportOrderState source,
            double deadline,
            double deliveredMass,
            long delayedCount) {
        return new TransportOrderState(
                source.orderId(),
                source.fleetId(),
                source.stableFactionId(),
                source.assignmentKind(),
                source.commodityId(),
                source.sourceEndpointId(),
                source.destinationEndpointId(),
                source.sourceProvenanceId(),
                source.orderedSystems(),
                source.oneWayDeliverySeconds(),
                source.roundTripCycleSeconds(),
                deadline,
                deliveredMass,
                delayedCount);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
