package com.spacesim.content;

import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;

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
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Immutable machine-readable M22.3 Empire authored package.
 *
 * <p>The package is content metadata only. Ship engineering, manufacturing, shipyard work, NPC
 * lifecycle and mission objective truth remain in their existing authorities. These records bind the
 * Empire package to those authorities and provide deterministic content/fingerprint governance.</p>
 */
public final class Stage22EmpirePackageCatalog {
    /** Canonical public package key. */
    public static final String PACKAGE_KEY = "core.empire";
    /** Existing authoritative runtime/save faction identity retained by M22.0. */
    public static final String STABLE_FACTION_ID = "faction.imperial_directorate";
    /** Required authored ship-family floor for M22.3. */
    public static final int REQUIRED_SHIP_FAMILIES = 9;
    /** Required signature station floor for M22.3. */
    public static final int REQUIRED_STATIONS = 3;
    /** Required recurring-NPC floor for M22.3. */
    public static final int REQUIRED_RECURRING_NPCS = 6;
    /** Required faction-facing mission-template floor for M22.3. */
    public static final int REQUIRED_MISSIONS = 10;
    /** Required short story-chain floor for M22.3. */
    public static final int REQUIRED_CHAINS = 2;

    private static final Pattern CONTENT_ID = Pattern.compile(
            "[a-z][a-z0-9_-]*(?:\\.[a-z0-9][a-z0-9_-]*)+");

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
    private final Map<String, ShipFamilyDefinition> shipByRoleId;
    private final Map<String, StationVariantDefinition> stationById;
    private final Map<String, RecurringNpcDefinition> npcById;
    private final Map<String, MissionTemplateDefinition> missionById;
    private final Map<String, StoryChainDefinition> chainById;
    private final String fingerprint;

    Stage22EmpirePackageCatalog(
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
        if (schemaVersion <= 0) {
            throw new IllegalArgumentException("Empire package schemaVersion must be positive");
        }
        this.schemaVersion = schemaVersion;
        this.catalogVersion = contentId(catalogVersion, "catalogVersion");
        this.packageKey = contentId(packageKey, "packageKey");
        this.stableFactionId = contentId(stableFactionId, "stableFactionId");
        if (!PACKAGE_KEY.equals(this.packageKey)) {
            throw new IllegalArgumentException("Empire package key must remain " + PACKAGE_KEY);
        }
        if (!STABLE_FACTION_ID.equals(this.stableFactionId)) {
            throw new IllegalArgumentException("Empire stable faction ID must remain " + STABLE_FACTION_ID);
        }
        this.shipFamilies = sorted(shipFamilies, ShipFamilyDefinition::roleId, "ship family role");
        this.stations = sorted(stations, StationVariantDefinition::id, "station");
        this.recurringNpcs = sorted(recurringNpcs, RecurringNpcDefinition::id, "recurring NPC");
        this.missions = sorted(missions, MissionTemplateDefinition::id, "mission template");
        this.storyChains = sorted(storyChains, StoryChainDefinition::id, "story chain");
        this.visualRules = sorted(visualRules, VisualRuleDefinition::id, "visual rule");
        this.shipByRoleId = index(this.shipFamilies, ShipFamilyDefinition::roleId, "ship role");
        this.stationById = index(this.stations, StationVariantDefinition::id, "station");
        this.npcById = index(this.recurringNpcs, RecurringNpcDefinition::id, "NPC");
        this.missionById = index(this.missions, MissionTemplateDefinition::id, "mission");
        this.chainById = index(this.storyChains, StoryChainDefinition::id, "chain");
        requireFloor(this.shipFamilies.size(), REQUIRED_SHIP_FAMILIES, "ship families");
        requireFloor(this.stations.size(), REQUIRED_STATIONS, "stations");
        requireFloor(this.recurringNpcs.size(), REQUIRED_RECURRING_NPCS, "recurring NPCs");
        requireFloor(this.missions.size(), REQUIRED_MISSIONS, "mission templates");
        requireFloor(this.storyChains.size(), REQUIRED_CHAINS, "story chains");
        if (this.visualRules.isEmpty()) {
            throw new IllegalArgumentException("Empire package requires visual rules");
        }
        validateMissionBindings();
        validateChainMissions();
        this.fingerprint = computeFingerprint();
    }

