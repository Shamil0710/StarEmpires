package com.spacesim.world.generation;

import com.spacesim.persistence.Stage19ConflictState;
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

        var stage21a = Stage21AGeneratedWorldRuntimeBridge.materializeBootstrap(
                stage20, List.of(first, second), 30L);
        Stage21BGeneratedWorldRuntimePersistentState stage21b =
                new Stage21BGeneratedWorldRuntimePersistentState(
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                        Stage21BGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                        stage21a.captureState(),
                        List.of(
                                FactionStrategicIntentState.initial(first),
                                FactionStrategicIntentState.initial(second)));
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
}