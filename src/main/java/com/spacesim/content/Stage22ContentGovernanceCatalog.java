package com.spacesim.content;

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
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Immutable Stage-22.0 governance catalog for content sources, legacy identities and alpha authoring gates.
 *
 * <p>This catalog is deliberately downstream of the existing {@link ContentCatalog} and world faction
 * identity authority. It classifies and inventories already-existing definitions, records explicit
 * compatibility/disposition decisions and defines authoring contracts for later Stage-22 packages. It
 * does not create inventory, fleets, diplomacy state, production output or a second faction registry.</p>
 */
public final class Stage22ContentGovernanceCatalog {
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");
    private static final Pattern FACTION_ID = Pattern.compile(
            "faction\\.[a-z0-9]+(?:[._-][a-z0-9]+)*");

    private final int schemaVersion;
    private final List<SourceDefinition> sources;
    private final List<HardcodedDefinition> hardcodedDefinitions;
    private final List<FactionIdentityDefinition> factionIdentities;
    private final AuthoringManifestContract authoringContract;
    private final AlphaFloorDefinition alphaFloor;
    private final List<CutPriorityDefinition> cutPriorities;
    private final Map<String, SourceDefinition> sourcesByPath;
    private final Map<String, HardcodedDefinition> hardcodedById;
    private final Map<String, FactionIdentityDefinition> identitiesById;
    private final String fingerprint;