    /** @return package schema version */
    public int schemaVersion() { return schemaVersion; }
    /** @return stable package catalog version */
    public String catalogVersion() { return catalogVersion; }
    /** @return canonical public package key */
    public String packageKey() { return packageKey; }
    /** @return authoritative runtime/save faction ID */
    public String stableFactionId() { return stableFactionId; }
    /** @return immutable authored ship families */
    public List<ShipFamilyDefinition> shipFamilies() { return shipFamilies; }
    /** @return immutable signature station variants */
    public List<StationVariantDefinition> stations() { return stations; }
    /** @return immutable recurring NPC definitions */
    public List<RecurringNpcDefinition> recurringNpcs() { return recurringNpcs; }
    /** @return immutable faction-facing mission templates */
    public List<MissionTemplateDefinition> missions() { return missions; }
    /** @return immutable short authored story chains */
    public List<StoryChainDefinition> storyChains() { return storyChains; }
    /** @return immutable visual authoring rules */
    public List<VisualRuleDefinition> visualRules() { return visualRules; }
    /** @return deterministic package semantic fingerprint */
    public String fingerprint() { return fingerprint; }

    /**
     * Finds the ship family bound to one common role.
     *
     * @param roleId common Stage-22 role ID
     * @return matching ship family, or {@code null} when absent
     */
    public ShipFamilyDefinition findShipForRole(String roleId) { return shipByRoleId.get(roleId); }
    /**
     * Finds one signature station variant.
     *
     * @param id stable station-variant ID
     * @return matching station variant, or {@code null} when absent
     */
    public StationVariantDefinition findStation(String id) { return stationById.get(id); }
    /**
     * Finds one recurring NPC.
     *
     * @param id stable NPC ID
     * @return matching NPC, or {@code null} when absent
     */
    public RecurringNpcDefinition findNpc(String id) { return npcById.get(id); }
    /**
     * Finds one faction-facing mission template.
     *
     * @param id stable mission-template ID
     * @return matching mission template, or {@code null} when absent
     */
    public MissionTemplateDefinition findMission(String id) { return missionById.get(id); }
    /**
     * Finds one short authored story chain.
     *
     * @param id stable story-chain ID
     * @return matching story chain, or {@code null} when absent
     */
    public StoryChainDefinition findChain(String id) { return chainById.get(id); }

    private void validateChainMissions() {
        for (StoryChainDefinition chain : storyChains) {
            for (String missionId : chain.missionTemplateIds()) {
                if (!missionById.containsKey(missionId)) {
                    throw new IllegalArgumentException(
                            "Empire story chain references unknown mission: " + chain.id() + " -> " + missionId);
                }
            }
        }
    }

    private void validateMissionBindings() {
        for (MissionTemplateDefinition mission : missions) {
            RecurringNpcDefinition issuer = npcById.get(mission.issuerNpcId());
            if (issuer == null) {
                throw new IllegalArgumentException("Empire mission references unknown issuer: " + mission.id());
            }
            if (!Stage21HNpcMissionState.canIssue(issuer.role(), mission.runtimeTemplate())) {
                throw new IllegalArgumentException(
                        "Empire mission issuer cannot issue runtime template: " + mission.id());
            }
        }
    }

