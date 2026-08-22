package com.spacesim.persistence;

import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18ShipyardCatalogLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.economy.Stage18FacilityRuntime;
import com.spacesim.economy.Stage18FacilityRuntime.FacilityCapabilitySnapshot;
import com.spacesim.economy.Stage18FacilityRuntime.InstalledFacilityState;
import com.spacesim.economy.Stage18ShipyardRuntime;
import com.spacesim.economy.Stage18ShipyardRuntime.InstalledYardState;
import com.spacesim.economy.Stage18ShipyardRuntime.YardCapabilitySnapshot;
import com.spacesim.economy.Stage18StationIndustrialNode;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.FacilityInstallationSnapshot;
import com.spacesim.persistence.Stage18IndustrialState.YardInstallationSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.FacilityStateAssignment;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialInitialInventoryPlan.StationInventoryEvidence;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.InstalledYardEvidence;
import com.spacesim.world.Stage20IndustrialShipyardInstallationPlan.SupportFacilityEvidence;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Collections;
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
 * Stage-20.5C bridge from accepted generated industrial authority to ordinary Stage-18 runtime
 * station, storage, facility and shipyard state.
 *
 * <p>Bootstrap consumes the already accepted Stage-20F specialization object and cross-checks it
 * against the saved Stage-20K canonical world. Resume consumes only the saved Stage-18 industrial
 * state plus canonical Stage-20K identities. Neither path regenerates station placement, owner,
 * inventory, facility allocation or yard allocation.</p>
 */
public final class Stage20IndustrialEntityMaterializer {
    /** Stable Stage-20.5C materialization contract version. */
    public static final String CURRENT_VERSION = "stage20_5.industrial-entity-materialization.v1";
    private static final String GENERATED_LOCATION_TAG =
            Stage20IndustrialFacilityOperatingPlan.GENERATED_STATION_LOCATION_TAG;

    private Stage20IndustrialEntityMaterializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Materializes the first live industrial registry from the accepted Stage-20F authority.
     *
     * @param saved exact Stage-20K campaign authority
     * @param specialization exact closed Stage-20F operational specialization used to capture it
     * @return deterministic live Stage-18 industrial registry
     */
    public static MaterializedIndustrialRegistry materializeBootstrap(
            Stage20GeneratedCampaignPersistentState saved,
            OperationalSpecializationReport specialization) {
        Stage20GeneratedCampaignPersistentState state = requireBase(saved);
        OperationalSpecializationReport operations = Objects.requireNonNull(
                specialization, "specialization");
        if (operations.rootSeed() != state.generationIdentity().worldSeed()
                || !operations.resolvedProbeVersion().equals(state.generationIdentity().generatorVersion())
                || !operations.readyForRuntimeBridge()) {
            throw new IllegalArgumentException(
                    "industrial specialization differs from saved generated authority");
        }

        var yardReport = operations.yardInstallation();
        var inventory = yardReport.inventory();
        var operating = inventory.operatingState();
        if (!yardReport.operationallyAuthoritative()
                || !inventory.initialInventoryAuthoritative()
                || !operating.facilityOperatingStateAuthoritative()) {
            throw new IllegalArgumentException("Stage-20.5C requires fully closed Stage-20F authority");
        }

        TreeMap<String, FacilityStateAssignment> facilities = new TreeMap<>();
        for (var facility : operating.facilities()) {
            putFacility(facilities, facility.assignment());
        }
        for (var station : yardReport.stations()) {
            for (SupportFacilityEvidence support : station.supports()) {
                putFacility(facilities, support.assignment());
            }
        }
        TreeMap<String, InstalledYardEvidence> yards = new TreeMap<>();
        for (var station : yardReport.stations()) {
            for (InstalledYardEvidence yard : station.yards()) {
                if (yards.putIfAbsent(yard.assignment().state().yardInstanceId(), yard) != null) {
                    throw new IllegalArgumentException("duplicate accepted yard instance");
                }
            }
        }

        TreeMap<String, String> ownerByStation = new TreeMap<>();
        operations.specializations().forEach(value -> mergeOwner(
                ownerByStation,
                value.key().station().stationPlacementId(),
                value.key().stableFactionId()));
        facilities.values().forEach(value -> mergeOwner(
                ownerByStation, value.slot().station().stationPlacementId(), value.stableFactionId()));
        yards.values().forEach(value -> mergeOwner(
                ownerByStation,
                value.assignment().slot().station().stationPlacementId(),
                value.assignment().stableFactionId()));

        ArrayList<MaterializedIndustrialStation> stations = new ArrayList<>();
        for (StationInventoryEvidence stationInventory : inventory.stations()) {
            StationKey key = stationInventory.assignment().station();
            String stationId = key.stationPlacementId();
            CanonicalStation canonical = canonicalStation(state, key, stationInventory.stationArchetypeId());
            String owner = requireOwner(ownerByStation, stationId);
            List<FacilityStateAssignment> stationFacilities = facilities.values().stream()
                    .filter(value -> value.slot().station().equals(key))
                    .toList();
            List<InstalledYardEvidence> stationYards = yards.values().stream()
                    .filter(value -> value.assignment().slot().station().equals(key))
                    .toList();
            stations.add(materializeStation(
                    canonical,
                    owner,
                    stationInventory.assignment().storage(),
                    stationFacilities,
                    stationYards,
                    true));
        }
        validateStationCoverage(ownerByStation.keySet(), stations);
        MaterializedIndustrialRegistry registry = registry(state, stations);
        validateExistingIndustrialState(state.industrialState(), registry);
        return registry;
    }

