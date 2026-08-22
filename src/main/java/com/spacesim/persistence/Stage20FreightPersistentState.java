package com.spacesim.persistence;

import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned Stage-20.5B persistence sidecar for physical freight fleets, cargo lots and orders.
 *
 * <p>Station and source inventory remain owned by the Stage-18 industrial state. This sidecar
 * stores only the physical cargo currently aboard real freight fleets plus their stable provenance
 * and route state. It therefore cannot turn a throughput reservation into inventory during load.</p>
 *
 * @param schemaVersion freight-sidecar schema version
 * @param rootSeed exact generated campaign seed
 * @param generatorVersion exact saved generated-world version
 * @param worldFingerprint exact saved generated-world fingerprint
 * @param materializationVersion exact Stage-20.5B bridge version
 * @param compatibilityAuthorityVersion explicit hull/fit compatibility authority
 * @param nextFleetIdValue next unused persistent fleet ID
 * @param nextCargoLotOrdinal next unused cargo-lot ordinal
 * @param freighters complete finite freight fleet state
 * @param cargoLots physical aboard-cargo provenance rows
 * @param orders ordinary persistent transport orders
 */
@SuppressWarnings("doclint:missing")
public record Stage20FreightPersistentState(
        int schemaVersion,
        long rootSeed,
        String generatorVersion,
        String worldFingerprint,
        String materializationVersion,
        String compatibilityAuthorityVersion,
        long nextFleetIdValue,
        long nextCargoLotOrdinal,
        List<FreighterState> freighters,
        List<CargoLotState> cargoLots,
        List<TransportOrderState> orders) {
    /** Current physical-freight persistence schema. */
    public static final int CURRENT_VERSION = 1;
    private static final double EPSILON = 1.0e-9d;

    /** Physical route lifecycle for one real freighter. */
    public enum FreightPhase {
        /** Owned reserve fleet without an assigned transport route. */ IDLE,
        /** Empty or loaded ship is physically present at its source endpoint. */ AT_SOURCE,
        /** Ship is executing the ordered producer-to-consumer route. */ OUTBOUND,
        /** Ship is physically present at the consumer endpoint. */ AT_DESTINATION,
        /** Ship is executing the reverse route before another loading cycle. */ RETURNING,
        /** Physical asset was destroyed and cannot deliver or respawn. */ DESTROYED
    }

    /** Source of one accepted transport assignment. */
    public enum AssignmentKind {
        /** Stage-20E essential bootstrap commitment. */ ESSENTIAL_BOOTSTRAP,
        /** Stage-20F selected industrial-input reservation. */ INDUSTRIAL_INPUT
    }

    /**
     * Persistent physical state of one owned freight asset.
     *
     * @param fleetId ordinary world-level fleet identity
     * @param stableFactionId exact owner identity
     * @param ownershipOrdinal exact Stage-20E owned-pool ordinal
     * @param hullId explicit compatible physical hull
     * @param fitId explicit compatible physical fit
     * @param cargoCapacityKg hull/fit-validated cargo mass capacity
     * @param currentSystemId current physical system
     * @param physicalState exact local physical kinematics
     * @param phase current route lifecycle phase
     * @param activeOrderId assigned order or empty for reserve fleet
     * @param routeIndex current index in the order's producer-to-consumer route
     * @param cargoStorage exact Stage-18 physical cargo-hold snapshot
     */
    public record FreighterState(
            FleetId fleetId,
            String stableFactionId,
            int ownershipOrdinal,
            String hullId,
            String fitId,
            double cargoCapacityKg,
            StarSystemId currentSystemId,
            LocalPhysicalKinematics physicalState,
            FreightPhase phase,
            String activeOrderId,
            int routeIndex,
            StationStorageSnapshot cargoStorage) {
        public FreighterState {
            Objects.requireNonNull(fleetId, "fleetId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            if (ownershipOrdinal < 0) {
                throw new IllegalArgumentException("ownershipOrdinal must be non-negative");
            }
            hullId = requireText(hullId, "hullId");
            fitId = requireText(fitId, "fitId");
            requirePositiveFinite(cargoCapacityKg, "cargoCapacityKg");
            Objects.requireNonNull(currentSystemId, "currentSystemId");
            Objects.requireNonNull(physicalState, "physicalState");
            Objects.requireNonNull(phase, "phase");
            activeOrderId = activeOrderId == null ? "" : activeOrderId.strip();
            if (routeIndex < 0) {
                throw new IllegalArgumentException("routeIndex must be non-negative");
            }
            Objects.requireNonNull(cargoStorage, "cargoStorage");
            if (!cargoStorage.stationId().equals(cargoHoldId(fleetId))) {
                throw new IllegalArgumentException("cargo storage identity must derive from FleetId");
            }
            double storedMass = cargoStorage.commodityMassByIdKg().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            for (Map.Entry<String, Integer> product : cargoStorage.productCountById().entrySet()) {
                if (product.getValue() != 0) {
                    throw new IllegalArgumentException(
                            "Stage-20.5B freight hold currently persists commodity mass only");
                }
            }
            if (storedMass > cargoCapacityKg + EPSILON) {
                throw new IllegalArgumentException("freighter cargo exceeds physical capacity");
            }
            if (phase == FreightPhase.IDLE && !activeOrderId.isEmpty()) {
                throw new IllegalArgumentException("idle reserve freighter cannot retain an active order");
            }
            if (phase != FreightPhase.IDLE && phase != FreightPhase.DESTROYED
                    && activeOrderId.isEmpty()) {
                throw new IllegalArgumentException("active freighter phase requires an order");
            }
        }

        /** @return total physical commodity mass aboard this fleet */
        public double cargoMassKg() {
            return cargoStorage.commodityMassByIdKg().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
        }

        /** @return whether this physical asset can still execute orders */
        public boolean operational() {
            return phase != FreightPhase.DESTROYED;
        }
    }

    /**
     * Stable provenance of one physical mass lot aboard a freight fleet.
     *
     * @param lotId globally stable lot identity inside this campaign
     * @param fleetId carrying fleet
     * @param orderId order that authorized loading
     * @param commodityId Stage-18 commodity identity
     * @param massKg current conserved mass
     * @param sourceEndpointId physical loading endpoint
     * @param sourceProvenanceId upstream production/extraction provenance
     * @param loadedAtSimulationSeconds authoritative loading time
     */
    public record CargoLotState(
            String lotId,
            FleetId fleetId,
            String orderId,
            String commodityId,
            double massKg,
            String sourceEndpointId,
            String sourceProvenanceId,
            double loadedAtSimulationSeconds) {
        public CargoLotState {
            lotId = requireText(lotId, "lotId");
            Objects.requireNonNull(fleetId, "fleetId");
            orderId = requireText(orderId, "orderId");
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(massKg, "massKg");
            sourceEndpointId = requireText(sourceEndpointId, "sourceEndpointId");
            sourceProvenanceId = requireText(sourceProvenanceId, "sourceProvenanceId");
            requireNonNegativeFinite(loadedAtSimulationSeconds, "loadedAtSimulationSeconds");
        }
    }

    /**
     * Persistent ordinary route order assigned to exactly one physical freight fleet.
     *
     * @param orderId stable order identity
     * @param fleetId assigned real fleet
     * @param stableFactionId exact owner
     * @param assignmentKind accepted planning source
     * @param commodityId transported Stage-18 commodity
     * @param sourceEndpointId physical source/loading endpoint
     * @param destinationEndpointId physical destination/unloading endpoint
     * @param sourceProvenanceId exact accepted source/commitment identity
     * @param orderedSystems explicit producer-to-consumer neighbor route
     * @param oneWayDeliverySeconds retained physical delivery time
     * @param roundTripCycleSeconds retained ready-again cadence
     * @param deliveryDeadlineSeconds current physical delivery deadline
     * @param deliveredMassKg mass actually delivered by this persistent order
     * @param delayedDeliveryCount number of missed deadlines observed by runtime
     */
    public record TransportOrderState(
            String orderId,
            FleetId fleetId,
            String stableFactionId,
            AssignmentKind assignmentKind,
            String commodityId,
            String sourceEndpointId,
            String destinationEndpointId,
            String sourceProvenanceId,
            List<StarSystemId> orderedSystems,
            double oneWayDeliverySeconds,
            double roundTripCycleSeconds,
            double deliveryDeadlineSeconds,
            double deliveredMassKg,
            long delayedDeliveryCount) {
        public TransportOrderState {
            orderId = requireText(orderId, "orderId");
            Objects.requireNonNull(fleetId, "fleetId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            Objects.requireNonNull(assignmentKind, "assignmentKind");
            commodityId = requireText(commodityId, "commodityId");
            sourceEndpointId = requireText(sourceEndpointId, "sourceEndpointId");
            destinationEndpointId = requireText(destinationEndpointId, "destinationEndpointId");
            sourceProvenanceId = requireText(sourceProvenanceId, "sourceProvenanceId");
            ArrayList<StarSystemId> route = new ArrayList<>(Objects.requireNonNull(
                    orderedSystems, "orderedSystems"));
            if (route.size() < 2 || route.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("transport order requires a remote physical route");
            }
            for (int index = 1; index < route.size(); index++) {
                if (route.get(index - 1).equals(route.get(index))) {
                    throw new IllegalArgumentException("transport route cannot repeat a system in one hop");
                }
            }
            orderedSystems = List.copyOf(route);
            requirePositiveFinite(oneWayDeliverySeconds, "oneWayDeliverySeconds");
            requirePositiveFinite(roundTripCycleSeconds, "roundTripCycleSeconds");
            if (roundTripCycleSeconds <= oneWayDeliverySeconds) {
                throw new IllegalArgumentException("round-trip cadence must exceed one-way delivery");
            }
            requireNonNegativeFinite(deliveryDeadlineSeconds, "deliveryDeadlineSeconds");
            requireNonNegativeFinite(deliveredMassKg, "deliveredMassKg");
            if (delayedDeliveryCount < 0L) {
                throw new IllegalArgumentException("delayedDeliveryCount must be non-negative");
            }
        }
    }

    public Stage20FreightPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20.5B freight schema: " + schemaVersion);
        }
        generatorVersion = requireText(generatorVersion, "generatorVersion");
        worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
        materializationVersion = requireText(materializationVersion, "materializationVersion");
        compatibilityAuthorityVersion = requireText(
                compatibilityAuthorityVersion, "compatibilityAuthorityVersion");
        if (nextFleetIdValue <= 0L || nextCargoLotOrdinal <= 0L) {
            throw new IllegalArgumentException("persistent allocator watermarks must be positive");
        }

        ArrayList<FreighterState> fleetCopy = new ArrayList<>(Objects.requireNonNull(
                freighters, "freighters"));
        ArrayList<CargoLotState> lotCopy = new ArrayList<>(Objects.requireNonNull(
                cargoLots, "cargoLots"));
        ArrayList<TransportOrderState> orderCopy = new ArrayList<>(Objects.requireNonNull(
                orders, "orders"));
        fleetCopy.sort(Comparator.comparing(FreighterState::fleetId));
        lotCopy.sort(Comparator.comparing(CargoLotState::lotId));
        orderCopy.sort(Comparator.comparing(TransportOrderState::orderId));
        if (fleetCopy.isEmpty() || fleetCopy.stream().anyMatch(Objects::isNull)
                || lotCopy.stream().anyMatch(Objects::isNull)
                || orderCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("freight persistence requires fleets and no null rows");
        }

        Map<FleetId, FreighterState> fleetsById = new HashMap<>();
        Set<String> ownerOrdinals = new HashSet<>();
        long maxFleetId = 0L;
        for (FreighterState fleet : fleetCopy) {
            if (fleetsById.putIfAbsent(fleet.fleetId(), fleet) != null
                    || !ownerOrdinals.add(fleet.stableFactionId() + '\u0000' + fleet.ownershipOrdinal())) {
                throw new IllegalArgumentException("freight fleet identities and ownership ordinals must be unique");
            }
            maxFleetId = Math.max(maxFleetId, fleet.fleetId().value());
        }
        if (nextFleetIdValue <= maxFleetId) {
            throw new IllegalArgumentException("nextFleetIdValue must exceed every materialized fleet ID");
        }

        Map<String, TransportOrderState> ordersById = new HashMap<>();
        Set<FleetId> orderedFleetIds = new HashSet<>();
        for (TransportOrderState order : orderCopy) {
            FreighterState fleet = fleetsById.get(order.fleetId());
            if (fleet == null || ordersById.putIfAbsent(order.orderId(), order) != null
                    || !orderedFleetIds.add(order.fleetId())) {
                throw new IllegalArgumentException("orders require unique existing physical fleets");
            }
            if (!fleet.stableFactionId().equals(order.stableFactionId())
                    || !fleet.activeOrderId().equals(order.orderId())
                    || fleet.routeIndex() >= order.orderedSystems().size()) {
                throw new IllegalArgumentException("fleet/order identity or route state differs");
            }
            StarSystemId expectedSystem = order.orderedSystems().get(fleet.routeIndex());
            if (!fleet.currentSystemId().equals(expectedSystem)) {
                throw new IllegalArgumentException("fleet current system differs from route index");
            }
        }
        for (FreighterState fleet : fleetCopy) {
            if (!fleet.activeOrderId().isEmpty() && !ordersById.containsKey(fleet.activeOrderId())) {
                throw new IllegalArgumentException("fleet references an absent transport order");
            }
        }

        Set<String> lotIds = new HashSet<>();
        Map<FleetId, Map<String, Double>> lotMassByFleetCommodity = new HashMap<>();
        long maxLotOrdinal = 0L;
        for (CargoLotState lot : lotCopy) {
            FreighterState fleet = fleetsById.get(lot.fleetId());
            TransportOrderState order = ordersById.get(lot.orderId());
            if (fleet == null || order == null || !order.fleetId().equals(lot.fleetId())
                    || !order.commodityId().equals(lot.commodityId()) || !lotIds.add(lot.lotId())) {
                throw new IllegalArgumentException("cargo lot must match one existing fleet order");
            }
            lotMassByFleetCommodity.computeIfAbsent(lot.fleetId(), ignored -> new HashMap<>())
                    .merge(lot.commodityId(), lot.massKg(), Double::sum);
            maxLotOrdinal = Math.max(maxLotOrdinal, parseLotOrdinal(lot.lotId()));
        }
        if (!lotCopy.isEmpty() && nextCargoLotOrdinal <= maxLotOrdinal) {
            throw new IllegalArgumentException("nextCargoLotOrdinal must exceed every persisted lot ordinal");
        }
        for (FreighterState fleet : fleetCopy) {
            Map<String, Double> lotMass = lotMassByFleetCommodity.getOrDefault(fleet.fleetId(), Map.of());
            if (!sameMassMap(lotMass, fleet.cargoStorage().commodityMassByIdKg())) {
                throw new IllegalArgumentException("cargo-lot provenance differs from physical hold inventory");
            }
        }

        freighters = List.copyOf(fleetCopy);
        cargoLots = List.copyOf(lotCopy);
        orders = List.copyOf(orderCopy);
    }

    /** @return stable Stage-18 storage identity for a fleet cargo hold */
    public static String cargoHoldId(FleetId fleetId) {
        return "freight-hold:" + Objects.requireNonNull(fleetId, "fleetId").value();
    }

    private static boolean sameMassMap(Map<String, Double> left, Map<String, Double> right) {
        Set<String> keys = new HashSet<>(left.keySet());
        keys.addAll(right.keySet());
        for (String key : keys) {
            double a = left.getOrDefault(key, 0d);
            double b = right.getOrDefault(key, 0d);
            double tolerance = Math.max(EPSILON, Math.max(Math.abs(a), Math.abs(b)) * 1.0e-12d);
            if (Math.abs(a - b) > tolerance) {
                return false;
            }
        }
        return true;
    }

    private static long parseLotOrdinal(String lotId) {
        int separator = lotId.lastIndexOf(':');
        if (separator < 0 || separator == lotId.length() - 1) {
            throw new IllegalArgumentException("cargo lot ID lacks a numeric ordinal");
        }
        try {
            long value = Long.parseLong(lotId.substring(separator + 1));
            if (value <= 0L) {
                throw new IllegalArgumentException("cargo lot ordinal must be positive");
            }
            return value;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("cargo lot ID has an invalid ordinal", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
