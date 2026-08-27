package com.spacesim.content;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.spacesim.content.Stage22ContentGovernanceCatalog.AlphaFloorDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.AuthoringManifestContract;
import com.spacesim.content.Stage22ContentGovernanceCatalog.BindingKind;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22ContentGovernanceCatalog.CutPriority;
import com.spacesim.content.Stage22ContentGovernanceCatalog.CutPriorityDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.FactionIdentityDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.HardcodedDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityDisposition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceDefinition;
import com.spacesim.content.Stage22ContentGovernanceCatalog.SourceMaturity;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Loads and validates the Stage-22.0 content/faction governance baseline. */
public final class Stage22ContentGovernanceLoader {
    /** Current supported governance schema. */
    public static final int CURRENT_SCHEMA_VERSION = 1;
    /** Default Stage-22.0 governance resource. */
    public static final String DEFAULT_RESOURCE = "data/content/stage22-content-governance-v1.json";

    private static final Set<String> REQUIRED_PRE_STAGE22_SOURCES = Set.of(
            "data/content/catalog-v1.json",
            "data/content/ship-engineering-v1.json",
            "data/content/ship-protection-runtime-v1.json",
            "data/content/shipyard-industrial-v1.json",
            "data/content/stage17_5i-combat-test-engineering-v1.json",
            "data/content/stage17_5i-doctrine-engineering-v1.json",
            "data/content/stage17_5i-protection-runtime-v1.json",
            "data/content/stage17_5i-weapon-ammunition-v1.json",
            "data/content/stage17_5i-weapon-launchers-v1.json",
            "data/content/stage18-extraction-v1.json",
            "data/content/stage18-facilities-v1.json",
            "data/content/stage18-facility-construction-v1.json",
            "data/content/stage18-manufacturing-v1.json",
            "data/content/stage18-refining-v1.json",
            "data/content/stage18-resource-ontology-v1.json",
            "data/content/stage18-ship-consumables-v1.json",
            "data/content/stage18-shipyards-v1.json",
            "data/content/stage18-stations-v1.json",
            "data/content/weapon-ammunition-v1.json",
            "data/content/weapon-launchers-v1.json");

    private static final Set<String> REQUIRED_FACTION_IDENTITIES = Set.of(
            "faction.neutral",
            "faction.trade_league",
            "faction.miners",
            "faction.imperial_directorate",
            "faction.frontier_union",
            "faction.industrial_combine",
            "faction.free_ports",
            "faction.research_consortium",
            "faction.alpha",
            "faction.beta");

    private Stage22ContentGovernanceLoader() {
        throw new AssertionError("No instances");
    }