    /**
     * Restores live industrial entities solely from persisted Stage-18 state and Stage-20K identity.
     *
     * @param saved exact saved generated campaign after an initial 20.5C bootstrap
     * @return deterministic restored live industrial registry
     */
    public static MaterializedIndustrialRegistry restore(
            Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState state = requireBase(saved);
        Stage18IndustrialState industrial = state.industrialState();
        if (industrial.stationStorages().isEmpty() || industrial.facilities().isEmpty()) {
            throw new IllegalArgumentException(
                    "saved industrial state has not passed initial Stage-20.5C materialization");
        }
        Map<String, CanonicalStation> canonical = canonicalSpecializedStations(state);
        if (!canonical.keySet().equals(industrial.stationStorages().stream()
                .map(StationStorageSnapshot::stationId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet()))) {
            throw new IllegalArgumentException(
                    "saved station storage set differs from generated industrial specialization");
        }

        TreeMap<String, List<FacilityStateAssignment>> unsupportedAssignments = new TreeMap<>();
        TreeMap<String, List<FacilityInstallationSnapshot>> facilitiesByStation = new TreeMap<>();
        industrial.facilities().forEach(value -> facilitiesByStation
                .computeIfAbsent(value.stationId(), ignored -> new ArrayList<>()).add(value));
        TreeMap<String, List<YardInstallationSnapshot>> yardsByStation = new TreeMap<>();
        industrial.yards().forEach(value -> yardsByStation
                .computeIfAbsent(value.stationId(), ignored -> new ArrayList<>()).add(value));

        ArrayList<MaterializedIndustrialStation> stations = new ArrayList<>();
        for (StationStorageSnapshot storage : industrial.stationStorages()) {
            CanonicalStation authority = canonical.get(storage.stationId());
            List<InstalledFacilityState> facilityStates = facilitiesByStation
                    .getOrDefault(storage.stationId(), List.of()).stream()
                    .map(FacilityInstallationSnapshot::state)
                    .toList();
            List<InstalledYardState> yardStates = yardsByStation
                    .getOrDefault(storage.stationId(), List.of()).stream()
                    .map(YardInstallationSnapshot::state)
                    .toList();
            stations.add(restoreStation(authority, storage, facilityStates, yardStates));
        }
        if (!unsupportedAssignments.isEmpty()) {
            throw new IllegalStateException("unexpected unsupported industrial assignments");
        }
        return registry(state, stations);
    }

