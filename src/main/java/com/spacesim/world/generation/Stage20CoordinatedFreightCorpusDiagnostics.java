package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.FailureReason;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.PlanReport;
import com.spacesim.world.Stage20CoordinatedWholePlacementFreightPlanner.Status;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only Stage-20E fixed-corpus measurement of the globally coordinated whole-placement freight planner.
 *
 * <p>The diagnostic replays the unchanged representative v2-candidate world-generation inputs and fixed
 * seed corpus. Only after the existing placement layer has accepted a seed does it invoke
 * {@link Stage20CoordinatedWholePlacementFreightPlanner} with the independently derived finite
 * per-start freight service-capacity requirement. The physical freight evaluator is the same helper
 * already used by {@link Stage20WholePlacementCapacityCorpusDiagnostics}; route geometry, fitted FTL,
 * local access, handling rates and payload are therefore not re-authored here.</p>
 *
 * <p>{@link #SEARCH_NODE_BUDGET_PER_SEED} is deliberately a CI measurement budget rather than a world
 * acceptance threshold. Exhaustion remains {@link SeedStatus#PLANNER_UNRESOLVED}; it is never folded
 * into physical infeasibility or seed rejection. This class does not change production acceptance,
 * grant ships, mutate resources/topology, or alter the frozen representative baseline.</p>
 */
public final class Stage20CoordinatedFreightCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.coordinated-freight-corpus-diagnostics.v1";

    /** Bounded CI evidence budget per accepted-placement seed; not a generation-quality threshold. */
    public static final int SEARCH_NODE_BUDGET_PER_SEED = 2_000;

    private Stage20CoordinatedFreightCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** Mutually exclusive diagnostic outcome for one fixed seed. */
    public enum SeedStatus {
        /** Existing v2-candidate placement was rejected before coordinated planning. */ PLACEMENT_REJECTED,
        /** Coordinated planner found a globally feasible finite-freight/shared-producer plan. */ PLANNER_ACCEPTED,
        /** Coordinated planner proved infeasibility under the supplied physical/fleet authorities. */ PLANNER_INFEASIBLE,
        /** Bounded diagnostic search ended without either a feasible plan or an infeasibility proof. */ PLANNER_UNRESOLVED
    }

    /**
     * Per-seed coordinated-planner evidence.
     *
     * @param rootSeed exact fixed-corpus root seed
     * @param placementStatus unchanged representative v2-candidate placement status
     * @param status mutually exclusive diagnostic outcome
     * @param plannerStatus coordinated planner status when placement was accepted
     * @param failureReason planner failure/unresolved reason when present
     * @param searchNodesVisited bounded discrete allocation states inspected
     * @param totalRemoteFreightersUsed accepted-plan remote freighters, otherwise zero
     */
    public record SeedEvidence(
            long rootSeed,
            PlacementStatus placementStatus,
            SeedStatus status,
            Optional<Status> plannerStatus,
            Optional<FailureReason> failureReason,
            int searchNodesVisited,
            int totalRemoteFreightersUsed) {
    }

    /**
     * Aggregate fixed-corpus coordinated-planning evidence.
     *
     * @param version diagnostic version
     * @param candidateProfileVersion unchanged v2-candidate profile version
     * @param bootstrapRequirementVersion corrected bootstrap requirement version
     * @param freightCapacityRequirementVersion derived finite per-start freight authority version
     * @param plannerVersion coordinated whole-placement planner version
     * @param perStartFreighterBudget derived finite service-capacity budget per placed start
     * @param searchNodeBudgetPerSeed bounded CI measurement budget per accepted placement
     * @param fixedSeedCount fixed corpus size
     * @param acceptedPlacementSeedCount seeds reaching coordinated planning
     * @param plannerAcceptedSeedCount accepted placements with a feasible coordinated plan
     * @param plannerInfeasibleSeedCount accepted placements with proved physical/fleet infeasibility
     * @param plannerUnresolvedSeedCount accepted placements whose bounded search remained unresolved
     * @param failureReasonCounts aggregate planner failure/unresolved reasons
     * @param totalSearchNodesVisited total search states visited across accepted placements
     * @param maxSearchNodesVisited maximum search states visited by one seed
     * @param totalAcceptedRemoteFreightersUsed remote freighters used across accepted coordinated plans
     * @param seeds deterministic per-seed evidence
     */
    public record Report(
            String version,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            String freightCapacityRequirementVersion,
            String plannerVersion,
            int perStartFreighterBudget,
            int searchNodeBudgetPerSeed,
            int fixedSeedCount,
            int acceptedPlacementSeedCount,
            int plannerAcceptedSeedCount,
            int plannerInfeasibleSeedCount,
            int plannerUnresolvedSeedCount,
            Map<String, Integer> failureReasonCounts,
            int totalSearchNodesVisited,
            int maxSearchNodesVisited,
            int totalAcceptedRemoteFreightersUsed,
            List<SeedEvidence> seeds) {
    }

    /**
     * Replays the fixed representative corpus under the coordinated planner without changing production acceptance.
     *
     * @return deterministic read-only coordinated-planning evidence
     */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        if (!capacity.bootstrapRequirementVersion().equals(profile.bootstrapRequirementVersion())) {
            throw new IllegalStateException("freight-capacity authority and v2 candidate use different bootstrap requirements");
        }
        PhysicalTransportAuthority transport = profile.inputs().transport();
        if (Math.abs(transport.fleetProfile().payloadMassKgPerFreighter() - capacity.payloadMassKg()) > 1.0e-9d) {
            throw new IllegalStateException("freight-capacity authority and production profile use different payloads");
        }

        int perStartBudget = capacity.requiredFreighterCountPerFactionStart();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        List<CommodityRequirement> requirements =
                profile.inputs().acceptance().bootstrapRequirements().essentialCommodities();
        ArrayList<SeedEvidence> seeds = new ArrayList<>();
        TreeMap<String, Integer> failures = new TreeMap<>();
        int acceptedPlacements = 0;
        int acceptedPlans = 0;
        int infeasiblePlans = 0;
        int unresolvedPlans = 0;
        int totalNodes = 0;
        int maxNodes = 0;
        int totalAcceptedRemoteFreighters = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            var probe = Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            PlacementResult placement = probe.placement().orElseThrow();
            if (placement.status() != PlacementStatus.ACCEPTED) {
                seeds.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        SeedStatus.PLACEMENT_REJECTED,
                        Optional.empty(),
                        Optional.empty(),
                        0,
                        0));
                continue;
            }
            acceptedPlacements++;

            GalaxyTopology topology = probe.topology().requireAcceptedTopology();
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
            Stage20PhysicalFreightRouteEvaluator routes =
                    Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(
                            topology,
                            probe.jumpEdges().orElseThrow(),
                            probe.localLayouts().orElseThrow(),
                            stations,
                            transport,
                            perStartBudget);
            PlanReport plan = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                    topology,
                    placement,
                    supply,
                    requirements,
                    perStartBudget,
                    SEARCH_NODE_BUDGET_PER_SEED,
                    routes::assessWithAllocatedFreighters);

            totalNodes = Math.addExact(totalNodes, plan.searchNodesVisited());
            maxNodes = Math.max(maxNodes, plan.searchNodesVisited());
            SeedStatus seedStatus;
            if (plan.status() == Status.ACCEPTED) {
                acceptedPlans++;
                totalAcceptedRemoteFreighters = Math.addExact(
                        totalAcceptedRemoteFreighters,
                        plan.totalRemoteFreightersUsed());
                seedStatus = SeedStatus.PLANNER_ACCEPTED;
            } else if (plan.status() == Status.INFEASIBLE) {
                infeasiblePlans++;
                FailureReason reason = plan.failureReason().orElseThrow();
                failures.merge(reason.name(), 1, Math::addExact);
                seedStatus = SeedStatus.PLANNER_INFEASIBLE;
            } else {
                unresolvedPlans++;
                FailureReason reason = plan.failureReason().orElseThrow();
                failures.merge(reason.name(), 1, Math::addExact);
                seedStatus = SeedStatus.PLANNER_UNRESOLVED;
            }
            seeds.add(new SeedEvidence(
                    rootSeed,
                    placement.status(),
                    seedStatus,
                    Optional.of(plan.status()),
                    plan.failureReason(),
                    plan.searchNodesVisited(),
                    plan.totalRemoteFreightersUsed()));
        }

        return new Report(
                CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                capacity.version(),
                Stage20CoordinatedWholePlacementFreightPlanner.CURRENT_VERSION,
                perStartBudget,
                SEARCH_NODE_BUDGET_PER_SEED,
                seeds.size(),
                acceptedPlacements,
                acceptedPlans,
                infeasiblePlans,
                unresolvedPlans,
                Map.copyOf(failures),
                totalNodes,
                maxNodes,
                totalAcceptedRemoteFreighters,
                List.copyOf(seeds));
    }

    /**
     * Serializes compact deterministic CI evidence.
     *
     * @param report measured report
     * @return stable text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(4_096);
        text.append("version=").append(value.version()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("freightCapacityRequirementVersion=").append(value.freightCapacityRequirementVersion()).append('\n');
        text.append("plannerVersion=").append(value.plannerVersion()).append('\n');
        text.append("perStartFreighterBudget=").append(value.perStartFreighterBudget()).append('\n');
        text.append("searchNodeBudgetPerSeed=").append(value.searchNodeBudgetPerSeed()).append('\n');
        text.append("fixedSeedCount=").append(value.fixedSeedCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("plannerAcceptedSeedCount=").append(value.plannerAcceptedSeedCount()).append('\n');
        text.append("plannerInfeasibleSeedCount=").append(value.plannerInfeasibleSeedCount()).append('\n');
        text.append("plannerUnresolvedSeedCount=").append(value.plannerUnresolvedSeedCount()).append('\n');
        text.append("failureReasonCounts=").append(new TreeMap<>(value.failureReasonCounts())).append('\n');
        text.append("totalSearchNodesVisited=").append(value.totalSearchNodesVisited()).append('\n');
        text.append("maxSearchNodesVisited=").append(value.maxSearchNodesVisited()).append('\n');
        text.append("totalAcceptedRemoteFreightersUsed=")
                .append(value.totalAcceptedRemoteFreightersUsed()).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" status=").append(seed.status())
                    .append(" planner=").append(seed.plannerStatus().map(Enum::name).orElse("NONE"))
                    .append(" failure=").append(seed.failureReason().map(Enum::name).orElse("NONE"))
                    .append(" nodes=").append(seed.searchNodesVisited())
                    .append(" remoteFreighters=").append(seed.totalRemoteFreightersUsed())
                    .append('\n');
        }
        return text.toString();
    }
}
