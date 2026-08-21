package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound.Assessment;
import com.spacesim.world.Stage20CommodityFrontierSharedProducerBound.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Production Stage-20E entry point for one commodity's whole-placement freight frontier.
 *
 * <p>The resolver first evaluates the verified shared-producer relaxation at the maximum
 * authoritative per-start fleet-cap vector. If even that deliberately over-generous network cannot
 * satisfy whole-placement demand, the exact physical frontier is proven empty and no route-prefix
 * DFS is necessary. Otherwise the accepted frontier-generator v2 runs unchanged.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityWholePlacementFrontierResolver {
    /** Stable production resolver version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-whole-placement-frontier-resolver.v1";

    private Stage20CommodityWholePlacementFrontierResolver() {
        throw new AssertionError("No instances");
    }

    /**
     * Resolves one commodity frontier without converting search uncertainty into physical failure.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted non-empty faction-start placement
     * @param supply authoritative physical producer capacities
     * @param requirement one essential bootstrap commodity requirement
     * @param remoteFreighterBudgetByFaction maximum physical remote-freighter budget at every start
     * @param searchNodeBudget bounded exact-search evidence budget when the precheck remains possible
     * @param routes authoritative route evaluator parameterized by allocated integer freighters
     * @return complete or explicitly unresolved frontier evidence
     */
    public static FrontierReport resolve(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            CommodityRequirement requirement,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            int searchNodeBudget,
            AllocatedRouteEvaluator routes) {
        Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(placement, "placement");
        Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(requirement, "requirement");
        Objects.requireNonNull(remoteFreighterBudgetByFaction, "remoteFreighterBudgetByFaction");
        Objects.requireNonNull(routes, "routes");
        if (searchNodeBudget <= 0) {
            throw new IllegalArgumentException("searchNodeBudget must be positive");
        }

        Assessment maximumCap = Stage20CommodityFrontierSharedProducerBound.assess(
                topology,
                placement,
                supply,
                requirement,
                remoteFreighterBudgetByFaction,
                routes);
        if (maximumCap.status() == Status.PROVED_INFEASIBLE) {
            return new FrontierReport(
                    CURRENT_VERSION,
                    placement.version(),
                    supply.profileVersion(),
                    requirement.commodityId(),
                    searchNodeBudget,
                    0,
                    Stage20CommodityFreightFrontierCombiner.FrontierStatus.COMPLETE,
                    maximumCap.remoteFreighterCapByFaction(),
                    List.of());
        }

        FrontierReport exact = Stage20CommodityWholePlacementFrontierGenerator.generate(
                topology,
                placement,
                supply,
                requirement,
                remoteFreighterBudgetByFaction,
                searchNodeBudget,
                routes);
        return new FrontierReport(
                CURRENT_VERSION,
                exact.placementVersion(),
                exact.supplyProfileVersion(),
                exact.commodityId(),
                exact.searchNodeBudget(),
                exact.searchNodesVisited(),
                exact.status(),
                exact.remoteFreighterBudgetByFaction(),
                exact.options());
    }
}
