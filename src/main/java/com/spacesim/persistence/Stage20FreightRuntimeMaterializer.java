package com.spacesim.persistence;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage20FreightPersistentState.AssignmentKind;
import com.spacesim.persistence.Stage20FreightPersistentState.FreightPhase;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20FreightPersistentState.TransportOrderState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.world.FleetId;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.CommitmentKey;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.FactionFleetOwnership;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipReport;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.OwnershipSlot;
import com.spacesim.world.Stage20BootstrapFreightOwnershipPlan.RemoteCommitmentAllocation;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.AssignedFreighterSlot;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.IndustrialFreightReport;
import com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.OwnedInputFreightAllocation;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Materializes exact accepted Stage-20 freight ownership into persistent physical fleet identity,
 * ordinary transport orders and empty Stage-18 cargo holds.
 *
 * <p>This bridge consumes the already-saved Stage-20K rows plus the matching closed Stage-20F
 * specialization. It does not rerun generation, add a freighter beyond the accepted finite pool or
 * create cargo. Essential commitments keep their committed slots; industrial routes consume only
 * their explicitly assigned reserve slots. The first cargo can enter a hold only later through the
 * ordinary {@code Stage18LogisticsRuntime} transfer boundary.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20FreightRuntimeMaterializer {
    /** Stable Stage-20.5B materialization contract. */
    public static final String CURRENT_VERSION = "stage20_5.freight-runtime-materialization.v1";
    /** Explicit provisional hull/fit mapping pending the Stage-22 content pass. */
    public static final String PROVISIONAL_AUTHORITY_VERSION =
            "stage20_5.freight-compatibility.test-bulk-freighter.v1";
    /** Cargo payload retained by the current physical Stage-20 freight capacity authority. */
    public static final double CURRENT_PAYLOAD_KG = 12_000_000d;
    /** Conservative integration volume occupied by one fully loaded current payload. */
    public static final double CURRENT_PAYLOAD_INTEGRATION_VOLUME_M3 = 12_000d;

    private static final String OWNERSHIP_SLOT_DOMAIN = "FREIGHT_OWNERSHIP_SLOT";
    private static final String LOCAL_LAYOUT_DOMAIN = "LOCAL_LAYOUT";
    private static final String INFRASTRUCTURE_DOMAIN = "INFRASTRUCTURE_PLACEMENT";
    private static final String TOPOLOGY_CONNECTION_DOMAIN = "TOPOLOGY_CONNECTION";
    private static final double EPSILON = 1.0e-9d;

    private Stage20FreightRuntimeMaterializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Versioned compatibility authority for the currently available physical freighter content.
     *
     * <p>The authority is deliberately explicit because the only current cargo hull is in the
     * production-valid but content-provisional Stage-17.5I pack. Stage 22 must either promote or
     * replace it; this bridge may not silently reinterpret an unrelated trader archetype.</p>
     */
    public record FreighterCompatibilityAuthority(
            String version,
            String hullId,
            String fitId,
            String engineeringCatalogFingerprint,
            double cargoCapacityKg,
            double cargoIntegrationVolumeM3,
            Set<String> supportedStorageClassIds,
            String sourceEvidenceId,
            boolean stage22ReviewRequired) {
        /**
         * Validates one explicit provisional freight-content compatibility authority.
         *
         * @param version compatibility contract version
         * @param hullId explicit physical hull identity
         * @param fitId explicit physical fit identity
         * @param engineeringCatalogFingerprint exact engineering catalog fingerprint
         * @param cargoCapacityKg validated physical cargo capacity
         * @param cargoIntegrationVolumeM3 occupied cargo integration volume
         * @param supportedStorageClassIds compatible Stage-18 storage classes
         * @param sourceEvidenceId exact content/provenance evidence
         * @param stage22ReviewRequired mandatory provisional-content review marker
         */
        public FreighterCompatibilityAuthority {
            version = requireText(version, "version");
            hullId = requireText(hullId, "hullId");
            fitId = requireText(fitId, "fitId");
            engineeringCatalogFingerprint = requireText(
                    engineeringCatalogFingerprint, "engineeringCatalogFingerprint");
            requirePositiveFinite(cargoCapacityKg, "cargoCapacityKg");
            requirePositiveFinite(cargoIntegrationVolumeM3, "cargoIntegrationVolumeM3");
            TreeSet<String> classes = new TreeSet<>(Objects.requireNonNull(
                    supportedStorageClassIds, "supportedStorageClassIds"));
            if (classes.isEmpty() || classes.stream().anyMatch(value -> value == null || value.isBlank())) {
                throw new IllegalArgumentException("freighter compatibility requires storage classes");
            }
            supportedStorageClassIds = Set.copyOf(classes);
            sourceEvidenceId = requireText(sourceEvidenceId, "sourceEvidenceId");
            if (!stage22ReviewRequired) {
                throw new IllegalArgumentException(
                        "provisional Stage-20.5 freighter compatibility must retain Stage-22 review");
            }
        }

        /** @return the explicit current provisional compatibility authority */
        public static FreighterCompatibilityAuthority currentProvisional() {
            ShipEngineeringCatalog catalog = Stage175ICombatTestContentPack.load();
            Set<String> storageClasses = Stage18ResourceOntologyLoader.loadDefault().getStorageClasses()
                    .stream().map(value -> value.id()).collect(java.util.stream.Collectors.toSet());
            return new FreighterCompatibilityAuthority(
                    PROVISIONAL_AUTHORITY_VERSION,
                    "hull.test_bulk_freighter_v1",
                    "fit.test_bulk_freighter_baseline_v1",
                    catalog.getFingerprint(),
                    CURRENT_PAYLOAD_KG,
                    CURRENT_PAYLOAD_INTEGRATION_VOLUME_M3,
                    storageClasses,
                    "data/content/stage17_5i-combat-test-engineering-v1.json"
                            + "#fit.test_bulk_freighter_baseline_v1|stage22_review_required",
                    true);
        }
    }

    /**
     * Creates the first exact persistent physical fleet and its ordinary orders.
     *
     * @param saved exact saved generated campaign
     * @param specialization exact matching closed Stage-20F authority
     * @param firstFleetIdValue first unused world-level FleetId value
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact catalog named by the compatibility authority
     * @return empty-cargo physical fleet state ready for ordinary loading
     */
    public static Stage20FreightPersistentState materializeBootstrap(
            Stage20GeneratedCampaignPersistentState saved,
            OperationalSpecializationReport specialization,
            long firstFleetIdValue,
            FreighterCompatibilityAuthority compatibility,
            ShipEngineeringCatalog engineering) {
        Stage20GeneratedCampaignPersistentState state = requireBase(saved);
        OperationalSpecializationReport operations = Objects.requireNonNull(
                specialization, "specialization");
        FreighterCompatibilityAuthority authority = validateCompatibility(
                Objects.requireNonNull(compatibility, "compatibility"),
                Objects.requireNonNull(engineering, "engineering"));
        if (firstFleetIdValue <= 0L) {
            throw new IllegalArgumentException("firstFleetIdValue must be positive");
        }
        if (operations.rootSeed() != state.generationIdentity().worldSeed()
                || !operations.resolvedProbeVersion().equals(state.generationIdentity().generatorVersion())
                || !operations.readyForRuntimeBridge()) {
            throw new IllegalArgumentException("freight specialization differs from saved generated authority");
        }

        IndustrialFreightReport industrial = operations.yardInstallation().inventory()
                .operatingState().freightOwnership();
        OwnershipReport ownership = industrial.bootstrapOwnership();
        if (ownership.rootSeed() != state.generationIdentity().worldSeed()
                || industrial.rootSeed() != ownership.rootSeed()
                || !industrial.freightOwnershipAuthoritative()) {
            throw new IllegalArgumentException("freight materialization requires matching closed ownership");
        }
        if (Double.compare(industrial.capacityProfile().payloadMassKgPerFreighter(),
                authority.cargoCapacityKg()) != 0) {
            throw new IllegalArgumentException(
                    "compatible hull capacity differs from accepted Stage-20 physical payload");
        }

        validateSavedOwnershipRows(state, ownership);
        SavedWorldIndex world = SavedWorldIndex.parse(state);
        Map<OwnerSlotKey, IndustrialAssignment> industrialBySlot = industrialAssignments(industrial);
        Map<CommitmentKey, RemoteCommitmentAllocation> essentialByKey = essentialCommitments(ownership);

        ArrayList<FreighterState> freighters = new ArrayList<>();
        ArrayList<TransportOrderState> orders = new ArrayList<>();
        long fleetIdValue = firstFleetIdValue;
        for (FactionFleetOwnership faction : ownership.factions()) {
            for (OwnershipSlot slot : faction.materializationSlots()) {
                FleetId fleetId = new FleetId(fleetIdValue++);
                OwnerSlotKey key = new OwnerSlotKey(slot.stableFactionId(), slot.ownershipOrdinal());
                IndustrialAssignment industrialAssignment = industrialBySlot.remove(key);
                TransportOrderState order = null;
                StarSystemId initialSystem;
                String activeOrderId;
                FreightPhase phase;
                if (slot.commitment().isPresent()) {
                    if (industrialAssignment != null) {
                        throw new IllegalArgumentException("essential ownership slot was reused for industry");
                    }
                    CommitmentKey commitmentKey = slot.commitment().orElseThrow().commitmentKey();
                    RemoteCommitmentAllocation allocation = essentialByKey.get(commitmentKey);
                    if (allocation == null) {
                        throw new IllegalArgumentException("ownership slot lost essential commitment authority");
                    }
                    order = essentialOrder(
                            state, fleetId, slot, allocation, world, authority.cargoCapacityKg());
                    initialSystem = commitmentKey.producerSystemId();
                    activeOrderId = order.orderId();
                    phase = FreightPhase.AT_SOURCE;
                } else if (industrialAssignment != null) {
                    order = industrialOrder(
                            state, fleetId, slot, industrialAssignment, world, authority.cargoCapacityKg());
                    initialSystem = order.orderedSystems().get(0);
                    activeOrderId = order.orderId();
                    phase = FreightPhase.AT_SOURCE;
                } else {
                    initialSystem = faction.homeStartSystemId();
                    activeOrderId = "";
                    phase = FreightPhase.IDLE;
                }
                LocalPhysicalPosition spawn = world.majorHubPosition(initialSystem);
                StationStorageSnapshot emptyHold = emptyHold(fleetId, authority);
                freighters.add(new FreighterState(
                        fleetId,
                        slot.stableFactionId(),
                        slot.ownershipOrdinal(),
                        authority.hullId(),
                        authority.fitId(),
                        authority.cargoCapacityKg(),
                        initialSystem,
                        LocalPhysicalKinematics.stationary(spawn),
                        phase,
                        activeOrderId,
                        0,
                        emptyHold));
                if (order != null) {
                    orders.add(order);
                }
            }
        }
        if (!industrialBySlot.isEmpty()) {
            throw new IllegalArgumentException("industrial allocation references an absent ownership slot");
        }
        if (freighters.size() != ownership.totalOwnedFreighters()) {
            throw new IllegalStateException("materialized fleet count differs from exact owned pool");
        }
        return new Stage20FreightPersistentState(
                Stage20FreightPersistentState.CURRENT_VERSION,
                state.generationIdentity().worldSeed(),
                state.generationIdentity().generatorVersion(),
                state.materializedWorld().worldFingerprint(),
                CURRENT_VERSION,
                authority.version(),
                fleetIdValue,
                1L,
                freighters,
                List.of(),
                orders);
    }

    /**
     * Materializes with the explicitly marked provisional current freighter content.
     *
     * @param saved exact saved generated campaign
     * @param specialization exact matching closed Stage-20F authority
     * @param firstFleetIdValue first unused world-level FleetId value
     * @return empty-cargo physical fleet state ready for ordinary loading
     */
    public static Stage20FreightPersistentState materializeBootstrap(
            Stage20GeneratedCampaignPersistentState saved,
            OperationalSpecializationReport specialization,
            long firstFleetIdValue) {
        return materializeBootstrap(
                saved,
                specialization,
                firstFleetIdValue,
                FreighterCompatibilityAuthority.currentProvisional(),
                Stage175ICombatTestContentPack.load());
    }

    /**
     * Validates that a saved freight sidecar is still bound to the exact saved world and fleet pool.
     * This restore seam never regenerates IDs or orders.
     *
     * @param saved exact saved generated campaign
     * @param specialization exact matching closed Stage-20F authority
     * @param freight persisted Stage-20.5B sidecar
     * @param compatibility explicit hull/fit compatibility authority
     * @param engineering exact named engineering catalog
     * @return the same validated persistent freight sidecar
     */
    public static Stage20FreightPersistentState validateRestore(
            Stage20GeneratedCampaignPersistentState saved,
            OperationalSpecializationReport specialization,
            Stage20FreightPersistentState freight,
            FreighterCompatibilityAuthority compatibility,
            ShipEngineeringCatalog engineering) {
        Stage20GeneratedCampaignPersistentState state = requireBase(saved);
        Stage20FreightPersistentState persisted = Objects.requireNonNull(freight, "freight");
        FreighterCompatibilityAuthority authority = validateCompatibility(compatibility, engineering);
        if (persisted.rootSeed() != state.generationIdentity().worldSeed()
                || !persisted.generatorVersion().equals(state.generationIdentity().generatorVersion())
                || !persisted.worldFingerprint().equals(state.materializedWorld().worldFingerprint())
                || !persisted.materializationVersion().equals(CURRENT_VERSION)
                || !persisted.compatibilityAuthorityVersion().equals(authority.version())) {
            throw new IllegalArgumentException("freight sidecar differs from saved generated authority");
        }
        OperationalSpecializationReport operations = Objects.requireNonNull(
                specialization, "specialization");
        IndustrialFreightReport industrial = operations.yardInstallation().inventory()
                .operatingState().freightOwnership();
        OwnershipReport ownership = industrial.bootstrapOwnership();
        validateSavedOwnershipRows(state, ownership);
        Set<OwnerSlotKey> exactSlots = new HashSet<>();
        ownership.factions().forEach(faction -> faction.materializationSlots().forEach(slot ->
                exactSlots.add(new OwnerSlotKey(slot.stableFactionId(), slot.ownershipOrdinal()))));
        Set<OwnerSlotKey> persistedSlots = new HashSet<>();
        for (FreighterState ship : persisted.freighters()) {
            persistedSlots.add(new OwnerSlotKey(ship.stableFactionId(), ship.ownershipOrdinal()));
            if (!ship.hullId().equals(authority.hullId())
                    || !ship.fitId().equals(authority.fitId())
                    || Double.compare(ship.cargoCapacityKg(), authority.cargoCapacityKg()) != 0) {
                throw new IllegalArgumentException("persisted freighter differs from compatibility authority");
            }
        }
        if (!persistedSlots.equals(exactSlots)) {
            throw new IllegalArgumentException("persisted freighters differ from exact ownership slots");
        }
        return persisted;
    }

    private static Stage20GeneratedCampaignPersistentState requireBase(
            Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState state = Objects.requireNonNull(saved, "saved");
        if (!state.openRuntimeBoundaries().contains(OpenRuntimeBoundary.FREIGHT_FLEET_MATERIALIZATION)
                || !state.openRuntimeBoundaries().contains(
                OpenRuntimeBoundary.CARGO_ORDER_AND_LOT_MATERIALIZATION)) {
            throw new IllegalArgumentException("saved campaign does not expose both freight boundaries");
        }
        if (!state.generationIdentity().contentFingerprint()
                .equals(Stage18IndustrialContentFingerprint.current())) {
            throw new IllegalArgumentException("saved industrial content differs from installed Stage-18 runtime");
        }
        return state;
    }

    private static FreighterCompatibilityAuthority validateCompatibility(
            FreighterCompatibilityAuthority authority,
            ShipEngineeringCatalog engineering) {
        FreighterCompatibilityAuthority result = Objects.requireNonNull(authority, "authority");
        ShipEngineeringCatalog catalog = Objects.requireNonNull(engineering, "engineering");
        if (!result.engineeringCatalogFingerprint().equals(catalog.getFingerprint())) {
            throw new IllegalArgumentException("freighter compatibility catalog fingerprint differs");
        }
        var fit = catalog.findDemonstratorFit(result.fitId());
        var hull = catalog.findHull(result.hullId());
        if (fit == null || hull == null || !fit.hullId().equals(hull.id())) {
            throw new IllegalArgumentException("freighter compatibility hull/fit mapping is absent");
        }
        var derived = new DerivedShipCalculator(catalog).deriveDemonstrator(
                result.fitId(),
                new ConsumableState(
                        result.cargoCapacityKg(), 0d, 0d,
                        result.cargoIntegrationVolumeM3(), List.of()),
                DamageState.pristine());
        if (!derived.hullId().equals(result.hullId())
                || Double.compare(derived.cargoMassKg(), result.cargoCapacityKg()) != 0
                || derived.totalMassKg() > hull.maxOperationalMassKg() + EPSILON
                || derived.remainingIntegrationVolumeM3() < -EPSILON) {
            throw new IllegalArgumentException("freighter compatibility exceeds physical hull/fit envelope");
        }
        var ontology = Stage18ResourceOntologyLoader.loadDefault();
        for (String storageClass : result.supportedStorageClassIds()) {
            if (ontology.findStorageClass(storageClass) == null) {
                throw new IllegalArgumentException("unknown compatible storage class: " + storageClass);
            }
        }
        return result;
    }

    private static Map<CommitmentKey, RemoteCommitmentAllocation> essentialCommitments(
            OwnershipReport ownership) {
        Map<CommitmentKey, RemoteCommitmentAllocation> result = new HashMap<>();
        ownership.factions().forEach(faction -> faction.remoteCommitments().forEach(allocation -> {
            if (result.putIfAbsent(allocation.commitmentKey(), allocation) != null) {
                throw new IllegalArgumentException("duplicate essential freight commitment");
            }
        }));
        return Map.copyOf(result);
    }

    private static Map<OwnerSlotKey, IndustrialAssignment> industrialAssignments(
            IndustrialFreightReport industrial) {
        Map<OwnerSlotKey, IndustrialAssignment> result = new HashMap<>();
        for (OwnedInputFreightAllocation allocation : industrial.allocations()) {
            for (AssignedFreighterSlot slot : allocation.assignedSlots()) {
                OwnerSlotKey key = new OwnerSlotKey(slot.stableFactionId(), slot.ownershipOrdinal());
                if (result.putIfAbsent(key, new IndustrialAssignment(allocation, slot)) != null) {
                    throw new IllegalArgumentException("industrial freight slot assigned more than once");
                }
            }
        }
        return result;
    }

    private static TransportOrderState essentialOrder(
            Stage20GeneratedCampaignPersistentState state,
            FleetId fleetId,
            OwnershipSlot slot,
            RemoteCommitmentAllocation allocation,
            SavedWorldIndex world,
            double payloadKg) {
        CommitmentKey key = allocation.commitmentKey();
        RouteAssessment route = allocation.route();
        world.requireNeighborRoute(route.orderedSystems());
        double roundTrip = roundTrip(payloadKg, allocation.allocatedFreighters(),
                route.sustainableCargoThroughputKgPerSecond(), route.travelTimeS());
        String orderId = "freight-order:essential:" + slot.stableFactionId() + ':'
                + slot.ownershipOrdinal();
        return new TransportOrderState(
                orderId,
                fleetId,
                slot.stableFactionId(),
                AssignmentKind.ESSENTIAL_BOOTSTRAP,
                key.commodityId(),
                world.majorHubId(key.producerSystemId()),
                world.majorHubId(key.consumerStartSystemId()),
                commitmentIdentity(key),
                route.orderedSystems(),
                route.travelTimeS(),
                roundTrip,
                route.travelTimeS(),
                0d,
                0L);
    }

    private static TransportOrderState industrialOrder(
            Stage20GeneratedCampaignPersistentState state,
            FleetId fleetId,
            OwnershipSlot slot,
            IndustrialAssignment assignment,
            SavedWorldIndex world,
            double payloadKg) {
        var demand = assignment.allocation().demand();
        RouteAssessment route = demand.minimumCapacityRoute().orElseThrow();
        world.requireNeighborRoute(route.orderedSystems());
        int count = assignment.allocation().assignedSlots().size();
        double roundTrip = roundTrip(
                payloadKg, count, route.sustainableCargoThroughputKgPerSecond(), route.travelTimeS());
        String orderId = "freight-order:industrial:" + slot.stableFactionId() + ':'
                + slot.ownershipOrdinal();
        return new TransportOrderState(
                orderId,
                fleetId,
                slot.stableFactionId(),
                AssignmentKind.INDUSTRIAL_INPUT,
                demand.input().inputCommodityId(),
                world.majorHubId(demand.input().supplyKey().systemId()),
                demand.input().process().stationPlacementId(),
                industrialIdentity(demand.input()),
                route.orderedSystems(),
                route.travelTimeS(),
                roundTrip,
                route.travelTimeS(),
                0d,
                0L);
    }

    private static double roundTrip(
            double payloadKg, int allocatedFreighters, double sustainableThroughputKgPerSecond,
            double oneWaySeconds) {
        double cycle = payloadKg * allocatedFreighters / sustainableThroughputKgPerSecond;
        if (!Double.isFinite(cycle) || cycle <= oneWaySeconds) {
            throw new IllegalArgumentException("retained freight cadence is not a physical round trip");
        }
        return cycle;
    }

    private static StationStorageSnapshot emptyHold(
            FleetId fleetId,
            FreighterCompatibilityAuthority authority) {
        TreeMap<String, Double> capacity = new TreeMap<>();
        authority.supportedStorageClassIds().forEach(value ->
                capacity.put(value, authority.cargoCapacityKg()));
        return new StationStorageSnapshot(
                Stage20FreightPersistentState.cargoHoldId(fleetId),
                capacity,
                Map.of(),
                Map.of());
    }

    private static void validateSavedOwnershipRows(
            Stage20GeneratedCampaignPersistentState state,
            OwnershipReport ownership) {
        TreeMap<String, List<String>> savedSlots = new TreeMap<>();
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (OWNERSHIP_SLOT_DOMAIN.equals(row.domain())
                    && savedSlots.putIfAbsent(row.stableId(), row.values()) != null) {
                throw new IllegalArgumentException("duplicate saved freight ownership slot");
            }
        }
        TreeMap<String, List<String>> expectedSlots = new TreeMap<>();
        for (FactionFleetOwnership faction : ownership.factions()) {
            for (OwnershipSlot slot : faction.materializationSlots()) {
                ArrayList<String> values = new ArrayList<>();
                values.add(slot.stableFactionId());
                values.add(Integer.toString(slot.ownershipOrdinal()));
                if (slot.commitment().isPresent()) {
                    CommitmentKey key = slot.commitment().orElseThrow().commitmentKey();
                    values.add("COMMITTED");
                    values.add(key.frontierVersion());
                    values.add(key.optionId());
                    values.add(key.stableFactionId());
                    values.add(key.commodityId());
                    values.add(Long.toString(key.producerSystemId().value()));
                    values.add(Long.toString(key.consumerStartSystemId().value()));
                    values.add(Integer.toString(key.sourceCommitmentOrdinal()));
                    values.add(Integer.toString(slot.commitment().orElseThrow().freighterOrdinal()));
                } else {
                    values.add("RESERVE");
                }
                expectedSlots.put(slot.stableFactionId() + ':' + slot.ownershipOrdinal(), List.copyOf(values));
            }
        }
        if (!savedSlots.equals(expectedSlots)) {
            throw new IllegalArgumentException("saved freight ownership slots differ from accepted authority");
        }
    }

    private static String commitmentIdentity(CommitmentKey key) {
        return "essential:" + key.frontierVersion() + ':' + key.optionId() + ':'
                + key.stableFactionId() + ':' + key.commodityId() + ':'
                + key.producerSystemId().value() + ':' + key.consumerStartSystemId().value() + ':'
                + key.sourceCommitmentOrdinal();
    }

    private static String industrialIdentity(
            com.spacesim.world.Stage20IndustrialInputFreightOwnershipPlan.InputFreightKey key) {
        return "industrial:" + key.process().systemId().value() + ':'
                + key.process().stationPlacementId() + ':' + key.process().processId() + ':'
                + key.inputCommodityId() + ':' + key.supplyKey().systemId().value();
    }

    private record OwnerSlotKey(String stableFactionId, int ownershipOrdinal) { }

    private record IndustrialAssignment(
            OwnedInputFreightAllocation allocation,
            AssignedFreighterSlot slot) { }

    private static final class SavedWorldIndex {
        private final Map<StarSystemId, String> majorHubBySystem;
        private final Map<String, LocalPhysicalPosition> infrastructurePositionById;
        private final Set<SystemPair> topologyConnections;

        private SavedWorldIndex(
                Map<StarSystemId, String> majorHubBySystem,
                Map<String, LocalPhysicalPosition> infrastructurePositionById,
                Set<SystemPair> topologyConnections) {
            this.majorHubBySystem = Map.copyOf(majorHubBySystem);
            this.infrastructurePositionById = Map.copyOf(infrastructurePositionById);
            this.topologyConnections = Set.copyOf(topologyConnections);
        }

        static SavedWorldIndex parse(Stage20GeneratedCampaignPersistentState state) {
            TreeMap<StarSystemId, String> hubs = new TreeMap<>();
            TreeMap<String, LocalPhysicalPosition> positions = new TreeMap<>();
            HashSet<SystemPair> connections = new HashSet<>();
            for (CanonicalRow row : state.materializedWorld().worldRows()) {
                List<String> values = row.values();
                if (LOCAL_LAYOUT_DOMAIN.equals(row.domain())) {
                    requireValueCountAtLeast(row, 3);
                    hubs.put(new StarSystemId(parsePositiveLong(row.stableId(), row, "systemId")),
                            requireText(values.get(2), "majorHubId"));
                } else if (INFRASTRUCTURE_DOMAIN.equals(row.domain())) {
                    requireValueCountAtLeast(row, 7);
                    StarSystemId systemId = new StarSystemId(parsePositiveLong(
                            values.get(0), row, "systemId"));
                    positions.put(systemId.value() + ":" + infrastructureId(row.stableId()),
                            new LocalPhysicalPosition(
                                    parseLong(values.get(3), row, "cellX"),
                                    parseLong(values.get(4), row, "cellY"),
                                    parseDouble(values.get(5), row, "offsetXM"),
                                    parseDouble(values.get(6), row, "offsetYM")));
                } else if (TOPOLOGY_CONNECTION_DOMAIN.equals(row.domain())) {
                    requireValueCountAtLeast(row, 2);
                    connections.add(new SystemPair(
                            new StarSystemId(parsePositiveLong(values.get(0), row, "firstSystemId")),
                            new StarSystemId(parsePositiveLong(values.get(1), row, "secondSystemId"))));
                }
            }
            if (hubs.isEmpty() || connections.isEmpty()) {
                throw new IllegalArgumentException("saved world lacks local hubs or topology connections");
            }
            SavedWorldIndex result = new SavedWorldIndex(hubs, positions, connections);
            hubs.keySet().forEach(result::majorHubPosition);
            return result;
        }

        String majorHubId(StarSystemId systemId) {
            String result = majorHubBySystem.get(systemId);
            if (result == null) {
                throw new IllegalArgumentException("saved world lacks major hub for " + systemId);
            }
            return result;
        }

        LocalPhysicalPosition majorHubPosition(StarSystemId systemId) {
            String id = majorHubId(systemId);
            LocalPhysicalPosition position = infrastructurePositionById.get(systemId.value() + ":" + id);
            if (position == null) {
                throw new IllegalArgumentException("saved world lacks physical major hub placement: " + id);
            }
            return position;
        }

        void requireNeighborRoute(List<StarSystemId> orderedSystems) {
            if (orderedSystems.size() < 2) {
                throw new IllegalArgumentException("freight order route must be remote");
            }
            for (int index = 1; index < orderedSystems.size(); index++) {
                if (!topologyConnections.contains(new SystemPair(
                        orderedSystems.get(index - 1), orderedSystems.get(index)))) {
                    throw new IllegalArgumentException("freight route contains a non-neighbor hop");
                }
            }
        }

        private static String infrastructureId(String stableId) {
            int separator = stableId.indexOf(':');
            if (separator < 0 || separator == stableId.length() - 1) {
                throw new IllegalArgumentException("malformed infrastructure stable ID: " + stableId);
            }
            return stableId.substring(separator + 1);
        }
    }

    private record SystemPair(StarSystemId first, StarSystemId second) {
        private SystemPair {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.equals(second)) {
                throw new IllegalArgumentException("topology connection endpoints must differ");
            }
            if (first.compareTo(second) > 0) {
                StarSystemId swap = first;
                first = second;
                second = swap;
            }
        }
    }

    private static void requireValueCountAtLeast(CanonicalRow row, int count) {
        if (row.values().size() < count) {
            throw new IllegalArgumentException("malformed " + row.domain() + " row: " + row.stableId());
        }
    }

    private static long parsePositiveLong(String value, CanonicalRow row, String field) {
        long parsed = parseLong(value, row, field);
        if (parsed <= 0L) {
            throw new IllegalArgumentException(field + " must be positive in " + row.stableId());
        }
        return parsed;
    }

    private static long parseLong(String value, CanonicalRow row, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is invalid in " + row.stableId(), exception);
        }
    }

    private static double parseDouble(String value, CanonicalRow row, String field) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("non-finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(field + " is invalid in " + row.stableId(), exception);
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
}
