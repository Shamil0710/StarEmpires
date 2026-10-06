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
 * @param personalMiningOrders actual persistent personal excavation intents
 * @param productLots provenance of actual countable finished goods aboard personal fleets
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
        List<TransportOrderState> orders,
        List<PersonalMiningOrder> personalMiningOrders,
        List<ProductCargoLotState> productLots) {
    /** Current physical-freight persistence schema. */
    public static final int CURRENT_VERSION = 7;
    private static final double EPSILON = 1.0e-9d;

    /**
     * Source-compatible constructor for checkpoints without finished-product cargo.
     * @param schemaVersion owning freight schema
     * @param rootSeed exact generated seed
     * @param generatorVersion exact generated version
     * @param worldFingerprint exact world fingerprint
     * @param materializationVersion materialization authority version
     * @param compatibilityAuthorityVersion physical hull/fit authority version
     * @param nextFleetIdValue next persistent fleet identity
     * @param nextCargoLotOrdinal next shared cargo-lot ordinal
     * @param freighters physical fleet owners
     * @param cargoLots raw commodity provenance
     * @param orders ordinary freight routes
     * @param personalMiningOrders personal mining work
     */
    public Stage20FreightPersistentState(int schemaVersion, long rootSeed, String generatorVersion,
            String worldFingerprint, String materializationVersion, String compatibilityAuthorityVersion,
            long nextFleetIdValue, long nextCargoLotOrdinal, List<FreighterState> freighters,
            List<CargoLotState> cargoLots, List<TransportOrderState> orders, List<PersonalMiningOrder> personalMiningOrders) {
        this(schemaVersion, rootSeed, generatorVersion, worldFingerprint, materializationVersion,
                compatibilityAuthorityVersion, nextFleetIdValue, nextCargoLotOrdinal, freighters,
                cargoLots, orders, personalMiningOrders, List.of());
    }

    /**
     * Physical countable products, distinct from raw commodity mass and individual used modules.
     * @param lotId globally unique shared cargo identity
     * @param fleetId actual carrying fleet
     * @param productId authored countable product
     * @param count positive retained whole units
     * @param sourceEndpointId actual original loading station
     * @param loadedAtSimulationSeconds original authoritative loading time
     */
    public record ProductCargoLotState(String lotId, FleetId fleetId, String productId, int count,
            String sourceEndpointId, double loadedAtSimulationSeconds) {
        /**
         * Validates finite source evidence for an existing physical product lot.
         * @param lotId globally unique shared cargo identity
         * @param fleetId actual carrying fleet
         * @param productId authored countable product
         * @param count positive retained whole units
         * @param sourceEndpointId actual original loading station
         * @param loadedAtSimulationSeconds original authoritative loading time
         */
        public ProductCargoLotState {
            lotId = requireText(lotId, "product lot ID"); Objects.requireNonNull(fleetId);
            productId = requireText(productId, "product ID"); sourceEndpointId = requireText(sourceEndpointId, "source endpoint");
            if (count <= 0 || com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts().findProduct(productId) == null)
                throw new IllegalArgumentException("Unknown or empty finished product lot");
            requireNonNegativeFinite(loadedAtSimulationSeconds, "product loading time");
            parseLotOrdinal(lotId);
        }
    }

    /**
     * Source-compatible constructor for checkpoints without personal mining work.
     * @param schemaVersion freight schema
     * @param rootSeed generated seed
     * @param generatorVersion generated-world version
     * @param worldFingerprint generated-world fingerprint
     * @param materializationVersion materialization version
     * @param compatibilityAuthorityVersion hull/fit authority version
     * @param nextFleetIdValue next unused fleet identity
     * @param nextCargoLotOrdinal next unused cargo-lot ordinal
     * @param freighters exact finite fleets
     * @param cargoLots exact aboard cargo
     * @param orders exact transport orders
     */
    public Stage20FreightPersistentState(int schemaVersion, long rootSeed, String generatorVersion,
            String worldFingerprint, String materializationVersion, String compatibilityAuthorityVersion,
            long nextFleetIdValue, long nextCargoLotOrdinal, List<FreighterState> freighters,
            List<CargoLotState> cargoLots, List<TransportOrderState> orders) {
        this(schemaVersion, rootSeed, generatorVersion, worldFingerprint, materializationVersion,
                compatibilityAuthorityVersion, nextFleetIdValue, nextCargoLotOrdinal, freighters,
                cargoLots, orders, List.of());
    }

    /**
     * SI extraction intent; creation awards no inventory, work or discovery.
     * @param fleetId actual personal fleet
     * @param sourceId contacted occurrence
     * @param methodId authored extraction method
     * @param requestedSourceKgPerTick gross-mass request per tick
     * @param lastProcessedTick last completed or assigned tick
     */
    public record PersonalMiningOrder(FleetId fleetId, String sourceId, String methodId,
            double requestedSourceKgPerTick, long lastProcessedTick) {
        /**
         * Validates an immutable excavation intent.
         * @param fleetId actual personal fleet
         * @param sourceId contacted occurrence
         * @param methodId authored extraction method
         * @param requestedSourceKgPerTick finite positive gross-mass request
         * @param lastProcessedTick non-negative tick watermark
         */
        public PersonalMiningOrder {
            Objects.requireNonNull(fleetId, "fleetId");
            sourceId = requireText(sourceId, "sourceId"); methodId = requireText(methodId, "methodId");
            requirePositiveFinite(requestedSourceKgPerTick, "requestedSourceKgPerTick");
            if (lastProcessedTick < 0) throw new IllegalArgumentException("Negative mining tick");
        }
    }

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
     * @param stableFactionId immutable accepted bootstrap pool origin
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
     * @param legalFactionId redundant legal-faction mirror; ordinary world affiliation remains canonical
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
            StationStorageSnapshot cargoStorage,
            String legalFactionId, double carriedEquipmentMassKg) {
        /**
         * Source-compatible commodity-only cargo; grants no carried equipment.
         * @param fleetId actual fleet
         * @param stableFactionId original pool faction
         * @param ownershipOrdinal original slot
         * @param hullId original hull
         * @param fitId actual fitting identifier
         * @param cargoCapacityKg full hold capacity
         * @param currentSystemId physical system
         * @param physicalState actual kinematics
         * @param phase actual freight lifecycle
         * @param activeOrderId ordinary route order
         * @param routeIndex actual route progress
         * @param cargoStorage commodity hold
         * @param legalFactionId actual legal affiliation
         */
        public FreighterState(FleetId fleetId, String stableFactionId, int ownershipOrdinal, String hullId,
                String fitId, double cargoCapacityKg, StarSystemId currentSystemId, LocalPhysicalKinematics physicalState,
                FreightPhase phase, String activeOrderId, int routeIndex, StationStorageSnapshot cargoStorage, String legalFactionId) {
            this(fleetId, stableFactionId, ownershipOrdinal, hullId, fitId, cargoCapacityKg, currentSystemId,
                    physicalState, phase, activeOrderId, routeIndex, cargoStorage, legalFactionId, 0);
        }
        /**
         * Source-compatible bootstrap constructor; original pool faction is also initial legal faction.
         * @param fleetId ordinary fleet identity
         * @param stableFactionId immutable accepted bootstrap pool origin
         * @param ownershipOrdinal original pool slot
         * @param hullId compatible hull
         * @param fitId compatible fit
         * @param cargoCapacityKg physical capacity
         * @param currentSystemId physical system
         * @param physicalState exact kinematics
         * @param phase freight lifecycle phase
         * @param activeOrderId assigned ordinary order or empty
         * @param routeIndex route index
         * @param cargoStorage existing owning cargo snapshot
         */
        public FreighterState(FleetId fleetId, String stableFactionId, int ownershipOrdinal, String hullId,
                String fitId, double cargoCapacityKg, StarSystemId currentSystemId,
                LocalPhysicalKinematics physicalState, FreightPhase phase, String activeOrderId,
                int routeIndex, StationStorageSnapshot cargoStorage) {
            this(fleetId, stableFactionId, ownershipOrdinal, hullId, fitId, cargoCapacityKg,
                    currentSystemId, physicalState, phase, activeOrderId, routeIndex, cargoStorage, stableFactionId);
        }

        /**
         * Validates one persistent physical freighter row.
         *
         * @param fleetId ordinary world-level fleet identity
         * @param stableFactionId immutable accepted bootstrap pool origin
         * @param ownershipOrdinal exact accepted owned-pool ordinal
         * @param hullId explicit compatible physical hull
         * @param fitId explicit compatible physical fit
         * @param cargoCapacityKg hull/fit-validated cargo capacity
         * @param currentSystemId current physical system
         * @param physicalState exact local physical kinematics
         * @param phase current route lifecycle phase
         * @param activeOrderId assigned order or empty for reserve fleet
         * @param routeIndex current order route index
         * @param cargoStorage exact Stage-18 cargo-hold snapshot
         * @param legalFactionId exact canonical world legal affiliation mirror
         * @param carriedEquipmentMassKg full mass of individually tracked equipment in the composed campaign
         */
        public FreighterState {
            Objects.requireNonNull(fleetId, "fleetId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            legalFactionId = requireText(legalFactionId, "legalFactionId");
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
            requireNonNegativeFinite(carriedEquipmentMassKg, "carriedEquipmentMassKg");
            if (carriedEquipmentMassKg > 0 && phase != FreightPhase.IDLE)
                throw new IllegalArgumentException("Individual equipment requires an operational personal idle freight fleet");
            if (!cargoStorage.stationId().equals(cargoHoldId(fleetId))) {
                throw new IllegalArgumentException("cargo storage identity must derive from FleetId");
            }
            double storedMass = cargoStorage.commodityMassByIdKg().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum();
            storedMass += productMassKg(cargoStorage.productCountById());
            if (!cargoStorage.productCountById().isEmpty() && phase != FreightPhase.IDLE)
                throw new IllegalArgumentException("Finished-product cargo requires an idle personal freight fleet");
            if (storedMass + carriedEquipmentMassKg > cargoCapacityKg + EPSILON) {
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

        /** @return total physical commodity and individual equipment mass aboard this fleet */
        public double cargoMassKg() {
            return cargoStorage.commodityMassByIdKg().values().stream()
                    .mapToDouble(Double::doubleValue)
                    .sum() + productMassKg(cargoStorage.productCountById()) + carriedEquipmentMassKg;
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
        /**
         * Validates one persistent physical cargo lot.
         *
         * @param lotId stable campaign-local lot identity
         * @param fleetId carrying fleet
         * @param orderId authorizing order
         * @param commodityId Stage-18 commodity identity
         * @param massKg current conserved lot mass
         * @param sourceEndpointId physical loading endpoint
         * @param sourceProvenanceId accepted upstream provenance
         * @param loadedAtSimulationSeconds authoritative loading time
         */
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
        /**
         * Validates one ordinary persistent transport order.
         *
         * @param orderId stable order identity
         * @param fleetId assigned real fleet
         * @param stableFactionId exact owner
         * @param assignmentKind accepted planning source
         * @param commodityId transported Stage-18 commodity
         * @param sourceEndpointId physical source endpoint
         * @param destinationEndpointId physical destination endpoint
         * @param sourceProvenanceId accepted source provenance
         * @param orderedSystems explicit producer-to-consumer route
         * @param oneWayDeliverySeconds retained one-way delivery time
         * @param roundTripCycleSeconds retained ready-again cycle
         * @param deliveryDeadlineSeconds current delivery deadline
         * @param deliveredMassKg mass actually delivered
         * @param delayedDeliveryCount observed missed deadlines
         */
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

    /**
     * Validates and canonicalizes one complete freight sidecar.
     *
     * @param schemaVersion freight-sidecar schema version
     * @param rootSeed exact generated campaign seed
     * @param generatorVersion exact generated-world version
     * @param worldFingerprint exact generated-world fingerprint
     * @param materializationVersion Stage-20.5B bridge version
     * @param compatibilityAuthorityVersion explicit hull/fit authority version
     * @param nextFleetIdValue next unused persistent FleetId value
     * @param nextCargoLotOrdinal next unused cargo-lot ordinal
     * @param freighters complete finite freight fleet
     * @param cargoLots current physical cargo provenance
     * @param orders ordinary persistent transport orders
     * @param personalMiningOrders actual persistent personal excavation intents
     * @param productLots actual countable finished-product provenance
     */
    public Stage20FreightPersistentState {
        if (schemaVersion < 1 || schemaVersion > CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20.5B freight schema: " + schemaVersion);
        }
        boolean historical = schemaVersion == 1;
        if (schemaVersion < 7 && (!Objects.requireNonNull(productLots).isEmpty()
                || freighters.stream().anyMatch(f -> !f.cargoStorage().productCountById().isEmpty())))
            throw new IllegalArgumentException("Finished-product cargo requires freight schema 7");
        if (schemaVersion < 6 && freighters.stream().anyMatch(f -> f.carriedEquipmentMassKg() != 0))
            throw new IllegalArgumentException("Individual equipment requires freight schema 6");
        boolean supportsExtraction = schemaVersion >= 4;
        if (schemaVersion < 5 && !Objects.requireNonNull(personalMiningOrders, "personalMiningOrders").isEmpty())
            throw new IllegalArgumentException("Personal mining work requires freight schema v5");
        if (schemaVersion < 3 && freighters.stream().anyMatch(f -> !f.legalFactionId().equals(f.stableFactionId())))
            throw new IllegalArgumentException("Historical freight cannot contain explicit legal affiliation");
        schemaVersion = CURRENT_VERSION;
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
        var miningCopy = new ArrayList<>(Objects.requireNonNull(personalMiningOrders, "personalMiningOrders"));
        miningCopy.sort(Comparator.comparing(PersonalMiningOrder::fleetId));
        var miningFleetIds = new HashSet<FleetId>();
        for (var mining : miningCopy) {
            var fleet = fleetsById.get(mining.fleetId());
            if (fleet == null || fleet.phase() != FreightPhase.IDLE || !miningFleetIds.add(mining.fleetId()))
                throw new IllegalArgumentException("Mining requires a unique idle physical fleet");
        }
        personalMiningOrders = List.copyOf(miningCopy);

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

        Set<String> lotIds = new HashSet<>();
        var productCopy = new ArrayList<>(Objects.requireNonNull(productLots));
        productCopy.sort(Comparator.comparing(ProductCargoLotState::lotId));
        var countsByFleet = new HashMap<FleetId, Map<String, Integer>>();
        for (var lot : productCopy) {
            var fleet = fleetsById.get(lot.fleetId());
            if (fleet == null || fleet.phase() != FreightPhase.IDLE || !lotIds.add(lot.lotId()))
                throw new IllegalArgumentException("Finished-product provenance requires a unique existing personal cargo owner");
            countsByFleet.computeIfAbsent(lot.fleetId(), ignored -> new java.util.TreeMap<>())
                    .merge(lot.productId(), lot.count(), Math::addExact);
            if (nextCargoLotOrdinal <= parseLotOrdinal(lot.lotId()))
                throw new IllegalArgumentException("Product lot allocator must exceed retained identities");
        }
        for (var fleet : fleetCopy) if (!fleet.cargoStorage().productCountById()
                .equals(countsByFleet.getOrDefault(fleet.fleetId(), Map.of())))
            throw new IllegalArgumentException("Finished-product provenance differs from the physical hold");
        productLots = List.copyOf(productCopy);
        for (FreighterState fleet : fleetCopy) {
            if (!fleet.activeOrderId().isEmpty() && !ordersById.containsKey(fleet.activeOrderId())) {
                throw new IllegalArgumentException("fleet references an absent transport order");
            }
        }

        Map<FleetId, Map<String, Double>> lotMassByFleetCommodity = new HashMap<>();
        long maxLotOrdinal = 0L;
        for (CargoLotState lot : lotCopy) {
            FreighterState fleet = fleetsById.get(lot.fleetId());
            TransportOrderState order = ordersById.get(lot.orderId());
            boolean manual = fleet != null && fleet.phase() == FreightPhase.IDLE
                    && lot.orderId().equals(manualCargoOrderId(fleet.fleetId()))
                    && lot.sourceProvenanceId().equals("player-market:" + lot.sourceEndpointId());
            boolean extracted = fleet != null && fleet.phase() == FreightPhase.IDLE
                    && lot.orderId().equals(personalExtractionOrderId(fleet.fleetId()))
                    && lot.sourceProvenanceId().equals("player-extraction:" + lot.sourceEndpointId());
            if (historical && manual) throw new IllegalArgumentException("Manual cargo requires freight schema v2");
            if (extracted && !supportsExtraction) throw new IllegalArgumentException("Extracted personal cargo requires freight schema v4");
            if (fleet == null || !manual && !extracted && (order == null || !order.fleetId().equals(lot.fleetId())
                    || !order.commodityId().equals(lot.commodityId())) || !lotIds.add(lot.lotId())) {
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

    /**
     * Derives the stable Stage-18 storage identity for a fleet cargo hold.
     *
     * @param fleetId real carrying fleet identity
     * @return stable Stage-18 cargo-hold storage identity
     */
    public static String cargoHoldId(FleetId fleetId) {
        return "freight-hold:" + Objects.requireNonNull(fleetId, "fleetId").value();
    }

    private static double productMassKg(Map<String, Integer> counts) {
        var products = com.spacesim.content.Stage22CivilianMiningProductionPath.loadProducts();
        double mass = 0d;
        for (var entry : counts.entrySet()) {
            var product = products.findProduct(entry.getKey());
            if (product == null || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid finished-product cargo");
            mass += product.unitMassKg() * entry.getValue();
        }
        if (!Double.isFinite(mass)) throw new IllegalArgumentException("Finished-product cargo mass overflow");
        return mass;
    }

    /**
     * Identifies manually purchased cargo on one existing reserve fleet.
     * @param fleetId carrying reserve fleet
     * @return ordinary manually purchased cargo provenance identity
     */
    public static String manualCargoOrderId(FleetId fleetId) { return "player-market-cargo:" + fleetId.value(); }

    /**
     * Identifies physical extraction lots without disguising them as purchased cargo.
     * @param fleetId carrying fleet
     * @return personal extraction provenance bucket
     */
    public static String personalExtractionOrderId(FleetId fleetId) { return "player-extraction-cargo:" + fleetId.value(); }

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
