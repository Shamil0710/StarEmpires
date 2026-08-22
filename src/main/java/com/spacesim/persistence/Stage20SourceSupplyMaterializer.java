package com.spacesim.persistence;

import com.spacesim.content.Stage18ExtractionCatalog.ExtractionEnvironment;
import com.spacesim.content.Stage18ExtractionCatalog.SourceKind;
import com.spacesim.economy.Stage18ExtractionRuntime.PhysicalSourceState;
import com.spacesim.persistence.Stage18IndustrialState.PhysicalSourceSnapshot;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Materializes saved Stage-20 generated natural-resource authority into live Stage-18 source state.
 *
 * <p>This is a load/runtime bridge, not a generator. It consumes only the canonical Stage-20K saved
 * world plus the matching Stage-18 industrial snapshot. Generated resource rows provide immutable
 * identity/provenance while {@link Stage18IndustrialState} provides any already-consumed finite
 * reserve. The bridge never invokes world generation and never creates cargo or inventory.</p>
 *
 * <p>The resulting mutable {@link PhysicalSourceState} instances are the ordinary inputs consumed by
 * {@code Stage18ExtractionRuntime}. Repeated reconstruction of the same saved state therefore keeps
 * the same logical source identities and remaining reserves without introducing a parallel
 * extraction model.</p>
 */
public final class Stage20SourceSupplyMaterializer {
    /** Stable Stage-20.5A source-materialization contract version. */
    public static final String CURRENT_VERSION = "stage20_5.source-supply-materialization.v1";

    private static final String RESOURCE_DOMAIN = "RESOURCE_OCCURRENCE";
    private static final String EXTRACTION_SITE_DOMAIN = "INITIAL_EXTRACTION_SITE";
    private static final int RESOURCE_PREFIX_VALUE_COUNT = 16;
    private static final int EXTRACTION_SITE_VALUE_COUNT = 6;

    private Stage20SourceSupplyMaterializer() {
        throw new AssertionError("No instances");
    }

    /**
     * Restores one runtime source registry from an already-saved generated campaign.
     *
     * <p>A saved natural source snapshot must exactly match the immutable generated authority and
     * the remaining reserve encoded into the canonical resource row. Missing source snapshots are
     * accepted only for never-consumed generated occurrences whose saved remaining reserve still
     * equals the generated initial reserve. Extra saved natural sources are rejected. Salvage
     * streams remain owned by their existing Stage-18H/Stage-20H path and are not materialized here.</p>
     *
     * @param saved exact Stage-20K saved campaign state
     * @return deterministic live generated-source registry
     */
    public static MaterializedSourceRegistry materialize(Stage20GeneratedCampaignPersistentState saved) {
        Stage20GeneratedCampaignPersistentState state = Objects.requireNonNull(saved, "saved");
        if (!state.openRuntimeBoundaries().contains(OpenRuntimeBoundary.SOURCE_SUPPLY_MATERIALIZATION)) {
            throw new IllegalArgumentException("saved campaign does not retain source-supply materialization boundary");
        }
        if (!state.generationIdentity().contentFingerprint().equals(Stage18IndustrialContentFingerprint.current())) {
            throw new IllegalArgumentException("saved industrial content differs from installed Stage-18 runtime");
        }

        TreeMap<String, SourceAuthority> authorityById = new TreeMap<>();
        TreeMap<String, InitialExtractionSite> sitesById = new TreeMap<>();
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (RESOURCE_DOMAIN.equals(row.domain())) {
                SourceAuthority authority = parseResource(row);
                if (authorityById.putIfAbsent(authority.sourceId(), authority) != null) {
                    throw new IllegalArgumentException("duplicate canonical generated source: " + authority.sourceId());
                }
            } else if (EXTRACTION_SITE_DOMAIN.equals(row.domain())) {
                InitialExtractionSite site = parseExtractionSite(row);
                if (sitesById.putIfAbsent(site.siteId(), site) != null) {
                    throw new IllegalArgumentException("duplicate canonical extraction site: " + site.siteId());
                }
            }
        }

