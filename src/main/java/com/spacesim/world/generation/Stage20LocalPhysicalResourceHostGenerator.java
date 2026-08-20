package com.spacesim.world.generation;

import com.spacesim.content.Stage18ExtractionCatalog;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.ExtractionMethodDefinition;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.simulation.SimulationRandom;
import com.spacesim.simulation.StatefulRandom;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.Stage20LocalInfrastructureLayout;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.Stage20ResourceOccurrenceGenerator.ResourceHostProfile;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Deterministically binds physical resource-host semantics to authoritative Stage-20C resource anchors.
 *
 * <p>This class closes the first local-host gap between generated SI world geometry and Stage-20E
 * resource occurrence generation. Stage-20C already owns the point-anchor {@link LocalPhysicalPosition};
 * this generator assigns a stable physical host class to each such anchor and derives occurrence
 * compatibility directly from the authoritative Stage-18 natural-extraction catalog.</p>
 *
 * <p>The generator does not create deposits, reserves, extraction sites, facilities, stock, ownership,
 * throughput or economic bonuses. It also does not invent resource-field extents: the underlying
 * Stage-20C row remains a point anchor until a later version has separate calibrated extent authority.</p>
 *
 * <p>The v1 host-class selection is deterministic authoring among Stage-18 environments that actually
 * expose at least one natural extraction method. Compatible occurrence affinities are neutral
 * {@code 1.0} gates rather than guessed richness multipliers. Regional richness and finite reserves
 * therefore remain exclusively owned by the Stage-20E occurrence generator.</p>
 */
public final class Stage20LocalPhysicalResourceHostGenerator {
    /** Current immutable local physical-resource-host generation version. */
    public static final String CURRENT_VERSION = "stage20b.local-physical-resource-hosts.v1";

    private static final String RNG_PREFIX = CURRENT_VERSION + ".system.";

    private Stage20LocalPhysicalResourceHostGenerator() {
        throw new AssertionError("No instances");
    }

    /** Supported v1 physical host classes backed by existing Stage-18 extraction environments. */
    public enum HostClass {
        /** Free-flying asteroid, rubble aggregate or analogous small body. */
        ASTEROID_FREE_BODY("host.asteroid.free_body", ExtractionEnvironment.FREE_BODY, "location.free_body"),
        /** Surface-accessible regolith or hard-body mining location. */
        ROCKY_SURFACE("host.rocky.surface", ExtractionEnvironment.SURFACE, "location.surface"),
        /** Deep hard-rock/subsurface extraction location on a larger body. */
        DEEP_SUBSURFACE_BODY(
                "host.rocky.deep_subsurface",
                ExtractionEnvironment.DEEP_SUBSURFACE,
                "location.deep_subsurface"),
        /** Ice/regolith or analogous volatile-bearing natural host. */
        VOLATILE_BEARING_BODY(
                "host.volatile_bearing",
                ExtractionEnvironment.VOLATILE_BEARING,
                "location.volatile_site");

        private final String hostClassId;
        private final ExtractionEnvironment environment;
        private final String locationTag;

        HostClass(String hostClassId, ExtractionEnvironment environment, String locationTag) {
            this.hostClassId = hostClassId;
            this.environment = environment;
            this.locationTag = locationTag;
        }

        /**
         * Returns the stable generation-only physical host classifier.
         *
         * @return stable host class ID
         */
        public String hostClassId() {
            return hostClassId;
        }

        /**
         * Returns the authoritative Stage-18 extraction environment represented by this host.
         *
         * @return extraction environment
         */
        public ExtractionEnvironment environment() {
            return environment;
        }

        /**
         * Returns the physical Stage-18 facility installation-location tag for this host.
         *
         * @return facility location tag
         */
        public String locationTag() {
            return locationTag;
        }
    }

