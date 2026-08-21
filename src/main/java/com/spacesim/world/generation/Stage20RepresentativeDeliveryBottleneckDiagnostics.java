package com.spacesim.world.generation;

import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20EconomicBootstrapValidator.RouteAssessment;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.TreeMap;

/**
 * Read-only Stage-20E diagnostics that split candidate essential-supply deficits into global
 * scarcity, route availability, storage-backed route-time and physical freight-throughput layers.
 *
 * <p>The diagnostics do not create a second movement formula. They reuse
 * {@link Stage20PhysicalFreightRouteEvaluator}, the production probe's generated layouts, physical
 * jump-edge state and exact transport authority. The small endpoint-authority reconstruction is
 * intentionally guarded by an exact parity check: recomputed faction-start evaluations must equal
 * the evaluations already emitted by {@link Stage20GeneratedWorldProductionProbe} for every system
 * in every fixed seed. Any future drift fails diagnostics instead of silently reporting a different
 * route model.</p>
 *
 * <p>All aggregate supplier figures remain diagnostic upper bounds. In particular, the current
 * representative {@code FreightFleetProfile} describes route-level allocated freighters; summing
 * multiple route capacities does not itself authorize simultaneous shared-fleet allocation.</p>
 */
public final class Stage20RepresentativeDeliveryBottleneckDiagnostics {
    /** Current deterministic diagnostics version. */
    public static final String CURRENT_VERSION = "stage20e.representative-delivery-bottleneck-diagnostics.v1";
    private static final double EPSILON = 1.0e-9d;

    private Stage20RepresentativeDeliveryBottleneckDiagnostics() {
        throw new AssertionError("No instances");
    }

    /** Ordered causal classification for one candidate/commodity pair. */
    public enum BottleneckClass {
        /** Total resolved supply in the generated world is below the sustained requirement. */
        GLOBAL_SCARCITY,
        /** Enough global supply exists, but physically executable routes expose too little producer capacity. */
        PHYSICAL_ROUTE_UNAVAILABLE,
        /** Executable routes exist, but the storage-backed supplier-time horizon excludes too much capacity. */
        ROUTE_TIME_BOTTLENECK,
        /** Time-admitted producer capacity is sufficient, but route-cycle throughput is insufficient. */
        FREIGHT_THROUGHPUT_BOTTLENECK,
        /** The current diagnostic aggregate can physically deliver the required rate to this candidate. */
        REACHABLE_SUFFICIENT
    }

