package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6PolicyReviewLifecycleAcceptanceTest {
    @Test
    void oneReviewWindowIsClaimedOnceAndSurvivesSaveLoadWithoutEconomicMutation() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        String faction = "faction.neutral";
        FactionPolicyReviewCadence cadence = new FactionPolicyReviewCadence(100L, 0L);
        long tick = world.getAuthoritativeWorldTick();
        long treasuryBefore = world.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
        FactionFiscalPolicyState fiscalBefore = world.findFactionFiscalPolicy(faction).orElseThrow();
        FactionStockProductionPolicyState stockBefore = world.findFactionStockProductionPolicy(faction).orElseThrow();
        FactionDoctrineState doctrineBefore = world.findFactionStrategicState(faction).orElseThrow().doctrine();

        assertEquals(FactionPolicyReviewState.INITIAL,
                world.findFactionPolicyReviewState(faction).orElseThrow());
        assertTrue(cadence.isDue(FactionPolicyReviewState.INITIAL, tick));
        assertTrue(world.tryBeginFactionPolicyReview(faction, cadence));
        assertEquals(new FactionPolicyReviewState(tick),
                world.findFactionPolicyReviewState(faction).orElseThrow());
        assertFalse(world.tryBeginFactionPolicyReview(faction, cadence));

        assertEquals(treasuryBefore, world.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
        assertEquals(fiscalBefore, world.findFactionFiscalPolicy(faction).orElseThrow());
        assertEquals(stockBefore, world.findFactionStockProductionPolicy(faction).orElseThrow());
        assertEquals(doctrineBefore, world.findFactionStrategicState(faction).orElseThrow().doctrine());

        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(world.snapshot()));
        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);

        assertEquals(tick, restored.getAuthoritativeWorldTick());
        assertEquals(new FactionPolicyReviewState(tick),
                restored.findFactionPolicyReviewState(faction).orElseThrow());
        assertFalse(restored.tryBeginFactionPolicyReview(faction, cadence),
                "Reloading the same authoritative tick must not grant a second bounded policy step");
        assertEquals(treasuryBefore,
                restored.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
        assertEquals(fiscalBefore, restored.findFactionFiscalPolicy(faction).orElseThrow());
        assertEquals(stockBefore, restored.findFactionStockProductionPolicy(faction).orElseThrow());
        assertEquals(doctrineBefore, restored.findFactionStrategicState(faction).orElseThrow().doctrine());
    }
}
