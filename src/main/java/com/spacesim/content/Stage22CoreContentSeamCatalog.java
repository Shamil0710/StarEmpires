package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable faction-neutral Stage-22.2 authoring seam for core production content.
 *
 * <p>The catalog describes role intent and links already authoritative engineering, production,
 * presentation and diagnostic seams. It stores no mutable fleet, economy, mission, faction,
 * manufacturing or asset state. Empire and Industrial Union packages consume this contract later;
 * this common catalog itself must remain free of faction-specific package policy.</p>
 */
public final class Stage22CoreContentSeamCatalog {
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");
    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final int schemaVersion;
    private final String catalogVersion;
    private final List<RoleDefinition> roles;
    private final List<MissionProfileDefinition> missionProfiles;
    private final List<LineageDefinition> lineages;
    private final List<VisualBindingDefinition> visualBindings;
    private final List<LocalizationRuleDefinition> localizationRules;
    private final List<TelemetryHookDefinition> telemetryHooks;
    private final List<AuthoringTemplateDefinition> authoringTemplates;
    private final Map<String, RoleDefinition> rolesById;
    private final Map<String, MissionProfileDefinition> missionsById;
    private final Map<String, LineageDefinition> lineagesById;
    private final Map<String, VisualBindingDefinition> visualsById;
    private final Map<String, LocalizationRuleDefinition> localizationById;
    private final Map<String, TelemetryHookDefinition> telemetryById;
    private final Map<String, AuthoringTemplateDefinition> templatesById;
    private final String fingerprint;

