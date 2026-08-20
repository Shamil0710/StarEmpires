package com.spacesim.world.generation;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalogLoader;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalogLoader;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalogLoader;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalogLoader;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyLoader;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalogLoader;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.GalaxyId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.Stage20BootstrapProductionCapacityCalculator;
import com.spacesim.world.Stage20EconomicBootstrapValidator.BootstrapRequirementProfile;
import com.spacesim.world.Stage20EconomicThroughputAcceptance;
import com.spacesim.world.Stage20ExtractionSiteLogisticsResolver;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator;
import com.spacesim.world.Stage20FactionStartCandidateEvaluator.Evaluation;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics;
import com.spacesim.world.Stage20FactionStartDependencyDiagnostics.Requirement;
import com.spacesim.world.Stage20FactionStartPlacementGenerator;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementResult;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator;
import com.spacesim.world.Stage20LocalInfrastructureLayoutGenerator.PlacementRequest;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.EndpointCycleProfile;
import com.spacesim.world.Stage20PhysicalFreightRouteEvaluator.FreightFleetProfile;
import com.spacesim.world.Stage20PhysicalGalacticRoutePlanner;
import com.spacesim.world.Stage20ResourceOccurrenceGenerator;
import com.spacesim.world.Stage20ResourceOccurrenceWorld;
import com.spacesim.world.Stage20SystemGeometry;
import com.spacesim.world.Stage20SystemGeometryGenerator;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.AnalysisProfile;
import com.spacesim.world.Stage20TheoreticalSupplyThroughputAnalyzer.SupplyThroughputReport;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.calibration.Stage20FactionStartAcceptanceProfile;
import com.spacesim.world.calibration.Stage20TopologyQualityCalibrationProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

/**
 * Production-style Stage-20B/D/E generated-world probe assembled from the real generation layers.
 *
 * <p>The probe exists to measure one exact root seed without replacing any authoritative subsystem
 * with a fixture world. It creates macro region/system placement, ordinary topology, per-system SI
 * geometry/layouts, generated physical resource hosts, correlated finite resources, extraction
 * logistics/capacity, station process capacity, physical freight throughput, start dependency
 * diagnostics, candidate evaluation, bounded faction placement and the existing whole-seed result.</p>
 *
 * <p>Numeric economic demand and fitted ship/jump authority are explicit inputs. This class does not
 * invent consumption, prices, stock, ownership, FTL capability or cargo capacity merely to make a
 * seed pass. Delivered monetary cost, buffer stock and resource ownership stay unresolved unless a
 * later authority supplies them; the start profile decides whether that is diagnostic or blocking.</p>
 *
 * <p>Initial infrastructure is selected before any resource occurrence is generated. Therefore later
 * resource/start failures cannot ask this layer to add a better station, deposit or topology edge.</p>
 */
public final class Stage20GeneratedWorldProductionProbe {
    /** Current immutable production-probe implementation version. */
    public static final String CURRENT_VERSION = "stage20e.production-seed-probe.v1";
    private static final GalaxyId GENERATED_GALAXY_ID = new GalaxyId(20L);

    private Stage20GeneratedWorldProductionProbe() {
        throw new AssertionError("No instances");
    }

