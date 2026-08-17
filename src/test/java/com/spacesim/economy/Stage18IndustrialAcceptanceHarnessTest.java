package com.spacesim.economy;

import com.spacesim.persistence.EntityId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage18IndustrialAcceptanceHarnessTest {
    @Test
    void completeHandAuthoredIndustrialUniverseClosesStage18ExitContract() {
        Stage18IndustrialAcceptanceHarness.AcceptanceReport report = Stage18IndustrialAcceptanceHarness.run();

        assertEquals(64, report.contentFingerprint().length());
        assertTrue(report.waterProducedKg() >= 100_000d);
        assertTrue(report.reactorFuelProducedKg() >= 10_000d);
        assertEquals(20_000d, report.reactionMassLoadedKg(), 1e-9d);
        assertTrue(report.heavyComponentsProducedKg() > 0d);
        assertTrue(report.electricalComponentsProducedKg() > 0d);
        assertTrue(report.precisionComponentsProducedKg() > 0d);
        assertTrue(report.strategicSourceMassRemovedKg() > 0d);
        assertTrue(report.ammunitionProducedUnits() >= 10);
        assertEquals(5, report.ammunitionTransferredUnits());
        assertEquals(new EntityId(18_001L), report.builtShipId());
        assertTrue(report.repairInputMassKg() > 0d);
        assertTrue(report.wreckConstructedMassKg() > 0d);
        assertTrue(report.wreckAccessibleMassKg() > 0d);
        assertTrue(report.wreckAccessibleMassKg() < report.wreckConstructedMassKg());
        assertTrue(report.recycledMassKg() > 0d);
        assertTrue(report.recycledMassKg() < report.wreckAccessibleMassKg());
        assertEquals("facility.processing.recycling", report.constructedFacilityDefinitionId());
        assertTrue(report.saveLoadEquivalent());
        assertEquals(64, report.finalIndustrialStateSha256().length());
    }

    @Test
    void completeIndustrialAcceptanceIsDeterministicAcrossIndependentRuns() {
        Stage18IndustrialAcceptanceHarness.AcceptanceReport first = Stage18IndustrialAcceptanceHarness.run();
        Stage18IndustrialAcceptanceHarness.AcceptanceReport second = Stage18IndustrialAcceptanceHarness.run();

        assertEquals(first, second);
    }
}
