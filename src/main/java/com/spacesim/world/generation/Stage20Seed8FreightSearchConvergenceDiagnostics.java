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
import java.util.Objects;
import java.util.Optional;

/**
 * Targeted read-only Stage-20E convergence evidence for fixed representative seed 8.
 *
 * <p>The coordinated fixed-corpus diagnostic identified seed 8 as the only accepted-placement seed
 * whose planner result remained {@link Status#UNRESOLVED_SEARCH_BUDGET} at the 2,000-node CI
 * measurement budget. This diagnostic replays that exact generated seed once, then evaluates the same
 * planner and physical freight authority at a bounded deterministic budget ladder. It stops at the
 * first resolved result so later budgets cannot become an implicit performance target.</p>
 *
 * <p>The ladder is search-characterization evidence only. It does not modify the 2,000-node corpus
 * budget, change world acceptance, alter route/resource/fleet authority or turn an unresolved result
 * into a rejection. If no rung resolves the seed, planner/search structure must be investigated before
 * any physical world-generation tuning.</p>
 */
public final class Stage20Seed8FreightSearchConvergenceDiagnostics {
    /** Stable targeted diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.seed8-freight-search-convergence-diagnostics.v1";

    /** Exact unresolved fixed-corpus seed identified by coordinated corpus evidence v1. */
    public static final long ROOT_SEED = 8L;

    /** Deterministic bounded evidence ladder; stops at first resolved planner result. */
    public static final List<Integer> SEARCH_NODE_BUDGET_LADDER = List.of(2_000, 4_000, 8_000);

    private Stage20Seed8FreightSearchConvergenceDiagnostics() {
        throw new AssertionError("No instances");
    }

    /**
     * One planner attempt at one CI evidence budget.
     *
     * @param searchNodeBudget supplied planner node budget
     * @param status planner status
     * @param failureReason explicit failure/unresolved reason when present
     * @param searchNodesVisited discrete states actually inspected
     * @param totalRemoteFreightersUsed accepted-plan remote freighters, otherwise zero
     */
    public record Attempt(
            int searchNodeBudget,
            Status status,
            Optional<FailureReason> failureReason,
            int searchNodesVisited,
            int totalRemoteFreightersUsed) {
        /** Validates bounded planner evidence for one ladder rung. */
        public Attempt {
            if (searchNodeBudget <= 0 || searchNodesVisited < 0 || searchNodesVisited > searchNodeBudget
                    || totalRemoteFreightersUsed < 0) {
                throw new IllegalArgumentException("search/freight counts must fit their non-negative bounds");
            }
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(failureReason, "failureReason");
            if (status == Status.ACCEPTED && failureReason.isPresent()) {
                throw new IllegalArgumentException("accepted attempt cannot expose a failure reason");
            }
            if (status != Status.ACCEPTED && failureReason.isEmpty()) {
                throw new IllegalArgumentException("non-accepted attempt requires an explicit reason");
            }
            if (status == Status.UNRESOLVED_SEARCH_BUDGET
                    != failureReason.equals(Optional.of(FailureReason.SEARCH_NODE_BUDGET_EXHAUSTED))) {
                throw new IllegalArgumentException("unresolved status and search-budget reason must agree");
            }
        }
    }

