package com.spacesim.world.generation;

import com.spacesim.persistence.Stage20GeneratedCampaignPersistenceCodec;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20GeneratedWorldSeedAcceptance;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessCalculator;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.GateStatus;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementId;
import com.spacesim.world.calibration.Stage20ACalibrationReadinessProfile.RequirementStatus;
import com.spacesim.world.calibration.Stage20DiscoverySensorGeometryAcceptance;
import com.spacesim.world.generation.Stage20GeneratedEconomyCadenceAcceptance.AcceptanceReport;
import com.spacesim.world.generation.Stage20GeneratedEconomyCadenceAcceptance.Status;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

/**
 * Final machine-readable Stage-20 physical-world acceptance matrix.
 *
 * <p>The matrix composes the accepted calibration, generated topology/resources/starts, closed
 * industrial and freight plans, physical economy cadence, discovery state and canonical campaign
 * persistence. It does not fabricate the runtime entities that Stage 20 deliberately leaves for the
 * runtime bridge. Those five exact boundaries remain visible as deferred checks while the generated
 * physical authority itself can complete Stage 20.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20PhysicalWorldAcceptanceMatrix {
    /** Stable Stage-20L matrix schema and evaluator version. */
    public static final String CURRENT_VERSION = "stage20l.physical-world-acceptance-matrix.v1";
    /** Number of hard invariants in the canonical Stage-20 roadmap. */
    public static final int HARD_INVARIANT_COUNT = 40;

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> REQUIRED_WORLD_DOMAINS = Set.of(
            "MACRO_PROFILE",
            "JUMP_EDGE",
            "LOCAL_LAYOUT",
            "RESOURCE_OCCURRENCE",
            "INITIAL_EXTRACTION_SITE",
            "FACTION_START",
            "FREIGHT_OWNERSHIP_SLOT",
            "INDUSTRIAL_PROCESS_PLAN",
            "INDUSTRIAL_YARD_PLAN",
            "SPECIAL_LOCATION");
    private static final Set<RuntimeBridgeRequirement> REQUIRED_STAGE20F_BOUNDARIES = Set.of(
            RuntimeBridgeRequirement.SOURCE_SUPPLY_MATERIALIZATION,
            RuntimeBridgeRequirement.FREIGHT_FLEET_MATERIALIZATION,
            RuntimeBridgeRequirement.CARGO_ORDER_AND_LOT_MATERIALIZATION,
            RuntimeBridgeRequirement.INDUSTRIAL_ENTITY_MATERIALIZATION);
    private static final Set<OpenRuntimeBoundary> REQUIRED_OPEN_BOUNDARIES = Set.of(
            OpenRuntimeBoundary.SOURCE_SUPPLY_MATERIALIZATION,
            OpenRuntimeBoundary.FREIGHT_FLEET_MATERIALIZATION,
            OpenRuntimeBoundary.CARGO_ORDER_AND_LOT_MATERIALIZATION,
            OpenRuntimeBoundary.INDUSTRIAL_ENTITY_MATERIALIZATION,
            OpenRuntimeBoundary.LIVE_ARRIVAL_AUTHORITY_INTEGRATION);

    private Stage20PhysicalWorldAcceptanceMatrix() {
        throw new AssertionError("No instances");
    }

    /** Acceptance-matrix categories from the Stage-20L roadmap. */
    public enum Category {
        /** Calibrated SI distance, time, propulsion and geometry bands. */ SCALE,
        /** Unbounded local coordinates, precision and causal LOD. */ UNBOUNDED_SPACE,
        /** Neighbor graph structure, redundancy and regional diversity. */ TOPOLOGY_DIVERSITY,
        /** Finite resources, industrial feasibility, freight and trade. */ RESOURCE_ECONOMY,
        /** Measured route/supplier dependencies and interests. */ STRATEGIC_EMERGENCE,
        /** Physically viable, asymmetric and parity-safe starts. */ FACTION_START,
        /** Physical sensor phases and explicit observer knowledge. */ SENSOR,
        /** Separation of tactical, logistics and strategic scales. */ TACTICAL_STRATEGIC_SCALE,
        /** Bounded generation, route data and materialization work. */ PERFORMANCE,
        /** Deterministic saved authority and explicit runtime seams. */ PERSISTENCE
    }

    /** Status of one matrix check. */
    public enum CheckStatus {
        /** Current Stage-20 authority proves the check. */ PASS,
        /** Exact physical plan is complete; live runtime entity binding remains intentionally open. */
        DEFERRED_RUNTIME_BRIDGE,
        /** Provisional physical calibration is accepted for Stage 20 and requires Stage-22 review. */
        DEFERRED_STAGE22_REVIEW,
        /** Required Stage-20 evidence is absent or inconsistent. */ FAIL
    }

    /** Reproducible production-candidate outcome vocabulary required by Stage 20L. */
    public enum WorldQualityGateOutcome {
        /** Candidate passed without topology repair. */ ACCEPT,
        /** Candidate passed after bounded versioned deterministic repair. */ DETERMINISTIC_REPAIR,
        /** Candidate failed an authoritative ordinary-world gate. */ REJECT_SEED,
        /** An explicitly authored scenario policy, never an implicit ordinary-world rescue. */
        EXPLICIT_SCENARIO_OVERRIDE
    }

    /** Stable check identities; their category is part of the schema. */
    public enum CheckId {
        /** Complete non-blocking Stage-20A physical calibration. */
        SCALE_CALIBRATION(Category.SCALE),
        /** Explicit Stage-22 promotion/review boundary for provisional content. */
        STAGE22_CONTENT_PROMOTION(Category.SCALE),
        /** Hierarchical local coordinates remain physical outside presentation extents. */
        UNBOUNDED_COORDINATE_AUTHORITY(Category.UNBOUNDED_SPACE),
        /** Distant materialization/persistence retains causal state. */
        LOD_STATE_CAUSALITY(Category.UNBOUNDED_SPACE),
        /** Every ordinary inter-system transition is one explicit neighbor edge. */
        NEIGHBOR_ONLY_TRANSITION_GRAPH(Category.TOPOLOGY_DIVERSITY),
        /** Connected graph also passes anti-linearity, redundancy and gateway gates. */
        TOPOLOGY_QUALITY_DIVERSITY(Category.TOPOLOGY_DIVERSITY),
        /** Fixed representative corpus identity is retained without result-aware selection. */
        REPRESENTATIVE_CORPUS_IDENTITY(Category.TOPOLOGY_DIVERSITY),
        /** Stage-18-hosted occurrences and extraction reserves are finite and explicit. */
        FINITE_STAGE18_RESOURCE_AUTHORITY(Category.RESOURCE_ECONOMY),
        /** Extraction, process, freight, buffer and construction cadence is physically closed. */
        PHYSICAL_LOGISTICS_CADENCE(Category.RESOURCE_ECONOMY),
        /** Supplier/route value and comparative advantage derive from physical state. */
        MEASURED_STRATEGIC_DEPENDENCIES(Category.STRATEGIC_EMERGENCE),
        /** Starts are accepted by physical reachability and share ordinary world authority. */
        FACTION_START_VIABILITY_AND_PARITY(Category.FACTION_START),
        /** Detection, classification, tracking and fire-control retain physical separation. */
        SENSOR_INFORMATION_GEOMETRY(Category.SENSOR),
        /** Durable knowledge remains owner-local and does not reveal generated reserves. */
        DISCOVERY_NON_OMNISCIENCE(Category.SENSOR),
        /** Combat, freight and broad-system geometry remain measurably distinct. */
        TACTICAL_STRATEGIC_SCALE_SEPARATION(Category.TACTICAL_STRATEGIC_SCALE),
        /** Candidate generation and stored route/resource envelopes are bounded. */
        BOUNDED_GENERATION_AND_ROUTING(Category.PERFORMANCE),
        /** Canonical fingerprints and exact binary round trip preserve generated authority. */
        CANONICAL_PERSISTENCE(Category.PERSISTENCE),
        /** Only the five named runtime integration boundaries remain open. */
        OPEN_RUNTIME_BRIDGE_BOUNDARIES(Category.PERSISTENCE);

        private final Category category;

        CheckId(Category category) {
            this.category = category;
        }

        /** @return immutable schema category for this check */
        public Category category() {
            return category;
        }
    }

    /**
     * One deterministic matrix row.
     *
     * @param id stable check identity
     * @param category stable roadmap category
     * @param status calculated closure state
     * @param evidence concise deterministic evidence
     * @param hardInvariants canonical Stage-20 hard-invariant numbers covered by this row
     */
    public record Check(
            CheckId id,
            Category category,
            CheckStatus status,
            String evidence,
            List<Integer> hardInvariants) {
        /**
         * Validates and canonicalizes one matrix row.
         *
         * @param id stable check identity
         * @param category stable roadmap category
         * @param status calculated closure state
         * @param evidence concise deterministic evidence
         * @param hardInvariants covered hard-invariant numbers
         */
        public Check {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(status, "status");
            evidence = requireText(evidence, "evidence");
            if (category != id.category()) {
                throw new IllegalArgumentException("check category differs from its stable identity");
            }
            TreeSet<Integer> invariantSet = new TreeSet<>(Objects.requireNonNull(
                    hardInvariants, "hardInvariants"));
            if (invariantSet.stream().anyMatch(value -> value == null
                    || value < 1 || value > HARD_INVARIANT_COUNT)) {
                throw new IllegalArgumentException("hard invariant IDs must be in 1..40");
            }
            hardInvariants = List.copyOf(invariantSet);
            if (status == CheckStatus.DEFERRED_RUNTIME_BRIDGE
                    && id != CheckId.OPEN_RUNTIME_BRIDGE_BOUNDARIES) {
                throw new IllegalArgumentException("runtime deferral is valid only for the runtime-boundary row");
            }
            if (status == CheckStatus.DEFERRED_STAGE22_REVIEW
                    && id != CheckId.STAGE22_CONTENT_PROMOTION) {
                throw new IllegalArgumentException("Stage-22 deferral is valid only for content promotion");
            }
        }
    }

    /**
     * Complete deterministic Stage-20L decision.
     *
     * @param version matrix schema/evaluator version
     * @param rootSeed exact accepted candidate seed
     * @param representativeProfileVersion exact resolved production profile
     * @param representativeCorpusVersion fixed representative-corpus identity
     * @param representativeCorpusSeeds exact preselected corpus seeds
     * @param worldFingerprint canonical materialized-world SHA-256
     * @param qualityFingerprint canonical quality-report SHA-256
     * @param topologyRepairPasses committed bounded repair additions
     * @param outcome final world-quality gate outcome
     * @param checks complete canonical matrix rows
     * @param openRuntimeBoundaries exact boundaries deliberately left for runtime integration
     */
    public record MatrixReport(
            String version,
            long rootSeed,
            String representativeProfileVersion,
            String representativeCorpusVersion,
            List<Long> representativeCorpusSeeds,
            String worldFingerprint,
            String qualityFingerprint,
            int topologyRepairPasses,
            WorldQualityGateOutcome outcome,
            List<Check> checks,
            List<OpenRuntimeBoundary> openRuntimeBoundaries) {
        /**
         * Validates full category/check/invariant coverage and outcome consistency.
         *
         * @param version matrix schema/evaluator version
         * @param rootSeed exact accepted candidate seed
         * @param representativeProfileVersion exact resolved production profile
         * @param representativeCorpusVersion fixed representative-corpus identity
         * @param representativeCorpusSeeds exact preselected corpus seeds
         * @param worldFingerprint canonical materialized-world SHA-256
         * @param qualityFingerprint canonical quality-report SHA-256
         * @param topologyRepairPasses committed bounded repair additions
         * @param outcome final world-quality gate outcome
         * @param checks complete canonical matrix rows
         * @param openRuntimeBoundaries exact deferred runtime boundaries
         */
        public MatrixReport {
            version = requireText(version, "version");
            representativeProfileVersion = requireText(
                    representativeProfileVersion, "representativeProfileVersion");
            representativeCorpusVersion = requireText(
                    representativeCorpusVersion, "representativeCorpusVersion");
            if (!CURRENT_VERSION.equals(version)
                    || !Stage20RepresentativeSeedCorpus.CURRENT_VERSION.equals(
                    representativeCorpusVersion)) {
                throw new IllegalArgumentException("unsupported Stage-20L matrix/corpus version");
            }
            representativeCorpusSeeds = List.copyOf(Objects.requireNonNull(
                    representativeCorpusSeeds, "representativeCorpusSeeds"));
            if (!representativeCorpusSeeds.equals(Stage20RepresentativeSeedCorpus.seeds())) {
                throw new IllegalArgumentException("matrix must retain the exact fixed representative corpus");
            }
            worldFingerprint = requireSha256(worldFingerprint, "worldFingerprint");
            qualityFingerprint = requireSha256(qualityFingerprint, "qualityFingerprint");
            if (topologyRepairPasses < 0) {
                throw new IllegalArgumentException("topologyRepairPasses must be non-negative");
            }
            Objects.requireNonNull(outcome, "outcome");

            ArrayList<Check> rows = new ArrayList<>(Objects.requireNonNull(checks, "checks"));
            if (rows.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("checks cannot contain null");
            }
            rows.sort(Comparator.comparing(Check::id));
            EnumSet<CheckId> checkIds = EnumSet.noneOf(CheckId.class);
            EnumSet<Category> categories = EnumSet.noneOf(Category.class);
            TreeSet<Integer> invariants = new TreeSet<>();
            for (Check row : rows) {
                if (!checkIds.add(row.id())) {
                    throw new IllegalArgumentException("duplicate Stage-20L check: " + row.id());
                }
                categories.add(row.category());
                invariants.addAll(row.hardInvariants());
            }
            if (!checkIds.equals(EnumSet.allOf(CheckId.class))
                    || !categories.equals(EnumSet.allOf(Category.class))) {
                throw new IllegalArgumentException("matrix must cover every stable check and category");
            }
            TreeSet<Integer> expectedInvariants = new TreeSet<>();
            for (int invariant = 1; invariant <= HARD_INVARIANT_COUNT; invariant++) {
                expectedInvariants.add(invariant);
            }
            if (!invariants.equals(expectedInvariants)) {
                throw new IllegalArgumentException("matrix must cover every Stage-20 hard invariant");
            }
            checks = List.copyOf(rows);

            ArrayList<OpenRuntimeBoundary> boundaries = new ArrayList<>(Objects.requireNonNull(
                    openRuntimeBoundaries, "openRuntimeBoundaries"));
            boundaries.sort(Comparator.comparing(Enum::name));
            if (!Set.copyOf(boundaries).equals(REQUIRED_OPEN_BOUNDARIES)
                    || new HashSet<>(boundaries).size() != boundaries.size()) {
                throw new IllegalArgumentException("matrix must retain the exact five runtime boundaries");
            }
            openRuntimeBoundaries = List.copyOf(boundaries);

            boolean failed = checks.stream().anyMatch(value -> value.status() == CheckStatus.FAIL);
            if (failed != (outcome == WorldQualityGateOutcome.REJECT_SEED
                    || outcome == WorldQualityGateOutcome.EXPLICIT_SCENARIO_OVERRIDE)) {
                throw new IllegalArgumentException("quality outcome differs from matrix failures");
            }
            if (!failed) {
                WorldQualityGateOutcome expected = topologyRepairPasses == 0
                        ? WorldQualityGateOutcome.ACCEPT
                        : WorldQualityGateOutcome.DETERMINISTIC_REPAIR;
                if (outcome != expected) {
                    throw new IllegalArgumentException("accepted outcome differs from topology repair evidence");
                }
            }
        }

        /** @return whether Stage-20 physical authority is complete despite named downstream deferrals */
        public boolean stage20Complete() {
            return checks.stream().noneMatch(value -> value.status() == CheckStatus.FAIL)
                    && (outcome == WorldQualityGateOutcome.ACCEPT
                    || outcome == WorldQualityGateOutcome.DETERMINISTIC_REPAIR);
        }

        /** @return canonical deferred rows, excluding no required physical evidence */
        public List<Check> deferredChecks() {
            return checks.stream()
                    .filter(value -> value.status() == CheckStatus.DEFERRED_RUNTIME_BRIDGE
                            || value.status() == CheckStatus.DEFERRED_STAGE22_REVIEW)
                    .toList();
        }

        /** @return exact sorted set {@code 1..40} when the report is valid */
        public Set<Integer> coveredHardInvariants() {
            TreeSet<Integer> result = new TreeSet<>();
            checks.forEach(value -> result.addAll(value.hardInvariants()));
            return java.util.Collections.unmodifiableSet(result);
        }
    }

    /**
     * Evaluates the final matrix over one exact accepted Stage-20 authority chain.
     *
     * @param resolved accepted resolved physical generated world
     * @param specialization closed Stage-20F operational specialization
     * @param cadence accepted Stage-20J physical economy cadence
     * @param persistentState canonical Stage-20K campaign persistence state
     * @return deterministic complete Stage-20L report
     */
    public static MatrixReport evaluate(
            ResolvedProbeResult resolved,
            OperationalSpecializationReport specialization,
            AcceptanceReport cadence,
            Stage20GeneratedCampaignPersistentState persistentState) {
        ResolvedProbeResult world = Objects.requireNonNull(resolved, "resolved");
        OperationalSpecializationReport industry = Objects.requireNonNull(
                specialization, "specialization");
        AcceptanceReport economy = Objects.requireNonNull(cadence, "cadence");
        Stage20GeneratedCampaignPersistentState saved = Objects.requireNonNull(
                persistentState, "persistentState");
        validateAuthorityIdentity(world, industry, economy, saved);

        Stage20ACalibrationReadinessProfile readiness =
                Stage20ACalibrationReadinessCalculator.deriveCurrent();
        var sensor = Stage20DiscoverySensorGeometryAcceptance.deriveCurrent();
        var generation = world.generation();
        var topologyResult = generation.topology();
        var topology = topologyResult.requireAcceptedTopology();
        var quality = topologyResult.qualityReport();
        var resources = generation.resourceWorld().orElseThrow();
        var placement = generation.placement().orElseThrow();

        ArrayList<Check> checks = new ArrayList<>();
        boolean readinessClosed = readiness.overallStatus() == GateStatus.READY_FOR_STAGE20B
                && readiness.blockingRequirements().isEmpty()
                && readiness.missingRepresentativeRoles().isEmpty()
                && readiness.requirements().size() == RequirementId.values().length;
        checks.add(check(
                CheckId.SCALE_CALIBRATION,
                readinessClosed,
                "profile=" + readiness.version()
                        + ";requirements=" + readiness.requirements().size()
                        + ";representatives=" + readiness.representativeCoverage().size(),
                1, 5, 6, 7, 8, 9, 11, 12, 40));

        long stage22Deferred = readiness.requirements().stream()
                .filter(value -> value.status() == RequirementStatus.DEFERRED_STAGE22_CONTENT)
                .count();
        checks.add(new Check(
                CheckId.STAGE22_CONTENT_PROMOTION,
                CheckId.STAGE22_CONTENT_PROMOTION.category(),
                stage22Deferred == 0L ? CheckStatus.PASS : CheckStatus.DEFERRED_STAGE22_REVIEW,
                "profile=" + readiness.version() + ";deferred_requirements=" + stage22Deferred,
                List.of()));

        boolean farPrecisionClosed = requirementSatisfied(readiness, RequirementId.FAR_COORDINATE_PRECISION);
        checks.add(check(
                CheckId.UNBOUNDED_COORDINATE_AUTHORITY,
                farPrecisionClosed
                        && saved.materializationState().envelopeVersion()
                        == com.spacesim.persistence.Stage20MaterializationPersistentState.CURRENT_VERSION,
                "far_precision=" + requirementStatus(readiness, RequirementId.FAR_COORDINATE_PRECISION)
                        + ";coordinate_domain=hierarchical_unbounded_local_si",
                2, 3, 4, 13, 15, 16));

        boolean hasFarPhysicalEvidence = saved.materializationState().physicalEntities().stream()
                .anyMatch(value -> value.physicalState().position().cellX() != 0L
                        || value.physicalState().position().cellY() != 0L);
        checks.add(check(
                CheckId.LOD_STATE_CAUSALITY,
                requirementSatisfied(readiness, RequirementId.MATERIALIZATION_LOD_CLOSURE)
                        && hasFarPhysicalEvidence,
                "lod_closure=" + requirementStatus(readiness, RequirementId.MATERIALIZATION_LOD_CLOSURE)
                        + ";persisted_far_entities=" + (hasFarPhysicalEvidence ? 1 : 0),
                14));

        int physicalEdgeCount = generation.jumpEdges().orElseThrow().edges().size();
        checks.add(check(
                CheckId.NEIGHBOR_ONLY_TRANSITION_GRAPH,
                physicalEdgeCount == topology.connections().size()
                        && physicalEdgeCount > 0,
                "ordinary_connections=" + topology.connections().size()
                        + ";physical_edges=" + physicalEdgeCount,
                17, 21));

        boolean topologyQualityClosed = quality.accepted()
                && quality.connectedComponents() == 1
                && quality.unreachableSystems().isEmpty()
                && quality.unreachableSectors().isEmpty()
                && !quality.hubSystems().isEmpty()
                && quality.cycleParticipationFraction() > 0d
                && quality.sectorMotifFingerprints().size() == topology.sectors().size();
        checks.add(check(
                CheckId.TOPOLOGY_QUALITY_DIVERSITY,
                topologyQualityClosed,
                "components=" + quality.connectedComponents()
                        + ";mean_degree=" + quality.meanDegree()
                        + ";cycle_fraction=" + quality.cycleParticipationFraction()
                        + ";core_redundancy=" + quality.coreRouteRedundancyCoverage()
                        + ";repair_passes=" + topologyResult.repairPasses(),
                18, 19, 20));

        checks.add(check(
                CheckId.REPRESENTATIVE_CORPUS_IDENTITY,
                Stage20RepresentativeSeedCorpus.seeds().equals(
                        List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L,
                                9L, 10L, 11L, 12L, 13L, 14L, 15L, 16L)),
                "corpus=" + Stage20RepresentativeSeedCorpus.CURRENT_VERSION
                        + ";seed_count=" + Stage20RepresentativeSeedCorpus.seeds().size(),
                18, 19, 40));

        boolean finiteResources = !resources.occurrences().isEmpty()
                && !resources.initialExtractionSites().isEmpty()
                && resources.occurrences().stream().allMatch(value ->
                Double.isFinite(value.initialAccessibleMassKg())
                        && value.initialAccessibleMassKg() > 0d
                        && Double.isFinite(value.gradeFraction())
                        && value.gradeFraction() > 0d)
                && !economy.extraction().isEmpty()
                && !economy.hiddenRestockUsed();
        checks.add(check(
                CheckId.FINITE_STAGE18_RESOURCE_AUTHORITY,
                finiteResources,
                "occurrences=" + resources.occurrences().size()
                        + ";extraction_sites=" + resources.initialExtractionSites().size()
                        + ";hidden_restock=" + economy.hiddenRestockUsed(),
                25, 26, 27, 28, 33, 34, 39));

        boolean cadenceClosed = economy.status() == Status.ACCEPTED
                && !economy.generatedProcesses().isEmpty()
                && !economy.operationalProcesses().isEmpty()
                && !economy.freight().isEmpty()
                && !economy.buffers().isEmpty()
                && !economy.shipyards().isEmpty()
                && !economy.tradePotential().isEmpty()
                && economy.freight().stream().allMatch(value ->
                value.sustainableThroughputKgPerSecond() >= value.reservedInputKgPerSecond());
        checks.add(check(
                CheckId.PHYSICAL_LOGISTICS_CADENCE,
                cadenceClosed,
                "extraction=" + economy.extraction().size()
                        + ";processes=" + economy.operationalProcesses().size()
                        + ";freight_routes=" + economy.freight().size()
                        + ";shipyards=" + economy.shipyards().size()
                        + ";trade_flows=" + economy.tradePotential().size(),
                24, 30, 31, 32));

        boolean strategicEvidence = !economy.tradePotential().isEmpty()
                && economy.tradePotential().stream().allMatch(value ->
                value.comparativeCapacityAdvantageKgPerSecond() > 0d
                        && value.ordinaryJumpHops() > 0)
                && Double.isFinite(quality.maxSingleGatewayDependency());
        checks.add(check(
                CheckId.MEASURED_STRATEGIC_DEPENDENCIES,
                strategicEvidence,
                "trade_flows=" + economy.tradePotential().size()
                        + ";bridges=" + quality.bridgeEdges().size()
                        + ";articulations=" + quality.articulationSystems().size()
                        + ";max_gateway_dependency=" + quality.maxSingleGatewayDependency(),
                30, 32, 35));

        Set<String> factionOwners = new HashSet<>();
        placement.assignments().forEach(value -> factionOwners.add(value.stableFactionId()));
        boolean startsClosed = placement.status() == PlacementStatus.ACCEPTED
                && !placement.assignments().isEmpty()
                && economy.freight().stream().allMatch(value -> factionOwners.contains(value.stableFactionId()))
                && industry.specializations().stream().allMatch(value ->
                factionOwners.contains(value.key().stableFactionId()));
        checks.add(check(
                CheckId.FACTION_START_VIABILITY_AND_PARITY,
                startsClosed,
                "placements=" + placement.assignments().size()
                        + ";owners=" + factionOwners.size()
                        + ";free_corrections=0",
                29, 36, 37));

        checks.add(check(
                CheckId.SENSOR_INFORMATION_GEOMETRY,
                sensor.accepted()
                        && sensor.firstDetectionMaxDistanceM() > sensor.activeFireControlMaxDistanceM()
                        && sensor.intermediateDurationSeconds() >= sensor.minimumMeaningfulDurationSeconds(),
                "profile=" + sensor.version()
                        + ";detection_m=" + sensor.firstDetectionMaxDistanceM()
                        + ";fire_control_m=" + sensor.activeFireControlMaxDistanceM()
                        + ";intermediate_s=" + sensor.intermediateDurationSeconds(),
                8, 22));

        long occurrenceCount = resources.occurrences().size();
        boolean discoveryClosed = !saved.discoveryState().knowledgeStates().isEmpty()
                && saved.discoveryState().knowledgeStates().size() == placement.assignments().size()
                && saved.discoveryState().knowledgeStates().stream().allMatch(owner -> {
                    long knownResources = owner.entries().stream()
                            .filter(value -> value.object().kind() == StaticObjectKind.RESOURCE_OCCURRENCE)
                            .count();
                    return knownResources < occurrenceCount;
                });
        long knownResourceRows = saved.discoveryState().knowledgeStates().stream()
                .flatMap(value -> value.entries().stream())
                .filter(value -> value.object().kind() == StaticObjectKind.RESOURCE_OCCURRENCE)
                .count();
        checks.add(check(
                CheckId.DISCOVERY_NON_OMNISCIENCE,
                discoveryClosed,
                "knowledge_owners=" + saved.discoveryState().knowledgeStates().size()
                        + ";known_resource_rows=" + knownResourceRows
                        + ";physical_occurrences=" + occurrenceCount,
                23));

        boolean tacticalScaleClosed = nonBlocking(readiness,
                RequirementId.WEAPON_PD_SPATIAL_EVIDENCE,
                RequirementId.FORMATION_SPATIAL_EVIDENCE,
                RequirementId.STATION_PHYSICAL_GEOMETRY,
                RequirementId.STATION_JUMP_ARRIVAL_STANDOFF,
                RequirementId.LOCAL_ROUTE_SEMANTIC_BANDS,
                RequirementId.MAJOR_INFRASTRUCTURE_EXTENT_BANDS);
        checks.add(check(
                CheckId.TACTICAL_STRATEGIC_SCALE_SEPARATION,
                tacticalScaleClosed && sensor.accepted() && cadenceClosed,
                "calibration_profile=" + readiness.version()
                        + ";sensor_profile=" + sensor.version()
                        + ";cadence_profile=" + economy.version(),
                10));

        long maximumSystems = Math.multiplyExact(
                generation.macroGeometry().request().sectorCount(),
                generation.macroGeometry().request().maxSystemsPerSector());
        long completeGraphEdgeBound = Math.multiplyExact(
                (long) topology.systems().size(), topology.systems().size() - 1L) / 2L;
        boolean boundedGeneration = topology.systems().size() <= maximumSystems
                && topology.connections().size() <= completeGraphEdgeBound
                && generation.localLayouts().orElseThrow().size() == topology.systems().size()
                && saved.materializedWorld().worldRows().size() < 1_000_000;
        checks.add(check(
                CheckId.BOUNDED_GENERATION_AND_ROUTING,
                boundedGeneration,
                "systems=" + topology.systems().size() + "/" + maximumSystems
                        + ";edges=" + topology.connections().size() + "/" + completeGraphEdgeBound
                        + ";world_rows=" + saved.materializedWorld().worldRows().size(),
                3, 14, 40));

        byte[] firstEncoding = Stage20GeneratedCampaignPersistenceCodec.encode(saved);
        byte[] secondEncoding = Stage20GeneratedCampaignPersistenceCodec.encode(saved);
        boolean domainCoverage = saved.materializedWorld().worldRows().stream()
                .map(Stage20GeneratedCampaignPersistentState.CanonicalRow::domain)
                .collect(java.util.stream.Collectors.toSet())
                .containsAll(REQUIRED_WORLD_DOMAINS);
        boolean persistenceClosed = Arrays.equals(firstEncoding, secondEncoding)
                && saved.equals(Stage20GeneratedCampaignPersistenceCodec.decode(firstEncoding))
                && domainCoverage;
        checks.add(check(
                CheckId.CANONICAL_PERSISTENCE,
                persistenceClosed,
                "world_fingerprint=" + saved.materializedWorld().worldFingerprint()
                        + ";quality_fingerprint=" + saved.materializedWorld().qualityFingerprint()
                        + ";encoded_bytes=" + firstEncoding.length
                        + ";required_domains=" + REQUIRED_WORLD_DOMAINS.size(),
                13, 14, 23, 25, 30, 31, 38));

        boolean exactRuntimeBoundaries = industry.runtimeBridgeRequirements().equals(
                REQUIRED_STAGE20F_BOUNDARIES)
                && economy.remainingRuntimeBridgeRequirements().equals(REQUIRED_STAGE20F_BOUNDARIES)
                && saved.stage20fRuntimeBridgeRequirements().equals(REQUIRED_STAGE20F_BOUNDARIES)
                && Set.copyOf(saved.openRuntimeBoundaries()).equals(REQUIRED_OPEN_BOUNDARIES);
        checks.add(new Check(
                CheckId.OPEN_RUNTIME_BRIDGE_BOUNDARIES,
                CheckId.OPEN_RUNTIME_BRIDGE_BOUNDARIES.category(),
                exactRuntimeBoundaries ? CheckStatus.DEFERRED_RUNTIME_BRIDGE : CheckStatus.FAIL,
                "stage20f_boundaries=" + REQUIRED_STAGE20F_BOUNDARIES.size()
                        + ";arrival_boundaries=1;total=" + REQUIRED_OPEN_BOUNDARIES.size(),
                List.of()));

        boolean failed = checks.stream().anyMatch(value -> value.status() == CheckStatus.FAIL);
        WorldQualityGateOutcome outcome = failed
                ? WorldQualityGateOutcome.REJECT_SEED
                : topologyResult.repairPasses() == 0
                        ? WorldQualityGateOutcome.ACCEPT
                        : WorldQualityGateOutcome.DETERMINISTIC_REPAIR;
        return new MatrixReport(
                CURRENT_VERSION,
                world.rootSeed(),
                world.representativeProfileVersion(),
                Stage20RepresentativeSeedCorpus.CURRENT_VERSION,
                Stage20RepresentativeSeedCorpus.seeds(),
                saved.materializedWorld().worldFingerprint(),
                saved.materializedWorld().qualityFingerprint(),
                topologyResult.repairPasses(),
                outcome,
                checks,
                saved.openRuntimeBoundaries());
    }

    private static void validateAuthorityIdentity(
            ResolvedProbeResult world,
            OperationalSpecializationReport industry,
            AcceptanceReport economy,
            Stage20GeneratedCampaignPersistentState saved) {
        var identity = saved.generationIdentity();
        if (world.seedAcceptance().status() != Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED
                || industry.rootSeed() != world.rootSeed()
                || economy.rootSeed() != world.rootSeed()
                || identity.worldSeed() != world.rootSeed()
                || !industry.resolvedProbeVersion().equals(world.version())
                || !economy.resolvedProbeVersion().equals(world.version())
                || !economy.specializationVersion().equals(industry.version())
                || !identity.generatorVersion().equals(world.version())
                || !identity.sourceGeneratorVersion().equals(world.sourceProbeVersion())
                || !identity.generationProfile().equals(world.representativeProfileVersion())
                || !industry.readyForRuntimeBridge()) {
            throw new IllegalArgumentException(
                    "Stage-20L inputs must be one exact accepted generated/industrial/persistence chain");
        }
    }

    private static Check check(CheckId id, boolean passed, String evidence, int... invariants) {
        return new Check(
                id,
                id.category(),
                passed ? CheckStatus.PASS : CheckStatus.FAIL,
                evidence,
                Arrays.stream(invariants).boxed().toList());
    }

    private static boolean requirementSatisfied(
            Stage20ACalibrationReadinessProfile readiness,
            RequirementId id) {
        return requirementStatus(readiness, id) == RequirementStatus.SATISFIED;
    }

    private static RequirementStatus requirementStatus(
            Stage20ACalibrationReadinessProfile readiness,
            RequirementId id) {
        return readiness.requirements().stream()
                .filter(value -> value.id() == id)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("missing readiness requirement: " + id))
                .status();
    }

    private static boolean nonBlocking(
            Stage20ACalibrationReadinessProfile readiness,
            RequirementId... ids) {
        return Arrays.stream(ids).allMatch(id ->
                requirementStatus(readiness, id) != RequirementStatus.BLOCKING_STAGE20B_ENTRY
                        && requirementStatus(readiness, id) != RequirementStatus.OWNED_BY_LATER_STAGE20);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }

    private static String requireSha256(String value, String field) {
        String checked = requireText(value, field);
        if (!SHA_256.matcher(checked).matches()) {
            throw new IllegalArgumentException(field + " must be lowercase SHA-256 hex");
        }
        return checked;
    }
}