    private String computeFingerprint() {
        StringBuilder out = new StringBuilder(16_384);
        out.append("schema|").append(schemaVersion).append('|').append(catalogVersion).append('|')
                .append(packageKey).append('|').append(stableFactionId).append('\n');
        for (ShipFamilyDefinition value : shipFamilies) {
            out.append("ship|").append(value.roleId()).append('|').append(value.familyId()).append('|')
                    .append(value.primaryFitId()).append('|').append(value.refitFitId()).append('|')
                    .append(value.productionManifestId()).append('|').append(value.visualBindingId()).append('|')
                    .append(value.lineageId()).append('|').append(value.fleetUse()).append('|')
                    .append(value.counterplay()).append('\n');
        }
        for (StationVariantDefinition value : stations) {
            out.append("station|").append(value.id()).append('|').append(value.stage18ArchetypeId()).append('|')
                    .append(value.requiredFacilityIds()).append('|').append(value.visualBrief()).append('\n');
        }
        for (RecurringNpcDefinition value : recurringNpcs) {
            out.append("npc|").append(value.id()).append('|').append(value.nameKey()).append('|')
                    .append(value.role()).append('|').append(value.characterOverlayId()).append('|')
                    .append(value.publicVoice()).append('|').append(value.privateVoice()).append('\n');
        }
        for (MissionTemplateDefinition value : missions) {
            out.append("mission|").append(value.id()).append('|').append(value.issuerNpcId()).append('|')
                    .append(value.runtimeTemplate()).append('|').append(value.authority()).append('|')
                    .append(value.objectiveKind()).append('|')
                    .append(value.semanticIntent()).append('\n');
        }
        for (StoryChainDefinition value : storyChains) {
            out.append("chain|").append(value.id()).append('|').append(value.missionTemplateIds()).append('|')
                    .append(value.semanticIntent()).append('\n');
        }
        for (VisualRuleDefinition value : visualRules) {
            out.append("visual|").append(value.id()).append('|').append(value.medium()).append('|')
                    .append(value.authorityDocument()).append('|').append(value.requirement()).append('\n');
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(out.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM does not provide mandatory SHA-256", exception);
        }
    }

    private static void requireFloor(int actual, int minimum, String label) {
        if (actual < minimum) {
            throw new IllegalArgumentException(label + " below M22.3 floor: " + actual + " < " + minimum);
        }
    }

    static String contentId(String value, String label) {
        String checked = text(value, label);
        if (!CONTENT_ID.matcher(checked).matches()) {
            throw new IllegalArgumentException(label + " must be a lower-case dotted content ID: " + checked);
        }
        return checked;
    }

    static String text(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
        return checked;
    }

    static List<String> contentIds(List<String> values, String label, boolean requireNonEmpty) {
        List<String> result = new ArrayList<>();
        for (String value : Objects.requireNonNull(values, label)) {
            result.add(contentId(value, label + " entry"));
        }
        result.sort(String::compareTo);
        for (int i = 1; i < result.size(); i++) {
            if (result.get(i - 1).equals(result.get(i))) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + result.get(i));
            }
        }
        if (requireNonEmpty && result.isEmpty()) {
            throw new IllegalArgumentException(label + " must not be empty");
        }
        return List.copyOf(result);
    }

    private static <T> List<T> sorted(List<T> values, Function<T, String> key, String label) {
        List<T> result = new ArrayList<>(Objects.requireNonNull(values, label));
        result.replaceAll(value -> Objects.requireNonNull(value, label + " entry"));
        result.sort(Comparator.comparing(key));
        return List.copyOf(result);
    }

    private static <T> Map<String, T> index(List<T> values, Function<T, String> key, String label) {
        Map<String, T> result = new LinkedHashMap<>();
        for (T value : values) {
            String id = key.apply(value);
            if (result.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Duplicate " + label + ": " + id);
            }
        }
        return Collections.unmodifiableMap(result);
    }

    /**
     * One required Empire ship family bound to common role, physical fits, production and visual seams.
     */
    public record ShipFamilyDefinition(
            String familyId,
            String roleId,
            String primaryFitId,
            String refitFitId,
            String productionManifestId,
            String visualBindingId,
            String lineageId,
            String fleetUse,
            String counterplay) {
        /** Validates one authored Empire ship-family binding. */
        public ShipFamilyDefinition {
            familyId = contentId(familyId, "ship familyId");
            roleId = contentId(roleId, "ship roleId");
            primaryFitId = contentId(primaryFitId, "primaryFitId");
            refitFitId = contentId(refitFitId, "refitFitId");
            if (primaryFitId.equals(refitFitId)) {
                throw new IllegalArgumentException("Empire primary and refit fit must differ: " + familyId);
            }
            productionManifestId = contentId(productionManifestId, "productionManifestId");
            visualBindingId = contentId(visualBindingId, "visualBindingId");
            lineageId = contentId(lineageId, "lineageId");
            fleetUse = text(fleetUse, "fleetUse");
            counterplay = text(counterplay, "counterplay");
        }
    }

    /** One Empire station visual/content variant over an existing Stage-18 station authority. */
    public record StationVariantDefinition(
            String id,
            String stage18ArchetypeId,
            List<String> requiredFacilityIds,
            String visualBrief) {
        /** Validates one Empire signature station variant. */
        public StationVariantDefinition {
            id = contentId(id, "station id");
            stage18ArchetypeId = contentId(stage18ArchetypeId, "stage18ArchetypeId");
            requiredFacilityIds = contentIds(requiredFacilityIds, "requiredFacilityIds", true);
            visualBrief = text(visualBrief, "station visualBrief");
        }
    }

    /** One recurring authored Empire character layered on the shared Character Master. */
    public record RecurringNpcDefinition(
            String id,
            String nameKey,
            NpcRole role,
            String characterOverlayId,
            String publicVoice,
            String privateVoice) {
        /** Validates one recurring Empire NPC binding. */
        public RecurringNpcDefinition {
            id = contentId(id, "NPC id");
            nameKey = contentId(nameKey, "NPC nameKey");
            role = Objects.requireNonNull(role, "NPC role");
            characterOverlayId = contentId(characterOverlayId, "characterOverlayId");
            publicVoice = text(publicVoice, "publicVoice");
            privateVoice = text(privateVoice, "privateVoice");
        }
    }

    /** One faction-facing mission template whose objective truth remains Stage-21H authority-owned. */
    public record MissionTemplateDefinition(
            String id,
            String issuerNpcId,
            MissionTemplate runtimeTemplate,
            ObjectiveAuthority authority,
            ObjectiveKind objectiveKind,
            String semanticIntent) {
        /** Validates one Empire faction-facing mission template. */
        public MissionTemplateDefinition {
            id = contentId(id, "mission id");
            issuerNpcId = contentId(issuerNpcId, "issuerNpcId");
            runtimeTemplate = Objects.requireNonNull(runtimeTemplate, "mission runtimeTemplate");
            authority = Objects.requireNonNull(authority, "mission authority");
            objectiveKind = Objects.requireNonNull(objectiveKind, "mission objectiveKind");
            if (authority != Stage21HNpcMissionState.expectedAuthority(objectiveKind)) {
                throw new IllegalArgumentException(
                        "Empire mission objective authority mismatch: " + id + " -> "
                                + authority + "/" + objectiveKind);
            }
            Stage21HNpcMissionState.validateTemplateObjective(runtimeTemplate, objectiveKind);
            semanticIntent = text(semanticIntent, "mission semanticIntent");
        }
    }

    /** One deterministic short authored Empire chain made exclusively from package mission templates. */
    public record StoryChainDefinition(String id, List<String> missionTemplateIds, String semanticIntent) {
        /** Validates one deterministic Empire story-chain definition. */
        public StoryChainDefinition {
            id = contentId(id, "chain id");
            missionTemplateIds = contentIds(missionTemplateIds, "missionTemplateIds", true);
            if (missionTemplateIds.size() < 2) {
                throw new IllegalArgumentException("Empire story chain requires at least two mission steps: " + id);
            }
            semanticIntent = text(semanticIntent, "chain semanticIntent");
        }
    }

    /** Machine-readable visual/character authoring invariant retained beside package content. */
    public record VisualRuleDefinition(String id, String medium, String authorityDocument, String requirement) {
        /** Validates one package-level visual authoring rule. */
        public VisualRuleDefinition {
            id = contentId(id, "visual rule id");
            medium = text(medium, "visual medium");
            authorityDocument = text(authorityDocument, "visual authorityDocument");
            if (!authorityDocument.startsWith("docs/") || authorityDocument.contains("..")) {
                throw new IllegalArgumentException("Visual authority must stay under docs/: " + authorityDocument);
            }
            requirement = text(requirement, "visual requirement");
        }
    }
}
