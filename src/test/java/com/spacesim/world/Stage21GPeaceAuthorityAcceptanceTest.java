package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.SettlementRecoveryState.ObligationStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage21GPeaceAuthorityAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void acceptingAndOpeningPeaceDoesNotRepairRefillRecreateOrPayAnythingForFree() {
        WorldSimulation world = DemoGalaxyFactory.create(21_797L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.stage21g.no-grant",
                world.getAuthoritativeWorldTick(), goals());
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.stage21g.no-grant",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.PEACE,
                war.warId(),
                List.of(),
                List.of(new Term(TermKind.TREASURY_PAYMENT, "reparations.stage21g.no-grant", 1_000L)),
                now + 100L));
        var physicalBefore = world.snapshot();
        long payerBefore = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits();
        long recipientBefore = world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits();

        peace = diplomacy.accept(peace.proposalId());
        SettlementRecoveryService recovery = new SettlementRecoveryService(SettlementRecoveryState.empty(now));
        var settlement = recovery.openAcceptedSettlement(diplomacy, peace.proposalId(), now);

        assertEquals(physicalBefore, world.snapshot(),
                "legal peace and recovery-plan opening must not mutate physical/economic world state");
        assertEquals(payerBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(recipientBefore,
                world.findFactionEconomicState(MINERS).orElseThrow().treasuryMilliCredits());
        assertEquals(ObligationStatus.PENDING, recovery.snapshot().payments().get(0).status());
        assertEquals(SettlementRecoveryState.SettlementStatus.PENDING,
                recovery.snapshot().requireSettlement(settlement.id()).status());
    }

    @Test
    void peaceAccessTermIsMaterializedByExistingStage17LawBeforeStage21GRecovery() {
        WorldSimulation world = DemoGalaxyFactory.create(21_798L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE, MINERS, "observed.attack.stage21g.access",
                world.getAuthoritativeWorldTick(), goals());
        long now = world.getAuthoritativeWorldTick();
        var peace = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.stage21g.access",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.PEACE,
                war.warId(),
                List.of(),
                List.of(new Term(TermKind.MARKET_ACCESS, "market-access.stage21g", 0L)),
                now + 100L));
        peace = diplomacy.accept(peace.proposalId());
        assertTrue(!peace.linkedTreatyId().isEmpty());
        var access = DiplomaticMarketAccessResolver.evaluate(
                world.snapshot().factionStrategies(),
                world.snapshot().factionDiplomacyStates(),
                TRADE_LEAGUE,
                MINERS,
                now);
        assertTrue(access.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, access.reason());
        var afterLegalAcceptance = world.snapshot();

        SettlementRecoveryService recovery = new SettlementRecoveryService(SettlementRecoveryState.empty(now));
        var settlement = recovery.openAcceptedSettlement(diplomacy, peace.proposalId(), now);
        recovery.finalizeRecoveryPlan(settlement.id(), now);

        assertEquals(afterLegalAcceptance, world.snapshot(),
                "Stage-21G recovery must consume accepted law rather than rematerialize treaty effects");
        assertEquals(SettlementRecoveryState.SettlementStatus.COMPLETE,
                recovery.snapshot().requireSettlement(settlement.id()).status());
    }

    private static DiplomaticLifecycleService diplomacy(WorldSimulation world) {
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        return new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal("goal.tl.stage21g.authority", TRADE_LEAGUE,
                        WarGoalKind.SECURITY, "security.tl.stage21g", true),
                new WarGoal("goal.miners.stage21g.authority", MINERS,
                        WarGoalKind.SECURITY, "security.miners.stage21g", true));
    }
}
