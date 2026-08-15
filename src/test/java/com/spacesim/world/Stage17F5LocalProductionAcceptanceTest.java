package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.InventoryComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.components.WalletComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F5LocalProductionAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";
    private static final String NEUTRAL = "faction.neutral";
    private static final String WEAPONS = "item.weapons";
    private static final String ARSENAL = "station.arsenal";
    private static final String WEAPONS_RECIPE = "recipe.weapons_assembly";
    private static final String FOOD_RECIPE = "recipe.food_growing";

    @Test
    void resilienceUsesOnlyOwnedCanonicalCapacityAndOrdinaryPolicyApply() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50031L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        Entity arsenal = ownedArchetype(world, TRADE_LEAGUE, ARSENAL);
        ProductionComponent production = arsenal.getComponent(ProductionComponent.class);
        InventoryComponent inventory = arsenal.getComponent(InventoryComponent.class);
        WalletComponent wallet = arsenal.getComponent(WalletComponent.class);

        FactionStockProductionPolicyState deliberatelyRetooled = withProductionPolicy(
                world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow(),
                new FactionProductionPolicyState(ARSENAL, FOOD_RECIPE));
        world.updateFactionStockProductionPolicy(TRADE_LEAGUE, deliberatelyRetooled);
        world.applyFactionStrategicPolicy(TRADE_LEAGUE);
        assertEquals("Выращивание продовольствия", production.getActiveRecipe().name);

        int stockBefore = inventory.getTotalStock();
        long walletBefore = wallet.getBalanceMilliCredits();
        long treasuryBefore = world.findFactionEconomicState(TRADE_LEAGUE)
                .orElseThrow().treasuryMilliCredits();
        FactionResiliencePlan resilience = resiliencePlan(world, TRADE_LEAGUE, WEAPONS, true);

        FactionLocalProductionPlan plan = FactionLocalProductionPlanner.analyze(world, resilience);

        assertEquals(1, plan.recommendations().size());
        FactionLocalProductionRecommendation recommendation = plan.recommendations().get(0);
        assertEquals(WEAPONS, recommendation.itemContentId());
        assertEquals(ARSENAL, recommendation.stationArchetypeContentId());
        assertEquals(WEAPONS_RECIPE, recommendation.recipeContentId());
        assertEquals(1, recommendation.outputUnitsPerCycle());
        assertEquals(6f, recommendation.durationSeconds());
        assertTrue(plan.capacityGapItemContentIds().isEmpty());

        FactionStockProductionPolicyState merged = plan.mergeRecommendedProduction(
                world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow());
        world.updateFactionStockProductionPolicy(TRADE_LEAGUE, merged);

        assertEquals("Выращивание продовольствия", production.getActiveRecipe().name,
                "Authoring must not retool physical production immediately");
        assertEquals(stockBefore, inventory.getTotalStock());
        assertEquals(walletBefore, wallet.getBalanceMilliCredits());
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits());

        FactionStrategicPolicyEngine.ApplicationReport report = world.applyFactionStrategicPolicy(TRADE_LEAGUE);

        assertTrue(report.productionStationsRetooled() > 0);
        assertEquals("Сборка вооружения", production.getActiveRecipe().name);
        assertEquals(0f, production.progressSeconds);
        assertEquals(stockBefore, inventory.getTotalStock(),
                "Retooling policy must not create production output");
        assertEquals(walletBefore, wallet.getBalanceMilliCredits(),
                "Retooling policy must not create or spend station money");
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(TRADE_LEAGUE).orElseThrow().treasuryMilliCredits(),
                "Retooling policy must not create or spend treasury money");
    }

    @Test
    void missingOwnedCanonicalCapacityStaysExplicitGapWithoutInventedRecipeOrAsset() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50032L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        FactionStockProductionPolicyState before =
                world.findFactionStockProductionPolicy(NEUTRAL).orElseThrow();
        FactionResiliencePlan resilience = resiliencePlan(world, NEUTRAL, WEAPONS, true);

        FactionLocalProductionPlan plan = FactionLocalProductionPlanner.analyze(world, resilience);

        assertTrue(plan.recommendations().isEmpty());
        assertEquals(List.of(WEAPONS), plan.capacityGapItemContentIds());
        assertEquals(before, plan.mergeRecommendedProduction(before));
        assertFalse(hasOwnedArchetype(world, NEUTRAL, ARSENAL));
    }

    @Test
    void itemWithoutLocalProductionIntentCreatesNeitherRetoolNorCapacityGap() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F50033L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        FactionLocalProductionPlan plan = FactionLocalProductionPlanner.analyze(
                world,
                resiliencePlan(world, TRADE_LEAGUE, WEAPONS, false));

        assertTrue(plan.recommendations().isEmpty());
        assertTrue(plan.capacityGapItemContentIds().isEmpty());
    }

    private static FactionResiliencePlan resiliencePlan(
            WorldSimulation world,
            String factionContentId,
            String itemContentId,
            boolean localProductionRecommended) {
        FactionResilienceItemDecision decision = new FactionResilienceItemDecision(
                itemContentId,
                100L,
                9_000,
                80L,
                400_000L,
                false,
                5_000,
                180,
                true,
                localProductionRecommended,
                false);
        return new FactionResiliencePlan(
                factionContentId,
                world.getAuthoritativeWorldTick(),
                80,
                1,
                List.of(decision));
    }

    private static FactionStockProductionPolicyState withProductionPolicy(
            FactionStockProductionPolicyState current,
            FactionProductionPolicyState replacement) {
        List<FactionProductionPolicyState> production = new ArrayList<>();
        for (FactionProductionPolicyState policy : current.productionPolicies()) {
            if (!policy.stationArchetypeContentId().equals(replacement.stationArchetypeContentId())) {
                production.add(policy);
            }
        }
        production.add(replacement);
        return new FactionStockProductionPolicyState(current.stockPolicies(), production);
    }

    private static Entity ownedArchetype(
            WorldSimulation world,
            String factionContentId,
            String archetypeContentId) {
        int factionRuntimeId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                if (faction != null
                        && faction.factionId == factionRuntimeId
                        && archetype != null
                        && archetype.contentId.equals(archetypeContentId)) {
                    return entity;
                }
            }
        }
        throw new AssertionError("Owned archetype not found: " + archetypeContentId);
    }

    private static boolean hasOwnedArchetype(
            WorldSimulation world,
            String factionContentId,
            String archetypeContentId) {
        int factionRuntimeId = world.findFactionRuntimeId(factionContentId).orElseThrow();
        for (StarSystemNode system : world.getTopology().systems()) {
            for (Entity entity : world.findSession(system.id()).orElseThrow().getEngine().getEntities()) {
                FactionComponent faction = entity.getComponent(FactionComponent.class);
                ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
                if (faction != null
                        && faction.factionId == factionRuntimeId
                        && archetype != null
                        && archetype.contentId.equals(archetypeContentId)) {
                    return true;
                }
            }
        }
        return false;
    }
}