    /**
     * Delivery evidence for one candidate and one essential commodity.
     *
     * @param rootSeed exact generated root seed
     * @param candidateSystemId evaluated consumer/start candidate
     * @param commodityId authoritative Stage-18 commodity ID
     * @param requiredKgPerSecond provenance-backed sustained service requirement
     * @param maxSupplierRouteTimeS storage-backed maximum supplier delivery time
     * @param globalResolvedSupplyKgPerSecond all resolved producer capacity in the generated world
     * @param physicallyRoutableProducerSupplyKgPerSecond producer capacity with an executable physical route
     * @param timeAdmittedProducerSupplyKgPerSecond producer capacity whose route also satisfies the time horizon
     * @param aggregateDeliveredUpperBoundKgPerSecond sum of min(producer capacity, route-cycle capacity) for admitted routes
     * @param totalProducerCount resolved producer-system count
     * @param physicallyRoutableProducerCount producers with an executable route
     * @param timeAdmittedProducerCount producers within the route-time horizon
     * @param freightLimitedProducerCount admitted producers whose route throughput is below producer capacity
     * @param bottleneck causal classification from the ordered physical checks
     */
    public record CandidateCommodityEvidence(
            long rootSeed,
            StarSystemId candidateSystemId,
            String commodityId,
            double requiredKgPerSecond,
            double maxSupplierRouteTimeS,
            double globalResolvedSupplyKgPerSecond,
            double physicallyRoutableProducerSupplyKgPerSecond,
            double timeAdmittedProducerSupplyKgPerSecond,
            double aggregateDeliveredUpperBoundKgPerSecond,
            int totalProducerCount,
            int physicallyRoutableProducerCount,
            int timeAdmittedProducerCount,
            int freightLimitedProducerCount,
            BottleneckClass bottleneck) {
        /**
         * Validates one immutable candidate/commodity delivery row.
         *
         * @param rootSeed exact generated root seed
         * @param candidateSystemId evaluated candidate system
         * @param commodityId authoritative commodity ID
         * @param requiredKgPerSecond sustained service requirement
         * @param maxSupplierRouteTimeS maximum supplier route time
         * @param globalResolvedSupplyKgPerSecond global resolved producer capacity
         * @param physicallyRoutableProducerSupplyKgPerSecond producer capacity with executable routes
         * @param timeAdmittedProducerSupplyKgPerSecond producer capacity satisfying the route-time horizon
         * @param aggregateDeliveredUpperBoundKgPerSecond admitted aggregate route-delivery upper bound
         * @param totalProducerCount total resolved producers
         * @param physicallyRoutableProducerCount producers with executable routes
         * @param timeAdmittedProducerCount producers satisfying route time
         * @param freightLimitedProducerCount admitted producers limited by route-cycle throughput
         * @param bottleneck ordered causal classification
         */
        public CandidateCommodityEvidence {
            Objects.requireNonNull(candidateSystemId, "candidateSystemId");
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(requiredKgPerSecond, "requiredKgPerSecond");
            requirePositiveFinite(maxSupplierRouteTimeS, "maxSupplierRouteTimeS");
            requireNonNegativeFinite(globalResolvedSupplyKgPerSecond, "globalResolvedSupplyKgPerSecond");
            requireNonNegativeFinite(physicallyRoutableProducerSupplyKgPerSecond,
                    "physicallyRoutableProducerSupplyKgPerSecond");
            requireNonNegativeFinite(timeAdmittedProducerSupplyKgPerSecond,
                    "timeAdmittedProducerSupplyKgPerSecond");
            requireNonNegativeFinite(aggregateDeliveredUpperBoundKgPerSecond,
                    "aggregateDeliveredUpperBoundKgPerSecond");
            requireNonNegative(totalProducerCount, "totalProducerCount");
            requireNonNegative(physicallyRoutableProducerCount, "physicallyRoutableProducerCount");
            requireNonNegative(timeAdmittedProducerCount, "timeAdmittedProducerCount");
            requireNonNegative(freightLimitedProducerCount, "freightLimitedProducerCount");
            Objects.requireNonNull(bottleneck, "bottleneck");
            if (physicallyRoutableProducerCount > totalProducerCount
                    || timeAdmittedProducerCount > physicallyRoutableProducerCount
                    || freightLimitedProducerCount > timeAdmittedProducerCount) {
                throw new IllegalArgumentException("delivery producer counts are inconsistent");
            }
            if (physicallyRoutableProducerSupplyKgPerSecond > globalResolvedSupplyKgPerSecond + EPSILON
                    || timeAdmittedProducerSupplyKgPerSecond
                    > physicallyRoutableProducerSupplyKgPerSecond + EPSILON) {
                throw new IllegalArgumentException("delivery producer-capacity layers are inconsistent");
            }
        }
    }