    /**
     * Explicit deterministic local-infrastructure authoring policy used by the probe.
     *
     * @param version stable infrastructure-policy version
     * @param majorHubArchetypeId Stage-18 major hub archetype installed in every generated system
     * @param industrialStationArchetypeIds Stage-18 station archetypes sampled independently of resources
     * @param resourceAnchorCountPerSystem fixed point-anchor count authored before resource generation
     */
    public record InitialInfrastructureProfile(
            String version,
            String majorHubArchetypeId,
            List<String> industrialStationArchetypeIds,
            int resourceAnchorCountPerSystem) {
        /**
         * Validates and freezes one infrastructure authoring profile.
         *
         * @param version stable profile version
         * @param majorHubArchetypeId major hub station archetype
         * @param industrialStationArchetypeIds sampled industrial station archetypes
         * @param resourceAnchorCountPerSystem fixed generated point-anchor count
         */
        public InitialInfrastructureProfile {
            version = requireText(version, "version");
            majorHubArchetypeId = requireText(majorHubArchetypeId, "majorHubArchetypeId");
            Objects.requireNonNull(industrialStationArchetypeIds, "industrialStationArchetypeIds");
            ArrayList<String> stations = new ArrayList<>();
            HashSet<String> unique = new HashSet<>();
            for (String stationId : industrialStationArchetypeIds) {
                String checked = requireText(stationId, "industrial station archetype ID");
                if (!unique.add(checked)) {
                    throw new IllegalArgumentException("duplicate industrial station archetype: " + checked);
                }
                stations.add(checked);
            }
            if (stations.isEmpty()) {
                throw new IllegalArgumentException("at least one industrial station archetype is required");
            }
            stations.sort(String::compareTo);
            industrialStationArchetypeIds = List.copyOf(stations);
            if (resourceAnchorCountPerSystem <= 0 || resourceAnchorCountPerSystem > 32) {
                throw new IllegalArgumentException("resourceAnchorCountPerSystem must be in 1..32");
            }
        }
    }

    /**
     * Economic/start acceptance authority supplied to the probe rather than guessed by it.
     *
     * <p>Every dependency requirement must match exactly one bootstrap commodity requirement in
     * commodity identity, sustained kg/s and maximum supplier-route time. The family label is only
     * additional diagnostics grouping metadata.</p>
     *
     * @param bootstrapRequirements authoritative essential-throughput requirements
     * @param dependencyRequirements exact diagnostics projection with explicit family labels
     * @param factionStartProfile versioned start-candidate/placement acceptance policy
     * @param stableFactionIds stable faction identities that require starts
     */
    public record AcceptanceAuthority(
            BootstrapRequirementProfile bootstrapRequirements,
            List<Requirement> dependencyRequirements,
            Stage20FactionStartAcceptanceProfile factionStartProfile,
            List<String> stableFactionIds) {
        /**
         * Validates one explicit acceptance-authority bundle.
         *
         * @param bootstrapRequirements essential throughput requirements
         * @param dependencyRequirements matching dependency requirements
         * @param factionStartProfile start acceptance/placement profile
         * @param stableFactionIds stable faction identities
         */
        public AcceptanceAuthority {
            Objects.requireNonNull(bootstrapRequirements, "bootstrapRequirements");
            Objects.requireNonNull(dependencyRequirements, "dependencyRequirements");
            Objects.requireNonNull(factionStartProfile, "factionStartProfile");
            Objects.requireNonNull(stableFactionIds, "stableFactionIds");
            ArrayList<Requirement> requirements = new ArrayList<>(dependencyRequirements);
            if (requirements.isEmpty() || requirements.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("dependencyRequirements must be non-empty and contain no nulls");
            }
            requirements.sort(Comparator.comparing(Requirement::commodityId));
            dependencyRequirements = List.copyOf(requirements);
            validateRequirementProjection(bootstrapRequirements, dependencyRequirements);
            ArrayList<String> factions = new ArrayList<>();
            HashSet<String> uniqueFactions = new HashSet<>();
            for (String factionId : stableFactionIds) {
                String checked = requireText(factionId, "stableFactionId");
                if (!uniqueFactions.add(checked)) {
                    throw new IllegalArgumentException("duplicate faction ID: " + checked);
                }
                factions.add(checked);
            }
            if (factions.isEmpty()) {
                throw new IllegalArgumentException("at least one faction start is required");
            }
            factions.sort(String::compareTo);
            stableFactionIds = List.copyOf(factions);
        }
    }

