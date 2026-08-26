package com.spacesim.persistence;

import com.spacesim.content.ContentCatalogLoader;
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
 * Physical/economic/discovery/diplomatic/operation truth remains owned by the embedded authorities.</p>
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
            if (identities.runtimeId(npc.factionContentId()).isEmpty()) {
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
            validateStableObjectiveIdentity(mission);
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

    private static void validateStableObjectiveIdentity(MissionContract mission) {
        ObjectiveKind kind = mission.objective().kind();
        if (kind == ObjectiveKind.FLEET_PRESENT_IN_SYSTEM || kind == ObjectiveKind.FLEET_ABSENT
                || kind == ObjectiveKind.CONSTRUCTION_DELIVERED_UNITS_AT_LEAST
                || kind == ObjectiveKind.CONSTRUCTION_COMPLETED
                || kind == ObjectiveKind.OPERATION_STATUS) {
            try {
                if (Long.parseLong(mission.objective().subjectId()) <= 0L) {
                    throw new IllegalArgumentException("identity must be positive");
                }
            } catch (NumberFormatException exception) {
                throw new IllegalArgumentException(
                        "Stage-21H mission has malformed numeric objective identity: " + mission.missionId(),
                        exception);
            }
        }
    }
}
