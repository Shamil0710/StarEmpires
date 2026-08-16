package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.DemoGalaxyFactory;
import com.spacesim.components.ArchetypeComponent;
import com.spacesim.components.FactionComponent;
import com.spacesim.components.ProductionComponent;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6ProductionPolicyIsolationAcceptanceTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void autonomousPolicyReviewsDoNotAuthorOrApplyRecipeSwitches() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60041L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        Entity arsenal = ownedArchetype(world, TRADE_LEAGUE, "station.arsenal");
        ProductionComponent production = arsenal.getComponent(ProductionComponent.class);
        String physicalRecipeBeforeAuthoring = production.getActiveRecipe().name;
        String requestedRecipeName = content.createRuntimeRecipe("recipe.food_growing").name;
        assertNotEquals(requestedRecipeName, physicalRecipeBeforeAuthoring,
                "Fixture must request a recipe different from the station's live configuration");

        FactionStockProductionPolicyState requested = new FactionStockProductionPolicyState(
                List.of(),
                List.of(new FactionProductionPolicyState("station.arsenal", "recipe.food_growing")));
        assertEquals(requested, world.updateFactionStockProductionPolicy(TRADE_LEAGUE, requested));
        assertEquals(physicalRecipeBeforeAuthoring, production.getActiveRecipe().name,
                "Authoring a production preference must remain physically inert until explicit apply");

        FactionPolicyReviewCadence cadence = FactionPolicyReviewCadence.defaultForFaction(TRADE_LEAGUE);
        advanceToTick(world, cadence.firstReviewOffsetTicks());
        long firstReviewTick = world.getAuthoritativeWorldTick();
        assertReviewDoesNotRetool(world, production, requested);

        advanceToTick(world, firstReviewTick + cadence.intervalTicks());
        assertReviewDoesNotRetool(world, production, requested);

        assertNotEquals(requestedRecipeName, production.getActiveRecipe().name,
                "Repeated autonomous reviews must not become an implicit recipe actuator");

        FactionStrategicPolicyEngine.ApplicationReport explicitApply =
                world.applyFactionStrategicPolicy(TRADE_LEAGUE);
        assertTrue(explicitApply.productionStationsRetooled() > 0);
        assertEquals(requestedRecipeName, production.getActiveRecipe().name,
                "Only the ordinary explicit strategic-policy apply may materialize the authored recipe preference");
        assertEquals(0f, production.progressSeconds,
                "A real explicit retool resets production progress exactly once at the executor boundary");
    }

    private static void assertReviewDoesNotRetool(
            WorldSimulation world,
            ProductionComponent production,
            FactionStockProductionPolicyState expectedPolicy) {
        String recipeBeforeReview = production.getActiveRecipe().name;
        int recipeIndexBeforeReview = production.activeRecipeIndex;
        float progressBeforeReview = production.progressSeconds;

        FactionPolicyReviewCoordinator.Report report = FactionPolicyReviewCoordinator.reviewPolicies(
                world, List.of(TRADE_LEAGUE));

        assertEquals(1L, report.claimedReviewCount(), "Each sampled window must actually be due and claimed");
        assertEquals(expectedPolicy, world.findFactionStockProductionPolicy(TRADE_LEAGUE).orElseThrow(),
                "Fiscal/resilience review must preserve separately authored production preferences");
        assertEquals(recipeBeforeReview, production.getActiveRecipe().name,
                "Common policy review must not retool a physical production station");
        assertEquals(recipeIndexBeforeReview, production.activeRecipeIndex,
                "Common policy review must not change the active recipe index");
        assertEquals(progressBeforeReview, production.progressSeconds,
                "Common policy review must not reset or advance production progress");
    }

    private static Entity ownedArchetype(
            WorldSimulation world,
            String factionContentId,
            String archetypeId) {
        int factionId = world.findFactionRuntimeId(factionContentId).orElseThrow();
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
        throw new AssertionError("Archetype not found: " + archetypeId);
    }

    private static void advanceToTick(WorldSimulation world, long targetTick) {
        float fixedStep = world.findSession(world.getActiveSystemId()).orElseThrow()
                .getClock().getFixedStepSeconds();
        int guard = 0;
        while (world.getAuthoritativeWorldTick() < targetTick && guard++ < 40_000) {
            world.advanceFrame(fixedStep);
        }
        assertTrue(world.getAuthoritativeWorldTick() >= targetTick, "World did not reach requested review tick");
    }
}
