package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.persistence.WorldStateCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FResiliencePlannerTest {
    private static final String TRADE_LEAGUE = "faction.trade_league";

    @Test
    void resilienceAnalysisIsReadOnlyAndHigherDoctrinePriorityNeverRecommendsAWeakerBuffer() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState initial = DemoGalaxyFactory.createState(0x17F50001L, content);
        WorldSimulation low = restore(initial, content);
        WorldSimulation high = restore(initial, content);
        FactionDoctrineState base = low.findFactionStrategicState(TRADE_LEAGUE).orElseThrow().doctrine();
        low.updateFactionDoctrine(TRADE_LEAGUE, withResilience(base, 0));
        high.updateFactionDoctrine(TRADE_LEAGUE, withResilience(base, 100));

        byte[] lowBefore = WorldStateCodec.encode(low.snapshot());
        byte[] highBefore = WorldStateCodec.encode(high.snapshot());
        FactionResiliencePlan lowPlan = FactionResiliencePlanner.analyze(low, TRADE_LEAGUE);
        FactionResiliencePlan highPlan = FactionResiliencePlanner.analyze(high, TRADE_LEAGUE);

        assertArrayEquals(lowBefore, WorldStateCodec.encode(low.snapshot()),
                "Resilience analysis must not mutate the low-priority world");
        assertArrayEquals(highBefore, WorldStateCodec.encode(high.snapshot()),
                "Resilience analysis must not mutate the high-priority world");
        assertFalse(lowPlan.items().isEmpty());
        assertEquals(lowPlan.items().size(), highPlan.items().size());
        assertEquals(0, lowPlan.economicResiliencePriority());
        assertEquals(100, highPlan.economicResiliencePriority());

        Map<String, FactionResilienceItemDecision> lowByItem = lowPlan.items().stream()
                .collect(Collectors.toMap(FactionResilienceItemDecision::itemContentId, Function.identity()));
        boolean observedStricterPreference = false;
        for (FactionResilienceItemDecision highItem : highPlan.items()) {
            FactionResilienceItemDecision lowItem = lowByItem.get(highItem.itemContentId());
            assertTrue(highItem.preferredMaximumPartnerShareBasisPoints()
                    <= lowItem.preferredMaximumPartnerShareBasisPoints());
            assertTrue(highItem.recommendedTargetFloorPerMarketUnits()
                    >= lowItem.recommendedTargetFloorPerMarketUnits());
            if (highItem.preferredMaximumPartnerShareBasisPoints()
                    < lowItem.preferredMaximumPartnerShareBasisPoints()
                    || highItem.recommendedTargetFloorPerMarketUnits()
                    > lowItem.recommendedTargetFloorPerMarketUnits()
                    || highItem.diversifySuppliersRecommended()
                    || highItem.localProductionRecommended()
                    || highItem.routeRedundancyRecommended()) {
                observedStricterPreference = true;
            }
        }
        assertTrue(observedStricterPreference,
                "Higher resilience doctrine must make at least one measured recommendation stricter");
    }

    @Test
    void bufferRecommendationMergesThroughExistingStockPolicyWithoutReplacingProductionChoice() {
        FactionProductionPolicyState production =
                new FactionProductionPolicyState("station.arsenal", "recipe.weapons_assembly");
        FactionStockProductionPolicyState current = new FactionStockProductionPolicyState(
                List.of(new FactionStockPolicyState("item.energy", 500)),
                List.of(production));
        FactionResiliencePlan plan = new FactionResiliencePlan(
                TRADE_LEAGUE,
                100L,
                80,
                2,
                List.of(
                        decision("item.energy", 900),
                        decision("item.food", 700)));

        FactionStockProductionPolicyState merged = plan.mergeRecommendedStockFloors(current);

        assertEquals(2, merged.stockPolicies().size());
        assertEquals(900, merged.stockPolicies().stream()
                .filter(policy -> policy.itemContentId().equals("item.energy"))
                .findFirst().orElseThrow().targetStockFloor());
        assertEquals(700, merged.stockPolicies().stream()
                .filter(policy -> policy.itemContentId().equals("item.food"))
                .findFirst().orElseThrow().targetStockFloor());
        assertEquals(List.of(production), merged.productionPolicies());
        assertEquals(500, current.stockPolicies().get(0).targetStockFloor(),
                "Planning must not mutate the existing persistent policy value");
    }

    private static FactionResilienceItemDecision decision(String itemContentId, int floor) {
        return new FactionResilienceItemDecision(
                itemContentId,
                1_000L,
                8_000,
                400L,
                50_000L,
                true,
                6_000,
                floor,
                true,
                true,
                true);
    }

    private static FactionDoctrineState withResilience(FactionDoctrineState base, int resilience) {
        return new FactionDoctrineState(
                base.tradeOpenness(),
                base.securityPosture(),
                base.expansionPreference(),
                base.sovereigntySensitivity(),
                base.treatyLegalism(),
                base.interventionism(),
                resilience);
    }

    private static WorldSimulation restore(WorldState state, ContentCatalog content) {
        return WorldSimulation.restore(
                state,
                content,
                DemoGalaxyFactory.ACTIVE_SYSTEM_ID,
                WorldSimulation.DEFAULT_STRATEGIC_STEP_TICKS,
                WorldSimulation.DEFAULT_REMOTE_UPDATE_BUDGET_PER_FRAME);
    }
}