    private static Stage20GeneratedCampaignPersistentState requireBase(
            Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState state = Objects.requireNonNull(saved, "saved");
        if (!state.openRuntimeBoundaries().contains(OpenRuntimeBoundary.INDUSTRIAL_ENTITY_MATERIALIZATION)) {
            throw new IllegalArgumentException("saved campaign does not expose industrial entity boundary");
        }
        if (!state.generationIdentity().contentFingerprint()
                .equals(Stage18IndustrialContentFingerprint.current())
                || !state.industrialState().contentFingerprint()
                .equals(state.generationIdentity().contentFingerprint())) {
            throw new IllegalArgumentException("industrial content fingerprint differs from saved authority");
        }
        return state;
    }

    private static void putFacility(
            Map<String, FacilityStateAssignment> target,
            FacilityStateAssignment assignment) {
        FacilityStateAssignment existing = target.putIfAbsent(
                assignment.state().facilityInstanceId(), assignment);
        if (existing != null && !existing.equals(assignment)) {
            throw new IllegalArgumentException("conflicting facility state assignment");
        }
    }

    private static MaterializedIndustrialStation materializeStation(
            CanonicalStation canonical,
            String owner,
            StationStorageSnapshot storageSnapshot,
            List<FacilityStateAssignment> facilityAssignments,
            List<InstalledYardEvidence> yardEvidence,
            boolean compareAcceptedYardProjection) {
        List<InstalledFacilityState> facilities = facilityAssignments.stream()
                .map(FacilityStateAssignment::state)
                .sorted(Comparator.comparing(InstalledFacilityState::facilityInstanceId))
                .toList();
        List<InstalledYardState> yards = yardEvidence.stream()
                .map(value -> value.assignment().state())
                .sorted(Comparator.comparing(InstalledYardState::yardInstanceId))
                .toList();
        MaterializedIndustrialStation station = restoreStation(
                canonical, storageSnapshot, facilities, yards, owner);
        if (compareAcceptedYardProjection) {
            Map<String, YardCapabilitySnapshot> accepted = new TreeMap<>();
            yardEvidence.forEach(value -> accepted.put(value.snapshot().yardInstanceId(), value.snapshot()));
            for (YardCapabilitySnapshot snapshot : station.yardCapabilities()) {
                if (!snapshot.equals(accepted.get(snapshot.yardInstanceId()))) {
                    throw new IllegalArgumentException(
                            "materialized yard projection differs from accepted Stage-20F evidence");
                }
            }
        }
        return station;
    }

    private static MaterializedIndustrialStation restoreStation(
            CanonicalStation canonical,
            StationStorageSnapshot storageSnapshot,
            List<InstalledFacilityState> facilities,
            List<InstalledYardState> yards) {
        return restoreStation(canonical, storageSnapshot, facilities, yards, canonical.ownerId());
    }

