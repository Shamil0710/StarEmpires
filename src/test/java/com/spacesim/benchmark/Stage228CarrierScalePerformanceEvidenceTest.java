package com.spacesim.benchmark;

import com.spacesim.benchmark.Stage228CarrierScalePerformanceEvidence.ScenarioSize;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage228CarrierScalePerformanceEvidenceTest {

    @Test
    void smallMediumAndDenseBudgetsGrowPersistentPopulationWhileLocalExactWorkStaysBounded() {
        var evidence = Stage228CarrierScalePerformanceEvidence.deriveCurrent();
        assertEquals(3, evidence.scenarios().size());
        assertTrue(evidence.empireCraftPerCarrier() > 0);
        assertTrue(evidence.unionCraftPerCarrier() > 0);

        EnumMap<ScenarioSize, Stage228CarrierScalePerformanceEvidence.ScenarioBudget> rows =
                new EnumMap<>(ScenarioSize.class);
        evidence.scenarios().forEach(row -> rows.put(row.size(), row));

        var small = rows.get(ScenarioSize.SMALL);
        var medium = rows.get(ScenarioSize.MEDIUM);
        var dense = rows.get(ScenarioSize.DENSE);

        assertTrue(small.persistentCraftRows() < medium.persistentCraftRows());
        assertTrue(medium.persistentCraftRows() < dense.persistentCraftRows());

        assertEquals(small.persistentCraftRows(), small.exactLocalCraftUpperBound(),
                "small probe intentionally allows the whole tiny wing to be local");
        assertTrue(medium.exactLocalCraftUpperBound() < medium.persistentCraftRows());
        assertTrue(dense.exactLocalCraftUpperBound() < dense.persistentCraftRows());
        assertTrue(dense.dormantCraftLowerBound() > medium.dormantCraftLowerBound());

        assertTrue(medium.exactMaterializationFraction() < small.exactMaterializationFraction());
        assertTrue(dense.exactMaterializationFraction() <= medium.exactMaterializationFraction());

        evidence.scenarios().forEach(row -> {
            assertEquals(row.physicalBayCount(), row.activeDeckOperationUpperBound());
            assertEquals(row.persistentCraftRows(), row.queuedDeckRequestUpperBound());
            assertTrue(row.activeDeckOperationUpperBound() <= row.queuedDeckRequestUpperBound());
            assertTrue(row.exactLocalCraftUpperBound() <= row.persistentCraftRows());
        });
    }

    @Test
    void evidenceKeepsAcceptedAuthoritiesAndExplicitDormantBoundaryVisible() {
        var evidence = Stage228CarrierScalePerformanceEvidence.deriveCurrent();

        assertTrue(evidence.persistenceAuthority().contains("SmallCraftRegistry"));
        assertTrue(evidence.deckSchedulingAuthority().contains("SmallCraftFlightDeckOperations"));
        assertTrue(evidence.localExactAuthority().contains("Stage19ExactTacticalEncounterResolver"));
        assertTrue(evidence.dormantPolicy().contains("dormant"));
        assertTrue(evidence.dormantPolicy().contains("not exact-materialized"));
    }
}