    /**
     * Physical fitted transport authority used by the real Stage-20D/E freight evaluator.
     *
     * @param loadedOutboundPlan executable fitted loaded one-edge jump plan
     * @param returnPlan executable fitted return one-edge jump plan
     * @param fleetProfile explicit representative freight allocation
     */
    public record PhysicalTransportAuthority(
            JumpPlan loadedOutboundPlan,
            JumpPlan returnPlan,
            FreightFleetProfile fleetProfile) {
        /**
         * Validates one physical transport authority bundle.
         *
         * @param loadedOutboundPlan fitted loaded jump plan
         * @param returnPlan fitted return jump plan
         * @param fleetProfile representative physical freight allocation
         */
        public PhysicalTransportAuthority {
            Objects.requireNonNull(loadedOutboundPlan, "loadedOutboundPlan");
            Objects.requireNonNull(returnPlan, "returnPlan");
            Objects.requireNonNull(fleetProfile, "fleetProfile");
            if (!loadedOutboundPlan.allowed() || !returnPlan.allowed()) {
                throw new IllegalArgumentException("production probe requires executable fitted jump plans");
            }
        }
    }

    /**
     * Complete explicit probe inputs.
     *
     * @param macroRequest generated galaxy size/region request
     * @param topologyQuality accepted Stage-20A topology quality policy
     * @param infrastructure initial local infrastructure authoring policy
     * @param acceptance economic/start acceptance authority
     * @param transport fitted physical freight authority
     */
    public record ProbeInputs(
            Stage20MacroGalaxyGeometryGenerator.GenerationRequest macroRequest,
            Stage20TopologyQualityCalibrationProfile topologyQuality,
            InitialInfrastructureProfile infrastructure,
            AcceptanceAuthority acceptance,
            PhysicalTransportAuthority transport) {
        /**
         * Validates one immutable probe input bundle.
         *
         * @param macroRequest macro geometry request
         * @param topologyQuality topology quality calibration
         * @param infrastructure initial infrastructure policy
         * @param acceptance economic/start acceptance authority
         * @param transport fitted physical transport authority
         */
        public ProbeInputs {
            Objects.requireNonNull(macroRequest, "macroRequest");
            Objects.requireNonNull(topologyQuality, "topologyQuality");
            Objects.requireNonNull(infrastructure, "infrastructure");
            Objects.requireNonNull(acceptance, "acceptance");
            Objects.requireNonNull(transport, "transport");
            if (!topologyQuality.closesStage20BEntryCoverage()) {
                throw new IllegalArgumentException(
                        "production probe requires accepted Stage-20A topology quality authority");
            }
        }
    }

