package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable Stage-22.2 component/hull/facility production-manifest and support-endurance contract.
 *
 * <p>The manifest contains references only. Physical definitions remain owned by the Stage-17.5
 * engineering catalog and Stage-18 manufacturing/facility/shipyard catalogs. Endurance values remain
 * owned by Stage-20 calibration. This catalog exists solely to prove that later authored faction data
 * can link those authorities without inventing a second production or logistics truth.</p>
 */
public final class Stage22CoreProductionManifestCatalog {
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

    private final int schemaVersion;
    private final String catalogVersion;
    private final List<ProductionManifestDefinition> productionManifests;
    private final List<SupportEnduranceRequirement> supportEnduranceRequirements;
    private final Map<String, ProductionManifestDefinition> manifestsById;
    private final Map<String, ProductionManifestDefinition> manifestsByFitId;
    private final Map<String, SupportEnduranceRequirement> enduranceByRoleId;
    private final String fingerprint;

    Stage22CoreProductionManifestCatalog(
            int schemaVersion,
            String catalogVersion,
            List<ProductionManifestDefinition> productionManifests,
            List<SupportEnduranceRequirement> supportEnduranceRequirements) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Stage-22.2 production-manifest schema must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.catalogVersion = requireContentId(catalogVersion, "catalogVersion");
        this.productionManifests = sorted(productionManifests, ProductionManifestDefinition::id);
        this.supportEnduranceRequirements = sorted(
                supportEnduranceRequirements, SupportEnduranceRequirement::roleId);
        this.manifestsById = index(this.productionManifests, ProductionManifestDefinition::id, "production manifest");
        this.manifestsByFitId = index(this.productionManifests, ProductionManifestDefinition::fitId, "production fit");
        this.enduranceByRoleId = index(
                this.supportEnduranceRequirements, SupportEnduranceRequirement::roleId, "support endurance role");
        this.fingerprint = computeFingerprint();
    }

    /** @return exact schema version */
    public int schemaVersion() { return schemaVersion; }

    /** @return semantic catalog version */
    public String catalogVersion() { return catalogVersion; }

    /** @return deterministic physical-production reference manifests */
    public List<ProductionManifestDefinition> productionManifests() { return productionManifests; }

    /** @return deterministic support-endurance requirements */
    public List<SupportEnduranceRequirement> supportEnduranceRequirements() { return supportEnduranceRequirements; }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String fingerprint() { return fingerprint; }

    /**
     * Finds one production manifest by stable manifest ID.
     *
     * @param id stable production-manifest ID
     * @return production manifest, or {@code null}
     */
    public ProductionManifestDefinition findManifest(String id) { return manifestsById.get(id); }

    /**
     * Finds the unique production manifest for one exact engineering fit.
     *
     * @param fitId exact engineering fit ID
     * @return production manifest, or {@code null}
     */
    public ProductionManifestDefinition findManifestForFit(String fitId) { return manifestsByFitId.get(fitId); }

    /**
     * Finds one support-endurance requirement by common role ID.
     *
     * @param roleId common support role ID
     * @return support endurance requirement, or {@code null}
     */
    public SupportEnduranceRequirement findEnduranceRequirement(String roleId) { return enduranceByRoleId.get(roleId); }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(4096);
        out.append("schema|").append(schemaVersion).append('|').append(catalogVersion).append('\n');
        for (ProductionManifestDefinition manifest : productionManifests) {
            out.append("manifest|").append(manifest.id()).append('|').append(manifest.fitId()).append('|')
                    .append(manifest.hullId()).append('|').append(manifest.shipyardId()).append('|')
                    .append(String.join(",", manifest.componentIds())).append('|')
                    .append(String.join(",", manifest.requiredFacilityIds())).append('|')
                    .append(manifest.contentMaturity()).append('|')
                    .append(manifest.semanticIntent()).append('\n');
        }
        for (SupportEnduranceRequirement endurance : supportEnduranceRequirements) {
            out.append("endurance|").append(endurance.roleId()).append('|')
                    .append(endurance.referenceId()).append('|')
                    .append(Double.toHexString(endurance.minimumMissionEnduranceS())).append('|')
                    .append(endurance.semanticReason()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static <T> List<T> sorted(List<T> values, Function<T, String> key) {
        ArrayList<T> copy = new ArrayList<>(Objects.requireNonNull(values, "catalog values"));
        copy.replaceAll(value -> Objects.requireNonNull(value, "catalog value"));
        copy.sort(Comparator.comparing(key));
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key, String label) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + id);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static String requireContentId(String value, String label) {
        String checked = requireText(value, label);
        if (!CONTENT_ID.matcher(checked).matches()) {
            throw new IllegalArgumentException(label + " must use lower-case dotted content-ID syntax: " + checked);
        }
        return checked;
    }

    static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return checked;
    }

    static List<String> distinctContentIds(List<String> values, String label) {
        TreeSet<String> ordered = new TreeSet<>();
        for (String value : Objects.requireNonNull(values, label + " not set")) {
            String checked = requireContentId(value, label + " entry");
            if (!ordered.add(checked)) {
                throw new IllegalArgumentException("Duplicate " + label + " entry: " + checked);
            }
        }
        if (ordered.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return List.copyOf(ordered);
    }

    /**
     * Exact reference manifest for one already legal physical engineering fit.
     *
     * @param id stable manifest ID
     * @param fitId exact engineering fit ID
     * @param hullId exact hull ID used by the fit and Stage-18 physical hull profile
     * @param componentIds exact installed module/component IDs required by the fit
     * @param shipyardId Stage-18 shipyard able to build/service the hull
     * @param requiredFacilityIds Stage-18 facility definitions required by that shipyard
     * @param contentMaturity shared Stage-22.0 governed authoring maturity
     * @param semanticIntent authoring rationale; never a gameplay modifier
     */
    public record ProductionManifestDefinition(
            String id,
            String fitId,
            String hullId,
            List<String> componentIds,
            String shipyardId,
            List<String> requiredFacilityIds,
            ContentMaturity contentMaturity,
            String semanticIntent) {
        /**
         * Validates one immutable reference manifest.
         *
         * @param id stable manifest ID
         * @param fitId exact engineering fit ID
         * @param hullId exact physical hull ID
         * @param componentIds exact installed component/module IDs
         * @param shipyardId Stage-18 shipyard ID
         * @param requiredFacilityIds Stage-18 required facility IDs
         * @param contentMaturity governed Stage-22 content maturity
         * @param semanticIntent non-authoritative authoring rationale
         */
        public ProductionManifestDefinition {
            id = requireContentId(id, "production manifest id");
            fitId = requireContentId(fitId, "production fitId");
            hullId = requireContentId(hullId, "production hullId");
            componentIds = distinctContentIds(componentIds, "componentIds");
            shipyardId = requireContentId(shipyardId, "shipyardId");
            requiredFacilityIds = distinctContentIds(requiredFacilityIds, "requiredFacilityIds");
            contentMaturity = Objects.requireNonNull(contentMaturity, "contentMaturity not set");
            semanticIntent = requireText(semanticIntent, "production semanticIntent");
        }
    }

    /**
     * Explicit common support-role endurance floor checked against Stage-20 accepted calibration.
     *
     * @param roleId common support role ID
     * @param referenceId Stage-20 representative endurance reference ID
     * @param minimumMissionEnduranceS required mission-stores endurance in seconds
     * @param semanticReason why this reference is a valid conservative planning analogue
     */
    public record SupportEnduranceRequirement(
            String roleId,
            String referenceId,
            double minimumMissionEnduranceS,
            String semanticReason) {
        /**
         * Validates one support-role endurance requirement.
         *
         * @param roleId common support role ID
         * @param referenceId Stage-20 representative endurance reference ID
         * @param minimumMissionEnduranceS required mission-stores endurance in seconds
         * @param semanticReason authoring rationale for the selected reference/floor
         */
        public SupportEnduranceRequirement {
            roleId = requireContentId(roleId, "support roleId");
            referenceId = requireText(referenceId, "support endurance referenceId");
            if (!Double.isFinite(minimumMissionEnduranceS) || minimumMissionEnduranceS <= 0d) {
                throw new IllegalArgumentException("minimumMissionEnduranceS must be finite and positive");
            }
            semanticReason = requireText(semanticReason, "support endurance semanticReason");
        }
    }
}
