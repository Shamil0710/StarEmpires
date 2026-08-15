package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiplomaticTreatyEvaluatorTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";
    private static final String NEUTRAL = "faction.neutral";

    @Test
    void favorableObservedProposalIsAcceptedDeterministicallyWithExplainableReasons() {
        WorldSimulation world = DemoGalaxyFactory.create(17_530L);
        DiplomaticTreatyState proposal = offerMarketAccess(world, TRADE_LEAGUE, MINERS);
        DiplomaticDecisionDoctrine doctrine = doctrine(30, -30, 5_000, 100L);
        DiplomaticTreatyEvaluationInputs inputs = new DiplomaticTreatyEvaluationInputs(
                80_000L,
                10,
                40,
                5,
                5_000L,
                world.getAuthoritativeWorldTick(),
                10_000);

        DiplomaticTreatyEvaluation first = DiplomaticTreatyEvaluator.evaluate(
                world, proposal.treatyId(), MINERS, doctrine, inputs);
        DiplomaticTreatyEvaluation second = DiplomaticTreatyEvaluator.evaluate(
                world, proposal.treatyId(), MINERS, doctrine, inputs);

        assertEquals(first, second);
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.ACCEPT, first.recommendation());
        assertTrue(first.totalUtilityPoints() >= doctrine.acceptUtilityThreshold());
        assertEquals(10_000, first.effectiveConfidenceBasisPoints());
        assertEquals(0L, first.observationAgeTicks());
        assertTrue(first.primaryReasons().stream().anyMatch(reason ->
                reason.reason() == DiplomaticTreatyEvaluation.Reason.ECONOMIC_BENEFIT
                        && reason.utilityPoints() > 0));
    }

    @Test
    void dependencySecuritySovereigntyAndFiscalRiskCanRejectProposal() {
        WorldSimulation world = DemoGalaxyFactory.create(17_531L);
        DiplomaticTreatyState proposal = offerMarketAccess(world, TRADE_LEAGUE, MINERS);
        DiplomaticDecisionDoctrine doctrine = doctrine(30, -30, 5_000, 100L);
        DiplomaticTreatyEvaluation evaluation = DiplomaticTreatyEvaluator.evaluate(
                world,
                proposal.treatyId(),
                MINERS,
                doctrine,
                new DiplomaticTreatyEvaluationInputs(
                        10_000L,
                        90,
                        -50,
                        80,
                        50_000L,
                        world.getAuthoritativeWorldTick(),
                        10_000));

        assertEquals(DiplomaticTreatyEvaluation.Recommendation.REJECT, evaluation.recommendation());
        assertTrue(evaluation.totalUtilityPoints() <= doctrine.rejectUtilityThreshold());
        assertTrue(evaluation.dependencyUtility() < 0);
        assertTrue(evaluation.securityUtility() < 0);
        assertTrue(evaluation.sovereigntyUtility() < 0);
        assertTrue(evaluation.fiscalCostUtility() < 0);
    }

    @Test
    void staleOrLowConfidenceInformationCannotAutoAcceptOtherwiseAttractiveProposal() {
        WorldSimulation world = DemoGalaxyFactory.create(17_532L);
        DiplomaticTreatyState proposal = offerMarketAccess(world, TRADE_LEAGUE, MINERS);
        advanceToAtLeast(world, 120L);
        DiplomaticDecisionDoctrine doctrine = doctrine(20, -40, 6_000, 50L);

        DiplomaticTreatyEvaluation stale = DiplomaticTreatyEvaluator.evaluate(
                world,
                proposal.treatyId(),
                MINERS,
                doctrine,
                new DiplomaticTreatyEvaluationInputs(
                        100_000L,
                        0,
                        100,
                        0,
                        0L,
                        0L,
                        10_000));
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.COUNTEROFFER, stale.recommendation());
        assertEquals(0, stale.effectiveConfidenceBasisPoints());
        assertEquals(0, stale.totalUtilityPoints());

        DiplomaticTreatyEvaluation lowConfidence = DiplomaticTreatyEvaluator.evaluate(
                world,
                proposal.treatyId(),
                MINERS,
                doctrine,
                new DiplomaticTreatyEvaluationInputs(
                        100_000L,
                        0,
                        100,
                        0,
                        0L,
                        world.getAuthoritativeWorldTick(),
                        2_000));
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.COUNTEROFFER, lowConfidence.recommendation());
        assertTrue(lowConfidence.totalUtilityPoints() >= doctrine.acceptUtilityThreshold(),
                "Positive utility may be known, but insufficient confidence must still block automatic acceptance");
        assertTrue(lowConfidence.effectiveConfidenceBasisPoints()
                < doctrine.minimumDecisionConfidenceBasisPoints());
    }

    @Test
    void evaluationUsesReceivingFactionsDirectedTrustAndCredibilityNotReverseOpinion() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(17_533L, content);
        List<FactionDiplomacyState> diplomacy = new ArrayList<>();
        for (FactionDiplomacyState state : base.factionDiplomacyStates()) {
            if (state.factionContentId().equals(MINERS)) {
                diplomacy.add(new FactionDiplomacyState(
                        MINERS,
                        List.of(new DiplomaticStandingState(TRADE_LEAGUE, -80, 10, 0L)),
                        List.of(),
                        List.of(),
                        List.of()));
            } else if (state.factionContentId().equals(TRADE_LEAGUE)) {
                diplomacy.add(new FactionDiplomacyState(
                        TRADE_LEAGUE,
                        List.of(new DiplomaticStandingState(MINERS, 80, 90, 0L)),
                        List.of(),
                        List.of(),
                        List.of()));
            } else {
                diplomacy.add(state);
            }
        }
        WorldSimulation world = restore(new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                base.factions(),
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps(),
                base.factionIdentities(),
                diplomacy), content);
        DiplomaticTreatyState proposal = offerMarketAccess(world, TRADE_LEAGUE, MINERS);
        DiplomaticDecisionDoctrine doctrine = new DiplomaticDecisionDoctrine(
                0, 0, 0, 0, 100, 100, 0,
                1_000L, 100L, 0, 30, -30);

        DiplomaticTreatyEvaluation evaluation = DiplomaticTreatyEvaluator.evaluate(
                world,
                proposal.treatyId(),
                MINERS,
                doctrine,
                new DiplomaticTreatyEvaluationInputs(0L, 0, 0, 0, 0L, 0L, 10_000));

        assertEquals(-80, evaluation.trustUtility());
        assertEquals(-80, evaluation.credibilityUtility());
        assertEquals(-160, evaluation.totalUtilityPoints());
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.REJECT, evaluation.recommendation());
    }

    @Test
    void onlyReceivingCounterpartyCanEvaluateOpenProposalAndFutureObservationIsRejected() {
        WorldSimulation world = DemoGalaxyFactory.create(17_534L);
        DiplomaticTreatyState proposal = offerMarketAccess(world, TRADE_LEAGUE, MINERS);
        DiplomaticDecisionDoctrine doctrine = doctrine(20, -20, 0, 100L);
        DiplomaticTreatyEvaluationInputs inputs = new DiplomaticTreatyEvaluationInputs(
                0L, 0, 0, 0, 0L, world.getAuthoritativeWorldTick(), 10_000);

        assertThrows(IllegalStateException.class, () -> DiplomaticTreatyEvaluator.evaluate(
                world, proposal.treatyId(), NEUTRAL, doctrine, inputs));
        assertThrows(IllegalArgumentException.class, () -> DiplomaticTreatyEvaluator.evaluate(
                world,
                proposal.treatyId(),
                MINERS,
                doctrine,
                new DiplomaticTreatyEvaluationInputs(
                        0L, 0, 0, 0, 0L, world.getAuthoritativeWorldTick() + 1L, 10_000)));

        world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Accept(MINERS, proposal.treatyId()));
        assertThrows(IllegalStateException.class, () -> DiplomaticTreatyEvaluator.evaluate(
                world, proposal.treatyId(), MINERS, doctrine, inputs));
    }

    private static DiplomaticDecisionDoctrine doctrine(
            int acceptThreshold,
            int rejectThreshold,
            int minimumConfidence,
            long decayTicks) {
        return new DiplomaticDecisionDoctrine(
                100,
                100,
                100,
                100,
                100,
                100,
                100,
                1_000L,
                decayTicks,
                minimumConfidence,
                acceptThreshold,
                rejectThreshold);
    }

    private static DiplomaticTreatyState offerMarketAccess(
            WorldSimulation world,
            String proposer,
            String counterparty) {
        return world.applyDiplomaticTreatyCommand(new DiplomaticTreatyCommand.Offer(
                proposer,
                counterparty,
                List.of(new DiplomaticTreatyClauseState(
                        DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                        DiplomaticTreatyClauseState.Direction.MUTUAL,
                        null)),
                -1L)).treaty();
    }

    private static void advanceToAtLeast(WorldSimulation world, long targetTick) {
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick) {
            world.advanceFrame(1f);
            if (++guard > 10_000) {
                throw new AssertionError("World did not reach diplomatic evaluation target tick");
            }
        }
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
