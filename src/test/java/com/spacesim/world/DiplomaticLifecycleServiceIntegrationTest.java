package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ObligationOutcome;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStatus;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomaticLifecycleServiceIntegrationTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void acceptedTradeProposalUsesStage17TreatyAccessAndTariffLaw() {
        WorldSimulation world = DemoGalaxyFactory.create(21_300L);
        DiplomaticLifecycleService service = service(world);
        var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.trade.anchor",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.TRADE,
                "route.anchor-corona",
                List.of(),
                List.of(),
                100L));

        var linked = service.materializeTreatyOffer(proposal.proposalId());
        assertFalse(linked.linkedTreatyId().isEmpty());
        service.accept(linked.proposalId());

        DiplomaticMarketAccessResolver.Decision access = world.evaluateFactionMarketAccess(
                TRADE_LEAGUE, MINERS);
        assertTrue(access.allowed());
        assertEquals(DiplomaticMarketAccessResolver.Reason.EXPLICIT_TREATY_RIGHT, access.reason());
        assertEquals(linked.linkedTreatyId(), access.instrumentId());

        CustomsTariffResolver.Decision tariff = CustomsTariffResolver.evaluate(
                world.getFactionDiplomacyStates(),
                TRADE_LEAGUE,
                MINERS,
                world.getAuthoritativeWorldTick());
        assertEquals(CustomsTariffResolver.Reason.TREATY_EXEMPTION, tariff.reason());
        assertEquals(0, tariff.basisPoints());
        assertEquals(linked.linkedTreatyId(), tariff.instrumentId());
    }

    @Test
    void negotiationCannotPromiseTreasuryOrTerritoryTheGrantorDoesNotOwn() {
        WorldSimulation world = DemoGalaxyFactory.create(21_301L);
        DiplomaticLifecycleService service = service(world);
        FactionEconomicState economy = world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow();
        long spendable = economy.treasuryMilliCredits() - economy.treasuryReserveFloorMilliCredits();

        assertThrows(IllegalStateException.class, () -> service.propose(
                new DiplomaticLifecycleService.ProposalRequest(
                        "goal.reparation.too-large",
                        TRADE_LEAGUE,
                        MINERS,
                        ProposalKind.PEACE,
                        "war.none",
                        List.of(),
                        List.of(new Term(TermKind.TREASURY_PAYMENT, "reparations", spendable + 1L)),
                        100L)));

        assertThrows(IllegalStateException.class, () -> service.propose(
                new DiplomaticLifecycleService.ProposalRequest(
                        "goal.illegal-concession",
                        TRADE_LEAGUE,
                        MINERS,
                        ProposalKind.CONSTRUCTION_RIGHTS,
                        DemoGalaxyFactory.INNER_SYSTEM_ID.toString(),
                        List.of(),
                        List.of(new Term(
                                TermKind.CONSTRUCTION_RIGHT,
                                Long.toString(DemoGalaxyFactory.INNER_SYSTEM_ID.value()),
                                0L)),
                        100L)));

        var legal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.legal-concession",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.CONSTRUCTION_RIGHTS,
                Long.toString(DemoGalaxyFactory.ACTIVE_SYSTEM_ID.value()),
                List.of(),
                List.of(new Term(
                        TermKind.CONSTRUCTION_RIGHT,
                        Long.toString(DemoGalaxyFactory.ACTIVE_SYSTEM_ID.value()),
                        0L)),
                100L));
        service.accept(legal.proposalId());
        assertTrue(world.hasTerritorialConstructionRight(
                TRADE_LEAGUE, MINERS, DemoGalaxyFactory.ACTIVE_SYSTEM_ID));
    }

    @Test
    void warRequiresPersistedCauseCreatesStage19ConflictsAndCannotOscillateAfterPeace() {
        WorldSimulation world = DemoGalaxyFactory.create(21_302L);
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        DiplomaticLifecycleService service = new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
        var ultimatum = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.coerce.anchor",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.ULTIMATUM,
                "security.anchor",
                List.of(),
                List.of(),
                100L));
        var crisis = service.openCrisis(ultimatum.proposalId(), "decision.open-crisis", 100L);
        String crisisId = crisis.crisisId();
        List<WarGoal> goals = goals();

        assertThrows(IllegalStateException.class, () -> service.declareWarFromCrisis(crisisId, goals));
        crisis = service.escalateCrisis(crisisId, "decision.pressure", 100L);
        assertEquals(CrisisEscalation.PRESSURE, crisis.escalation());
        crisis = service.escalateCrisis(crisisId, "decision.ultimatum", 100L);
        assertEquals(CrisisEscalation.ULTIMATUM, crisis.escalation());
        crisis = service.escalateCrisis(crisisId, "decision.war-authorized", 100L);
        assertEquals(CrisisEscalation.WAR_AUTHORIZED, crisis.escalation());

        var war = service.declareWarFromCrisis(crisisId, goals);
        assertEquals(WarStatus.ACTIVE, war.status());
        assertEquals(2, war.stage19ConflictIds().size());
        for (String conflictId : war.stage19ConflictIds()) {
            assertTrue(warfare.find(conflictId).isPresent());
        }

        var peace = service.peace(war.warId(), DiplomaticLifecycleService.MINIMUM_REESCALATION_COOLDOWN_TICKS);
        assertEquals(WarStatus.PEACE, peace.status());
        assertThrows(IllegalStateException.class, () -> service.declareWarFromObservedAttack(
                TRADE_LEAGUE,
                MINERS,
                "attack.after-peace",
                world.getAuthoritativeWorldTick(),
                goals));
    }

    @Test
    void guaranteeMayBeRefusedButRefusalBreachesExistingTreatyAndCreatesReputationalMemory() {
        WorldSimulation world = DemoGalaxyFactory.create(21_303L);
        DiplomaticLifecycleService service = service(world);
        var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.defensive-cooperation",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.DEFENSIVE_COOPERATION,
                "security.corona",
                List.of(),
                List.of(),
                100L));
        var linked = service.materializeTreatyOffer(proposal.proposalId());
        service.accept(linked.proposalId());

        var decision = service.evaluateObligation(
                linked.linkedTreatyId(),
                TRADE_LEAGUE,
                MINERS,
                "observed.attack.miners",
                false);

        assertEquals(ObligationOutcome.REFUSED, decision.outcome());
        assertTrue(decision.reputationImpact() < 0);
        assertEquals(
                DiplomaticTreatyState.Status.BREACHED,
                world.findDiplomaticTreaty(linked.linkedTreatyId()).orElseThrow().status());
        assertTrue(service.derivedRelation(MINERS, TRADE_LEAGUE) < 0);
    }

    private static DiplomaticLifecycleService service(WorldSimulation world) {
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        return new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));
    }

    private static List<WarGoal> goals() {
        return List.of(
                new WarGoal(
                        "war-goal.trade-league.security",
                        TRADE_LEAGUE,
                        WarGoalKind.SECURITY,
                        "security.anchor",
                        true),
                new WarGoal(
                        "war-goal.miners.security",
                        MINERS,
                        WarGoalKind.SECURITY,
                        "security.corona",
                        true));
    }
}