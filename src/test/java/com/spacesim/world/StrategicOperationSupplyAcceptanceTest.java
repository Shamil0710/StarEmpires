package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.StrategicOperationService.SupplyDecision;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class StrategicOperationSupplyAcceptanceTest {
    @Test
    void unsuppliedOtherwiseReadyForceWithdrawsWithoutReceivingResources() {
        FleetId fleetId = new FleetId(10L);
        OperationState operation = operation(fleetId, new SupplyPolicy(2_000, 5_000, 0L));
        StrategicOperationState state = new StrategicOperationState(2L, List.of(operation));
        FleetForceRegistry forces = registry(
                fleetId, new StarSystemId(2L), new FleetReadinessState(9_000, 9_000, 9_000, 9_000, 9_000, 9_000, 1_000));

        var review = new StrategicOperationService().reviewSupplyAndReadiness(state, 1L, forces, 10L);

        assertEquals(SupplyDecision.SUBMIT_ORDINARY_WITHDRAW_ORDER, review.decision());
        assertEquals(OperationStatus.WITHDRAWING, review.state().requireOperation(1L).status());
        assertEquals(1_000, forces.find(fleetId).orElseThrow().readiness().supplyAccessBps(),
                "operation review must not grant supply");
    }

    @Test
    void zeroPropellantCanMakeConfiguredWithdrawalPhysicallyImpossible() {
        FleetId fleetId = new FleetId(10L);
        OperationState operation = operation(fleetId, new SupplyPolicy(2_000, 0, 100L));
        StrategicOperationState state = new StrategicOperationState(2L, List.of(operation));
        FleetForceRegistry forces = registry(
                fleetId, new StarSystemId(2L), new FleetReadinessState(9_000, 9_000, 0, 9_000, 9_000, 9_000, 9_000));

        var review = new StrategicOperationService().reviewSupplyAndReadiness(state, 1L, forces, 10L);

        assertEquals(SupplyDecision.FAIL_CANNOT_WITHDRAW, review.decision());
        assertEquals(OperationStatus.FAILED, review.state().requireOperation(1L).status());
    }

    @Test
    void reinforcementMustPhysicallyArriveBeforeJoining() {
        FleetId participant = new FleetId(10L);
        FleetId reinforcement = new FleetId(11L);
        StrategicOperationState state = new StrategicOperationState(
                2L, List.of(operation(participant, new SupplyPolicy(2_000, 0, 100L))));
        FleetForceRegistry wrongSystem = new FleetForceRegistry(List.of(
                entry(participant, new StarSystemId(2L), ready()),
                entry(reinforcement, new StarSystemId(3L), ready())));

        assertThrows(IllegalStateException.class, () -> new Stage21EReinforcementService().attachArrived(
                state, 1L, reinforcement, wrongSystem, 5L));

        FleetForceRegistry arrived = new FleetForceRegistry(List.of(
                entry(participant, new StarSystemId(2L), ready()),
                entry(reinforcement, new StarSystemId(2L), ready())));
        StrategicOperationState reinforced = new Stage21EReinforcementService().attachArrived(
                state, 1L, reinforcement, arrived, 6L);

        assertTrue(reinforced.requireOperation(1L).participantFleetIds().contains(reinforcement));
    }

    private static OperationState operation(FleetId fleetId, SupplyPolicy supply) {
        return new OperationState(
                1L, OperationType.DEFENSE, 1L, 1L, 1, List.of(fleetId),
                new StarSystemId(1L), new StarSystemId(2L), "system:2",
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                supply,
                new WithdrawalPolicy(new StarSystemId(1L), 1_000, true, true),
                OperationStatus.ACTIVE, 0L, 0L, -1L, null, null);
    }

    private static FleetForceRegistry registry(FleetId fleetId, StarSystemId systemId, FleetReadinessState readiness) {
        return new FleetForceRegistry(List.of(entry(fleetId, systemId, readiness)));
    }

    private static FleetForceRegistry.Entry entry(
            FleetId fleetId, StarSystemId systemId, FleetReadinessState readiness) {
        EntityState entity = new EntityState(
                new EntityId(1_000L + fleetId.value()),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(1),
                null, null, null, null, null, null, null, null, null);
        return new FleetForceRegistry.Entry(
                fleetId, 1, FleetLocationKind.IN_SYSTEM, systemId, null, null, entity, readiness);
    }

    private static FleetReadinessState ready() {
        return new FleetReadinessState(9_000, 9_000, 9_000, 9_000, 9_000, 9_000, 9_000);
    }
}