        TreeMap<String, PhysicalSourceSnapshot> persistedNaturalSources = new TreeMap<>();
        for (PhysicalSourceSnapshot snapshot : state.industrialState().sources()) {
            if (snapshot.sourceKind() != SourceKind.NATURAL_OCCURRENCE) {
                continue;
            }
            if (persistedNaturalSources.putIfAbsent(snapshot.sourceId(), snapshot) != null) {
                throw new IllegalArgumentException("duplicate persisted natural source: " + snapshot.sourceId());
            }
        }

        TreeMap<String, MaterializedSource> materialized = new TreeMap<>();
        for (SourceAuthority authority : authorityById.values()) {
            PhysicalSourceSnapshot persisted = persistedNaturalSources.remove(authority.sourceId());
            PhysicalSourceState runtimeState;
            if (persisted == null) {
                if (Double.compare(authority.remainingAccessibleMassKg(), authority.initialAccessibleMassKg()) != 0) {
                    throw new IllegalArgumentException(
                            "depleted canonical source has no matching industrial snapshot: " + authority.sourceId());
                }
                runtimeState = authority.restore();
            } else {
                requireExactPersistedSource(authority, persisted);
                runtimeState = persisted.restore();
            }
            materialized.put(authority.sourceId(), authority.materialize(runtimeState));
        }
        if (!persistedNaturalSources.isEmpty()) {
            throw new IllegalArgumentException(
                    "saved industrial state contains natural source absent from canonical generated world: "
                            + persistedNaturalSources.firstKey());
        }

        HashSet<String> siteSourceIds = new HashSet<>();
        for (InitialExtractionSite site : sitesById.values()) {
            MaterializedSource source = materialized.get(site.sourceId());
            if (source == null) {
                throw new IllegalArgumentException("extraction site references unknown generated source: " + site.siteId());
            }
            if (!source.systemId().equals(site.systemId()) || !source.hostAnchorId().equals(site.hostAnchorId())) {
                throw new IllegalArgumentException("extraction site differs from source system/host: " + site.siteId());
            }
            if (!siteSourceIds.add(site.sourceId())) {
                throw new IllegalArgumentException("generated source has multiple initial extraction sites: " + site.sourceId());
            }
        }