    /**
     * Fixed-corpus aggregate report.
     *
     * @param version exact diagnostics version
     * @param corpusVersion fixed seed-corpus version
     * @param representativeProfileVersion representative production-probe profile version
     * @param bootstrapRequirementVersion bootstrap service-level authority version
     * @param activeFreighterCount explicit current representative route-level freighter allocation
     * @param candidateCount total evaluated systems across the fixed corpus
     * @param evidence deterministic candidate/commodity rows
     * @param bottleneckCounts aggregate counts by causal class
     * @param bottleneckCountsByCommodity aggregate counts by {@code class|commodity}
     */
    public record Report(
            String version,
            String corpusVersion,
            String representativeProfileVersion,
            String bootstrapRequirementVersion,
            int activeFreighterCount,
            int candidateCount,
            List<CandidateCommodityEvidence> evidence,
            Map<String, Integer> bottleneckCounts,
            Map<String, Integer> bottleneckCountsByCommodity) {
        /**
         * Validates and freezes one aggregate delivery report.
         *
         * @param version exact diagnostics version
         * @param corpusVersion fixed seed-corpus version
         * @param representativeProfileVersion representative profile version
         * @param bootstrapRequirementVersion bootstrap requirement authority version
         * @param activeFreighterCount explicit current route-level freighter allocation
         * @param candidateCount total evaluated systems
         * @param evidence deterministic candidate/commodity rows
         * @param bottleneckCounts aggregate causal counts
         * @param bottleneckCountsByCommodity aggregate causal counts by commodity
         */
        public Report {
            version = requireText(version, "version");
            corpusVersion = requireText(corpusVersion, "corpusVersion");
            representativeProfileVersion = requireText(representativeProfileVersion,
                    "representativeProfileVersion");
            bootstrapRequirementVersion = requireText(bootstrapRequirementVersion,
                    "bootstrapRequirementVersion");
            if (activeFreighterCount <= 0 || candidateCount <= 0) {
                throw new IllegalArgumentException("freighter and candidate counts must be positive");
            }
            Objects.requireNonNull(evidence, "evidence");
            ArrayList<CandidateCommodityEvidence> rows = new ArrayList<>(evidence);
            if (rows.isEmpty() || rows.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("delivery evidence must be non-empty and contain no nulls");
            }
            rows.sort(Comparator.comparingLong(CandidateCommodityEvidence::rootSeed)
                    .thenComparing(CandidateCommodityEvidence::candidateSystemId)
                    .thenComparing(CandidateCommodityEvidence::commodityId));
            evidence = List.copyOf(rows);
            bottleneckCounts = immutableCounts(bottleneckCounts, "bottleneckCounts");
            bottleneckCountsByCommodity = immutableCounts(
                    bottleneckCountsByCommodity, "bottleneckCountsByCommodity");
        }
    }

    /**
     * Replays the fixed corpus and measures where global resolved supply is lost before candidate delivery.
     *
     * @return deterministic delivery-bottleneck evidence
     */
    public static Report evaluateCurrent() {
        Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile profile =
                Stage20RepresentativeGeneratedWorldProbeProfile.deriveCurrent();
        Stage18StationInfrastructureCatalog stations = Stage18StationInfrastructureCatalogLoader.loadDefault();
        ArrayList<CandidateCommodityEvidence> evidence = new ArrayList<>();
        TreeMap<String, Integer> counts = new TreeMap<>();
        TreeMap<String, Integer> countsByCommodity = new TreeMap<>();
        int candidateCount = 0;

        for (long rootSeed : Stage20RepresentativeSeedCorpus.seeds()) {
            Stage20GeneratedWorldProductionProbe.ProbeResult probe =
                    Stage20GeneratedWorldProductionProbe.run(rootSeed, profile.inputs());
            GalaxyTopology topology = probe.topology().requireAcceptedTopology();
            Stage20JumpEdgeCatalog jumpEdges = probe.jumpEdges().orElseThrow();
            List<Stage20LocalInfrastructureLayout> layouts = probe.localLayouts().orElseThrow();
            SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
            Stage20PhysicalFreightRouteEvaluator routes = physicalRoutes(
                    topology, jumpEdges, layouts, stations, profile.inputs().transport());

            List<StarSystemId> systems = topology.systems().stream()
                    .map(value -> value.id())
                    .sorted()
                    .toList();
            candidateCount += systems.size();
            verifyProductionCandidateParity(probe, profile, topology, routes, systems);

            for (StarSystemId candidate : systems) {
                for (Requirement requirement : profile.inputs().acceptance().dependencyRequirements()) {
                    CandidateCommodityEvidence row = analyzeCommodity(
                            rootSeed, candidate, requirement, topology, supply, routes);
                    evidence.add(row);
                    counts.merge(row.bottleneck().name(), 1, Math::addExact);
                    countsByCommodity.merge(
                            row.bottleneck().name() + "|" + row.commodityId(), 1, Math::addExact);
                }
            }
        }

        return new Report(
                CURRENT_VERSION,
                Stage20RepresentativeSeedCorpus.CURRENT_VERSION,
                profile.version(),
                profile.bootstrapRequirementVersion(),
                profile.activeFreighterCount(),
                candidateCount,
                evidence,
                counts,
                countsByCommodity);
    }

