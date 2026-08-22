package com.spacesim.world;

import com.spacesim.content.Stage18ManufacturingProductRegistry;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.economy.Stage18StationStorage;
import com.spacesim.economy.Stage18StationStorage.StationStorageSnapshot;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.OperatingReport;
import com.spacesim.world.Stage20IndustrialFacilityOperatingPlan.StationKey;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.StationCandidate;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Validates canonical Stage-18 initial station inventory for selected Stage-20F industry.
 *
 * <p>The caller supplies one exact {@link StationStorageSnapshot} for every selected generated
 * station. Physical capacity must equal the retained Stage-18 station archetype, and every commodity
 * and finished product is validated by the canonical Stage-18 storage implementation.</p>
 *
 * <p>Operational input buffer mass is not an arbitrary bonus. For every accepted source/process
 * reservation this plan requires {@code reserved kg/s * retained delivery seconds} at the consuming
 * station. This is the physical pipeline-fill mass needed for the selected process to run from
 * bootstrap until its first already-owned delivery can arrive. Shared commodities are summed once
 * per station, and rejection commits no partial inventory authority.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialInitialInventoryPlan {
    /** Stable Stage-20F initial station inventory plan version. */
    public static final String CURRENT_VERSION =
            "stage20f.industrial-initial-station-inventory-plan.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20IndustrialInitialInventoryPlan() {
        throw new AssertionError("No instances");
    }

    /** Final initial-inventory authority state. */
    public enum Status {
        /** Every selected station has canonical capacity and sufficient physical pipeline buffer. */
        ACCEPTED,
        /** At least one required commodity pipeline buffer is absent or too small. */
        INSUFFICIENT_INITIAL_INVENTORY
    }

    /** Machine-readable rejection reason. */
    public enum FailureReason {
        /** Canonical storage lacks one or more derived first-delivery input buffers. */
        PIPELINE_BUFFER_SHORTAGE
    }

    /**
     * Explicit canonical initial storage state for one generated station.
     *
     * @param station exact generated station identity
     * @param storage exact Stage-18 storage snapshot
     */
    public record StationInventoryAssignment(
            StationKey station,
            StationStorageSnapshot storage) {
        /**
         * Validates one exact station inventory assignment.
         *
         * @param station exact generated station identity
         * @param storage exact Stage-18 storage snapshot
         */
        public StationInventoryAssignment {
            Objects.requireNonNull(station, "station");
            Objects.requireNonNull(storage, "storage");
            if (!station.stationPlacementId().equals(storage.stationId())) {
                throw new IllegalArgumentException(
                        "initial storage identity must equal generated station placement ID");
            }
        }
    }

    /**
     * Versioned caller authority for exact selected-station initial contents.
     *
     * @param version caller-defined inventory policy/result version
     * @param rootSeed exact accepted generated root seed
     * @param stations exact unique selected-station storage states
     */
    public record InitialInventoryAuthority(
            String version,
            long rootSeed,
            List<StationInventoryAssignment> stations) {
        /**
         * Canonicalizes one explicit initial-inventory authority.
         *
         * @param version caller-defined inventory policy/result version
         * @param rootSeed exact accepted generated root seed
         * @param stations exact unique selected-station storage states
         */
        public InitialInventoryAuthority {
            version = requireText(version, "version");
            ArrayList<StationInventoryAssignment> copy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException(
                        "initial inventory requires non-empty selected-station state");
            }
            copy.sort(Comparator.comparing(StationInventoryAssignment::station));
            if (copy.stream().map(StationInventoryAssignment::station).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("initial station inventory identities must be unique");
            }
            stations = List.copyOf(copy);
        }
    }

    /**
     * Derived physical first-delivery buffer for one station commodity.
     *
     * @param commodityId exact Stage-18 input commodity
     * @param requiredMassKg summed reserved-rate times retained delivery-time mass
     * @param availableMassKg exact initial canonical storage mass
     * @param shortageMassKg positive missing mass, or zero when sufficient
     * @param status whether this commodity buffer is sufficient
     */
    public record CommodityBufferEvidence(
            String commodityId,
            double requiredMassKg,
            double availableMassKg,
            double shortageMassKg,
            Status status) {
        /**
         * Validates one exact commodity buffer result.
         *
         * @param commodityId exact Stage-18 input commodity
         * @param requiredMassKg summed physical pipeline-fill mass
         * @param availableMassKg exact initial canonical storage mass
         * @param shortageMassKg positive missing mass, or zero when sufficient
         * @param status whether this commodity buffer is sufficient
         */
        public CommodityBufferEvidence {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredMassKg, "requiredMassKg");
            requireNonNegativeFinite(availableMassKg, "availableMassKg");
            requireNonNegativeFinite(shortageMassKg, "shortageMassKg");
            Objects.requireNonNull(status, "status");
            double expectedShortage = Math.max(0d, requiredMassKg - availableMassKg);
            close(shortageMassKg, expectedShortage, "shortageMassKg");
            if ((status == Status.ACCEPTED) != (shortageMassKg <= EPSILON)) {
                throw new IllegalArgumentException(
                        "commodity buffer status differs from required/available mass");
            }
        }
    }

    /**
     * Canonical storage and derived pipeline-buffer evidence for one selected station.
     *
     * @param assignment exact caller-authored storage state
     * @param stationArchetypeId exact Stage-18 station archetype
     * @param buffers every selected input commodity required at this station
     * @param status whether every derived buffer is present
     */
    public record StationInventoryEvidence(
            StationInventoryAssignment assignment,
            String stationArchetypeId,
            List<CommodityBufferEvidence> buffers,
            Status status) {
        /**
         * Validates one immutable selected-station inventory result.
         *
         * @param assignment exact caller-authored storage state
         * @param stationArchetypeId exact Stage-18 station archetype
         * @param buffers every selected input commodity required at this station
         * @param status whether every derived buffer is present
         */
        public StationInventoryEvidence {
            Objects.requireNonNull(assignment, "assignment");
            stationArchetypeId = requireText(stationArchetypeId, "stationArchetypeId");
            ArrayList<CommodityBufferEvidence> copy = new ArrayList<>(Objects.requireNonNull(
                    buffers, "buffers"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selected station requires input buffer evidence");
            }
            copy.sort(Comparator.comparing(CommodityBufferEvidence::commodityId));
            if (copy.stream().map(CommodityBufferEvidence::commodityId).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("station commodity buffers must be unique");
            }
            buffers = List.copyOf(copy);
            Objects.requireNonNull(status, "status");
            boolean accepted = buffers.stream().allMatch(value -> value.status() == Status.ACCEPTED);
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException(
                        "station inventory status differs from commodity buffer evidence");
            }
        }
    }

    /**
     * Complete all-or-nothing initial station inventory result.
     *
     * @param version plan contract version
     * @param rootSeed exact accepted generated root seed
     * @param resolvedProbeVersion exact generated evidence version
     * @param candidatePlanVersion exact candidate reconstruction version
     * @param operatingState accepted preceding facility operating authority
     * @param authority exact caller-authored station inventory
     * @param status final initial-inventory status
     * @param failureReason absent only when accepted
     * @param stations exact selected-station storage/buffer evidence
     * @param missingAuthorities authorities still blocking operational specialization
     */
    public record InventoryReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String candidatePlanVersion,
            OperatingReport operatingState,
            InitialInventoryAuthority authority,
            Status status,
            Optional<FailureReason> failureReason,
            List<StationInventoryEvidence> stations,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Canonicalizes and validates one immutable initial-inventory result.
         *
         * @param version plan contract version
         * @param rootSeed exact accepted generated root seed
         * @param resolvedProbeVersion exact generated evidence version
         * @param candidatePlanVersion exact candidate reconstruction version
         * @param operatingState accepted preceding facility operating authority
         * @param authority exact caller-authored station inventory
         * @param status final initial-inventory status
         * @param failureReason absent only when accepted
         * @param stations exact selected-station storage/buffer evidence
         * @param missingAuthorities authorities still blocking operational specialization
         */
        public InventoryReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            candidatePlanVersion = requireText(candidatePlanVersion, "candidatePlanVersion");
            Objects.requireNonNull(operatingState, "operatingState");
            Objects.requireNonNull(authority, "authority");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (rootSeed != operatingState.rootSeed() || rootSeed != authority.rootSeed()) {
                throw new IllegalArgumentException("inventory authorities target different root seeds");
            }
            if (!operatingState.facilityOperatingStateAuthoritative()) {
                throw new IllegalArgumentException("initial inventory requires accepted facility operation");
            }
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException("failure reason must be absent exactly when accepted");
            }
            ArrayList<StationInventoryEvidence> copy = new ArrayList<>(Objects.requireNonNull(
                    stations, "stations"));
            copy.sort(Comparator.comparing(value -> value.assignment().station()));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(value -> value.assignment().station()).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("inventory report stations must be non-empty and unique");
            }
            stations = List.copyOf(copy);
            Set<StationKey> expectedStations = operatingState.stations().stream()
                    .map(Stage20IndustrialFacilityOperatingPlan.StationOperatingEvidence::station)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<StationKey> authorityStations = authority.stations().stream()
                    .map(StationInventoryAssignment::station)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            Set<StationKey> actualStations = stations.stream()
                    .map(value -> value.assignment().station())
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            if (!expectedStations.equals(authorityStations)
                    || !expectedStations.equals(actualStations)) {
                throw new IllegalArgumentException(
                        "initial inventory must exactly cover selected operating stations");
            }
            validateDerivedInventoryEvidence(operatingState, authority, stations);
            boolean allAccepted = stations.stream().allMatch(value -> value.status() == Status.ACCEPTED);
            if ((status == Status.ACCEPTED) != allAccepted) {
                throw new IllegalArgumentException("inventory report status differs from station evidence");
            }
            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> expected = EnumSet.copyOf(
                    operatingState.missingAuthorities());
            if (status == Status.ACCEPTED) {
                expected.remove(MissingAuthority.INITIAL_STATION_INVENTORY);
            }
            EnumSet<MissingAuthority> actual = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!actual.equals(expected)) {
                throw new IllegalArgumentException(
                        "inventory report cannot silently change another authority");
            }
            missingAuthorities = immutableAuthorities(actual);
        }

        /** @return whether canonical initial storage and pipeline buffers are authoritative */
        public boolean initialInventoryAuthoritative() {
            return status == Status.ACCEPTED
                    && !missingAuthorities.contains(MissingAuthority.INITIAL_STATION_INVENTORY);
        }

        /** @return total required physical pipeline-fill mass across selected stations */
        public double totalRequiredBufferMassKg() {
            double result = 0d;
            for (StationInventoryEvidence station : stations) {
                for (CommodityBufferEvidence buffer : station.buffers()) {
                    result = finiteAdd(result, buffer.requiredMassKg());
                }
            }
            return result;
        }

        /** @return whether every operational specialization authority is present */
        public boolean operationallyAuthoritative() {
            return missingAuthorities.isEmpty();
        }
    }

    /**
     * Validates explicit initial station storage for accepted selected facility operation.
     *
     * @param resolved exact accepted generated-world authority
     * @param operatingState exact accepted facility operating state
     * @param authority explicit canonical initial storage states
     * @return deterministic accepted or fail-closed inventory report
     */
    public static InventoryReport plan(
            ResolvedProbeResult resolved,
            OperatingReport operatingState,
            InitialInventoryAuthority authority) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        OperatingReport operating = Objects.requireNonNull(operatingState, "operatingState");
        InitialInventoryAuthority inventory = Objects.requireNonNull(authority, "authority");
        if (accepted.rootSeed() != operating.rootSeed()
                || !operating.resolvedProbeVersion().equals(accepted.version())
                || !operating.facilityOperatingStateAuthoritative()) {
            throw new IllegalArgumentException(
                    "initial inventory requires matching accepted generated operating evidence");
        }
        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(accepted);
        return planEvidence(
                operating,
                candidates,
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ManufacturingProductRegistry.loadDefault(),
                inventory);
    }

    static InventoryReport planEvidence(
            OperatingReport operatingState,
            CandidateReport candidates,
            com.spacesim.content.Stage18ResourceOntologyCatalog ontology,
            Stage18ManufacturingProductRegistry products,
            InitialInventoryAuthority authority) {
        OperatingReport operating = Objects.requireNonNull(operatingState, "operatingState");
        CandidateReport candidateReport = Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(ontology, "ontology");
        Objects.requireNonNull(products, "products");
        InitialInventoryAuthority inventory = Objects.requireNonNull(authority, "authority");
        if (!operating.facilityOperatingStateAuthoritative()
                || operating.rootSeed() != candidateReport.rootSeed()
                || inventory.rootSeed() != operating.rootSeed()) {
            throw new IllegalArgumentException("inventory evidence targets different authorities");
        }

        TreeMap<StationKey, StationCandidate> stationByKey = candidateStations(candidateReport);
        TreeMap<StationKey, StationInventoryAssignment> assignmentByStation = new TreeMap<>();
        inventory.stations().forEach(value -> assignmentByStation.put(value.station(), value));
        Set<StationKey> selectedStations = operating.stations().stream()
                .map(Stage20IndustrialFacilityOperatingPlan.StationOperatingEvidence::station)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!assignmentByStation.keySet().equals(selectedStations)) {
            throw new IllegalArgumentException(
                    "initial inventory authority must exactly cover selected operating stations");
        }

        TreeMap<StationKey, TreeMap<String, Double>> requiredByStation = new TreeMap<>();
        for (var reservation : operating.freightOwnership().reservation().reservations()) {
            StationKey station = new StationKey(
                    reservation.process().systemId(),
                    reservation.process().stationPlacementId());
            if (!selectedStations.contains(station)) {
                throw new IllegalArgumentException(
                        "input reservation targets a station outside selected operation");
            }
            double pipelineMass = finiteMultiply(
                    reservation.reservedInputKgPerSecond(), reservation.route().travelTimeS());
            requiredByStation.computeIfAbsent(station, ignored -> new TreeMap<>())
                    .merge(reservation.inputCommodityId(), pipelineMass,
                            Stage20IndustrialInitialInventoryPlan::finiteAdd);
        }

        ArrayList<StationInventoryEvidence> evidence = new ArrayList<>();
        for (StationKey station : selectedStations.stream().sorted().toList()) {
            StationCandidate physical = stationByKey.get(station);
            if (physical == null) {
                throw new IllegalArgumentException("selected station is absent from candidate evidence");
            }
            StationInventoryAssignment assignment = assignmentByStation.get(station);
            if (!assignment.storage().capacityByStorageClassKg().equals(
                    physical.archetype().storageCapacityByClassKg())) {
                throw new IllegalArgumentException(
                        "initial inventory capacity must equal generated station archetype capacity");
            }
            Stage18StationStorage storage = Stage18StationStorage.restore(
                    ontology, products, assignment.storage());
            TreeMap<String, Double> required = requiredByStation.get(station);
            if (required == null || required.isEmpty()) {
                throw new IllegalArgumentException("selected station lost process input reservations");
            }
            ArrayList<CommodityBufferEvidence> buffers = new ArrayList<>();
            for (Map.Entry<String, Double> entry : required.entrySet()) {
                double available = storage.commodityMassKg(entry.getKey());
                double shortage = Math.max(0d, entry.getValue() - available);
                buffers.add(new CommodityBufferEvidence(
                        entry.getKey(),
                        entry.getValue(),
                        available,
                        shortage,
                        shortage <= EPSILON
                                ? Status.ACCEPTED
                                : Status.INSUFFICIENT_INITIAL_INVENTORY));
            }
            Status stationStatus = buffers.stream().allMatch(value -> value.status() == Status.ACCEPTED)
                    ? Status.ACCEPTED
                    : Status.INSUFFICIENT_INITIAL_INVENTORY;
            evidence.add(new StationInventoryEvidence(
                    assignment,
                    physical.archetype().id(),
                    buffers,
                    stationStatus));
        }

        boolean accepted = evidence.stream().allMatch(value -> value.status() == Status.ACCEPTED);
        EnumSet<MissingAuthority> missing = EnumSet.copyOf(operating.missingAuthorities());
        if (accepted) {
            missing.remove(MissingAuthority.INITIAL_STATION_INVENTORY);
        }
        return new InventoryReport(
                CURRENT_VERSION,
                operating.rootSeed(),
                operating.resolvedProbeVersion(),
                candidateReport.version(),
                operating,
                inventory,
                accepted ? Status.ACCEPTED : Status.INSUFFICIENT_INITIAL_INVENTORY,
                accepted ? Optional.empty() : Optional.of(FailureReason.PIPELINE_BUFFER_SHORTAGE),
                evidence,
                missing);
    }

    private static TreeMap<StationKey, StationCandidate> candidateStations(
            CandidateReport candidates) {
        TreeMap<StationKey, StationCandidate> result = new TreeMap<>();
        for (var system : candidates.systems()) {
            for (StationCandidate station : system.stations()) {
                StationKey key = new StationKey(system.systemId(), station.placement().id());
                if (result.putIfAbsent(key, station) != null) {
                    throw new IllegalArgumentException("candidate station identities must be unique");
                }
            }
        }
        return result;
    }

    private static void validateDerivedInventoryEvidence(
            OperatingReport operating,
            InitialInventoryAuthority authority,
            List<StationInventoryEvidence> stations) {
        TreeMap<StationKey, StationInventoryAssignment> authorityByStation = new TreeMap<>();
        authority.stations().forEach(value -> authorityByStation.put(value.station(), value));
        TreeMap<StationKey, Stage20IndustrialFacilityOperatingPlan.StationOperatingEvidence>
                operatingByStation = new TreeMap<>();
        operating.stations().forEach(value -> operatingByStation.put(value.station(), value));

        TreeMap<StationKey, TreeMap<String, Double>> requiredByStation = new TreeMap<>();
        for (var reservation : operating.freightOwnership().reservation().reservations()) {
            StationKey station = new StationKey(
                    reservation.process().systemId(),
                    reservation.process().stationPlacementId());
            double pipelineMass = finiteMultiply(
                    reservation.reservedInputKgPerSecond(),
                    reservation.route().travelTimeS());
            requiredByStation.computeIfAbsent(station, ignored -> new TreeMap<>())
                    .merge(reservation.inputCommodityId(), pipelineMass,
                            Stage20IndustrialInitialInventoryPlan::finiteAdd);
        }

        for (StationInventoryEvidence station : stations) {
            StationKey key = station.assignment().station();
            StationInventoryAssignment expectedAssignment = authorityByStation.get(key);
            var operatingStation = operatingByStation.get(key);
            TreeMap<String, Double> required = requiredByStation.get(key);
            if (!station.assignment().equals(expectedAssignment)
                    || operatingStation == null
                    || !station.stationArchetypeId().equals(
                    operatingStation.stationArchetypeId())
                    || required == null
                    || required.size() != station.buffers().size()) {
                throw new IllegalArgumentException(
                        "inventory evidence must retain exact authority, station and input coverage");
            }
            TreeMap<String, CommodityBufferEvidence> actual = new TreeMap<>();
            station.buffers().forEach(value -> actual.put(value.commodityId(), value));
            if (!actual.keySet().equals(required.keySet())) {
                throw new IllegalArgumentException(
                        "inventory evidence must exactly cover derived station commodities");
            }
            for (Map.Entry<String, Double> entry : required.entrySet()) {
                CommodityBufferEvidence buffer = actual.get(entry.getKey());
                double available = station.assignment().storage().commodityMassByIdKg()
                        .getOrDefault(entry.getKey(), 0d);
                close(buffer.requiredMassKg(), entry.getValue(), "requiredMassKg");
                close(buffer.availableMassKg(), available, "availableMassKg");
            }
        }
    }

    private static Set<MissingAuthority> immutableAuthorities(EnumSet<MissingAuthority> authorities) {
        return Collections.unmodifiableSet(authorities.isEmpty()
                ? EnumSet.noneOf(MissingAuthority.class)
                : EnumSet.copyOf(authorities));
    }

    private static double finiteMultiply(double left, double right) {
        double result = left * right;
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalArgumentException("pipeline buffer mass must be positive and finite");
        }
        return result;
    }

    private static double finiteAdd(double left, double right) {
        double result = left + right;
        if (!Double.isFinite(result)) {
            throw new IllegalArgumentException("inventory mass sum overflowed finite range");
        }
        return result;
    }

    private static void close(double actual, double expected, String field) {
        double scale = Math.max(1d, Math.max(Math.abs(actual), Math.abs(expected)));
        if (Math.abs(actual - expected) > EPSILON * scale) {
            throw new IllegalArgumentException(field + " differs from derived inventory evidence");
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
