package com.spacesim.world;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.content.Stage18FacilityCatalog;
import com.spacesim.content.Stage18FacilityCatalog.FacilityDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog;
import com.spacesim.content.Stage18ManufacturingCatalog.ComponentRecipeDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18RefiningCatalog;
import com.spacesim.content.Stage18RefiningCatalog.RecipeInputDefinition;
import com.spacesim.content.Stage18RefiningCatalog.RefiningRecipeDefinition;
import com.spacesim.content.Stage18ResourceOntologyCatalog;
import com.spacesim.content.Stage18ResourceOntologyCatalog.CommodityDefinition;
import com.spacesim.content.Stage18StationInfrastructureCatalog;
import com.spacesim.content.Stage18StationInfrastructureCatalog.StationArchetypeDefinition;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.InitialExtractionSite;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Stage-20E economic-bootstrap validator over real Stage-18 resources, facilities and recipes.
 *
 * <p>The validator never creates missing commodities. It computes a deterministic production
 * closure from generated initial extraction sites and explicit station facilities. Every imported
 * dependency must be backed by a caller-provided physical route assessment whose ordered path is
 * revalidated against {@link GalaxyTopology#neighbors(StarSystemId)}. This keeps legacy abstract
 * strategic-speed timing outside the Stage-20E acceptance boundary.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20EconomicBootstrapValidator {
    private Stage20EconomicBootstrapValidator() {
        throw new AssertionError("No instances");
    }

    /** Supplies physical route consequences produced by the Stage-20D/navigation-logistics layer. */
    @FunctionalInterface
    public interface RouteEvaluator {
        /**
         * Evaluates one physical supply route.
         * @param origin producer system
         * @param destination consumer/processor system
         * @return explicit physical route assessment, or empty when no feasible route exists
         */
        Optional<RouteAssessment> assess(StarSystemId origin, StarSystemId destination);
    }

    /**
     * One explicit physical supply-route assessment.
     * @param orderedSystems ordered systems from producer to consumer, including both endpoints
     * @param travelTimeS physical representative delivery time
     * @param sustainableCargoThroughputKgPerSecond physically sustainable delivered throughput
     */
    public record RouteAssessment(List<StarSystemId> orderedSystems, double travelTimeS,
            double sustainableCargoThroughputKgPerSecond) {
        /** Validates one immutable physical route assessment. */
        public RouteAssessment {
            Objects.requireNonNull(orderedSystems, "orderedSystems");
            if (orderedSystems.isEmpty() || orderedSystems.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("orderedSystems must be non-empty and contain no nulls");
            }
            orderedSystems = List.copyOf(orderedSystems);
            if (!Double.isFinite(travelTimeS) || travelTimeS < 0d) {
                throw new IllegalArgumentException("travelTimeS must be finite and non-negative");
            }
            if (!Double.isFinite(sustainableCargoThroughputKgPerSecond)
                    || sustainableCargoThroughputKgPerSecond <= 0d) {
                throw new IllegalArgumentException("sustainableCargoThroughputKgPerSecond must be positive and finite");
            }
        }
    }

    /**
     * Calibrated essential/bootstrap requirements; numeric limits are injected, not guessed here.
     * @param version stable calibration/profile version
     * @param maxIntermediateInputRouteTimeS maximum delivery time accepted for recipe inputs
     * @param minIntermediateInputThroughputKgPerSecond minimum delivered throughput for recipe inputs
     * @param essentialCommodities required commodity reachability by start system
     */
    public record BootstrapRequirementProfile(String version, double maxIntermediateInputRouteTimeS,
            double minIntermediateInputThroughputKgPerSecond, List<CommodityRequirement> essentialCommodities) {
        /** Validates one immutable requirement profile. */
        public BootstrapRequirementProfile {
            version = requireText(version, "version");
            requirePositiveFinite(maxIntermediateInputRouteTimeS, "maxIntermediateInputRouteTimeS");
            requirePositiveFinite(minIntermediateInputThroughputKgPerSecond,
                    "minIntermediateInputThroughputKgPerSecond");
            Objects.requireNonNull(essentialCommodities, "essentialCommodities");
            ArrayList<CommodityRequirement> copy = new ArrayList<>(essentialCommodities);
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("essentialCommodities must be non-empty and contain no nulls");
            }
            copy.sort(Comparator.comparing(CommodityRequirement::commodityId));
            HashSet<String> ids = new HashSet<>();
            for (CommodityRequirement requirement : copy) {
                if (!ids.add(requirement.commodityId())) {
                    throw new IllegalArgumentException("duplicate essential commodity requirement: " + requirement.commodityId());
                }
            }
            essentialCommodities = List.copyOf(copy);
        }
    }

    /**
     * One required delivered commodity for a bootstrap/start-system acceptance check.
     * @param commodityId authoritative Stage-18 commodity ID
     * @param maxSupplierRouteTimeS maximum acceptable physical supplier route time
     * @param minSupplierThroughputKgPerSecond minimum acceptable delivered throughput
     */
    public record CommodityRequirement(String commodityId, double maxSupplierRouteTimeS,
            double minSupplierThroughputKgPerSecond) {
        /** Validates one commodity requirement. */
        public CommodityRequirement {
            commodityId = requireText(commodityId, "commodityId");
            requirePositiveFinite(maxSupplierRouteTimeS, "maxSupplierRouteTimeS");
            requirePositiveFinite(minSupplierThroughputKgPerSecond, "minSupplierThroughputKgPerSecond");
        }
    }

    /**
     * Machine-readable acceptance result.
     * @param accepted true only when every required start/commodity has a physical supply chain
     * @param requirementProfileVersion exact calibrated requirement profile version
     * @param producerSystemsByCommodity deterministic production closure by commodity
     * @param requirementEvidence successful essential-supply evidence
     * @param failures deterministic rejection diagnostics
     */
    public record BootstrapReport(boolean accepted, String requirementProfileVersion,
            Map<String, Set<StarSystemId>> producerSystemsByCommodity,
            List<RequirementEvidence> requirementEvidence, List<BootstrapFailure> failures) {
        /** Validates and freezes one deterministic report. */
        public BootstrapReport {
            requirementProfileVersion = requireText(requirementProfileVersion, "requirementProfileVersion");
            Objects.requireNonNull(producerSystemsByCommodity, "producerSystemsByCommodity");
            Objects.requireNonNull(requirementEvidence, "requirementEvidence");
            Objects.requireNonNull(failures, "failures");
            TreeMap<String, Set<StarSystemId>> producers = new TreeMap<>();
            for (Map.Entry<String, Set<StarSystemId>> entry : producerSystemsByCommodity.entrySet()) {
                TreeSet<StarSystemId> systems = new TreeSet<>(Objects.requireNonNull(entry.getValue(), "producer systems"));
                producers.put(requireText(entry.getKey(), "producer commodity"), Collections.unmodifiableSet(systems));
            }
            producerSystemsByCommodity = Collections.unmodifiableMap(producers);
            ArrayList<RequirementEvidence> evidenceCopy = new ArrayList<>(requirementEvidence);
            ArrayList<BootstrapFailure> failureCopy = new ArrayList<>(failures);
            evidenceCopy.sort(Comparator.comparing(RequirementEvidence::startSystemId)
                    .thenComparing(RequirementEvidence::commodityId));
            failureCopy.sort(Comparator.comparing(BootstrapFailure::startSystemId)
                    .thenComparing(BootstrapFailure::commodityId)
                    .thenComparing(value -> value.reason().name()));
            requirementEvidence = List.copyOf(evidenceCopy);
            failures = List.copyOf(failureCopy);
            if (accepted != failures.isEmpty()) {
                throw new IllegalArgumentException("accepted must equal failures.isEmpty()");
            }
        }
    }

    /** Why one essential bootstrap requirement failed. */
    public enum FailureReason {
        /** No physical production chain can produce the requested Stage-18 commodity. */ NO_PRODUCER,
        /** Producers exist, but no calibrated route satisfies time/throughput requirements. */ NO_FEASIBLE_ROUTE
    }

    /**
     * Successful supplier evidence for one start-system essential requirement.
     * @param startSystemId evaluated start/bootstrap system
     * @param commodityId required commodity
     * @param producerSystemId selected physical producer
     * @param route selected explicit physical route
     */
    public record RequirementEvidence(StarSystemId startSystemId, String commodityId,
            StarSystemId producerSystemId, RouteAssessment route) {
        /** Validates one evidence row. */
        public RequirementEvidence {
            Objects.requireNonNull(startSystemId, "startSystemId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(producerSystemId, "producerSystemId");
            Objects.requireNonNull(route, "route");
        }
    }

    /**
     * Deterministic bootstrap rejection row.
     * @param startSystemId evaluated start/bootstrap system
     * @param commodityId required commodity
     * @param reason failure class
     * @param detail human-readable diagnostic without hidden repair action
     */
    public record BootstrapFailure(StarSystemId startSystemId, String commodityId,
            FailureReason reason, String detail) {
        /** Validates one failure row. */
        public BootstrapFailure {
            Objects.requireNonNull(startSystemId, "startSystemId");
            commodityId = requireText(commodityId, "commodityId");
            Objects.requireNonNull(reason, "reason");
            detail = requireText(detail, "detail");
        }
    }

    /**
     * Validates bootstrap production and essential delivered-supply reachability.
     * @param topology authoritative explicit neighbor topology
     * @param resourceWorld generated Stage-20E resource world
     * @param localLayouts Stage-20C station placements
     * @param startSystems systems evaluated for ordinary bootstrap viability
     * @param requirementProfile calibrated economic acceptance requirements
     * @param routeEvaluator physical route/time/throughput evaluator
     * @param ontology authoritative Stage-18 ontology
     * @param extraction authoritative Stage-18 extraction methods
     * @param facilities authoritative Stage-18 facility catalog
     * @param stationInfrastructure authoritative Stage-18 station archetypes
     * @param refining authoritative Stage-18 refining recipes
     * @param manufacturing authoritative Stage-18 component manufacturing recipes
     * @return machine-readable acceptance report; no world state is mutated
     */
    public static BootstrapReport validate(GalaxyTopology topology, Stage20ResourceOccurrenceWorld resourceWorld,
            List<Stage20LocalInfrastructureLayout> localLayouts, List<StarSystemId> startSystems,
            BootstrapRequirementProfile requirementProfile, RouteEvaluator routeEvaluator,
            Stage18ResourceOntologyCatalog ontology, Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities, Stage18StationInfrastructureCatalog stationInfrastructure,
            Stage18RefiningCatalog refining, Stage18ManufacturingCatalog manufacturing) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Stage20ResourceOccurrenceWorld checkedWorld = Objects.requireNonNull(resourceWorld, "resourceWorld");
        BootstrapRequirementProfile checkedProfile = Objects.requireNonNull(requirementProfile, "requirementProfile");
        RouteEvaluator checkedRoutes = Objects.requireNonNull(routeEvaluator, "routeEvaluator");
        Stage18ResourceOntologyCatalog checkedOntology = Objects.requireNonNull(ontology, "ontology");
        Stage18ExtractionCatalog checkedExtraction = Objects.requireNonNull(extraction, "extraction");
        Stage18FacilityCatalog checkedFacilities = Objects.requireNonNull(facilities, "facilities");
        Stage18StationInfrastructureCatalog checkedStations = Objects.requireNonNull(stationInfrastructure, "stationInfrastructure");
        Stage18RefiningCatalog checkedRefining = Objects.requireNonNull(refining, "refining");
        Stage18ManufacturingCatalog checkedManufacturing = Objects.requireNonNull(manufacturing, "manufacturing");
        Objects.requireNonNull(localLayouts, "localLayouts");
        Objects.requireNonNull(startSystems, "startSystems");
        validateWorldFingerprints(checkedWorld, checkedOntology, checkedExtraction, checkedFacilities);
        for (CommodityRequirement requirement : checkedProfile.essentialCommodities()) {
            if (checkedOntology.findCommodity(requirement.commodityId()) == null) {
                throw new IllegalArgumentException("bootstrap requirement references unknown Stage-18 commodity: " + requirement.commodityId());
            }
        }
        TreeSet<StarSystemId> orderedStarts = new TreeSet<>();
        for (StarSystemId start : startSystems) {
            StarSystemId checked = Objects.requireNonNull(start, "start system");
            if (checkedTopology.findSystem(checked).isEmpty()) {
                throw new IllegalArgumentException("start system is outside topology: " + checked);
            }
            orderedStarts.add(checked);
        }
        if (orderedStarts.isEmpty()) throw new IllegalArgumentException("startSystems must not be empty");
        List<IndustrialNode> industrialNodes = industrialNodes(checkedTopology, localLayouts, checkedStations, checkedFacilities);
        Map<String, Set<StarSystemId>> producers = initialFeedstockProducers(checkedWorld, checkedOntology, checkedExtraction, checkedFacilities);
        computeProductionClosure(checkedTopology, producers, industrialNodes, checkedProfile, checkedRoutes,
                checkedOntology, checkedRefining, checkedManufacturing);
        ArrayList<RequirementEvidence> evidence = new ArrayList<>();
        ArrayList<BootstrapFailure> failures = new ArrayList<>();
        for (StarSystemId start : orderedStarts) {
            for (CommodityRequirement requirement : checkedProfile.essentialCommodities()) {
                Set<StarSystemId> commodityProducers = producers.getOrDefault(requirement.commodityId(), Set.of());
                if (commodityProducers.isEmpty()) {
                    failures.add(new BootstrapFailure(start, requirement.commodityId(), FailureReason.NO_PRODUCER,
                            "No generated extraction/facility/recipe chain produces the required commodity"));
                    continue;
                }
                SupplierChoice supplier = chooseSupplier(checkedTopology, checkedRoutes, commodityProducers, start,
                        requirement.maxSupplierRouteTimeS(), requirement.minSupplierThroughputKgPerSecond());
                if (supplier == null) {
                    failures.add(new BootstrapFailure(start, requirement.commodityId(), FailureReason.NO_FEASIBLE_ROUTE,
                            "Producers exist but no explicit neighbor path satisfies calibrated time/throughput limits"));
                } else {
                    evidence.add(new RequirementEvidence(start, requirement.commodityId(),
                            supplier.producerSystemId(), supplier.route()));
                }
            }
        }
        return new BootstrapReport(failures.isEmpty(), checkedProfile.version(), producers, evidence, failures);
    }

    private static List<IndustrialNode> industrialNodes(GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> localLayouts,
            Stage18StationInfrastructureCatalog stationInfrastructure, Stage18FacilityCatalog facilities) {
        ArrayList<IndustrialNode> nodes = new ArrayList<>();
        HashSet<String> nodeIds = new HashSet<>();
        for (Stage20LocalInfrastructureLayout layout : localLayouts) {
            Stage20LocalInfrastructureLayout checkedLayout = Objects.requireNonNull(layout, "local layout");
            if (topology.findSystem(checkedLayout.systemId()).isEmpty()) {
                throw new IllegalArgumentException("local layout is outside topology: " + checkedLayout.systemId());
            }
            for (InfrastructurePlacement placement : checkedLayout.placements()) {
                if (!placement.isStation()) continue;
                String archetypeId = placement.stationArchetypeId().orElseThrow();
                StationArchetypeDefinition archetype = stationInfrastructure.findArchetype(archetypeId);
                if (archetype == null) {
                    throw new IllegalArgumentException("station placement references unknown Stage-18 archetype: " + archetypeId);
                }
                ArrayList<Set<String>> facilityCapabilitySets = new ArrayList<>();
                for (String facilityId : archetype.installedFacilityDefinitionIds()) {
                    FacilityDefinition facility = facilities.findFacility(facilityId);
                    if (facility == null) {
                        throw new IllegalArgumentException("station archetype references unknown facility: " + facilityId);
                    }
                    facilityCapabilitySets.add(Collections.unmodifiableSet(new TreeSet<>(facility.capabilityTags())));
                }
                String nodeId = checkedLayout.systemId().value() + ":" + placement.id();
                if (!nodeIds.add(nodeId)) throw new IllegalArgumentException("duplicate industrial node: " + nodeId);
                nodes.add(new IndustrialNode(nodeId, checkedLayout.systemId(), facilityCapabilitySets,
                        archetype.storageCapacityByClassKg().keySet()));
            }
        }
        nodes.sort(Comparator.comparing(IndustrialNode::systemId).thenComparing(IndustrialNode::nodeId));
        return List.copyOf(nodes);
    }

    private static Map<String, Set<StarSystemId>> initialFeedstockProducers(Stage20ResourceOccurrenceWorld world,
            Stage18ResourceOntologyCatalog ontology, Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities) {
        TreeMap<String, Set<StarSystemId>> producers = new TreeMap<>();
        for (InitialExtractionSite site : world.initialExtractionSites()) {
            ResourceOccurrence occurrence = world.occurrence(site.sourceId());
            FacilityDefinition facility = facilities.findFacility(site.facilityDefinitionId());
            if (facility == null) {
                throw new IllegalArgumentException("generated extraction site references unknown facility: " + site.facilityDefinitionId());
            }
            ExtractionMethodDefinition method = extraction.findMethod(site.extractionMethodId());
            if (method == null || method.sourceKind() != SourceKind.NATURAL_OCCURRENCE) {
                throw new IllegalArgumentException("generated extraction site references invalid natural extraction method: " + site.extractionMethodId());
            }
            if (method.environment() != occurrence.environment()
                    || !method.compatibleOccurrenceTypeIds().contains(occurrence.occurrenceTypeId())) {
                throw new IllegalArgumentException("generated extraction site method is incompatible with occurrence: " + site.siteId());
            }
            if (!facility.allowedLocationTags().contains(site.locationTag())
                    || !facility.capabilityTags().containsAll(method.requiredCapabilityTags())
                    || !facility.capabilityTags().containsAll(occurrence.requiredCapabilityTags())) {
                throw new IllegalArgumentException("generated extraction site facility is incompatible with source/method: " + site.siteId());
            }
            CommodityDefinition commodity = ontology.findCommodity(occurrence.outputCommodityId());
            if (commodity == null) {
                throw new IllegalArgumentException("generated occurrence references unknown commodity: " + occurrence.outputCommodityId());
            }
            if (!facility.storageClassInterfaces().contains(commodity.storageClassId())) {
                throw new IllegalArgumentException("generated extraction site cannot physically handle output storage class: " + site.siteId());
            }
            producers.computeIfAbsent(occurrence.outputCommodityId(), ignored -> new TreeSet<>()).add(occurrence.systemId());
        }
        return producers;
    }

    private static void computeProductionClosure(GalaxyTopology topology, Map<String, Set<StarSystemId>> producers,
            List<IndustrialNode> nodes, BootstrapRequirementProfile profile, RouteEvaluator routes,
            Stage18ResourceOntologyCatalog ontology, Stage18RefiningCatalog refining,
            Stage18ManufacturingCatalog manufacturing) {
        boolean changed;
        int iterations = 0;
        int maxIterations = ontology.getCommodities().size() + 4;
        do {
            changed = false;
            iterations++;
            for (IndustrialNode node : nodes) {
                for (RefiningRecipeDefinition recipe : refining.getRecipes()) {
                    if (!node.hasSingleFacilityCapability(recipe.requiredCapabilityTags())) continue;
                    if (!nodeSupportsRecipeStorage(node, ontology,
                            recipe.inputs().stream().map(RecipeInputDefinition::commodityId).toList(),
                            recipe.outputCommodityId())) continue;
                    if (inputsReachNode(topology, routes, producers,
                            recipe.inputs().stream().map(RecipeInputDefinition::commodityId).toList(), node.systemId(),
                            profile.maxIntermediateInputRouteTimeS(), profile.minIntermediateInputThroughputKgPerSecond())) {
                        changed |= addProducer(producers, recipe.outputCommodityId(), node.systemId());
                    }
                }
                for (ComponentRecipeDefinition recipe : manufacturing.getComponentRecipes()) {
                    if (!node.hasSingleFacilityCapability(recipe.requiredCapabilityTags())) continue;
                    if (!nodeSupportsRecipeStorage(node, ontology,
                            recipe.inputs().stream().map(ManufacturingInputDefinition::commodityId).toList(),
                            recipe.outputCommodityId())) continue;
                    if (inputsReachNode(topology, routes, producers,
                            recipe.inputs().stream().map(ManufacturingInputDefinition::commodityId).toList(), node.systemId(),
                            profile.maxIntermediateInputRouteTimeS(), profile.minIntermediateInputThroughputKgPerSecond())) {
                        changed |= addProducer(producers, recipe.outputCommodityId(), node.systemId());
                    }
                }
            }
        } while (changed && iterations <= maxIterations);
        if (changed) throw new IllegalStateException("Stage-20E production closure did not converge within bounded iterations");
    }

    private static boolean nodeSupportsRecipeStorage(IndustrialNode node, Stage18ResourceOntologyCatalog ontology,
            List<String> inputCommodityIds, String outputCommodityId) {
        for (String inputId : inputCommodityIds) {
            CommodityDefinition input = ontology.findCommodity(inputId);
            if (input == null || !node.storageClassIds().contains(input.storageClassId())) return false;
        }
        CommodityDefinition output = ontology.findCommodity(outputCommodityId);
        return output != null && node.storageClassIds().contains(output.storageClassId());
    }

    private static boolean inputsReachNode(GalaxyTopology topology, RouteEvaluator routes,
            Map<String, Set<StarSystemId>> producers, List<String> inputCommodityIds, StarSystemId destination,
            double maxTimeS, double minThroughputKgPerSecond) {
        for (String inputId : inputCommodityIds) {
            Set<StarSystemId> inputProducers = producers.getOrDefault(inputId, Set.of());
            if (inputProducers.isEmpty()) return false;
            if (chooseSupplier(topology, routes, inputProducers, destination, maxTimeS,
                    minThroughputKgPerSecond) == null) return false;
        }
        return true;
    }

    private static SupplierChoice chooseSupplier(GalaxyTopology topology, RouteEvaluator routes,
            Set<StarSystemId> producerSystems, StarSystemId destination, double maxTimeS,
            double minThroughputKgPerSecond) {
        SupplierChoice best = null;
        for (StarSystemId producer : new TreeSet<>(producerSystems)) {
            Optional<RouteAssessment> maybe = routes.assess(producer, destination);
            if (maybe.isEmpty()) continue;
            RouteAssessment route = validateRouteAssessment(topology, producer, destination, maybe.orElseThrow());
            if (route.travelTimeS() > maxTimeS
                    || route.sustainableCargoThroughputKgPerSecond() < minThroughputKgPerSecond) continue;
            SupplierChoice candidate = new SupplierChoice(producer, route);
            if (best == null || candidate.route().travelTimeS() < best.route().travelTimeS()
                    || (candidate.route().travelTimeS() == best.route().travelTimeS()
                    && candidate.producerSystemId().compareTo(best.producerSystemId()) < 0)) {
                best = candidate;
            }
        }
        return best;
    }

    private static RouteAssessment validateRouteAssessment(GalaxyTopology topology, StarSystemId origin,
            StarSystemId destination, RouteAssessment assessment) {
        List<StarSystemId> path = assessment.orderedSystems();
        if (!path.get(0).equals(origin) || !path.get(path.size() - 1).equals(destination)) {
            throw new IllegalArgumentException("route assessment endpoints do not match requested producer/consumer");
        }
        if (origin.equals(destination)) {
            if (path.size() != 1) throw new IllegalArgumentException("same-system route must contain exactly one system");
            return assessment;
        }
        if (path.size() < 2) throw new IllegalArgumentException("inter-system route must contain at least two systems");
        for (int index = 0; index < path.size() - 1; index++) {
            StarSystemId from = path.get(index);
            StarSystemId to = path.get(index + 1);
            if (!topology.neighbors(from).contains(to)) {
                throw new IllegalArgumentException("route evaluator returned non-neighbor shortcut: " + from + " -> " + to);
            }
        }
        return assessment;
    }

    private static boolean addProducer(Map<String, Set<StarSystemId>> producers, String commodityId,
            StarSystemId systemId) {
        return producers.computeIfAbsent(commodityId, ignored -> new TreeSet<>()).add(systemId);
    }

    private static void validateWorldFingerprints(Stage20ResourceOccurrenceWorld world,
            Stage18ResourceOntologyCatalog ontology, Stage18ExtractionCatalog extraction,
            Stage18FacilityCatalog facilities) {
        if (!world.ontologyFingerprint().equals(ontology.getFingerprint())) {
            throw new IllegalArgumentException("resource world ontology fingerprint differs from validator ontology");
        }
        if (!world.extractionFingerprint().equals(extraction.getFingerprint())) {
            throw new IllegalArgumentException("resource world extraction fingerprint differs from validator extraction catalog");
        }
        if (!world.facilityFingerprint().equals(facilities.getFingerprint())) {
            throw new IllegalArgumentException("resource world facility fingerprint differs from validator facilities");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " must be non-blank");
        return value;
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) throw new IllegalArgumentException(field + " must be positive and finite");
    }

    private record IndustrialNode(String nodeId, StarSystemId systemId, List<Set<String>> facilityCapabilitySets,
            Set<String> storageClassIds) {
        private IndustrialNode {
            nodeId = requireText(nodeId, "nodeId");
            Objects.requireNonNull(systemId, "systemId");
            Objects.requireNonNull(facilityCapabilitySets, "facilityCapabilitySets");
            ArrayList<Set<String>> capabilityCopy = new ArrayList<>();
            for (Set<String> capabilitySet : facilityCapabilitySets) {
                capabilityCopy.add(Collections.unmodifiableSet(
                        new TreeSet<>(Objects.requireNonNull(capabilitySet, "facility capability set"))));
            }
            capabilityCopy.sort(Comparator.comparing(value -> String.join("\u0000", value)));
            facilityCapabilitySets = List.copyOf(capabilityCopy);
            storageClassIds = Collections.unmodifiableSet(new TreeSet<>(storageClassIds));
        }
        private boolean hasSingleFacilityCapability(Set<String> requiredCapabilityTags) {
            Objects.requireNonNull(requiredCapabilityTags, "requiredCapabilityTags");
            return facilityCapabilitySets.stream().anyMatch(value -> value.containsAll(requiredCapabilityTags));
        }
    }

    private record SupplierChoice(StarSystemId producerSystemId, RouteAssessment route) {}
}