    /**
     * One generated physical natural-resource host bound to an authoritative SI point anchor.
     *
     * @param version exact host-generation version
     * @param systemId owning star system
     * @param anchorId stable Stage-20C resource-field anchor identity
     * @param hostClass generated physical host class
     * @param position authoritative local SI position inherited from the resource anchor
     * @param occurrenceAffinityByTypeId neutral compatibility gates for Stage-18 occurrence types
     * @param sourceRequiredCapabilityTags source-specific extraction requirements beyond method baseline
     */
    public record PhysicalHost(
            String version,
            StarSystemId systemId,
            String anchorId,
            HostClass hostClass,
            LocalPhysicalPosition position,
            Map<String, Double> occurrenceAffinityByTypeId,
            Set<String> sourceRequiredCapabilityTags) {
        /**
         * Validates and deterministically freezes one generated physical host row.
         *
         * @param version exact host-generation version
         * @param systemId owning star system
         * @param anchorId stable resource anchor identity
         * @param hostClass generated physical host class
         * @param position authoritative local SI position
         * @param occurrenceAffinityByTypeId Stage-18 occurrence compatibility gates
         * @param sourceRequiredCapabilityTags source-specific extra capability tags
         */
        public PhysicalHost {
            version = requireText(version, "version");
            Objects.requireNonNull(systemId, "systemId");
            anchorId = requireText(anchorId, "anchorId");
            Objects.requireNonNull(hostClass, "hostClass");
            Objects.requireNonNull(position, "position");
            Objects.requireNonNull(occurrenceAffinityByTypeId, "occurrenceAffinityByTypeId");
            TreeMap<String, Double> affinities = new TreeMap<>();
            for (Map.Entry<String, Double> entry : occurrenceAffinityByTypeId.entrySet()) {
                String occurrenceTypeId = requireText(entry.getKey(), "occurrence type ID");
                double affinity = Objects.requireNonNull(entry.getValue(), "occurrence affinity");
                if (!Double.isFinite(affinity) || affinity <= 0d || affinity > 2d) {
                    throw new IllegalArgumentException("occurrence affinity must be in (0,2]");
                }
                affinities.put(occurrenceTypeId, affinity);
            }
            if (affinities.isEmpty()) {
                throw new IllegalArgumentException("physical resource host must support at least one occurrence type");
            }
            occurrenceAffinityByTypeId = Collections.unmodifiableMap(affinities);
            Objects.requireNonNull(sourceRequiredCapabilityTags, "sourceRequiredCapabilityTags");
            TreeSet<String> tags = new TreeSet<>();
            for (String tag : sourceRequiredCapabilityTags) {
                tags.add(requireText(tag, "source required capability tag"));
            }
            sourceRequiredCapabilityTags = Collections.unmodifiableSet(tags);
        }

        /**
         * Converts this generated physical host into the existing Stage-20E occurrence input contract.
         *
         * <p>The profile carries no independent position because Stage-20E resolves the same anchor ID
         * back to the authoritative Stage-20C layout. Generation-time validation guarantees that this
         * row and that anchor shared the exact {@link #position()} when the host was created.</p>
         *
         * @return immutable Stage-20E resource-host profile
         */
        public ResourceHostProfile toResourceHostProfile() {
            return new ResourceHostProfile(
                    systemId,
                    anchorId,
                    hostClass.hostClassId(),
                    hostClass.environment(),
                    hostClass.locationTag(),
                    occurrenceAffinityByTypeId,
                    sourceRequiredCapabilityTags);
        }
    }

