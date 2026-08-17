package com.spacesim.ship;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IFleetSaturationHarnessTest {
    private static final Stage175IFleetSaturationHarness HARNESS = new Stage175IFleetSaturationHarness();

    @Test
    void nominalWaveMaterializesOnePhysicalMissileAndConsumesOneRoundPerAttackingShip() {
        var result = HARNESS.run(Stage175IFleetSaturationHarness.Scenario.nominal());

        assertEquals(4, result.missilesLaunched());
        assertEquals(96L, result.attackingMissileRoundsBefore());
        assertEquals(92L, result.attackingMissileRoundsAfter());
        assertEquals(8_000d, result.launchedMissileMassKg(), 1e-9d);
        assertTrue(result.interceptorAssignments() > 0);
        assertEquals(64, result.fingerprint().length());
    }

    @Test
    void changingAttackingShipCountChangesActualBodiesAmmoAndFleetMass() {
        var four = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                4, 0, 12_000d, 1d, 0, 0L, true));
        var eight = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                8, 0, 12_000d, 1d, 0, 0L, true));

        assertEquals(4, four.missilesLaunched());
        assertEquals(8, eight.missilesLaunched());
        assertEquals(four.attackingMissileRoundsBefore() * 2L, eight.attackingMissileRoundsBefore());
        assertEquals(four.attackingFleetMassKg() * 2d, eight.attackingFleetMassKg(), 1e-6d);
        assertNotEquals(four.fingerprint(), eight.fingerprint());
    }

    @Test
    void finiteInterceptorRoundsAndChannelsBoundAssignmentsWithoutPdProbability() {
        var one = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                8, 1, 12_000d, 1d, 1, 1L, true));
        var four = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                8, 4, 12_000d, 1d, 2, 2L, true));

        assertTrue(one.interceptorAssignments() <= 1);
        assertTrue(four.interceptorAssignments() <= 8);
        assertTrue(four.interceptorAssignments() > one.interceptorAssignments());
        assertEquals(one.missilesLaunched() - one.interceptorAssignments(), one.unassignedThreats());
        assertEquals(four.missilesLaunched() - four.interceptorAssignments(), four.unassignedThreats());
    }

    @Test
    void thermalLockoutPreventsAssignmentsWithoutDeletingIncomingBodies() {
        var available = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 12_000d, 1d, 2, 4L, true));
        var hot = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 12_000d, 1d, 2, 4L, false));

        assertEquals(6, available.missilesLaunched());
        assertEquals(6, hot.missilesLaunched());
        assertTrue(available.interceptorAssignments() > 0);
        assertEquals(0, hot.interceptorAssignments());
        assertEquals(6, hot.unassignedThreats());
        assertNotEquals(available.fingerprint(), hot.fingerprint());
    }

    @Test
    void formationSpacingChangesPhysicalThreatAndStationGeometryDeterministically() {
        var compact = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 6_000d, 1d, 2, 4L, true));
        var dispersed = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 40_000d, 1d, 2, 4L, true));
        var dispersedAgain = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 40_000d, 1d, 2, 4L, true));

        assertNotEquals(compact.fingerprint(), dispersed.fingerprint());
        assertEquals(dispersed, dispersedAgain);
    }

    @Test
    void emptyPhysicalMissileStoresProduceNoBodiesAndNoAssignments() {
        var empty = HARNESS.run(new Stage175IFleetSaturationHarness.Scenario(
                6, 3, 12_000d, 0d, 2, 4L, true));

        assertEquals(0L, empty.attackingMissileRoundsBefore());
        assertEquals(0L, empty.attackingMissileRoundsAfter());
        assertEquals(0, empty.missilesLaunched());
        assertEquals(0d, empty.launchedMissileMassKg(), 0d);
        assertEquals(0, empty.interceptorAssignments());
        assertEquals(0, empty.unassignedThreats());
    }
}
