package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.Stage21EPhysicalConsequenceService.ConsequenceReport;
import com.spacesim.world.Stage21EPhysicalConsequenceService.FleetConsequence;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class Stage21EPhysicalConsequenceServiceTest {
    @Test
    void reportedLossRequiresOrdinaryFleetToDisappear() {
        FleetId fleetId = new FleetId(10L);
        FleetForceRegistry before = registry(fleetId, readiness(9_000, 8_000, 7_000, 9_500));
        FleetForceRegistry after = new FleetForceRegistry(List.of());

        ConsequenceReport report = new Stage21EPhysicalConsequenceService()
                .reconcile(operation(fleetId), before, after);
        FleetConsequence row = report.fleets().get(0);

        assertTrue(row.destroyed());
        assertEquals(List.of(fleetId), report.losses());
        assertEquals(List.of(), report.survivors());
        assertEquals(-9_000, row.structuralDeltaBps());
        assertEquals(-8_000, row.ammunitionDeltaBps());
        assertEquals(-7_000, row.propellantDeltaBps());
        assertEquals(-9_500, row.crewDeltaBps());
    }

    @Test
    void survivingFleetReportsOnlyPhysicalBeforeAfterReadinessChanges() {
        FleetId fleetId = new FleetId(10L);
        FleetForceRegistry before = registry(fleetId, readiness(9_000, 8_000, 7_000, 9_500));
        FleetForceRegistry after = registry(fleetId, readiness(7_500, 5_000, 6_500, 8_000));

        FleetConsequence row = new Stage21EPhysicalConsequenceService()
                .reconcile(operation(fleetId), before, after).fleets().get(0);

        assertFalse(row.destroyed());
        assertEquals(-1_500, row.structuralDeltaBps());
        assertEquals(-3_000, row.ammunitionDeltaBps());
        assertEquals(-500, row.propellantDeltaBps());
        assertEquals(-1_500, row.crewDeltaBps());
    }

    private static FleetForceRegistry registry(FleetId fleetId, FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(1_000L + fleetId.value()),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(1),
                null, null, null, null, null, null, null, null, null);
        FleetForceRegistry.Entry entry = new FleetForceRegistry.Entry(
                fleetId, 1, FleetLocationKind.IN_SYSTEM, new StarSystemId(1L),
                null, null, entity, readiness);
        return new FleetForceRegistry(List.of(entry));
    }

    private static FleetReadinessState readiness(int structure, int ammo, int propellant, int crew) {
        return new FleetReadinessState(structure, ammo, propellant, crew, 10_000, 10_000, 10_000);
    }

    private static OperationState operation(FleetId fleetId) {
        return new OperationState(
                1L, OperationType.RAID, 1L, 1L, 1, List.of(fleetId),
                new StarSystemId(1L), new StarSystemId(1L), "system:1",
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(2_000, 1_000, 20L),
                new WithdrawalPolicy(new StarSystemId(1L), 1_500, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null);
    }
}
