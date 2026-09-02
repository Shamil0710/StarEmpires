package com.spacesim.content;

import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/** Immutable M22.4 Industrial Union authored production-package catalog. */
public final class Stage22IndustrialUnionPackageCatalog {
    /** Exact required major-family floor inherited from the M22.2 shared role contract. */
    public static final int REQUIRED_SHIP_FAMILIES = 9;
    /** Stable package key already accepted by M22.0/M22.1. */
    public static final String PACKAGE_KEY = "core.industrial_union";
    /** Stable runtime/save identity already accepted by M22.0/M22.1. */
    public static final String STABLE_FACTION_ID = "faction.industrial_combine";
    private static final Pattern CONTENT_ID = Pattern.compile("[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

    private final int schemaVersion;
    private final String catalogVersion;
    private final String packageKey;
    private final String stableFactionId;
    private final List<ShipFamilyDefinition> shipFamilies;
    private final List<StationVariantDefinition> stations;
    private final List<RecurringNpcDefinition> recurringNpcs;
    private final List<MissionTemplateDefinition> missions;
    private final List<StoryChainDefinition> storyChains;
    private final List<VisualRuleDefinition> visualRules;
    private final Map<String, RecurringNpcDefinition> npcById;
    private final Map<String, MissionTemplateDefinition> missionById;
    private final String fingerprint;

    Stage22IndustrialUnionPackageCatalog(
            int schemaVersion,
            String catalogVersion,
            String packageKey,
            String stableFactionId,
            List<ShipFamilyDefinition> shipFamilies,
            List<StationVariantDefinition> stations,
            List<RecurringNpcDefinition> recurringNpcs,
            List<MissionTemplateDefinition> missions,
            List<StoryChainDefinition> storyChains,
            List<VisualRuleDefinition> visualRules) {
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("Industrial Union package schema must be 1");
        }
        this.schemaVersion = schemaVersion;
        this.catalogVersion = requireId(catalogVersion, "catalogVersion");
        this.packageKey = requireId(packageKey, "packageKey");
        this.stableFactionId = requireId(stableFactionId, "stableFactionId");
        if (!PACKAGE_KEY.equals(this.packageKey) || !STABLE_FACTION_ID.equals(this.stableFactionId)) {
            throw new IllegalArgumentException("Industrial Union package cannot change accepted package/runtime identity");
        }
        this.shipFamilies = canonical(shipFamilies, ShipFamilyDefinition::familyId, "ship family");
        this.stations = canonical(stations, StationVariantDefinition::id, "station");
        this.recurringNpcs = canonical(recurringNpcs, RecurringNpcDefinition::id, "recurring NPC");
        this.missions = canonical(missions, MissionTemplateDefinition::id, "mission");
        this.storyChains = canonical(storyChains, StoryChainDefinition::id, "story chain");
        this.visualRules = canonical(visualRules, VisualRuleDefinition::id, "visual rule");
        if (this.shipFamilies.size() != REQUIRED_SHIP_FAMILIES) {
            throw new IllegalArgumentException("Industrial Union package requires exactly nine shared-role ship families");
        }
        if (this.stations.size() < 3 || this.recurringNpcs.size() < 6 || this.missions.size() < 10 || this.storyChains.size() < 2) {
            throw new IllegalArgumentException("Industrial Union package misses the M22.4 authored content floor");
        }
        this.npcById = index(this.recurringNpcs, RecurringNpcDefinition::id, "NPC");
        this.missionById = index(this.missions, MissionTemplateDefinition::id, "mission");
        for (MissionTemplateDefinition mission : this.missions) {
            if (!npcById.containsKey(mission.issuerNpcId())) {
                throw new IllegalArgumentException("Mission references unknown Industrial Union issuer: " + mission.id());
            }
        }
        for (StoryChainDefinition chain : this.storyChains) {
            for (String missionId : chain.missionTemplateIds()) {
                if (!missionById.containsKey(missionId)) {
                    throw new IllegalArgumentException("Story chain references unknown Industrial Union mission: " + missionId);
                }
            }
        }
        this.fingerprint = computeFingerprint();
    }

    /** @return supported package schema version */
    public int schemaVersion() { return schemaVersion; }
    /** @return stable package catalog version */
    public String catalogVersion() { return catalogVersion; }
    /** @return canonical package key */
    public String packageKey() { return packageKey; }
    /** @return accepted runtime/save faction identity */
    public String stableFactionId() { return stableFactionId; }
    /** @return exact nine shared-role ship families */
    public List<ShipFamilyDefinition> shipFamilies() { return shipFamilies; }
    /** @return authored Union station variants */
    public List<StationVariantDefinition> stations() { return stations; }
    /** @return authored recurring NPC definitions */
    public List<RecurringNpcDefinition> recurringNpcs() { return recurringNpcs; }
    /** @return authored faction-facing mission templates */
    public List<MissionTemplateDefinition> missions() { return missions; }
    /** @return authored short mission chains */
    public List<StoryChainDefinition> storyChains() { return storyChains; }
    /** @return visual authoring requirements */
    public List<VisualRuleDefinition> visualRules() { return visualRules; }
    /** @return deterministic package semantic fingerprint */
    public String fingerprint() { return fingerprint; }

