package com.spacesim.warfare;

import com.spacesim.persistence.Stage19ConflictState.ConflictStatus;
import com.spacesim.persistence.Stage19ConflictStateCodec;
import com.spacesim.warfare.Stage19AggregateWarfareAcceptanceHarness.MobilizationDemand;
import com.spacesim.warfare.StrategicWarPolicyService.Decision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage19AggregateWarfareAcceptanceHarnessTest {
    @Test
    void canonicalChainEscalatesAppliesObservedCoercionAndResolvesExplicitSettlement() {
        Stage19AggregateWarfareAcceptanceHarness harness = new Stage19AggregateWarfareAcceptanceHarness();

        var result = harness.runUninterrupted();

        assertTrue(result.acceptanceSatisfied());
        assertTrue(result.mobilizationDemand().fullyBacked());
        assertEquals(List.of(
                Decision.ESCALATE,
                Decision.OFFER_SETTLEMENT,
                Decision.ACCEPT_SETTLEMENT), result.decisions());
        assertEquals(ConflictStatus.RESOLVED, result.finalConflict().status());
        assertEquals(600_000d,
                result.finalConflict().consequences().observedOpponentUndeliveredCargoKg(), 1e-9d);
    }

    @Test
    void midConflictSaveLoadProducesByteIdenticalFinalWarfareState() {
        Stage19AggregateWarfareAcceptanceHarness harness = new Stage19AggregateWarfareAcceptanceHarness();

        var uninterrupted = harness.runUninterrupted();
        var checkpointed = harness.runWithMidConflictCheckpoint();

        assertEquals(uninterrupted.decisions(), checkpointed.decisions());
        assertEquals(uninterrupted.finalState(), checkpointed.finalState());
        assertArrayEquals(
                Stage19ConflictStateCodec.encode(uninterrupted.finalState()),
                Stage19ConflictStateCodec.encode(checkpointed.finalState()));
    }

    @Test
    void physicalMobilizationShortageIsDetectedWithoutVirtualBacking() {
        MobilizationDemand shortAmmo = new MobilizationDemand(
                20L, 19L,
                20_000d, 50_000d,
                10_000d, 12_000d,
                0, 0);
        MobilizationDemand shortReactionMass = new MobilizationDemand(
                20L, 20L,
                20_000d, 19_999d,
                10_000d, 12_000d,
                0, 0);
        MobilizationDemand shortRepair = new MobilizationDemand(
                20L, 20L,
                20_000d, 50_000d,
                10_000d, 9_999d,
                0, 0);
        MobilizationDemand shortReplacement = new MobilizationDemand(
                20L, 20L,
                20_000d, 50_000d,
                10_000d, 12_000d,
                1, 0);

        assertFalse(shortAmmo.fullyBacked());
        assertFalse(shortReactionMass.fullyBacked());
        assertFalse(shortRepair.fullyBacked());
        assertFalse(shortReplacement.fullyBacked());
    }
}