    /**
     * Machine-readable evidence from one exact root-seed production probe.
     *
     * @param version exact probe implementation version
     * @param rootSeed evaluated root seed
     * @param macroGeometry generated macro geometry
     * @param topology generated topology result
     * @param localLayouts generated local SI layouts; absent only after topology rejection
     * @param physicalHosts generated physical resource-host semantics; absent only after topology rejection
     * @param resourceWorld generated finite resource world; absent only after topology rejection
     * @param logisticsReport generated extraction-site logistics resolution; absent only after topology rejection
     * @param supplyThroughput generated physical supply closure; absent only after topology rejection
     * @param candidateEvaluations faction-start candidate evaluations; absent only after topology rejection
     * @param placement bounded faction-start placement; absent only after topology rejection
     * @param economicAcceptance physical essential-throughput acceptance; absent only after topology rejection
     * @param seedAcceptance final existing whole-seed acceptance result
     */
    public record ProbeResult(
            String version,
            long rootSeed,
            Stage20MacroGalaxyGeometryGenerator.MacroGeometryResult macroGeometry,
            Stage20JumpTopologyGenerationResult topology,
            Optional<List<Stage20LocalInfrastructureLayout>> localLayouts,
            Optional<Stage20LocalPhysicalResourceHostGenerator.GenerationResult> physicalHosts,
            Optional<Stage20ResourceOccurrenceWorld> resourceWorld,
            Optional<Stage20ExtractionSiteLogisticsResolver.ResolutionReport> logisticsReport,
            Optional<SupplyThroughputReport> supplyThroughput,
            Optional<List<Evaluation>> candidateEvaluations,
            Optional<PlacementResult> placement,
            Optional<Stage20EconomicThroughputAcceptance.AcceptanceReport> economicAcceptance,
            Stage20GeneratedWorldSeedAcceptance.SeedResult seedAcceptance) {
        /**
         * Validates one immutable probe result.
         *
         * @param version exact probe version
         * @param rootSeed evaluated seed
         * @param macroGeometry generated macro geometry
         * @param topology generated topology
         * @param localLayouts generated local layouts
         * @param physicalHosts generated physical hosts
         * @param resourceWorld generated finite resources
         * @param logisticsReport extraction logistics resolution
         * @param supplyThroughput physical supply closure
         * @param candidateEvaluations start candidate evaluations
         * @param placement bounded placement
         * @param economicAcceptance physical economic acceptance
         * @param seedAcceptance final whole-seed result
         */
        public ProbeResult {
            version = requireText(version, "version");
            Objects.requireNonNull(macroGeometry, "macroGeometry");
            Objects.requireNonNull(topology, "topology");
            Objects.requireNonNull(localLayouts, "localLayouts");
            Objects.requireNonNull(physicalHosts, "physicalHosts");
            Objects.requireNonNull(resourceWorld, "resourceWorld");
            Objects.requireNonNull(logisticsReport, "logisticsReport");
            Objects.requireNonNull(supplyThroughput, "supplyThroughput");
            Objects.requireNonNull(candidateEvaluations, "candidateEvaluations");
            Objects.requireNonNull(placement, "placement");
            Objects.requireNonNull(economicAcceptance, "economicAcceptance");
            Objects.requireNonNull(seedAcceptance, "seedAcceptance");
            localLayouts = localLayouts.map(List::copyOf);
            candidateEvaluations = candidateEvaluations.map(List::copyOf);
            if (macroGeometry.rootSeed() != rootSeed || topology.seed() != rootSeed
                    || seedAcceptance.rootSeed() != rootSeed) {
                throw new IllegalArgumentException("probe evidence root seeds differ");
            }
            boolean topologyAccepted = topology.status() == Stage20JumpTopologyGenerationResult.Status.ACCEPTED;
            boolean downstreamComplete = localLayouts.isPresent()
                    && physicalHosts.isPresent()
                    && resourceWorld.isPresent()
                    && logisticsReport.isPresent()
                    && supplyThroughput.isPresent()
                    && candidateEvaluations.isPresent()
                    && placement.isPresent()
                    && economicAcceptance.isPresent();
            if (topologyAccepted != downstreamComplete) {
                throw new IllegalArgumentException(
                        "downstream probe evidence must exist exactly when topology is accepted");
            }
        }
    }