    Stage22CoreContentSeamCatalog(
            int schemaVersion,
            String catalogVersion,
            List<RoleDefinition> roles,
            List<MissionProfileDefinition> missionProfiles,
            List<LineageDefinition> lineages,
            List<VisualBindingDefinition> visualBindings,
            List<LocalizationRuleDefinition> localizationRules,
            List<TelemetryHookDefinition> telemetryHooks,
            List<AuthoringTemplateDefinition> authoringTemplates) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Stage-22.2 schema version must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.catalogVersion = requireContentId(catalogVersion, "catalogVersion");
        this.roles = sorted(roles, RoleDefinition::id);
        this.missionProfiles = sorted(missionProfiles, MissionProfileDefinition::id);
        this.lineages = sorted(lineages, LineageDefinition::id);
        this.visualBindings = sorted(visualBindings, VisualBindingDefinition::id);
        this.localizationRules = sorted(localizationRules, LocalizationRuleDefinition::id);
        this.telemetryHooks = sorted(telemetryHooks, TelemetryHookDefinition::id);
        this.authoringTemplates = sorted(authoringTemplates, AuthoringTemplateDefinition::id);
        this.rolesById = index(this.roles, RoleDefinition::id, "role");
        this.missionsById = index(this.missionProfiles, MissionProfileDefinition::id, "mission profile");
        this.lineagesById = index(this.lineages, LineageDefinition::id, "lineage");
        this.visualsById = index(this.visualBindings, VisualBindingDefinition::id, "visual binding");
        this.localizationById = index(this.localizationRules, LocalizationRuleDefinition::id, "localization rule");
        this.telemetryById = index(this.telemetryHooks, TelemetryHookDefinition::id, "telemetry hook");
        this.templatesById = index(this.authoringTemplates, AuthoringTemplateDefinition::id, "authoring template");
        this.fingerprint = computeFingerprint();
    }

    /** @return exact Stage-22.2 schema version */
    public int schemaVersion() { return schemaVersion; }

    /** @return stable semantic catalog version */
    public String catalogVersion() { return catalogVersion; }

    /** @return complete deterministic common role taxonomy */
    public List<RoleDefinition> roles() { return roles; }

    /** @return deterministic data-only mission profiles */
    public List<MissionProfileDefinition> missionProfiles() { return missionProfiles; }

    /** @return deterministic manufacturer/design/procurement lineage templates */
    public List<LineageDefinition> lineages() { return lineages; }

    /** @return deterministic fit-to-visual authoring bindings */
    public List<VisualBindingDefinition> visualBindings() { return visualBindings; }

    /** @return deterministic localization naming rules */
    public List<LocalizationRuleDefinition> localizationRules() { return localizationRules; }

    /** @return deterministic diagnostic-only telemetry hooks */
    public List<TelemetryHookDefinition> telemetryHooks() { return telemetryHooks; }

    /** @return deterministic end-to-end authoring templates */
    public List<AuthoringTemplateDefinition> authoringTemplates() { return authoringTemplates; }

    /** @return lowercase SHA-256 fingerprint of common authoring semantics */
    public String fingerprint() { return fingerprint; }

    /**
     * Finds one role definition.
     *
     * @param id stable role ID
     * @return role definition, or {@code null}
     */
    public RoleDefinition findRole(String id) { return rolesById.get(id); }

    /**
     * Finds one mission profile.
     *
     * @param id stable mission-profile ID
     * @return mission profile, or {@code null}
     */
    public MissionProfileDefinition findMission(String id) { return missionsById.get(id); }

    /**
     * Finds one lineage template.
     *
     * @param id stable lineage ID
     * @return lineage definition, or {@code null}
     */
    public LineageDefinition findLineage(String id) { return lineagesById.get(id); }

    /**
     * Finds one visual binding.
     *
     * @param id stable visual-binding ID
     * @return visual binding, or {@code null}
     */
    public VisualBindingDefinition findVisualBinding(String id) { return visualsById.get(id); }

    /**
     * Finds one localization rule.
     *
     * @param id stable localization-rule ID
     * @return localization rule, or {@code null}
     */
    public LocalizationRuleDefinition findLocalizationRule(String id) { return localizationById.get(id); }

    /**
     * Finds one telemetry hook.
     *
     * @param id stable telemetry-hook ID
     * @return telemetry hook, or {@code null}
     */
    public TelemetryHookDefinition findTelemetryHook(String id) { return telemetryById.get(id); }

    /**
     * Finds one authoring template.
     *
     * @param id stable authoring-template ID
     * @return template definition, or {@code null}
     */
    public AuthoringTemplateDefinition findTemplate(String id) { return templatesById.get(id); }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(8192);
        out.append("schema|").append(schemaVersion).append('|').append(catalogVersion).append('\n');
        for (RoleDefinition role : roles) {
            out.append("role|").append(role.id()).append('|').append(role.domain()).append('|')
                    .append(role.enduranceReferenceId()).append('|').append(role.semanticIntent()).append('\n');
        }
        for (MissionProfileDefinition mission : missionProfiles) {
            out.append("mission|").append(mission.id()).append('|').append(mission.roleId()).append('|');
            mission.authorityInputs().forEach(value -> out.append(value.name()).append(','));
            out.append('|').append(mission.objective()).append('\n');
        }
        for (LineageDefinition lineage : lineages) {
            out.append("lineage|").append(lineage.id()).append('|').append(lineage.designAuthorityRef()).append('|')
                    .append(lineage.manufacturerRef()).append('|').append(lineage.procurementPolicyRef()).append('|')
                    .append(lineage.licenseMode()).append('\n');
        }
        for (VisualBindingDefinition visual : visualBindings) {
            out.append("visual|").append(visual.id()).append('|').append(visual.fitId()).append('|')
                    .append(visual.status()).append('|').append(nullToEmpty(visual.expectedFitFingerprint())).append('|')
                    .append(visual.assetRef()).append('|').append(visual.provenanceRef()).append('\n');
        }
        for (LocalizationRuleDefinition localization : localizationRules) {
            out.append("localization|").append(localization.id()).append('|').append(localization.sourceLanguage()).append('|')
                    .append(String.join(",", localization.languages())).append('|')
                    .append(localization.idKeyPrefix()).append('|').append(localization.namingRule()).append('\n');
        }
        for (TelemetryHookDefinition telemetry : telemetryHooks) {
            out.append("telemetry|").append(telemetry.id()).append('|').append(telemetry.authorityRef()).append('|')
                    .append(String.join(",", telemetry.metrics())).append('|').append(telemetry.diagnosticOnly()).append('\n');
        }
        for (AuthoringTemplateDefinition template : authoringTemplates) {
            out.append("template|").append(template.id()).append('|').append(template.roleId()).append('|')
                    .append(template.missionProfileId()).append('|').append(template.fitId()).append('|')
                    .append(template.productionHullId()).append('|').append(template.lineageId()).append('|')
                    .append(template.visualBindingId()).append('|').append(template.localizationRuleId()).append('|')
                    .append(String.join(",", template.telemetryHookIds())).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
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

    static String optionalFingerprint(String value) {
        if (value == null) return null;
        String checked = requireText(value, "expectedFitFingerprint");
        if (!SHA256.matcher(checked).matches()) {
            throw new IllegalArgumentException("expectedFitFingerprint must be lowercase SHA-256");
        }
        return checked;
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }

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

    /** Common Stage-22 ship-role domain. */
    public enum RoleDomain {
        /** Combat, reconnaissance, escort and force-projection hull families. */ MILITARY,
        /** Freight, replenishment and fleet-support hull families. */ SUPPORT
    }

    /** Common authoring license/procurement relationship; no faction receives behavior from this enum. */
    public enum LicenseMode {
        /** Design and production remain with the same declared organization. */ IN_HOUSE,
        /** Production is performed under an explicit license from another design authority. */ LICENSED,
        /** Design is a shared/common standard with multiple lawful producers. */ SHARED_STANDARD
    }

    /**
     * One stable common ship-role family.
     *
     * @param id stable role taxonomy ID
     * @param domain military or support role domain
     * @param enduranceReferenceId existing Stage-20 representative endurance role, or {@code NONE}
     * @param semanticIntent human-readable mission intent without hidden bonuses
     */
    public record RoleDefinition(
            String id, RoleDomain domain, String enduranceReferenceId, String semanticIntent) {
        /**
         * Validates one common role.
         *
         * @param id stable role ID
         * @param domain role domain
         * @param enduranceReferenceId Stage-20 endurance reference ID or NONE
         * @param semanticIntent role intent
         */
        public RoleDefinition {
            id = requireContentId(id, "role id");
            domain = Objects.requireNonNull(domain, "role domain");
            enduranceReferenceId = requireText(enduranceReferenceId, "enduranceReferenceId");
            semanticIntent = requireText(semanticIntent, "role semanticIntent");
        }
    }

    /**
     * Data-only mission profile; actual mission lifecycle stays with existing mission/world authorities.
     *
     * @param id stable mission-profile ID
     * @param roleId common role served by this profile
     * @param authorityInputs existing authority domains required to evaluate mission fitness
     * @param objective semantic mission objective
     */
    public record MissionProfileDefinition(
            String id, String roleId, List<ObjectiveAuthority> authorityInputs, String objective) {
        /**
         * Validates one data-only mission profile.
         *
         * @param id stable mission-profile ID
         * @param roleId common role ID
         * @param authorityInputs existing authority inputs
         * @param objective semantic objective
         */
        public MissionProfileDefinition {
            id = requireContentId(id, "mission profile id");
            roleId = requireContentId(roleId, "mission role id");
            Objects.requireNonNull(authorityInputs, "authorityInputs");
            EnumSet<ObjectiveAuthority> unique = EnumSet.noneOf(ObjectiveAuthority.class);
            for (ObjectiveAuthority authority : authorityInputs) {
                if (!unique.add(Objects.requireNonNull(authority, "mission authority input"))) {
                    throw new IllegalArgumentException("Duplicate mission authority input: " + authority);
                }
            }
            ArrayList<ObjectiveAuthority> ordered = new ArrayList<>(unique);
            ordered.sort(Comparator.comparing(Enum::name));
            authorityInputs = List.copyOf(ordered);
            objective = requireText(objective, "mission objective");
        }
    }

    /**
     * Manufacturer/design/procurement lineage metadata for authored content.
     *
     * @param id stable lineage ID
     * @param designAuthorityRef stable design-authority reference
     * @param manufacturerRef stable manufacturer reference
     * @param procurementPolicyRef existing/common procurement policy reference
     * @param licenseMode declared production relationship
     */
    public record LineageDefinition(
            String id,
            String designAuthorityRef,
            String manufacturerRef,
            String procurementPolicyRef,
            LicenseMode licenseMode) {
        /**
         * Validates one lineage record.
         *
         * @param id stable lineage ID
         * @param designAuthorityRef design authority reference
         * @param manufacturerRef manufacturer reference
         * @param procurementPolicyRef procurement policy reference
         * @param licenseMode production relationship
         */
        public LineageDefinition {
            id = requireContentId(id, "lineage id");
            designAuthorityRef = requireContentId(designAuthorityRef, "designAuthorityRef");
            manufacturerRef = requireContentId(manufacturerRef, "manufacturerRef");
            procurementPolicyRef = requireContentId(procurementPolicyRef, "procurementPolicyRef");
            licenseMode = Objects.requireNonNull(licenseMode, "licenseMode");
        }
    }

    /**
     * Fit-to-visual authoring binding. Production-like states must pin an exact fit fingerprint.
     *
     * @param id stable visual-binding ID
     * @param fitId exact engineering fit ID
     * @param status governed asset status
     * @param expectedFitFingerprint pinned fingerprint, optional only while status is CONCEPT
     * @param assetRef repository/classpath asset or authoring-document reference
     * @param provenanceRef source/license/provenance reference
     */
    public record VisualBindingDefinition(
            String id,
            String fitId,
            AssetStatus status,
            String expectedFitFingerprint,
            String assetRef,
            String provenanceRef) {
        /**
         * Validates one fit-to-visual binding record.
         *
         * @param id stable visual binding ID
         * @param fitId exact engineering fit ID
         * @param status governed asset status
         * @param expectedFitFingerprint pinned fingerprint or null
         * @param assetRef asset/document reference
         * @param provenanceRef provenance reference
         */
        public VisualBindingDefinition {
            id = requireContentId(id, "visual binding id");
            fitId = requireContentId(fitId, "visual fitId");
            status = Objects.requireNonNull(status, "visual status");
            expectedFitFingerprint = optionalFingerprint(expectedFitFingerprint);
            assetRef = requireText(assetRef, "visual assetRef");
            provenanceRef = requireText(provenanceRef, "visual provenanceRef");
        }
    }

    /**
     * Common naming/localization rule downstream of Stage-22.0 language governance.
     *
     * @param id stable localization-rule ID
     * @param sourceLanguage canonical source language
     * @param languages required localization path
     * @param idKeyPrefix stable localization-key prefix
     * @param namingRule human-readable naming convention
     */
    public record LocalizationRuleDefinition(
            String id, String sourceLanguage, List<String> languages, String idKeyPrefix, String namingRule) {
        /**
         * Validates and canonicalizes one localization rule.
         *
         * @param id stable rule ID
         * @param sourceLanguage source language
         * @param languages supported languages
         * @param idKeyPrefix stable key prefix
         * @param namingRule naming convention
         */
        public LocalizationRuleDefinition {
            id = requireContentId(id, "localization rule id");
            sourceLanguage = requireText(sourceLanguage, "sourceLanguage");
            TreeSet<String> ordered = new TreeSet<>();
            for (String language : Objects.requireNonNull(languages, "languages")) {
                if (!ordered.add(requireText(language, "language"))) {
                    throw new IllegalArgumentException("Duplicate localization language: " + language);
                }
            }
            languages = List.copyOf(ordered);
            idKeyPrefix = requireContentId(idKeyPrefix, "idKeyPrefix");
            namingRule = requireText(namingRule, "namingRule");
        }
    }

    /**
     * Diagnostic telemetry projection used by balance evidence, never a gameplay modifier.
     *
     * @param id stable telemetry hook ID
     * @param authorityRef existing source authority/calibration reference
     * @param metrics deterministic metric names projected from that authority
     * @param diagnosticOnly must remain true for Stage-22 common telemetry
     */
    public record TelemetryHookDefinition(
            String id, String authorityRef, List<String> metrics, boolean diagnosticOnly) {
        /**
         * Validates one diagnostic hook.
         *
         * @param id stable hook ID
         * @param authorityRef source authority reference
         * @param metrics metric names
         * @param diagnosticOnly diagnostic-only marker
         */
        public TelemetryHookDefinition {
            id = requireContentId(id, "telemetry id");
            authorityRef = requireText(authorityRef, "telemetry authorityRef");
            TreeSet<String> ordered = new TreeSet<>();
            for (String metric : Objects.requireNonNull(metrics, "metrics")) {
                if (!ordered.add(requireText(metric, "telemetry metric"))) {
                    throw new IllegalArgumentException("Duplicate telemetry metric: " + metric);
                }
            }
            if (ordered.isEmpty()) {
                throw new IllegalArgumentException("Telemetry hook must expose at least one metric");
            }
            metrics = List.copyOf(ordered);
            if (!diagnosticOnly) {
                throw new IllegalArgumentException("Stage-22 telemetry hooks must be diagnostic-only");
            }
        }
    }

    /**
     * One faction-neutral end-to-end authoring example from role to existing physical/presentation seams.
     *
     * @param id stable template ID
     * @param roleId common role ID
     * @param missionProfileId data-only mission profile ID
     * @param fitId exact engineering fit ID
     * @param productionHullId existing Stage-18 physical hull production path
     * @param lineageId manufacturer/design/procurement lineage metadata
     * @param visualBindingId fit-to-visual binding
     * @param localizationRuleId localization naming rule
     * @param telemetryHookIds diagnostic evidence hooks
     */
    public record AuthoringTemplateDefinition(
            String id,
            String roleId,
            String missionProfileId,
            String fitId,
            String productionHullId,
            String lineageId,
            String visualBindingId,
            String localizationRuleId,
            List<String> telemetryHookIds) {
        /**
         * Validates one authoring template reference set.
         *
         * @param id stable template ID
         * @param roleId common role ID
         * @param missionProfileId mission profile ID
         * @param fitId engineering fit ID
         * @param productionHullId physical hull path
         * @param lineageId lineage ID
         * @param visualBindingId visual binding ID
         * @param localizationRuleId localization rule ID
         * @param telemetryHookIds telemetry hook IDs
         */
        public AuthoringTemplateDefinition {
            id = requireContentId(id, "authoring template id");
            roleId = requireContentId(roleId, "template roleId");
            missionProfileId = requireContentId(missionProfileId, "template missionProfileId");
            fitId = requireContentId(fitId, "template fitId");
            productionHullId = requireContentId(productionHullId, "template productionHullId");
            lineageId = requireContentId(lineageId, "template lineageId");
            visualBindingId = requireContentId(visualBindingId, "template visualBindingId");
            localizationRuleId = requireContentId(localizationRuleId, "template localizationRuleId");
            TreeSet<String> ordered = new TreeSet<>();
            for (String hook : Objects.requireNonNull(telemetryHookIds, "telemetryHookIds")) {
                if (!ordered.add(requireContentId(hook, "telemetryHookId"))) {
                    throw new IllegalArgumentException("Duplicate telemetry hook reference: " + hook);
                }
            }
            telemetryHookIds = List.copyOf(ordered);
        }
    }
}
