package com.spacesim.persistence;

import com.spacesim.content.Stage22FactionProfileCatalog;
import com.spacesim.content.Stage22FactionProfileCatalog.SystemicProfileDefinition;
import com.spacesim.world.FactionIdentityResolver;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bounded Stage-22.1 persistence sidecar binding existing faction identities to immutable profiles.
 *
 * <p>This envelope persists only identity-to-profile selection and the exact semantic fingerprint.
 * Mutable doctrine, diplomacy, fleets, industry, knowledge and recovery remain in their existing
 * world-state authorities.</p>
 *
 * @param envelopeVersion exact sidecar envelope version
 * @param profileSchemaVersion exact Stage-22.1 profile schema version
 * @param catalogVersion semantic profile catalog version
 * @param catalogFingerprint exact lowercase SHA-256 profile catalog fingerprint
 * @param bindings deterministic stable-faction-to-profile bindings
 */
public record Stage22FactionProfileBindingState(
        int envelopeVersion,
        int profileSchemaVersion,
        String catalogVersion,
        String catalogFingerprint,
        List<Binding> bindings) {
    /** Current Stage-22.1 binding envelope version. */
    public static final int CURRENT_VERSION = 1;
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

    /**
     * Validates and deterministically orders one profile-binding sidecar.
     *
     * @param envelopeVersion exact sidecar envelope version
     * @param profileSchemaVersion positive profile schema version
     * @param catalogVersion semantic profile catalog version
     * @param catalogFingerprint exact lowercase SHA-256 catalog fingerprint
     * @param bindings stable faction bindings
     */
    public Stage22FactionProfileBindingState {
        if (envelopeVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-22.1 profile binding envelope: " + envelopeVersion);
        }
        if (profileSchemaVersion <= 0) {
            throw new IllegalArgumentException("Profile schema version must be positive");
        }
        catalogVersion = requireText(catalogVersion, "catalogVersion");
        catalogFingerprint = requireText(catalogFingerprint, "catalogFingerprint");
        if (!SHA_256.matcher(catalogFingerprint).matches()) {
            throw new IllegalArgumentException("Catalog fingerprint must be lowercase SHA-256");
        }
        ArrayList<Binding> ordered = new ArrayList<>(Objects.requireNonNull(bindings, "bindings"));
        ordered.replaceAll(value -> Objects.requireNonNull(value, "profile binding"));
        ordered.sort(Comparator.comparing(Binding::stableFactionId));
        Set<String> stableIds = new HashSet<>();
        Set<Integer> runtimeIds = new HashSet<>();
        Set<String> profileIds = new HashSet<>();
        for (Binding binding : ordered) {
            if (!stableIds.add(binding.stableFactionId())) {
                throw new IllegalArgumentException("Duplicate profile stable faction ID: " + binding.stableFactionId());
            }
            if (!runtimeIds.add(binding.runtimeFactionId())) {
                throw new IllegalArgumentException("Duplicate profile runtime faction ID: " + binding.runtimeFactionId());
            }
            if (!profileIds.add(binding.profileId())) {
                throw new IllegalArgumentException("Duplicate profile ID binding: " + binding.profileId());
            }
        }
        bindings = List.copyOf(ordered);
    }

    /**
     * Captures the exact current catalog binding through the existing stable/runtime identity resolver.
     *
     * @param catalog immutable systemic-profile catalog
     * @param resolver existing authoritative faction identity resolver
     * @return deterministic binding sidecar
     */
    public static Stage22FactionProfileBindingState capture(
            Stage22FactionProfileCatalog catalog,
            FactionIdentityResolver resolver) {
        Stage22FactionProfileCatalog checkedCatalog = Objects.requireNonNull(catalog, "catalog");
        FactionIdentityResolver checkedResolver = Objects.requireNonNull(resolver, "resolver");
        List<Binding> bindings = new ArrayList<>();
        for (SystemicProfileDefinition profile : checkedCatalog.systemicProfiles()) {
            int runtimeId = checkedResolver.runtimeId(profile.stableFactionId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Profile stable faction is absent from the identity authority: "
                                    + profile.stableFactionId()));
            bindings.add(new Binding(
                    profile.stableFactionId(), runtimeId, profile.profileId(), profile.profileVersion()));
        }
        Stage22FactionProfileBindingState result = new Stage22FactionProfileBindingState(
                CURRENT_VERSION,
                checkedCatalog.schemaVersion(),
                checkedCatalog.catalogVersion(),
                checkedCatalog.fingerprint(),
                bindings);
        result.validateAgainst(checkedCatalog, checkedResolver);
        return result;
    }

    /**
     * Rejects schema, fingerprint, profile-version or stable/runtime identity drift.
     *
     * @param catalog current immutable profile catalog
     * @param resolver current authoritative faction identity resolver
     */
    public void validateAgainst(Stage22FactionProfileCatalog catalog, FactionIdentityResolver resolver) {
        Stage22FactionProfileCatalog checkedCatalog = Objects.requireNonNull(catalog, "catalog");
        FactionIdentityResolver checkedResolver = Objects.requireNonNull(resolver, "resolver");
        if (profileSchemaVersion != checkedCatalog.schemaVersion()
                || !catalogVersion.equals(checkedCatalog.catalogVersion())
                || !catalogFingerprint.equals(checkedCatalog.fingerprint())) {
            throw new IllegalArgumentException("Stage-22.1 profile schema/version/fingerprint mismatch");
        }
        if (bindings.size() != checkedCatalog.systemicProfiles().size()) {
            throw new IllegalArgumentException("Stage-22.1 profile binding coverage mismatch");
        }
        for (Binding binding : bindings) {
            SystemicProfileDefinition profile = checkedCatalog.findProfile(binding.profileId());
            if (profile == null
                    || !profile.stableFactionId().equals(binding.stableFactionId())
                    || profile.profileVersion() != binding.profileVersion()) {
                throw new IllegalArgumentException("Stage-22.1 persisted profile binding mismatch: " + binding.profileId());
            }
            int currentRuntimeId = checkedResolver.runtimeId(binding.stableFactionId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Persisted profile faction is absent from identity authority: " + binding.stableFactionId()));
            String currentStableId = checkedResolver.stableId(binding.runtimeFactionId()).orElseThrow(
                    () -> new IllegalArgumentException(
                            "Persisted profile runtime ID is absent from identity authority: "
                                    + binding.runtimeFactionId()));
            if (currentRuntimeId != binding.runtimeFactionId()
                    || !currentStableId.equals(binding.stableFactionId())) {
                throw new IllegalArgumentException(
                        "Stage-22.1 persisted stable/runtime identity mismatch: " + binding.stableFactionId());
            }
        }
    }

    /**
     * One persisted selection of an immutable profile for an existing faction authority.
     *
     * @param stableFactionId authoritative stable faction ID
     * @param runtimeFactionId bounded runtime faction slot
     * @param profileId immutable systemic profile ID
     * @param profileVersion exact positive profile version
     */
    public record Binding(
            String stableFactionId,
            int runtimeFactionId,
            String profileId,
            int profileVersion) {
        /**
         * Validates one bounded profile binding.
         *
         * @param stableFactionId authoritative stable faction ID
         * @param runtimeFactionId non-negative runtime faction slot
         * @param profileId immutable systemic profile ID
         * @param profileVersion exact positive profile version
         */
        public Binding {
            stableFactionId = com.spacesim.world.WorldFactionIdentityState.normalizeStableId(stableFactionId);
            if (runtimeFactionId < 0) {
                throw new IllegalArgumentException("Runtime faction ID must be non-negative");
            }
            profileId = requireText(profileId, "profileId");
            if (!CONTENT_ID.matcher(profileId).matches()) {
                throw new IllegalArgumentException("Profile ID must use lower-case dotted content-ID syntax");
            }
            if (profileVersion <= 0) {
                throw new IllegalArgumentException("Profile version must be positive");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value.strip();
    }
}
