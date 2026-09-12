package com.spacesim.campaign;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimePersistentState;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionLivingActorRuntime;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.Stage21HNpcMissionState;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;

import java.util.List;
import java.util.Objects;

/**
 * Atomic campaign handoff between the ordinary generated-world session and the accepted Stage-21
 * authority chain.
 *
 * <p>This class owns no gameplay state. Capture embeds snapshots from the existing Stage-21A-H
 * owners into their already accepted persistence envelopes, while restore exposes those same
 * immutable snapshots together with an independently restored ordinary campaign session and actor
 * scheduler. Physical world, economy, fleets, diplomacy, operations, recovery and RPG state keep
 * their original authorities.</p>
 */
public final class GeneratedCampaignAuthorityCheckpoint {
    private GeneratedCampaignAuthorityCheckpoint() {
        throw new AssertionError("No instances");
    }

    /**
     * Captures the complete accepted Stage-21 authority chain over one ordinary campaign session.
     *
     * @param session ordinary Stage-20/20.5 campaign authority
     * @param actors Stage-21A autonomous-faction lifecycle owner
     * @param strategicIntents Stage-21B strategic-intent snapshots
     * @param diplomacy Stage-21C diplomacy lifecycle snapshot
     * @param warfare Stage-19 conflict state referenced by diplomacy
     * @param commands Stage-21D fleet command/order state
     * @param operations Stage-21E operation state
     * @param transitions Stage-21F territorial-transition state
     * @param recovery Stage-21G settlement/recovery state
     * @param npcMissions Stage-21H NPC/mission/reputation/story state
     * @return validated native Stage-21I checkpoint
     */
    public static Stage21IGeneratedWorldRuntimePersistentState capture(
            GeneratedCampaignSession session,
            FactionLivingActorRuntime actors,
            List<FactionStrategicIntentState> strategicIntents,
            DiplomaticLifecycleState diplomacy,
            Stage19ConflictState warfare,
            FleetCommandState commands,
            StrategicOperationState operations,
            TerritorialTransitionState transitions,
            SettlementRecoveryState recovery,
            Stage21HNpcMissionState npcMissions) {
        GeneratedCampaignSession checkedSession = Objects.requireNonNull(session, "session");
        FactionLivingActorRuntime checkedActors = Objects.requireNonNull(actors, "actors");

        Stage21AGeneratedWorldRuntimePersistentState stage21A =
                new Stage21AGeneratedWorldRuntimePersistentState(
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21AGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        checkedSession.captureState(),
                        checkedActors.capture());
        Stage21BGeneratedWorldRuntimePersistentState stage21B =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21A,
                        Objects.requireNonNull(strategicIntents, "strategicIntents"));
        Stage21CGeneratedWorldRuntimePersistentState stage21C =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21B,
                        Objects.requireNonNull(diplomacy, "diplomacy"),
                        Objects.requireNonNull(warfare, "warfare"));
        Stage21DGeneratedWorldRuntimePersistentState stage21D =
                Stage21DGeneratedWorldRuntimePersistentState.compose(
                        stage21C, Objects.requireNonNull(commands, "commands"));
        Stage21EGeneratedWorldRuntimePersistentState stage21E =
                Stage21EGeneratedWorldRuntimePersistentState.compose(
                        stage21D, Objects.requireNonNull(operations, "operations"));
        Stage21FGeneratedWorldRuntimePersistentState stage21F =
                Stage21FGeneratedWorldRuntimePersistentState.compose(
                        stage21E, Objects.requireNonNull(transitions, "transitions"));
        Stage21GGeneratedWorldRuntimePersistentState stage21G =
                Stage21GGeneratedWorldRuntimePersistentState.compose(
                        stage21F, Objects.requireNonNull(recovery, "recovery"));
        Stage21HGeneratedWorldRuntimePersistentState stage21H =
                Stage21HGeneratedWorldRuntimePersistentState.compose(
                        stage21G, Objects.requireNonNull(npcMissions, "npcMissions"));
        return Stage21IGeneratedWorldRuntimePersistentState.compose(stage21H);
    }

    /**
     * Restores the ordinary campaign and Stage-21A scheduler while retaining exact immutable
     * snapshots for every later accepted authority owner.
     *
     * @param checkpoint native or migrated final Stage-21 checkpoint
     * @return independent restored campaign handoff
     */
    public static RestoredAuthorities restore(Stage21IGeneratedWorldRuntimePersistentState checkpoint) {
        Stage21IGeneratedWorldRuntimePersistentState saved = Objects.requireNonNull(checkpoint, "checkpoint");
        Stage21HGeneratedWorldRuntimePersistentState stage21H = saved.stage21HRuntime();
        Stage21GGeneratedWorldRuntimePersistentState stage21G = stage21H.stage21GRuntime();
        Stage21FGeneratedWorldRuntimePersistentState stage21F = stage21G.stage21FRuntime();
        Stage21EGeneratedWorldRuntimePersistentState stage21E = stage21F.stage21ERuntime();
        Stage21DGeneratedWorldRuntimePersistentState stage21D = stage21E.stage21DRuntime();
        Stage21CGeneratedWorldRuntimePersistentState stage21C = stage21D.stage21CRuntime();
        Stage21BGeneratedWorldRuntimePersistentState stage21B = stage21C.stage21BRuntime();
        Stage21AGeneratedWorldRuntimePersistentState stage21A = stage21B.stage21ARuntime();

        return new RestoredAuthorities(
                GeneratedCampaignSession.restore(stage21A.stage20Runtime()),
                FactionLivingActorRuntime.restore(stage21A.livingActors()),
                stage21B.strategicIntents(),
                stage21C.diplomacyLifecycle(),
                stage21C.warfareState(),
                stage21D.fleetCommandState(),
                stage21E.operationState(),
                stage21F.territorialTransitions(),
                stage21G.settlementRecovery(),
                stage21H.npcMissionState());
    }

    /**
     * Restored references to the original accepted authority snapshots.
     *
     * <p>The record is an orchestration handoff only. Mutable continuation must be performed by the
     * original production owners/services for each snapshot type.</p>
     *
     * @param session independently restored ordinary generated-world campaign
     * @param actors independently restored Stage-21A actor scheduler
     * @param strategicIntents exact Stage-21B intent snapshots
     * @param diplomacy exact Stage-21C diplomacy snapshot
     * @param warfare exact Stage-19 conflict snapshot
     * @param commands exact Stage-21D command snapshot
     * @param operations exact Stage-21E operation snapshot
     * @param transitions exact Stage-21F transition snapshot
     * @param recovery exact Stage-21G recovery snapshot
     * @param npcMissions exact Stage-21H RPG snapshot
     */
    public record RestoredAuthorities(
            GeneratedCampaignSession session,
            FactionLivingActorRuntime actors,
            List<FactionStrategicIntentState> strategicIntents,
            DiplomaticLifecycleState diplomacy,
            Stage19ConflictState warfare,
            FleetCommandState commands,
            StrategicOperationState operations,
            TerritorialTransitionState transitions,
            SettlementRecoveryState recovery,
            Stage21HNpcMissionState npcMissions) {
        /**
         * Validates that every restored authority reference is present and freezes the intent list.
         *
         * @param session independently restored ordinary generated-world campaign
         * @param actors independently restored Stage-21A actor scheduler
         * @param strategicIntents exact Stage-21B intent snapshots
         * @param diplomacy exact Stage-21C diplomacy snapshot
         * @param warfare exact Stage-19 conflict snapshot
         * @param commands exact Stage-21D command snapshot
         * @param operations exact Stage-21E operation snapshot
         * @param transitions exact Stage-21F transition snapshot
         * @param recovery exact Stage-21G recovery snapshot
         * @param npcMissions exact Stage-21H RPG snapshot
         */
        public RestoredAuthorities {
            Objects.requireNonNull(session, "session");
            Objects.requireNonNull(actors, "actors");
            strategicIntents = List.copyOf(Objects.requireNonNull(strategicIntents, "strategicIntents"));
            Objects.requireNonNull(diplomacy, "diplomacy");
            Objects.requireNonNull(warfare, "warfare");
            Objects.requireNonNull(commands, "commands");
            Objects.requireNonNull(operations, "operations");
            Objects.requireNonNull(transitions, "transitions");
            Objects.requireNonNull(recovery, "recovery");
            Objects.requireNonNull(npcMissions, "npcMissions");
        }
    }
}
