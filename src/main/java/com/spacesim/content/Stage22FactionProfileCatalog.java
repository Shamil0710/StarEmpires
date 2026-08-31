package com.spacesim.content;

import com.spacesim.content.Stage22ContentGovernanceCatalog.AssetStatus;
import com.spacesim.content.Stage22ContentGovernanceCatalog.ContentMaturity;
import com.spacesim.content.Stage22ContentGovernanceCatalog.IdentityClass;
import com.spacesim.world.FactionDoctrineState;
import com.spacesim.world.FactionStrategicDoctrineProfile;
import com.spacesim.world.StrategicGoalType;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable Stage-22.1 systemic-profile catalog for authored sovereign faction packages.
 *
 * <p>The catalog contains declarative inputs and references only. It cannot mutate faction identity,
 * doctrine, industry, fleets, diplomacy, territory, knowledge or recovery state. Callers explicitly
 * pass the projected {@link FactionDoctrineState} and {@link FactionStrategicDoctrineProfile} into
 * the already accepted common authorities.</p>
 */
public final class Stage22FactionProfileCatalog {
    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");
    private static final Pattern LANGUAGE_ID = Pattern.compile("[a-z]{2}(?:-[A-Z]{2})?");

    private final int schemaVersion;
    private final String catalogVersion;
    private final List<DoctrineProfileDefinition> doctrineProfiles;
    private final List<PolicyBindingDefinition> policyBindings;
    private final List<ManifestReferenceDefinition> manifestReferences;
    private final List<VisualProfileDefinition> visualProfiles;
    private final List<LocalizationDefinition> localizations;
    private final List<SystemicProfileDefinition> systemicProfiles;
    private final Map<String, DoctrineProfileDefinition> doctrineById;
    private final Map<String, PolicyBindingDefinition> policyById;
    private final Map<String, ManifestReferenceDefinition> manifestById;
    private final Map<String, VisualProfileDefinition> visualById;
    private final Map<String, LocalizationDefinition> localizationById;
    private final Map<String, SystemicProfileDefinition> profileById;
    private final Map<String, SystemicProfileDefinition> profileByStableFactionId;
    private final String fingerprint;

    Stage22FactionProfileCatalog(
            int schemaVersion,
            String catalogVersion,
            List<DoctrineProfileDefinition> doctrineProfiles,
            List<PolicyBindingDefinition> policyBindings,
            List<ManifestReferenceDefinition> manifestReferences,
            List<VisualProfileDefinition> visualProfiles,
            List<LocalizationDefinition> localizations,
            List<SystemicProfileDefinition> systemicProfiles) {
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Stage-22 profile schema version must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.catalogVersion = requireContentId(catalogVersion, "catalogVersion");
        this.doctrineProfiles = sorted(doctrineProfiles, DoctrineProfileDefinition::id);
        this.policyBindings = sorted(policyBindings, PolicyBindingDefinition::id);
        this.manifestReferences = sorted(manifestReferences, ManifestReferenceDefinition::id);
        this.visualProfiles = sorted(visualProfiles, VisualProfileDefinition::id);
        this.localizations = sorted(localizations, LocalizationDefinition::id);
        this.systemicProfiles = sorted(systemicProfiles, SystemicProfileDefinition::profileId);
        this.doctrineById = index(this.doctrineProfiles, DoctrineProfileDefinition::id, "doctrine profile");
        this.policyById = index(this.policyBindings, PolicyBindingDefinition::id, "policy binding");
        this.manifestById = index(this.manifestReferences, ManifestReferenceDefinition::id, "manifest reference");
        this.visualById = index(this.visualProfiles, VisualProfileDefinition::id, "visual profile");
        this.localizationById = index(this.localizations, LocalizationDefinition::id, "localization");
        this.profileById = index(this.systemicProfiles, SystemicProfileDefinition::profileId, "systemic profile");
        this.profileByStableFactionId = index(
                this.systemicProfiles,
                SystemicProfileDefinition::stableFactionId,
                "systemic profile stable faction ID");
        this.fingerprint = computeFingerprint();
    }

    /** @return exact supported profile schema version */
    public int schemaVersion() {
        return schemaVersion;
    }

    /** @return stable semantic catalog version ID */
    public String catalogVersion() {
        return catalogVersion;
    }

    /** @return deterministic doctrine profile definitions */
    public List<DoctrineProfileDefinition> doctrineProfiles() {
        return doctrineProfiles;
    }

