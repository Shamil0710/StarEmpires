package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.model.ItemCategory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConstructionDurationPolicyTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();

    @Test
    void defaultMiningBaseDurationCombinesSetupAndWeightedPhysicalBill() {
        ContentCatalog.StationArchetypeDefinition station = CONTENT.findStationArchetype("station.mining_base");
        ConstructionDurationPolicy.Estimate estimate = ConstructionDurationPolicy.estimate(CONTENT, station);

        assertEquals(180L, estimate.totalMaterialUnits());
        assertEquals(153d, estimate.materialWorkUnits(), 0.000001d);
        assertEquals(25d, estimate.baseSetupSeconds(), 0.000001d);
        assertEquals(12.75d, estimate.materialAssemblySeconds(), 0.000001d);
        assertEquals(37.75d, estimate.totalSeconds(), 0.000001d);
        assertTrue(estimate.totalSeconds() > station.construction().buildSeconds());
    }

    @Test
    void moreRequiredPhysicalMaterialTakesLongerWithSameSetupComplexity() {
        ContentCatalog.StationArchetypeDefinition light = station("test.light", 25f, Map.of("item.steel", 100));
        ContentCatalog.StationArchetypeDefinition heavy = station("test.heavy", 25f, Map.of("item.steel", 300));

        ConstructionDurationPolicy.Estimate lightEstimate = ConstructionDurationPolicy.estimate(CONTENT, light);
        ConstructionDurationPolicy.Estimate heavyEstimate = ConstructionDurationPolicy.estimate(CONTENT, heavy);

        assertTrue(heavyEstimate.materialWorkUnits() > lightEstimate.materialWorkUnits());
        assertTrue(heavyEstimate.totalSeconds() > lightEstimate.totalSeconds());
    }

    @Test
    void handlingWeightsExpressCurrentCategoryWorkInsteadOfPretendingToBeKilograms() {
        assertEquals(0.55d, ConstructionDurationPolicy.handlingWeight(ItemCategory.GAS_LIQUID), 0d);
        assertEquals(1d, ConstructionDurationPolicy.handlingWeight(ItemCategory.MATERIAL), 0d);
        assertEquals(1.60d, ConstructionDurationPolicy.handlingWeight(ItemCategory.FINISHED_GOODS), 0d);
        assertTrue(ConstructionDurationPolicy.handlingWeight(ItemCategory.FINISHED_GOODS)
                > ConstructionDurationPolicy.handlingWeight(ItemCategory.MATERIAL));
    }

    @Test
    void largerMoreComplexExistingStationReceivesLongerCalculatedDuration() {
        ConstructionDurationPolicy.Estimate mining = ConstructionDurationPolicy.estimate(
                CONTENT, CONTENT.findStationArchetype("station.mining_base"));
        ConstructionDurationPolicy.Estimate arsenal = ConstructionDurationPolicy.estimate(
                CONTENT, CONTENT.findStationArchetype("station.arsenal"));
        ConstructionDurationPolicy.Estimate colony = ConstructionDurationPolicy.estimate(
                CONTENT, CONTENT.findStationArchetype("station.colony"));

        assertTrue(arsenal.totalSeconds() > mining.totalSeconds());
        assertTrue(colony.totalSeconds() > arsenal.totalSeconds());
    }

    private static ContentCatalog.StationArchetypeDefinition station(
            String id,
            float baseSetupSeconds,
            Map<String, Integer> materials) {
        return new ContentCatalog.StationArchetypeDefinition(
                id,
                id,
                1_000,
                1_000d,
                "faction.neutral",
                null,
                List.of(),
                new ContentCatalog.ConstructionDefinition(1_000d, baseSetupSeconds, materials));
    }
}
