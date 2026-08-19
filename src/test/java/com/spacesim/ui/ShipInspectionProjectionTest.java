package com.spacesim.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipInspectionProjectionTest {
    @Test
    void selectedShipInspectionReadsAuthoritativeStateWithoutMutatingRuntime() {
        ScaledLiveTacticalSimulationSession session =
                new ScaledLiveTacticalSimulationSession(TacticalScenarioId.MIXED_8V8);
        long entityId = session.snapshot().ships().get(0).entityId();
        var before = session.fingerprint();

        ShipInspectionSnapshot inspection = session.inspectionSnapshot(entityId).orElseThrow();
        var after = session.fingerprint();

        assertEquals(before, after);
        assertEquals(entityId, inspection.entityId());
        assertEquals(session.runtime().battleState().requireCombatant(entityId).spec().side(), inspection.side());
        assertEquals(session.runtime().battleState().requireCombatant(entityId).hull().id(), inspection.hullId());
        assertEquals(session.runtime().battleState().requireCombatant(entityId).doctrine().fitId(), inspection.fitId());
        assertTrue(inspection.meanIntegrity() > 0d);
        assertTrue(inspection.reactionMassKg() >= 0d);
        assertTrue(inspection.ammunitionCount() >= 0L);
        assertTrue(inspection.acceleration().startsWith("N/A"));
        assertTrue(inspection.ecmEccm().startsWith("N/A"));
        assertFalse(inspection.weaponFeeds().isEmpty());
    }

    @Test
    void unknownOrNonPositiveEntityHasNoInspectionCard() {
        ScaledLiveTacticalSimulationSession session =
                new ScaledLiveTacticalSimulationSession(TacticalScenarioId.BALANCED_4V4);

        assertTrue(session.inspectionSnapshot(0L).isEmpty());
        assertTrue(session.inspectionSnapshot(Long.MAX_VALUE).isEmpty());
    }

    @Test
    void inspectionReflectsLiveVelocityAfterAuthoritativeTicks() {
        ScaledLiveTacticalSimulationSession session =
                new ScaledLiveTacticalSimulationSession(TacticalScenarioId.BALANCED_4V4);
        long entityId = session.snapshot().ships().get(0).entityId();

        for (int tick = 0; tick < 20; tick++) {
            session.stepOneTick();
        }

        ShipInspectionSnapshot inspection = session.inspectionSnapshot(entityId).orElseThrow();
        double expectedSpeed = Math.hypot(inspection.velocityXMps(), inspection.velocityYMps());
        assertEquals(expectedSpeed, inspection.speedMps(), 1e-9d);
    }
}
