package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.WalletComponent;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.SettlementRecoveryState.ObligationStatus;
import com.spacesim.world.SettlementRecoveryState.SettlementStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SettlementRecoveryServiceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void acceptedPeaceExecutesConservedReparationExactlyOnceAndBuildsPostWarMemory() {
        WorldSimulation world = DemoGalaxyFactory.create(21_700L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.21g",
                world.getAuthoritativeWorldTick(), goals());
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.21g", TRADE_LEAGUE, MINERS, ProposalKind.PEACE, war.warId(),
                List.of(),
                List.of(new Term(TermKind.TREASURY_PAYMENT, "reparations", 1_000L)),
                now + 100L));
        peace = diplomacy.accept(peace.proposalId());
        SettlementRecoveryService recovery = new SettlementRecoveryService(
                SettlementRecoveryState.empty(world.getAuthoritativeWorldTick()));
        var settlement = recovery.openAcceptedSettlement(diplomacy, peace.proposalId(), world.getAuthoritativeWorldTick());
        long payerBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();
        long recipientBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();

        recovery.executePayments(world, settlement.id(), world.getAuthoritativeWorldTick());
        long payerAfter = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();
        long recipientAfter = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();

        assertEquals(payerBefore - 1_000L, payerAfter);
        assertEquals(recipientBefore + 1_000L, recipientAfter);
        assertEquals(ObligationStatus.COMPLETE, recovery.snapshot().payments().get(0).status());
        assertEquals(SettlementStatus.COMPLETE, recovery.snapshot().requireSettlement(settlement.id()).status());

        recovery.executePayments(world, settlement.id(), world.getAuthoritativeWorldTick());
        assertEquals(payerAfter, world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(recipientAfter, world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());

        int beforeRelation = diplomacy.derivedRelation(TRADE_LEAGUE, MINERS);
        recovery.recordCompletionMemory(diplomacy, settlement.id(), world.getAuthoritativeWorldTick());
        assertTrue(diplomacy.derivedRelation(TRADE_LEAGUE, MINERS) > beforeRelation);
        recovery.recordCompletionMemory(diplomacy, settlement.id(), world.getAuthoritativeWorldTick());
        assertEquals(1L, recovery.snapshot().settlements().stream()
                .filter(value -> value.memoryRecorded()).count());
    }

    @Test
    void paymentStallsWithoutViolatingTreasuryReserveOrPartiallyTransferring() {
        WorldSimulation world = DemoGalaxyFactory.create(21_701L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.stall",
                world.getAuthoritativeWorldTick(), goals());
        FactionEconomicState economy = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow();
        long spendable = economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits();
        long payment = Math.max(1L, spendable / 2L);
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.stall", TRADE_LEAGUE, MINERS, ProposalKind.PEACE, war.warId(),
                List.of(), List.of(new Term(TermKind.TREASURY_PAYMENT, "reparations", payment)), now + 100L));
        peace = diplomacy.accept(peace.proposalId());
        SettlementRecoveryService recovery = new SettlementRecoveryService(SettlementRecoveryState.empty(now));
        var settlement = recovery.openAcceptedSettlement(diplomacy, peace.proposalId(), now);

        WalletComponent drain = new WalletComponent();
        assertTrue(world.transferFromFactionTreasury(
                TRADE_LEAGUE, drain, "test.stage21g.drain", spendable, "test-stage21g-drain"));
        long payerBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();
        long recipientBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();

        recovery.executePayments(world, settlement.id(), now);

        assertEquals(payerBefore, world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(recipientBefore, world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        assertEquals(ObligationStatus.STALLED, recovery.snapshot().payments().get(0).status());
        assertEquals(SettlementStatus.STALLED, recovery.snapshot().requireSettlement(settlement.id()).status());
    }

    @Test
    void unacceptedOrNonPeaceProposalCannotCreateRecoverySettlement() {
        WorldSimulation world = DemoGalaxyFactory.create(21_702L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.trade.not-peace", TRADE_LEAGUE, MINERS, ProposalKind.TRADE, "route.21g",
                List.of(), List.of(), world.getAuthoritativeWorldTick() + 100L));
        SettlementRecoveryService recovery = new SettlementRecoveryService(
                SettlementRecoveryState.empty(world.getAuthoritativeWorldTick()));

        assertThrows(IllegalStateException.class, () -> recovery.openAcceptedSettlement(
                diplomacy, proposal.proposalId(), world.getAuthoritativeWorldTick()));
    }

    private static DiplomaticLifecycleService diplomacy(WorldSimulation world) {
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        return new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal("goal.tl.security.21g", TRADE_LEAGUE, WarGoalKind.SECURITY, "security.tl", true),
                new WarGoal("goal.miners.security.21g", MINERS, WarGoalKind.SECURITY, "security.miners", true));
    }
}