    /**
     * Immutable result for one world-seed local physical-host materialization pass.
     *
     * @param version exact generator version
     * @param rootSeed authoritative world-generation seed
     * @param hosts generated physical hosts sorted by system and anchor identity
     * @param extractionCatalogFingerprint exact Stage-18 extraction semantics consumed
     */
    public record GenerationResult(
            String version,
            long rootSeed,
            List<PhysicalHost> hosts,
            String extractionCatalogFingerprint) {
        /**
         * Validates and freezes one generated host result.
         *
         * @param version exact generator version
         * @param rootSeed authoritative root seed
         * @param hosts generated physical host rows
         * @param extractionCatalogFingerprint exact Stage-18 extraction catalog fingerprint
         */
        public GenerationResult {
            version = requireText(version, "version");
            Objects.requireNonNull(hosts, "hosts");
            extractionCatalogFingerprint = requireText(
                    extractionCatalogFingerprint, "extractionCatalogFingerprint");
            ArrayList<PhysicalHost> copy = new ArrayList<>(hosts);
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("generated hosts cannot contain null");
            }
            copy.sort(Comparator.comparing(PhysicalHost::systemId).thenComparing(PhysicalHost::anchorId));
            HashSet<String> keys = new HashSet<>();
            for (PhysicalHost host : copy) {
                String key = host.systemId().value() + "\u0000" + host.anchorId();
                if (!keys.add(key)) {
                    throw new IllegalArgumentException("duplicate generated physical host: " + key);
                }
            }
            hosts = List.copyOf(copy);
        }

        /**
         * Projects every generated host into the existing Stage-20E resource-host input contract.
         *
         * @return deterministic immutable Stage-20E host-profile list
         */
        public List<ResourceHostProfile> resourceHostProfiles() {
            return hosts.stream().map(PhysicalHost::toResourceHostProfile).toList();
        }

