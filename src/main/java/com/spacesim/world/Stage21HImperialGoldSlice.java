package com.spacesim.world;

import com.spacesim.world.Stage21HNpcMissionState.MissionTemplate;
import com.spacesim.world.Stage21HNpcMissionState.NpcAvailability;
import com.spacesim.world.Stage21HNpcMissionState.NpcRole;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveAuthority;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;

import java.util.List;
import java.util.Objects;

/**
 * Minimum authored Stage-21H Imperial gold slice.
 *
 * <p>The definitions provide stable identities, localization keys, lawful issuer roles and the
 * ordinary authority each mission template must observe. They deliberately do not instantiate a
 * mission, create a target, fund a reward or force a story outcome. Live world evidence is still
 * required before {@link Stage21HNpcMissionService} can create and resolve a contract.</p>
 */
public final class Stage21HImperialGoldSlice {
    /** Stable faction identity already used by the generated-world Imperial Directorate. */
    public static final String IMPERIAL_FACTION_ID = "faction.imperial_directorate";

    /** Stable authored Imperial four-step chain identity. */
    public static final String IMPERIAL_CHAIN_ID = "story.imperial.supply-access-security.v1";

    private Stage21HImperialGoldSlice() {
        throw new AssertionError("No instances");
    }

    /**
     * One minimum contract definition.
     *
     * @param template canonical Stage-21H template
     * @param issuerRole lawful recurring-contact role
     * @param opportunityClaimCode Stage-21A/20 fact family required to justify an offer
     * @param objectiveAuthority ordinary state authority used for completion
     * @param objectiveKind expected predicate family
     * @param titleKey stable localization title key
     */
    public record ContractBlueprint(
            MissionTemplate template,
            NpcRole issuerRole,
            String opportunityClaimCode,
            ObjectiveAuthority objectiveAuthority,
            ObjectiveKind objectiveKind,
            String titleKey) {

        /** Validates one authored contract definition. */
        public ContractBlueprint {
            Objects.requireNonNull(template, "Mission template not set");
            Objects.requireNonNull(issuerRole, "Mission issuer role not set");
            opportunityClaimCode = requireText(opportunityClaimCode, "Opportunity claim code");
            Objects.requireNonNull(objectiveAuthority, "Objective authority not set");
            Objects.requireNonNull(objectiveKind, "Objective kind not set");
            titleKey = requireText(titleKey, "Mission title key");
        }
    }

    /**
     * One authored structural story-chain step.
     *
     * @param ordinal one-based step position
     * @param stepId stable authored step identity
     * @param issuerRole responsible recurring contact
     * @param missionTemplate mission family used when live evidence permits an offer
     * @param requiredLivingWorldSignal actor-known signal that gates creation
     * @param adaptationRule bounded rule when the live world already changed the subject
     */
    public record ChainStep(
            int ordinal,
            String stepId,
            NpcRole issuerRole,
            MissionTemplate missionTemplate,
            String requiredLivingWorldSignal,
            String adaptationRule) {

        /** Validates one non-scripted story-chain definition. */
        public ChainStep {
            if (ordinal <= 0) {
                throw new IllegalArgumentException("Story-chain ordinal must be positive");
            }
            stepId = requireText(stepId, "Story-chain step ID");
            Objects.requireNonNull(issuerRole, "Story-chain issuer role not set");
            Objects.requireNonNull(missionTemplate, "Story-chain mission template not set");
            requiredLivingWorldSignal = requireText(requiredLivingWorldSignal, "Story-chain signal");
            adaptationRule = requireText(adaptationRule, "Story-chain adaptation rule");
        }
    }

    /**
     * Creates the six recurring Imperial contacts at one real generated-world posting.
     *
     * <p>Names are stable localization keys and therefore do not reroll on restore. Portrait/art
     * availability is intentionally absent from simulation identity.</p>
     *
     * @param postingSystem real system in which the gold-slice contacts are currently posted
     * @return exactly one persistent contact for every canonical Stage-21H role
     */
    public static List<NpcState> recurringImperialContacts(StarSystemId postingSystem) {
        StarSystemId system = Objects.requireNonNull(postingSystem, "Imperial posting system not set");
        return List.of(
                npc("npc.imperial.elena-vorontsova", "npc.imperial.elena-vorontsova.name", NpcRole.OFFICIAL, system),
                npc("npc.imperial.mikhail-orlov", "npc.imperial.mikhail-orlov.name", NpcRole.MILITARY, system),
                npc("npc.imperial.vera-melnik", "npc.imperial.vera-melnik.name", NpcRole.TRADE_LOGISTICS, system),
                npc("npc.imperial.anton-karelin", "npc.imperial.anton-karelin.name", NpcRole.INDUSTRY_YARD, system),
                npc("npc.imperial.irina-sokolova", "npc.imperial.irina-sokolova.name", NpcRole.EXPLORATION_INTELLIGENCE, system),
                npc("npc.imperial.pavel-rybin", "npc.imperial.pavel-rybin.name", NpcRole.INDEPENDENT_FRONTIER, system));
    }

