package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.world.FactionFiscalPolicyState;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage17FFiscalPolicyPersistenceTest {
    @Test
    void commonFiscalPolicyBoundaryRoundTripsWithoutMovingTreasury() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F2A001L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        String faction = "faction.neutral";
        long treasuryBefore = world.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits();
        FactionFiscalPolicyState policy = new FactionFiscalPolicyState(
                1_200,
                800,
                250_000L,
                75_000L,
                40_000L,
                125_000L);

        assertEquals(policy, world.updateFactionFiscalPolicy(faction, policy));
        assertEquals(treasuryBefore,
                world.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());

        WorldState snapshot = world.snapshot();
        WorldState decoded = WorldStateCodec.decode(WorldStateCodec.encode(snapshot));
        assertEquals(snapshot, decoded);

        WorldSimulation restored = WorldSimulation.restore(
                decoded,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        assertEquals(policy, restored.findFactionFiscalPolicy(faction).orElseThrow());
        assertEquals(treasuryBefore,
                restored.findFactionEconomicState(faction).orElseThrow().treasuryMilliCredits());
    }

    @Test
    void legacyEconomicConstructorKeepsPreFiscalSpendingBehavior() {
        com.spacesim.world.FactionEconomicState legacy = new com.spacesim.world.FactionEconomicState(
                "faction.legacy",
                1_000_000L,
                100_000L,
                50_000L);

        assertEquals(0L, legacy.treasuryReserveFloorMilliCredits());
        assertEquals(Long.MAX_VALUE, legacy.maxConstructionInvestmentPerDecisionMilliCredits());
    }
}