    /**
     * Serializes compact deterministic aggregate evidence for CI inspection.
     *
     * @param report measured report
     * @return deterministic aggregate text ending with a newline
     */
    public static String toText(Report report) {
        Report value = Objects.requireNonNull(report, "report");
        StringBuilder text = new StringBuilder(4_096);
        text.append("version=").append(value.version()).append('\n');
        text.append("corpusVersion=").append(value.corpusVersion()).append('\n');
        text.append("representativeProfileVersion=").append(value.representativeProfileVersion()).append('\n');
        text.append("bootstrapRequirementVersion=").append(value.bootstrapRequirementVersion()).append('\n');
        text.append("activeFreighterCount=").append(value.activeFreighterCount()).append('\n');
        text.append("candidateCount=").append(value.candidateCount()).append('\n');
        text.append("candidateCommodityRows=").append(value.evidence().size()).append('\n');
        text.append("bottleneckCounts=").append(value.bottleneckCounts()).append('\n');
        text.append("bottleneckCountsByCommodity=").append(value.bottleneckCountsByCommodity()).append('\n');
        for (String commodityId : value.evidence().stream()
                .map(CandidateCommodityEvidence::commodityId)
                .distinct()
                .sorted()
                .toList()) {
            double maxDelivered = value.evidence().stream()
                    .filter(row -> row.commodityId().equals(commodityId))
                    .mapToDouble(CandidateCommodityEvidence::aggregateDeliveredUpperBoundKgPerSecond)
                    .max()
                    .orElse(0d);
            double maxTimeAdmitted = value.evidence().stream()
                    .filter(row -> row.commodityId().equals(commodityId))
                    .mapToDouble(CandidateCommodityEvidence::timeAdmittedProducerSupplyKgPerSecond)
                    .max()
                    .orElse(0d);
            text.append("commodityMax commodity=").append(commodityId)
                    .append(" maxTimeAdmittedProducerKgS=").append(maxTimeAdmitted)
                    .append(" maxAggregateDeliveredKgS=").append(maxDelivered)
                    .append('\n');
        }
        return text.toString();
    }

