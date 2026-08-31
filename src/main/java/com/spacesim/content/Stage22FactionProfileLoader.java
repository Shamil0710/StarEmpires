package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22ContentGovernanceCatalog.FactionIdentityDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityDisposition;
import com.spacesim.content.Stage22FactionProfileCatalog.AuthoritySeam;
import com.spacesim.content.Stage22FactionProfileCatalog.DoctrineProfileDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.LocalizationDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.ManifestReferenceDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.PackageScope;
import com.spacesim.content.Stage22FactionProfileCatalog.PolicyBindingDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.PolicyKind;
import com.spacesim.content.Stage22FactionProfileCatalog.RoleProductionBindingDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.SystemicProfileDefinition;
import com.spacesim.content.Stage22FactionProfileCatalog.VisualKind;
import com.spacesim.content.Stage22FactionProfileCatalog.VisualProfileDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage21GeneratedMilitaryEngineeringCatalog;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.StrategicGoalType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Loads and fail-closed validates the versioned Stage-22.1 systemic faction-profile contract. */
public final class Stage22FactionProfileLoader {
    /** Exact supported Stage-22.1 profile schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Built-in core-pair profile resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-faction-profiles-v1.json";

    private static final Set<String> REQUIRED_CORE_PACKAGES = Set.of(
            "core.empire", "core.industrial_union");
    private static final Map<PolicyKind, AuthoritySeam> REQUIRED_AUTHORITY_SEAMS = Map.of(
            PolicyKind.INDUSTRIAL, AuthoritySeam.STAGE18_INDUSTRY,
            PolicyKind.PROCUREMENT, AuthoritySeam.FACTION_POLICY_COMMAND,
            PolicyKind.LOGISTICS, AuthoritySeam.STAGE20_FREIGHT,
            PolicyKind.FLEET, AuthoritySeam.STAGE21_FLEET_COMMAND,
            PolicyKind.DIPLOMACY, AuthoritySeam.FACTION_DIPLOMACY,
            PolicyKind.TERRITORY, AuthoritySeam.TERRITORIAL_CONTROL,
            PolicyKind.KNOWLEDGE, AuthoritySeam.DISCOVERY_KNOWLEDGE,
            PolicyKind.RECOVERY, AuthoritySeam.SETTLEMENT_RECOVERY);

    private Stage22FactionProfileLoader() {
        throw new AssertionError("No instances");
    }

    /**
     * Loads the built-in two-faction profile catalog against existing identity and physical authorities.
     *
     * @return immutable validated core-pair profile catalog
     */
    public static Stage22FactionProfileCatalog loadDefault() {
        ClassLoader classLoader = Stage22FactionProfileLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22.1 faction profile resource: " + DEFAULT_RESOURCE);
            }
            Stage22FactionProfileCatalog result = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            validateProductionBaseline(result);
            return result;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22.1 faction profile resource", exception);
        }
    }

    /**
     * Parses one profile document and validates every reference against accepted common authorities.
     *
     * @param json complete profile JSON document
     * @return immutable internally and externally validated profile catalog
     */
    public static Stage22FactionProfileCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-22.1 faction profile JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-22.1 faction profile JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-22.1 faction profile root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-22.1 faction profile schema: " + schemaVersion);
        }

        List<DoctrineProfileDefinition> doctrines = new ArrayList<>();
        for (JsonValue node = requireArray(root, "doctrineProfiles").child; node != null; node = node.next) {
            JsonValue axes = requireObject(node, "institutionalDoctrine");
            JsonValue preferences = requireObject(node, "strategicGoalPreferences");
            EnumMap<StrategicGoalType, Integer> strategic = new EnumMap<>(StrategicGoalType.class);
            for (StrategicGoalType type : StrategicGoalType.values()) {
                strategic.put(type, requireInt(preferences, type.wireId()));
            }
            if (preferences.size != StrategicGoalType.values().length) {
                throw new IllegalArgumentException("strategicGoalPreferences contains unsupported keys");
            }
            doctrines.add(new DoctrineProfileDefinition(
                    requireText(node, "id"),
                    new FactionDoctrineState(
                            requireInt(axes, "tradeOpenness"),
                            requireInt(axes, "securityPosture"),
                            requireInt(axes, "expansionPreference"),
                            requireInt(axes, "sovereigntySensitivity"),
                            requireInt(axes, "treatyLegalism"),
                            requireInt(axes, "interventionism"),
                            requireInt(axes, "economicResiliencePriority")),
                    strategic));
        }

        List<PolicyBindingDefinition> policies = new ArrayList<>();
        for (JsonValue node = requireArray(root, "policyBindings").child; node != null; node = node.next) {
            policies.add(new PolicyBindingDefinition(
                    requireText(node, "id"),
                    enumValue(PolicyKind.class, node, "kind"),
                    enumValue(AuthoritySeam.class, node, "authoritySeam"),
                    stringArray(node, "dependsOn"),
                    requireText(node, "semanticIntent")));
        }

        List<ManifestReferenceDefinition> manifests = new ArrayList<>();
        for (JsonValue node = requireArray(root, "manifestReferences").child; node != null; node = node.next) {
            List<RoleProductionBindingDefinition> roles = new ArrayList<>();
            for (JsonValue role = requireArray(node, "roleBindings").child; role != null; role = role.next) {
                roles.add(new RoleProductionBindingDefinition(
                        requireText(role, "roleId"),
                        requireText(role, "fitId"),
                        requireText(role, "productionPathRef"),
                        requireText(role, "visualProfileRef")));
            }
            manifests.add(new ManifestReferenceDefinition(
                    requireText(node, "id"),
                    requireText(node, "packageKey"),
                    enumValue(PackageScope.class, node, "scope"),
                    enumValue(ContentMaturity.class, node, "maturity"),
                    roles));
        }

        List<VisualProfileDefinition> visuals = new ArrayList<>();
        for (JsonValue node = requireArray(root, "visualProfiles").child; node != null; node = node.next) {
            visuals.add(new VisualProfileDefinition(
                    requireText(node, "id"),
                    enumValue(VisualKind.class, node, "kind"),
                    requireText(node, "packageKey"),
                    enumValue(AssetStatus.class, node, "status"),
                    requireText(node, "authorityDocument")));
        }

        List<LocalizationDefinition> localizations = new ArrayList<>();
        for (JsonValue node = requireArray(root, "localizations").child; node != null; node = node.next) {
            localizations.add(new LocalizationDefinition(
                    requireText(node, "id"),
                    requireText(node, "namespace"),
                    requireText(node, "sourceLanguage"),
                    stringArray(node, "languages")));
        }

        List<SystemicProfileDefinition> profiles = new ArrayList<>();
        for (JsonValue node = requireArray(root, "systemicProfiles").child; node != null; node = node.next) {
            profiles.add(new SystemicProfileDefinition(
                    requireText(node, "profileId"),
                    requireText(node, "stableFactionId"),
                    requireText(node, "packageKey"),
                    requireInt(node, "profileVersion"),
                    enumValue(IdentityClass.class, node, "identityClass"),
                    requireText(node, "doctrineProfileRef"),
                    requireText(node, "industrialPolicyRef"),
                    requireText(node, "procurementPolicyRef"),
                    requireText(node, "logisticsPolicyRef"),
                    requireText(node, "fleetDoctrineRef"),
                    requireText(node, "diplomacyPolicyRef"),
                    requireText(node, "territoryPolicyRef"),
                    requireText(node, "knowledgePolicyRef"),
                    requireText(node, "recoveryPolicyRef"),
                    requireText(node, "authoredContentManifestRef"),
                    requireText(node, "shipVisualProfileRef"),
                    requireText(node, "characterVisualProfileRef"),
                    requireText(node, "localizationRef"),
                    stringArray(node, "compatibilityAliases")));
        }

        Stage22FactionProfileCatalog catalog = new Stage22FactionProfileCatalog(
                schemaVersion,
                requireText(root, "catalogVersion"),
                doctrines,
                policies,
                manifests,
                visuals,
                localizations,
                profiles);
        validateReferences(
                catalog,
                Stage22ContentGovernanceLoader.loadDefault(),
                Stage21GeneratedMilitaryEngineeringCatalog.load(),
                Stage18ShipyardCatalogLoader.loadDefault());
        return catalog;
    }

    private static void validateReferences(
            Stage22FactionProfileCatalog catalog,
            Stage22ContentGovernanceCatalog governance,
            ShipEngineeringCatalog engineering,
            Stage18ShipyardCatalog shipyards) {
        if (catalog.systemicProfiles().isEmpty()) {
            throw new IllegalArgumentException("Stage-22.1 catalog requires systemic profiles");
        }
        validatePolicyGraph(catalog);
        Map<String, Integer> doctrineUses = new HashMap<>();
        Map<String, Integer> manifestUses = new HashMap<>();
        Map<String, Integer> visualUses = new HashMap<>();
        Map<String, Integer> localizationUses = new HashMap<>();
        Set<String> referencedPolicies = new HashSet<>();

        for (SystemicProfileDefinition profile : catalog.systemicProfiles()) {
            FactionIdentityDefinition identity = governance.findFactionIdentity(profile.stableFactionId());
            if (identity == null
                    || identity.identityClass() != profile.identityClass()
                    || !profile.packageKey().equals(identity.canonicalPackageKey())) {
                throw new IllegalArgumentException(
                        "Profile identity/package binding is not governed by Stage-22.0: " + profile.profileId());
            }
            requirePackageId(profile.doctrineProfileRef(), "doctrine", profile.packageKey());
            requireReference(catalog.findDoctrine(profile.doctrineProfileRef()), "doctrine", profile.doctrineProfileRef());
            doctrineUses.merge(profile.doctrineProfileRef(), 1, Integer::sum);

            Map<PolicyKind, String> policyRefs = policyReferences(profile);
            if (!policyRefs.keySet().equals(EnumSet.allOf(PolicyKind.class))) {
                throw new IllegalArgumentException("Profile does not cover every common policy kind: " + profile.profileId());
            }
            for (Map.Entry<PolicyKind, String> entry : policyRefs.entrySet()) {
                requirePackageId(entry.getValue(), "policy", profile.packageKey());
                PolicyBindingDefinition policy = requireReference(
                        catalog.findPolicy(entry.getValue()), "policy", entry.getValue());
                if (policy.kind() != entry.getKey() || policy.authoritySeam() != REQUIRED_AUTHORITY_SEAMS.get(entry.getKey())) {
                    throw new IllegalArgumentException("Policy kind/authority mismatch: " + policy.id());
                }
                referencedPolicies.add(policy.id());
            }

            ManifestReferenceDefinition manifest = requireReference(
                    catalog.findManifest(profile.authoredContentManifestRef()),
                    "manifest",
                    profile.authoredContentManifestRef());
            if (!manifest.packageKey().equals(profile.packageKey()) || manifest.scope() != PackageScope.CORE) {
                throw new IllegalArgumentException("Core profile has a package/scope manifest mismatch: " + profile.profileId());
            }
            manifestUses.merge(manifest.id(), 1, Integer::sum);
            validateRoleBindings(manifest, catalog, engineering, shipyards);

            validateVisual(profile.shipVisualProfileRef(), VisualKind.SHIP, profile.packageKey(), catalog, visualUses);
            validateVisual(
                    profile.characterVisualProfileRef(), VisualKind.CHARACTER, profile.packageKey(), catalog, visualUses);
            LocalizationDefinition localization = requireReference(
                    catalog.findLocalization(profile.localizationRef()), "localization", profile.localizationRef());
            if (!localization.namespace().equals("faction." + profile.packageKey())
                    || !localization.languages().containsAll(governance.getAuthoringContract().localizationLanguages())
                    || !localization.sourceLanguage().equals(governance.getAuthoringContract().sourceLanguage())) {
                throw new IllegalArgumentException("Profile localization contract mismatch: " + profile.profileId());
            }
            localizationUses.merge(localization.id(), 1, Integer::sum);

            for (String alias : profile.compatibilityAliases()) {
                FactionIdentityDefinition aliasIdentity = governance.findFactionIdentity(alias);
                if (aliasIdentity == null
                        || (aliasIdentity.disposition() != IdentityDisposition.ALIAS
                                && aliasIdentity.disposition() != IdentityDisposition.MIGRATE)
                        || !profile.stableFactionId().equals(aliasIdentity.targetStableFactionId())) {
                    throw new IllegalArgumentException("Compatibility alias lacks an explicit migration: " + alias);
                }
            }
        }

        requireExactSingleUse("doctrine", catalog.doctrineProfiles().stream().map(DoctrineProfileDefinition::id).toList(), doctrineUses);
        requireExactSingleUse("manifest", catalog.manifestReferences().stream().map(ManifestReferenceDefinition::id).toList(), manifestUses);
        requireExactSingleUse("visual", catalog.visualProfiles().stream().map(VisualProfileDefinition::id).toList(), visualUses);
        requireExactSingleUse("localization", catalog.localizations().stream().map(LocalizationDefinition::id).toList(), localizationUses);
        if (referencedPolicies.size() != catalog.policyBindings().size()) {
            throw new IllegalArgumentException("Policy binding exists without exactly one systemic authority reference");
        }
    }

    private static void validatePolicyGraph(Stage22FactionProfileCatalog catalog) {
        for (PolicyBindingDefinition policy : catalog.policyBindings()) {
            if (policy.authoritySeam() != REQUIRED_AUTHORITY_SEAMS.get(policy.kind())) {
                throw new IllegalArgumentException("Policy points at the wrong common authority: " + policy.id());
            }
            for (String dependency : policy.dependsOn()) {
                PolicyBindingDefinition resolved = catalog.findPolicy(dependency);
                if (resolved == null) {
                    throw new IllegalArgumentException("Policy dependency does not exist: " + dependency);
                }
                String ownPackage = policyPackage(policy.id());
                if (!ownPackage.equals(policyPackage(resolved.id()))) {
                    throw new IllegalArgumentException("Policy dependency crosses faction packages: " + policy.id());
                }
            }
        }
        Set<String> visiting = new HashSet<>();
        Set<String> visited = new HashSet<>();
        for (PolicyBindingDefinition policy : catalog.policyBindings()) {
            visitPolicy(policy, catalog, visiting, visited);
        }
    }

    private static void visitPolicy(
            PolicyBindingDefinition policy,
            Stage22FactionProfileCatalog catalog,
            Set<String> visiting,
            Set<String> visited) {
        if (visited.contains(policy.id())) {
            return;
        }
        if (!visiting.add(policy.id())) {
            throw new IllegalArgumentException("Circular policy dependency: " + policy.id());
        }
        for (String dependency : policy.dependsOn()) {
            visitPolicy(Objects.requireNonNull(catalog.findPolicy(dependency)), catalog, visiting, visited);
        }
        visiting.remove(policy.id());
        visited.add(policy.id());
    }

    private static void validateRoleBindings(
            ManifestReferenceDefinition manifest,
            Stage22FactionProfileCatalog catalog,
            ShipEngineeringCatalog engineering,
            Stage18ShipyardCatalog shipyards) {
        for (RoleProductionBindingDefinition role : manifest.roleBindings()) {
            var fit = engineering.findDemonstratorFit(role.fitId());
            if (fit == null || shipyards.findHullProfile(fit.hullId()) == null) {
                throw new IllegalArgumentException("Role references an unknown or physically invalid fit: " + role.roleId());
            }
            if (!role.productionPathRef().equals(fit.hullId())) {
                throw new IllegalArgumentException("Role has no matching physical production path: " + role.roleId());
            }
            VisualProfileDefinition visual = catalog.findVisual(role.visualProfileRef());
            if (visual == null || visual.kind() != VisualKind.SHIP || !visual.packageKey().equals(manifest.packageKey())) {
                throw new IllegalArgumentException("Role visual/systemic binding mismatch: " + role.roleId());
            }
        }
    }

    private static void validateVisual(
            String id,
            VisualKind expectedKind,
            String packageKey,
            Stage22FactionProfileCatalog catalog,
            Map<String, Integer> uses) {
        VisualProfileDefinition visual = requireReference(catalog.findVisual(id), "visual", id);
        if (visual.kind() != expectedKind || !visual.packageKey().equals(packageKey)) {
            throw new IllegalArgumentException("Visual profile has no matching systemic profile: " + id);
        }
        uses.merge(id, 1, Integer::sum);
    }

    private static Map<PolicyKind, String> policyReferences(SystemicProfileDefinition profile) {
        return Map.of(
                PolicyKind.INDUSTRIAL, profile.industrialPolicyRef(),
                PolicyKind.PROCUREMENT, profile.procurementPolicyRef(),
                PolicyKind.LOGISTICS, profile.logisticsPolicyRef(),
                PolicyKind.FLEET, profile.fleetDoctrineRef(),
                PolicyKind.DIPLOMACY, profile.diplomacyPolicyRef(),
                PolicyKind.TERRITORY, profile.territoryPolicyRef(),
                PolicyKind.KNOWLEDGE, profile.knowledgePolicyRef(),
                PolicyKind.RECOVERY, profile.recoveryPolicyRef());
    }

    private static String policyPackage(String policyId) {
        for (String packageKey : REQUIRED_CORE_PACKAGES) {
            if (policyId.startsWith("policy." + packageKey + ".")) {
                return packageKey;
            }
        }
        throw new IllegalArgumentException("Policy leaks outside the Stage-22 core package boundary: " + policyId);
    }

    private static void requirePackageId(String id, String family, String packageKey) {
        if (!id.startsWith(family + "." + packageKey + ".")) {
            throw new IllegalArgumentException(family + " reference crosses package boundary: " + id);
        }
    }

    private static <T> T requireReference(T value, String family, String id) {
        if (value == null) {
            throw new IllegalArgumentException("Missing " + family + " reference: " + id);
        }
        return value;
    }

    private static void requireExactSingleUse(
            String family,
            List<String> definedIds,
            Map<String, Integer> uses) {
        for (String id : definedIds) {
            if (uses.getOrDefault(id, 0) != 1) {
                throw new IllegalArgumentException(family + " must be referenced by exactly one systemic profile: " + id);
            }
        }
    }

    private static void validateProductionBaseline(Stage22FactionProfileCatalog catalog) {
        Set<String> packages = new HashSet<>();
        Set<String> stableIds = new HashSet<>();
        for (SystemicProfileDefinition profile : catalog.systemicProfiles()) {
            packages.add(profile.packageKey());
            stableIds.add(profile.stableFactionId());
            ManifestReferenceDefinition manifest = catalog.findManifest(profile.authoredContentManifestRef());
            if (manifest.maturity() != ContentMaturity.SEED || !manifest.roleBindings().isEmpty()) {
                throw new IllegalStateException(
                        "M22.1 manifests must remain empty SEED contracts until the M22.2 role seam");
            }
        }
        if (!packages.equals(REQUIRED_CORE_PACKAGES)
                || !stableIds.equals(Set.of("faction.imperial_directorate", "faction.industrial_combine"))
                || catalog.systemicProfiles().size() != 2
                || catalog.policyBindings().size() != 16
                || catalog.visualProfiles().size() != 4
                || catalog.fingerprint().length() != 64) {
            throw new IllegalStateException("Stage-22.1 production core-pair profile baseline drift");
        }
    }

    private static JsonValue requireArray(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isArray()) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        return value;
    }

    private static JsonValue requireObject(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException(field + " must be an object");
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

    private static String requireText(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString() || value.asString().isBlank()) {
            throw new IllegalArgumentException(field + " must be a non-blank string");
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
}
