package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.FailureReason;
import com.spacesim.world.Stage20CommodityFreightFrontierCombiner.Status;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20ResolvedFreightAcceptance;
import com.spacesim.world.Stage20ResolvedFreightAcceptance.AcceptanceReport;
import com.spacesim.world.calibration.Stage20BootstrapFreightCapacityRequirementProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Read-only fixed-corpus evidence for the production coordinated-freight acceptance primitive.
 *
 * <p>This diagnostic intentionally reuses the unchanged representative v2-candidate generated-world
 * inputs and the independently derived finite `13`-freighter/start capacity authority. It constructs
 * physical routes through the production-safe explicit-allocation factory and then calls
 * {@link Stage20ResolvedFreightAcceptance}; no ad-hoc frontier/combiner logic remains in the corpus
 * harness itself.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20ResolvedFreightAcceptanceCorpusDiagnostics {
    /** Stable diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.resolved-freight-acceptance-corpus-diagnostics.v1";
    /** Evidence-only bounded exact search budget per commodity; never a world-quality target. */
    public static final int SEARCH_NODE_BUDGET_PER_COMMODITY = 2_000;

    private Stage20ResolvedFreightAcceptanceCorpusDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** One fixed-seed coordinated-freight measurement status. */
    public enum SeedStatus {
        /** Faction-start placement was not accepted, so coordinated freight is not applicable. */ PLACEMENT_NOT_ACCEPTED,
        /** Exact coordinated freight produced a concrete finite-fleet combination. */ FREIGHT_ACCEPTED,
        /** Complete frontier evidence proved physical infeasibility. */ FREIGHT_INFEASIBLE,
        /** Incomplete frontier evidence still prevents a decision. */ FREIGHT_UNRESOLVED
    }

    /**
     * Compact evidence for one fixed representative root seed.
     *
     * @param rootSeed fixed representative root seed
     * @param placementStatus existing faction-start placement status
     * @param status coordinated-freight measurement status
     * @param combinationStatus exact combiner status when placement was accepted
     * @param failureReason exact combiner failure/unresolved cause when present
     * @param totalSearchNodesVisited bounded route-prefix nodes across all commodity frontiers
     */
    public record SeedEvidence(
            long rootSeed,
            PlacementStatus placementStatus,
            SeedStatus status,
            Optional<Status> combinationStatus,
            Optional<FailureReason> failureReason,
            int totalSearchNodesVisited) {
        /**
         * Validates one fixed-seed evidence row.
         *
         * @param rootSeed fixed representative root seed
         * @param placementStatus existing faction-start placement status
         * @param status coordinated-freight measurement status
         * @param combinationStatus exact combiner status when applicable
         * @param failureReason exact failure/unresolved cause when present
         * @param totalSearchNodesVisited bounded search work
         */
        public SeedEvidence {
            if (rootSeed <= 0L || totalSearchNodesVisited < 0) {
                throw new IllegalArgumentException("seed/search counts must be valid");
            }
            Objects.requireNonNull(placementStatus, "placementStatus");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(combinationStatus, "combinationStatus");
            Objects.requireNonNull(failureReason, "failureReason");
            if (status == SeedStatus.PLACEMENT_NOT_ACCEPTED) {
                if (placementStatus == PlacementStatus.ACCEPTED || combinationStatus.isPresent()
                        || failureReason.isPresent() || totalSearchNodesVisited != 0) {
                    throw new IllegalArgumentException("non-accepted placement cannot expose freight evidence");
                }
            } else if (placementStatus != PlacementStatus.ACCEPTED || combinationStatus.isEmpty()) {
                throw new IllegalArgumentException("freight evidence requires accepted placement and combiner status");
            }
        }
    }

    /**
     * Aggregate fixed-corpus evidence without applying any accepted-seed rate target.
     *
     * @param version diagnostic version
     * @param acceptanceVersion coordinated freight acceptance primitive version
     * @param routeFactoryVersion production-safe physical route factory version
     * @param freightCapacityRequirementVersion derived finite capacity authority version
     * @param perStartFreighterCapacity finite physical capacity at every accepted start
     * @param fixedSeedCount number of representative seeds measured
     * @param acceptedPlacementSeedCount seeds reaching accepted faction placement
     * @param freightAcceptedSeedCount accepted-placement seeds with exact fitting freight plan
     * @param freightInfeasibleSeedCount accepted-placement seeds with complete physical infeasibility
     * @param freightUnresolvedSeedCount accepted-placement seeds with incomplete frontier evidence
     * @param totalSearchNodesVisited total bounded route-prefix search work
     * @param seeds deterministic per-seed rows
     */
    public record Report(
            String version,
            String acceptanceVersion,
            String routeFactoryVersion,
            String freightCapacityRequirementVersion,
            int perStartFreighterCapacity,
            int fixedSeedCount,
            int acceptedPlacementSeedCount,
            int freightAcceptedSeedCount,
            int freightInfeasibleSeedCount,
            int freightUnresolvedSeedCount,
            int totalSearchNodesVisited,
            List<SeedEvidence> seeds) {
        /**
         * Validates aggregate fixed-corpus counts.
         *
         * @param version diagnostic version
         * @param acceptanceVersion coordinated freight acceptance primitive version
         * @param routeFactoryVersion production-safe physical route factory version
         * @param freightCapacityRequirementVersion derived finite capacity authority version
         * @param perStartFreighterCapacity finite physical capacity at every accepted start
         * @param fixedSeedCount number of representative seeds measured
         * @param acceptedPlacementSeedCount seeds reaching accepted faction placement
         * @param freightAcceptedSeedCount accepted coordinated freight results
         * @param freightInfeasibleSeedCount complete infeasible coordinated freight results
         * @param freightUnresolvedSeedCount unresolved coordinated freight results
         * @param totalSearchNodesVisited total bounded route-prefix search work
         * @param seeds deterministic per-seed rows
         */
        public Report {
            version = requireText(version, "version");
            acceptanceVersion = requireText(acceptanceVersion, "acceptanceVersion");
            routeFactoryVersion = requireText(routeFactoryVersion, "routeFactoryVersion");
            freightCapacityRequirementVersion = requireText(
                    freightCapacityRequirementVersion, "freightCapacityRequirementVersion");
            if (perStartFreighterCapacity <= 0 || fixedSeedCount <= 0 || acceptedPlacementSeedCount < 0
                    || freightAcceptedSeedCount < 0 || freightInfeasibleSeedCount < 0
                    || freightUnresolvedSeedCount < 0 || totalSearchNodesVisited < 0) {
                throw new IllegalArgumentException("aggregate corpus counts must be valid");
            }
            ArrayList<SeedEvidence> copy = new ArrayList<>(Objects.requireNonNull(seeds, "seeds"));
            if (copy.size() != fixedSeedCount || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("report requires exactly one row per fixed seed");
            }
            seeds = List.copyOf(copy);
            long acceptedPlacementsMeasured = seeds.stream()
                    .filter(value -> value.placementStatus() == PlacementStatus.ACCEPTED)
                    .count();
            if (acceptedPlacementsMeasured != acceptedPlacementSeedCount
                    || freightAcceptedSeedCount + freightInfeasibleSeedCount + freightUnresolvedSeedCount
                    != acceptedPlacementSeedCount) {
                throw new IllegalArgumentException("freight status counts must partition accepted placements");
            }
        }
    }

    /**
     * Replays the fixed representative corpus through the production coordinated-freight primitive.
     *
     * @return deterministic measurement; no pass-rate target is applied
     */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage20BootstrapFreightCapacityRequirementProfile capacity =
                Stage20BootstrapFreightCapacityRequirementProfile.deriveCurrent();
        if (!capacity.bootstrapRequirementVersion().equals(profile.bootstrapRequirementVersion())) {
            throw new IllegalStateException("freight capacity and candidate profile use different bootstrap requirements");
        }
        if (Math.abs(capacity.payloadMassKg() - profile.inputs().transport().fleetProfile().payloadMassKgPerFreighter())
                > 1.0e-9d) {
            throw new IllegalStateException("freight capacity and transport profile use different payload authority");
        }

        int perStartCapacity = capacity.requiredFreighterCountPerFactionStart();
        var stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        ArrayList<SeedEvidence> seeds = new ArrayList<>();
        int acceptedPlacements = 0;
        int accepted = 0;
        int infeasible = 0;
        int unresolved = 0;
        int totalNodes = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            var probe = Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            var placement = probe.placement().orElseThrow();
            if (placement.status() != PlacementStatus.ACCEPTED) {
                seeds.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        SeedStatus.PLACEMENT_NOT_ACCEPTED,
                        Optional.empty(),
                        Optional.empty(),
                        0));
                continue;
            }
            acceptedPlacements++;
            var topology = probe.topology().requireAcceptedTopology();
            var routes = Stage20PhysicalFreightRouteEvaluatorFactory.create(
                    topology,
                    probe.jumpEdges().orElseThrow(),
                    probe.localLayouts().orElseThrow(),
                    stations,
                    profile.inputs().transport(),
                    perStartCapacity);
            TreeMap<String, Integer> budgets = new TreeMap<>();
            for (Assignment assignment : placement.assignments()) {
                budgets.put(assignment.stableFactionId(), perStartCapacity);
            }
            AcceptanceReport freight = Stage20ResolvedFreightAcceptance.evaluate(
                    topology,
                    placement,
                    probe.supplyThroughput().orElseThrow(),
                    profile.inputs().acceptance().bootstrapRequirements().essentialCommodities(),
                    budgets,
                    SEARCH_NODE_BUDGET_PER_COMMODITY,
                    routes::assessWithAllocatedFreighters);
            totalNodes = Math.addExact(totalNodes, freight.totalSearchNodesVisited());

            SeedStatus status;
            if (freight.accepted()) {
                accepted++;
                status = SeedStatus.FREIGHT_ACCEPTED;
            } else if (freight.infeasible()) {
                infeasible++;
                status = SeedStatus.FREIGHT_INFEASIBLE;
            } else {
                unresolved++;
                status = SeedStatus.FREIGHT_UNRESOLVED;
            }
            seeds.add(new SeedEvidence(
                    rootSeed,
                    placement.status(),
                    status,
                    Optional.of(freight.combination().status()),
                    freight.combination().failureReason(),
                    freight.totalSearchNodesVisited()));
        }

        return new Report(
                CURRENT_VERSION,
                Stage20ResolvedFreightAcceptance.CURRENT_VERSION,
                Stage20PhysicalFreightRouteEvaluatorFactory.CURRENT_VERSION,
                capacity.version(),
                perStartCapacity,
                seeds.size(),
                acceptedPlacements,
                accepted,
                infeasible,
                unresolved,
                totalNodes,
                seeds);
    }

    /**
     * Serializes compact deterministic CI evidence.
     *
     * @param report measured fixed-corpus report
     * @return stable text ending with newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(4_096);
        text.append("version=").append(value.version()).append('\n');
        text.append("acceptanceVersion=").append(value.acceptanceVersion()).append('\n');
        text.append("routeFactoryVersion=").append(value.routeFactoryVersion()).append('\n');
        text.append("freightCapacityRequirementVersion=").append(value.freightCapacityRequirementVersion()).append('\n');
        text.append("perStartFreighterCapacity=").append(value.perStartFreighterCapacity()).append('\n');
        text.append("fixedSeedCount=").append(value.fixedSeedCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("freightAcceptedSeedCount=").append(value.freightAcceptedSeedCount()).append('\n');
        text.append("freightInfeasibleSeedCount=").append(value.freightInfeasibleSeedCount()).append('\n');
        text.append("freightUnresolvedSeedCount=").append(value.freightUnresolvedSeedCount()).append('\n');
        text.append("totalSearchNodesVisited=").append(value.totalSearchNodesVisited()).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" freight=").append(seed.status())
                    .append(" combination=").append(seed.combinationStatus().map(Enum::name).orElse("NONE"))
                    .append(" failure=").append(seed.failureReason().map(Enum::name).orElse("NONE"))
                    .append(" nodes=").append(seed.totalSearchNodesVisited())
                    .append('\n');
        }
        return text.toString();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
