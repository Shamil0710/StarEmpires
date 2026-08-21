package com.spacesim.world;

import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.ProcessInputRoutePlan;
import com.spacesim.world.Stage20IndustrialInputRouteEvidencePlan.RouteEvidenceReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.ProcessCandidate;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.InputSupplyRouteEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessInputThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Reserves finite Stage-20F industrial-input supply capacity for an explicit process selection.
 *
 * <p>The caller must identify every selected physical facility/process row and its requested output
 * rate. No station or system label is interpreted as a production request. For each input commodity,
 * this plan solves one deterministic maximum-flow problem whose source edges are capped by the exact
 * retained {@link SupplyKey} capacities and whose service arcs are capped by the admitted physical
 * route evidence reconstructed by {@link Stage20IndustrialInputRouteEvidencePlan}.</p>
 *
 * <p>An accepted report closes only shared input-capacity reservation. Remote routes remain evidence,
 * not owned freight allocations; facility operating state, initial inventory and installed shipyards
 * also remain explicit unresolved authorities. No runtime state or specialization bonus is created.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialInputReservationPlan {
    /** Stable Stage-20F industrial-input reservation version. */
    public static final String CURRENT_VERSION = "stage20f.industrial-input-reservation-plan.v1";
    private static final double EPSILON = 1.0e-9d;
    private static final Set<MissingAuthority> ALL_MISSING_AUTHORITIES = immutableAuthorities(
            EnumSet.allOf(MissingAuthority.class));
    private static final Set<MissingAuthority> ACCEPTED_MISSING_AUTHORITIES;

    static {
        EnumSet<MissingAuthority> missing = EnumSet.allOf(MissingAuthority.class);
        missing.remove(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        ACCEPTED_MISSING_AUTHORITIES = immutableAuthorities(missing);
    }

    private Stage20IndustrialInputReservationPlan() {
        throw new AssertionError("No instances");
    }

    /** Final state of the supplied explicit process/output-rate selection. */
    public enum Status {
        /** Every selected process input is reserved without reusing finite supply-key capacity. */
        ACCEPTED,
        /** The selected rates cannot coexist under the shared finite supply-key ceilings. */
        SHARED_SUPPLY_KEY_CONFLICT
    }

    /** Machine-readable rejection reason for a non-authoritative reservation attempt. */
    public enum FailureReason {
        /** At least one input commodity cannot satisfy all selected process demands together. */
        SHARED_SUPPLY_KEY_CAPACITY_CONFLICT
    }

    /**
     * Stable identity of one exact generated station facility/process candidate.
     *
     * @param systemId physical processing system
     * @param stationPlacementId exact generated station placement
     * @param facilityDefinitionId exact Stage-18 facility definition
     * @param processId exact Stage-18 recipe/process
     * @param outputCommodityId exact process output commodity
     */
    public record ProcessSelectionKey(
            StarSystemId systemId,
            String stationPlacementId,
            String facilityDefinitionId,
            String processId,
            String outputCommodityId) implements Comparable<ProcessSelectionKey> {
        /**
         * Validates one complete physical process identity.
         *
         * @param systemId physical processing system
         * @param stationPlacementId exact generated station placement
         * @param facilityDefinitionId exact Stage-18 facility definition
         * @param processId exact Stage-18 recipe/process
         * @param outputCommodityId exact process output commodity
         */
        public ProcessSelectionKey {
            Objects.requireNonNull(systemId, "systemId");
            stationPlacementId = requireText(stationPlacementId, "stationPlacementId");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            processId = requireText(processId, "processId");
            outputCommodityId = requireText(outputCommodityId, "outputCommodityId");
        }

        /**
         * Creates the stable selection identity for an exact retained candidate.
         *
         * @param candidate exact physical process candidate
         * @return canonical selection identity
         */
        public static ProcessSelectionKey from(ProcessCandidate candidate) {
            var capacity = Objects.requireNonNull(candidate, "candidate").capacity();
            return new ProcessSelectionKey(
                    capacity.systemId(),
                    capacity.stationPlacementId(),
                    capacity.facilityDefinitionId(),
                    capacity.processId(),
                    capacity.outputCommodityId());
        }

        /** Orders exact process identities deterministically. */
        @Override
        public int compareTo(ProcessSelectionKey other) {
            int comparison = systemId.compareTo(other.systemId);
            if (comparison != 0) return comparison;
            comparison = stationPlacementId.compareTo(other.stationPlacementId);
            if (comparison != 0) return comparison;
            comparison = facilityDefinitionId.compareTo(other.facilityDefinitionId);
            if (comparison != 0) return comparison;
            comparison = processId.compareTo(other.processId);
            return comparison != 0 ? comparison : outputCommodityId.compareTo(other.outputCommodityId);
        }
    }

    /**
     * One caller-authored process selection and requested output rate.
     *
     * @param process exact generated process identity
     * @param requestedOutputKgPerSecond explicitly requested output mass rate
     */
    public record ProcessOutputRequest(
            ProcessSelectionKey process,
            double requestedOutputKgPerSecond) {
        /**
         * Validates one positive finite explicit output request.
         *
         * @param process exact generated process identity
         * @param requestedOutputKgPerSecond explicitly requested output mass rate
         */
        public ProcessOutputRequest {
            Objects.requireNonNull(process, "process");
            requirePositiveFinite(requestedOutputKgPerSecond, "requestedOutputKgPerSecond");
        }
    }

    /**
     * Versioned caller authority selecting processes for one exact accepted generated root seed.
     *
     * @param version caller-defined selection-policy/result version
     * @param rootSeed exact accepted generated root seed being selected
     * @param requests non-empty unique process/output-rate requests
     */
    public record SelectionAuthority(
            String version,
            long rootSeed,
            List<ProcessOutputRequest> requests) {
        /**
         * Canonicalizes and validates an explicit selection authority.
         *
         * @param version caller-defined selection-policy/result version
         * @param rootSeed exact accepted generated root seed
         * @param requests non-empty unique process/output-rate requests
         */
        public SelectionAuthority {
            version = requireText(version, "version");
            ArrayList<ProcessOutputRequest> copy = new ArrayList<>(Objects.requireNonNull(
                    requests, "requests"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selection requests must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparing(ProcessOutputRequest::process));
            HashSet<ProcessSelectionKey> keys = new HashSet<>();
            for (ProcessOutputRequest request : copy) {
                if (!keys.add(request.process())) {
                    throw new IllegalArgumentException("selection cannot request one process more than once");
                }
            }
            requests = List.copyOf(copy);
        }
    }

    /**
     * One accepted source-to-process input-capacity reservation.
     *
     * @param process selected consuming process
     * @param inputCommodityId exact required input commodity
     * @param supplyKey finite commodity/source-system identity being reserved
     * @param route retained physical route from source to process
     * @param reservedInputKgPerSecond exact reserved input rate
     * @param local whether the source and process occupy the same system
     */
    public record InputReservation(
            ProcessSelectionKey process,
            String inputCommodityId,
            SupplyKey supplyKey,
            RouteAssessment route,
            double reservedInputKgPerSecond,
            boolean local) {
        /**
         * Validates one immutable accepted reservation and its route endpoints.
         *
         * @param process selected consuming process
         * @param inputCommodityId exact required input commodity
         * @param supplyKey finite commodity/source-system identity
         * @param route retained physical route from source to process
         * @param reservedInputKgPerSecond exact reserved input rate
         * @param local whether source and process occupy the same system
         */
        public InputReservation {
            Objects.requireNonNull(process, "process");
            inputCommodityId = requireText(inputCommodityId, "inputCommodityId");
            Objects.requireNonNull(supplyKey, "supplyKey");
            Objects.requireNonNull(route, "route");
            requirePositiveFinite(reservedInputKgPerSecond, "reservedInputKgPerSecond");
            if (!supplyKey.commodityId().equals(inputCommodityId)) {
                throw new IllegalArgumentException("reservation supply key must match its input commodity");
            }
            List<StarSystemId> path = route.orderedSystems();
            if (!path.get(0).equals(supplyKey.systemId())
                    || !path.get(path.size() - 1).equals(process.systemId())) {
                throw new IllegalArgumentException("reservation route endpoints must match source and process");
            }
            if (local != supplyKey.systemId().equals(process.systemId())) {
                throw new IllegalArgumentException("local flag must match source/process system identity");
            }
            if ((local && path.size() != 1) || (!local && path.size() < 2)) {
                throw new IllegalArgumentException("reservation route shape must match its local flag");
            }
            if (reservedInputKgPerSecond > route.sustainableCargoThroughputKgPerSecond() + EPSILON) {
                throw new IllegalArgumentException("reservation cannot exceed its retained route throughput");
            }
        }
    }

    /**
     * Maximum-flow evidence for one selected process input.
     *
     * @param process selected process identity
     * @param inputCommodityId exact required input commodity
     * @param requestedOutputKgPerSecond selected process output rate
     * @param inputKgPerOutputKg exact Stage-18 recipe mass ratio
     * @param requiredInputKgPerSecond derived input demand
     * @param maxReservableInputKgPerSecond maximum input rate found in the global flow
     * @param status whether this exact input demand can be fully reserved
     */
    public record InputDemandEvidence(
            ProcessSelectionKey process,
            String inputCommodityId,
            double requestedOutputKgPerSecond,
            double inputKgPerOutputKg,
            double requiredInputKgPerSecond,
            double maxReservableInputKgPerSecond,
            Status status) {
        /**
         * Validates one derived process-input demand result.
         *
         * @param process selected process identity
         * @param inputCommodityId exact required input commodity
         * @param requestedOutputKgPerSecond selected process output rate
         * @param inputKgPerOutputKg exact Stage-18 recipe mass ratio
         * @param requiredInputKgPerSecond derived input demand
         * @param maxReservableInputKgPerSecond maximum input rate found in the global flow
         * @param status whether this exact input demand can be fully reserved
         */
        public InputDemandEvidence {
            Objects.requireNonNull(process, "process");
            inputCommodityId = requireText(inputCommodityId, "inputCommodityId");
            requirePositiveFinite(requestedOutputKgPerSecond, "requestedOutputKgPerSecond");
            requirePositiveFinite(inputKgPerOutputKg, "inputKgPerOutputKg");
            requirePositiveFinite(requiredInputKgPerSecond, "requiredInputKgPerSecond");
            requireNonNegativeFinite(maxReservableInputKgPerSecond, "maxReservableInputKgPerSecond");
            Objects.requireNonNull(status, "status");
            close(requiredInputKgPerSecond,
                    finiteMultiply(requestedOutputKgPerSecond, inputKgPerOutputKg),
                    "requiredInputKgPerSecond");
            if (maxReservableInputKgPerSecond > requiredInputKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("maximum reservable input cannot exceed demand");
            }
            boolean accepted = maxReservableInputKgPerSecond + EPSILON >= requiredInputKgPerSecond;
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException("input demand status differs from maximum-flow evidence");
            }
        }
    }

    /**
     * Aggregate shared-capacity evidence for one selected input commodity.
     *
     * @param commodityId exact input commodity
     * @param requiredInputKgPerSecond total selected demand
     * @param maxReservableInputKgPerSecond maximum globally reservable demand
     * @param participatingSupplyCapacityKgPerSecond finite capacity of all participating supply keys
     * @param status whether every selected demand for this commodity can be reserved
     */
    public record CommodityReservationEvidence(
            String commodityId,
            double requiredInputKgPerSecond,
            double maxReservableInputKgPerSecond,
            double participatingSupplyCapacityKgPerSecond,
            Status status) {
        /**
         * Validates one aggregate commodity reservation result.
         *
         * @param commodityId exact input commodity
         * @param requiredInputKgPerSecond total selected demand
         * @param maxReservableInputKgPerSecond maximum globally reservable demand
         * @param participatingSupplyCapacityKgPerSecond finite participating supply-key capacity
         * @param status whether every selected demand for this commodity can be reserved
         */
        public CommodityReservationEvidence {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredInputKgPerSecond, "requiredInputKgPerSecond");
            requireNonNegativeFinite(maxReservableInputKgPerSecond, "maxReservableInputKgPerSecond");
            requireNonNegativeFinite(
                    participatingSupplyCapacityKgPerSecond,
                    "participatingSupplyCapacityKgPerSecond");
            Objects.requireNonNull(status, "status");
            if (maxReservableInputKgPerSecond > requiredInputKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("maximum reservable commodity input cannot exceed demand");
            }
            boolean accepted = maxReservableInputKgPerSecond + EPSILON >= requiredInputKgPerSecond;
            if ((status == Status.ACCEPTED) != accepted) {
                throw new IllegalArgumentException("commodity status differs from maximum-flow evidence");
            }
        }
    }

    /**
     * Deterministic fail-closed reservation result for one explicit selection authority.
     *
     * @param version reservation contract version
     * @param rootSeed exact accepted generated root seed
     * @param resolvedProbeVersion exact resolved production-probe version
     * @param routeEvidenceVersion exact industrial-input route-evidence version
     * @param candidatePlanVersion exact industrial candidate-plan version
     * @param supplyProfileVersion exact physical supply-analysis profile
     * @param selection exact caller-authored process/output-rate authority
     * @param status final reservation status
     * @param failureReason absent only when accepted
     * @param reservations exact committed source/input rates; empty on rejection
     * @param inputDemands per-process/input maximum-flow evidence
     * @param commodities aggregate per-commodity maximum-flow evidence
     * @param missingAuthorities authorities still blocking operational specialization
     */
    public record ReservationReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String routeEvidenceVersion,
            String candidatePlanVersion,
            String supplyProfileVersion,
            SelectionAuthority selection,
            Status status,
            Optional<FailureReason> failureReason,
            List<InputReservation> reservations,
            List<InputDemandEvidence> inputDemands,
            List<CommodityReservationEvidence> commodities,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Canonicalizes and validates one immutable fail-closed reservation report.
         *
         * @param version reservation contract version
         * @param rootSeed exact accepted generated root seed
         * @param resolvedProbeVersion exact resolved production-probe version
         * @param routeEvidenceVersion exact industrial-input route-evidence version
         * @param candidatePlanVersion exact industrial candidate-plan version
         * @param supplyProfileVersion exact physical supply-analysis profile
         * @param selection exact caller-authored process/output-rate authority
         * @param status final reservation status
         * @param failureReason absent only when accepted
         * @param reservations exact committed source/input rates; empty on rejection
         * @param inputDemands per-process/input maximum-flow evidence
         * @param commodities aggregate per-commodity maximum-flow evidence
         * @param missingAuthorities authorities still blocking operational specialization
         */
        public ReservationReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            routeEvidenceVersion = requireText(routeEvidenceVersion, "routeEvidenceVersion");
            candidatePlanVersion = requireText(candidatePlanVersion, "candidatePlanVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            Objects.requireNonNull(selection, "selection");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (selection.rootSeed() != rootSeed) {
                throw new IllegalArgumentException("selection root seed must match reservation evidence");
            }
            if ((status == Status.ACCEPTED) != failureReason.isEmpty()) {
                throw new IllegalArgumentException("failure reason must be absent exactly when accepted");
            }

            ArrayList<InputReservation> reservationCopy = new ArrayList<>(Objects.requireNonNull(
                    reservations, "reservations"));
            if (reservationCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("reservations cannot contain nulls");
            }
            reservationCopy.sort(RESERVATION_ORDER);
            HashSet<ReservationKey> reservationKeys = new HashSet<>();
            for (InputReservation reservation : reservationCopy) {
                if (!reservationKeys.add(ReservationKey.from(reservation))) {
                    throw new IllegalArgumentException("input reservations must be unique per process/input/supply");
                }
            }
            reservations = List.copyOf(reservationCopy);

            ArrayList<InputDemandEvidence> demandCopy = new ArrayList<>(Objects.requireNonNull(
                    inputDemands, "inputDemands"));
            if (demandCopy.isEmpty() || demandCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("input demand evidence must be non-empty and contain no nulls");
            }
            demandCopy.sort(DEMAND_EVIDENCE_ORDER);
            if (demandCopy.stream().map(DemandKey::from).distinct().count() != demandCopy.size()) {
                throw new IllegalArgumentException("input demand evidence must be unique");
            }
            inputDemands = List.copyOf(demandCopy);

            ArrayList<CommodityReservationEvidence> commodityCopy = new ArrayList<>(Objects.requireNonNull(
                    commodities, "commodities"));
            if (commodityCopy.isEmpty() || commodityCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodity evidence must be non-empty and contain no nulls");
            }
            commodityCopy.sort(Comparator.comparing(CommodityReservationEvidence::commodityId));
            if (commodityCopy.stream().map(CommodityReservationEvidence::commodityId).distinct().count()
                    != commodityCopy.size()) {
                throw new IllegalArgumentException("commodity evidence must be unique");
            }
            commodities = List.copyOf(commodityCopy);

            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            Set<MissingAuthority> expectedMissing = status == Status.ACCEPTED
                    ? ACCEPTED_MISSING_AUTHORITIES
                    : ALL_MISSING_AUTHORITIES;
            EnumSet<MissingAuthority> missing = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!missing.equals(expectedMissing)) {
                throw new IllegalArgumentException("reservation report cannot silently close another authority");
            }
            missingAuthorities = immutableAuthorities(missing);

            boolean allDemandsAccepted = inputDemands.stream()
                    .allMatch(value -> value.status() == Status.ACCEPTED);
            boolean allCommoditiesAccepted = commodities.stream()
                    .allMatch(value -> value.status() == Status.ACCEPTED);
            if (status == Status.ACCEPTED) {
                if (!allDemandsAccepted || !allCommoditiesAccepted || reservations.isEmpty()) {
                    throw new IllegalArgumentException("accepted report requires complete demand reservations");
                }
            } else if (allDemandsAccepted || allCommoditiesAccepted || !reservations.isEmpty()) {
                throw new IllegalArgumentException("rejected report must remain non-committing and retain a conflict");
            }
        }

        /** @return whether shared industrial input capacity is authoritatively reserved */
        public boolean industrialInputReservationAuthoritative() {
            return status == Status.ACCEPTED
                    && !missingAuthorities.contains(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS);
        }

        /** @return whether every operational specialization authority is present */
        public boolean operationallyAuthoritative() {
            return missingAuthorities.isEmpty();
        }

        /** @return number of accepted reservations that still require explicit owned freight */
        public int remoteReservationCount() {
            return (int) reservations.stream().filter(value -> !value.local()).count();
        }
    }

    /**
     * Reconstructs exact route evidence and reserves shared input capacity for an explicit selection.
     *
     * @param resolved exact accepted resolved production evidence
     * @param selection explicit process/output-rate authority for the same root seed
     * @return deterministic accepted reservation or fail-closed shared-capacity conflict
     */
    public static ReservationReport reserve(
            ResolvedProbeResult resolved,
            SelectionAuthority selection) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        RouteEvidenceReport routes = Stage20IndustrialInputRouteEvidencePlan.reconstruct(accepted);
        SupplyThroughputReport supply = accepted.generation().supplyThroughput().orElseThrow(
                () -> new IllegalArgumentException("accepted generated seed lost physical supply evidence"));
        return reserveEvidence(supply, routes, selection);
    }

    static ReservationReport reserveEvidence(
            SupplyThroughputReport supply,
            RouteEvidenceReport routes,
            SelectionAuthority selection) {
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        RouteEvidenceReport checkedRoutes = Objects.requireNonNull(routes, "routes");
        SelectionAuthority checkedSelection = Objects.requireNonNull(selection, "selection");
        if (checkedSelection.rootSeed() != checkedRoutes.rootSeed()) {
            throw new IllegalArgumentException("selection authority targets a different generated root seed");
        }
        if (!checkedSupply.profileVersion().equals(checkedRoutes.supplyProfileVersion())) {
            throw new IllegalArgumentException("route evidence and supply profile versions differ");
        }
        validateProcessCoverage(checkedSupply, checkedRoutes);

        TreeMap<ProcessSelectionKey, ProcessInputRoutePlan> processByKey = new TreeMap<>();
        for (ProcessInputRoutePlan process : checkedRoutes.processes()) {
            ProcessSelectionKey key = ProcessSelectionKey.from(process.candidate());
            if (processByKey.putIfAbsent(key, process) != null) {
                throw new IllegalArgumentException("route evidence contains duplicate process identities");
            }
            validateFinalSupplyBounds(checkedSupply, process);
        }

        TreeMap<String, List<DemandSpec>> demandsByCommodity = new TreeMap<>();
        for (ProcessOutputRequest request : checkedSelection.requests()) {
            ProcessInputRoutePlan process = processByKey.get(request.process());
            if (process == null) {
                throw new IllegalArgumentException("selection references an unknown physical process candidate");
            }
            double individualUpperBound = process.candidate().throughput().inputLimitedOutputKgPerSecond();
            if (request.requestedOutputKgPerSecond() > individualUpperBound + EPSILON) {
                throw new IllegalArgumentException(
                        "selected output rate exceeds the process's physical input-limited upper bound");
            }
            for (ProcessInputThroughputEvidence input : process.inputs()) {
                DemandSpec demand = new DemandSpec(request, input);
                demandsByCommodity.computeIfAbsent(input.commodityId(), ignored -> new ArrayList<>())
                        .add(demand);
            }
        }

        ArrayList<InputReservation> provisionalReservations = new ArrayList<>();
        ArrayList<InputDemandEvidence> demandEvidence = new ArrayList<>();
        ArrayList<CommodityReservationEvidence> commodityEvidence = new ArrayList<>();
        boolean accepted = true;
        for (Map.Entry<String, List<DemandSpec>> entry : demandsByCommodity.entrySet()) {
            CommodityFlowResult result = reserveCommodity(
                    checkedSupply,
                    entry.getKey(),
                    entry.getValue());
            provisionalReservations.addAll(result.reservations());
            demandEvidence.addAll(result.demands());
            commodityEvidence.add(result.commodity());
            if (result.commodity().status() != Status.ACCEPTED) {
                accepted = false;
            }
        }

        return new ReservationReport(
                CURRENT_VERSION,
                checkedRoutes.rootSeed(),
                checkedRoutes.resolvedProbeVersion(),
                checkedRoutes.version(),
                checkedRoutes.candidatePlanVersion(),
                checkedRoutes.supplyProfileVersion(),
                checkedSelection,
                accepted ? Status.ACCEPTED : Status.SHARED_SUPPLY_KEY_CONFLICT,
                accepted
                        ? Optional.empty()
                        : Optional.of(FailureReason.SHARED_SUPPLY_KEY_CAPACITY_CONFLICT),
                accepted ? provisionalReservations : List.of(),
                demandEvidence,
                commodityEvidence,
                accepted ? ACCEPTED_MISSING_AUTHORITIES : ALL_MISSING_AUTHORITIES);
    }

    private static CommodityFlowResult reserveCommodity(
            SupplyThroughputReport supply,
            String commodityId,
            List<DemandSpec> sourceDemands) {
        ArrayList<DemandSpec> demands = new ArrayList<>(sourceDemands);
        demands.sort(Comparator.comparing(DemandSpec::key));
        TreeSet<SupplyKey> participatingSupply = new TreeSet<>();
        for (DemandSpec demand : demands) {
            demand.serviceArcs().forEach(value -> participatingSupply.add(value.supplyKey()));
        }

        FlowNetwork network = FlowNetwork.build(supply, demands, participatingSupply);
        double maxFlow = network.maxFlow();
        List<FlowAllocation> allocations = network.allocations();
        TreeMap<DemandKey, Double> allocatedByDemand = new TreeMap<>();
        ArrayList<InputReservation> reservations = new ArrayList<>();
        for (FlowAllocation allocation : allocations) {
            allocatedByDemand.merge(
                    allocation.demand().key(),
                    allocation.usedKgPerSecond(),
                    Stage20IndustrialInputReservationPlan::finiteAdd);
            InputSupplyRouteEvidence routeEvidence = allocation.arc().routeEvidence();
            RouteAssessment route = routeEvidence.route().orElseThrow();
            ProcessSelectionKey process = allocation.demand().request().process();
            SupplyKey supplyKey = routeEvidence.supplyKey();
            reservations.add(new InputReservation(
                    process,
                    commodityId,
                    supplyKey,
                    route,
                    allocation.usedKgPerSecond(),
                    supplyKey.systemId().equals(process.systemId())));
        }

        double required = 0d;
        ArrayList<InputDemandEvidence> demandEvidence = new ArrayList<>();
        for (DemandSpec demand : demands) {
            required = finiteAdd(required, demand.requiredInputKgPerSecond());
            double allocated = allocatedByDemand.getOrDefault(demand.key(), 0d);
            Status demandStatus = allocated + EPSILON >= demand.requiredInputKgPerSecond()
                    ? Status.ACCEPTED
                    : Status.SHARED_SUPPLY_KEY_CONFLICT;
            demandEvidence.add(new InputDemandEvidence(
                    demand.request().process(),
                    demand.input().commodityId(),
                    demand.request().requestedOutputKgPerSecond(),
                    demand.input().inputKgPerOutputKg(),
                    demand.requiredInputKgPerSecond(),
                    Math.min(demand.requiredInputKgPerSecond(), allocated),
                    demandStatus));
        }

        double participatingCapacity = 0d;
        for (SupplyKey key : participatingSupply) {
            participatingCapacity = finiteAdd(
                    participatingCapacity,
                    supply.capacityKgPerSecondBySupply().getOrDefault(key, 0d));
        }
        Status status = maxFlow + EPSILON >= required
                ? Status.ACCEPTED
                : Status.SHARED_SUPPLY_KEY_CONFLICT;
        return new CommodityFlowResult(
                List.copyOf(reservations),
                List.copyOf(demandEvidence),
                new CommodityReservationEvidence(
                        commodityId,
                        required,
                        Math.min(required, maxFlow),
                        participatingCapacity,
                        status));
    }

    private static void validateProcessCoverage(
            SupplyThroughputReport supply,
            RouteEvidenceReport routes) {
        ArrayList<ProcessThroughputEvidence> routeProcesses = new ArrayList<>();
        routes.processes().forEach(value -> routeProcesses.add(value.candidate().throughput()));
        routeProcesses.sort(PROCESS_THROUGHPUT_ORDER);
        if (!routeProcesses.equals(supply.processEvidence())) {
            throw new IllegalArgumentException(
                    "route evidence must exactly cover the retained physical process throughput rows");
        }
    }

    private static void validateFinalSupplyBounds(
            SupplyThroughputReport supply,
            ProcessInputRoutePlan process) {
        for (ProcessInputThroughputEvidence input : process.inputs()) {
            for (InputSupplyRouteEvidence route : input.supplyRoutes()) {
                double finalCapacity = supply.capacityKgPerSecondBySupply()
                        .getOrDefault(route.supplyKey(), 0d);
                if (finalCapacity + EPSILON < route.sourceCapacityKgPerSecond()) {
                    throw new IllegalArgumentException(
                            "input route source exceeds retained final supply-key capacity");
                }
            }
        }
    }

    private record DemandKey(ProcessSelectionKey process, String commodityId)
            implements Comparable<DemandKey> {
        private DemandKey {
            Objects.requireNonNull(process, "process");
            commodityId = requireText(commodityId, "commodityId");
        }

        static DemandKey from(InputDemandEvidence evidence) {
            return new DemandKey(evidence.process(), evidence.inputCommodityId());
        }

        @Override
        public int compareTo(DemandKey other) {
            int comparison = process.compareTo(other.process);
            return comparison != 0 ? comparison : commodityId.compareTo(other.commodityId);
        }
    }

    private record DemandSpec(
            ProcessOutputRequest request,
            ProcessInputThroughputEvidence input,
            double requiredInputKgPerSecond,
            List<ServiceArc> serviceArcs) {
        private DemandSpec(ProcessOutputRequest request, ProcessInputThroughputEvidence input) {
            this(
                    Objects.requireNonNull(request, "request"),
                    Objects.requireNonNull(input, "input"),
                    finiteMultiply(request.requestedOutputKgPerSecond(), input.inputKgPerOutputKg()),
                    admittedArcs(input));
        }

        private DemandSpec {
            serviceArcs = List.copyOf(Objects.requireNonNull(serviceArcs, "serviceArcs"));
        }

        private DemandKey key() {
            return new DemandKey(request.process(), input.commodityId());
        }
    }

    private record ServiceArc(InputSupplyRouteEvidence routeEvidence) {
        private ServiceArc {
            Objects.requireNonNull(routeEvidence, "routeEvidence");
            if (routeEvidence.status() != RouteAdmissionStatus.ADMITTED
                    || routeEvidence.route().isEmpty()) {
                throw new IllegalArgumentException("service arc requires admitted physical route evidence");
            }
        }

        private SupplyKey supplyKey() {
            return routeEvidence.supplyKey();
        }

        private double capacityKgPerSecond() {
            return routeEvidence.admittedInputKgPerSecond();
        }
    }

    private record FlowArcKey(DemandKey demand, SupplyKey supplyKey) {}

    private record FlowAllocation(
            DemandSpec demand,
            ServiceArc arc,
            double usedKgPerSecond) {
        private FlowAllocation {
            Objects.requireNonNull(demand, "demand");
            Objects.requireNonNull(arc, "arc");
            requirePositiveFinite(usedKgPerSecond, "usedKgPerSecond");
        }
    }

    private record CommodityFlowResult(
            List<InputReservation> reservations,
            List<InputDemandEvidence> demands,
            CommodityReservationEvidence commodity) {}

    private record ReservationKey(
            ProcessSelectionKey process,
            String inputCommodityId,
            SupplyKey supplyKey) {
        static ReservationKey from(InputReservation reservation) {
            return new ReservationKey(
                    reservation.process(),
                    reservation.inputCommodityId(),
                    reservation.supplyKey());
        }
    }

    private static final class FlowNetwork {
        private final List<List<Edge>> graph;
        private final int source;
        private final int sink;
        private final Map<FlowArcKey, Edge> serviceEdges;
        private final Map<FlowArcKey, ServiceArc> serviceArcs;
        private final Map<DemandKey, DemandSpec> demandByKey;

        private FlowNetwork(
                List<List<Edge>> graph,
                int source,
                int sink,
                Map<FlowArcKey, Edge> serviceEdges,
                Map<FlowArcKey, ServiceArc> serviceArcs,
                Map<DemandKey, DemandSpec> demandByKey) {
            this.graph = graph;
            this.source = source;
            this.sink = sink;
            this.serviceEdges = serviceEdges;
            this.serviceArcs = serviceArcs;
            this.demandByKey = demandByKey;
        }

        private static FlowNetwork build(
                SupplyThroughputReport supply,
                List<DemandSpec> demands,
                Set<SupplyKey> participatingSupply) {
            ArrayList<SupplyKey> supplyKeys = new ArrayList<>(participatingSupply);
            supplyKeys.sort(Comparator.naturalOrder());
            int source = 0;
            int supplyOffset = 1;
            int demandOffset = supplyOffset + supplyKeys.size();
            int sink = demandOffset + demands.size();
            ArrayList<List<Edge>> graph = new ArrayList<>();
            for (int index = 0; index <= sink; index++) {
                graph.add(new ArrayList<>());
            }

            HashMap<SupplyKey, Integer> supplyNodes = new HashMap<>();
            for (int index = 0; index < supplyKeys.size(); index++) {
                SupplyKey key = supplyKeys.get(index);
                int node = supplyOffset + index;
                supplyNodes.put(key, node);
                double capacity = supply.capacityKgPerSecondBySupply().getOrDefault(key, 0d);
                if (capacity > EPSILON) {
                    addEdge(graph, source, node, capacity);
                }
            }

            LinkedHashMap<FlowArcKey, Edge> serviceEdges = new LinkedHashMap<>();
            LinkedHashMap<FlowArcKey, ServiceArc> serviceArcs = new LinkedHashMap<>();
            TreeMap<DemandKey, DemandSpec> demandByKey = new TreeMap<>();
            for (int demandIndex = 0; demandIndex < demands.size(); demandIndex++) {
                DemandSpec demand = demands.get(demandIndex);
                DemandKey demandKey = demand.key();
                if (demandByKey.putIfAbsent(demandKey, demand) != null) {
                    throw new IllegalArgumentException("duplicate selected process input demand");
                }
                int demandNode = demandOffset + demandIndex;
                addEdge(graph, demandNode, sink, demand.requiredInputKgPerSecond());
                for (ServiceArc arc : demand.serviceArcs()) {
                    Integer supplyNode = supplyNodes.get(arc.supplyKey());
                    if (supplyNode == null) {
                        throw new IllegalArgumentException("input route references absent participating supply");
                    }
                    Edge edge = addEdge(graph, supplyNode, demandNode, arc.capacityKgPerSecond());
                    FlowArcKey key = new FlowArcKey(demandKey, arc.supplyKey());
                    if (serviceEdges.putIfAbsent(key, edge) != null
                            || serviceArcs.putIfAbsent(key, arc) != null) {
                        throw new IllegalArgumentException("duplicate supply-to-process input route");
                    }
                }
            }
            return new FlowNetwork(
                    graph,
                    source,
                    sink,
                    Collections.unmodifiableMap(serviceEdges),
                    Collections.unmodifiableMap(serviceArcs),
                    Collections.unmodifiableMap(demandByKey));
        }

        private double maxFlow() {
            double total = 0d;
            while (true) {
                Edge[] parentEdge = new Edge[graph.size()];
                int[] parentNode = new int[graph.size()];
                java.util.Arrays.fill(parentNode, -1);
                ArrayDeque<Integer> queue = new ArrayDeque<>();
                queue.add(source);
                parentNode[source] = source;
                while (!queue.isEmpty() && parentNode[sink] < 0) {
                    int node = queue.removeFirst();
                    for (Edge edge : graph.get(node)) {
                        if (edge.residualCapacity <= EPSILON || parentNode[edge.to] >= 0) {
                            continue;
                        }
                        parentNode[edge.to] = node;
                        parentEdge[edge.to] = edge;
                        queue.addLast(edge.to);
                        if (edge.to == sink) break;
                    }
                }
                if (parentNode[sink] < 0) break;
                double augmentation = Double.POSITIVE_INFINITY;
                for (int node = sink; node != source; node = parentNode[node]) {
                    augmentation = Math.min(augmentation, parentEdge[node].residualCapacity);
                }
                requirePositiveFinite(augmentation, "flow augmentation");
                for (int node = sink; node != source; node = parentNode[node]) {
                    Edge edge = parentEdge[node];
                    edge.residualCapacity -= augmentation;
                    graph.get(edge.to).get(edge.reverseIndex).residualCapacity += augmentation;
                }
                total = finiteAdd(total, augmentation);
            }
            return total;
        }

        private List<FlowAllocation> allocations() {
            ArrayList<FlowAllocation> allocations = new ArrayList<>();
            for (Map.Entry<FlowArcKey, Edge> entry : serviceEdges.entrySet()) {
                Edge edge = entry.getValue();
                double used = edge.originalCapacity - edge.residualCapacity;
                if (used <= EPSILON) continue;
                FlowArcKey key = entry.getKey();
                allocations.add(new FlowAllocation(
                        demandByKey.get(key.demand()),
                        serviceArcs.get(key),
                        used));
            }
            allocations.sort(FLOW_ALLOCATION_ORDER);
            return List.copyOf(allocations);
        }
    }

    private static final class Edge {
        private final int to;
        private final int reverseIndex;
        private final double originalCapacity;
        private double residualCapacity;

        private Edge(int to, int reverseIndex, double capacity) {
            this.to = to;
            this.reverseIndex = reverseIndex;
            this.originalCapacity = capacity;
            this.residualCapacity = capacity;
        }
    }

    private static List<ServiceArc> admittedArcs(ProcessInputThroughputEvidence input) {
        ArrayList<ServiceArc> arcs = new ArrayList<>();
        for (InputSupplyRouteEvidence route : input.supplyRoutes()) {
            if (route.status() == RouteAdmissionStatus.ADMITTED) {
                arcs.add(new ServiceArc(route));
            }
        }
        arcs.sort(Comparator.comparing(ServiceArc::supplyKey));
        return List.copyOf(arcs);
    }

    private static Edge addEdge(List<List<Edge>> graph, int from, int to, double capacity) {
        requirePositiveFinite(capacity, "flow edge capacity");
        Edge forward = new Edge(to, graph.get(to).size(), capacity);
        Edge reverse = new Edge(from, graph.get(from).size(), 0d);
        graph.get(from).add(forward);
        graph.get(to).add(reverse);
        return forward;
    }

    private static double finiteAdd(double first, double second) {
        requireNonNegativeFinite(first, "first addend");
        requireNonNegativeFinite(second, "second addend");
        double result = first + second;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("throughput sum overflow");
        }
        return result;
    }

    private static double finiteMultiply(double first, double second) {
        requirePositiveFinite(first, "first factor");
        requirePositiveFinite(second, "second factor");
        double result = first * second;
        if (!Double.isFinite(result)) {
            throw new IllegalStateException("throughput product overflow");
        }
        return result;
    }

    private static void close(double actual, double expected, String field) {
        double scale = Math.max(1d, Math.max(Math.abs(actual), Math.abs(expected)));
        if (Math.abs(actual - expected) > EPSILON * scale) {
            throw new IllegalArgumentException(field + " differs from derived selection evidence");
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

    private static Set<MissingAuthority> immutableAuthorities(EnumSet<MissingAuthority> values) {
        return Collections.unmodifiableSet(EnumSet.copyOf(values));
    }

    private static final Comparator<ProcessThroughputEvidence> PROCESS_THROUGHPUT_ORDER = Comparator
            .comparing(ProcessThroughputEvidence::systemId)
            .thenComparing(ProcessThroughputEvidence::stationPlacementId)
            .thenComparing(ProcessThroughputEvidence::facilityDefinitionId)
            .thenComparing(ProcessThroughputEvidence::processId)
            .thenComparing(ProcessThroughputEvidence::outputCommodityId);

    private static final Comparator<InputReservation> RESERVATION_ORDER = Comparator
            .comparing(InputReservation::process)
            .thenComparing(InputReservation::inputCommodityId)
            .thenComparing(InputReservation::supplyKey);

    private static final Comparator<InputDemandEvidence> DEMAND_EVIDENCE_ORDER = Comparator
            .comparing(InputDemandEvidence::process)
            .thenComparing(InputDemandEvidence::inputCommodityId);

    private static final Comparator<FlowAllocation> FLOW_ALLOCATION_ORDER = Comparator
            .comparing((FlowAllocation value) -> value.demand().key())
            .thenComparing(value -> value.arc().supplyKey());
}