    /** @return deterministic common-authority policy bindings */
    public List<PolicyBindingDefinition> policyBindings() {
        return policyBindings;
    }

    /** @return deterministic authored-content manifest references */
    public List<ManifestReferenceDefinition> manifestReferences() {
        return manifestReferences;
    }

    /** @return deterministic ship and character visual profile references */
    public List<VisualProfileDefinition> visualProfiles() {
        return visualProfiles;
    }

    /** @return deterministic localization definitions */
    public List<LocalizationDefinition> localizations() {
        return localizations;
    }

    /** @return deterministic systemic faction profiles */
    public List<SystemicProfileDefinition> systemicProfiles() {
        return systemicProfiles;
    }

    /** @return lowercase SHA-256 semantic fingerprint */
    public String fingerprint() {
        return fingerprint;
    }

    /**
     * Finds one doctrine definition.
     *
     * @param id doctrine profile ID
     * @return doctrine definition, or {@code null}
     */
    public DoctrineProfileDefinition findDoctrine(String id) {
        return doctrineById.get(id);
    }

    /**
     * Finds one common-authority policy binding.
     *
     * @param id policy binding ID
     * @return policy binding, or {@code null}
     */
    public PolicyBindingDefinition findPolicy(String id) {
        return policyById.get(id);
    }

    /**
     * Finds one authored-content manifest reference.
     *
     * @param id manifest reference ID
     * @return manifest reference, or {@code null}
     */
    public ManifestReferenceDefinition findManifest(String id) {
        return manifestById.get(id);
    }

    /**
     * Finds one visual profile.
     *
     * @param id visual profile ID
     * @return visual profile, or {@code null}
     */
    public VisualProfileDefinition findVisual(String id) {
        return visualById.get(id);
    }

    /**
     * Finds one localization definition.
     *
     * @param id localization ID
     * @return localization definition, or {@code null}
     */
    public LocalizationDefinition findLocalization(String id) {
        return localizationById.get(id);
    }

    /**
     * Finds one systemic profile by profile ID.
     *
     * @param id profile ID
     * @return systemic profile, or {@code null}
     */
    public SystemicProfileDefinition findProfile(String id) {
        return profileById.get(id);
    }

