package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.EconomicTransaction;
import com.spacesim.economy.EconomicTransaction.Type;
import com.spacesim.world.FactionIdentityResolver;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.Stage21HNpcMissionState.MissionContract;
import com.spacesim.world.Stage21HNpcMissionState.NpcState;
import com.spacesim.world.Stage21HNpcMissionState.ObjectiveKind;
import com.spacesim.world.Stage21HNpcMissionState.ReputationState;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldState;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Atomic Stage-21H generated-world checkpoint composition.
 *
 * <p>The complete accepted Stage-21G runtime is embedded unchanged. Stage 21H adds only NPC
 * identity/received knowledge, mission lifecycle/escrow, RPG reputation and authored-chain progress.
 * Physical/economic/discovery/diplomatic/operation truth remains owned by the embedded authorities.
 * Every active escrow additionally requires the exact ordinary treasury-transfer ledger provenance
 * that funded it, so a sidecar value alone cannot mint money on restore.</p>
 *
 * @param schemaVersion exact Stage-21H checkpoint schema
 * @param runtimeVersion exact Stage-21H runtime contract identifier
 * @param stage21GRuntime complete accepted Stage-21G checkpoint
 * @param npcMissionState Stage-21H RPG sidecar
 */
public record Stage21HGeneratedWorldRuntimePersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21GGeneratedWorldRuntimePersistentState stage21GRuntime,
        Stage21HNpcMissionState npcMissionState) {

    /** Current Stage-21H generated-world checkpoint schema. */
    public static final int CURRENT_VERSION = 11;
    /** Current Stage-21H generated-world runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "stage21h.generated-world-npc-missions.v11";

    /**
     * Validates Stage-21H references against the complete embedded generated world without repairing them.
     *
     * @param schemaVersion exact Stage-21H checkpoint schema
     * @param runtimeVersion exact runtime contract
     * @param stage21GRuntime complete embedded Stage-21G checkpoint
     * @param npcMissionState persistent Stage-21H RPG sidecar
     */
    public Stage21HGeneratedWorldRuntimePersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-21H checkpoint schema: " + schemaVersion);
        }
        runtimeVersion = Objects.requireNonNull(runtimeVersion, "runtimeVersion").strip();
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported Stage-21H runtime version: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21GRuntime, "stage21GRuntime");
        Objects.requireNonNull(npcMissionState, "npcMissionState");

        Stage20GeneratedWorldRuntimePersistentState stage20 = stage21GRuntime.stage21FRuntime()
                .stage21ERuntime().stage21DRuntime().stage21CRuntime().stage21BRuntime()
                .stage21ARuntime().stage20Runtime();
        WorldState world = stage20.worldState();
        long authoritativeWorldTick = world.systems().stream()
                .filter(system -> system.systemId().equals(stage20.activeSystemId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Stage-21H checkpoint active system is absent from saved world state"))
                .simulationState().clock().tick();
        if (npcMissionState.simulationTick() > authoritativeWorldTick) {
            throw new IllegalArgumentException("Stage-21H RPG state is ahead of authoritative world time");
        }

        Set<StarSystemId> systems = new HashSet<>();
        world.topology().systems().forEach(system -> systems.add(system.id()));
        FactionIdentityResolver identities = FactionIdentityResolver.createDefault(
                ContentCatalogLoader.loadDefault(), world.factionIdentities());

        for (NpcState npc : npcMissionState.npcs()) {
            if (!identities.containsStableId(npc.factionContentId())) {
                throw new IllegalArgumentException(
                        "Stage-21H NPC references unknown faction identity: " + npc.npcId());
            }
            if (!systems.contains(npc.locationSystemId())) {
                throw new IllegalArgumentException(
                        "Stage-21H NPC references unknown location system: " + npc.npcId());
            }
            npc.knowledge().forEach(fact -> {
                if (fact.receivedTick() > npcMissionState.simulationTick()) {
                    throw new IllegalArgumentException(
                            "Stage-21H NPC knowledge is newer than RPG checkpoint: " + fact.factId());
                }
            });
        }

        long escrowTotal = 0L;
        for (MissionContract mission : npcMissionState.missions()) {
            if (mission.objective().systemId() > 0L
                    && !systems.contains(new StarSystemId(mission.objective().systemId()))) {
                throw new IllegalArgumentException(
                        "Stage-21H mission references unknown objective system: " + mission.missionId());
            }
            validateStableObjectiveIdentity(mission, identities);
            for (var wakeup : mission.pendingWakeups()) {
                if (wakeup.observedTick() > npcMissionState.simulationTick()) {
                    throw new IllegalArgumentException(
                            "Stage-21H mission wakeup is newer than RPG checkpoint: " + mission.missionId());
                }
            }
            try {
                escrowTotal = Math.addExact(escrowTotal, mission.escrowMilliCredits());
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("Stage-21H aggregate escrow balance overflows", exception);
            }
            if (mission.active() && !hasExactEscrowFunding(world, mission)) {
                throw new IllegalArgumentException(
                        "Active Stage-21H mission lacks exact ordinary treasury funding provenance: "
                                + mission.missionId());
            }
        }

        for (ReputationState reputation : npcMissionState.reputations()) {
            for (var event : reputation.events()) {
                if (event.observedTick() > npcMissionState.simulationTick()) {
                    throw new IllegalArgumentException(
                            "Stage-21H reputation evidence is newer than RPG checkpoint: " + event.eventId());
                }
            }
        }
    }

    /**
     * Composes a current Stage-21H checkpoint over the accepted Stage-21G checkpoint.
     *
     * @param stage21G complete accepted Stage-21G generated-world checkpoint
     * @param npcMissions persistent Stage-21H RPG sidecar
     * @return validated current-version Stage-21H checkpoint
     */
    public static Stage21HGeneratedWorldRuntimePersistentState compose(
            Stage21GGeneratedWorldRuntimePersistentState stage21G,
            Stage21HNpcMissionState npcMissions) {
        return new Stage21HGeneratedWorldRuntimePersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21G, npcMissions);
    }

    private static void validateStableObjectiveIdentity(
            MissionContract mission,
            FactionIdentityResolver identities) {
        ObjectiveKind kind = mission.objective().kind();
        switch (kind) {
            case FLEET_PRESENT_IN_SYSTEM, FLEET_ABSENT, FLEET_REACTION_MASS_KG_AT_LEAST,
                    CONSTRUCTION_DELIVERED_UNITS_AT_LEAST, CONSTRUCTION_COMPLETED, OPERATION_STATUS ->
                    requirePositiveNumericIdentity(mission.objective().subjectId(), mission.missionId());
            case ESCORT_FLEETS_PRESENT_IN_SYSTEM -> {
                requirePositiveNumericIdentity(mission.objective().subjectId(), mission.missionId());
                requirePositiveNumericIdentity(mission.objective().requiredState(), mission.missionId());
            }
            case MARKET_ACCESS_ALLOWED -> {
                if (!identities.containsStableId(mission.objective().subjectId())
                        || !identities.containsStableId(mission.objective().requiredState())) {
                    throw new IllegalArgumentException(
                            "Stage-21H access mission references unknown faction identity: " + mission.missionId());
                }
            }
            case FACTION_TREASURY_AT_LEAST -> {
                if (!identities.containsStableId(mission.objective().subjectId())) {
                    throw new IllegalArgumentException(
                            "Stage-21H economic mission references unknown faction identity: " + mission.missionId());
                }
            }
            case DERELICT_DISCOVERED_AND_SALVAGED_KG_AT_LEAST -> {
                String[] subjects = mission.objective().subjectId().split("\\|", -1);
                if (subjects.length != 2 || subjects[0].isBlank() || subjects[1].isBlank()) {
                    throw new IllegalArgumentException(
                            "Stage-21H derelict mission has malformed static/salvage identity: "
                                    + mission.missionId());
                }
            }
            case FREIGHT_ORDER_DELIVERED_KG_AT_LEAST, DISCOVERY_AT_LEAST -> {
                // Stable string identities are validated structurally by the Stage-21H sidecar.
            }
        }
    }

    private static void requirePositiveNumericIdentity(String value, String missionId) {
        try {
            if (Long.parseLong(value) <= 0L) {
                throw new IllegalArgumentException("identity must be positive");
            }
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Stage-21H mission has malformed numeric objective identity: " + missionId,
                    exception);
        }
    }

    private static boolean hasExactEscrowFunding(WorldState world, MissionContract mission) {
        String source = "faction:" + mission.issuerFactionId() + ":treasury";
        String destination = "mission-escrow:" + mission.missionId();
        int matching = 0;
        for (var system : world.systems()) {
            for (EconomicTransaction entry : system.simulationState().ledger().entries()) {
                if (entry.type() == Type.MONEY_TRANSFER
                        && source.equals(entry.source())
                        && destination.equals(entry.destination())
                        && entry.moneyMilliCredits() == mission.rewardMilliCredits()
                        && "stage21h-mission-escrow-fund".equals(entry.reason())) {
                    matching++;
                }
            }
        }
        return matching == 1;
    }
}
