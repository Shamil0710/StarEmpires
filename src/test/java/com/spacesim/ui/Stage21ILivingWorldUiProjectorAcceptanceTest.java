package com.spacesim.ui;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21HGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21IGeneratedWorldRuntimeMigration;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class Stage21ILivingWorldUiProjectorAcceptanceTest {

    @Test
    void projectionIsDeterministicReadOnlyAndUsesAuthoritativeActiveTreaties() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 31L).runtime();
        var initial = stage20.captureState();
        String viewer = initial.worldState().factions().get(0).factionContentId();
        String counterparty = initial.worldState().factions().get(1).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();

        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                stage20.world(), warfare, DiplomaticLifecycleState.empty(now));
        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.stage21i.ui.trade",
                viewer,
                counterparty,
                DiplomaticLifecycleState.ProposalKind.TRADE,
                "route.stage21i.ui.trade",
                List.of(),
                List.of(),
                now + 120L));
        proposal = diplomacy.materializeTreatyOffer(proposal.proposalId());
        diplomacy.accept(proposal.proposalId());

        Stage21BGeneratedWorldRuntimePersistentState stage21B = stage21b(stage20, viewer, counterparty);
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                diplomacy.snapshot(),
                warfare.snapshot());
        var checkpoint = Stage21IGeneratedWorldRuntimeMigration.migrate(stage21C).stage21HRuntime();
        byte[] before = Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint);

        Stage21ILivingWorldUiProjector projector = new Stage21ILivingWorldUiProjector();
        Stage21ILivingWorldUiSnapshot first = projector.project(checkpoint, viewer);
        Stage21ILivingWorldUiSnapshot second = projector.project(checkpoint, viewer);

        assertEquals(first, second);
        assertArrayEquals(before, Stage21HGeneratedWorldRuntimePersistenceCodec.encode(checkpoint));
        var counterpartyRow = first.factions().stream()
                .filter(row -> row.factionId().equals(counterparty))
                .findFirst()
                .orElseThrow();
        assertFalse(counterpartyRow.treaties().isEmpty());
        assertEquals(proposal.linkedTreatyId(), counterpartyRow.treaties().get(0).split(":", 2)[0]);
        assertEquals(List.of(), first.factions().stream()
                .filter(row -> row.factionId().equals(viewer))
                .findFirst()
                .orElseThrow()
                .treaties());
    }

    private static Stage21BGeneratedWorldRuntimePersistentState stage21b(
            LiveRuntime stage20,
            String first,
            String second) {
        var stage21A = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        return new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21A.captureState(),
                List.of(
                        FactionStrategicIntentState.initial(first),
                        FactionStrategicIntentState.initial(second)));
    }
}