    private static CandidateCommodityEvidence analyzeCommodity(
            long rootSeed,
            StarSystemId candidate,
            Requirement requirement,
            GalaxyTopology topology,
            SupplyThroughputReport supply,
            Stage20PhysicalFreightRouteEvaluator routes) {
        double globalSupply = 0d;
        double routableSupply = 0d;
        double timeAdmittedSupply = 0d;
        double deliveredUpperBound = 0d;
        int totalProducers = 0;
        int routableProducers = 0;
        int timeAdmittedProducers = 0;
        int freightLimitedProducers = 0;

        for (Map.Entry<SupplyKey, Double> entry : supply.capacityKgPerSecondBySupply().entrySet()) {
            if (!entry.getKey().commodityId().equals(requirement.commodityId())) {
                continue;
            }
            totalProducers++;
            double producerCapacity = entry.getValue();
            globalSupply += producerCapacity;
            Optional<RouteAssessment> maybeRoute = routes.assess(entry.getKey().systemId(), candidate);
            if (maybeRoute.isEmpty()) {
                continue;
            }
            RouteAssessment route = validateRoute(
                    topology, entry.getKey().systemId(), candidate, maybeRoute.orElseThrow());
            routableProducers++;
            routableSupply += producerCapacity;
            if (route.travelTimeS() > requirement.maxSupplierRouteTimeS()) {
                continue;
            }
            timeAdmittedProducers++;
            timeAdmittedSupply += producerCapacity;
            double delivered = Math.min(producerCapacity, route.sustainableCargoThroughputKgPerSecond());
            deliveredUpperBound += delivered;
            if (route.sustainableCargoThroughputKgPerSecond() + EPSILON < producerCapacity) {
                freightLimitedProducers++;
            }
        }

        requireNonNegativeFinite(globalSupply, "globalSupply");
        requireNonNegativeFinite(routableSupply, "routableSupply");
        requireNonNegativeFinite(timeAdmittedSupply, "timeAdmittedSupply");
        requireNonNegativeFinite(deliveredUpperBound, "deliveredUpperBound");
        BottleneckClass bottleneck;
        if (globalSupply + EPSILON < requirement.requiredKgPerSecond()) {
            bottleneck = BottleneckClass.GLOBAL_SCARCITY;
        } else if (routableSupply + EPSILON < requirement.requiredKgPerSecond()) {
            bottleneck = BottleneckClass.PHYSICAL_ROUTE_UNAVAILABLE;
        } else if (timeAdmittedSupply + EPSILON < requirement.requiredKgPerSecond()) {
            bottleneck = BottleneckClass.ROUTE_TIME_BOTTLENECK;
        } else if (deliveredUpperBound + EPSILON < requirement.requiredKgPerSecond()) {
            bottleneck = BottleneckClass.FREIGHT_THROUGHPUT_BOTTLENECK;
        } else {
            bottleneck = BottleneckClass.REACHABLE_SUFFICIENT;
        }

        return new CandidateCommodityEvidence(
                rootSeed,
                candidate,
                requirement.commodityId(),
                requirement.requiredKgPerSecond(),
                requirement.maxSupplierRouteTimeS(),
                globalSupply,
                routableSupply,
                timeAdmittedSupply,
                deliveredUpperBound,
                totalProducers,
                routableProducers,
                timeAdmittedProducers,
                freightLimitedProducers,
                bottleneck);
    }

    private static void verifyProductionCandidateParity(
            Stage20GeneratedWorldProductionProbe.ProbeResult probe,
            Stage20RepresentativeGeneratedWorldProbeProfile.DerivedProfile profile,
            GalaxyTopology topology,
            Stage20PhysicalFreightRouteEvaluator routes,
            List<StarSystemId> systems) {
        var reserves = Stage20FactionStartDependencyDiagnostics.initialOperationalReserves(
                probe.resourceWorld().orElseThrow());
        SupplyThroughputReport supply = probe.supplyThroughput().orElseThrow();
        ArrayList<Evaluation> recomputed = new ArrayList<>();
        for (StarSystemId systemId : systems) {
            var diagnostics = Stage20FactionStartDependencyDiagnostics.analyze(
                    topology,
                    systemId,
                    profile.inputs().acceptance().dependencyRequirements(),
                    supply,
                    routes,
                    reserves,
                    (supplier, candidate, commodity, route) -> OptionalDouble.empty(),
                    (candidate, commodity) -> Optional.empty());
            recomputed.add(Stage20FactionStartCandidateEvaluator.evaluate(
                    diagnostics, profile.inputs().acceptance().factionStartProfile()));
        }
        recomputed.sort(Comparator.comparing(Evaluation::candidateSystemId));
        List<Evaluation> production = probe.candidateEvaluations().orElseThrow();
        if (!production.equals(List.copyOf(recomputed))) {
            throw new IllegalStateException(
                    "diagnostic endpoint reconstruction diverged from production candidate evaluations for seed "
                            + probe.rootSeed());
        }
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

    private static Map<String, Integer> immutableCounts(Map<String, Integer> source, String field) {
        Objects.requireNonNull(source, field);
        TreeMap<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : source.entrySet()) {
            String key = requireText(entry.getKey(), field + " key");
            int value = Objects.requireNonNull(entry.getValue(), field + " value");
            if (value <= 0) {
                throw new IllegalArgumentException(field + " values must be positive");
            }
            result.put(key, value);
        }
        return Map.copyOf(result);
    }

    private static void requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
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

    private record EndpointAuthority(
            double maximumLocalTravelSeconds,
            double jumpAccessSeconds,
            double transferRateKgPerSecond,
            String sourceEvidenceId) {
    }
}