        return new MaterializedSourceRegistry(
                state.generationIdentity().worldSeed(),
                state.generationIdentity().generatorVersion(),
                state.materializedWorld().worldFingerprint(),
                materialized,
                sitesById);
    }

    private static SourceAuthority parseResource(CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() < RESOURCE_PREFIX_VALUE_COUNT) {
            throw malformed(row, "resource row is shorter than the v1 fixed prefix");
        }
        int tagCount = parseNonNegativeInt(values.get(15), row, "required capability tag count");
        if (values.size() != RESOURCE_PREFIX_VALUE_COUNT + tagCount) {
            throw malformed(row, "resource row capability-tag count differs from encoded values");
        }
        TreeSet<String> tags = new TreeSet<>();
        for (int index = 0; index < tagCount; index++) {
            String tag = requireText(values.get(RESOURCE_PREFIX_VALUE_COUNT + index), "required capability tag");
            if (!tags.add(tag)) {
                throw malformed(row, "duplicate required capability tag: " + tag);
            }
        }
        double generationScore = parseDouble(values.get(10), row, "generationScore");
        if (generationScore < 0d || generationScore > 1d) {
            throw malformed(row, "generationScore must be in [0,1]");
        }
        return new SourceAuthority(
                row.stableId(),
                new StarSystemId(parsePositiveLong(values.get(0), row, "systemId")),
                values.get(1),
                values.get(2),
                new LocalPhysicalPosition(
                        parseLong(values.get(3), row, "cellX"),
                        parseLong(values.get(4), row, "cellY"),
                        parseDouble(values.get(5), row, "offsetXM"),
                        parseDouble(values.get(6), row, "offsetYM")),
                values.get(7),
                parseEnvironment(values.get(8), row),
                values.get(9),
                generationScore,
                parseDouble(values.get(11), row, "initialAccessibleMassKg"),
                parseDouble(values.get(12), row, "remainingAccessibleMassKg"),
                parseDouble(values.get(13), row, "gradeFraction"),
                parseDouble(values.get(14), row, "sourceRecoveryFraction"),
                tags);
    }

    private static InitialExtractionSite parseExtractionSite(CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() != EXTRACTION_SITE_VALUE_COUNT) {
            throw malformed(row, "initial extraction site row must contain exactly six values");
        }
        return new InitialExtractionSite(
                row.stableId(),
                values.get(0),
                new StarSystemId(parsePositiveLong(values.get(1), row, "systemId")),
                values.get(2),
                values.get(3),
                values.get(4),
                values.get(5));
    }

    private static ExtractionEnvironment parseEnvironment(String value, CanonicalRow row) {
        try {
            return ExtractionEnvironment.valueOf(requireText(value, "environment"));
        } catch (IllegalArgumentException exception) {
            throw malformed(row, "unknown extraction environment: " + value, exception);
        }
    }

    private static void requireExactPersistedSource(
            SourceAuthority authority,
            PhysicalSourceSnapshot persisted) {
        if (persisted.sourceKind() != SourceKind.NATURAL_OCCURRENCE
                || !persisted.sourceId().equals(authority.sourceId())
                || !persisted.sourceTypeId().equals(authority.occurrenceTypeId())
                || persisted.environment() != authority.environment()
                || !persisted.outputCommodityId().equals(authority.outputCommodityId())
                || Double.compare(persisted.initialAccessibleMassKg(), authority.initialAccessibleMassKg()) != 0
                || Double.compare(persisted.remainingAccessibleMassKg(), authority.remainingAccessibleMassKg()) != 0
                || Double.compare(persisted.gradeFraction(), authority.gradeFraction()) != 0
                || Double.compare(persisted.sourceRecoveryFraction(), authority.sourceRecoveryFraction()) != 0
                || !persisted.requiredCapabilityTags().equals(authority.requiredCapabilityTags())) {
            throw new IllegalArgumentException(
                    "saved natural source differs from canonical generated authority: " + authority.sourceId());
        }
    }

    private static long parsePositiveLong(String value, CanonicalRow row, String field) {
        long parsed = parseLong(value, row, field);
        if (parsed <= 0L) {
            throw malformed(row, field + " must be positive");
        }
        return parsed;
    }

    private static long parseLong(String value, CanonicalRow row, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw malformed(row, field + " is not a valid long", exception);
        }
    }

    private static int parseNonNegativeInt(String value, CanonicalRow row, String field) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw malformed(row, field + " must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw malformed(row, field + " is not a valid integer", exception);
        }
    }

    private static double parseDouble(String value, CanonicalRow row, String field) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw malformed(row, field + " must be finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw malformed(row, field + " is not a valid finite double", exception);
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static IllegalArgumentException malformed(CanonicalRow row, String detail) {
        return new IllegalArgumentException(
                "Malformed " + row.domain() + '/' + row.stableId() + ": " + detail);
    }

    private static IllegalArgumentException malformed(
            CanonicalRow row, String detail, RuntimeException cause) {
        return new IllegalArgumentException(
                "Malformed " + row.domain() + '/' + row.stableId() + ": " + detail,
                cause);
    }

    private record SourceAuthority(
            String sourceId,
            StarSystemId systemId,
            String hostAnchorId,
            String hostClassId,
            LocalPhysicalPosition position,
            String occurrenceTypeId,
            ExtractionEnvironment environment,
            String outputCommodityId,
            double generationScore,
            double initialAccessibleMassKg,
            double remainingAccessibleMassKg,
            double gradeFraction,
            double sourceRecoveryFraction,
            Set<String> requiredCapabilityTags) {
        private SourceAuthority {
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            hostAnchorId = requireText(hostAnchorId, "hostAnchorId");
            hostClassId = requireText(hostClassId, "hostClassId");
            Objects.requireNonNull(position, "position");
            occurrenceTypeId = requireText(occurrenceTypeId, "occurrenceTypeId");
            Objects.requireNonNull(environment, "environment");
            outputCommodityId = requireText(outputCommodityId, "outputCommodityId");
            if (!Double.isFinite(generationScore) || generationScore < 0d || generationScore > 1d) {
                throw new IllegalArgumentException("generationScore must be in [0,1]");
            }
            requiredCapabilityTags = Collections.unmodifiableSet(new TreeSet<>(
                    Objects.requireNonNull(requiredCapabilityTags, "requiredCapabilityTags")));
            PhysicalSourceState validation = new PhysicalSourceState(
                    sourceId,
                    SourceKind.NATURAL_OCCURRENCE,
                    occurrenceTypeId,
                    environment,
                    outputCommodityId,
                    initialAccessibleMassKg,
                    remainingAccessibleMassKg,
                    gradeFraction,
                    sourceRecoveryFraction,
                    requiredCapabilityTags);
            if (Double.compare(validation.remainingAccessibleMassKg(), remainingAccessibleMassKg) != 0) {
                throw new IllegalArgumentException("remaining source reserve was not preserved exactly");
            }
        }

        private PhysicalSourceState restore() {
            return new PhysicalSourceState(
                    sourceId,
                    SourceKind.NATURAL_OCCURRENCE,
                    occurrenceTypeId,
                    environment,
                    outputCommodityId,
                    initialAccessibleMassKg,
                    remainingAccessibleMassKg,
                    gradeFraction,
                    sourceRecoveryFraction,
                    requiredCapabilityTags);
        }

        private MaterializedSource materialize(PhysicalSourceState sourceState) {
            return new MaterializedSource(
                    sourceId, systemId, hostAnchorId, hostClassId, position, generationScore, sourceState);
        }
    }

    /**
     * One live generated natural source plus immutable physical-world provenance.
     *
     * @param sourceId stable generated source identity
     * @param systemId owning system
     * @param hostAnchorId owning physical resource-field anchor
     * @param hostClassId generated physical host classifier
     * @param position authoritative local-system SI position
     * @param generationScore retained generation evidence in {@code [0,1]}
     * @param sourceState mutable finite Stage-18 extraction source
     */
    public record MaterializedSource(
            String sourceId,
            StarSystemId systemId,
            String hostAnchorId,
            String hostClassId,
            LocalPhysicalPosition position,
            double generationScore,
            PhysicalSourceState sourceState) {
        /** Validates one live generated-source binding. */
        public MaterializedSource {
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            hostAnchorId = requireText(hostAnchorId, "hostAnchorId");
            hostClassId = requireText(hostClassId, "hostClassId");
            Objects.requireNonNull(position, "position");
            if (!Double.isFinite(generationScore) || generationScore < 0d || generationScore > 1d) {
                throw new IllegalArgumentException("generationScore must be in [0,1]");
            }
            Objects.requireNonNull(sourceState, "sourceState");
            if (!sourceState.sourceId().equals(sourceId)
                    || sourceState.sourceKind() != SourceKind.NATURAL_OCCURRENCE) {
                throw new IllegalArgumentException("runtime source state differs from generated source identity/kind");
            }
        }
    }

    /**
     * Immutable saved installation binding for one generated initial extraction site.
     *
     * @param siteId stable generated site identity
     * @param sourceId generated natural source served by the site
     * @param systemId owning star system
     * @param hostAnchorId owning physical resource-field anchor
     * @param locationTag Stage-18 facility location tag
     * @param facilityDefinitionId exact installed-facility definition identity
     * @param extractionMethodId exact Stage-18 extraction method identity
     */
    public record InitialExtractionSite(
            String siteId,
            String sourceId,
            StarSystemId systemId,
            String hostAnchorId,
            String locationTag,
            String facilityDefinitionId,
            String extractionMethodId) {
        /** Validates one saved extraction-site binding. */
        public InitialExtractionSite {
            siteId = requireText(siteId, "siteId");
            sourceId = requireText(sourceId, "sourceId");
            Objects.requireNonNull(systemId, "systemId");
            hostAnchorId = requireText(hostAnchorId, "hostAnchorId");
            locationTag = requireText(locationTag, "locationTag");
            facilityDefinitionId = requireText(facilityDefinitionId, "facilityDefinitionId");
            extractionMethodId = requireText(extractionMethodId, "extractionMethodId");
        }
    }

    /** Live deterministic registry of generated natural sources restored from one saved campaign. */
    public static final class MaterializedSourceRegistry {
        private final long rootSeed;
        private final String generatorVersion;
        private final String worldFingerprint;
        private final Map<String, MaterializedSource> sourcesById;
        private final Map<String, InitialExtractionSite> sitesById;
        private final Map<String, InitialExtractionSite> siteBySourceId;

        private MaterializedSourceRegistry(
                long rootSeed,
                String generatorVersion,
                String worldFingerprint,
                Map<String, MaterializedSource> sourcesById,
                Map<String, InitialExtractionSite> sitesById) {
            this.rootSeed = rootSeed;
            this.generatorVersion = requireText(generatorVersion, "generatorVersion");
            this.worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
            this.sourcesById = Collections.unmodifiableMap(new TreeMap<>(sourcesById));
            this.sitesById = Collections.unmodifiableMap(new TreeMap<>(sitesById));
            TreeMap<String, InitialExtractionSite> bySource = new TreeMap<>();
            for (InitialExtractionSite site : this.sitesById.values()) {
                if (bySource.putIfAbsent(site.sourceId(), site) != null) {
                    throw new IllegalArgumentException("generated source has multiple initial extraction sites: "
                            + site.sourceId());
                }
            }
            this.siteBySourceId = Collections.unmodifiableMap(bySource);
        }

        /** @return Stage-20.5A materialization contract version */
        public String version() {
            return CURRENT_VERSION;
        }

        /** @return exact saved world root seed */
        public long rootSeed() {
            return rootSeed;
        }

        /** @return exact saved resolved-generator version */
        public String generatorVersion() {
            return generatorVersion;
        }

        /** @return exact saved canonical world fingerprint */
        public String worldFingerprint() {
            return worldFingerprint;
        }

        /** @return stable source-ID ordered live generated sources */
        public List<MaterializedSource> sources() {
            return List.copyOf(sourcesById.values());
        }

        /** @return stable site-ID ordered initial extraction-site bindings */
        public List<InitialExtractionSite> initialExtractionSites() {
            return List.copyOf(sitesById.values());
        }

        /**
         * Finds one live generated source.
         *
         * @param sourceId stable generated source ID
         * @return matching live source
         */
        public MaterializedSource source(String sourceId) {
            MaterializedSource source = sourcesById.get(requireText(sourceId, "sourceId"));
            if (source == null) {
                throw new IllegalArgumentException("Unknown materialized generated source: " + sourceId);
            }
            return source;
        }

        /**
         * Returns the initial generated extraction site serving a source, when one exists.
         *
         * @param sourceId stable generated source ID
         * @return optional exact initial-site binding
         */
        public Optional<InitialExtractionSite> initialSiteForSource(String sourceId) {
            String checked = requireText(sourceId, "sourceId");
            if (!sourcesById.containsKey(checked)) {
                throw new IllegalArgumentException("Unknown materialized generated source: " + checked);
            }
            return Optional.ofNullable(siteBySourceId.get(checked));
        }

        /**
         * Captures the current mutable finite reserves for Stage-18 persistence integration.
         *
         * @return stable source-ID ordered snapshots
         */
        public List<PhysicalSourceSnapshot> captureSourceSnapshots() {
            ArrayList<PhysicalSourceSnapshot> snapshots = new ArrayList<>(sourcesById.size());
            for (MaterializedSource source : sourcesById.values()) {
                snapshots.add(PhysicalSourceSnapshot.capture(source.sourceState()));
            }
            return List.copyOf(snapshots);
        }
    }
}