    /**
     * Complete targeted convergence evidence.
     *
     * @param version diagnostic version
     * @param rootSeed exact fixed-corpus seed
     * @param candidateProfileVersion unchanged v2-candidate profile version
     * @param bootstrapRequirementVersion corrected bootstrap requirement version
     * @param freightCapacityRequirementVersion derived finite per-start freight authority version
     * @param plannerVersion coordinated planner version
     * @param perStartFreighterBudget derived finite service-capacity budget per placed start
     * @param baselineCorpusSearchBudget existing corpus diagnostic search budget
     * @param attempts ordered evaluated ladder rungs
     * @param firstResolvedBudget first budget that returned accepted/proved-infeasible, when any
     */
    public record Report(
            String version,
            long rootSeed,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            String freightCapacityRequirementVersion,
            String plannerVersion,
            int perStartFreighterBudget,
            int baselineCorpusSearchBudget,
            List<Attempt> attempts,
            Optional<Integer> firstResolvedBudget) {
        /** Validates deterministic targeted convergence evidence. */
        public Report {
            version = requireText(version, "version");
            candidateProfileVersion = requireText(candidateProfileVersion, "candidateProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            freightCapacityRequirementVersion = requireText(
                    freightCapacityRequirementVersion, "freightCapacityRequirementVersion");
            plannerVersion = requireText(plannerVersion, "plannerVersion");
            if (rootSeed <= 0L || perStartFreighterBudget <= 0 || baselineCorpusSearchBudget <= 0) {
                throw new IllegalArgumentException("seed and budgets must be positive");
            }
            ArrayList<Attempt> copy = new ArrayList<>(Objects.requireNonNull(attempts, "attempts"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("attempts must be non-empty and contain no nulls");
            }
            attempts = List.copyOf(copy);
            Objects.requireNonNull(firstResolvedBudget, "firstResolvedBudget");
            Optional<Integer> measuredResolved = attempts.stream()
                    .filter(value -> value.status() != Status.UNRESOLVED_SEARCH_BUDGET)
                    .map(Attempt::searchNodeBudget)
                    .findFirst();
            if (!measuredResolved.equals(firstResolvedBudget)) {
                throw new IllegalArgumentException("firstResolvedBudget must match ordered attempts");
            }
        }
    }

    /**
     * Replays fixed seed 8 once and evaluates increasing bounded search evidence until first resolution.
     *
     * @return deterministic targeted convergence report
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

        var probe = Stage20GeneratedWorldProductionProbe.run(ROOT_SEED, profile.inputs());
        PlacementResult placement = probe.placement().orElseThrow();
        if (placement.status() != PlacementStatus.ACCEPTED) {
            throw new IllegalStateException("targeted seed no longer reaches accepted v2-candidate placement");
        }
        int perStartBudget = capacity.requiredFreighterCountPerFactionStart();
        GalaxyTopology topology = probe.topology().requireAcceptedTopology();
        SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        Stage20PhysicalFreightRouteEvaluator routes = Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(
                topology,
                probe.jumpEdges().orElseThrow(),
                probe.localLayouts().orElseThrow(),
                stations,
                transport,
                perStartBudget);
        List<CommodityRequirement> requirements =
                profile.inputs().acceptance().bootstrapRequirements().essentialCommodities();

        ArrayList<Attempt> attempts = new ArrayList<>();
        Optional<Integer> resolvedBudget = Optional.empty();
        for (int searchBudget : SEARCH_NODE_BUDGET_LADDER) {
            PlanReport plan = Stage20CoordinatedWholePlacementFreightPlanner.plan(
                    topology,
                    placement,
                    supply,
                    requirements,
                    perStartBudget,
                    searchBudget,
                    routes::assessWithAllocatedFreighters);
            attempts.add(new Attempt(
                    searchBudget,
                    plan.status(),
                    plan.failureReason(),
                    plan.searchNodesVisited(),
                    plan.totalRemoteFreightersUsed()));
            if (plan.status() != Status.UNRESOLVED_SEARCH_BUDGET) {
                resolvedBudget = Optional.of(searchBudget);
                break;
            }
        }

        return new Report(
                CURRENT_VERSION,
                ROOT_SEED,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                capacity.version(),
                Stage20CoordinatedWholePlacementFreightPlanner.CURRENT_VERSION,
                perStartBudget,
                Stage20CoordinatedFreightCorpusDiagnostics.SEARCH_NODE_BUDGET_PER_SEED,
                attempts,
                resolvedBudget);
    }

    /**
     * Serializes compact deterministic CI evidence.
     *
     * @param report measured report
     * @return stable text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(1_024);
        text.append("version=").append(value.version()).append('\n');
        text.append("rootSeed=").append(value.rootSeed()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("freightCapacityRequirementVersion=").append(value.freightCapacityRequirementVersion()).append('\n');
        text.append("plannerVersion=").append(value.plannerVersion()).append('\n');
        text.append("perStartFreighterBudget=").append(value.perStartFreighterBudget()).append('\n');
        text.append("baselineCorpusSearchBudget=").append(value.baselineCorpusSearchBudget()).append('\n');
        text.append("firstResolvedBudget=").append(value.firstResolvedBudget().map(String::valueOf).orElse("NONE"))
                .append('\n');
        for (Attempt attempt : value.attempts()) {
            text.append("budget=").append(attempt.searchNodeBudget())
                    .append(" status=").append(attempt.status())
                    .append(" failure=").append(attempt.failureReason().map(Enum::name).orElse("NONE"))
                    .append(" nodes=").append(attempt.searchNodesVisited())
                    .append(" remoteFreighters=").append(attempt.totalRemoteFreightersUsed())
                    .append('\n');
        }
        return text.toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