    /**
     * Creates one validated immutable governance catalog.
     *
     * @param schemaVersion governance schema version
     * @param sources complete governed JSON source list
     * @param hardcodedDefinitions explicit Java/procedural definitions outside JSON sources
     * @param factionIdentities current authored/bootstrap faction dispositions
     * @param authoringContract authoring/binding contract for later Stage-22 packages
     * @param alphaFloor approved Stage-22 alpha coverage floor
     * @param cutPriorities explicit scope/cut priorities
     */
    public Stage22ContentGovernanceCatalog(
            int schemaVersion,
            List<SourceDefinition> sources,
            List<HardcodedDefinition> hardcodedDefinitions,
            List<FactionIdentityDefinition> factionIdentities,
            AuthoringManifestContract authoringContract,
            AlphaFloorDefinition alphaFloor,
            List<CutPriorityDefinition> cutPriorities) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Stage-22 governance schema version must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.sources = sortedCopy(sources, Comparator.comparing(SourceDefinition::resourcePath));
        this.hardcodedDefinitions = sortedCopy(
                hardcodedDefinitions, Comparator.comparing(HardcodedDefinition::id));
        this.factionIdentities = sortedCopy(
                factionIdentities, Comparator.comparing(FactionIdentityDefinition::stableFactionId));
        this.authoringContract = Objects.requireNonNull(authoringContract, "Authoring contract not set");
        this.alphaFloor = Objects.requireNonNull(alphaFloor, "Alpha floor not set");
        this.cutPriorities = sortedCopy(cutPriorities, Comparator.comparing(CutPriorityDefinition::scopeId));
        this.sourcesByPath = uniqueMap(this.sources, SourceDefinition::resourcePath, "source resource");
        this.hardcodedById = uniqueMap(this.hardcodedDefinitions, HardcodedDefinition::id, "hardcoded definition");
        this.identitiesById = uniqueMap(this.factionIdentities, FactionIdentityDefinition::stableFactionId, "faction identity");
        this.fingerprint = computeFingerprint();
    }

    /** @return governance schema version */
    public int getSchemaVersion() {
        return schemaVersion;
    }

    /** @return complete immutable governed JSON source list */
    public List<SourceDefinition> getSources() {
        return sources;
    }

    /** @return explicit hardcoded/procedural content definitions */
    public List<HardcodedDefinition> getHardcodedDefinitions() {
        return hardcodedDefinitions;
    }

    /** @return current explicit faction identity dispositions */
    public List<FactionIdentityDefinition> getFactionIdentities() {
        return factionIdentities;
    }

    /** @return immutable later-stage authoring contract */
    public AuthoringManifestContract getAuthoringContract() {
        return authoringContract;
    }

    /** @return approved Stage-22 alpha floor */
    public AlphaFloorDefinition getAlphaFloor() {
        return alphaFloor;
    }

    /** @return explicit scope/cut priorities */
    public List<CutPriorityDefinition> getCutPriorities() {
        return cutPriorities;
    }

    /** @return deterministic SHA-256 fingerprint of governance decisions */
    public String getFingerprint() {
        return fingerprint;
    }

    /** @return governed source or {@code null} */
    public SourceDefinition findSource(String resourcePath) {
        return sourcesByPath.get(resourcePath);
    }

    /** @return explicit hardcoded definition or {@code null} */
    public HardcodedDefinition findHardcodedDefinition(String id) {
        return hardcodedById.get(id);
    }

    /** @return explicit faction disposition or {@code null} */
    public FactionIdentityDefinition findFactionIdentity(String stableFactionId) {
        return identitiesById.get(stableFactionId);
    }

    /**
     * Resolves the approved player-facing canonical display identity without rewriting the stable save ID.
     *
     * @param stableFactionId current stable faction ID
     * @param fallbackDisplayName existing authored/bootstrap display name
     * @return Stage-22-approved display name, or the supplied fallback when no override is authored
     */
    public String canonicalDisplayName(String stableFactionId, String fallbackDisplayName) {
        FactionIdentityDefinition definition = identitiesById.get(stableFactionId);
        if (definition == null || definition.canonicalDisplayName() == null) {
            return fallbackDisplayName;
        }
        return definition.canonicalDisplayName();
    }

    /**
     * Returns the core package key bound to a compatibility stable ID, if any.
     *
     * @param stableFactionId stable save/runtime faction ID
     * @return core package key or {@code null}
     */
    public String canonicalPackageKey(String stableFactionId) {
        FactionIdentityDefinition definition = identitiesById.get(stableFactionId);
        return definition == null ? null : definition.canonicalPackageKey();
    }

    private String computeFingerprint() {
        StringBuilder canonical = new StringBuilder(8192);
        canonical.append("schema=").append(schemaVersion).append('\n');
        for (SourceDefinition source : sources) {
            canonical.append("source|").append(source.resourcePath()).append('|')
                    .append(source.domain()).append('|').append(source.maturity()).append('|')
                    .append(source.defaultDisposition()).append('|').append(source.semanticReason()).append('\n');
        }
        for (HardcodedDefinition definition : hardcodedDefinitions) {
            canonical.append("hardcoded|").append(definition.id()).append('|')
                    .append(definition.source()).append('|').append(definition.maturity()).append('|')
                    .append(definition.disposition()).append('|').append(definition.semanticReason()).append('|');
            definition.references().stream().sorted().forEach(value -> canonical.append(value).append(','));
            canonical.append('\n');
        }
        for (FactionIdentityDefinition identity : factionIdentities) {
            canonical.append("identity|").append(identity.stableFactionId()).append('|')
                    .append(identity.identityClass()).append('|').append(identity.disposition()).append('|')
                    .append(nullToEmpty(identity.targetStableFactionId())).append('|')
                    .append(nullToEmpty(identity.canonicalPackageKey())).append('|')
                    .append(nullToEmpty(identity.canonicalDisplayName())).append('|')
                    .append(identity.sourceVersionRange()).append('|')
                    .append(identity.saveBehavior()).append('|').append(identity.collisionBehavior()).append('|')
                    .append(identity.semanticReason()).append('\n');
        }
        canonical.append("authoring|").append(authoringContract.sourceLanguage()).append('|')
                .append(authoringContract.requireProvenance()).append('|')
                .append(authoringContract.requireFitFingerprintVisualBinding()).append('|');
        authoringContract.requiredBindingKinds().stream().sorted().forEach(value -> canonical.append(value).append(','));
        canonical.append('|');
        authoringContract.requiredAssetStatuses().stream().sorted().forEach(value -> canonical.append(value).append(','));
        canonical.append('|');
        authoringContract.requiredContentMaturities().stream().sorted().forEach(value -> canonical.append(value).append(','));
        canonical.append('|');
        authoringContract.localizationLanguages().stream().sorted().forEach(value -> canonical.append(value).append(','));
        canonical.append('\n');
        canonical.append("alpha|").append(alphaFloor.canonicalForm()).append('\n');
        for (CutPriorityDefinition cut : cutPriorities) {
            canonical.append("cut|").append(cut.scopeId()).append('|').append(cut.priority()).append('|')
                    .append(cut.reason()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> sortedCopy(List<T> source, Comparator<T> comparator) {
        ArrayList<T> copy = new ArrayList<>(Objects.requireNonNull(source, "Governance list not set"));
        copy.replaceAll(value -> Objects.requireNonNull(value, "Governance entry not set"));
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static <T> Map<String, T> uniqueMap(
            List<T> values,
            java.util.function.Function<T, String> keyFunction,
            String label) {
        LinkedHashMap<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String key = keyFunction.apply(value);
            if (result.putIfAbsent(key, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + key);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    static String requireContentId(String value, String label) {
        String id = Objects.requireNonNull(value, label + " not set").strip();
        if (!CONTENT_ID.matcher(id).matches()) {
            throw new IllegalArgumentException(label + " must use lower-case dotted content-ID syntax: " + id);
        }
        return id;
    }

    static String requireFactionId(String value) {
        String id = Objects.requireNonNull(value, "Faction stable ID not set").strip();
        if (!FACTION_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Faction stable ID must use lower-case faction.* syntax: " + id);
        }
        return id;
    }

    static String requireNonBlank(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return checked;
    }

    static String optionalNonBlank(String value, String label) {
        return value == null ? null : requireNonBlank(value, label);
    }

    /** Maturity of one governed source or hardcoded definition at the Stage-22 entry gate. */
    public enum SourceMaturity {
        /** Compatibility-era content retained because stable saves/runtime still reference it. */
        LEGACY_COMPATIBILITY,
        /** Accepted common physical/economic foundation retained as Stage-22 authority. */
        PRODUCTION_FOUNDATION,
        /** Production-valid test/prototype content requiring an explicit Stage-22 disposition. */
        PROVISIONAL
    }

    /** Explicit Stage-22 disposition of current content definitions. */
    public enum ContentDisposition {
        /** Keep current definition/authority unchanged. */
        PRESERVE,
        /** Explicitly accept the current authored definition as Stage-22 content. */
        PROMOTE,
        /** Keep the role/need but author a new production definition through the same authority. */
        REAUTHOR,
        /** Replace the current temporary definition with a different production definition. */
        REPLACE,
        /** Remove the definition from production after references are migrated or quarantined. */
        RETIRE
    }

    /** Stage-22 governance class for one currently addressable faction identity. */
    public enum IdentityClass {
        MAJOR_AUTHORED,
        MINOR_AUTHORED,
        TRANSNATIONAL_NETWORK,
        WORLD_GENERATED,
        SCENARIO_ONLY,
        TEST_FIXTURE,
        LEGACY_COMPATIBILITY
    }

    /** Stable-ID migration/disposition decision. */
    public enum IdentityDisposition {
        PRESERVE,
        ALIAS,
        MIGRATE,
        RETIRE_TEST_ONLY
    }

    /** Binding families that later production manifests must support. */
    public enum BindingKind {
        SHIP_VISUAL,
        CHARACTER_VISUAL,
        NPC,
        MISSION,
        LOCALIZATION,
        VFX,
        AUDIO,
        UI_ICON
    }

    /** Production status vocabulary for visual/audio assets. */
    public enum AssetStatus {
        CONCEPT,
        ENGINEERING_APPROVED,
        PRODUCTION,
        DEPRECATED
    }

    /** Maturity vocabulary for authored content definitions. */
    public enum ContentMaturity {
        SEED,
        CANDIDATE,
        VALIDATED,
        FROZEN
    }

    /** Product-scope cut priority used by Stage-22 authoring. */
    public enum CutPriority {
        CRITICAL,
        MUST,
        SHOULD,
        DEFERRED
    }

    /**
     * One classpath JSON source participating in the Stage-22 entry inventory.
     *
     * @param resourcePath classpath resource path
     * @param domain concise governance domain name
     * @param maturity current maturity classification
     * @param defaultDisposition effective decision for definitions in this source
     * @param semanticReason reason the disposition is safe/required
     */
    public record SourceDefinition(
            String resourcePath,
            String domain,
            SourceMaturity maturity,
            ContentDisposition defaultDisposition,
            String semanticReason) {
        public SourceDefinition {
            resourcePath = requireNonBlank(resourcePath, "Source resource path");
            if (!resourcePath.startsWith("data/content/") || !resourcePath.endsWith(".json")) {
                throw new IllegalArgumentException("Governed source must be a data/content JSON resource: " + resourcePath);
            }
            domain = requireNonBlank(domain, "Source domain");
            maturity = Objects.requireNonNull(maturity, "Source maturity not set");
            defaultDisposition = Objects.requireNonNull(defaultDisposition, "Source disposition not set");
            semanticReason = requireNonBlank(semanticReason, "Source semantic reason");
            if (maturity == SourceMaturity.PRODUCTION_FOUNDATION
                    && defaultDisposition != ContentDisposition.PRESERVE
                    && defaultDisposition != ContentDisposition.PROMOTE) {
                throw new IllegalArgumentException("Production-foundation source cannot be reauthored by default");
            }
            if (maturity == SourceMaturity.PROVISIONAL
                    && defaultDisposition == ContentDisposition.PRESERVE) {
                throw new IllegalArgumentException("Provisional source requires explicit non-preserve disposition");
            }
        }
    }

    /**
     * Explicit definition created procedurally in Java rather than by a scanned JSON resource.
     *
     * @param id stable content/faction ID
     * @param source stable source descriptor
     * @param maturity current maturity classification
     * @param disposition Stage-22 disposition
     * @param references stable IDs referenced by the generated definition
     * @param semanticReason rationale for the decision
     */
    public record HardcodedDefinition(
            String id,
            String source,
            SourceMaturity maturity,
            ContentDisposition disposition,
            List<String> references,
            String semanticReason) {
        public HardcodedDefinition {
            id = requireContentId(id, "Hardcoded definition ID");
            source = requireNonBlank(source, "Hardcoded definition source");
            maturity = Objects.requireNonNull(maturity, "Hardcoded maturity not set");
            disposition = Objects.requireNonNull(disposition, "Hardcoded disposition not set");
            ArrayList<String> checkedReferences = new ArrayList<>();
            for (String reference : Objects.requireNonNull(references, "Hardcoded references not set")) {
                checkedReferences.add(requireContentId(reference, "Hardcoded reference ID"));
            }
            checkedReferences.sort(String::compareTo);
            references = List.copyOf(checkedReferences);
            semanticReason = requireNonBlank(semanticReason, "Hardcoded semantic reason");
            if (maturity == SourceMaturity.PROVISIONAL && disposition == ContentDisposition.PRESERVE) {
                throw new IllegalArgumentException("Provisional hardcoded definition requires non-preserve disposition");
            }
        }
    }

    /**
     * Explicit Stage-22.0 faction identity compatibility/migration decision.
     *
     * @param stableFactionId current stable runtime/save faction ID
     * @param identityClass governance class
     * @param disposition stable-ID disposition
     * @param targetStableFactionId target for ALIAS/MIGRATE, otherwise {@code null}
     * @param canonicalPackageKey public/core package binding key or {@code null}
     * @param canonicalDisplayName approved public display identity or {@code null}
     * @param sourceVersionRange supported source version statement
     * @param saveBehavior exact save/load behavior
     * @param collisionBehavior deterministic collision behavior
     * @param semanticReason rationale for the disposition
     */
    public record FactionIdentityDefinition(
            String stableFactionId,
            IdentityClass identityClass,
            IdentityDisposition disposition,
            String targetStableFactionId,
            String canonicalPackageKey,
            String canonicalDisplayName,
            String sourceVersionRange,
            String saveBehavior,
            String collisionBehavior,
            String semanticReason) {
        public FactionIdentityDefinition {
            stableFactionId = requireFactionId(stableFactionId);
            identityClass = Objects.requireNonNull(identityClass, "Identity class not set");
            disposition = Objects.requireNonNull(disposition, "Identity disposition not set");
            targetStableFactionId = targetStableFactionId == null ? null : requireFactionId(targetStableFactionId);
            canonicalPackageKey = optionalNonBlank(canonicalPackageKey, "Canonical package key");
            canonicalDisplayName = optionalNonBlank(canonicalDisplayName, "Canonical display name");
            sourceVersionRange = requireNonBlank(sourceVersionRange, "Source version range");
            saveBehavior = requireNonBlank(saveBehavior, "Save behavior");
            collisionBehavior = requireNonBlank(collisionBehavior, "Collision behavior");
            semanticReason = requireNonBlank(semanticReason, "Identity semantic reason");
            boolean requiresTarget = disposition == IdentityDisposition.ALIAS || disposition == IdentityDisposition.MIGRATE;
            if (requiresTarget != (targetStableFactionId != null)) {
                throw new IllegalArgumentException("Alias/migrate disposition must have exactly one target stable ID");
            }
            if (targetStableFactionId != null && targetStableFactionId.equals(stableFactionId)) {
                throw new IllegalArgumentException("Faction identity cannot alias/migrate to itself");
            }
        }
    }

    /**
     * Common authoring manifest vocabulary required before bulk Stage-22 asset/content production.
     *
     * @param requiredBindingKinds required runtime binding families
     * @param requiredAssetStatuses complete asset status vocabulary
     * @param requiredContentMaturities complete content maturity vocabulary
     * @param sourceLanguage canonical source-copy language
     * @param localizationLanguages required localization path languages
     * @param requireProvenance whether shipped assets require provenance/license metadata
     * @param requireFitFingerprintVisualBinding whether fit-changing visuals require fit fingerprint binding
     */
    public record AuthoringManifestContract(
            List<BindingKind> requiredBindingKinds,
            List<AssetStatus> requiredAssetStatuses,
            List<ContentMaturity> requiredContentMaturities,
            String sourceLanguage,
            List<String> localizationLanguages,
            boolean requireProvenance,
            boolean requireFitFingerprintVisualBinding) {
        public AuthoringManifestContract {
            requiredBindingKinds = immutableDistinctEnumList(requiredBindingKinds, BindingKind.class, "binding kinds");
            requiredAssetStatuses = immutableDistinctEnumList(requiredAssetStatuses, AssetStatus.class, "asset statuses");
            requiredContentMaturities = immutableDistinctEnumList(
                    requiredContentMaturities, ContentMaturity.class, "content maturities");
            sourceLanguage = requireNonBlank(sourceLanguage, "Source language");
            ArrayList<String> languages = new ArrayList<>();
            for (String language : Objects.requireNonNull(localizationLanguages, "Localization languages not set")) {
                String checked = requireNonBlank(language, "Localization language").toLowerCase(java.util.Locale.ROOT);
                if (!languages.contains(checked)) {
                    languages.add(checked);
                }
            }
            languages.sort(String::compareTo);
            localizationLanguages = List.copyOf(languages);
        }

        private static <E extends Enum<E> & Comparable<E>> List<E> immutableDistinctEnumList(
                List<E> values, Class<E> enumClass, String label) {
            Objects.requireNonNull(values, label + " not set");
            EnumSet<E> set = EnumSet.noneOf(enumClass);
            for (E value : values) {
                if (!set.add(Objects.requireNonNull(value, label + " entry not set"))) {
                    throw new IllegalArgumentException("Duplicate " + label + " entry: " + value);
                }
            }
            ArrayList<E> ordered = new ArrayList<>(set);
            ordered.sort(Comparator.naturalOrder());
            return List.copyOf(ordered);
        }
    }

    /**
     * Quantified Stage-22 alpha floor agreed before bulk authoring.
     *
     * @param productionCoreFactions production-complete sovereign core factions
     * @param requiredPostCoreFactions production-complete post-core factions required now
     * @param militaryBaseHullsPerCoreFaction military base-hull floor per core faction
     * @param civilianSupportBaseHullsPerCoreFaction faction civilian/support base-hull floor
     * @param sharedCivilianHulls shared/licensed civilian hull floor
     * @param stationExteriorRoles combined functional station exterior-role floor
     * @param signatureStationsPerCoreFaction signature station variants per core faction
     * @param recurringNamedNpcsPerCoreFaction recurring named NPC floor per core faction
     * @param sharedRecurringContacts shared/minor/independent contact floor
     * @param generatedNpcRoleArchetypes generated NPC role-archetype floor
     * @param factionMissionTemplatesPerCoreFaction faction-facing mission template floor
     * @param gameWideMissionTemplates final game-wide parametric mission template floor
     * @param storyChainsPerCoreFaction authored short faction-chain floor
     * @param specialLocationArchetypes special-location archetype floor
     * @param publicPrivateEventTemplates public/private event template planning floor
     */
    public record AlphaFloorDefinition(
            int productionCoreFactions,
            int requiredPostCoreFactions,
            int militaryBaseHullsPerCoreFaction,
            int civilianSupportBaseHullsPerCoreFaction,
            int sharedCivilianHulls,
            int stationExteriorRoles,
            int signatureStationsPerCoreFaction,
            int recurringNamedNpcsPerCoreFaction,
            int sharedRecurringContacts,
            int generatedNpcRoleArchetypes,
            int factionMissionTemplatesPerCoreFaction,
            int gameWideMissionTemplates,
            int storyChainsPerCoreFaction,
            int specialLocationArchetypes,
            int publicPrivateEventTemplates) {
        public AlphaFloorDefinition {
            int[] values = {
                    productionCoreFactions, requiredPostCoreFactions, militaryBaseHullsPerCoreFaction,
                    civilianSupportBaseHullsPerCoreFaction, sharedCivilianHulls, stationExteriorRoles,
                    signatureStationsPerCoreFaction, recurringNamedNpcsPerCoreFaction,
                    sharedRecurringContacts, generatedNpcRoleArchetypes,
                    factionMissionTemplatesPerCoreFaction, gameWideMissionTemplates,
                    storyChainsPerCoreFaction, specialLocationArchetypes, publicPrivateEventTemplates
            };
            for (int value : values) {
                if (value < 0) {
                    throw new IllegalArgumentException("Stage-22 alpha floors must not be negative");
                }
            }
        }

        String canonicalForm() {
            return productionCoreFactions + "," + requiredPostCoreFactions + ","
                    + militaryBaseHullsPerCoreFaction + "," + civilianSupportBaseHullsPerCoreFaction + ","
                    + sharedCivilianHulls + "," + stationExteriorRoles + "," + signatureStationsPerCoreFaction + ","
                    + recurringNamedNpcsPerCoreFaction + "," + sharedRecurringContacts + ","
                    + generatedNpcRoleArchetypes + "," + factionMissionTemplatesPerCoreFaction + ","
                    + gameWideMissionTemplates + "," + storyChainsPerCoreFaction + ","
                    + specialLocationArchetypes + "," + publicPrivateEventTemplates;
        }
    }

    /**
     * Explicit product-scope priority used when Stage-22 content must be cut rather than widened.
     *
     * @param scopeId stable scope key
     * @param priority priority class
     * @param reason why the scope has that priority
     */
    public record CutPriorityDefinition(String scopeId, CutPriority priority, String reason) {
        public CutPriorityDefinition {
            scopeId = requireContentId(scopeId, "Cut scope ID");
            priority = Objects.requireNonNull(priority, "Cut priority not set");
            reason = requireNonBlank(reason, "Cut priority reason");
        }
    }
}
