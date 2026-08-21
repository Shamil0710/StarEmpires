package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierResolver;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import com.spacesim.world.generation.Stage20CommodityFrontierCorpusDiagnostics.CommodityEvidence;
import com.spacesim.world.generation.Stage20CommodityFrontierCorpusDiagnostics.Report;
import com.spacesim.world.generation.Stage20CommodityFrontierCorpusDiagnostics.SeedEvidence;
import com.spacesim.world.generation.Stage20CommodityFrontierCorpusDiagnostics.SeedStatus;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only fixed-corpus measurement of the production frontier resolver with maximum-cap precheck.
 *
 * <p>This diagnostic intentionally preserves the v1 corpus-report data contract so baseline and
 * resolved measurements are directly comparable. It changes no production acceptance authority and
 * applies no accepted-seed target.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityFrontierResolvedCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-frontier-resolved-corpus-diagnostics.v1";
    /** Unchanged bounded per-commodity evidence budget. */
    public static final int FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY =
            Stage20CommodityFrontierCorpusDiagnostics.FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY;

    private Stage20CommodityFrontierResolvedCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /**
     * Replays the fixed representative corpus through the production maximum-cap frontier resolver.
     *
     * @return deterministic measurement using the unchanged physical/economic authorities
     */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        if (!capacity.bootstrapRequirementVersion().equals(profile.bootstrapRequirementVersion())) {
            throw new IllegalStateException(
                    "freight-capacity authority and candidate profile disagree on bootstrap requirements");
        }
        PhysicalTransportAuthority transport = profile.inputs().transport();
        if (Math.abs(transport.fleetProfile().payloadMassKgPerFreighter() - capacity.payloadMassKg()) > 1.0e-9d) {
            throw new IllegalStateException("freight-capacity authority and production profile use different payloads");
        }

        int perStartBudget = capacity.requiredFreighterCountPerFactionStart();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        ArrayList<CommodityRequirement> requirements = new ArrayList<>(
                profile.inputs().acceptance().bootstrapRequirements().essentialCommodities());
        requirements.sort(Comparator.comparing(CommodityRequirement::commodityId));
        if (requirements.isEmpty()) {
            throw new IllegalStateException("representative bootstrap profile must contain essential commodities");
        }

        ArrayList<SeedEvidence> seeds = new ArrayList<>();
        int acceptedPlacements = 0;
        int acceptedCombinations = 0;
        int infeasibleCombinations = 0;
        int unresolvedCombinations = 0;
        int totalFrontierNodes = 0;
        int maxCommodityNodes = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            var probe = Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            PlacementResult placement = probe.placement().orElseThrow();
            if (placement.status() != PlacementStatus.ACCEPTED) {
                seeds.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        SeedStatus.PLACEMENT_REJECTED,
                        List.of(),
                        Optional.empty(),
                        Optional.empty(),
                        Map.of(),
                        List.of()));
                continue;
            }
            acceptedPlacements++;

            GalaxyTopology topology = probe.topology().requireAcceptedTopology();
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
            Stage20PhysicalFreightRouteEvaluator routes = Stage20WholePlacementCapacityCorpusDiagnostics.physicalRoutes(
                    topology,
                    probe.jumpEdges().orElseThrow(),
                    probe.localLayouts().orElseThrow(),
                    stations,
                    transport,
                    perStartBudget);
            TreeMap<String, Integer> perFactionBudgets = budgets(placement, perStartBudget);

            ArrayList<FrontierReport> frontierReports = new ArrayList<>();
            ArrayList<CommodityEvidence> commodityEvidence = new ArrayList<>();
            for (CommodityRequirement requirement : requirements) {
                FrontierReport frontier = Stage20CommodityWholePlacementFrontierResolver.resolve(
                        topology,
                        placement,
                        supply,
                        requirement,
                        perFactionBudgets,
                        FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY,
                        routes::assessWithAllocatedFreighters);
                frontierReports.add(frontier);
                totalFrontierNodes = Math.addExact(totalFrontierNodes, frontier.searchNodesVisited());
                maxCommodityNodes = Math.max(maxCommodityNodes, frontier.searchNodesVisited());
                commodityEvidence.add(new CommodityEvidence(
                        requirement.commodityId(),
                        frontier.status(),
                        frontier.searchNodesVisited(),
                        frontier.options().size(),
                        frontier.options().stream().map(value -> value.remoteFreightersByFaction()).toList()));
            }

            CombinationReport combination = Stage20CommodityFreightFrontierCombiner.combine(
                    frontierReports.stream().map(FrontierReport::toCombinerFrontier).toList(),
                    perFactionBudgets);
            SeedStatus seedStatus;
            if (combination.status() == Status.ACCEPTED) {
                acceptedCombinations++;
                seedStatus = SeedStatus.COMBINER_ACCEPTED;
            } else if (combination.status() == Status.INFEASIBLE) {
                infeasibleCombinations++;
                seedStatus = SeedStatus.COMBINER_INFEASIBLE;
            } else {
                unresolvedCombinations++;
                seedStatus = SeedStatus.COMBINER_UNRESOLVED;
            }
            seeds.add(new SeedEvidence(
                    rootSeed,
                    placement.status(),
                    seedStatus,
                    commodityEvidence,
                    Optional.of(combination.status()),
                    combination.failureReason(),
                    combination.remoteFreightersUsedByFaction(),
                    combination.selectedOptions()));
        }

        return new Report(
                CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                capacity.version(),
                Stage20CommodityWholePlacementFrontierResolver.CURRENT_VERSION,
                Stage20CommodityFreightFrontierCombiner.CURRENT_VERSION,
                perStartBudget,
                FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY,
                seeds.size(),
                acceptedPlacements,
                acceptedCombinations,
                infeasibleCombinations,
                unresolvedCombinations,
                totalFrontierNodes,
                maxCommodityNodes,
                seeds);
    }

    /**
     * Serializes the resolved measurement using the stable baseline corpus format.
     *
     * @param report measured report
     * @return deterministic text ending with a newline
     */
    public static String toText(Report report) {
        return Stage20CommodityFrontierCorpusDiagnostics.toText(report);
    }

    private static TreeMap<String, Integer> budgets(PlacementResult placement, int perStartBudget) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Assignment assignment : placement.assignments()) {
            if (result.putIfAbsent(assignment.stableFactionId(), perStartBudget) != null) {
                throw new IllegalStateException("accepted placement contains duplicate faction IDs");
            }
        }
        return result;
    }
}
