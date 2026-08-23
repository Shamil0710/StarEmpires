package com.spacesim.world.generation;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.CustomsTariffResolver;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticMarketAccessResolver;
import com.spacesim.world.FactionStrategicIntentState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21CGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void acceptedTradeAndPoliticalLifecycleRoundTripWithoutRewritingEmbeddedAuthorities() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var initial = stage20.captureState();
        String first = initial.worldState().factions().get(0).factionContentId();
        String second = initial.worldState().factions().get(1).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                stage20.world(), warfare, DiplomaticLifecycleState.empty(now));
        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.generated.trade",
                first,
                second,
                DiplomaticLifecycleState.ProposalKind.TRADE,
                "route.generated.trade",
                List.of(),
                List.of(),
                now + 120L));
        proposal = diplomacy.materializeTreatyOffer(proposal.proposalId());
        diplomacy.accept(proposal.proposalId());

        Stage21BGeneratedWorldRuntimePersistentState stage21b = stage21b(stage20, first, second);
        Stage21CGeneratedWorldRuntimePersistentState stage21c =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21b,
                        diplomacy.snapshot(),
                        warfare.snapshot());

        byte[] encoded = Stage21CGeneratedWorldRuntimePersistenceCodec.encode(stage21c);
        Stage21CGeneratedWorldRuntimePersistentState decoded =
                Stage21CGeneratedWorldRuntimePersistenceCodec.decode(encoded);

        assertArrayEquals(encoded, Stage21CGeneratedWorldRuntimePersistenceCodec.encode(decoded));
        assertArrayEquals(
                Stage21BGeneratedWorldRuntimePersistenceCodec.encode(stage21b),
                Stage21BGeneratedWorldRuntimePersistenceCodec.encode(decoded.stage21BRuntime()));
        var savedWorld = decoded.stage21BRuntime().stage21ARuntime().stage20Runtime().worldState();
        var access = DiplomaticMarketAccessResolver.evaluate(
                savedWorld.factionStrategies(),
                savedWorld.factionDiplomacyStates(),
                first,
                second,
                decoded.diplomacyLifecycle().simulationTick());
        assertTrue(access.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, access.reason());
        assertEquals(proposal.linkedTreatyId(), access.instrumentId());
        var tariff = CustomsTariffResolver.evaluate(
                savedWorld.factionDiplomacyStates(),
                first,
                second,
                decoded.diplomacyLifecycle().simulationTick());
        assertEquals(CustomsTariffResolver.Reason.TREATY_EXEMPTION, tariff.reason());
        assertEquals(proposal.linkedTreatyId(), tariff.instrumentId());
    }

    @Test
    void legalWarAndItsExactActorPerspectiveStage19ConflictsRoundTripTogether() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED + 1L).runtime();
        var initial = stage20.captureState();
        String first = initial.worldState().factions().get(0).factionContentId();
        String second = initial.worldState().factions().get(1).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                stage20.world(), warfare, DiplomaticLifecycleState.empty(now));
        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.generated.security",
                first,
                second,
                DiplomaticLifecycleState.ProposalKind.ULTIMATUM,
                "security.generated-frontier",
                List.of(),
                List.of(),
                now + 120L));
        var crisis = diplomacy.openCrisis(proposal.proposalId(), "decision.open", now + 120L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "decision.pressure", now + 120L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "decision.ultimatum", now + 120L);
        crisis = diplomacy.escalateCrisis(crisis.crisisId(), "decision.war", now + 120L);
        var war = diplomacy.declareWarFromCrisis(
                crisis.crisisId(),
                List.of(
                        new DiplomaticLifecycleState.WarGoal(
                                "war-goal.first.security",
                                first,
                                DiplomaticLifecycleState.WarGoalKind.SECURITY,
                                "security.generated-frontier",
                                true),
                        new DiplomaticLifecycleState.WarGoal(
                                "war-goal.second.security",
                                second,
                                DiplomaticLifecycleState.WarGoalKind.SECURITY,
                                "security.generated-frontier",
                                true)));

        Stage21BGeneratedWorldRuntimePersistentState stage21b = stage21b(stage20, first, second);
        Stage21CGeneratedWorldRuntimePersistentState stage21c =
                new Stage21CGeneratedWorldRuntimePersistentState(
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21b,
                        diplomacy.snapshot(),
                        warfare.snapshot());
        Stage21CGeneratedWorldRuntimePersistentState decoded =
                Stage21CGeneratedWorldRuntimePersistenceCodec.decode(
                        Stage21CGeneratedWorldRuntimePersistenceCodec.encode(stage21c));

        assertEquals(List.of(war), decoded.diplomacyLifecycle().wars());
        Stage19ConflictRuntime restoredWarfare = new Stage19ConflictRuntime(decoded.warfareState());
        for (String conflictId : war.stage19ConflictIds()) {
            assertTrue(restoredWarfare.find(conflictId).isPresent());
        }
        assertThrows(IllegalArgumentException.class, () -> new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21b,
                diplomacy.snapshot(),
                Stage19ConflictState.empty(now)));
    }

    @Test
    void compositionRejectsUnknownFactionReferences() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        String first = stage20.captureState().worldState().factions().get(0).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();
        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first), 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21b =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21a.captureState(),
                        List.of(FactionStrategicIntentState.initial(first)));
        DiplomaticLifecycleState corrupt = new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                now,
                1L,
                1L,
                1L,
                List.of(new DiplomaticLifecycleState.RelationMemory(
                        first,
                        "faction.not-in-world",
                        List.of())),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21b,
                corrupt,
                Stage19ConflictState.empty(now)));
    }

    @Test
    void compositionRejectsFutureActorMemoryEvidence() {
        var stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var initial = stage20.captureState();
        String first = initial.worldState().factions().get(0).factionContentId();
        String second = initial.worldState().factions().get(1).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();
        Stage21BGeneratedWorldRuntimePersistentState stage21b = stage21b(stage20, first, second);
        DiplomaticLifecycleState futureEvidence = new DiplomaticLifecycleState(
                DiplomaticLifecycleState.CURRENT_VERSION,
                now,
                1L,
                1L,
                1L,
                List.of(new DiplomaticLifecycleState.RelationMemory(
                        first,
                        second,
                        List.of(new DiplomaticLifecycleState.RelationEvent(
                                "memory.future",
                                DiplomaticLifecycleState.RelationFactor.THREAT,
                                -20,
                                now + 1L,
                                "future.security-report")))),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        assertThrows(IllegalArgumentException.class, () -> new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21b,
                futureEvidence,
                Stage19ConflictState.empty(now)));
    }

    private static Stage21BGeneratedWorldRuntimePersistentState stage21b(
            LiveRuntime stage20,
            String first,
            String second) {
        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        return new Stage21BGeneratedWorldRuntimePersistentState(
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21a.captureState(),
                List.of(
                        FactionStrategicIntentState.initial(first),
                        FactionStrategicIntentState.initial(second)));
    }
}
