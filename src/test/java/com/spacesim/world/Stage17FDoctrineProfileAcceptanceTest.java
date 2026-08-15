package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FDoctrineProfileAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String MINERS = "faction.miners";

    @Test
    void doctrineAxesAreBoundedAndNeutralMigrationProfileUsesTheMidpoint() {
        FactionDoctrineState neutral = FactionDoctrineState.neutral();

        assertEquals(new FactionDoctrineState(50, 50, 50, 50, 50, 50, 50), neutral);
        assertThrows(IllegalArgumentException.class,
                () -> new FactionDoctrineState(-1, 50, 50, 50, 50, 50, 50));
        assertThrows(IllegalArgumentException.class,
                () -> new FactionDoctrineState(50, 50, 50, 50, 50, 50, 101));
    }

    @Test
    void commonDoctrineBoundaryChangesDecisionWeightsWithoutChangingPhysicalOrLegalState() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(17_601L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        DiplomaticTreatyCommandResult offer = world.applyDiplomaticTreatyCommand(
                new DiplomaticTreatyCommand.Offer(
                        MINERS,
                        TRADE_LEAGUE,
                        List.of(new DiplomaticTreatyClauseState(
                                DiplomaticTreatyClauseState.Kind.MARKET_ACCESS,
                                DiplomaticTreatyClauseState.Direction.OWNER_TO_COUNTERPARTY,
                                null)),
                        -1L));
        DiplomaticTreatyEvaluationInputs inputs = new DiplomaticTreatyEvaluationInputs(
                100_000L,
                80,
                0,
                0,
                0L,
                world.getAuthoritativeWorldTick(),
                10_000);

        FactionDoctrineState closedResilient = new FactionDoctrineState(
                0, 50, 50, 50, 50, 50, 100);
        world.updateFactionDoctrine(TRADE_LEAGUE, closedResilient);
        DiplomaticTreatyEvaluation closedEvaluation = DiplomaticTreatyEvaluator.evaluate(
                world, offer.treaty().treatyId(), TRADE_LEAGUE, inputs);

        WorldState beforeOpenUpdate = world.snapshot();
        FactionStrategicState beforeStrategy = world.findFactionStrategicState(TRADE_LEAGUE).orElseThrow();
        DiplomaticMarketAccessResolver.Decision accessBefore =
                world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS);

        FactionDoctrineState openEfficient = new FactionDoctrineState(
                100, 50, 50, 50, 50, 50, 0);
        FactionStrategicState updated = world.updateFactionDoctrine(TRADE_LEAGUE, openEfficient);
        WorldState afterOpenUpdate = world.snapshot();
        DiplomaticTreatyEvaluation openEvaluation = DiplomaticTreatyEvaluator.evaluate(
                world, offer.treaty().treatyId(), TRADE_LEAGUE, inputs);

        assertEquals(openEfficient, updated.doctrine());
        assertEquals(expectedDoctrineReplacement(beforeStrategy, openEfficient), updated);
        assertEquals(beforeOpenUpdate.systems(), afterOpenUpdate.systems());
        assertEquals(beforeOpenUpdate.factions(), afterOpenUpdate.factions());
        assertEquals(beforeOpenUpdate.factionDiplomacyStates(), afterOpenUpdate.factionDiplomacyStates());
        assertEquals(accessBefore, world.evaluateFactionMarketAccess(TRADE_LEAGUE, MINERS));

        assertEquals(25, closedEvaluation.economicBenefitUtility());
        assertEquals(-80, closedEvaluation.dependencyUtility());
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.REJECT, closedEvaluation.recommendation());
        assertEquals(100, openEvaluation.economicBenefitUtility());
        assertEquals(-16, openEvaluation.dependencyUtility());
        assertEquals(DiplomaticTreatyEvaluation.Recommendation.ACCEPT, openEvaluation.recommendation());
        assertTrue(openEvaluation.totalUtilityPoints() > closedEvaluation.totalUtilityPoints());

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(afterOpenUpdate));
        assertEquals(openEfficient, decoded.factionStrategies().stream()
                .filter(strategy -> strategy.factionContentId().equals(TRADE_LEAGUE))
                .findFirst()
                .orElseThrow()
                .doctrine());
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(openEfficient,
                restored.findFactionStrategicState(TRADE_LEAGUE).orElseThrow().doctrine());
        assertEquals(afterOpenUpdate.systems(), restored.snapshot().systems());
        assertEquals(afterOpenUpdate.factions(), restored.snapshot().factions());
        assertEquals(afterOpenUpdate.factionDiplomacyStates(), restored.snapshot().factionDiplomacyStates());

        assertThrows(IllegalArgumentException.class,
                () -> world.updateFactionDoctrine("faction.unknown", FactionDoctrineState.neutral()));
        assertThrows(NullPointerException.class,
                () -> world.updateFactionDoctrine(TRADE_LEAGUE, null));
    }

    private static FactionStrategicState expectedDoctrineReplacement(
            FactionStrategicState source,
            FactionDoctrineState doctrine) {
        return new FactionStrategicState(
                source.factionContentId(),
                source.minimumMarketAccessRelation(),
                source.relations(),
                source.controlledSystems(),
                source.stationTaxBasisPoints(),
                source.foreignTerritoryTariffBasisPoints(),
                source.stockPolicies(),
                source.productionPolicies(),
                source.strategicGoals(),
                source.territorialClaims(),
                source.territorialControlStates(),
                source.territorialRecognitions(),
                source.constructionRightsGranted(),
                doctrine);
    }
}
