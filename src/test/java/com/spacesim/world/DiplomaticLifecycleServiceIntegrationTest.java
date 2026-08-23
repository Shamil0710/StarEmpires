package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.persistence.Stage19ConflictState;
import com.spacesim.warfare.Stage19ConflictRuntime;
import com.spacesim.world.DiplomaticLifecycleState.CrisisEscalation;
import com.spacesim.world.DiplomaticLifecycleState.ObligationOutcome;
import com.spacesim.world.DiplomaticLifecycleState.ProposalKind;
import com.spacesim.world.DiplomaticLifecycleState.ProposalStatus;
import com.spacesim.world.DiplomaticLifecycleState.RelationEvent;
import com.spacesim.world.DiplomaticLifecycleState.RelationFactor;
import com.spacesim.world.DiplomaticLifecycleState.Term;
import com.spacesim.world.DiplomaticLifecycleState.TermKind;
import com.spacesim.world.DiplomaticLifecycleState.WarGoal;
import com.spacesim.world.DiplomaticLifecycleState.WarGoalKind;
import com.spacesim.world.DiplomaticLifecycleState.WarStartKind;
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
    void proposalResponseDeadlineDoesNotBecomeAcceptedTreatyExpiry() {
        WorldSimulation world = DemoGalaxyFactory.create(21_305L);
        DiplomaticLifecycleService service = service(world);
        long deadline = world.getAuthoritativeWorldTick() + 2L;
        var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.trade.duration-seam",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.TRADE,
                "route.duration-seam",
                List.of(),
                List.of(),
                deadline));
        proposal = service.materializeTreatyOffer(proposal.proposalId());
        String treatyId = proposal.linkedTreatyId();
        assertEquals(-1L, world.findDiplomaticTreaty(treatyId).orElseThrow().expiresTick());
        service.accept(proposal.proposalId());

        advancePast(world, deadline);

        DiplomaticTreatyState treaty = world.findDiplomaticTreaty(treatyId).orElseThrow();
        assertEquals(DiplomaticTreatyState.Status.ACTIVE, treaty.status());
        assertTrue(treaty.activeAt(world.getAuthoritativeWorldTick()));
    }

    @Test
    void expiredStage21CProposalClosesLinkedStage17TreatyOffer() {
        WorldSimulation world = DemoGalaxyFactory.create(21_306L);
        DiplomaticLifecycleService service = service(world);
        long deadline = world.getAuthoritativeWorldTick() + 2L;
        var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.trade.expiry-seam",
                TRADE_LEAGUE,
                MINERS,
                ProposalKind.TRADE,
                "route.expiry-seam",
                List.of(),
                List.of(),
                deadline));
        proposal = service.materializeTreatyOffer(proposal.proposalId());
        String proposalId = proposal.proposalId();
        String treatyId = proposal.linkedTreatyId();

        advancePast(world, deadline);

        assertEquals(1, service.expireDueProposals());
        assertEquals(
                ProposalStatus.EXPIRED,
                service.snapshot().proposals().stream()
                        .filter(saved -> saved.proposalId().equals(proposalId))
                        .findFirst()
                        .orElseThrow()
                        .status());
        assertEquals(
                DiplomaticTreatyState.Status.REJECTED,
                world.findDiplomaticTreaty(treatyId).orElseThrow().status());
    }

    @Test
    void everyRequiredProposalFamilyHasPersistentIdentity() {
        WorldSimulation world = DemoGalaxyFactory.create(21_307L);
        DiplomaticLifecycleService service = service(world);
        long deadline = world.getAuthoritativeWorldTick() + 100L;

        for (ProposalKind kind : ProposalKind.values()) {
            var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                    "goal.family." + kind.name().toLowerCase(),
                    TRADE_LEAGUE,
                    MINERS,
                    kind,
                    "issue.family." + kind.name().toLowerCase(),
                    List.of(),
                    List.of(),
                    deadline));
            assertFalse(proposal.proposalId().isBlank());
            assertEquals(kind, proposal.kind());
        }

        assertEquals(ProposalKind.values().length, service.snapshot().proposals().size());
        for (ProposalKind kind : ProposalKind.values()) {
            assertTrue(service.snapshot().proposals().stream().anyMatch(proposal -> proposal.kind() == kind));
        }
    }

    @Test
    void allRequiredRelationFactorsContributeOnlyThroughRememberedActorEvidence() {
        WorldSimulation world = DemoGalaxyFactory.create(21_308L);
        DiplomaticLifecycleService service = service(world);
        long now = world.getAuthoritativeWorldTick();

        for (RelationFactor factor : RelationFactor.values()) {
            service.remember(
                    TRADE_LEAGUE,
                    MINERS,
                    new RelationEvent(
                            "memory.factor." + factor.name().toLowerCase(),
                            factor,
                            5,
                            now,
                            "subject." + factor.name().toLowerCase()));
        }

        assertEquals(RelationFactor.values().length * 5, service.derivedRelation(TRADE_LEAGUE, MINERS));
        assertEquals(RelationFactor.values().length, service.snapshot().relationMemories().get(0).events().size());
        assertThrows(IllegalArgumentException.class, () -> service.remember(
                TRADE_LEAGUE,
                MINERS,
                new RelationEvent("memory.future", RelationFactor.THREAT, -5, now + 1L, "future.hidden")));
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
    void observedHostileAttackCanCreateLegalWarWithoutFabricatingACrisis() {
        WorldSimulation world = DemoGalaxyFactory.create(21_304L);
        Stage19ConflictRuntime warfare = new Stage19ConflictRuntime(
                Stage19ConflictState.empty(world.getAuthoritativeWorldTick()));
        DiplomaticLifecycleService service = new DiplomaticLifecycleService(
                world, warfare, DiplomaticLifecycleState.empty(world.getAuthoritativeWorldTick()));

        var war = service.declareWarFromObservedAttack(
                TRADE_LEAGUE,
                MINERS,
                "observed.attack.corona",
                world.getAuthoritativeWorldTick(),
                goals());

        assertEquals(WarStartKind.OBSERVED_HOSTILE_ATTACK, war.startEvidence().kind());
        assertTrue(war.startEvidence().crisisId().isEmpty());
        assertEquals(2, war.stage19ConflictIds().size());
        assertTrue(service.snapshot().crises().isEmpty());
    }

    @Test
    void guaranteeMayBeHonoredWithoutMutatingTreatyAuthorityAndBuildsPositiveMemory() {
        WorldSimulation world = DemoGalaxyFactory.create(21_309L);
        DiplomaticLifecycleService service = service(world);
        var proposal = service.propose(new DiplomaticLifecycleService.ProposalRequest(
                "goal.defensive-cooperation-honored",
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
                "observed.attack.miners.honored",
                true);

        assertEquals(ObligationOutcome.HONORED, decision.outcome());
        assertTrue(decision.reputationImpact() > 0);
        assertEquals(
                DiplomaticTreatyState.Status.ACTIVE,
                world.findDiplomaticTreaty(linked.linkedTreatyId()).orElseThrow().status());
        assertTrue(service.derivedRelation(MINERS, TRADE_LEAGUE) > 0);
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

    private static void advancePast(WorldSimulation world, long tick) {
        for (int attempt = 0; attempt < 100 && world.getAuthoritativeWorldTick() <= tick; attempt++) {
            world.advanceFrame(1.0f);
        }
        assertTrue(world.getAuthoritativeWorldTick() > tick);
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