        /**
         * Finds one generated host by stable owning-system and anchor identity.
         *
         * @param systemId owning system ID
         * @param anchorId stable resource anchor ID
         * @return matching generated host
         * @throws IllegalArgumentException when the host does not exist
         */
        public PhysicalHost host(StarSystemId systemId, String anchorId) {
            Objects.requireNonNull(systemId, "systemId");
            String checkedAnchorId = requireText(anchorId, "anchorId");
            return hosts.stream()
                    .filter(value -> value.systemId().equals(systemId) && value.anchorId().equals(checkedAnchorId))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException(
                            "Unknown generated physical host: " + systemId + "/" + checkedAnchorId));
        }
    }

    /**
     * Generates deterministic physical host semantics for every Stage-20C resource-field point anchor.
     *
     * <p>Layout ordering is irrelevant. The random stream is keyed by stable system/anchor identity,
     * and every selected host class must be backed by at least one Stage-18 natural extraction method.
     * No late-stage economic viability signal participates in selection, so this layer cannot rescue
     * a resource-poor seed.</p>
     *
     * @param rootSeed authoritative world-generation seed shared with the local layouts
     * @param topology authoritative system membership/topology
     * @param localLayouts generated Stage-20C local SI layouts
     * @param extractionCatalog authoritative Stage-18 extraction semantics
     * @return deterministic generated physical host rows and Stage-20E profile projection
     */
    public static GenerationResult generate(
            long rootSeed,
            GalaxyTopology topology,
            List<Stage20LocalInfrastructureLayout> localLayouts,
            Stage18ExtractionCatalog extractionCatalog) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(localLayouts, "localLayouts");
        Stage18ExtractionCatalog checkedExtraction = Objects.requireNonNull(extractionCatalog, "extractionCatalog");

        Map<ExtractionEnvironment, Set<String>> occurrenceTypesByEnvironment =
                naturalOccurrenceTypesByEnvironment(checkedExtraction);
        List<HostClass> supportedClasses = supportedHostClasses(occurrenceTypesByEnvironment);
        if (supportedClasses.isEmpty()) {
            throw new IllegalArgumentException(
                    "Stage-18 extraction catalog exposes no v1 natural physical host environment");
        }

        ArrayList<Stage20LocalInfrastructureLayout> layouts = new ArrayList<>(localLayouts);
        if (layouts.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("localLayouts cannot contain null");
        }
        layouts.sort(Comparator.comparing(Stage20LocalInfrastructureLayout::systemId));
        HashSet<StarSystemId> layoutSystems = new HashSet<>();
        SimulationRandom random = new SimulationRandom(rootSeed);
        ArrayList<PhysicalHost> hosts = new ArrayList<>();

        for (Stage20LocalInfrastructureLayout layout : layouts) {
            if (layout.rootSeed() != rootSeed) {
                throw new IllegalArgumentException(
                        "Stage-20C layout root seed differs from physical-host root seed: " + layout.systemId());
            }
            if (checkedTopology.findSystem(layout.systemId()).isEmpty()) {
                throw new IllegalArgumentException(
                        "Stage-20C layout references system outside topology: " + layout.systemId());
            }
            if (!layoutSystems.add(layout.systemId())) {
                throw new IllegalArgumentException("duplicate Stage-20C layout for system " + layout.systemId());
            }
            for (InfrastructurePlacement placement : layout.placements()) {
                if (placement.kind() != PlacementKind.RESOURCE_FIELD_ANCHOR) {
                    continue;
                }
                StatefulRandom stream = random.createStream(streamName(layout.systemId(), placement.id()));
                HostClass hostClass = supportedClasses.get(unsignedIndex(stream.nextLong(), supportedClasses.size()));
                Map<String, Double> affinities = neutralAffinities(
                        occurrenceTypesByEnvironment.get(hostClass.environment()));
                hosts.add(new PhysicalHost(
                        CURRENT_VERSION,
                        layout.systemId(),
                        placement.id(),
                        hostClass,
                        placement.position(),
                        affinities,
                        Set.of()));
            }
        }

        return new GenerationResult(
                CURRENT_VERSION,
                rootSeed,
                hosts,
                checkedExtraction.getFingerprint());
    }

    private static Map<ExtractionEnvironment, Set<String>> naturalOccurrenceTypesByEnvironment(
            Stage18ExtractionCatalog extractionCatalog) {
        EnumMap<ExtractionEnvironment, TreeSet<String>> mutable = new EnumMap<>(ExtractionEnvironment.class);
        for (ExtractionMethodDefinition method : extractionCatalog.getMethods()) {
            if (method.sourceKind() != SourceKind.NATURAL_OCCURRENCE) {
                continue;
            }
            mutable.computeIfAbsent(method.environment(), ignored -> new TreeSet<>())
                    .addAll(method.compatibleOccurrenceTypeIds());
        }
        EnumMap<ExtractionEnvironment, Set<String>> result = new EnumMap<>(ExtractionEnvironment.class);
        for (Map.Entry<ExtractionEnvironment, TreeSet<String>> entry : mutable.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableSet(new TreeSet<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }

    private static List<HostClass> supportedHostClasses(
            Map<ExtractionEnvironment, Set<String>> occurrenceTypesByEnvironment) {
        ArrayList<HostClass> supported = new ArrayList<>();
        for (HostClass hostClass : HostClass.values()) {
            Set<String> occurrenceTypes = occurrenceTypesByEnvironment.get(hostClass.environment());
            if (occurrenceTypes != null && !occurrenceTypes.isEmpty()) {
                supported.add(hostClass);
            }
        }
        return List.copyOf(supported);
    }

    private static Map<String, Double> neutralAffinities(Set<String> occurrenceTypeIds) {
        Objects.requireNonNull(occurrenceTypeIds, "occurrenceTypeIds");
        TreeMap<String, Double> affinities = new TreeMap<>();
        for (String occurrenceTypeId : occurrenceTypeIds) {
            affinities.put(requireText(occurrenceTypeId, "occurrenceTypeId"), 1d);
        }
        if (affinities.isEmpty()) {
            throw new IllegalArgumentException("natural physical host environment has no compatible occurrences");
        }
        return Collections.unmodifiableMap(affinities);
    }

    private static String streamName(StarSystemId systemId, String anchorId) {
        return RNG_PREFIX + systemId.value() + ".anchor." + requireText(anchorId, "anchorId");
    }

    private static int unsignedIndex(long value, int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException("bound must be positive");
        }
        return (int) Long.remainderUnsigned(value, bound);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}
