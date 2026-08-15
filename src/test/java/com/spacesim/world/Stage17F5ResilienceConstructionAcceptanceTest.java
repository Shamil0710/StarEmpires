package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5ResilienceConstructionAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String STEEL = "item.steel";
    private static final String FOUNDRY = "station.foundry";

    @Test
    void actionableCapacityGapCreatesOnlyOrdinaryFundedMaterialBoundProject() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = world(content, 0x17F50041L);
        int steelRuntimeId = content.findItem(STEEL).runtimeId();
        createOwnedDeficit(world, TRADE_LEAGUE, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, steelRuntimeId, 320);
        FactionLocalProductionPlan localPlan = capacityGapPlan(world, TRADE_LEAGUE, STEEL);
        ContentCatalog.StationArchetypeDefinition foundry = content.findStationArchetype(FOUNDRY);
        assertNotNull(foundry);
        int foundriesBefore = ownedArchetypeCount(world, TRADE_LEAGUE, FOUNDRY);
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();
        int projectsBefore = world.getConstructionProjects().size();

        FactionResilienceConstructionRecommendation recommendation =
                FactionResilienceConstructionPlanner.recommendNext(world, localPlan).orElseThrow();
        FactionResilienceConstructionService.Result result =
                FactionResilienceConstructionService.startNext(world, localPlan).orElseThrow();

        assertEquals(TRADE_LEAGUE, recommendation.factionContentId());
        assertEquals(DemoGalaxyFactory.ACTIVE_SYSTEM_ID, recommendation.systemId());
        assertEquals(STEEL, recommendation.itemContentId());
        assertEquals(FOUNDRY, recommendation.stationArchetypeContentId());
        assertEquals(foundry.construction().materials(), recommendation.materials());
        assertEquals(foundry.construction().buildSeconds(), recommendation.buildSeconds());
        assertEquals(recommendation, result.recommendation());

        ConstructionProjectState project = result.project();
        assertEquals(projectsBefore + 1, world.getConstructionProjects().size());
        assertEquals(recommendation.fundingMilliCredits(), project.minimumFundingMilliCredits());
        assertEquals(recommendation.fundingMilliCredits(), project.projectWalletMilliCredits());
        assertEquals(
                treasuryBefore - recommendation.fundingMilliCredits(),
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
        assertEquals(
                treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits()
                        + project.projectWalletMilliCredits(),
                "Funding must be a conserved treasury-to-project transfer");
        assertEquals(ConstructionProjectStatus.FUNDED, project.status());
        assertFalse(project.materialsFulfilled());
        assertEquals(0L, project.totalDeliveredUnits());
        assertEquals(foundriesBefore, ownedArchetypeCount(world, TRADE_LEAGUE, FOUNDRY),
                "Funding must not materialize the completed producer");
        assertTrue(project.materials().stream().allMatch(material -> material.deliveredAmount() == 0));
    }

    @Test
    void fiscalConstructionCapBlocksProjectBeforeAnyTreasuryMutation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = world(content, 0x17F50042L);
        int steelRuntimeId = content.findItem(STEEL).runtimeId();
        createOwnedDeficit(world, TRADE_LEAGUE, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, steelRuntimeId, 320);
        FactionLocalProductionPlan localPlan = capacityGapPlan(world, TRADE_LEAGUE, STEEL);
        FactionResilienceConstructionRecommendation recommendation =
                FactionResilienceConstructionPlanner.recommendNext(world, localPlan).orElseThrow();
        FactionFiscalPolicyState current = world.findFactionFiscalPolicy(TRADE_LEAGUE).orElseThrow();
        world.updateFactionFiscalPolicy(
                TRADE_LEAGUE,
                new FactionFiscalPolicyState(
                        current.stationTaxBasisPoints(),
                        current.foreignTerritoryLevyBasisPoints(),
                        current.treasuryReserveFloorMilliCredits(),
                        current.stationLiquidityReserveMilliCredits(),
                        current.maxLiquiditySupportPerDecisionMilliCredits(),
                        recommendation.fundingMilliCredits() - 1L));
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();
        int projectsBefore = world.getConstructionProjects().size();

        assertTrue(FactionResilienceConstructionService.startNext(world, localPlan).isEmpty());

        assertEquals(projectsBefore, world.getConstructionProjects().size());
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
    }

    @Test
    void capacityGapWithoutCurrentOwnMarketDeficitDoesNotInventConstructionPressure() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = world(content, 0x17F50043L);
        int steelRuntimeId = content.findItem(STEEL).runtimeId();
        clearOwnedSignal(world, TRADE_LEAGUE, DemoGalaxyFactory.ACTIVE_SYSTEM_ID, steelRuntimeId);
        FactionLocalProductionPlan localPlan = capacityGapPlan(world, TRADE_LEAGUE, STEEL);
        int projectsBefore = world.getConstructionProjects().size();
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();

        assertTrue(FactionResilienceConstructionPlanner.recommendNext(world, localPlan).isEmpty());
        assertTrue(FactionResilienceConstructionService.startNext(world, localPlan).isEmpty());

        assertEquals(projectsBefore, world.getConstructionProjects().size());
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());
    }

    private static WorldSimulation world(ContentCatalog content, long seed) {
        return WorldSimulation.restore(
                DemoGalaxyFactory.createState(seed, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }

    private static FactionLocalProductionPlan capacityGapPlan(
            WorldSimulation world,
            String factionContentId,
            String itemContentId) {
        return new FactionLocalProductionPlan(
                factionContentId,
                world.getAuthoritativeWorldTick(),
                List.of(),
                List.of(itemContentId));
    }

    private static void createOwnedDeficit(
            WorldSimulation world,
            String factionContentId,
            StarSystemId systemId,
            int itemRuntimeId,
            int deficitUnits) {
        Entity marketEntity = firstOwnedMarket(world, factionContentId, systemId);
        MarketComponent market = marketEntity.getComponent(MarketComponent.class);
        InventoryComponent inventory = marketEntity.getComponent(InventoryComponent.class);
        market.configureTradableItem(itemRuntimeId, deficitUnits, 0f);
        market.targetStock[itemRuntimeId] = deficitUnits;
        inventory.stock[itemRuntimeId] = 0;
    }

    private static void clearOwnedSignal(
            WorldSimulation world,
            String factionContentId,
            StarSystemId systemId,
            int itemRuntimeId) {
        int factionRuntimeId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        for (Entity entity : world.findSession(systemId).orElseThrow().getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            MarketComponent market = entity.getComponent(MarketComponent.class);
            InventoryComponent inventory = entity.getComponent(InventoryComponent.class);
            if (faction != null && faction.factionId == factionRuntimeId && market != null && inventory != null) {
                market.targetStock[itemRuntimeId] = 0;
                inventory.stock[itemRuntimeId] = 0;
            }
        }
    }

    private static Entity firstOwnedMarket(
            WorldSimulation world,
            String factionContentId,
            StarSystemId systemId) {
        int factionRuntimeId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        for (Entity entity : world.findSession(systemId).orElseThrow().getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            if (faction != null
                    && faction.factionId == factionRuntimeId
                    && entity.getComponent(MarketComponent.class) != null
                    && entity.getComponent(InventoryComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Owned market not found for " + factionContentId + " in " + systemId);
    }

    private static int ownedArchetypeCount(
            WorldSimulation world,
            String factionContentId,
            String archetypeContentId) {
        int factionRuntimeId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        int count = 0;
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                if (faction != null
                        && faction.factionId == factionRuntimeId
                        && archetype != null
                        && archetype.contentId.equals(archetypeContentId)) {
                    count++;
                }
            }
        }
        return count;
    }
}
