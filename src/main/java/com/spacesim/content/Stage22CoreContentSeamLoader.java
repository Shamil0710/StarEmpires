package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22CoreContentSeamCatalog.AuthoringTemplateDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.LineageDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.LicenseMode;
import com.spacesim.content.Stage22CoreContentSeamCatalog.LocalizationRuleDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.MissionProfileDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.RoleDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.RoleDomain;
import com.spacesim.content.Stage22CoreContentSeamCatalog.TelemetryHookDefinition;
import com.spacesim.content.Stage22CoreContentSeamCatalog.VisualBindingDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads and validates the faction-neutral Stage-22.2 shared core-content authoring seam. */
public final class Stage22CoreContentSeamLoader {
    /** Exact supported Stage-22.2 schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in shared content-seam resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-core-content-seam-v1.json";

    private static final Set<String> REQUIRED_ROLES = Set.of(
            "role.military.corvette",
            "role.military.frigate",
            "role.military.destroyer",
            "role.military.cruiser",
            "role.military.battleship",
            "role.military.carrier",
            "role.support.freight",
            "role.support.tanker_replenishment",
            "role.support.fleet_logistics_repair_salvage");
    private static final Set<String> SUPPORT_ROLES = Set.of(
            "role.support.freight",
            "role.support.tanker_replenishment",
            "role.support.fleet_logistics_repair_salvage");
    private static final Set<String> ALLOWED_TELEMETRY_AUTHORITIES = Set.of(
            "authority.ship_engineering",
            "authority.stage18_shipyard",
            "authority.stage20_endurance");
    private static final List<String> FORBIDDEN_COMMON_TOKENS = List.of(
            "core.empire",
            "faction.imperial_directorate",
            "core.industrial_union",
            "faction.industrial_combine");

    private Stage22CoreContentSeamLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the built-in Stage-22.2 common seam against current physical and governance authorities.
     *
     * @return immutable validated faction-neutral authoring catalog
     */
    public static Stage22CoreContentSeamCatalog loadDefault() {
        ClassLoader classLoader = Stage22CoreContentSeamLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22.2 content seam resource: " + DEFAULT_RESOURCE);
            }
            return parse(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22.2 content seam resource", exception);
        }
    }

    /**
     * Parses one common authoring contract and validates all external references fail-closed.
     *
     * @param json complete Stage-22.2 JSON document
     * @return immutable validated common content-seam catalog
     */
    public static Stage22CoreContentSeamCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-22.2 content seam JSON must not be blank");
        }
        rejectFactionBias(json);
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-22.2 content seam JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-22.2 content seam root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-22.2 content seam schema: " + schemaVersion);
        }

        List<RoleDefinition> roles = new ArrayList<>();
        for (JsonValue node = requireArray(root, "roles").child; node != null; node = node.next) {
            roles.add(new RoleDefinition(
                    requireText(node, "id"),
                    enumValue(RoleDomain.class, node, "domain"),
                    requireText(node, "enduranceReferenceId"),
                    requireText(node, "semanticIntent")));
        }

        List<MissionProfileDefinition> missions = new ArrayList<>();
        for (JsonValue node = requireArray(root, "missionProfiles").child; node != null; node = node.next) {
            List<ObjectiveAuthority> inputs = new ArrayList<>();
            for (String value : stringArray(node, "authorityInputs")) {
                try {
                    inputs.add(ObjectiveAuthority.valueOf(value));
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException("Unknown mission authority input: " + value, exception);
                }
            }
            missions.add(new MissionProfileDefinition(
                    requireText(node, "id"),
                    requireText(node, "roleId"),
                    inputs,
                    requireText(node, "objective")));
        }

        List<LineageDefinition> lineages = new ArrayList<>();
        for (JsonValue node = requireArray(root, "lineages").child; node != null; node = node.next) {
            lineages.add(new LineageDefinition(
                    requireText(node, "id"),
                    requireText(node, "designAuthorityRef"),
                    requireText(node, "manufacturerRef"),
                    requireText(node, "procurementPolicyRef"),
                    enumValue(LicenseMode.class, node, "licenseMode")));
        }

        List<VisualBindingDefinition> visuals = new ArrayList<>();
        for (JsonValue node = requireArray(root, "visualBindings").child; node != null; node = node.next) {
            visuals.add(new VisualBindingDefinition(
                    requireText(node, "id"),
                    requireText(node, "fitId"),
                    enumValue(AssetStatus.class, node, "status"),
                    optionalText(node, "expectedFitFingerprint"),
                    requireText(node, "assetRef"),
                    requireText(node, "provenanceRef")));
        }

        List<LocalizationRuleDefinition> localizationRules = new ArrayList<>();
        for (JsonValue node = requireArray(root, "localizationRules").child; node != null; node = node.next) {
            localizationRules.add(new LocalizationRuleDefinition(
                    requireText(node, "id"),
                    requireText(node, "sourceLanguage"),
                    stringArray(node, "languages"),
                    requireText(node, "idKeyPrefix"),
                    requireText(node, "namingRule")));
        }

        List<TelemetryHookDefinition> telemetry = new ArrayList<>();
        for (JsonValue node = requireArray(root, "telemetryHooks").child; node != null; node = node.next) {
            telemetry.add(new TelemetryHookDefinition(
                    requireText(node, "id"),
                    requireText(node, "authorityRef"),
                    stringArray(node, "metrics"),
                    requireBoolean(node, "diagnosticOnly")));
        }

        List<AuthoringTemplateDefinition> templates = new ArrayList<>();
        for (JsonValue node = requireArray(root, "authoringTemplates").child; node != null; node = node.next) {
            templates.add(new AuthoringTemplateDefinition(
                    requireText(node, "id"),
                    requireText(node, "roleId"),
                    requireText(node, "missionProfileId"),
                    requireText(node, "fitId"),
                    requireText(node, "productionHullId"),
                    requireText(node, "lineageId"),
                    requireText(node, "visualBindingId"),
                    requireText(node, "localizationRuleId"),
                    stringArray(node, "telemetryHookIds")));
        }

        Stage22CoreContentSeamCatalog catalog = new Stage22CoreContentSeamCatalog(
                schemaVersion,
                requireText(root, "catalogVersion"),
                roles,
                missions,
                lineages,
                visuals,
                localizationRules,
                telemetry,
                templates);
        validateReferences(
                catalog,
                Stage22ContentGovernanceLoader.loadDefault(),
                ShipEngineeringCatalogLoader.loadDefault(),
                Stage18ShipyardCatalogLoader.loadDefault(),
                Stage20RepresentativeEnduranceProfile.deriveCurrent());
        return catalog;
    }

    private static void validateReferences(
            Stage22CoreContentSeamCatalog catalog,
            Stage22ContentGovernanceCatalog governance,
            ShipEngineeringCatalog engineering,
            Stage18ShipyardCatalog shipyards,
            Stage20RepresentativeEnduranceProfile endurance) {
        Set<String> roleIds = new HashSet<>();
        Map<String, Integer> missionUses = new HashMap<>();
        for (RoleDefinition role : catalog.roles()) roleIds.add(role.id());
        if (!roleIds.equals(REQUIRED_ROLES) || catalog.roles().size() != REQUIRED_ROLES.size()) {
            throw new IllegalArgumentException("Stage-22.2 common role taxonomy must contain exactly the approved nine role families");
        }

        for (MissionProfileDefinition mission : catalog.missionProfiles()) {
            RoleDefinition role = requireReference(catalog.findRole(mission.roleId()), "role", mission.roleId());
            if (mission.authorityInputs().isEmpty()) {
                throw new IllegalArgumentException("Mission profile must name at least one existing authority input: " + mission.id());
            }
            missionUses.merge(role.id(), 1, Integer::sum);
        }
        for (RoleDefinition role : catalog.roles()) {
            if (missionUses.getOrDefault(role.id(), 0) != 1) {
                throw new IllegalArgumentException("Each common role must have exactly one mission profile: " + role.id());
            }
        }

        Set<String> enduranceIds = new HashSet<>();
        endurance.samples().forEach(sample -> enduranceIds.add(sample.representativeId()));
        for (RoleDefinition role : catalog.roles()) {
            if (SUPPORT_ROLES.contains(role.id()) && !enduranceIds.contains(role.enduranceReferenceId())) {
                throw new IllegalArgumentException("Support role lacks an existing Stage-20 endurance reference: " + role.id());
            }
        }

        for (LineageDefinition lineage : catalog.lineages()) {
            if (!lineage.procurementPolicyRef().startsWith("policy.shared.procurement.")) {
                throw new IllegalArgumentException("Common lineage must use a shared procurement-policy seam: " + lineage.id());
            }
        }

        Map<String, String> resolvedFingerprints = new HashMap<>();
        for (VisualBindingDefinition visual : catalog.visualBindings()) {
            var fit = engineering.findDemonstratorFit(visual.fitId());
            if (fit == null) {
                throw new IllegalArgumentException("Visual binding references an unknown engineering fit: " + visual.id());
            }
            String fingerprint = Stage22FitFingerprint.compute(engineering, fit);
            resolvedFingerprints.put(visual.id(), fingerprint);
            if (visual.status() != AssetStatus.CONCEPT && visual.expectedFitFingerprint() == null) {
                throw new IllegalArgumentException("Engineering-approved/production visual must pin a fit fingerprint: " + visual.id());
            }
            if (visual.expectedFitFingerprint() != null && !visual.expectedFitFingerprint().equals(fingerprint)) {
                throw new IllegalArgumentException("Visual binding fit fingerprint is stale: " + visual.id());
            }
        }

        var authoring = governance.getAuthoringContract();
        for (LocalizationRuleDefinition localization : catalog.localizationRules()) {
            if (!localization.sourceLanguage().equals(authoring.sourceLanguage())
                    || !localization.languages().equals(authoring.localizationLanguages())) {
                throw new IllegalArgumentException("Localization rule drifts from Stage-22.0 language governance: " + localization.id());
            }
        }

        for (TelemetryHookDefinition hook : catalog.telemetryHooks()) {
            if (!hook.diagnosticOnly() || !ALLOWED_TELEMETRY_AUTHORITIES.contains(hook.authorityRef())) {
                throw new IllegalArgumentException("Telemetry hook is not a diagnostic projection of an accepted authority: " + hook.id());
            }
        }

        if (catalog.authoringTemplates().isEmpty()) {
            throw new IllegalArgumentException("Stage-22.2 requires at least one end-to-end common authoring template");
        }
        for (AuthoringTemplateDefinition template : catalog.authoringTemplates()) {
            RoleDefinition role = requireReference(catalog.findRole(template.roleId()), "role", template.roleId());
            MissionProfileDefinition mission = requireReference(
                    catalog.findMission(template.missionProfileId()), "mission profile", template.missionProfileId());
            if (!mission.roleId().equals(role.id())) {
                throw new IllegalArgumentException("Template mission does not serve its declared role: " + template.id());
            }
            var fit = engineering.findDemonstratorFit(template.fitId());
            if (fit == null || !fit.hullId().equals(template.productionHullId())) {
                throw new IllegalArgumentException("Template fit does not match its physical hull path: " + template.id());
            }
            if (shipyards.findHullProfile(template.productionHullId()) == null) {
                throw new IllegalArgumentException("Template hull has no Stage-18 physical production profile: " + template.id());
            }
            requireReference(catalog.findLineage(template.lineageId()), "lineage", template.lineageId());
            VisualBindingDefinition visual = requireReference(
                    catalog.findVisualBinding(template.visualBindingId()), "visual binding", template.visualBindingId());
            if (!visual.fitId().equals(template.fitId()) || resolvedFingerprints.get(visual.id()) == null) {
                throw new IllegalArgumentException("Template visual does not bind its exact engineering fit: " + template.id());
            }
            requireReference(
                    catalog.findLocalizationRule(template.localizationRuleId()),
                    "localization rule",
                    template.localizationRuleId());
            if (template.telemetryHookIds().isEmpty()) {
                throw new IllegalArgumentException("Template must expose diagnostic evidence hooks: " + template.id());
            }
            for (String hookId : template.telemetryHookIds()) {
                requireReference(catalog.findTelemetryHook(hookId), "telemetry hook", hookId);
            }
        }

        if (catalog.fingerprint().length() != 64) {
            throw new IllegalStateException("Stage-22.2 common content fingerprint drift");
        }
    }

    private static void rejectFactionBias(String json) {
        String lower = json.toLowerCase(java.util.Locale.ROOT);
        for (String token : FORBIDDEN_COMMON_TOKENS) {
            if (lower.contains(token)) {
                throw new IllegalArgumentException("Common Stage-22.2 seam contains faction-specific package token: " + token);
            }
        }
    }

    private static JsonValue requireArray(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static int requireInt(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        double raw = value.asDouble();
        int result = value.asInt();
        if (raw != result) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return result;
    }

    private static boolean requireBoolean(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.asBoolean();
    }

    private static String requireText(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
        }
        return value.asString().strip();
    }

    private static String optionalText(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || value.isNull()) return null;
        if (!value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be null or a non-blank string");
        }
        return value.asString().strip();
    }

    private static List<String> stringArray(JsonValue node, String field) {
        List<String> result = new ArrayList<>();
        for (JsonValue value = requireArray(node, field).child; value != null; value = value.next) {
            if (!value.isString() || value.asString().isBlank()) {
                throw new IllegalArgumentException(field + " must contain non-blank strings");
            }
            result.add(value.asString().strip());
        }
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonValue node, String field) {
        String raw = requireText(node, field);
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field + " value: " + raw, exception);
        }
    }

    private static <T> T requireReference(T value, String family, String id) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + family + " reference: " + id);
        }
        return value;
    }
}
