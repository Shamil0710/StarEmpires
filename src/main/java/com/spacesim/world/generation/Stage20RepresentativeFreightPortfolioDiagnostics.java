package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20EconomicBootstrapValidator.CommodityRequirement;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20JumpEdgeCatalog;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.EndpointCycleProfile;
import com.spacesim.world.Stage20PhysicalGalacticRoutePlanner;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyKey;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.generation.Stage20GeneratedWorldProductionProbe.PhysicalTransportAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Read-only Stage-20E diagnostics for the current single-supplier final throughput gate.
 *
 * <p>The diagnostic replays the v2 bootstrap-service-cadence candidate from PR #273 and asks a
 * narrower question: if the already configured representative freighters are explicitly divided
 * among several physical supplier routes, how many ships are minimally required to satisfy each
 * essential commodity? It never creates freighters, resources, routes or throughput. Every marginal
 * route capacity is obtained from {@link Stage20PhysicalFreightRouteEvaluator} through its bounded
 * allocation API, so cycle physics is not duplicated here.</p>
 *
 * <p>Two fleet bounds are reported independently. A per-start bound shares the configured fleet
 * between all essential commodities of one start. A whole-placement bound shares that same fleet
 * across every assigned faction start in the seed. Neither bound is promoted to production policy;
 * both are evidence for deciding what explicit fleet-allocation authority Stage 20E still needs.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20RepresentativeFreightPortfolioDiagnostics {
    /** Current deterministic diagnostic version. */
    public static final String CURRENT_VERSION = "stage20e.representative-freight-portfolio-diagnostics.v1";
    private static final double EPSILON = 1e-9d;

    private Stage20RepresentativeFreightPortfolioDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** Outcome for one placed start and one essential commodity. */
    public enum RequirementStatus {
        /** The existing final gate already has one supplier that can meet the full rate. */ SINGLE_SUPPLIER_SUFFICIENT,
        /** No single supplier suffices, but a bounded multi-supplier allocation can meet the rate. */ PORTFOLIO_SUFFICIENT,
        /** Time-admitted physical producer capacity is insufficient even before fleet allocation. */ INSUFFICIENT_ADMITTED_SUPPLY,
        /** Admitted supply exists, but the configured freight fleet cannot move enough of it. */ INSUFFICIENT_CONFIGURED_FLEET
    }

    /** One deterministic marginal contribution from allocating one additional freighter to a route. */
    public record MarginalAllocation(
            StarSystemId supplierSystemId,
            int supplierFreighterOrdinal,
            double marginalDeliveredKgPerSecond) {
        public MarginalAllocation {
            Objects.requireNonNull(supplierSystemId, "supplierSystemId");
            if (supplierFreighterOrdinal <= 0) {
                throw new IllegalArgumentException("supplierFreighterOrdinal must be positive");
            }
            requirePositiveFinite(marginalDeliveredKgPerSecond, "marginalDeliveredKgPerSecond");
        }
    }

    /** One placed-start/commodity allocation result. */
    public record RequirementEvidence(
            long rootSeed,
            StarSystemId startSystemId,
            String commodityId,
            double requiredKgPerSecond,
            double maxSupplierRouteTimeS,
            int configuredFreighterCount,
            int admittedSupplierCount,
            double admittedProducerCapacityKgPerSecond,
            double bestSingleSupplierDeliveredKgPerSecond,
            int minimumFreightersRequired,
            int selectedSupplierCount,
            double selectedPortfolioCapacityKgPerSecond,
            RequirementStatus status,
            List<MarginalAllocation> selectedMarginals) {
        public RequirementEvidence {
            Objects.requireNonNull(startSystemId, "startSystemId");
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requirePositiveFinite(maxSupplierRouteTimeS, "maxSupplierRouteTimeS");
            if (configuredFreighterCount <= 0 || admittedSupplierCount < 0 || minimumFreightersRequired < 0
                    || selectedSupplierCount < 0) {
                throw new IllegalArgumentException("freighter/supplier counts must be valid");
            }
            requireNonNegativeFinite(admittedProducerCapacityKgPerSecond, "admittedProducerCapacityKgPerSecond");
            requireNonNegativeFinite(bestSingleSupplierDeliveredKgPerSecond, "bestSingleSupplierDeliveredKgPerSecond");
            requireNonNegativeFinite(selectedPortfolioCapacityKgPerSecond, "selectedPortfolioCapacityKgPerSecond");
            Objects.requireNonNull(status, "status");
            selectedMarginals = List.copyOf(Objects.requireNonNull(selectedMarginals, "selectedMarginals"));
            if (selectedMarginals.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("selectedMarginals cannot contain null");
            }
            if ((status == RequirementStatus.SINGLE_SUPPLIER_SUFFICIENT
                    || status == RequirementStatus.PORTFOLIO_SUFFICIENT)
                    && (minimumFreightersRequired <= 0
                    || minimumFreightersRequired > configuredFreighterCount
                    || selectedPortfolioCapacityKgPerSecond + EPSILON < requiredKgPerSecond)) {
                throw new IllegalArgumentException("sufficient requirement evidence must have a bounded allocation");
            }
            if ((status == RequirementStatus.INSUFFICIENT_ADMITTED_SUPPLY
                    || status == RequirementStatus.INSUFFICIENT_CONFIGURED_FLEET)
                    && minimumFreightersRequired != 0) {
                throw new IllegalArgumentException("failed requirement evidence cannot claim a minimum allocation");
            }
        }
    }

    /** Aggregate evidence for one accepted placed start. */
    public record StartEvidence(
            long rootSeed,
            StarSystemId startSystemId,
            int configuredFreighterCount,
            int minimumFreightersAcrossEssentialCommodities,
            boolean allRequirementsPortfolioSufficient,
            boolean fitsOneSharedStartFleet,
            List<RequirementEvidence> requirements) {
        public StartEvidence {
            Objects.requireNonNull(startSystemId, "startSystemId");
            if (configuredFreighterCount <= 0 || minimumFreightersAcrossEssentialCommodities < 0) {
                throw new IllegalArgumentException("start fleet counts must be valid");
            }
            requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
            if (requirements.isEmpty() || requirements.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("start requirements must be non-empty");
            }
            if (fitsOneSharedStartFleet != (allRequirementsPortfolioSufficient
                    && minimumFreightersAcrossEssentialCommodities <= configuredFreighterCount)) {
                throw new IllegalArgumentException("fitsOneSharedStartFleet is inconsistent");
            }
        }
    }

    /** Fixed-seed portfolio evidence after faction-start placement. */
    public record SeedEvidence(
            long rootSeed,
            PlacementStatus placementStatus,
            int assignedStartCount,
            int configuredFreighterCount,
            int minimumFreightersAcrossPlacedStarts,
            boolean everyPlacedStartFitsOwnFleet,
            boolean wholePlacementFitsOneGlobalFleet,
            List<StartEvidence> starts) {
        public SeedEvidence {
            Objects.requireNonNull(placementStatus, "placementStatus");
            if (assignedStartCount < 0 || configuredFreighterCount <= 0 || minimumFreightersAcrossPlacedStarts < 0) {
                throw new IllegalArgumentException("seed fleet/start counts must be valid");
            }
            starts = List.copyOf(Objects.requireNonNull(starts, "starts"));
            if (assignedStartCount != starts.size()) {
                throw new IllegalArgumentException("assignedStartCount must match start evidence");
            }
            if (placementStatus != PlacementStatus.ACCEPTED && !starts.isEmpty()) {
                throw new IllegalArgumentException("non-accepted placement cannot expose assigned starts");
            }
            boolean allOwn = !starts.isEmpty() && starts.stream().allMatch(StartEvidence::fitsOneSharedStartFleet);
            if (everyPlacedStartFitsOwnFleet != allOwn) {
                throw new IllegalArgumentException("everyPlacedStartFitsOwnFleet is inconsistent");
            }
            boolean global = allOwn && minimumFreightersAcrossPlacedStarts <= configuredFreighterCount;
            if (wholePlacementFitsOneGlobalFleet != global) {
                throw new IllegalArgumentException("wholePlacementFitsOneGlobalFleet is inconsistent");
            }
        }
    }

    /** Aggregate fixed-corpus diagnostic report. */
    public record Report(
            String version,
            String candidateProfileVersion,
            String bootstrapRequirementVersion,
            int configuredFreighterCount,
            int acceptedPlacementSeedCount,
            int allPlacedStartsOwnFleetSeedCount,
            int wholePlacementSingleGlobalFleetSeedCount,
            Map<String, Integer> requirementStatusCounts,
            List<SeedEvidence> seeds) {
        public Report {
            version = requireText(version, "version");
            candidateProfileVersion = requireText(candidateProfileVersion, "candidateProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion, "bootstrapRequirementVersion");
            if (configuredFreighterCount <= 0 || acceptedPlacementSeedCount < 0
                    || allPlacedStartsOwnFleetSeedCount < 0 || wholePlacementSingleGlobalFleetSeedCount < 0) {
                throw new IllegalArgumentException("report counts must be valid");
            }
            requirementStatusCounts = immutableCounts(requirementStatusCounts);
            seeds = List.copyOf(Objects.requireNonNull(seeds, "seeds"));
            if (seeds.isEmpty() || seeds.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("seeds must be non-empty");
            }
        }
    }

    /** Replays the fixed corpus through the v2 cadence candidate and measures shared-fleet demand. */
    public static Report evaluateCurrent() {
        var profile = Stage20RepresentativeGeneratedWorldProbeProfileV2.deriveCurrent();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        int configuredFreighters = profile.inputs().transport().fleetProfile().activeFreighterCount();
        ArrayList<SeedEvidence> seedEvidence = new ArrayList<>();
        TreeMap<String, Integer> statusCounts = new TreeMap<>();
        int acceptedPlacementSeeds = 0;
        int allOwnFleetSeeds = 0;
        int oneGlobalFleetSeeds = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            var probe = Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            PlacementResult placement = probe.placement().orElseThrow();
            if (placement.status() != PlacementStatus.ACCEPTED) {
                seedEvidence.add(new SeedEvidence(
                        rootSeed,
                        placement.status(),
                        0,
                        configuredFreighters,
                        0,
                        false,
                        false,
                        List.of()));
                continue;
            }
            acceptedPlacementSeeds++;
            GalaxyTopology topology = probe.topology().requireAcceptedTopology();
            Stage20PhysicalFreightRouteEvaluator routes = physicalRoutes(
                    topology,
                    probe.jumpEdges().orElseThrow(),
                    probe.localLayouts().orElseThrow(),
                    stations,
                    profile.inputs().transport());
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();

            ArrayList<StartEvidence> starts = new ArrayList<>();
            for (var assignment : placement.assignments()) {
                ArrayList<RequirementEvidence> requirements = new ArrayList<>();
                int startMinimum = 0;
                boolean allRequirements = true;
                for (CommodityRequirement requirement :
                        profile.inputs().acceptance().bootstrapRequirements().essentialCommodities()) {
                    RequirementEvidence evidence = evaluateRequirement(
                            rootSeed,
                            assignment.systemId(),
                            requirement,
                            topology,
                            supply,
                            routes,
                            configuredFreighters);
                    requirements.add(evidence);
                    statusCounts.merge(evidence.status().name(), 1, Math::addExact);
                    if (evidence.minimumFreightersRequired() == 0) {
                        allRequirements = false;
                    } else {
                        startMinimum = Math.addExact(startMinimum, evidence.minimumFreightersRequired());
                    }
                }
                requirements.sort(Comparator.comparing(RequirementEvidence::commodityId));
                boolean fitsStart = allRequirements && startMinimum <= configuredFreighters;
                starts.add(new StartEvidence(
                        rootSeed,
                        assignment.systemId(),
                        configuredFreighters,
                        startMinimum,
                        allRequirements,
                        fitsStart,
                        requirements));
            }
            starts.sort(Comparator.comparing(StartEvidence::startSystemId));
            int totalMinimum = starts.stream()
                    .mapToInt(StartEvidence::minimumFreightersAcrossEssentialCommodities)
                    .sum();
            boolean allOwn = starts.stream().allMatch(StartEvidence::fitsOneSharedStartFleet);
            boolean oneGlobal = allOwn && totalMinimum <= configuredFreighters;
            if (allOwn) {
                allOwnFleetSeeds++;
            }
            if (oneGlobal) {
                oneGlobalFleetSeeds++;
            }
            seedEvidence.add(new SeedEvidence(
                    rootSeed,
                    placement.status(),
                    starts.size(),
                    configuredFreighters,
                    totalMinimum,
                    allOwn,
                    oneGlobal,
                    starts));
        }

        return new Report(
                CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                configuredFreighters,
                acceptedPlacementSeeds,
                allOwnFleetSeeds,
                oneGlobalFleetSeeds,
                statusCounts,
                seedEvidence);
    }

    /** Serializes compact deterministic evidence for exact-head CI inspection. */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(8_192);
        text.append("version=").append(value.version()).append('\n');
        text.append("candidateProfileVersion=").append(value.candidateProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("configuredFreighterCount=").append(value.configuredFreighterCount()).append('\n');
        text.append("acceptedPlacementSeedCount=").append(value.acceptedPlacementSeedCount()).append('\n');
        text.append("allPlacedStartsOwnFleetSeedCount=").append(value.allPlacedStartsOwnFleetSeedCount()).append('\n');
        text.append("wholePlacementSingleGlobalFleetSeedCount=")
                .append(value.wholePlacementSingleGlobalFleetSeedCount()).append('\n');
        text.append("requirementStatusCounts=").append(new TreeMap<>(value.requirementStatusCounts())).append('\n');
        for (SeedEvidence seed : value.seeds()) {
            text.append("seed=").append(seed.rootSeed())
                    .append(" placement=").append(seed.placementStatus())
                    .append(" starts=").append(seed.assignedStartCount())
                    .append(" minFreightersAllStarts=").append(seed.minimumFreightersAcrossPlacedStarts())
                    .append(" eachStartFits8=").append(seed.everyPlacedStartFitsOwnFleet())
                    .append(" allStartsFitOne8=").append(seed.wholePlacementFitsOneGlobalFleet())
                    .append('\n');
            for (StartEvidence start : seed.starts()) {
                text.append("  start=").append(start.startSystemId().value())
                        .append(" minFreighters=").append(start.minimumFreightersAcrossEssentialCommodities())
                        .append(" fits8=").append(start.fitsOneSharedStartFleet())
                        .append('\n');
                for (RequirementEvidence requirement : start.requirements()) {
                    text.append("    commodity=").append(requirement.commodityId())
                            .append(" status=").append(requirement.status())
                            .append(" required=").append(requirement.requiredKgPerSecond())
                            .append(" bestSingle=").append(requirement.bestSingleSupplierDeliveredKgPerSecond())
                            .append(" admittedSupply=").append(requirement.admittedProducerCapacityKgPerSecond())
                            .append(" admittedSuppliers=").append(requirement.admittedSupplierCount())
                            .append(" minFreighters=").append(requirement.minimumFreightersRequired())
                            .append(" selectedSuppliers=").append(requirement.selectedSupplierCount())
                            .append(" portfolioCapacity=").append(requirement.selectedPortfolioCapacityKgPerSecond())
                            .append('\n');
                }
            }
        }
        return text.toString();
    }

    private static RequirementEvidence evaluateRequirement(
            long rootSeed,
            StarSystemId start,
            CommodityRequirement requirement,
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            Stage20PhysicalFreightRouteEvaluator routes,
            int configuredFreighters) {
        ArrayList<MarginalCandidate> marginals = new ArrayList<>();
        int admittedSuppliers = 0;
        double admittedSupply = 0d;
        double bestSingle = 0d;

        ArrayList<Map.Entry<SupplyKey, Double>> producers = supply.capacityKgPerSecondBySupply().entrySet().stream()
                .filter(entry -> entry.getKey().commodityId().equals(requirement.commodityId()))
                .sorted(Map.Entry.comparingByKey())
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        for (Map.Entry<SupplyKey, Double> producer : producers) {
            Optional<RouteAssessment> fullMaybe = routes.assess(producer.getKey().systemId(), start);
            if (fullMaybe.isEmpty()) {
                continue;
            }
            RouteAssessment full = validateRoute(
                    topology, producer.getKey().systemId(), start, fullMaybe.orElseThrow());
            if (full.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                continue;
            }
            admittedSuppliers++;
            admittedSupply += producer.getValue();
            double fullDelivered = Math.min(producer.getValue(), full.sustainableCargoThroughputKgPerSecond());
            bestSingle = Math.max(bestSingle, fullDelivered);

            double previous = 0d;
            for (int allocation = 1; allocation <= configuredFreighters; allocation++) {
                RouteAssessment route = routes.assessWithAllocatedFreighters(
                        producer.getKey().systemId(), start, allocation).orElseThrow();
                double delivered = Math.min(producer.getValue(), route.sustainableCargoThroughputKgPerSecond());
                double marginal = delivered - previous;
                if (marginal > EPSILON) {
                    marginals.add(new MarginalCandidate(
                            producer.getKey().systemId(), allocation, marginal));
                }
                previous = delivered;
            }
        }

        if (admittedSupply + EPSILON < requirement.minSupplierThroughputKgPerSecond()) {
            return failedRequirement(
                    rootSeed, start, requirement, configuredFreighters, admittedSuppliers,
                    admittedSupply, bestSingle, RequirementStatus.INSUFFICIENT_ADMITTED_SUPPLY);
        }

        marginals.sort(Comparator.comparingDouble(MarginalCandidate::marginalKgPerSecond).reversed()
                .thenComparing(MarginalCandidate::supplierSystemId)
                .thenComparingInt(MarginalCandidate::supplierFreighterOrdinal));
        ArrayList<MarginalAllocation> selected = new ArrayList<>();
        double portfolio = 0d;
        for (MarginalCandidate marginal : marginals) {
            if (selected.size() >= configuredFreighters
                    || portfolio + EPSILON >= requirement.minSupplierThroughputKgPerSecond()) {
                break;
            }
            selected.add(new MarginalAllocation(
                    marginal.supplierSystemId(),
                    marginal.supplierFreighterOrdinal(),
                    marginal.marginalKgPerSecond()));
            portfolio += marginal.marginalKgPerSecond();
        }
        if (portfolio + EPSILON < requirement.minSupplierThroughputKgPerSecond()) {
            return failedRequirement(
                    rootSeed, start, requirement, configuredFreighters, admittedSuppliers,
                    admittedSupply, bestSingle, RequirementStatus.INSUFFICIENT_CONFIGURED_FLEET);
        }
        Set<StarSystemId> suppliers = new HashSet<>();
        selected.forEach(value -> suppliers.add(value.supplierSystemId()));
        RequirementStatus status = bestSingle + EPSILON >= requirement.minSupplierThroughputKgPerSecond()
                ? RequirementStatus.SINGLE_SUPPLIER_SUFFICIENT
                : RequirementStatus.PORTFOLIO_SUFFICIENT;
        return new RequirementEvidence(
                rootSeed,
                start,
                requirement.commodityId(),
                requirement.minSupplierThroughputKgPerSecond(),
                requirement.maxSupplierRouteTimeS(),
                configuredFreighters,
                admittedSuppliers,
                admittedSupply,
                bestSingle,
                selected.size(),
                suppliers.size(),
                portfolio,
                status,
                selected);
    }

    private static RequirementEvidence failedRequirement(
            long rootSeed,
            StarSystemId start,
            CommodityRequirement requirement,
            int configuredFreighters,
            int admittedSuppliers,
            double admittedSupply,
            double bestSingle,
            RequirementStatus status) {
        return new RequirementEvidence(
                rootSeed,
                start,
                requirement.commodityId(),
                requirement.minSupplierThroughputKgPerSecond(),
                requirement.maxSupplierRouteTimeS(),
                configuredFreighters,
                admittedSuppliers,
                admittedSupply,
                bestSingle,
                0,
                0,
                0d,
                status,
                List.of());
    }

    private static Stage20PhysicalFreightRouteEvaluator physicalRoutes(
            GalaxyTopology topology,
            Stage20JumpEdgeCatalog jumpEdges,
            List<Stage20LocalInfrastructureLayout> layouts,
            Stage18StationInfrastructureCatalog stations,
            PhysicalTransportAuthority transport) {
        TreeMap<StarSystemId, EndpointAuthority> endpointBySystem = new TreeMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            StationArchetypeDefinition hub = stations.findArchetype(
                    layout.placement(layout.majorHubId()).stationArchetypeId().orElseThrow());
            if (hub == null) {
                throw new IllegalArgumentException("generated hub references unknown station archetype");
            }
            double maximumLocalTravelSeconds = layout.connections().stream()
                    .filter(value -> !touchesJumpAnchor(layout, value))
                    .map(CalibratedConnection::logisticsConsequences)
                    .mapToDouble(value -> value.civilianRoutineTravelTimeMaxS())
                    .max()
                    .orElse(0d);
            double jumpAccessSeconds = layout.connections().stream()
                    .filter(value -> touchesJumpAnchor(layout, value))
                    .map(CalibratedConnection::logisticsConsequences)
                    .mapToDouble(value -> value.civilianRoutineTravelTimeMaxS())
                    .max()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "diagnostic layout lacks jump-arrival physical access: " + layout.systemId()));
            endpointBySystem.put(
                    layout.systemId(),
                    new EndpointAuthority(
                            maximumLocalTravelSeconds,
                            jumpAccessSeconds,
                            hub.transferMassRateKgPerSecond(),
                            "stage20c-layout:" + layout.routeCalibrationVersion()));
        }

        Stage20PhysicalGalacticRoutePlanner loaded = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.loadedOutboundPlan(), jumpEdges);
        Stage20PhysicalGalacticRoutePlanner returned = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.returnPlan(), jumpEdges);
        return new Stage20PhysicalFreightRouteEvaluator(
                loaded,
                returned,
                transport.fleetProfile(),
                (origin, destination) -> {
                    EndpointAuthority from = endpointBySystem.get(origin);
                    EndpointAuthority to = endpointBySystem.get(destination);
                    if (from == null || to == null) {
                        return Optional.empty();
                    }
                    double outboundLocal;
                    double returnLocal;
                    if (origin.equals(destination)) {
                        outboundLocal = from.maximumLocalTravelSeconds();
                        returnLocal = from.maximumLocalTravelSeconds();
                    } else {
                        outboundLocal = from.maximumLocalTravelSeconds()
                                + from.jumpAccessSeconds()
                                + to.jumpAccessSeconds();
                        returnLocal = to.maximumLocalTravelSeconds()
                                + to.jumpAccessSeconds()
                                + from.jumpAccessSeconds();
                    }
                    return Optional.of(new EndpointCycleProfile(
                            outboundLocal,
                            returnLocal,
                            from.transferRateKgPerSecond(),
                            to.transferRateKgPerSecond(),
                            from.sourceEvidenceId() + "+" + to.sourceEvidenceId()));
                });
    }

    private static boolean touchesJumpAnchor(
            Stage20LocalInfrastructureLayout layout,
            CalibratedConnection connection) {
        return layout.placement(connection.fromId()).kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR
                || layout.placement(connection.toId()).kind() == PlacementKind.JUMP_ARRIVAL_ANCHOR;
    }

    private static RouteAssessment validateRoute(
            GalaxyTopology topology,
            StarSystemId origin,
            StarSystemId destination,
            RouteAssessment route) {
        List<StarSystemId> path = route.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("diagnostic route endpoints do not match request");
        }
        if (origin.equals(destination)) {
            if (path.size() != 1) {
                throw new IllegalArgumentException("same-system diagnostic route must contain one system");
            }
            return route;
        }
        for (int index = 0; index < path.size() - 1; index++) {
            if (!topology.neighbors(path.get(index)).contains(path.get(index + 1))) {
                throw new IllegalArgumentException("diagnostic route contains non-neighbor shortcut");
            }
        }
        return route;
    }

    private static Map<String, Integer> immutableCounts(Map<String, Integer> source) {
        Objects.requireNonNull(source, "source");
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), "status key");
            Integer value = Objects.requireNonNull(entry.getValue(), "status count");
            if (value <= 0) {
                throw new IllegalArgumentException("status counts must be positive");
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
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

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private record MarginalCandidate(
            StarSystemId supplierSystemId,
            int supplierFreighterOrdinal,
            double marginalKgPerSecond) {
    }

    private record EndpointAuthority(
            double maximumLocalTravelSeconds,
            double jumpAccessSeconds,
            double transferRateKgPerSecond,
            String sourceEvidenceId) {
    }
}
