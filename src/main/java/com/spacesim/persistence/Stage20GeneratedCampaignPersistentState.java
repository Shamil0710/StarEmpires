package com.spacesim.persistence;

import com.spacesim.world.Stage20OperationalIndustrialSpecializationPlan.RuntimeBridgeRequirement;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Complete Stage-20K persistence envelope for one already materialized generated campaign.
 *
 * <p>The generation identity is provenance only. Resume always consumes the saved canonical world
 * snapshot and never reruns a newer generator behind the campaign's back. Core simulation,
 * industrial runtime, discovery knowledge and far-local physical state retain their existing typed
 * persistence formats inside this aggregate.</p>
 *
 * @param schemaVersion aggregate Stage-20K schema version
 * @param generationIdentity exact seed/version/profile/content provenance
 * @param materializedWorld canonical saved generated-world and quality rows
 * @param materializationState core ECS plus exact far-local physical kinematics
 * @param industrialState current finite Stage-18 industrial runtime state
 * @param discoveryState observer-local durable generated-world knowledge
 * @param openRuntimeBoundaries explicit work intentionally left for the runtime bridge
 */
@SuppressWarnings("doclint:missing")
public record Stage20GeneratedCampaignPersistentState(
        int schemaVersion,
        GenerationIdentity generationIdentity,
        MaterializedWorldSnapshot materializedWorld,
        Stage20MaterializationPersistentState materializationState,
        Stage18IndustrialState industrialState,
        Stage20DiscoveryPersistentState discoveryState,
        List<OpenRuntimeBoundary> openRuntimeBoundaries) {
    /** Current Stage-20K aggregate persistence schema. */
    public static final int CURRENT_VERSION = 1;
    /** Stable canonical materialized-world row schema. */
    public static final String CURRENT_SNAPSHOT_VERSION = "stage20k.materialized-generated-world.v1";

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final List<OpenRuntimeBoundary> CURRENT_OPEN_BOUNDARIES = List.of(
            OpenRuntimeBoundary.SOURCE_SUPPLY_MATERIALIZATION,
            OpenRuntimeBoundary.FREIGHT_FLEET_MATERIALIZATION,
            OpenRuntimeBoundary.CARGO_ORDER_AND_LOT_MATERIALIZATION,
            OpenRuntimeBoundary.INDUSTRIAL_ENTITY_MATERIALIZATION,
            OpenRuntimeBoundary.LIVE_ARRIVAL_AUTHORITY_INTEGRATION);

    /**
     * Validates exact cross-sidecar identity and freezes deterministic boundary ordering.
     *
     * @param schemaVersion aggregate schema version
     * @param generationIdentity exact generation provenance
     * @param materializedWorld canonical saved world
     * @param materializationState core and far-local state
     * @param industrialState finite industrial state
     * @param discoveryState observer-local knowledge
     * @param openRuntimeBoundaries exact deferred runtime seams
     */
    public Stage20GeneratedCampaignPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20K campaign schema: " + schemaVersion);
        }
        Objects.requireNonNull(generationIdentity, "generationIdentity");
        Objects.requireNonNull(materializedWorld, "materializedWorld");
        Objects.requireNonNull(materializationState, "materializationState");
        Objects.requireNonNull(industrialState, "industrialState");
        Objects.requireNonNull(discoveryState, "discoveryState");
        if (materializationState.gameState().rootSeed() != generationIdentity.worldSeed()) {
            throw new IllegalArgumentException("core GameState root seed differs from generated campaign");
        }
        if (!industrialState.contentFingerprint().equals(generationIdentity.contentFingerprint())) {
            throw new IllegalArgumentException("industrial content fingerprint differs from generated campaign");
        }
        if (discoveryState.rootSeed() != generationIdentity.worldSeed()
                || !discoveryState.worldGenerationVersion().equals(generationIdentity.generatorVersion())
                || !discoveryState.worldFingerprint().equals(materializedWorld.worldFingerprint())) {
            throw new IllegalArgumentException("discovery state is not bound to the exact saved generated world");
        }

        ArrayList<OpenRuntimeBoundary> boundaries = new ArrayList<>(
                Objects.requireNonNull(openRuntimeBoundaries, "openRuntimeBoundaries"));
        if (boundaries.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("openRuntimeBoundaries cannot contain null");
        }
        boundaries.sort(Comparator.comparing(Enum::name));
        if (new HashSet<>(boundaries).size() != boundaries.size()
                || !Set.copyOf(boundaries).equals(Set.copyOf(CURRENT_OPEN_BOUNDARIES))) {
            throw new IllegalArgumentException("Stage-20K must retain every exact open runtime boundary");
        }
        openRuntimeBoundaries = List.copyOf(boundaries);
    }

    /**
     * Chooses the only safe resume behavior against the currently installed generator identity.
     *
     * @param currentIdentity current executable generation provenance
     * @return preserve-snapshot decision plus explicit regeneration policy
     */
    public ResumeDecision resumeAgainst(GenerationIdentity currentIdentity) {
        GenerationIdentity current = Objects.requireNonNull(currentIdentity, "currentIdentity");
        boolean same = generationIdentity.equals(current);
        return new ResumeDecision(
                ResumePolicy.PRESERVE_SAVED_MATERIALIZED_WORLD,
                same,
                same
                        ? RegenerationPolicy.NOT_REQUIRED
                        : RegenerationPolicy.EXPLICIT_MIGRATION_OR_NEW_WORLD_REQUIRED);
    }

    /**
     * Returns the exact Stage-20F bridge requirements represented by the saved boundary set.
     *
     * @return immutable exact four-requirement Stage-20F set
     */
    public Set<RuntimeBridgeRequirement> stage20fRuntimeBridgeRequirements() {
        EnumSet<RuntimeBridgeRequirement> result = EnumSet.noneOf(RuntimeBridgeRequirement.class);
        for (OpenRuntimeBoundary boundary : openRuntimeBoundaries) {
            boundary.stage20fRequirement().ifPresent(result::add);
        }
        return Set.copyOf(result);
    }

    /**
     * Exact generator provenance that identifies a new-world recipe without replacing saved state.
     *
     * @param worldSeed authoritative campaign seed
     * @param generatorVersion resolved generator version
     * @param sourceGeneratorVersion underlying physical generator version
     * @param generationProfile exact representative profile version
     * @param contentFingerprint exact Stage-18 semantic content fingerprint
     */
    public record GenerationIdentity(
            long worldSeed,
            String generatorVersion,
            String sourceGeneratorVersion,
            String generationProfile,
            String contentFingerprint) {
        /**
         * Validates one exact generation identity tuple.
         *
         * @param worldSeed authoritative campaign seed
         * @param generatorVersion resolved generator version
         * @param sourceGeneratorVersion underlying generator version
         * @param generationProfile exact profile version
         * @param contentFingerprint lowercase SHA-256 content fingerprint
         */
        public GenerationIdentity {
            generatorVersion = requireText(generatorVersion, "generatorVersion");
            sourceGeneratorVersion = requireText(sourceGeneratorVersion, "sourceGeneratorVersion");
            generationProfile = requireText(generationProfile, "generationProfile");
            contentFingerprint = requireSha256(contentFingerprint, "contentFingerprint");
        }
    }

    /**
     * One length-delimited canonical row in the saved generated world or quality report.
     *
     * @param domain versioned semantic row domain
     * @param stableId domain-local stable identity
     * @param values ordered exact scalar values
     */
    public record CanonicalRow(
            String domain,
            String stableId,
            List<String> values) implements Comparable<CanonicalRow> {
        /**
         * Validates and freezes one ordered row.
         *
         * @param domain versioned semantic row domain
         * @param stableId domain-local stable identity
         * @param values ordered exact scalar values
         */
        public CanonicalRow {
            domain = requireText(domain, "domain");
            stableId = requireText(stableId, "stableId");
            ArrayList<String> copy = new ArrayList<>(Objects.requireNonNull(values, "values"));
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("canonical row values cannot contain null");
            }
            values = List.copyOf(copy);
        }

        @Override
        public int compareTo(CanonicalRow other) {
            CanonicalRow checked = Objects.requireNonNull(other, "other");
            int domainComparison = domain.compareTo(checked.domain);
            return domainComparison != 0 ? domainComparison : stableId.compareTo(checked.stableId);
        }
    }

    /**
     * Saved authoritative static/dynamic generated-world rows and their exact quality evidence.
     *
     * @param snapshotVersion canonical row-schema version
     * @param worldRows complete canonical materialized-world rows
     * @param qualityRows complete canonical generation-quality rows
     * @param worldFingerprint SHA-256 over canonical world rows
     * @param qualityFingerprint SHA-256 over canonical quality rows
     */
    public record MaterializedWorldSnapshot(
            String snapshotVersion,
            List<CanonicalRow> worldRows,
            List<CanonicalRow> qualityRows,
            String worldFingerprint,
            String qualityFingerprint) {
        /**
         * Canonicalizes rows, rejects unstable identities and verifies both SHA-256 fingerprints.
         *
         * @param snapshotVersion canonical row-schema version
         * @param worldRows complete materialized-world rows
         * @param qualityRows complete quality rows
         * @param worldFingerprint exact world-row fingerprint
         * @param qualityFingerprint exact quality-row fingerprint
         */
        public MaterializedWorldSnapshot {
            snapshotVersion = requireText(snapshotVersion, "snapshotVersion");
            if (!CURRENT_SNAPSHOT_VERSION.equals(snapshotVersion)) {
                throw new IllegalArgumentException("Unsupported Stage-20K materialized-world snapshot version");
            }
            worldRows = canonicalRows(worldRows, "worldRows");
            qualityRows = canonicalRows(qualityRows, "qualityRows");
            if (worldRows.isEmpty() || qualityRows.isEmpty()) {
                throw new IllegalArgumentException("materialized world and quality report must both be non-empty");
            }
            worldFingerprint = requireSha256(worldFingerprint, "worldFingerprint");
            qualityFingerprint = requireSha256(qualityFingerprint, "qualityFingerprint");
            if (!worldFingerprint.equals(fingerprint(worldRows))) {
                throw new IllegalArgumentException("world fingerprint differs from canonical materialized rows");
            }
            if (!qualityFingerprint.equals(fingerprint(qualityRows))) {
                throw new IllegalArgumentException("quality fingerprint differs from canonical quality rows");
            }
        }

        /**
         * Builds a validated snapshot and computes both fingerprints from canonical rows.
         *
         * @param worldRows materialized-world rows in any input order
         * @param qualityRows generation-quality rows in any input order
         * @return sorted validated snapshot with exact SHA-256 fingerprints
         */
        public static MaterializedWorldSnapshot create(
                List<CanonicalRow> worldRows,
                List<CanonicalRow> qualityRows) {
            List<CanonicalRow> world = canonicalRows(worldRows, "worldRows");
            List<CanonicalRow> quality = canonicalRows(qualityRows, "qualityRows");
            return new MaterializedWorldSnapshot(
                    CURRENT_SNAPSHOT_VERSION,
                    world,
                    quality,
                    fingerprint(world),
                    fingerprint(quality));
        }
    }

    /** Runtime bridge boundaries deliberately persisted as open rather than fabricated as state. */
    public enum OpenRuntimeBoundary {
        /** Bind generated finite source/supply authority to live producer state. */
        SOURCE_SUPPLY_MATERIALIZATION(RuntimeBridgeRequirement.SOURCE_SUPPLY_MATERIALIZATION),
        /** Allocate persistent FleetIds for exact retained ownership ordinals. */
        FREIGHT_FLEET_MATERIALIZATION(RuntimeBridgeRequirement.FREIGHT_FLEET_MATERIALIZATION),
        /** Create ordinary cargo lots/orders/deadlines over the retained physical plan. */
        CARGO_ORDER_AND_LOT_MATERIALIZATION(RuntimeBridgeRequirement.CARGO_ORDER_AND_LOT_MATERIALIZATION),
        /** Instantiate planned industrial stations/facilities/storage/yards as runtime entities. */
        INDUSTRIAL_ENTITY_MATERIALIZATION(RuntimeBridgeRequirement.INDUSTRIAL_ENTITY_MATERIALIZATION),
        /** Apply persisted Stage-20D arrival endpoint position/velocity to the live transit authority. */
        LIVE_ARRIVAL_AUTHORITY_INTEGRATION(null);

        private final RuntimeBridgeRequirement stage20fRequirement;

        OpenRuntimeBoundary(RuntimeBridgeRequirement stage20fRequirement) {
            this.stage20fRequirement = stage20fRequirement;
        }

        private java.util.Optional<RuntimeBridgeRequirement> stage20fRequirement() {
            return java.util.Optional.ofNullable(stage20fRequirement);
        }
    }

    /** Resume always preserves saved authoritative rows, independent of installed generator version. */
    public enum ResumePolicy {
        /** Load the exact saved world and do not invoke generation. */
        PRESERVE_SAVED_MATERIALIZED_WORLD
    }

    /** Explicit policy required before any attempt to replace saved generated authority. */
    public enum RegenerationPolicy {
        /** Installed generation identity matches; normal resume still uses the saved snapshot. */
        NOT_REQUIRED,
        /** A generator/profile/content change requires explicit migration or creation of a new world. */
        EXPLICIT_MIGRATION_OR_NEW_WORLD_REQUIRED
    }

    /**
     * Machine-readable resume decision.
     *
     * @param resumePolicy mandatory saved-world resume behavior
     * @param generationIdentityMatches whether installed and saved tuples are exact
     * @param regenerationPolicy policy for any requested replacement
     */
    public record ResumeDecision(
            ResumePolicy resumePolicy,
            boolean generationIdentityMatches,
            RegenerationPolicy regenerationPolicy) {
        /**
         * Validates one coherent resume decision.
         *
         * @param resumePolicy mandatory saved-world resume behavior
         * @param generationIdentityMatches whether tuples are exact
         * @param regenerationPolicy explicit replacement policy
         */
        public ResumeDecision {
            Objects.requireNonNull(resumePolicy, "resumePolicy");
            Objects.requireNonNull(regenerationPolicy, "regenerationPolicy");
            if (generationIdentityMatches != (regenerationPolicy == RegenerationPolicy.NOT_REQUIRED)) {
                throw new IllegalArgumentException("identity match and regeneration policy differ");
            }
        }
    }

    private static List<CanonicalRow> canonicalRows(List<CanonicalRow> rows, String field) {
        ArrayList<CanonicalRow> copy = new ArrayList<>(Objects.requireNonNull(rows, field));
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " cannot contain null");
        }
        copy.sort(Comparator.naturalOrder());
        HashSet<String> keys = new HashSet<>();
        for (CanonicalRow row : copy) {
            if (!keys.add(row.domain() + '\u0000' + row.stableId())) {
                throw new IllegalArgumentException("duplicate canonical row identity: "
                        + row.domain() + "/" + row.stableId());
            }
        }
        return List.copyOf(copy);
    }

    private static String fingerprint(List<CanonicalRow> rows) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (DataOutputStream output = new DataOutputStream(bytes)) {
                output.writeInt(rows.size());
                for (CanonicalRow row : rows) {
                    writeFingerprintText(output, row.domain());
                    writeFingerprintText(output, row.stableId());
                    output.writeInt(row.values().size());
                    for (String value : row.values()) {
                        writeFingerprintText(output, value);
                    }
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest(bytes.toByteArray()));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        } catch (IOException exception) {
            throw new IllegalStateException("Unexpected in-memory fingerprint encoding error", exception);
        }
    }

    private static void writeFingerprintText(DataOutputStream output, String value) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        output.writeInt(bytes.length);
        output.write(bytes);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
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
