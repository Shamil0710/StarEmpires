package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.warfare.StrategicWarPolicyService;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import com.spacesim.warfare.StrategicWarPolicyService.EscalationLevel;
import com.spacesim.warfare.StrategicWarPolicyService.ObjectiveEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.PhysicalWarEvidence;
import com.spacesim.warfare.StrategicWarPolicyService.Policy;
import com.spacesim.warfare.StrategicWarPolicyService.SettlementOffer;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage21GPeaceOutcomePolicyTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String TRADE_GOAL = "goal.trade.security.stage21g";
    private static final Policy POLICY = new Policy(
            2,
            20_000d,
            true,
            2_000_000d,
            500_000d);

    @Test
    void visibleOfferCoveringMandatoryLegalGoalCanFlowIntoAcceptedPeaceAndRecovery() {
        WorldSimulation world = DemoGalaxyFactory.create(21_791L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE,
                MINERS,
                "observed.attack.stage21g.policy",
                world.getAuthoritativeWorldTick(),
                goals());
        Stage21GPeaceOutcomePolicy outcomes = new Stage21GPeaceOutcomePolicy(
                new StrategicWarPolicyService());
        PhysicalWarEvidence sustainable = evidence(4, 50_000d, 0d, 0d, 0d, 0d);

        var withoutCoverage = outcomes.evaluate(
                war,
                TRADE_LEAGUE,
                EscalationLevel.CRISIS,
                Map.of(TRADE_GOAL, ObjectiveEvidence.OBSERVED_UNMET),
                sustainable,
                POLICY,
                new SettlementOffer(true, Set.of()));
        var covered = outcomes.evaluate(
                war,
                TRADE_LEAGUE,
                EscalationLevel.CRISIS,
                Map.of(TRADE_GOAL, ObjectiveEvidence.OBSERVED_UNMET),
                sustainable,
                POLICY,
                new SettlementOffer(true, Set.of(TRADE_GOAL)));

        assertEquals(Decision.ESCALATE, withoutCoverage.decision());
        assertEquals(Decision.ACCEPT_SETTLEMENT, covered.decision());

        long now = world.getAuthoritativeWorldTick();
        var proposal = diplomacy.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.peace.stage21g.policy",
                MINERS,
                TRADE_LEAGUE,
                ProposalKind.PEACE,
                war.warId(),
                List.of(),
                List.of(),
                now + 100L));
        proposal = diplomacy.accept(proposal.proposalId());
        SettlementRecoveryService recovery = new SettlementRecoveryService(
                SettlementRecoveryState.empty(now));
        var settlement = recovery.openAcceptedSettlement(
                diplomacy, proposal.proposalId(), now);
        recovery.finalizeRecoveryPlan(settlement.id(), now);

        assertEquals(SettlementRecoveryState.SettlementStatus.COMPLETE,
                recovery.snapshot().requireSettlement(settlement.id()).status());
    }

    @Test
    void physicalExhaustionSeeksSettlementWithoutInventingVictoryOrOpponentKnowledge() {
        WorldSimulation world = DemoGalaxyFactory.create(21_792L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE,
                MINERS,
                "observed.attack.stage21g.exhaustion",
                world.getAuthoritativeWorldTick(),
                goals());
        Stage21GPeaceOutcomePolicy outcomes = new Stage21GPeaceOutcomePolicy(
                new StrategicWarPolicyService());

        var result = outcomes.evaluate(
                war,
                TRADE_LEAGUE,
                EscalationLevel.LIMITED_WAR,
                Map.of(TRADE_GOAL, ObjectiveEvidence.OBSERVED_UNMET),
                evidence(4, 19_999d, 0d, 0d, 0d, 0d),
                POLICY,
                SettlementOffer.none());

        assertEquals(Decision.SEEK_SETTLEMENT, result.decision());
        assertEquals(Set.of(TRADE_GOAL), result.unresolvedMandatoryObjectiveIds());
    }

    @Test
    void bridgeRejectsForeignGoalEvidenceAndForgedOfferCoverage() {
        WorldSimulation world = DemoGalaxyFactory.create(21_793L);
        DiplomaticLifecycleService diplomacy = diplomacy(world);
        var war = diplomacy.declareWarFromObservedAttack(
                TRADE_LEAGUE,
                MINERS,
                "observed.attack.stage21g.actor-bounds",
                world.getAuthoritativeWorldTick(),
                goals());
        Stage21GPeaceOutcomePolicy outcomes = new Stage21GPeaceOutcomePolicy(
                new StrategicWarPolicyService());

        assertThrows(IllegalArgumentException.class, () -> outcomes.evaluate(
                war,
                TRADE_LEAGUE,
                EscalationLevel.CRISIS,
                Map.of("goal.miners.security.stage21g", ObjectiveEvidence.OBSERVED_MET),
                evidence(4, 50_000d, 0d, 0d, 0d, 0d),
                POLICY,
                SettlementOffer.none()));
        assertThrows(IllegalArgumentException.class, () -> outcomes.evaluate(
                war,
                TRADE_LEAGUE,
                EscalationLevel.CRISIS,
                Map.of(TRADE_GOAL, ObjectiveEvidence.OBSERVED_UNMET),
                evidence(4, 50_000d, 0d, 0d, 0d, 0d),
                POLICY,
                new SettlementOffer(true, Set.of("goal.forged"))));
    }

    private static DiplomaticLifecycleService diplomacy(WorldSimulation world) {
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        return new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal(TRADE_GOAL, TRADE_LEAGUE, WarGoalKind.SECURITY, "security.trade", true),
                new WarGoal("goal.miners.security.stage21g", MINERS, WarGoalKind.SECURITY, "security.miners", true));
    }

    private static PhysicalWarEvidence evidence(
            int ships,
            double reactionMassKg,
            double repairDemandKg,
            double repairAvailableKg,
            double observedOpponentDestroyedMassKg,
            double observedOpponentUndeliveredCargoKg) {
        return new PhysicalWarEvidence(
                ships,
                reactionMassKg,
                repairDemandKg,
                repairAvailableKg,
                0d,
                0d,
                observedOpponentDestroyedMassKg,
                observedOpponentUndeliveredCargoKg);
    }
}
