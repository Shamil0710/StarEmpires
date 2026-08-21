package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.CombinationReport;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FailureReason;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FrontierStatus;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.SelectedOption;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator;
import com.spacesim.world.Stage20CommodityWholePlacementFrontierGenerator.FrontierReport;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only Stage-20E fixed-corpus measurement of the per-commodity freight-frontier decomposition.
 *
 * <p>The diagnostic replays the unchanged representative v2-candidate production probe. After a seed
 * reaches an accepted faction-start placement it generates one physical whole-placement frontier per
 * essential bootstrap commodity and joins those frontiers with the exact finite-fleet combiner. Route
 * geometry, Stage-18 supply, bootstrap requirements and the derived per-start freight fleet are the
 * existing production authorities.</p>
 *
 * <p>The search-node budget is evidence-only. An incomplete frontier remains unresolved unless known
 * concrete options already suffice for an accepted exact combination. This diagnostic changes no
 * production acceptance or physical generation authority.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20CommodityFrontierCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.commodity-frontier-corpus-diagnostics.v1";
    /** Bounded per-commodity CI evidence budget; never a world-quality threshold. */
    public static final int FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY = 2_000;

    private Stage20CommodityFrontierCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** Mutually exclusive measurement outcome for one fixed seed. */
    public enum SeedStatus {
        /** Existing placement rejected before freight measurement. */ PLACEMENT_REJECTED,
        /** A concrete exact cross-commodity combination fits the finite shared fleets. */ COMBINER_ACCEPTED,
        /** Complete evidence proves commodity or shared-fleet infeasibility. */ COMBINER_INFEASIBLE,
        /** No known fitting combination exists while at least one frontier is incomplete. */ COMBINER_UNRESOLVED
    }

    /** Compact evidence for one commodity frontier on one accepted-placement seed. */
    public record CommodityEvidence(
            String commodityId,
            FrontierStatus status,
            int searchNodesVisited,
            int optionCount,
            List<Map<String, Integer>> nondominatedShipVectors) {
        /** Validates deterministic bounded commodity evidence. */
        public CommodityEvidence {
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(status, "status");
            if (searchNodesVisited < 0 || searchNodesVisited > FRONTIER_SEARCH_NODE_BUDGET_PER_COMMODITY
                    || optionCount < 0) {
                throw new IllegalArgumentException("commodity frontier counts must fit bounded evidence");
            }
            ArrayList<Map<String, Integer>> supplied = new ArrayList<>(
                    Objects.requireNonNull(nondominatedShipVectors, "nondominatedShipVectors"));
            if (supplied.stream().anyMatch(Objects::isNull) || supplied.size() != optionCount) {
                throw new IllegalArgumentException("every frontier option must expose one ship vector");
            }
            ArrayList<Map<String, Integer>> canonical = new ArrayList<>();
            for (Map<String, Integer> vector : supplied) {
                TreeMap<String, Integer> sorted = new TreeMap<>(vector);
                if (sorted.isEmpty() || sorted.values().stream().anyMatch(value -> value == null || value < 0)) {
                    throw new IllegalArgumentException("ship vectors must be non-empty and non-negative");
                }
                canonical.add(Collections.unmodifiableMap(sorted));
            }
            nondominatedShipVectors = List.copyOf(canonical);
        }
    }

    /** Deterministic frontier/combiner evidence for one fixed root seed. */
    public record SeedEvidence(
            long rootSeed,
            PlacementStatus placementStatus,
            SeedStatus status,
            List<CommodityEvidence> commodities,
            Optional<Status> combinerStatus,
            Optional<FailureReason> combinerFailureReason,
            Map<String, Integer> combinedRemoteFreightersByFaction,
            List<SelectedOption> selectedOptions) {
        /** Validates one per-seed measurement without converting unresolved evidence into failure. */
        public SeedEvidence {
            if (rootSeed <= 0L) {
                throw new IllegalArgumentException("rootSeed must be positive");
            }
            Objects.requireNonNull(placementStatus, "placementStatus");
            Objects.requireNonNull(status, "status");

            ArrayList<CommodityEvidence> commodityCopy = new ArrayList<>(
                    Objects.requireNonNull(commodities, "commodities"));
            if (commodityCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("commodity evidence cannot contain nulls");
            }
            commodityCopy.sort(Comparator.comparing(CommodityEvidence::commodityId));
            commodities = List.copyOf(commodityCopy);

            Objects.requireNonNull(combinerStatus, "combinerStatus");
            Objects.requireNonNull(combinerFailureReason, "combinerFailureReason");
            TreeMap<String, Integer> combined = new TreeMap<>(Objects.requireNonNull(
                    combinedRemoteFreightersByFaction,
                    "combinedRemoteFreightersByFaction"));
            if (combined.values().stream().anyMatch(value -> value == null || value < 0)) {
                throw new IllegalArgumentException("combined freight usage must be non-negative");
            }
            combinedRemoteFreightersByFaction = Collections.unmodifiableMap(combined);

            ArrayList<SelectedOption> selectedCopy = new ArrayList<>(
                    Objects.requireNonNull(selectedOptions, "selectedOptions"));
            if (selectedCopy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selected options cannot contain nulls");
            }
            selectedCopy.sort(Comparator.comparing(SelectedOption::commodityId));
            selectedOptions = List.copyOf(selectedCopy);

            if (status == SeedStatus.PLACEMENT_REJECTED) {
                if (placementStatus == PlacementStatus.ACCEPTED || !commodities.isEmpty()
                        || combinerStatus.isPresent() || combinerFailureReason.isPresent()
                        || !combinedRemoteFreightersByFaction.isEmpty() || !selectedOptions.isEmpty()) {
                    throw new IllegalArgumentException("placement-rejected seed cannot expose freight evidence");
                }
            } else {
                if (placementStatus != PlacementStatus.ACCEPTED || commodities.isEmpty() || combinerStatus.isEmpty()) {
                    throw new IllegalArgumentException("accepted placement must expose commodity and combiner evidence");
                }
                Status measured = combinerStatus.orElseThrow();
                switch (status) {
                    case COMBINER_ACCEPTED -> {
                        if (measured != Status.ACCEPTED || combinerFailureReason.isPresent()
                                || combinedRemoteFreightersByFaction.isEmpty() || selectedOptions.isEmpty()) {
                            throw new IllegalArgumentException("accepted seed requires a concrete selected combination");
                        }
                    }
                    case COMBINER_INFEASIBLE -> {
                        if (measured != Status.INFEASIBLE || combinerFailureReason.isEmpty()
                                || !combinedRemoteFreightersByFaction.isEmpty() || !selectedOptions.isEmpty()) {
                            throw new IllegalArgumentException("infeasible seed requires complete failure evidence only");
                        }
                    }
                    case COMBINER_UNRESOLVED -> {
                        if (measured != Status.UNRESOLVED_FRONTIER
                                || !combinerFailureReason.equals(Optional.of(FailureReason.FRONTIER_INCOMPLETE))
                                || !combinedRemoteFreightersByFaction.isEmpty() || !selectedOptions.isEmpty()) {
                            throw new IllegalArgumentException("unresolved seed must retain frontier-incomplete semantics");
                        }
                    }
                    case PLACEMENT_REJECTED -> throw new IllegalStateException("handled by outer branch");
                }
            }
        }
    }

    /** Aggregate fixed-corpus measurement evidence. */
    public record Report(
            String version,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            String freightCapacityRequirementVersion,
            String frontierGeneratorVersion,
            String combinerVersion,
            int perStartFreighterBudget,
            int frontierSearchNodeBudgetPerCommodity,
            int fixedSeedCount,
            int acceptedPlacementSeedCount,
            int combinerAcceptedSeedCount,
            int combinerInfeasibleSeedCount,
            int combinerUnresolvedSeedCount,
            int totalFrontierSearchNodesVisited,
            int maxCommodityFrontierSearchNodesVisited,
            List<SeedEvidence> seeds) {
        /** Validates aggregate status/count consistency. */
        public Report {
            version = requireText(version, "version");
            candidateProfileVersion = requireText(candidateProfileVersion, "candidateProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            freightCapacityRequirementVersion = requireText(
                    freightCapacityRequirementVersion,
                    "freightCapacityRequirementVersion");
            frontierGeneratorVersion = requireText(frontierGeneratorVersion, "frontierGeneratorVersion");
            combinerVersion = requireText(combinerVersion, "combinerVersion");
            if (perStartFreighterBudget <= 0 || frontierSearchNodeBudgetPerCommodity <= 0
                    || fixedSeedCount <= 0 || acceptedPlacementSeedCount < 0
                    || combinerAcceptedSeedCount < 0 || combinerInfeasibleSeedCount < 0
                    || combinerUnresolvedSeedCount < 0 || totalFrontierSearchNodesVisited < 0
                    || maxCommodityFrontierSearchNodesVisited < 0
                    || maxCommodityFrontierSearchNodesVisited > frontierSearchNodeBudgetPerCommodity) {
                throw new IllegalArgumentException("aggregate diagnostic counts or budgets are invalid");
            }
            ArrayList<SeedEvidence> copy = new ArrayList<>(Objects.requireNonNull(seeds, "seeds"));
            if (copy.stream().anyMatch(Objects::isNull) || copy.size() != fixedSeedCount) {
                throw new IllegalArgumentException("report must expose exactly one record per fixed seed");
            }
            seeds = List.copyOf(copy);
            long placementRejected = seeds.stream()
                    .filter(value -> value.status() == SeedStatus.PLACEMENT_REJECTED)
                    .count();
            if (acceptedPlacementSeedCount + placementRejected != fixedSeedCount
                    || combinerAcceptedSeedCount + combinerInfeasibleSeedCount + combinerUnresolvedSeedCount
                    != acceptedPlacementSeedCount) {
                throw new IllegalArgumentException("aggregate status counts must partition the fixed corpus");
            }
        }
    }

    /**
     * Replays the fixed representative corpus using physical commodity frontiers and exact combination.
     *
     * @return deterministic measurement; no accepted-seed target is applied
     */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        if (!capacity.bootstrapRequirementVersion().equals(profile.bootstrapRequirementVersion())) {
            throw new IllegalStateException("freight-capacity authority and candidate profile disagree on bootstrap requirements");
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
                FrontierReport frontier = Stage20CommodityWholePlacementFrontierGenerator.generate(
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
                Stage20CommodityWholePlacementFrontierGenerator.CURRENT_VERSION,
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
     * Serializes compact deterministic CI evidence.
     *
     * @param report measured report
     * @return stable text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(8_192);
        text.append("version=").append(value.version()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("freightCapacityRequirementVersion=").append(value.freightCapacityRequirementVersion()).append('\n');
        text.append("frontierGeneratorVersion=").append(value.frontierGeneratorVersion()).append('\n');
        text.append("combinerVersion=").append(value.combinerVersion()).append('\n');
        text.append("perStartFreighterBudget=").append(value.perStartFreighterBudget()).append('\n');
        text.append("frontierSearchNodeBudgetPerCommodity=")
                .append(value.frontierSearchNodeBudgetPerCommodity()).append('\n');
        text.append("fixedSeedCount=").append(value.fixedSeedCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("combinerAcceptedSeedCount=").append(value.combinerAcceptedSeedCount()).append('\n');
        text.append("combinerInfeasibleSeedCount=").append(value.combinerInfeasibleSeedCount()).append('\n');
        text.append("combinerUnresolvedSeedCount=").append(value.combinerUnresolvedSeedCount()).append('\n');
        text.append("totalFrontierSearchNodesVisited=").append(value.totalFrontierSearchNodesVisited()).append('\n');
        text.append("maxCommodityFrontierSearchNodesVisited=")
                .append(value.maxCommodityFrontierSearchNodesVisited()).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" status=").append(seed.status())
                    .append(" combiner=").append(seed.combinerStatus().map(Enum::name).orElse("NONE"))
                    .append(" failure=").append(seed.combinerFailureReason().map(Enum::name).orElse("NONE"))
                    .append(" combinedShips=").append(new TreeMap<>(seed.combinedRemoteFreightersByFaction()))
                    .append('\n');
            for (CommodityEvidence commodity : seed.commodities()) {
                text.append("  commodity=").append(commodity.commodityId())
                        .append(" frontier=").append(commodity.status())
                        .append(" nodes=").append(commodity.searchNodesVisited())
                        .append(" options=").append(commodity.optionCount())
                        .append(" vectors=").append(canonicalVectors(commodity.nondominatedShipVectors()))
                        .append('\n');
            }
        }
        return text.toString();
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

    private static List<Map<String, Integer>> canonicalVectors(List<Map<String, Integer>> vectors) {
        ArrayList<Map<String, Integer>> result = new ArrayList<>();
        for (Map<String, Integer> vector : vectors) {
            result.add(new TreeMap<>(vector));
        }
        return List.copyOf(result);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }
}
