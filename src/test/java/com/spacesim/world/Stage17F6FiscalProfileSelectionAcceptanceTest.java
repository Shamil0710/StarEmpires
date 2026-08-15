package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17F6FiscalProfileSelectionAcceptanceTest {
    @Test
    void authoredDoctrineSelectsProfilesThroughOneReadOnlyWorldBoundary() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60021L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        byte[] before = WorldStateCodec.encode(world.snapshot());
        Map<String, FactionFiscalReviewProfile> profiles = new HashMap<>();

        for (ContentCatalog.FactionDefinition faction : content.getFactions()) {
            FactionDoctrineState doctrine = world.findFactionStrategicState(faction.id()).orElseThrow().doctrine();
            long reserveTarget = FactionFiscalPositionAnalyzer.analyze(world, faction.id())
                    .liquidityReserveTargetMilliCredits();
            FactionFiscalReviewProfile expected = FactionFiscalReviewProfileSelector.select(
                    doctrine, reserveTarget);
            FactionFiscalReviewProfile actual = WorldFactionFiscalReviewProfileSelector.select(
                    world, faction.id());

            assertEquals(expected, actual);
            profiles.put(faction.id(), actual);
        }

        assertTrue(profiles.get("faction.trade_league").normalStationTaxTargetBasisPoints()
                < profiles.get("faction.neutral").normalStationTaxTargetBasisPoints());
        assertTrue(profiles.get("faction.miners").liquidityStressEnterBasisPoints()
                < profiles.get("faction.trade_league").liquidityStressEnterBasisPoints());
        assertArrayEquals(before, WorldStateCodec.encode(world.snapshot()),
                "Fiscal profile selection must be byte-for-byte read-only");
    }

    @Test
    void identicalDoctrineProducesIdenticalNonMonetaryResponseShapeAcrossFactionIds() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldSimulation world = WorldSimulation.restore(
                DemoGalaxyFactory.createState(0x17F60022L, content),
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
        String firstFaction = "faction.neutral";
        String secondFaction = "faction.trade_league";
        FactionDoctrineState shared = new FactionDoctrineState(80, 40, 60, 45, 70, 65, 85);
        world.updateFactionDoctrine(firstFaction, shared);
        world.updateFactionDoctrine(secondFaction, shared);
        byte[] beforeSelection = WorldStateCodec.encode(world.snapshot());

        FactionFiscalReviewProfile first = WorldFactionFiscalReviewProfileSelector.select(world, firstFaction);
        FactionFiscalReviewProfile second = WorldFactionFiscalReviewProfileSelector.select(world, secondFaction);

        assertEquals(first.liquidityStressEnterBasisPoints(), second.liquidityStressEnterBasisPoints());
        assertEquals(first.liquidityStressExitBasisPoints(), second.liquidityStressExitBasisPoints());
        assertEquals(first.normalStationTaxTargetBasisPoints(), second.normalStationTaxTargetBasisPoints());
        assertEquals(first.stressStationTaxTargetBasisPoints(), second.stressStationTaxTargetBasisPoints());
        assertEquals(first.maxStationTaxStepBasisPoints(), second.maxStationTaxStepBasisPoints());
        assertArrayEquals(beforeSelection, WorldStateCodec.encode(world.snapshot()),
                "Stable faction identity must not introduce hidden selector behavior");
    }
}