    /**
     * Finds one systemic profile by authoritative stable faction ID.
     *
     * @param stableFactionId authoritative runtime/save stable faction ID
     * @return systemic profile, or {@code null}
     */
    public SystemicProfileDefinition findProfileForFaction(String stableFactionId) {
        return profileByStableFactionId.get(stableFactionId);
    }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(16_384);
        out.append("schema|").append(schemaVersion).append('|').append(catalogVersion).append('\n');
        for (DoctrineProfileDefinition value : doctrineProfiles) {
            FactionDoctrineState doctrine = value.institutionalDoctrine();
            out.append("doctrine|").append(value.id()).append('|')
                    .append(doctrine.tradeOpenness()).append('|')
                    .append(doctrine.securityPosture()).append('|')
                    .append(doctrine.expansionPreference()).append('|')
                    .append(doctrine.sovereigntySensitivity()).append('|')
                    .append(doctrine.treatyLegalism()).append('|')
                    .append(doctrine.interventionism()).append('|')
                    .append(doctrine.economicResiliencePriority()).append('|');
            for (StrategicGoalType type : StrategicGoalType.values()) {
                out.append(type.wireId()).append('=').append(value.strategicGoalPreferences().get(type)).append(',');
            }
            out.append('\n');
        }
        for (PolicyBindingDefinition value : policyBindings) {
            out.append("policy|").append(value.id()).append('|').append(value.kind()).append('|')
                    .append(value.authoritySeam()).append('|')
                    .append(String.join(",", value.dependsOn())).append('|')
                    .append(value.semanticIntent()).append('\n');
        }
        for (ManifestReferenceDefinition value : manifestReferences) {
            out.append("manifest|").append(value.id()).append('|').append(value.packageKey()).append('|')
                    .append(value.scope()).append('|').append(value.maturity()).append('|');
            for (RoleProductionBindingDefinition role : value.roleBindings()) {
                out.append(role.roleId()).append(',').append(role.fitId()).append(',')
                        .append(role.productionPathRef()).append(',').append(role.visualProfileRef()).append(';');
            }
            out.append('\n');
        }
        for (VisualProfileDefinition value : visualProfiles) {
            out.append("visual|").append(value.id()).append('|').append(value.kind()).append('|')
                    .append(value.packageKey()).append('|').append(value.status()).append('|')
                    .append(value.authorityDocument()).append('\n');
        }
        for (LocalizationDefinition value : localizations) {
            out.append("localization|").append(value.id()).append('|').append(value.namespace()).append('|')
                    .append(value.sourceLanguage()).append('|').append(String.join(",", value.languages())).append('\n');
        }
        for (SystemicProfileDefinition value : systemicProfiles) {
            out.append("profile|").append(value.profileId()).append('|').append(value.stableFactionId()).append('|')
                    .append(value.packageKey()).append('|').append(value.profileVersion()).append('|')
                    .append(value.identityClass()).append('|').append(value.doctrineProfileRef()).append('|')
                    .append(value.industrialPolicyRef()).append('|').append(value.procurementPolicyRef()).append('|')
                    .append(value.logisticsPolicyRef()).append('|').append(value.fleetDoctrineRef()).append('|')
                    .append(value.diplomacyPolicyRef()).append('|').append(value.territoryPolicyRef()).append('|')
                    .append(value.knowledgePolicyRef()).append('|').append(value.recoveryPolicyRef()).append('|')
                    .append(value.authoredContentManifestRef()).append('|').append(value.shipVisualProfileRef()).append('|')
                    .append(value.characterVisualProfileRef()).append('|').append(value.localizationRef()).append('|')
                    .append(String.join(",", value.compatibilityAliases())).append('\n');
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
            throw new IllegalArgumentException(label + " must be a lower-case dotted content ID: " + checked);
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

    private static String requireLanguage(String value) {
        String checked = requireText(value, "language");
        if (!LANGUAGE_ID.matcher(checked).matches()) {
            throw new IllegalArgumentException("Unsupported language tag: " + checked);
        }
        return checked;
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

    /** Kinds of declarative faction policy references admitted by the common profile contract. */
    public enum PolicyKind {
        /** Industrial configuration consumed by Stage-18 catalogs and runtimes. */ INDUSTRIAL,
        /** Treasury/order configuration consumed by common faction policy commands. */ PROCUREMENT,
        /** Physical freight and supply configuration. */ LOGISTICS,
        /** Fleet composition/readiness decision configuration. */ FLEET,
        /** Treaty/crisis decision configuration. */ DIPLOMACY,
        /** Claim/control decision configuration. */ TERRITORY,
        /** Actor-bounded observation/knowledge configuration. */ KNOWLEDGE,
        /** Repair/replacement/recovery decision configuration. */ RECOVERY
    }

    /** Existing mutable authority seam consumed by one data-only policy binding. */
    public enum AuthoritySeam {
        /** Stage-18 catalog and industrial-state authorities. */ STAGE18_INDUSTRY,
        /** Stage-17 common faction policy command path. */ FACTION_POLICY_COMMAND,
        /** Stage-20 physical freight/order runtime. */ STAGE20_FREIGHT,
        /** Stage-21D fleet readiness/order authority. */ STAGE21_FLEET_COMMAND,
        /** Stage-17E/21C diplomacy and legal lifecycle. */ FACTION_DIPLOMACY,
        /** Stage-17D/21F territory and control lifecycle. */ TERRITORIAL_CONTROL,
        /** Stage-20 discovery and Stage-21 actor-bounded observation state. */ DISCOVERY_KNOWLEDGE,
        /** Stage-21G finite settlement repair/replacement authority. */ SETTLEMENT_RECOVERY
    }

    /** Product boundary of one declared authored-content manifest. */
    public enum PackageScope {
        /** Required Empire/Industrial Union package. */ CORE,
        /** Explicitly deferred post-core sovereign package. */ POST_CORE
    }

    /** Visual profile media family. */
    public enum VisualKind {
        /** Ship/station engineering and sprite language. */ SHIP,
        /** Character Master Prompt faction overlay. */ CHARACTER
    }

    /**
     * Doctrine inputs projected into existing common doctrine types.
     *
     * @param id stable doctrine profile ID
     * @param institutionalDoctrine persistent bounded institutional axes
     * @param strategicGoalPreferences complete Stage-21 strategic-goal preference map
     */
    public record DoctrineProfileDefinition(
            String id,
            FactionDoctrineState institutionalDoctrine,
            Map<StrategicGoalType, Integer> strategicGoalPreferences) {
        /**
         * Validates and freezes one doctrine profile.
         *
         * @param id stable doctrine profile ID
         * @param institutionalDoctrine persistent bounded institutional axes
         * @param strategicGoalPreferences complete strategic preference map in basis points
         */
        public DoctrineProfileDefinition {
            id = requireContentId(id, "doctrine profile id");
            institutionalDoctrine = Objects.requireNonNull(institutionalDoctrine, "institutionalDoctrine");
            Objects.requireNonNull(strategicGoalPreferences, "strategicGoalPreferences");
            EnumMap<StrategicGoalType, Integer> checked = new EnumMap<>(StrategicGoalType.class);
            for (StrategicGoalType type : StrategicGoalType.values()) {
                Integer value = Objects.requireNonNull(
                        strategicGoalPreferences.get(type), "Missing strategic preference for " + type);
                if (value < 0 || value > 10_000) {
                    throw new IllegalArgumentException("Strategic preference must be in [0,10000]: " + type);
                }
                checked.put(type, value);
            }
            if (strategicGoalPreferences.size() != checked.size()) {
                throw new IllegalArgumentException("Strategic preference map contains unsupported keys");
            }
            strategicGoalPreferences = Collections.unmodifiableMap(checked);
        }

        /**
         * Builds immutable Stage-21 strategic preference input.
         *
         * @return common strategic doctrine profile
         */
        public FactionStrategicDoctrineProfile strategicDoctrine() {
            FactionStrategicDoctrineProfile result = FactionStrategicDoctrineProfile.neutral();
            for (StrategicGoalType type : StrategicGoalType.values()) {
                result = result.withPreference(type, strategicGoalPreferences.get(type));
            }
            return result;
        }
    }

    /**
     * One declarative binding to an already accepted mutable authority.
     *
     * @param id stable binding ID
     * @param kind policy family represented by the binding
     * @param authoritySeam existing authority that consumes future configuration
     * @param dependsOn other policy bindings required before this binding is meaningful
     * @param semanticIntent non-numeric authoring intent, never a direct outcome grant
     */
    public record PolicyBindingDefinition(
            String id,
            PolicyKind kind,
            AuthoritySeam authoritySeam,
            List<String> dependsOn,
            String semanticIntent) {
        /**
         * Validates and freezes one policy binding.
         *
         * @param id stable binding ID
         * @param kind policy family
         * @param authoritySeam existing authority seam
         * @param dependsOn prerequisite binding IDs
         * @param semanticIntent bounded semantic authoring intent
         */
        public PolicyBindingDefinition {
            id = requireContentId(id, "policy binding id");
            kind = Objects.requireNonNull(kind, "policy kind");
            authoritySeam = Objects.requireNonNull(authoritySeam, "authority seam");
            TreeSet<String> ordered = new TreeSet<>();
            for (String dependency : Objects.requireNonNull(dependsOn, "dependsOn")) {
                String checked = requireContentId(dependency, "policy dependency");
                if (!ordered.add(checked)) {
                    throw new IllegalArgumentException("Duplicate policy dependency: " + checked);
                }
            }
            if (ordered.contains(id)) {
                throw new IllegalArgumentException("Policy binding cannot depend on itself: " + id);
            }
            dependsOn = List.copyOf(ordered);
            semanticIntent = requireText(semanticIntent, "policy semanticIntent");
        }
    }

    /**
     * Role-to-production declaration reserved for the Stage-22.2 common manifest validator.
     *
     * @param roleId stable role taxonomy ID
     * @param fitId physically validated engineering fit ID
     * @param productionPathRef existing physical production-path reference
     * @param visualProfileRef ship visual profile bound to the legal fit
     */
    public record RoleProductionBindingDefinition(
            String roleId,
            String fitId,
            String productionPathRef,
            String visualProfileRef) {
        /**
         * Validates one complete role chain.
         *
         * @param roleId stable role taxonomy ID
         * @param fitId engineering fit ID
         * @param productionPathRef physical production-path reference
         * @param visualProfileRef visual profile reference
         */
        public RoleProductionBindingDefinition {
            roleId = requireContentId(roleId, "roleId");
            fitId = requireContentId(fitId, "fitId");
            productionPathRef = requireContentId(productionPathRef, "productionPathRef");
            visualProfileRef = requireContentId(visualProfileRef, "visualProfileRef");
        }
    }

    /**
     * Versioned authored-content manifest reference selected by a systemic profile.
     *
     * @param id stable manifest reference ID
     * @param packageKey canonical Stage-22 package key
     * @param scope core or explicitly deferred post-core scope
     * @param maturity current content maturity
     * @param roleBindings optional complete role/fit/production/visual chains
     */
    public record ManifestReferenceDefinition(
            String id,
            String packageKey,
            PackageScope scope,
            ContentMaturity maturity,
            List<RoleProductionBindingDefinition> roleBindings) {
        /**
         * Validates and freezes one manifest reference.
         *
         * @param id stable manifest reference ID
         * @param packageKey canonical package key
         * @param scope package scope
         * @param maturity content maturity
         * @param roleBindings role chains in deterministic role order
         */
        public ManifestReferenceDefinition {
            id = requireContentId(id, "manifest id");
            packageKey = requireContentId(packageKey, "manifest packageKey");
            scope = Objects.requireNonNull(scope, "manifest scope");
            maturity = Objects.requireNonNull(maturity, "manifest maturity");
            roleBindings = sorted(roleBindings, RoleProductionBindingDefinition::roleId);
            index(roleBindings, RoleProductionBindingDefinition::roleId, "manifest role");
        }
    }

    /**
     * Ship or character visual contract referenced by exactly one systemic profile.
     *
     * @param id stable visual profile ID
     * @param kind ship or character media family
     * @param packageKey canonical Stage-22 package key
     * @param status current governed asset maturity
     * @param authorityDocument repository-relative visual authority document
     */
    public record VisualProfileDefinition(
            String id,
            VisualKind kind,
            String packageKey,
            AssetStatus status,
            String authorityDocument) {
        /**
         * Validates one visual profile reference.
         *
         * @param id stable visual profile ID
         * @param kind visual media kind
         * @param packageKey canonical package key
         * @param status governed asset status
         * @param authorityDocument repository-relative authority document
         */
        public VisualProfileDefinition {
            id = requireContentId(id, "visual profile id");
            kind = Objects.requireNonNull(kind, "visual kind");
            packageKey = requireContentId(packageKey, "visual packageKey");
            status = Objects.requireNonNull(status, "visual asset status");
            authorityDocument = requireText(authorityDocument, "visual authorityDocument");
            if (!authorityDocument.startsWith("docs/") || authorityDocument.contains("..")) {
                throw new IllegalArgumentException("Visual authority document must stay under docs/");
            }
        }
    }

    /**
     * Localization namespace selected by one faction profile.
     *
     * @param id stable localization definition ID
     * @param namespace stable localization namespace
     * @param sourceLanguage source-copy language
     * @param languages required localized languages including the source language
     */
    public record LocalizationDefinition(
            String id,
            String namespace,
            String sourceLanguage,
            List<String> languages) {
        /**
         * Validates and freezes one localization definition.
         *
         * @param id stable localization definition ID
         * @param namespace stable localization namespace
         * @param sourceLanguage source-copy language
         * @param languages supported localized languages
         */
        public LocalizationDefinition {
            id = requireContentId(id, "localization id");
            namespace = requireContentId(namespace, "localization namespace");
            sourceLanguage = requireLanguage(sourceLanguage);
            TreeSet<String> ordered = new TreeSet<>();
            for (String language : Objects.requireNonNull(languages, "languages")) {
                if (!ordered.add(requireLanguage(language))) {
                    throw new IllegalArgumentException("Duplicate localization language: " + language);
                }
            }
            if (!ordered.contains(sourceLanguage)) {
                throw new IllegalArgumentException("Localization languages must include source language");
            }
            languages = List.copyOf(ordered);
        }
    }

    /**
     * Complete data-only Stage-22 systemic profile bound to one existing stable faction identity.
     *
     * @param profileId stable profile ID
     * @param stableFactionId existing authoritative runtime/save stable faction ID
     * @param packageKey canonical Stage-22 package key
     * @param profileVersion positive profile version
     * @param identityClass M22.0-governed identity class
     * @param doctrineProfileRef doctrine definition reference
     * @param industrialPolicyRef industrial policy binding
     * @param procurementPolicyRef procurement policy binding
     * @param logisticsPolicyRef logistics policy binding
     * @param fleetDoctrineRef fleet policy binding
     * @param diplomacyPolicyRef diplomacy policy binding
     * @param territoryPolicyRef territory policy binding
     * @param knowledgePolicyRef actor-bounded knowledge policy binding
     * @param recoveryPolicyRef recovery policy binding
     * @param authoredContentManifestRef authored-content manifest reference
     * @param shipVisualProfileRef ship visual profile reference
     * @param characterVisualProfileRef character visual profile reference
     * @param localizationRef localization definition reference
     * @param compatibilityAliases explicit legacy aliases governed by M22.0 migration data
     */
    public record SystemicProfileDefinition(
            String profileId,
            String stableFactionId,
            String packageKey,
            int profileVersion,
            IdentityClass identityClass,
            String doctrineProfileRef,
            String industrialPolicyRef,
            String procurementPolicyRef,
            String logisticsPolicyRef,
            String fleetDoctrineRef,
            String diplomacyPolicyRef,
            String territoryPolicyRef,
            String knowledgePolicyRef,
            String recoveryPolicyRef,
            String authoredContentManifestRef,
            String shipVisualProfileRef,
            String characterVisualProfileRef,
            String localizationRef,
            List<String> compatibilityAliases) {
        /**
         * Validates and freezes one systemic profile.
         *
         * @param profileId stable profile ID
         * @param stableFactionId authoritative stable faction ID
         * @param packageKey canonical package key
         * @param profileVersion positive profile version
         * @param identityClass governed identity class
         * @param doctrineProfileRef doctrine reference
         * @param industrialPolicyRef industrial policy reference
         * @param procurementPolicyRef procurement policy reference
         * @param logisticsPolicyRef logistics policy reference
         * @param fleetDoctrineRef fleet policy reference
         * @param diplomacyPolicyRef diplomacy policy reference
         * @param territoryPolicyRef territory policy reference
         * @param knowledgePolicyRef knowledge policy reference
         * @param recoveryPolicyRef recovery policy reference
         * @param authoredContentManifestRef manifest reference
         * @param shipVisualProfileRef ship visual reference
         * @param characterVisualProfileRef character visual reference
         * @param localizationRef localization reference
         * @param compatibilityAliases explicit compatibility aliases
         */
        public SystemicProfileDefinition {
            profileId = requireContentId(profileId, "profileId");
            stableFactionId = Stage22ContentGovernanceCatalog.requireFactionId(stableFactionId);
            packageKey = requireContentId(packageKey, "profile packageKey");
            if (profileVersion <= 0) {
                throw new IllegalArgumentException("profileVersion must be positive");
            }
            identityClass = Objects.requireNonNull(identityClass, "identityClass");
            doctrineProfileRef = requireContentId(doctrineProfileRef, "doctrineProfileRef");
            industrialPolicyRef = requireContentId(industrialPolicyRef, "industrialPolicyRef");
            procurementPolicyRef = requireContentId(procurementPolicyRef, "procurementPolicyRef");
            logisticsPolicyRef = requireContentId(logisticsPolicyRef, "logisticsPolicyRef");
            fleetDoctrineRef = requireContentId(fleetDoctrineRef, "fleetDoctrineRef");
            diplomacyPolicyRef = requireContentId(diplomacyPolicyRef, "diplomacyPolicyRef");
            territoryPolicyRef = requireContentId(territoryPolicyRef, "territoryPolicyRef");
            knowledgePolicyRef = requireContentId(knowledgePolicyRef, "knowledgePolicyRef");
            recoveryPolicyRef = requireContentId(recoveryPolicyRef, "recoveryPolicyRef");
            authoredContentManifestRef = requireContentId(
                    authoredContentManifestRef, "authoredContentManifestRef");
            shipVisualProfileRef = requireContentId(shipVisualProfileRef, "shipVisualProfileRef");
            characterVisualProfileRef = requireContentId(
                    characterVisualProfileRef, "characterVisualProfileRef");
            localizationRef = requireContentId(localizationRef, "localizationRef");
            TreeSet<String> orderedAliases = new TreeSet<>();
            for (String alias : Objects.requireNonNull(compatibilityAliases, "compatibilityAliases")) {
                String checked = Stage22ContentGovernanceCatalog.requireFactionId(alias);
                if (checked.equals(stableFactionId)) {
                    throw new IllegalArgumentException("Profile cannot alias its own stable faction ID");
                }
                if (!orderedAliases.add(checked)) {
                    throw new IllegalArgumentException("Duplicate compatibility alias: " + checked);
                }
            }
            compatibilityAliases = List.copyOf(orderedAliases);
        }
    }
}