    /**
     * Runs the real Stage-20 generated-world layers for exactly one root seed.
     *
     * <p>Topology rejection stops all downstream materialization. Accepted topology continues through
     * physical local/resource/economic/start layers without trying another root seed or mutating a
     * failed state.</p>
     *
     * @param rootSeed exact root seed to measure
     * @param inputs explicit generation, acceptance and physical transport authority
     * @return complete deterministic probe evidence
     */
    public static ProbeResult run(long rootSeed, ProbeInputs inputs) {
        ProbeInputs authority = Objects.requireNonNull(inputs, "inputs");
        Stage20MacroGalaxyGeometryGenerator.MacroGeometryResult macro =
                Stage20MacroGalaxyGeometryGenerator.generate(rootSeed, authority.macroRequest());
        Stage20JumpTopologyGenerationResult topologyResult = Stage20JumpTopologyGenerator.generate(
                GENERATED_GALAXY_ID,
                "Generated galaxy " + Long.toUnsignedString(rootSeed),
                macro.sectors(),
                rootSeed,
                authority.topologyQuality());
        if (topologyResult.status() == Stage20JumpTopologyGenerationResult.Status.REJECTED_SEED) {
            Stage20GeneratedWorldSeedAcceptance.SeedResult seedResult = Stage20GeneratedWorldSeedAcceptance.compose(
                    topologyResult,
                    Optional.empty(),
                    Optional.empty());
            return new ProbeResult(
                    CURRENT_VERSION,
                    rootSeed,
                    macro,
                    topologyResult,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    seedResult);
        }

        GalaxyTopology topology = topologyResult.requireAcceptedTopology();
        Catalogs catalogs = loadCatalogs();
        validateInfrastructureProfile(authority.infrastructure(), catalogs.stations());
        List<Stage20LocalInfrastructureLayout> layouts = generateLocalLayouts(
                rootSeed, topology, authority.infrastructure());
        Stage20LocalPhysicalResourceHostGenerator.GenerationResult hosts =
                Stage20LocalPhysicalResourceHostGenerator.generate(
                        rootSeed, topology, layouts, catalogs.extraction());
        Stage20ResourceOccurrenceWorld resourceWorld = Stage20ResourceOccurrenceGenerator.generate(
                rootSeed,
                topology,
                layouts,
                hosts.resourceHostProfiles(),
                catalogs.ontology(),
                catalogs.extraction(),
                catalogs.facilities());

        Stage20ExtractionSiteLogisticsResolver.ResolutionReport logistics =
                Stage20ExtractionSiteLogisticsResolver.resolve(
                        resourceWorld, catalogs.ontology(), catalogs.stations());
        var extractionCapacities = Stage20BootstrapProductionCapacityCalculator.extractionCapacities(
                resourceWorld,
                catalogs.extraction(),
                catalogs.facilities(),
                logistics.asExportHandlingProvider());
        var stationCapacities = Stage20BootstrapProductionCapacityCalculator.stationProcessCapacities(
                layouts,
                catalogs.stations(),
                catalogs.facilities(),
                catalogs.ontology(),
                catalogs.refining(),
                catalogs.manufacturing());

        Stage20PhysicalFreightRouteEvaluator routes = physicalRoutes(
                topology, layouts, catalogs.stations(), authority.transport());
        AnalysisProfile analysisProfile = new AnalysisProfile(
                CURRENT_VERSION + ".supply-analysis",
                authority.acceptance().bootstrapRequirements().maxIntermediateInputRouteTimeS());
        SupplyThroughputReport supply = Stage20TheoreticalSupplyThroughputAnalyzer.analyze(
                topology,
                analysisProfile,
                routes,
                extractionCapacities,
                stationCapacities,
                catalogs.refining(),
                catalogs.manufacturing());

        List<Stage20FactionStartDependencyDiagnostics.ReserveSource> reserves =
                Stage20FactionStartDependencyDiagnostics.initialOperationalReserves(resourceWorld);
        List<StarSystemId> systems = orderedSystems(topology);
        ArrayList<Evaluation> evaluations = new ArrayList<>();
        for (StarSystemId systemId : systems) {
            var diagnostics = Stage20FactionStartDependencyDiagnostics.analyze(
                    topology,
                    systemId,
                    authority.acceptance().dependencyRequirements(),
                    supply,
                    routes,
                    reserves,
                    (supplier, candidate, commodity, route) -> OptionalDouble.empty(),
                    (candidate, commodity) -> Optional.empty());
            evaluations.add(Stage20FactionStartCandidateEvaluator.evaluate(
                    diagnostics, authority.acceptance().factionStartProfile()));
        }
        evaluations.sort(Comparator.comparing(Evaluation::candidateSystemId));

        PlacementResult placement = Stage20FactionStartPlacementGenerator.place(
                rootSeed,
                topology,
                authority.acceptance().stableFactionIds(),
                evaluations,
                authority.acceptance().factionStartProfile());

        Set<StarSystemId> economicSystems = economicEvaluationSystems(evaluations, placement, systems);
        Stage20EconomicThroughputAcceptance.AcceptanceReport economics =
                Stage20EconomicThroughputAcceptance.validate(
                        topology,
                        supply,
                        economicSystems,
                        authority.acceptance().bootstrapRequirements(),
                        routes);
        Stage20GeneratedWorldSeedAcceptance.SeedResult seedResult = Stage20GeneratedWorldSeedAcceptance.compose(
                topologyResult,
                Optional.of(economics),
                Optional.of(placement));

        return new ProbeResult(
                CURRENT_VERSION,
                rootSeed,
                macro,
                topologyResult,
                Optional.of(layouts),
                Optional.of(hosts),
                Optional.of(resourceWorld),
                Optional.of(logistics),
                Optional.of(supply),
                Optional.of(List.copyOf(evaluations)),
                Optional.of(placement),
                Optional.of(economics),
                seedResult);
    }