    private static MaterializedIndustrialStation restoreStation(
            CanonicalStation canonical,
            StationStorageSnapshot storageSnapshot,
            List<InstalledFacilityState> facilities,
            List<InstalledYardState> yards,
            String owner) {
        Objects.requireNonNull(canonical, "canonical");
        if (!canonical.stationId().equals(storageSnapshot.stationId())) {
            throw new IllegalArgumentException("storage station differs from canonical station");
        }
        Stage18StationInfrastructureCatalog stationCatalog =
                Stage18StationInfrastructureCatalogLoader.loadDefault();
        StationArchetypeDefinition archetype = stationCatalog.findArchetype(canonical.archetypeId());
        if (archetype == null) {
            throw new IllegalArgumentException("unknown generated station archetype: " + canonical.archetypeId());
        }
        Stage18StationIndustrialNode node = Stage18StationIndustrialNode.instantiate(
                canonical.stationId(),
                GENERATED_LOCATION_TAG,
                archetype,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault());
        if (!storageSnapshot.capacityByStorageClassKg().equals(archetype.storageCapacityByClassKg())) {
            throw new IllegalArgumentException("saved storage capacity differs from generated archetype");
        }
        Stage18StationStorage storage = Stage18StationStorage.restore(
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(),
                storageSnapshot);

        TreeMap<String, InstalledFacilityState> stateById = new TreeMap<>();
        facilities.forEach(value -> {
            if (stateById.putIfAbsent(value.facilityInstanceId(), value) != null) {
                throw new IllegalArgumentException("duplicate installed facility state");
            }
        });
        Set<String> structuralIds = node.installedFacilities().stream()
                .map(Stage18StationIndustrialNode.InstalledFacilityReference::facilityInstanceId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!structuralIds.containsAll(stateById.keySet())) {
            throw new IllegalArgumentException("facility state is absent from generated station archetype");
        }
        Stage18FacilityRuntime facilityRuntime = new Stage18FacilityRuntime(
                Stage18FacilityCatalogLoader.loadDefault());
        ArrayList<FacilityCapabilitySnapshot> facilityCapabilities = new ArrayList<>();
        stateById.values().forEach(value -> facilityCapabilities.add(facilityRuntime.project(value)));
        facilityCapabilities.sort(Comparator.comparing(FacilityCapabilitySnapshot::facilityInstanceId));

        Stage18ShipyardRuntime shipyardRuntime = new Stage18ShipyardRuntime(
                Stage18ShipyardCatalogLoader.loadDefault(),
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault());
        ArrayList<YardCapabilitySnapshot> yardCapabilities = new ArrayList<>();
        ArrayList<InstalledYardState> sortedYards = new ArrayList<>(yards);
        sortedYards.sort(Comparator.comparing(InstalledYardState::yardInstanceId));
        Set<String> yardIds = new HashSet<>();
        for (InstalledYardState yard : sortedYards) {
            if (!yardIds.add(yard.yardInstanceId())) {
                throw new IllegalArgumentException("duplicate installed yard state");
            }
            yardCapabilities.add(shipyardRuntime.projectYard(yard, node, facilityCapabilities));
        }
        return new MaterializedIndustrialStation(
                canonical.systemId(),
                canonical.stationId(),
                owner,
                canonical.archetypeId(),
                canonical.position(),
                node,
                storage,
                List.copyOf(stateById.values()),
                facilityCapabilities,
                sortedYards,
                yardCapabilities);
    }

    private static MaterializedIndustrialRegistry registry(
            Stage20GeneratedCampaignPersistentState state,
            List<MaterializedIndustrialStation> stations) {
        return new MaterializedIndustrialRegistry(
                CURRENT_VERSION,
                state.generationIdentity().worldSeed(),
                state.generationIdentity().generatorVersion(),
                state.materializedWorld().worldFingerprint(),
                stations);
    }

    private static void validateExistingIndustrialState(
            Stage18IndustrialState existing,
            MaterializedIndustrialRegistry registry) {
        boolean hasLiveIndustrialState = !existing.stationStorages().isEmpty()
                || !existing.facilities().isEmpty()
                || !existing.yards().isEmpty();
        if (!hasLiveIndustrialState) {
            return;
        }
        Stage18IndustrialState captured = registry.captureIndustrialState(existing);
        if (!captured.stationStorages().equals(existing.stationStorages())
                || !captured.facilities().equals(existing.facilities())
                || !captured.yards().equals(existing.yards())) {
            throw new IllegalArgumentException(
                    "existing industrial runtime state differs from accepted materialization");
        }
    }

