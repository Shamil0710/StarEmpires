package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.economy.Money;
import com.spacesim.persistence.EntityId;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.trade.MarketDirectory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionProjectSystemIntegrationTest {
    private static final long ROOT_SEED = 0x9B51_2026L;
    private static final String OWNER = "faction.miners";

    @Test
    void fundedConstructionSiteВиденExistingMarketDirectoryКакPhysicalConsumer() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.foundry", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 820f, 610f);
        world.fundConstructionProject(projectId, Money.fromCredits(40_000d));
        world.advanceFrame(0.1f);

        ConstructionProjectState project = world.findConstructionProject(projectId).orElseThrow();
        SimulationSession session = world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow();
        MarketDirectory directory = new MarketDirectory(content);
        directory.rebuild(session.getEngine().getEntities());
        int steelId = content.findItem("item.steel").runtimeId();

        assertTrue(directory.consumers(steelId).stream()
                .anyMatch(market -> market.id().equals(project.constructionSiteEntityId())),
                "Construction site должен использовать тот же market discovery path, что и обычные stations");
        assertTrue(directory.find(project.constructionSiteEntityId()).walletBalanceMilliCredits() > 0L);
        assertTrue(directory.find(project.constructionSiteEntityId()).freeCapacity() > 0);
    }

    @Test
    void constructionSiteНеПолучаетFactionLiquiditySupport() {
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.mining_base", DemoGalaxyFactory.INNER_SYSTEM_ID, 510f, 390f);

        assertEquals(0L, world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits());
        world.applyLiquiditySupport(OWNER);
        assertEquals(0L, world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits(),
                "Construction budget должен приходить только через explicit project funding");
    }

    @Test
    void constructionSiteНеПлатитCompletedStationTax() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(ROOT_SEED, content);
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            if (strategy.factionContentId().equals(OWNER)) {
                strategies.add(new FactionStrategicState(
                        strategy.factionContentId(),
                        strategy.minimumMarketAccessRelation(),
                        strategy.relations(),
                        strategy.controlledSystems(),
                        2_000,
                        strategy.foreignTerritoryTariffBasisPoints(),
                        strategy.stockPolicies(),
                        strategy.productionPolicies(),
                        strategy.strategicGoals()));
            } else {
                strategies.add(strategy);
            }
        }
        WorldState taxedState = new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                base.factions(),
                strategies,
                base.nextConstructionProjectIdValue(),
                base.constructionProjects());
        WorldSimulation world = WorldSimulation.restore(
                taxedState,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.mining_base", DemoGalaxyFactory.INNER_SYSTEM_ID, 510f, 390f);
        long funding = Money.fromCredits(200_000d);
        world.fundConstructionProject(projectId, funding);
        long beforeTax = world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits();

        world.applyFiscalPolicy(OWNER);

        assertEquals(beforeTax, world.findConstructionProject(projectId).orElseThrow().projectWalletMilliCredits(),
                "Project wallet не является completed-station tax base");
    }

    @Test
    void remoteSystemProjectДоходитДоCompletionЧерезCoarseSimulationClock() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.mining_base", DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 330f, 470f);
        world.fundConstructionProject(projectId, Money.fromCredits(25_000d));
        EntityId cargo = ConstructionProjectTestFixtures.createLoadedCargo(
                world, content, DemoGalaxyFactory.FRONTIER_SYSTEM_ID, 120, 60);
        world.deliverConstructionMaterial(projectId, cargo, "item.steel", 120);
        world.deliverConstructionMaterial(projectId, cargo, "item.energy", 60);

        ConstructionProjectStatus initial = world.findConstructionProject(projectId).orElseThrow().status();
        assertNotEquals(ConstructionProjectStatus.COMPLETED, initial);
        for (int frame = 0; frame < 2_000; frame++) {
            world.advanceFrame(0.1f);
            if (world.findConstructionProject(projectId).orElseThrow().status()
                    == ConstructionProjectStatus.COMPLETED) {
                break;
            }
        }

        ConstructionProjectState completed = world.findConstructionProject(projectId).orElseThrow();
        assertEquals(ConstructionProjectStatus.COMPLETED, completed.status());
        assertTrue(world.findSession(DemoGalaxyFactory.FRONTIER_SYSTEM_ID).orElseThrow()
                .getEntityRegistry().contains(completed.completedStationEntityId()));
    }

    @Test
    void cancellationПослеПервойPhysicalDeliveryЯвноОтклоняется() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = DemoGalaxyFactory.create(ROOT_SEED);
        ConstructionProjectId projectId = world.createConstructionProject(
                OWNER, "station.mining_base", DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 120f, 150f);
        world.fundConstructionProject(projectId, Money.fromCredits(25_000d));
        EntityId cargo = ConstructionProjectTestFixtures.createLoadedCargo(
                world, content, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, 1, 0);
        world.deliverConstructionMaterial(projectId, cargo, "item.steel", 1);

        assertThrows(IllegalStateException.class, () -> world.cancelConstructionProject(projectId));
        assertEquals(1L, world.findConstructionProject(projectId).orElseThrow().totalDeliveredUnits());
    }
}
