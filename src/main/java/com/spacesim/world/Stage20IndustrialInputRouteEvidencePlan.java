package com.spacesim.world;

import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.CandidateReport;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.MissingAuthority;
import com.spacesim.world.Stage20IndustrialSpecializationCandidatePlan.ProcessCandidate;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.InputSupplyRouteEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.ProcessInputThroughputEvidence;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.RouteAdmissionStatus;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Reconstructs exact candidate source/route provenance for Stage-20F industrial process inputs.
 *
 * <p>The Stage-20E supply closure already evaluates physical input delivery, but its earlier public
 * evidence retained only the final input-limited output number. This plan exposes the candidate
 * supply keys, finite source capacity, explicit neighbor route, route-time admission state and
 * route-limited deliverable capacity behind every process input.</p>
 *
 * <p>The evidence remains non-reserved. One upstream supply key or the same representative freight
 * capacity may appear in several process candidates. This plan therefore cannot authorize process
 * operation, inventory, specialization or freight ownership.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20IndustrialInputRouteEvidencePlan {
    /** Stable Stage-20F industrial-input route-evidence version. */
    public static final String CURRENT_VERSION = "stage20f.industrial-input-route-evidence-plan.v1";
    private static final double EPSILON = 1.0e-9d;
    private static final Set<MissingAuthority> CURRENT_MISSING_AUTHORITIES = Collections.unmodifiableSet(
            EnumSet.allOf(MissingAuthority.class));

    private Stage20IndustrialInputRouteEvidencePlan() {
        throw new AssertionError("No instances");
    }

    /**
     * One exact facility/process candidate and all candidate routes for each required input.
     *
     * @param candidate exact Stage-20F facility/process candidate
     * @param inputs unchanged input source/route evidence retained by its physical throughput row
     */
    public record ProcessInputRoutePlan(
            ProcessCandidate candidate,
            List<ProcessInputThroughputEvidence> inputs) {
        /**
         * Validates an exact candidate-to-input-evidence join.
         *
         * @param candidate exact Stage-20F facility/process candidate
         * @param inputs unchanged physical input evidence
         */
        public ProcessInputRoutePlan {
            Objects.requireNonNull(candidate, "candidate");
            ArrayList<ProcessInputThroughputEvidence> copy = new ArrayList<>(Objects.requireNonNull(
                    inputs, "inputs"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("process input route plan requires input evidence");
            }
            copy.sort(Comparator.comparing(ProcessInputThroughputEvidence::commodityId));
            inputs = List.copyOf(copy);
            if (!inputs.equals(candidate.throughput().inputEvidence())) {
                throw new IllegalArgumentException(
                        "process input routes must equal the candidate's physical throughput evidence");
            }
        }

        /** @return number of physically admitted, still-unreserved supply routes */
        public int admittedRouteCount() {
            return (int) inputs.stream()
                    .flatMap(input -> input.supplyRoutes().stream())
                    .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                    .count();
        }

        /** @return number of admitted routes crossing at least one inter-system edge */
        public int admittedRemoteRouteCount() {
            return (int) inputs.stream()
                    .flatMap(input -> input.supplyRoutes().stream())
                    .filter(route -> route.status() == RouteAdmissionStatus.ADMITTED)
                    .filter(route -> !route.supplyKey().systemId().equals(
                            candidate.capacity().systemId()))
                    .count();
        }
    }

    /**
     * Complete fail-closed industrial-input route evidence for one exact accepted generated seed.
     *
     * @param version route-evidence plan version
     * @param rootSeed exact accepted generated root seed
     * @param resolvedProbeVersion exact resolved production-probe version
     * @param candidatePlanVersion exact specialization candidate-plan version
     * @param supplyProfileVersion exact non-reserved supply-analysis profile version
     * @param processes exact process/input route plans
     * @param missingAuthorities authorities still blocking operational specialization
     */
    public record RouteEvidenceReport(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String candidatePlanVersion,
            String supplyProfileVersion,
            List<ProcessInputRoutePlan> processes,
            Set<MissingAuthority> missingAuthorities) {
        /**
         * Validates complete deterministic process coverage and fail-closed authority state.
         *
         * @param version route-evidence plan version
         * @param rootSeed exact accepted root seed
         * @param resolvedProbeVersion exact resolved production-probe version
         * @param candidatePlanVersion exact candidate-plan version
         * @param supplyProfileVersion exact supply-analysis profile version
         * @param processes complete process/input route plans
         * @param missingAuthorities unresolved operational authorities
         */
        public RouteEvidenceReport {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            candidatePlanVersion = requireText(candidatePlanVersion, "candidatePlanVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            ArrayList<ProcessInputRoutePlan> copy = new ArrayList<>(Objects.requireNonNull(
                    processes, "processes"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("route evidence requires process input plans");
            }
            copy.sort(PROCESS_ORDER);
            HashSet<ProcessKey> keys = new HashSet<>();
            for (ProcessInputRoutePlan process : copy) {
                if (!keys.add(ProcessKey.from(process.candidate()))) {
                    throw new IllegalArgumentException("process input route plans must be unique");
                }
            }
            processes = List.copyOf(copy);

            Objects.requireNonNull(missingAuthorities, "missingAuthorities");
            EnumSet<MissingAuthority> missing = missingAuthorities.isEmpty()
                    ? EnumSet.noneOf(MissingAuthority.class)
                    : EnumSet.copyOf(missingAuthorities);
            if (!missing.equals(CURRENT_MISSING_AUTHORITIES)) {
                throw new IllegalArgumentException(
                        "route evidence cannot silently close an unresolved operational authority");
            }
            missingAuthorities = Collections.unmodifiableSet(missing);
        }

        /** @return total candidate supply-key route rows across every process input */
        public int candidateRouteCount() {
            return processes.stream()
                    .flatMap(process -> process.inputs().stream())
                    .mapToInt(input -> input.supplyRoutes().size())
                    .sum();
        }

        /** @return total physically admitted but unreserved supply routes */
        public int admittedRouteCount() {
            return processes.stream().mapToInt(ProcessInputRoutePlan::admittedRouteCount).sum();
        }

        /** @return whether input capacity and its physical freight are already reserved and owned */
        public boolean reservationAuthoritative() {
            return !missingAuthorities.contains(MissingAuthority.RESERVED_INDUSTRIAL_INPUTS)
                    && !missingAuthorities.contains(MissingAuthority.OWNED_INDUSTRIAL_INPUT_FREIGHT);
        }
    }

    /**
     * Reconstructs route-level process-input evidence from one accepted resolved generated world.
     *
     * @param resolved exact accepted resolved production evidence
     * @return deterministic non-reserved input source/route evidence
     */
    public static RouteEvidenceReport reconstruct(ResolvedProbeResult resolved) {
        ResolvedProbeResult accepted = Objects.requireNonNull(resolved, "resolved");
        CandidateReport candidates = Stage20IndustrialSpecializationCandidatePlan.reconstruct(accepted);
        GalaxyTopology topology = accepted.generation().topology().requireAcceptedTopology();
        SupplyThroughputReport supply = accepted.generation().supplyThroughput().orElseThrow(
                () -> new IllegalArgumentException("accepted generated seed lost physical supply evidence"));

        ArrayList<ProcessInputRoutePlan> processes = new ArrayList<>();
        for (var system : candidates.systems()) {
            for (var station : system.stations()) {
                for (ProcessCandidate candidate : station.processes()) {
                    validateRoutes(topology, supply, candidate);
                    processes.add(new ProcessInputRoutePlan(
                            candidate,
                            candidate.throughput().inputEvidence()));
                }
            }
        }
        if (processes.size() != supply.processEvidence().size()) {
            throw new IllegalArgumentException(
                    "input route evidence must exactly cover the physical process throughput rows");
        }
        return new RouteEvidenceReport(
                CURRENT_VERSION,
                candidates.rootSeed(),
                candidates.resolvedProbeVersion(),
                candidates.version(),
                candidates.supplyProfileVersion(),
                processes,
                candidates.missingAuthorities());
    }

    private static void validateRoutes(
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            ProcessCandidate candidate) {
        for (ProcessInputThroughputEvidence input : candidate.throughput().inputEvidence()) {
            for (InputSupplyRouteEvidence route : input.supplyRoutes()) {
                Double finalSourceCapacity = supply.capacityKgPerSecondBySupply().get(route.supplyKey());
                if (finalSourceCapacity == null
                        || finalSourceCapacity + EPSILON < route.sourceCapacityKgPerSecond()) {
                    throw new IllegalArgumentException(
                            "candidate input source exceeds the retained physical supply closure");
                }
                if (route.route().isEmpty()) {
                    continue;
                }
                List<StarSystemId> path = route.route().orElseThrow().orderedSystems();
                for (int index = 0; index < path.size() - 1; index++) {
                    if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                        throw new IllegalArgumentException(
                                "industrial input route contains a non-neighbor shortcut");
                    }
                }
            }
        }
    }

    private record ProcessKey(
            StarSystemId systemId,
            String stationPlacementId,
            String facilityDefinitionId,
            String processId,
            String outputCommodityId) {
        static ProcessKey from(ProcessCandidate candidate) {
            var capacity = candidate.capacity();
            return new ProcessKey(
                    capacity.systemId(),
                    capacity.stationPlacementId(),
                    capacity.facilityDefinitionId(),
                    capacity.processId(),
                    capacity.outputCommodityId());
        }
    }

    private static final Comparator<ProcessInputRoutePlan> PROCESS_ORDER = Comparator
            .comparing((ProcessInputRoutePlan value) -> value.candidate().capacity().systemId())
            .thenComparing(value -> value.candidate().capacity().stationPlacementId())
            .thenComparing(value -> value.candidate().capacity().facilityDefinitionId())
            .thenComparing(value -> value.candidate().capacity().processId())
            .thenComparing(value -> value.candidate().capacity().outputCommodityId());

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
