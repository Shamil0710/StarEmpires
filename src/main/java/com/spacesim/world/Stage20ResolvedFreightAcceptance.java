package com.spacesim.world;

import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20FreightPortfolioAllocator.AllocatedRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Production Stage-20E acceptance primitive for coordinated finite-fleet bootstrap freight.
 *
 * <p>The historical quantitative throughput gate evaluates one supplier at a time and explicitly does
 * not model simultaneous multi-commodity fleet allocation. This primitive consumes an already accepted
 * faction-start placement, resolves one complete-or-explicitly-unresolved whole-placement frontier per
 * essential commodity, and joins those physical options through the exact finite-fleet combiner.</p>
 *
 * <p>No world repair occurs here. A complete empty frontier or complete non-fitting fleet combination
 * is physical infeasibility; an incomplete frontier remains unresolved. A concrete accepted result
 * retains the rich frontiers required for later physical-plan reconstruction and ownership.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20ResolvedFreightAcceptance {
    /** Stable coordinated freight acceptance version. */
    public static final String CURRENT_VERSION = "stage20e.resolved-freight-acceptance.v1";

    private Stage20ResolvedFreightAcceptance() {
        throw new AssertionError("No instances");
    }

    /**
     * Complete coordinated freight acceptance evidence for one accepted faction-start placement.
     *
     * @param version acceptance primitive version
     * @param placementVersion exact accepted placement version
     * @param supplyProfileVersion exact physical supply profile version
     * @param searchNodeBudgetPerCommodity bounded exact-search evidence budget for each commodity
     * @param remoteFreighterBudgetByFaction finite physical freight capacity at every placed start
     * @param commodityFrontiers rich resolved physical frontiers for every essential commodity
     * @param combination exact cross-commodity finite-fleet result
     */
    public record AcceptanceReport(
            String version,
            String placementVersion,
            String supplyProfileVersion,
            int searchNodeBudgetPerCommodity,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            List<FrontierReport> commodityFrontiers,
            CombinationReport combination) {
        /**
         * Validates complete frontier/combiner consistency.
         *
         * @param version acceptance primitive version
         * @param placementVersion exact accepted placement version
         * @param supplyProfileVersion exact physical supply profile version
         * @param searchNodeBudgetPerCommodity bounded exact-search evidence budget for each commodity
         * @param remoteFreighterBudgetByFaction finite physical freight capacity at every placed start
         * @param commodityFrontiers rich resolved physical frontiers for every essential commodity
         * @param combination exact cross-commodity finite-fleet result
         */
        public AcceptanceReport {
            version = requireText(version, "version");
            placementVersion = requireText(placementVersion, "placementVersion");
            supplyProfileVersion = requireText(supplyProfileVersion, "supplyProfileVersion");
            if (searchNodeBudgetPerCommodity <= 0) {
                throw new IllegalArgumentException("searchNodeBudgetPerCommodity must be positive");
            }
            remoteFreighterBudgetByFaction = canonicalPositiveBudgetMap(remoteFreighterBudgetByFaction);
            ArrayList<FrontierReport> copy = new ArrayList<>(
                    Objects.requireNonNull(commodityFrontiers, "commodityFrontiers"));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("resolved freight acceptance requires commodity frontiers");
            }
            copy.sort(Comparator.comparing(FrontierReport::commodityId));
            HashSet<String> commodities = new HashSet<>();
            for (FrontierReport frontier : copy) {
                if (!commodities.add(frontier.commodityId())
                        || !placementVersion.equals(frontier.placementVersion())
                        || !supplyProfileVersion.equals(frontier.supplyProfileVersion())
                        || frontier.searchNodeBudget() != searchNodeBudgetPerCommodity
                        || !frontier.remoteFreighterBudgetByFaction().equals(remoteFreighterBudgetByFaction)) {
                    throw new IllegalArgumentException("commodity frontier authority differs from acceptance report");
                }
            }
            commodityFrontiers = List.copyOf(copy);
            Objects.requireNonNull(combination, "combination");
            if (!combination.remoteFreighterBudgetByFaction().equals(remoteFreighterBudgetByFaction)) {
                throw new IllegalArgumentException("exact combination must use the same finite fleet budgets");
            }
            Set<String> combinedCommodityIds = new HashSet<>();
            combination.selectedOptions().forEach(value -> combinedCommodityIds.add(value.commodityId()));
            if (combination.status() == Stage20CommodityFreightFrontierCombiner.Status.ACCEPTED
                    && !combinedCommodityIds.equals(commodities)) {
                throw new IllegalArgumentException("accepted combination must select every resolved commodity");
            }
        }

        /** @return true only for a concrete exact finite-fleet combination */
        public boolean accepted() {
            return combination.status() == Stage20CommodityFreightFrontierCombiner.Status.ACCEPTED;
        }

        /** @return true only when complete frontier evidence proves physical infeasibility */
        public boolean infeasible() {
            return combination.status() == Stage20CommodityFreightFrontierCombiner.Status.INFEASIBLE;
        }

        /** @return true only when incomplete frontier evidence prevents a decision */
        public boolean unresolved() {
            return combination.status() == Stage20CommodityFreightFrontierCombiner.Status.UNRESOLVED_FRONTIER;
        }

        /** @return total bounded route-prefix nodes visited across all commodity frontiers */
        public int totalSearchNodesVisited() {
            int total = 0;
            for (FrontierReport frontier : commodityFrontiers) {
                total = Math.addExact(total, frontier.searchNodesVisited());
            }
            return total;
        }
    }

    /**
     * Resolves all essential commodity frontiers and exactly combines them under finite start fleets.
     *
     * @param topology authoritative explicit-neighbor topology
     * @param placement accepted non-empty faction-start placement
     * @param supply authoritative physical producer capacities
     * @param essentialCommodities required bootstrap commodities
     * @param remoteFreighterBudgetByFaction finite physical freight capacity for every placed start
     * @param searchNodeBudgetPerCommodity bounded exact-search evidence budget per commodity
     * @param routes authoritative route evaluator parameterized by integer allocated freighters
     * @return deterministic accepted, infeasible or unresolved coordinated freight evidence
     */
    public static AcceptanceReport evaluate(
            GalaxyTopology topology,
            PlacementResult placement,
            SupplyThroughputReport supply,
            List<CommodityRequirement> essentialCommodities,
            Map<String, Integer> remoteFreighterBudgetByFaction,
            int searchNodeBudgetPerCommodity,
            AllocatedRouteEvaluator routes) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        PlacementResult checkedPlacement = Objects.requireNonNull(placement, "placement");
        SupplyThroughputReport checkedSupply = Objects.requireNonNull(supply, "supply");
        Objects.requireNonNull(routes, "routes");
        if (checkedPlacement.status() != PlacementStatus.ACCEPTED || checkedPlacement.assignments().isEmpty()) {
            throw new IllegalArgumentException("resolved freight acceptance requires an accepted non-empty placement");
        }
        if (searchNodeBudgetPerCommodity <= 0) {
            throw new IllegalArgumentException("searchNodeBudgetPerCommodity must be positive");
        }
        for (Assignment assignment : checkedPlacement.assignments()) {
            if (checkedTopology.findSystem(assignment.systemId()).isEmpty()) {
                throw new IllegalArgumentException("placed start is outside supplied topology");
            }
        }

        TreeMap<String, Integer> budgets = canonicalPositiveBudgetMap(remoteFreighterBudgetByFaction);
        Set<String> placedFactions = checkedPlacement.assignments().stream()
                .map(Assignment::stableFactionId)
                .collect(java.util.stream.Collectors.toSet());
        if (!budgets.keySet().equals(placedFactions)) {
            throw new IllegalArgumentException("finite fleet budgets must cover exactly the placed factions");
        }

        ArrayList<CommodityRequirement> requirements = new ArrayList<>(
                Objects.requireNonNull(essentialCommodities, "essentialCommodities"));
        if (requirements.isEmpty() || requirements.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("essentialCommodities must be non-empty and contain no nulls");
        }
        requirements.sort(Comparator.comparing(CommodityRequirement::commodityId));
        HashSet<String> commodityIds = new HashSet<>();
        for (CommodityRequirement requirement : requirements) {
            if (!commodityIds.add(requirement.commodityId())) {
                throw new IllegalArgumentException("duplicate essential commodity: " + requirement.commodityId());
            }
        }

        ArrayList<FrontierReport> frontiers = new ArrayList<>();
        for (CommodityRequirement requirement : requirements) {
            frontiers.add(Stage20CommodityWholePlacementFrontierResolver.resolve(
                    checkedTopology,
                    checkedPlacement,
                    checkedSupply,
                    requirement,
                    budgets,
                    searchNodeBudgetPerCommodity,
                    routes));
        }
        CombinationReport combination = Stage20CommodityFreightFrontierCombiner.combine(
                frontiers.stream().map(FrontierReport::toCombinerFrontier).toList(),
                budgets);
        return new AcceptanceReport(
                CURRENT_VERSION,
                checkedPlacement.version(),
                checkedSupply.profileVersion(),
                searchNodeBudgetPerCommodity,
                budgets,
                frontiers,
                combination);
    }

    private static Map<String, Integer> canonicalPositiveBudgetMap(Map<String, Integer> input) {
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : Objects.requireNonNull(input, "remoteFreighterBudgetByFaction").entrySet()) {
            String faction = WorldFactionIdentityState.normalizeStableId(entry.getKey());
            Integer count = Objects.requireNonNull(entry.getValue(), "remoteFreighterBudgetByFaction count");
            if (count <= 0 || result.putIfAbsent(faction, count) != null) {
                throw new IllegalArgumentException("finite fleet budgets require unique factions and positive counts");
            }
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("finite fleet budgets cannot be empty");
        }
        return Collections.unmodifiableMap(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
