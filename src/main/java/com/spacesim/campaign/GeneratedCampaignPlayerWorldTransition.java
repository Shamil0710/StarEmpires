package com.spacesim.campaign;

import com.spacesim.persistence.*;
import com.spacesim.player.PlayableWorldState;

/** Composes an existing player/world transition while retaining every adjacent campaign owner. */
final class GeneratedCampaignPlayerWorldTransition {
    private GeneratedCampaignPlayerWorldTransition() { }

    static Stage228GeneratedCampaignPersistentState compose(
            Stage228GeneratedCampaignPersistentState source, PlayableWorldState updated) {
        var s = source.stage21Runtime().stage21HRuntime().stage21GRuntime().stage21FRuntime().stage21ERuntime()
                .stage21DRuntime().stage21CRuntime().stage21BRuntime().stage21ARuntime().stage20Runtime();
        var next20 = new Stage20GeneratedWorldRuntimePersistentState(s.schemaVersion(), s.bridgeVersion(),
                s.campaign(), updated.worldState(), s.activeSystemId(), s.strategicStepTicks(),
                s.remoteUpdateBudgetPerFrame(), s.freight(), s.localFleetPhysicalStates());
        return composeRuntime(source, next20, updated.playerState());
    }

    static Stage228GeneratedCampaignPersistentState composeRuntime(Stage228GeneratedCampaignPersistentState source,
            Stage20GeneratedWorldRuntimePersistentState next20, com.spacesim.player.PlayerState player) {
        var i = source.stage21Runtime(); var h = i.stage21HRuntime(); var g = h.stage21GRuntime();
        var f = g.stage21FRuntime(); var e = f.stage21ERuntime(); var d = e.stage21DRuntime();
        var c = d.stage21CRuntime(); var b = c.stage21BRuntime(); var a = b.stage21ARuntime();
        var nextA = new Stage21AGeneratedWorldRuntimePersistentState(a.schemaVersion(), a.runtimeVersion(), next20, a.livingActors());
        var nextB = new Stage21BGeneratedWorldRuntimePersistentState(b.schemaVersion(), b.runtimeVersion(), nextA, b.strategicIntents());
        var nextC = new Stage21CGeneratedWorldRuntimePersistentState(c.schemaVersion(), c.runtimeVersion(), nextB, c.diplomacyLifecycle(), c.warfareState());
        var nextD = new Stage21DGeneratedWorldRuntimePersistentState(d.schemaVersion(), d.runtimeVersion(), nextC, d.fleetCommandState());
        var nextE = new Stage21EGeneratedWorldRuntimePersistentState(e.schemaVersion(), e.runtimeVersion(), nextD, e.operationState());
        var nextF = new Stage21FGeneratedWorldRuntimePersistentState(f.schemaVersion(), f.runtimeVersion(), nextE, f.territorialTransitions());
        var nextG = new Stage21GGeneratedWorldRuntimePersistentState(g.schemaVersion(), g.runtimeVersion(), nextF, g.settlementRecovery());
        var nextH = new Stage21HGeneratedWorldRuntimePersistentState(h.schemaVersion(), h.runtimeVersion(), nextG, h.npcMissionState());
        var nextI = new Stage21IGeneratedWorldRuntimePersistentState(i.schemaVersion(), i.runtimeVersion(), nextH, i.migrationProvenance());
        return new Stage228GeneratedCampaignPersistentState(Stage228GeneratedCampaignPersistentState.CURRENT_VERSION,
                Stage228GeneratedCampaignPersistentState.CURRENT_RUNTIME_VERSION, nextI, source.smallCraft(), source.hangars(),
                source.flightDeck(), source.operations(), player, source.playerJournal(), source.moduleCustody(), source.repairQueue(), source.refitQueue(), source.moduleTransfers(), source.productTransfers(), source.yardConstruction());
    }
}