    private static CanonicalStation canonicalStation(
            Stage20GeneratedCampaignPersistentState state,
            StationKey key,
            String archetypeId) {
        String stableId = key.systemId().value() + ":" + key.stationPlacementId();
        CanonicalRow row = uniqueRow(state, "INFRASTRUCTURE_PLACEMENT", stableId);
        List<String> values = row.values();
        if (values.size() < 9
                || Long.parseLong(values.get(0)) != key.systemId().value()
                || !values.get(2).equals(archetypeId)) {
            throw new IllegalArgumentException("canonical station placement differs from Stage-20F authority");
        }
        return new CanonicalStation(
                key.systemId(),
                key.stationPlacementId(),
                "",
                archetypeId,
                new LocalPhysicalPosition(
                        Long.parseLong(values.get(3)),
                        Long.parseLong(values.get(4)),
                        Double.parseDouble(values.get(5)),
                        Double.parseDouble(values.get(6))));
    }

    private static Map<String, CanonicalStation> canonicalSpecializedStations(
            Stage20GeneratedCampaignPersistentState state) {
        TreeMap<String, CanonicalStation> result = new TreeMap<>();
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (!row.domain().equals("INDUSTRIAL_SPECIALIZATION")) {
                continue;
            }
            List<String> values = row.values();
            if (values.size() < 4) {
                throw new IllegalArgumentException("malformed industrial specialization row");
            }
            StarSystemId systemId = new StarSystemId(Long.parseLong(values.get(0)));
            String stationId = values.get(1);
            String owner = values.get(2);
            CanonicalRow placement = uniqueRow(
                    state, "INFRASTRUCTURE_PLACEMENT", systemId.value() + ":" + stationId);
            List<String> placementValues = placement.values();
            if (placementValues.size() < 9 || placementValues.get(2).isBlank()) {
                throw new IllegalArgumentException("specialized station lacks canonical station archetype");
            }
            CanonicalStation station = new CanonicalStation(
                    systemId,
                    stationId,
                    owner,
                    placementValues.get(2),
                    new LocalPhysicalPosition(
                            Long.parseLong(placementValues.get(3)),
                            Long.parseLong(placementValues.get(4)),
                            Double.parseDouble(placementValues.get(5)),
                            Double.parseDouble(placementValues.get(6))));
            CanonicalStation existing = result.putIfAbsent(stationId, station);
            if (existing != null && !existing.equals(station)) {
                throw new IllegalArgumentException("station has conflicting canonical specialization owners");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("saved world has no industrial specializations");
        }
        return Collections.unmodifiableMap(result);
    }