    private static Set<StarSystemId> economicEvaluationSystems(
            List<Evaluation> evaluations,
            PlacementResult placement,
            List<StarSystemId> allSystems) {
        if (placement.status() == Stage20FactionStartPlacementGenerator.PlacementStatus.ACCEPTED) {
            HashSet<StarSystemId> selected = new HashSet<>();
            placement.assignments().forEach(value -> selected.add(value.systemId()));
            return Collections.unmodifiableSet(selected);
        }
        HashSet<StarSystemId> acceptedCandidates = new HashSet<>();
        for (Evaluation evaluation : evaluations) {
            if (evaluation.status() == Stage20FactionStartCandidateEvaluator.Status.ACCEPTED) {
                acceptedCandidates.add(evaluation.candidateSystemId());
            }
        }
        if (!acceptedCandidates.isEmpty()) {
            return Collections.unmodifiableSet(acceptedCandidates);
        }
        return Set.of(allSystems.get(0));
    }

    private static List<Stage20LocalInfrastructureLayout> generateLocalLayouts(
            long rootSeed,
            GalaxyTopology topology,
            InitialInfrastructureProfile profile) {
        ArrayList<Stage20LocalInfrastructureLayout> layouts = new ArrayList<>();
        for (StarSystemId systemId : orderedSystems(topology)) {
            Stage20SystemGeometry geometry = Stage20SystemGeometryGenerator.generate(rootSeed, systemId);
            ArrayList<PlacementRequest> requests = new ArrayList<>();
            for (int index = 0; index < profile.resourceAnchorCountPerSystem(); index++) {
                requests.add(PlacementRequest.resourceFieldAnchor(
                        "resource." + systemId.value() + "." + index));
            }
            requests.add(PlacementRequest.jumpArrivalAnchor("jump-arrival." + systemId.value()));
            String industrialArchetype = selectedIndustrialArchetype(rootSeed, systemId, profile);
            requests.add(PlacementRequest.independentStation(
                    "industry." + systemId.value(), industrialArchetype));
            layouts.add(Stage20LocalInfrastructureLayoutGenerator.generate(
                    geometry,
                    geometry.centralReference(),
                    "hub." + systemId.value(),
                    profile.majorHubArchetypeId(),
                    requests));
        }
        layouts.sort(Comparator.comparing(Stage20LocalInfrastructureLayout::systemId));
        return List.copyOf(layouts);
    }

    private static String selectedIndustrialArchetype(
            long rootSeed,
            StarSystemId systemId,
            InitialInfrastructureProfile profile) {
        long mixed = mix64(rootSeed ^ Long.rotateLeft(systemId.value(), 21));
        int index = (int) Long.remainderUnsigned(mixed, profile.industrialStationArchetypeIds().size());
        return profile.industrialStationArchetypeIds().get(index);
    }