    /**
     * Finds one recurring Union NPC.
     *
     * @param id stable NPC content ID
     * @return matching NPC or {@code null}
     */
    public RecurringNpcDefinition findNpc(String id) { return npcById.get(id); }

    /**
     * Finds one Union mission template.
     *
     * @param id stable mission content ID
     * @return matching mission or {@code null}
     */
    public MissionTemplateDefinition findMission(String id) { return missionById.get(id); }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(16_384)
                .append(schemaVersion).append('|').append(catalogVersion).append('|')
                .append(packageKey).append('|').append(stableFactionId).append('\n');
        shipFamilies.forEach(value -> out.append("ship|").append(value).append('\n'));
        stations.forEach(value -> out.append("station|").append(value).append('\n'));
        recurringNpcs.forEach(value -> out.append("npc|").append(value).append('\n'));
        missions.forEach(value -> out.append("mission|").append(value).append('\n'));
        storyChains.forEach(value -> out.append("chain|").append(value).append('\n'));
        visualRules.forEach(value -> out.append("visual|").append(value).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    /** One required Union ship family bound to common role, exact fits, production and visual seams. */
    public record ShipFamilyDefinition(
            String familyId, String roleId, String primaryFitId, String refitFitId,
            String productionManifestId, String visualBindingId, String lineageId,
            String fleetUse, String counterplay) {
        /**
         * Validates one authored Union ship-family binding.
         *
         * @param familyId stable Union family content ID
         * @param roleId common M22.2 role ID
         * @param primaryFitId exact primary engineering fit ID
         * @param refitFitId exact alternate/refit engineering fit ID
         * @param productionManifestId primary-fit production manifest ID
         * @param visualBindingId exact-fit visual binding ID
         * @param lineageId manufacturer/design/procurement lineage ID
         * @param fleetUse authored fleet-composition use
         * @param counterplay intended physical/economic counterplay
         */
        public ShipFamilyDefinition {
            familyId = unionId(familyId, "ship_family.industrial_union.", "familyId");
            roleId = requireId(roleId, "roleId");
            primaryFitId = unionId(primaryFitId, "fit.industrial_union.", "primaryFitId");
            refitFitId = unionId(refitFitId, "fit.industrial_union.", "refitFitId");
            if (primaryFitId.equals(refitFitId)) throw new IllegalArgumentException("Primary and refit fit must differ");
            productionManifestId = unionId(productionManifestId, "production_manifest.industrial_union.", "productionManifestId");
            visualBindingId = unionId(visualBindingId, "visual_binding.industrial_union.", "visualBindingId");
            lineageId = unionId(lineageId, "lineage.industrial_union.", "lineageId");
            fleetUse = requireText(fleetUse, "fleetUse");
            counterplay = requireText(counterplay, "counterplay");
        }
    }

    /** One Union-specific station presentation/content variant over a Stage-18 station archetype. */
    public record StationVariantDefinition(String id, String stage18ArchetypeId, List<String> requiredFacilityIds, String visualBrief) {
        /**
         * Validates one station variant.
         *
         * @param id stable Union station-variant ID
         * @param stage18ArchetypeId existing Stage-18 station archetype ID
         * @param requiredFacilityIds physical facility definitions required by the variant
         * @param visualBrief faction visual-authoring brief
         */
        public StationVariantDefinition {
            id = unionId(id, "station_variant.industrial_union.", "station id");
            stage18ArchetypeId = requireId(stage18ArchetypeId, "stage18ArchetypeId");
            requiredFacilityIds = nonEmptyIds(requiredFacilityIds, "requiredFacilityIds");
            visualBrief = requireText(visualBrief, "visualBrief");
        }
    }

    /** One recurring Stage-21H NPC identity used by the Union authored package. */
    public record RecurringNpcDefinition(String id, String nameKey, NpcRole role, String characterOverlayId, String publicVoice, String privateVoice) {
        /**
         * Validates one recurring NPC binding.
         *
         * @param id stable Union NPC ID
         * @param nameKey localization key
         * @param role accepted Stage-21H NPC role
         * @param characterOverlayId faction character-overlay ID
         * @param publicVoice public-facing voice brief
         * @param privateVoice private motivation/voice brief
         */
        public RecurringNpcDefinition {
            id = unionId(id, "npc.industrial_union.", "npc id");
            nameKey = requireId(nameKey, "nameKey");
            role = Objects.requireNonNull(role, "role");
            characterOverlayId = unionId(characterOverlayId, "character_overlay.industrial_union.", "characterOverlayId");
            publicVoice = requireText(publicVoice, "publicVoice");
            privateVoice = requireText(privateVoice, "privateVoice");
        }
    }

    /** One Union faction-facing mission bound to the accepted Stage-21H objective authorities. */
    public record MissionTemplateDefinition(
            String id, String issuerNpcId, MissionTemplate runtimeTemplate,
            ObjectiveAuthority authority, ObjectiveKind objectiveKind, String semanticIntent) {
        /**
         * Validates one mission binding.
         *
         * @param id stable Union mission ID
         * @param issuerNpcId recurring issuer NPC ID
         * @param runtimeTemplate accepted Stage-21H mission template
         * @param authority existing objective truth authority
         * @param objectiveKind accepted objective kind
         * @param semanticIntent faction-specific authored intent
         */
        public MissionTemplateDefinition {
            id = unionId(id, "mission.industrial_union.", "mission id");
            issuerNpcId = unionId(issuerNpcId, "npc.industrial_union.", "issuerNpcId");
            runtimeTemplate = Objects.requireNonNull(runtimeTemplate, "runtimeTemplate");
            authority = Objects.requireNonNull(authority, "authority");
            objectiveKind = Objects.requireNonNull(objectiveKind, "objectiveKind");
            semanticIntent = requireText(semanticIntent, "semanticIntent");
        }
    }

    /** One short ordered Union story chain composed only from ordinary package missions. */
    public record StoryChainDefinition(String id, List<String> missionTemplateIds, String semanticIntent) {
        /**
         * Validates one story chain.
         *
         * @param id stable Union story-chain ID
         * @param missionTemplateIds ordered mission-template IDs
         * @param semanticIntent authored causal chain intent
         */
        public StoryChainDefinition {
            id = unionId(id, "story_chain.industrial_union.", "story chain id");
            missionTemplateIds = nonEmptyIds(missionTemplateIds, "missionTemplateIds");
            semanticIntent = requireText(semanticIntent, "semanticIntent");
        }
    }

    /** One machine-readable Union visual-authoring rule. */
    public record VisualRuleDefinition(String id, String medium, String authorityDocument, String requirement) {
        /**
         * Validates one visual rule.
         *
         * @param id stable visual-rule ID
         * @param medium affected presentation medium
         * @param authorityDocument canonical repository visual authority
         * @param requirement authored visual requirement
         */
        public VisualRuleDefinition {
            id = unionId(id, "visual_rule.industrial_union.", "visual rule id");
            medium = requireText(medium, "medium");
            authorityDocument = requireText(authorityDocument, "authorityDocument");
            if (!authorityDocument.startsWith("docs/")) throw new IllegalArgumentException("Visual authority must be a docs path");
            requirement = requireText(requirement, "requirement");
        }
    }

    private static <T> List<T> canonical(List<T> source, java.util.function.Function<T,String> id, String kind) {
        List<T> copy = new ArrayList<>(Objects.requireNonNull(source, kind + " list"));
        copy.replaceAll(value -> Objects.requireNonNull(value, kind));
        copy.sort(Comparator.comparing(id));
        if (copy.stream().map(id).distinct().count() != copy.size()) throw new IllegalArgumentException("Duplicate " + kind);
        return List.copyOf(copy);
    }

    private static <T> Map<String,T> index(List<T> source, java.util.function.Function<T,String> id, String kind) {
        Map<String,T> result = new LinkedHashMap<>();
        for (T value : source) if (result.putIfAbsent(id.apply(value), value) != null) throw new IllegalArgumentException("Duplicate " + kind);
        return Map.copyOf(result);
    }

    private static List<String> nonEmptyIds(List<String> source, String label) {
        List<String> copy = new ArrayList<>(Objects.requireNonNull(source, label));
        copy.replaceAll(value -> requireId(value, label));
        if (copy.isEmpty()) throw new IllegalArgumentException(label + " must not be empty");
        return List.copyOf(copy);
    }
    private static String unionId(String value, String prefix, String label) {
        String checked = requireId(value, label);
        if (!checked.startsWith(prefix)) throw new IllegalArgumentException(label + " escapes Industrial Union namespace: " + checked);
        return checked;
    }
    private static String requireId(String value, String label) {
        String checked = requireText(value, label);
        if (!CONTENT_ID.matcher(checked).matches()) throw new IllegalArgumentException(label + " must be a dotted content ID: " + checked);
        return checked;
    }
    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) throw new IllegalArgumentException(label + " must not be blank");
        return checked;
    }
}