    /** Loads the built-in Stage-22.0 governance baseline and applies production-entry validation. */
    public static Stage22ContentGovernanceCatalog loadDefault() {
        ClassLoader classLoader = Stage22ContentGovernanceLoader.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage-22 governance resource: " + DEFAULT_RESOURCE);
            }
            Stage22ContentGovernanceCatalog catalog = parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            validateProductionBaseline(catalog, classLoader);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage-22 governance resource", exception);
        }
    }

    /** Parses one standalone governance document. */
    public static Stage22ContentGovernanceCatalog parse(String json) {
        Objects.requireNonNull(json, "json");
        if (json.isBlank()) {
            throw new IllegalArgumentException("Stage-22 governance JSON must not be blank");
        }
        final JsonValue root;
        try {
            root = new JsonReader().parse(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid Stage-22 governance JSON", exception);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("Stage-22 governance root must be an object");
        }
        int schemaVersion = requireInt(root, "schemaVersion");
        if (schemaVersion != CURRENT_SCHEMA_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-22 governance schema: " + schemaVersion);
        }

        List<SourceDefinition> sources = new ArrayList<>();
        for (JsonValue node = requireArray(root, "sources").child; node != null; node = node.next) {
            sources.add(new SourceDefinition(
                    requireNonBlank(node, "resourcePath"),
                    requireNonBlank(node, "domain"),
                    enumValue(SourceMaturity.class, node, "maturity"),
                    enumValue(ContentDisposition.class, node, "defaultDisposition"),
                    requireNonBlank(node, "semanticReason")));
        }

        List<HardcodedDefinition> hardcodedDefinitions = new ArrayList<>();
        for (JsonValue node = requireArray(root, "hardcodedDefinitions").child; node != null; node = node.next) {
            hardcodedDefinitions.add(new HardcodedDefinition(
                    requireNonBlank(node, "id"),
                    requireNonBlank(node, "source"),
                    enumValue(SourceMaturity.class, node, "maturity"),
                    enumValue(ContentDisposition.class, node, "disposition"),
                    stringArray(node, "references"),
                    requireNonBlank(node, "semanticReason")));
        }

        List<FactionIdentityDefinition> factionIdentities = new ArrayList<>();
        for (JsonValue node = requireArray(root, "factionIdentities").child; node != null; node = node.next) {
            factionIdentities.add(new FactionIdentityDefinition(
                    requireNonBlank(node, "stableFactionId"),
                    enumValue(IdentityClass.class, node, "identityClass"),
                    enumValue(IdentityDisposition.class, node, "disposition"),
                    optionalString(node, "targetStableFactionId"),
                    optionalString(node, "canonicalPackageKey"),
                    optionalString(node, "canonicalDisplayName"),
                    requireNonBlank(node, "sourceVersionRange"),
                    requireNonBlank(node, "saveBehavior"),
                    requireNonBlank(node, "collisionBehavior"),
                    requireNonBlank(node, "semanticReason")));
        }

        JsonValue authoring = requireObject(root, "authoringContract");
        AuthoringManifestContract authoringContract = new AuthoringManifestContract(
                enumArray(BindingKind.class, authoring, "requiredBindingKinds"),
                enumArray(AssetStatus.class, authoring, "requiredAssetStatuses"),
                enumArray(ContentMaturity.class, authoring, "requiredContentMaturities"),
                requireNonBlank(authoring, "sourceLanguage"),
                stringArray(authoring, "localizationLanguages"),
                requireBoolean(authoring, "requireProvenance"),
                requireBoolean(authoring, "requireFitFingerprintVisualBinding"));

        JsonValue floor = requireObject(root, "alphaFloor");
        AlphaFloorDefinition alphaFloor = new AlphaFloorDefinition(
                requireInt(floor, "productionCoreFactions"),
                requireInt(floor, "requiredPostCoreFactions"),
                requireInt(floor, "militaryBaseHullsPerCoreFaction"),
                requireInt(floor, "civilianSupportBaseHullsPerCoreFaction"),
                requireInt(floor, "sharedCivilianHulls"),
                requireInt(floor, "stationExteriorRoles"),
                requireInt(floor, "signatureStationsPerCoreFaction"),
                requireInt(floor, "recurringNamedNpcsPerCoreFaction"),
                requireInt(floor, "sharedRecurringContacts"),
                requireInt(floor, "generatedNpcRoleArchetypes"),
                requireInt(floor, "factionMissionTemplatesPerCoreFaction"),
                requireInt(floor, "gameWideMissionTemplates"),
                requireInt(floor, "storyChainsPerCoreFaction"),
                requireInt(floor, "specialLocationArchetypes"),
                requireInt(floor, "publicPrivateEventTemplates"));

        List<CutPriorityDefinition> cutPriorities = new ArrayList<>();
        for (JsonValue node = requireArray(root, "cutPriorities").child; node != null; node = node.next) {
            cutPriorities.add(new CutPriorityDefinition(
                    requireNonBlank(node, "scopeId"),
                    enumValue(CutPriority.class, node, "priority"),
                    requireNonBlank(node, "reason")));
        }

        return new Stage22ContentGovernanceCatalog(
                schemaVersion,
                sources,
                hardcodedDefinitions,
                factionIdentities,
                authoringContract,
                alphaFloor,
                cutPriorities);
    }

    private static void validateProductionBaseline(
            Stage22ContentGovernanceCatalog catalog,
            ClassLoader classLoader) {
        Set<String> actualSources = catalog.getSources().stream()
                .map(SourceDefinition::resourcePath)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!actualSources.equals(REQUIRED_PRE_STAGE22_SOURCES)) {
            throw new IllegalStateException(
                    "Stage-22 governance source inventory drift: expected " + REQUIRED_PRE_STAGE22_SOURCES
                            + " but found " + actualSources);
        }
        for (String resource : REQUIRED_PRE_STAGE22_SOURCES) {
            if (classLoader.getResource(resource) == null) {
                throw new IllegalStateException("Governed Stage-22 source is missing from classpath: " + resource);
            }
        }

        Set<String> identities = catalog.getFactionIdentities().stream()
                .map(FactionIdentityDefinition::stableFactionId)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (!identities.equals(REQUIRED_FACTION_IDENTITIES)) {
            throw new IllegalStateException(
                    "Stage-22 faction identity inventory drift: expected " + REQUIRED_FACTION_IDENTITIES
                            + " but found " + identities);
        }

        requireCoreBinding(catalog, "faction.imperial_directorate", "core.empire", "Империя");
        requireCoreBinding(catalog, "faction.industrial_combine", "core.industrial_union", "Индустриальный Союз");
        requireNoCoreBinding(catalog, "faction.frontier_union");
        requireNoCoreBinding(catalog, "faction.free_ports");
        requireNoCoreBinding(catalog, "faction.research_consortium");
        requireNoCoreBinding(catalog, "faction.alpha");
        requireNoCoreBinding(catalog, "faction.beta");

        requireIdentity(catalog, "faction.neutral", IdentityClass.MINOR_AUTHORED);
        requireIdentity(catalog, "faction.miners", IdentityClass.MINOR_AUTHORED);
        requireIdentity(catalog, "faction.trade_league", IdentityClass.TRANSNATIONAL_NETWORK);
        requireIdentity(catalog, "faction.alpha", IdentityClass.WORLD_GENERATED);
        requireIdentity(catalog, "faction.beta", IdentityClass.WORLD_GENERATED);

        for (SourceDefinition source : catalog.getSources()) {
            if (source.resourcePath().contains("stage18-")) {
                if (source.maturity() != SourceMaturity.PRODUCTION_FOUNDATION
                        || source.defaultDisposition() != ContentDisposition.PRESERVE) {
                    throw new IllegalStateException("Stage-18 production source must remain preserved: "
                            + source.resourcePath());
                }
            }
            if (source.maturity() == SourceMaturity.PROVISIONAL
                    && source.defaultDisposition() == ContentDisposition.PRESERVE) {
                throw new IllegalStateException("Provisional source lacks Stage-22 disposition: "
                        + source.resourcePath());
            }
        }

        AuthoringManifestContract contract = catalog.getAuthoringContract();
        if (!EnumSet.copyOf(contract.requiredBindingKinds()).equals(EnumSet.allOf(BindingKind.class))) {
            throw new IllegalStateException("Stage-22 authoring contract must govern every binding kind");
        }
        if (!EnumSet.copyOf(contract.requiredAssetStatuses()).equals(EnumSet.allOf(AssetStatus.class))) {
            throw new IllegalStateException("Stage-22 authoring contract must govern every asset status");
        }
        if (!EnumSet.copyOf(contract.requiredContentMaturities()).equals(EnumSet.allOf(ContentMaturity.class))) {
            throw new IllegalStateException("Stage-22 authoring contract must govern every content maturity");
        }
        if (!contract.sourceLanguage().equals("ru")
                || !contract.localizationLanguages().containsAll(Set.of("ru", "en"))
                || !contract.requireProvenance()
                || !contract.requireFitFingerprintVisualBinding()) {
            throw new IllegalStateException("Stage-22 authoring/localization/provenance contract drift");
        }

        AlphaFloorDefinition floor = catalog.getAlphaFloor();
        if (floor.productionCoreFactions() != 2
                || floor.requiredPostCoreFactions() != 0
                || floor.militaryBaseHullsPerCoreFaction() < 6
                || floor.civilianSupportBaseHullsPerCoreFaction() < 3
                || floor.sharedCivilianHulls() < 8
                || floor.stationExteriorRoles() < 10
                || floor.signatureStationsPerCoreFaction() < 3
                || floor.recurringNamedNpcsPerCoreFaction() < 6
                || floor.sharedRecurringContacts() < 6
                || floor.generatedNpcRoleArchetypes() < 24
                || floor.factionMissionTemplatesPerCoreFaction() < 10
                || floor.gameWideMissionTemplates() < 48
                || floor.storyChainsPerCoreFaction() < 2
                || floor.specialLocationArchetypes() < 20
                || floor.publicPrivateEventTemplates() < 60) {
            throw new IllegalStateException("Stage-22 alpha coverage floor is below the accepted production contract");
        }
    }

    private static void requireCoreBinding(
            Stage22ContentGovernanceCatalog catalog,
            String stableId,
            String packageKey,
            String displayName) {
        FactionIdentityDefinition identity = Objects.requireNonNull(
                catalog.findFactionIdentity(stableId), "Missing core compatibility identity " + stableId);
        if (identity.identityClass() != IdentityClass.LEGACY_COMPATIBILITY
                || identity.disposition() != IdentityDisposition.PRESERVE
                || identity.targetStableFactionId() != null
                || !packageKey.equals(identity.canonicalPackageKey())
                || !displayName.equals(identity.canonicalDisplayName())) {
            throw new IllegalStateException("Invalid Stage-22 core compatibility binding: " + stableId);
        }
    }

    private static void requireNoCoreBinding(Stage22ContentGovernanceCatalog catalog, String stableId) {
        FactionIdentityDefinition identity = Objects.requireNonNull(
                catalog.findFactionIdentity(stableId), "Missing compatibility identity " + stableId);
        if (identity.canonicalPackageKey() != null) {
            throw new IllegalStateException("Non-core compatibility identity gained a core package binding: " + stableId);
        }
    }

    private static void requireIdentity(
            Stage22ContentGovernanceCatalog catalog,
            String stableId,
            IdentityClass expectedClass) {
        FactionIdentityDefinition identity = Objects.requireNonNull(
                catalog.findFactionIdentity(stableId), "Missing compatibility identity " + stableId);
        if (identity.identityClass() != expectedClass || identity.disposition() != IdentityDisposition.PRESERVE) {
            throw new IllegalStateException("Invalid compatibility classification: " + stableId);
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
        int integer = value.asInt();
        if (raw != integer) {
            throw new IllegalArgumentException(field + " must be an integer");
        }
        return integer;
    }

    private static boolean requireBoolean(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isBoolean()) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return value.asBoolean();
    }

    private static String requireString(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || !value.isString()) {
            throw new IllegalArgumentException(field + " must be a string");
        }
        return value.asString();
    }

    private static String requireNonBlank(JsonValue node, String field) {
        String value = requireString(node, field).strip();
        if (value.isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value;
    }

    private static String optionalString(JsonValue node, String field) {
        JsonValue value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isString()) {
            throw new IllegalArgumentException(field + " must be a string or null");
        }
        String checked = value.asString().strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(field + " must be non-blank when present");
        }
        return checked;
    }

    private static List<String> stringArray(JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        List<String> result = new ArrayList<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain strings");
            }
            String value = child.asString().strip();
            if (value.isEmpty()) {
                throw new IllegalArgumentException(field + " must not contain blank strings");
            }
            result.add(value);
        }
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> List<E> enumArray(Class<E> type, JsonValue node, String field) {
        JsonValue array = requireArray(node, field);
        List<E> result = new ArrayList<>();
        for (JsonValue child = array.child; child != null; child = child.next) {
            if (!child.isString()) {
                throw new IllegalArgumentException(field + " must contain enum names");
            }
            try {
                result.add(Enum.valueOf(type, child.asString()));
            } catch (IllegalArgumentException exception) {
                throw new IllegalArgumentException("Unknown " + field + " value: " + child.asString(), exception);
            }
        }
        return List.copyOf(result);
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, JsonValue node, String field) {
        String raw = requireString(node, field);
        try {
            return Enum.valueOf(type, raw);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Unknown " + field + " value: " + raw, exception);
        }
    }
}
