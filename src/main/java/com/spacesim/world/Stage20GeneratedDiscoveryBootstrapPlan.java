package com.spacesim.world;

import com.spacesim.persistence.Stage20DiscoveryPersistentState;
import com.spacesim.world.Stage20DiscoveryKnowledgeRuntime.StaticObservation;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryEvidence;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoverySource;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.DiscoveryState;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledge;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.ResourceKnowledgeLevel;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectKind;
import com.spacesim.world.Stage20DiscoveryKnowledgeState.StaticObjectRef;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.Assignment;
import com.spacesim.world.Stage20FactionStartPlacementGenerator.PlacementStatus;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.FactionStationSpecialization;
import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.OperationalSpecializationReport;
import com.spacesim.world.Stage20ResourceOccurrenceWorld.ResourceOccurrence;
import com.spacesim.world.generation.Stage20ResolvedGeneratedWorldProductionProbe.ResolvedProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeMap;

/**
 * Builds explicit owner-local Stage-20G knowledge from accepted generated and Stage-20F authority.
 *
 * <p>The planner grants only three categories: the assigned start-system major hub, exact
 * Stage-20F operational stations owned by the faction, and caller-authored resource-knowledge
 * grants. It never iterates generated resources into owner knowledge by default and never copies
 * generated reserve/grade truth into a survey estimate.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20GeneratedDiscoveryBootstrapPlan {
    /** Stable generated-discovery bootstrap contract version. */
    public static final String CURRENT_VERSION = "stage20g.generated-discovery-bootstrap.v1";

    private Stage20GeneratedDiscoveryBootstrapPlan() {
        throw new AssertionError("No instances");
    }

    /**
     * Explicit resource knowledge granted at bootstrap by authored survey/map/intelligence state.
     *
     * @param stableFactionId receiving canonical faction identity
     * @param sourceId exact generated resource-occurrence identity
     * @param knowledge observer knowledge, never physical reserve state
     * @param evidence explicit provenance and freshness
     */
    public record ResourceKnowledgeGrant(
            String stableFactionId,
            String sourceId,
            ResourceKnowledge knowledge,
            DiscoveryEvidence evidence) implements Comparable<ResourceKnowledgeGrant> {
        /**
         * Validates one explicit resource-knowledge grant.
         *
         * @param stableFactionId receiving faction
         * @param sourceId generated resource source
         * @param knowledge observer resource knowledge
         * @param evidence provenance and freshness
         */
        public ResourceKnowledgeGrant {
            stableFactionId = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(knowledge, "knowledge");
            Objects.requireNonNull(evidence, "evidence");
            if (knowledge.level() == ResourceKnowledgeLevel.NONE) {
                throw new IllegalArgumentException("resource grant must contain resource knowledge");
            }
        }

        @Override
        public int compareTo(ResourceKnowledgeGrant other) {
            int owner = stableFactionId.compareTo(other.stableFactionId);
            if (owner != 0) {
                return owner;
            }
            int source = sourceId.compareTo(other.sourceId);
            return source != 0 ? source : evidence.compareTo(other.evidence);
        }
    }

    /**
     * Explicit bootstrap-time discovery authority not derivable from generated physical truth.
     *
     * @param version caller-authored authority version
     * @param rootSeed exact accepted generated root seed
     * @param worldFingerprint exact generated-world identity/content fingerprint
     * @param bootstrapSeconds authoritative bootstrap time
     * @param infrastructureIntelFreshnessSeconds finite owned-station bootstrap-intelligence freshness
     * @param resourceGrants explicit resource survey/map/intelligence grants
     */
    public record BootstrapAuthority(
            String version,
            long rootSeed,
            String worldFingerprint,
            double bootstrapSeconds,
            double infrastructureIntelFreshnessSeconds,
            List<ResourceKnowledgeGrant> resourceGrants) {
        /**
         * Canonicalizes explicit discovery bootstrap authority.
         *
         * @param version authority version
         * @param rootSeed exact root seed
         * @param worldFingerprint generated-world fingerprint
         * @param bootstrapSeconds bootstrap time
         * @param infrastructureIntelFreshnessSeconds owned-station bootstrap-intelligence freshness
         * @param resourceGrants explicit resource knowledge grants
         */
        public BootstrapAuthority {
            version = requireText(version, "version");
            worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
            requireNonNegativeFinite(bootstrapSeconds, "bootstrapSeconds");
            requirePositiveFinite(
                    infrastructureIntelFreshnessSeconds,
                    "infrastructureIntelFreshnessSeconds");
            ArrayList<ResourceKnowledgeGrant> copy = new ArrayList<>(Objects.requireNonNull(
                    resourceGrants, "resourceGrants"));
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("resourceGrants cannot contain null");
            }
            copy.sort(null);
            Set<String> keys = new HashSet<>();
            for (ResourceKnowledgeGrant grant : copy) {
                if (grant.evidence().observedAtSeconds() > bootstrapSeconds) {
                    throw new IllegalArgumentException("resource grant evidence cannot come from the future");
                }
                String key = grant.stableFactionId() + "\u0000" + grant.sourceId()
                        + "\u0000" + grant.evidence().source() + "\u0000"
                        + grant.evidence().provenanceId();
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("duplicate resource knowledge grant: " + key);
                }
            }
            resourceGrants = List.copyOf(copy);
        }
    }

    /**
     * Auditable accepted generated-discovery bootstrap result.
     *
     * @param version plan contract version
     * @param rootSeed exact accepted root seed
     * @param resolvedProbeVersion exact resolved generated-world evidence version
     * @param operationalSpecializationVersion exact Stage-20F authority version
     * @param bootstrapAuthorityVersion exact discovery authority version
     * @param worldFingerprint exact generated-world fingerprint
     * @param ownerKnowledge owner-local persistent static knowledge
     * @param startingHubGrantCount one start-hub grant per placed faction
     * @param operationalStationGrantCount exact Stage-20F owner/station grants
     * @param resourceKnowledgeGrantCount exact caller-authored resource grants
     */
    public record BootstrapResult(
            String version,
            long rootSeed,
            String resolvedProbeVersion,
            String operationalSpecializationVersion,
            String bootstrapAuthorityVersion,
            String worldFingerprint,
            List<Stage20DiscoveryKnowledgeState> ownerKnowledge,
            int startingHubGrantCount,
            int operationalStationGrantCount,
            int resourceKnowledgeGrantCount) {
        /**
         * Validates and canonicalizes the accepted bootstrap result.
         *
         * @param version plan version
         * @param rootSeed exact root seed
         * @param resolvedProbeVersion resolved generated-world evidence version
         * @param operationalSpecializationVersion Stage-20F authority version
         * @param bootstrapAuthorityVersion discovery authority version
         * @param worldFingerprint generated-world fingerprint
         * @param ownerKnowledge owner-local knowledge snapshots
         * @param startingHubGrantCount start-hub grants
         * @param operationalStationGrantCount operational-station grants
         * @param resourceKnowledgeGrantCount explicit resource grants
         */
        public BootstrapResult {
            version = requireText(version, "version");
            resolvedProbeVersion = requireText(resolvedProbeVersion, "resolvedProbeVersion");
            operationalSpecializationVersion = requireText(
                    operationalSpecializationVersion, "operationalSpecializationVersion");
            bootstrapAuthorityVersion = requireText(
                    bootstrapAuthorityVersion, "bootstrapAuthorityVersion");
            worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
            ArrayList<Stage20DiscoveryKnowledgeState> copy = new ArrayList<>(Objects.requireNonNull(
                    ownerKnowledge, "ownerKnowledge"));
            copy.sort(Comparator.comparing(Stage20DiscoveryKnowledgeState::ownerId));
            if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)
                    || copy.stream().map(Stage20DiscoveryKnowledgeState::ownerId).distinct().count()
                    != copy.size()) {
                throw new IllegalArgumentException("ownerKnowledge must be non-empty and owner-unique");
            }
            ownerKnowledge = List.copyOf(copy);
            if (startingHubGrantCount != ownerKnowledge.size()
                    || operationalStationGrantCount < 0
                    || resourceKnowledgeGrantCount < 0) {
                throw new IllegalArgumentException("bootstrap grant counts are inconsistent");
            }
        }

        /**
         * Returns one placed owner's exact bootstrap knowledge.
         *
         * @param stableFactionId placed canonical faction identity
         * @return exact owner-local knowledge snapshot
         */
        public Stage20DiscoveryKnowledgeState knowledgeFor(String stableFactionId) {
            String owner = WorldFactionIdentityState.normalizeStableId(stableFactionId);
            return ownerKnowledge.stream()
                    .filter(value -> value.ownerId().equals(owner))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Unknown bootstrap knowledge owner: " + owner));
        }

        /**
         * Projects the accepted result into the Stage-20G persistent sidecar.
         *
         * @return exact persistent discovery state
         */
        public Stage20DiscoveryPersistentState toPersistentState() {
            return new Stage20DiscoveryPersistentState(
                    Stage20DiscoveryPersistentState.CURRENT_VERSION,
                    rootSeed,
                    resolvedProbeVersion,
                    worldFingerprint,
                    ownerKnowledge);
        }
    }

    /**
     * Plans owner-local bootstrap knowledge from exact accepted generated/industrial authority.
     *
     * @param resolved accepted resolved generated world
     * @param operational exact closed Stage-20F operational specialization
     * @param authority explicit discovery bootstrap authority
     * @return deterministic auditable bootstrap result
     */
    public static BootstrapResult plan(
            ResolvedProbeResult resolved,
            OperationalSpecializationReport operational,
            BootstrapAuthority authority) {
        ResolvedProbeResult world = Objects.requireNonNull(resolved, "resolved");
        OperationalSpecializationReport industry = Objects.requireNonNull(operational, "operational");
        BootstrapAuthority grants = Objects.requireNonNull(authority, "authority");
        validateAuthorityChain(world, industry, grants);

        var generation = world.generation();
        var placement = generation.placement().orElseThrow();
        List<Stage20LocalInfrastructureLayout> layouts = generation.localLayouts().orElseThrow();
        Stage20ResourceOccurrenceWorld resources = generation.resourceWorld().orElseThrow();
        Map<StarSystemId, Stage20LocalInfrastructureLayout> layoutBySystem = indexLayouts(layouts);
        Map<String, ResourceOccurrence> occurrenceById = indexOccurrences(resources);
        TreeMap<String, Stage20DiscoveryKnowledgeState> knowledge = new TreeMap<>();
        Stage20DiscoveryKnowledgeRuntime runtime = new Stage20DiscoveryKnowledgeRuntime();

        for (Assignment assignment : placement.assignments()) {
            Stage20DiscoveryKnowledgeState owner = new Stage20DiscoveryKnowledgeState(
                    assignment.stableFactionId(), List.of());
            Stage20LocalInfrastructureLayout layout = requireLayout(
                    layoutBySystem, assignment.systemId());
            InfrastructurePlacement hub = layout.placement(layout.majorHubId());
            owner = runtime.observe(owner, stationObservation(
                    assignment.systemId(),
                    hub,
                    DiscoverySource.FACTION_INTELLIGENCE,
                    CURRENT_VERSION + ":start-hub:" + assignment.stableFactionId(),
                    grants.bootstrapSeconds(),
                    OptionalDouble.empty()));
            knowledge.put(owner.ownerId(), owner);
        }

        int stationGrants = 0;
        for (FactionStationSpecialization specialization : industry.specializations()) {
            String ownerId = specialization.key().stableFactionId();
            Stage20DiscoveryKnowledgeState owner = requireOwner(knowledge, ownerId);
            var station = specialization.key().station();
            Stage20LocalInfrastructureLayout layout = requireLayout(layoutBySystem, station.systemId());
            InfrastructurePlacement placementRow = layout.placement(station.stationPlacementId());
            if (!placementRow.isStation()) {
                throw new IllegalArgumentException("Stage-20F specialization must reference a station placement");
            }
            owner = runtime.observe(owner, stationObservation(
                    station.systemId(),
                    placementRow,
                    DiscoverySource.FACTION_INTELLIGENCE,
                    CURRENT_VERSION + ":owned-operational-station:" + ownerId
                            + ":" + station.stationPlacementId(),
                    grants.bootstrapSeconds(),
                    OptionalDouble.of(grants.bootstrapSeconds()
                            + grants.infrastructureIntelFreshnessSeconds())));
            knowledge.put(ownerId, owner);
            stationGrants++;
        }

        for (ResourceKnowledgeGrant grant : grants.resourceGrants()) {
            Stage20DiscoveryKnowledgeState owner = requireOwner(knowledge, grant.stableFactionId());
            ResourceOccurrence occurrence = occurrenceById.get(grant.sourceId());
            if (occurrence == null) {
                throw new IllegalArgumentException(
                        "resource knowledge grant references unknown generated source: " + grant.sourceId());
            }
            validateResourceFamily(grant, occurrence);
            String classification = grant.knowledge().resourceFamilyId()
                    .orElse("resource.host." + occurrence.hostClassId());
            StaticObservation observation = new StaticObservation(
                    new StaticObjectRef(
                            occurrence.systemId(),
                            StaticObjectKind.RESOURCE_OCCURRENCE,
                            occurrence.sourceId()),
                    DiscoveryState.KNOWN_STATIC_LOCATION,
                    Optional.of(classification),
                    Optional.of(occurrence.position()),
                    grant.knowledge(),
                    grant.evidence());
            knowledge.put(owner.ownerId(), runtime.observe(owner, observation));
        }

        return new BootstrapResult(
                CURRENT_VERSION,
                world.rootSeed(),
                world.version(),
                industry.version(),
                grants.version(),
                grants.worldFingerprint(),
                List.copyOf(knowledge.values()),
                placement.assignments().size(),
                stationGrants,
                grants.resourceGrants().size());
    }

    private static void validateAuthorityChain(
            ResolvedProbeResult world,
            OperationalSpecializationReport industry,
            BootstrapAuthority authority) {
        if (world.seedAcceptance().status() != Stage20GeneratedWorldSeedAcceptance.Status.ACCEPTED
                || world.generation().placement().orElseThrow().status() != PlacementStatus.ACCEPTED) {
            throw new IllegalArgumentException("discovery bootstrap requires an accepted placed generated world");
        }
        if (!industry.operationallyAuthoritative()
                || industry.rootSeed() != world.rootSeed()
                || !industry.resolvedProbeVersion().equals(world.version())
                || authority.rootSeed() != world.rootSeed()) {
            throw new IllegalArgumentException(
                    "discovery, generated world and Stage-20F authority must share one accepted root seed");
        }
    }

    private static StaticObservation stationObservation(
            StarSystemId systemId,
            InfrastructurePlacement placement,
            DiscoverySource source,
            String provenance,
            double observedAt,
            OptionalDouble freshUntil) {
        if (!placement.isStation()) {
            throw new IllegalArgumentException("bootstrap infrastructure knowledge requires a station placement");
        }
        return new StaticObservation(
                new StaticObjectRef(systemId, StaticObjectKind.INFRASTRUCTURE, placement.id()),
                DiscoveryState.KNOWN_STATIC_LOCATION,
                Optional.of(placement.stationArchetypeId().orElseThrow()),
                Optional.of(placement.position()),
                ResourceKnowledge.none(),
                new DiscoveryEvidence(source, provenance, observedAt, freshUntil));
    }

    private static void validateResourceFamily(ResourceKnowledgeGrant grant, ResourceOccurrence occurrence) {
        if (grant.knowledge().resourceFamilyId().isPresent()
                && !grant.knowledge().resourceFamilyId().orElseThrow().equals(occurrence.outputCommodityId())) {
            throw new IllegalArgumentException(
                    "classified resource grant family differs from generated occurrence output family");
        }
    }

    private static Map<StarSystemId, Stage20LocalInfrastructureLayout> indexLayouts(
            List<Stage20LocalInfrastructureLayout> layouts) {
        HashMap<StarSystemId, Stage20LocalInfrastructureLayout> result = new HashMap<>();
        for (Stage20LocalInfrastructureLayout layout : layouts) {
            if (result.put(layout.systemId(), layout) != null) {
                throw new IllegalArgumentException("duplicate generated local layout: " + layout.systemId());
            }
        }
        return Map.copyOf(result);
    }

    private static Map<String, ResourceOccurrence> indexOccurrences(Stage20ResourceOccurrenceWorld resources) {
        HashMap<String, ResourceOccurrence> result = new HashMap<>();
        for (ResourceOccurrence occurrence : resources.occurrences()) {
            if (result.put(occurrence.sourceId(), occurrence) != null) {
                throw new IllegalArgumentException("duplicate generated occurrence: " + occurrence.sourceId());
            }
        }
        return Map.copyOf(result);
    }

    private static Stage20LocalInfrastructureLayout requireLayout(
            Map<StarSystemId, Stage20LocalInfrastructureLayout> layouts,
            StarSystemId systemId) {
        Stage20LocalInfrastructureLayout layout = layouts.get(systemId);
        if (layout == null) {
            throw new IllegalArgumentException("Missing generated local layout: " + systemId);
        }
        return layout;
    }

    private static Stage20DiscoveryKnowledgeState requireOwner(
            Map<String, Stage20DiscoveryKnowledgeState> knowledge,
            String ownerId) {
        Stage20DiscoveryKnowledgeState owner = knowledge.get(ownerId);
        if (owner == null) {
            throw new IllegalArgumentException("knowledge grant references unplaced faction: " + ownerId);
        }
        return owner;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