    /**
     * Returns the exact first eight Stage-21H contract blueprints required by the roadmap.
     *
     * <p>Each blueprint names an ordinary authority/predicate family. Some physical contracts share
     * the same predicate family because their distinct gameplay source and issuer role are different;
     * the blueprint never claims that a label alone completed the work.</p>
     *
     * @return canonical eight-template content floor
     */
    public static List<ContractBlueprint> minimumContractBlueprints() {
        return List.of(
                new ContractBlueprint(
                        MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                        NpcRole.TRADE_LOGISTICS,
                        "ECONOMIC.RESOURCE_DEFICIT",
                        ObjectiveAuthority.FREIGHT,
                        ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        "mission.stage21h.emergency-supply.title"),
                new ContractBlueprint(
                        MissionTemplate.ORDINARY_MARKET_PROCUREMENT,
                        NpcRole.TRADE_LOGISTICS,
                        "ECONOMIC.SUPPLY_DEPENDENCY",
                        ObjectiveAuthority.FREIGHT,
                        ObjectiveKind.FREIGHT_ORDER_DELIVERED_KG_AT_LEAST,
                        "mission.stage21h.market-procurement.title"),
                new ContractBlueprint(
                        MissionTemplate.CONVOY_ESCORT,
                        NpcRole.MILITARY,
                        "SECURITY.ROUTE_EXPOSURE",
                        ObjectiveAuthority.FLEET,
                        ObjectiveKind.FLEET_PRESENT_IN_SYSTEM,
                        "mission.stage21h.convoy-escort.title"),
                new ContractBlueprint(
                        MissionTemplate.STRANDED_FLEET_RESCUE_REFUEL,
                        NpcRole.INDEPENDENT_FRONTIER,
                        "SECURITY.ROUTE_EXPOSURE",
                        ObjectiveAuthority.FLEET,
                        ObjectiveKind.FLEET_PRESENT_IN_SYSTEM,
                        "mission.stage21h.stranded-rescue.title"),
                new ContractBlueprint(
                        MissionTemplate.SYSTEM_OBJECT_RECONNAISSANCE,
                        NpcRole.EXPLORATION_INTELLIGENCE,
                        "DISCOVERY.STATIC_OBJECT",
                        ObjectiveAuthority.DISCOVERY,
                        ObjectiveKind.DISCOVERY_AT_LEAST,
                        "mission.stage21h.reconnaissance.title"),
                new ContractBlueprint(
                        MissionTemplate.DERELICT_INVESTIGATION_RECOVERY,
                        NpcRole.EXPLORATION_INTELLIGENCE,
                        "DISCOVERY.SPECIAL_LOCATION",
                        ObjectiveAuthority.DISCOVERY,
                        ObjectiveKind.DISCOVERY_AT_LEAST,
                        "mission.stage21h.derelict-investigation.title"),
                new ContractBlueprint(
                        MissionTemplate.INTERCEPTION_DEFENSE,
                        NpcRole.MILITARY,
                        "SECURITY.BORDER_SECURITY",
                        ObjectiveAuthority.OPERATION,
                        ObjectiveKind.OPERATION_STATUS,
                        "mission.stage21h.interception-defense.title"),
                new ContractBlueprint(
                        MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY,
                        NpcRole.INDUSTRY_YARD,
                        "ECONOMIC.RESOURCE_DEFICIT",
                        ObjectiveAuthority.CONSTRUCTION,
                        ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST,
                        "mission.stage21h.construction-input.title"));
    }

    /**
     * Returns the compact Imperial chain required to prove authored narrative over changing world state.
     *
     * <p>Every step is gated by a live signal and carries an adaptation rule. No step creates its
     * shortage, treaty, convoy or crisis target.</p>
     *
     * @return exact four-step Imperial gold-slice chain
     */
    public static List<ChainStep> imperialChain() {
        return List.of(
                new ChainStep(
                        1,
                        IMPERIAL_CHAIN_ID + ".supply",
                        NpcRole.TRADE_LOGISTICS,
                        MissionTemplate.EMERGENCY_SUPPLY_DELIVERY,
                        "ECONOMIC.RESOURCE_DEFICIT",
                        "close-or-retarget-if-ordinary-shortage-no-longer-exists"),
                new ChainStep(
                        2,
                        IMPERIAL_CHAIN_ID + ".yard",
                        NpcRole.INDUSTRY_YARD,
                        MissionTemplate.CONSTRUCTION_REPAIR_INPUT_DELIVERY,
                        "ECONOMIC.SUPPLY_DEPENDENCY",
                        "close-or-retarget-if-project-completes-fails-or-changes-inputs"),
                new ChainStep(
                        3,
                        IMPERIAL_CHAIN_ID + ".access",
                        NpcRole.OFFICIAL,
                        MissionTemplate.IMPERIAL_ACCESS_NEGOTIATION,
                        "DIPLOMATIC.MARKET_ACCESS",
                        "complete-only-on-existing-stage17-access-state-or-close-if-issue-disappears"),
                new ChainStep(
                        4,
                        IMPERIAL_CHAIN_ID + ".security",
                        NpcRole.MILITARY,
                        MissionTemplate.CONVOY_ESCORT,
                        "SECURITY.ROUTE_EXPOSURE",
                        "escort-if-real-risk-persists-otherwise-close-with-world-resolved-outcome"));
    }

    private static NpcState npc(String id, String nameKey, NpcRole role, StarSystemId system) {
        return new NpcState(
                id,
                nameKey,
                role,
                IMPERIAL_FACTION_ID,
                system,
                NpcAvailability.AVAILABLE,
                List.of());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
