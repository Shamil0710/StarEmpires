package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.MarketComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionStrategicPolicyEngineTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void strategicPoliciesПереживаютCodecИФизическиМеняютMarketИProduction() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState base = DemoGalaxyFactory.createState(0xFA710101L, content);
        FactionStrategicState original = base.factionStrategies().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst().orElseThrow();
        FactionStrategicState policy = new FactionStrategicState(
                original.factionContentId(),
                original.minimumMarketAccessRelation(),
                original.relations(),
                original.controlledSystems(),
                List.of(new FactionStockPolicyState("item.energy", 500)),
                List.of(new FactionProductionPolicyState(
                        "station.arsenal", "recipe.weapons_assembly")),
                List.of(
                        new FactionStrategicGoalState(
                                "goal.rearm",
                                FactionStrategicGoalState.GoalType.MILITARY,
                                List.of(new FactionStockPolicyState("item.weapons", 600))),
                        new FactionStrategicGoalState(
                                "goal.expand",
                                FactionStrategicGoalState.GoalType.EXPANSION,
                                List.of(new FactionStockPolicyState("item.food", 700)))));
        WorldState configured = replaceStrategy(base, policy);

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(configured));
        assertEquals(configured, decoded);
        assertEquals(policy, decoded.factionStrategies().stream()
                .filter(state -> state.factionContentId().equals(TRADE_LEAGUE))
                .findFirst().orElseThrow());

        WorldSimulation world = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                10,
                8);
        world.advanceFrame(0.1f);
        Entity arsenal = ownedArchetype(world, content, "station.arsenal");
        Entity agrodome = ownedArchetype(world, content, "station.agrodome");
        int energyId = content.findItem("item.energy").runtimeId();
        int weaponsId = content.findItem("item.weapons").runtimeId();
        int foodId = content.findItem("item.food").runtimeId();
        float weaponsPriceBefore = arsenal.getComponent(MarketComponent.class).buyPrices[weaponsId];

        ProductionComponent production = arsenal.getComponent(ProductionComponent.class);
        production.recipes.clear();
        production.recipes.add(content.createRuntimeRecipe("recipe.food_growing"));
        production.activeRecipeIndex = 0;
        production.progressSeconds = 2f;

        FactionStrategicPolicyEngine.ApplicationReport report =
                FactionStrategicPolicyEngine.apply(world, content, TRADE_LEAGUE);

        assertTrue(report.marketsAdjusted() >= 2);
        assertEquals(1, report.productionStationsRetooled());
        assertEquals(2, report.activeStrategicGoals());
        assertEquals(500, arsenal.getComponent(MarketComponent.class).targetStock[energyId]);
        assertEquals(600, arsenal.getComponent(MarketComponent.class).targetStock[weaponsId]);
        assertEquals(500, agrodome.getComponent(MarketComponent.class).targetStock[energyId]);
        assertEquals(700, agrodome.getComponent(MarketComponent.class).targetStock[foodId]);
        assertEquals("Сборка вооружения", production.getActiveRecipe().name);
        assertEquals(0f, production.progressSeconds);

        world.advanceFrame(0.1f);
        assertTrue(arsenal.getComponent(MarketComponent.class).buyPrices[weaponsId] > weaponsPriceBefore);

        FactionStrategicPolicyEngine.ApplicationReport repeated =
                FactionStrategicPolicyEngine.apply(world, content, TRADE_LEAGUE);
        assertEquals(0, repeated.marketsAdjusted());
        assertEquals(0, repeated.productionStationsRetooled());
        assertEquals(2, repeated.activeStrategicGoals());
    }

    private static WorldState replaceStrategy(WorldState base, FactionStrategicState replacement) {
        List<FactionStrategicState> strategies = new ArrayList<>();
        for (FactionStrategicState strategy : base.factionStrategies()) {
            strategies.add(strategy.factionContentId().equals(replacement.factionContentId())
                    ? replacement
                    : strategy);
        }
        return new WorldState(
                WorldState.CURRENT_VERSION,
                base.topology(),
                base.systems(),
                base.factions(),
                strategies);
    }

    private static Entity ownedArchetype(
            WorldSimulation world,
            ContentCatalog content,
            String archetypeId) {
        int factionId = content.findFaction(TRADE_LEAGUE).runtimeId();
        for (Entity entity : world.findSession(DemoGalaxyFactory.ACTIVE_SYSTEM_ID).orElseThrow()
                .getEngine().getEntities()) {
            FactionComponent faction = entity.getComponent(FactionComponent.class);
            ArchetypeComponent archetype = entity.getComponent(ArchetypeComponent.class);
            if (faction != null
                    && faction.factionId == factionId
                    && archetype != null
                    && archetype.contentId.equals(archetypeId)) {
                return entity;
            }
        }
        throw new AssertionError("Не найден archetype: " + archetypeId);
    }
}