    private static CanonicalRow uniqueRow(
            Stage20GeneratedCampaignPersistentState state,
            String domain,
            String stableId) {
        CanonicalRow result = null;
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (row.domain().equals(domain) && row.stableId().equals(stableId)) {
                if (result != null) {
                    throw new IllegalArgumentException("duplicate canonical row: " + domain + ":" + stableId);
                }
                result = row;
            }
        }
        if (result == null) {
            throw new IllegalArgumentException("missing canonical row: " + domain + ":" + stableId);
        }
        return result;
    }

    private static void mergeOwner(Map<String, String> ownerByStation, String stationId, String owner) {
        String existing = ownerByStation.putIfAbsent(stationId, owner);
        if (existing != null && !existing.equals(owner)) {
            throw new IllegalArgumentException("generated station has conflicting industrial owners");
        }
    }

    private static String requireOwner(Map<String, String> ownerByStation, String stationId) {
        String owner = ownerByStation.get(stationId);
        if (owner == null) {
            throw new IllegalArgumentException("generated station lacks explicit industrial owner");
        }
        return owner;
    }

    private static void validateStationCoverage(
            Set<String> expectedStationIds,
            List<MaterializedIndustrialStation> stations) {
        Set<String> actual = stations.stream()
                .map(MaterializedIndustrialStation::stationId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!actual.equals(expectedStationIds)) {
            throw new IllegalArgumentException("materialized station set differs from accepted specialization");
        }
    }

    private record CanonicalStation(
            StarSystemId systemId,
            String stationId,
            String ownerId,
            String archetypeId,
            LocalPhysicalPosition position) {}

    /**
     * One materialized generated industrial station and its ordinary Stage-18 runtime state.
     *
     * @param systemId exact generated system identity
     * @param stationId exact generated station placement identity
     * @param stableFactionId exact generated owner identity
     * @param stationArchetypeId accepted Stage-18 station archetype identity
     * @param position exact generated local physical position
     * @param stationNode ordinary Stage-18 station industrial node
     * @param storage ordinary Stage-18 station storage
     * @param facilities persisted installed facility states
     * @param facilityCapabilities ordinary Stage-18 projected facility capabilities
     * @param yards persisted installed yard states
     * @param yardCapabilities ordinary Stage-18 projected yard capabilities
     */
    public record MaterializedIndustrialStation(
            StarSystemId systemId,
            String stationId,
            String stableFactionId,
            String stationArchetypeId,
            LocalPhysicalPosition position,
            Stage18StationIndustrialNode stationNode,
            Stage18StationStorage storage,
            List<InstalledFacilityState> facilities,
            List<FacilityCapabilitySnapshot> facilityCapabilities,
            List<InstalledYardState> yards,
            List<YardCapabilitySnapshot> yardCapabilities) {
        /**
         * Validates and freezes one station materialization.
         *
         * @param systemId exact generated system identity
         * @param stationId exact generated station placement identity
         * @param stableFactionId exact generated owner identity
         * @param stationArchetypeId accepted Stage-18 station archetype identity
         * @param position exact generated local physical position
         * @param stationNode ordinary Stage-18 station industrial node
         * @param storage ordinary Stage-18 station storage
         * @param facilities persisted installed facility states
         * @param facilityCapabilities ordinary Stage-18 projected facility capabilities
         * @param yards persisted installed yard states
         * @param yardCapabilities ordinary Stage-18 projected yard capabilities
         */
        public MaterializedIndustrialStation {
            Objects.requireNonNull(systemId, "systemId");
            stationId = requireText(stationId, "stationId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(stationNode, "stationNode");
            Objects.requireNonNull(storage, "storage");
            facilities = sortedUniqueFacilities(facilities);
            facilityCapabilities = sortedUniqueFacilityCapabilities(facilityCapabilities);
            yards = sortedUniqueYards(yards);
            yardCapabilities = sortedUniqueYardCapabilities(yardCapabilities);
            if (!stationId.equals(stationNode.stationId())
                    || !stationId.equals(storage.stationId())
                    || !stationArchetypeId.equals(stationNode.archetypeId())) {
                throw new IllegalArgumentException("materialized station runtime identity mismatch");
            }
        }
    }

    /**
     * Deterministic generated-world industrial runtime registry.
     *
     * @param version stable Stage-20.5C materialization contract version
     * @param rootSeed exact generated-world root seed
     * @param generatorVersion exact generated-world generator version
     * @param worldFingerprint exact saved materialized-world fingerprint
     * @param stations deterministic live industrial station registry
     */
    public record MaterializedIndustrialRegistry(
            String version,
            long rootSeed,
            String generatorVersion,
            String worldFingerprint,
            List<MaterializedIndustrialStation> stations) {
        /**
         * Validates and freezes one materialized industrial registry.
         *
         * @param version stable Stage-20.5C materialization contract version
         * @param rootSeed exact generated-world root seed
         * @param generatorVersion exact generated-world generator version
         * @param worldFingerprint exact saved materialized-world fingerprint
         * @param stations deterministic live industrial station registry
         */
        public MaterializedIndustrialRegistry {
            version = requireText(version, "version");
            generatorVersion = requireText(generatorVersion, "generatorVersion");
            worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
            ArrayList<MaterializedIndustrialStation> copy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            copy.sort(Comparator.comparing(MaterializedIndustrialStation::stationId));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(MaterializedIndustrialStation::stationId).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("industrial registry stations must be non-empty and unique");
            }
            stations = List.copyOf(copy);
        }

        /**
         * Captures the live station/facility/yard state while preserving sources and queued orders.
         *
         * @param base prior industrial state whose sources and orders remain authoritative
         * @return complete Stage-18 industrial state for save/load
         */
        public Stage18IndustrialState captureIndustrialState(Stage18IndustrialState base) {
            Stage18IndustrialState previous = Objects.requireNonNull(base, "base");
            ArrayList<StationStorageSnapshot> storage = new ArrayList<>();
            ArrayList<FacilityInstallationSnapshot> facilities = new ArrayList<>();
            ArrayList<YardInstallationSnapshot> yards = new ArrayList<>();
            for (MaterializedIndustrialStation station : stations) {
                storage.add(station.storage().snapshot());
                station.facilities().forEach(value -> facilities.add(
                        new FacilityInstallationSnapshot(station.stationId(), value)));
                station.yards().forEach(value -> yards.add(
                        new YardInstallationSnapshot(station.stationId(), value)));
            }
            return new Stage18IndustrialState(
                    Stage18IndustrialState.CURRENT_VERSION,
                    previous.contentFingerprint(),
                    previous.simulationTick(),
                    previous.sources(),
                    storage,
                    facilities,
                    yards,
                    previous.constructionOrders(),
                    previous.processOrders());
        }

        /**
         * Finds one station by exact generated placement ID.
         *
         * @param stationId exact generated station placement ID
         * @return materialized station carrying that identity
         */
        public MaterializedIndustrialStation station(String stationId) {
            String id = requireText(stationId, "stationId");
            return stations.stream().filter(value -> value.stationId().equals(id))
                    .findFirst().orElseThrow(() -> new IllegalArgumentException(
                            "unknown materialized industrial station: " + id));
        }
    }

    private static List<InstalledFacilityState> sortedUniqueFacilities(List<InstalledFacilityState> source) {
        ArrayList<InstalledFacilityState> copy = new ArrayList<>(Objects.requireNonNull(source, "facilities"));
        copy.sort(Comparator.comparing(InstalledFacilityState::facilityInstanceId));
        if (copy.stream().anyMatch(Objects::isNull)
                || copy.stream().map(InstalledFacilityState::facilityInstanceId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("facility states must be unique");
        }
        return List.copyOf(copy);
    }

    private static List<FacilityCapabilitySnapshot> sortedUniqueFacilityCapabilities(
            List<FacilityCapabilitySnapshot> source) {
        ArrayList<FacilityCapabilitySnapshot> copy = new ArrayList<>(Objects.requireNonNull(
                source, "facilityCapabilities"));
        copy.sort(Comparator.comparing(FacilityCapabilitySnapshot::facilityInstanceId));
        if (copy.stream().anyMatch(Objects::isNull)
                || copy.stream().map(FacilityCapabilitySnapshot::facilityInstanceId).distinct().count()
                != copy.size()) {
            throw new IllegalArgumentException("facility capabilities must be unique");
        }
        return List.copyOf(copy);
    }

    private static List<InstalledYardState> sortedUniqueYards(List<InstalledYardState> source) {
        ArrayList<InstalledYardState> copy = new ArrayList<>(Objects.requireNonNull(source, "yards"));
        copy.sort(Comparator.comparing(InstalledYardState::yardInstanceId));
        if (copy.stream().anyMatch(Objects::isNull)
                || copy.stream().map(InstalledYardState::yardInstanceId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("yard states must be unique");
        }
        return List.copyOf(copy);
    }

    private static List<YardCapabilitySnapshot> sortedUniqueYardCapabilities(
            List<YardCapabilitySnapshot> source) {
        ArrayList<YardCapabilitySnapshot> copy = new ArrayList<>(Objects.requireNonNull(
                source, "yardCapabilities"));
        copy.sort(Comparator.comparing(YardCapabilitySnapshot::yardInstanceId));
        if (copy.stream().anyMatch(Objects::isNull)
                || copy.stream().map(YardCapabilitySnapshot::yardInstanceId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("yard capabilities must be unique");
        }
        return List.copyOf(copy);
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be non-blank");
        }
        return value;
    }
}
