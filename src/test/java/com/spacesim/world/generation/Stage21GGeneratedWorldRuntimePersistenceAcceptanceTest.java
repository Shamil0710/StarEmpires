package com.spacesim.world.generation;

import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge.LiveRuntime;
import com.spacesim.persistence.Stage21AGeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage21BGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21CGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21DGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21EGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21FGeneratedWorldRuntimePersistentState;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage21GGeneratedWorldRuntimePersistentState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleService;
import com.spacesim.world.DiplomaticLifecycleState;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.FactionStrategicIntentState;
import com.spacesim.world.FleetCommandState;
import com.spacesim.world.SettlementRecoveryService;
import com.spacesim.world.SettlementRecoveryState;
import com.spacesim.world.SettlementRecoveryState.PaymentObligation;
import com.spacesim.world.SettlementRecoveryState.Settlement;
import com.spacesim.world.StrategicOperationState;
import com.spacesim.world.TerritorialTransitionState;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21GGeneratedWorldRuntimePersistenceAcceptanceTest {

    @Test
    void acceptedPeaceRecoveryRoundTripsDeterministicallyOverCompleteStage21FCheckpoint() {
        Fixture fixture = fixture();
        Stage21GGeneratedWorldRuntimePersistentState expected = Stage21GGeneratedWorldRuntimePersistentState.compose(
                fixture.stage21F(), fixture.recovery());

        byte[] first = Stage21GGeneratedWorldRuntimePersistenceCodec.encode(expected);
        Stage21GGeneratedWorldRuntimePersistentState decoded =
                Stage21GGeneratedWorldRuntimePersistenceCodec.decode(first);
        byte[] second = Stage21GGeneratedWorldRuntimePersistenceCodec.encode(decoded);

        assertArrayEquals(first, second);
        assertEquals(fixture.stage21F(), decoded.stage21FRuntime());
        assertEquals(fixture.recovery(), decoded.settlementRecovery());
        Settlement settlement = decoded.settlementRecovery().requireSettlement(1L);
        assertEquals(fixture.peaceProposalId(), settlement.proposalId());
        assertEquals(fixture.warId(), settlement.warId());
        assertEquals(1, decoded.settlementRecovery().payments().size());
        PaymentObligation payment = decoded.settlementRecovery().payments().get(0);
        assertEquals(25_000L, payment.amountMilliCredits());
        assertEquals(fixture.first(), payment.payerFactionId());
        assertEquals(fixture.second(), payment.recipientFactionId());
    }

    @Test
    void recoveryReferencesMustMatchAcceptedPeaceAndEmbeddedAuthoritativeTime() {
        Fixture fixture = fixture();
        Settlement original = fixture.recovery().requireSettlement(1L);
        Settlement unknownProposal = new Settlement(
                original.id(),
                "proposal.stage21g.unknown",
                original.warId(),
                original.factionA(),
                original.factionB(),
                original.openedTick(),
                original.updatedTick(),
                original.status(),
                original.memoryRecorded());
        SettlementRecoveryState wrongReference = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                fixture.now(),
                2L,
                1L,
                List.of(unknownProposal),
                fixture.recovery().payments(),
                List.of(),
                List.of(),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistentState.compose(fixture.stage21F(), wrongReference));

        SettlementRecoveryState future = new SettlementRecoveryState(
                SettlementRecoveryState.CURRENT_VERSION,
                fixture.now() + 1L,
                2L,
                1L,
                fixture.recovery().settlements(),
                fixture.recovery().payments(),
                List.of(),
                List.of(),
                List.of());
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistentState.compose(fixture.stage21F(), future));
    }

    @Test
    void futureOrCorruptTopLevelPayloadFailsClosed() {
        Fixture fixture = fixture();
        byte[] valid = Stage21GGeneratedWorldRuntimePersistenceCodec.encode(
                Stage21GGeneratedWorldRuntimePersistentState.compose(fixture.stage21F(), fixture.recovery()));

        byte[] futureFile = valid.clone();
        ByteBuffer.wrap(futureFile).putInt(4, 99);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistenceCodec.decode(futureFile));

        byte[] futureSchema = valid.clone();
        ByteBuffer.wrap(futureSchema).putInt(8, Stage21GGeneratedWorldRuntimePersistentState.CURRENT_VERSION + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistenceCodec.decode(futureSchema));

        byte[] corruptMagic = valid.clone();
        corruptMagic[0] ^= 0x7f;
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistenceCodec.decode(corruptMagic));

        byte[] truncated = Arrays.copyOf(valid, valid.length - 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistenceCodec.decode(truncated));

        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        assertThrows(IllegalArgumentException.class,
                () -> Stage21GGeneratedWorldRuntimePersistenceCodec.decode(trailing));
    }

    private static Fixture fixture() {
        LiveRuntime stage20 = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        var initial = stage20.captureState();
        String first = initial.worldState().factions().get(0).factionContentId();
        String second = initial.worldState().factions().get(1).factionContentId();
        long now = stage20.world().getAuthoritativeWorldTick();

        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(Stage19ConflictState.empty(now));
        DiplomaticLifecycleService diplomacy = new DiplomaticLifecycleService(
                stage20.world(), warfare, DiplomaticLifecycleState.empty(now));
        var war = diplomacy.declareWarFromObservedAttack(
                first,
                second,
                "observed.attack.stage21g.persistence",
                now,
                List.of(
                        new WarGoal("goal.stage21g.first", first, WarGoalKind.SECURITY, "security.frontier", true),
                        new WarGoal("goal.stage21g.second", second, WarGoalKind.SECURITY, "security.frontier", true)));
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.stage21g.peace",
                first,
                second,
                ProposalKind.PEACE,
                war.warId(),
                List.of(),
                List.of(new Term(TermKind.TREASURY_PAYMENT, "reparations.stage21g", 25_000L)),
                now + 120L));
        peace = diplomacy.accept(peace.proposalId());

        Stage21BGeneratedWorldRuntimePersistentState stage21B = stage21B(stage20, first, second);
        Stage21CGeneratedWorldRuntimePersistentState stage21C = new Stage21CGeneratedWorldRuntimePersistentState(
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_VERSION,
                Stage21CGeneratedWorldRuntimePersistentState.CURRENT_RUNTIME_VERSION,
                stage21B,
                diplomacy.snapshot(),
                warfare.snapshot());
        Stage21DGeneratedWorldRuntimePersistentState stage21D = Stage21DGeneratedWorldRuntimePersistentState.compose(
                stage21C, FleetCommandState.empty());
        Stage21EGeneratedWorldRuntimePersistentState stage21E = Stage21EGeneratedWorldRuntimePersistentState.compose(
                stage21D, StrategicOperationState.empty());
        Stage21FGeneratedWorldRuntimePersistentState stage21F = Stage21FGeneratedWorldRuntimePersistentState.compose(
                stage21E, TerritorialTransitionState.empty());

        SettlementRecoveryService recovery = new SettlementRecoveryService(SettlementRecoveryState.empty(now));
        recovery.openAcceptedSettlement(diplomacy, peace.proposalId(), now);
        assertTrue(!recovery.snapshot().payments().isEmpty());
        return new Fixture(stage21F, recovery.snapshot(), first, second, war.warId(), peace.proposalId(), now);
    }

    private static Stage21BGeneratedWorldRuntimePersistentState stage21B(
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

    private record Fixture(
            Stage21FGeneratedWorldRuntimePersistentState stage21F,
            SettlementRecoveryState recovery,
            String first,
            String second,
            String warId,
            String peaceProposalId,
            long now) {
    }
}
