package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.AsteroidComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionExpansionOpportunityAnalyzerTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void boundedHorizonUsesNearestControlledTerritory() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11A0L);
        ExpansionOpportunityPolicy policy = new ExpansionOpportunityPolicy(
                1, 16, 30, 25, 15, 15, 15, 20, 3_000);

        List<ExpansionOpportunity> opportunities =
                FactionExpansionOpportunityAnalyzer.analyze(world, content, TRADE_LEAGUE, policy);

        assertEquals(1, opportunities.size());
        ExpansionOpportunity opportunity = opportunities.get(0);
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, opportunity.sourceSystemId());
        assertEquals(DemoGalaxyFactory.INNER_SYSTEM_ID, opportunity.targetSystemId());
        assertEquals(1, opportunity.path().jumpCount());
        assertEquals("faction.miners", opportunity.controllingFactionContentId());
        assertTrue(opportunity.foreignControlled());
        assertEquals("station.agrodome", opportunity.anchorStationArchetypeContentId());
        assertTrue(opportunity.constructionFundingMilliCredits() > 0L);
    }

    @Test
    void sameWorldProducesIdenticalDeterministicRanking() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11A1L);

        List<ExpansionOpportunity> first =
                FactionExpansionOpportunityAnalyzer.analyze(world, content, TRADE_LEAGUE);
        List<ExpansionOpportunity> second =
                FactionExpansionOpportunityAnalyzer.analyze(world, content, TRADE_LEAGUE);

        assertFalse(first.isEmpty());
        assertEquals(first, second);
    }

    @Test
    void resourceWeightedPolicyCanPreferRicherTwoHopFrontier() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(0x11A2L);
        materializeRemoteAsteroids(world);
        setRemainingAsteroidResource(world, DemoGalaxyFactory.INNER_SYSTEM_ID, 1L);
        setRemainingAsteroidResource(world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 1_000_000L);
        ExpansionOpportunityPolicy resourceOnly = new ExpansionOpportunityPolicy(
                2, 16, 100, 0, 0, 0, 0, 0, 0);

        List<ExpansionOpportunity> opportunities =
                FactionExpansionOpportunityAnalyzer.analyze(world, content, TRADE_LEAGUE, resourceOnly);

        assertEquals(2, opportunities.size());
        assertEquals(DemoGalaxyFactory.FRONTIER_SYSTEM_ID, opportunities.get(0).targetSystemId());
        assertEquals(2, opportunities.get(0).path().jumpCount());
        assertTrue(opportunities.get(0).remainingMineableUnits()
                > opportunities.get(1).remainingMineableUnits());
    }

    @Test
    void insufficientTreasuryProducesNoExpansionCandidate() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(0x11A3L, content);
        List<FactionEconomicState> factions = new ArrayList<>(base.factions().size());
        for (FactionEconomicState faction : base.factions()) {
            if (TRADE_LEAGUE.equals(faction.factionContentId())) {
                factions.add(new FactionEconomicState(
                        faction.factionContentId(),
                        Money.fromCredits(1d),
                        faction.stationLiquidityReserveMilliCredits(),
                        faction.maxLiquiditySupportPerDecisionMilliCredits()));
            } else {
                factions.add(faction);
            }
        }
        WorldState poorState = new WorldState(
                base.schemaVersion(),
                base.topology(),
                base.systems(),
                factions,
                base.factionStrategies(),
                base.nextConstructionProjectIdValue(),
                base.constructionProjects(),
                base.factionEconomicPressures(),
                base.nextFleetIdValue(),
                base.fleets(),
                base.fleetJumps());
        WorldSimulation poorWorld = WorldSimulation.restore(
                poorState,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        assertTrue(FactionExpansionOpportunityAnalyzer
                .analyze(poorWorld, content, TRADE_LEAGUE)
                .isEmpty());
    }

    private static void materializeRemoteAsteroids(WorldSimulation world) {
        float fixedStep = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID)
                .orElseThrow()
                .getClock()
                .getFixedStepSeconds();
        int guard = 0;
        while ((!hasAsteroids(world, DemoGalaxyFactory.INNER_SYSTEM_ID)
                || !hasAsteroids(world, DemoGalaxyFactory.FRONTIER_SYSTEM_ID))
                && guard++ < 100) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(guard < 100, "Remote scheduler did not materialize asteroid sources");
    }

    private static boolean hasAsteroids(WorldSimulation world, StarSystemId systemId) {
        for (Entity entity : world.findSession(systemId).orElseThrow().getEngine().getEntities()) {
            if (entity.getComponent(AsteroidComponent.class) != null) {
                return true;
            }
        }
        return false;
    }

    private static void setRemainingAsteroidResource(
            WorldSimulation world,
            StarSystemId systemId,
            long remainingPerAsteroid) {
        boolean found = false;
        for (Entity entity : world.findSession(systemId).orElseThrow().getEngine().getEntities()) {
            AsteroidComponent asteroid = entity.getComponent(AsteroidComponent.class);
            if (asteroid != null) {
                asteroid.remainingResource = remainingPerAsteroid;
                found = true;
            }
        }
        assertTrue(found, "Expected at least one physical asteroid in " + systemId);
    }
}