    private static Stage20PhysicalFreightRouteEvaluator physicalRoutes(
            GalaxyTopology topology,
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
                            "production probe layout lacks jump-arrival physical access: " + layout.systemId()));
            endpointBySystem.put(
                    layout.systemId(),
                    new EndpointAuthority(
                            maximumLocalTravelSeconds,
                            jumpAccessSeconds,
                            hub.transferMassRateKgPerSecond(),
                            "stage20c-layout:" + layout.routeCalibrationVersion()));
        }

        Stage20PhysicalGalacticRoutePlanner loaded = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.loadedOutboundPlan());
        Stage20PhysicalGalacticRoutePlanner returned = new Stage20PhysicalGalacticRoutePlanner(
                topology, transport.returnPlan());
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

    private static void validateInfrastructureProfile(
            InitialInfrastructureProfile profile,
            Stage18StationInfrastructureCatalog stations) {
        StationArchetypeDefinition hub = stations.findArchetype(profile.majorHubArchetypeId());
        if (hub == null) {
            throw new IllegalArgumentException("unknown major hub archetype: " + profile.majorHubArchetypeId());
        }
        if (!hub.allowedLocationTags().contains("location.orbital_station")) {
            throw new IllegalArgumentException("major hub must support orbital-station placement");
        }
        for (String stationId : profile.industrialStationArchetypeIds()) {
            StationArchetypeDefinition station = stations.findArchetype(stationId);
            if (station == null) {
                throw new IllegalArgumentException("unknown industrial station archetype: " + stationId);
            }
            if (!station.allowedLocationTags().contains("location.orbital_station")) {
                throw new IllegalArgumentException(
                        "probe industrial station must support orbital placement: " + stationId);
            }
        }
    }

    private static List<StarSystemId> orderedSystems(GalaxyTopology topology) {
        List<StarSystemId> systems = topology.systems().stream().map(value -> value.id()).sorted().toList();
        if (systems.isEmpty()) {
            throw new IllegalArgumentException("production probe topology has no systems");
        }
        return systems;
    }

    private static Catalogs loadCatalogs() {
        return new Catalogs(
                Stage18ResourceOntologyLoader.loadDefault(),
                Stage18ExtractionCatalogLoader.loadDefault(),
                Stage18FacilityCatalogLoader.loadDefault(),
                Stage18StationInfrastructureCatalogLoader.loadDefault(),
                Stage18RefiningCatalogLoader.loadDefault(),
                Stage18ManufacturingCatalogLoader.loadDefault());
    }

    private static void validateRequirementProjection(
            BootstrapRequirementProfile bootstrap,
            List<Requirement> dependencyRequirements) {
        if (bootstrap.essentialCommodities().size() != dependencyRequirements.size()) {
            throw new IllegalArgumentException(
                    "dependency requirements must cover every bootstrap essential exactly once");
        }
        TreeMap<String, Requirement> dependencyByCommodity = new TreeMap<>();
        for (Requirement requirement : dependencyRequirements) {
            if (dependencyByCommodity.put(requirement.commodityId(), requirement) != null) {
                throw new IllegalArgumentException("duplicate dependency commodity: " + requirement.commodityId());
            }
        }
        for (var commodity : bootstrap.essentialCommodities()) {
            Requirement dependency = dependencyByCommodity.get(commodity.commodityId());
            if (dependency == null) {
                throw new IllegalArgumentException(
                        "missing dependency projection for " + commodity.commodityId());
            }
            if (Double.compare(
                    dependency.requiredKgPerSecond(), commodity.minSupplierThroughputKgPerSecond()) != 0
                    || Double.compare(
                    dependency.maxSupplierRouteTimeS(), commodity.maxSupplierRouteTimeS()) != 0) {
                throw new IllegalArgumentException(
                        "dependency requirement must preserve bootstrap rate/time for " + commodity.commodityId());
            }
        }
    }

    private static long mix64(long value) {
        long mixed = value;
        mixed = (mixed ^ (mixed >>> 30)) * 0xbf58476d1ce4e5b9L;
        mixed = (mixed ^ (mixed >>> 27)) * 0x94d049bb133111ebL;
        return mixed ^ (mixed >>> 31);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private record EndpointAuthority(
            double maximumLocalTravelSeconds,
            double jumpAccessSeconds,
            double transferRateKgPerSecond,
            String sourceEvidenceId) {
    }

    private record Catalogs(
            Stage18ResourceOntologyCatalog ontology,
            Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities,
            Stage18StationInfrastructureCatalog stations,
            Stage18RefiningCatalog refining,
            Stage18ManufacturingCatalog manufacturing) {
    }
}
