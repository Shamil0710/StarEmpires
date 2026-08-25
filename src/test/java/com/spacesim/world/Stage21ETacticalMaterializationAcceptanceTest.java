package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.world.FactionActorObservationSnapshot.ObservationChannel;
import com.spacesim.world.Stage21ETacticalMaterializationService.TacticalMaterializationRequest;
import com.spacesim.world.StrategicOperationState.ContactState;
import com.spacesim.world.StrategicOperationState.OperationState;
import com.spacesim.world.StrategicOperationState.OperationStatus;
import com.spacesim.world.StrategicOperationState.OperationType;
import com.spacesim.world.StrategicOperationState.RulesOfEngagement;
import com.spacesim.world.StrategicOperationState.SupplyPolicy;
import com.spacesim.world.StrategicOperationState.WithdrawalPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class Stage21ETacticalMaterializationAcceptanceTest {
    @Test
    void exactOrdinaryEntityPayloadsAreHandedToStage19AuthorityAfterPhysicalMeeting() {
        FleetId attackerId = new FleetId(10L);
        FleetId targetId = new FleetId(20L);
        EntityState attacker = physicalEntity(1010L, 1, "hull.attacker");
        EntityState target = physicalEntity(1020L, 2, "hull.target");
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(attackerId, 1, attacker),
                entry(targetId, 2, target)));
        OperationState operation = contactedOperation(attackerId, targetId, new StarSystemId(2L), 40L, 60L);
        AtomicReference<TacticalMaterializationRequest> captured = new AtomicReference<>();

        var encounter = new Stage21ETacticalMaterializationService().materialize(
                operation, forces, 50L, request -> {
                    captured.set(request);
                    return 77L;
                });

        assertEquals(77L, encounter.encounterId());
        assertEquals(targetId, encounter.targetFleetId());
        TacticalMaterializationRequest request = captured.get();
        assertEquals(new StarSystemId(2L), request.systemId());
        assertSame(attacker, request.combatants().stream()
                .filter(row -> row.fleetId().equals(attackerId)).findFirst().orElseThrow().entityState());
        assertSame(target, request.combatants().stream()
                .filter(row -> row.fleetId().equals(targetId)).findFirst().orElseThrow().entityState());
    }

    @Test
    void targetThatMovedAwayFromObservedSystemCannotMaterializeCombat() {
        FleetId attackerId = new FleetId(10L);
        FleetId targetId = new FleetId(20L);
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(attackerId, 1, physicalEntity(1010L, 1, "hull.attacker")),
                new FleetForceRegistry.Entry(
                        targetId, 2, FleetLocationKind.IN_SYSTEM, new StarSystemId(3L), null, null,
                        physicalEntity(1020L, 2, "hull.target"), ready())));
        OperationState operation = contactedOperation(attackerId, targetId, new StarSystemId(2L), 40L, 60L);

        assertThrows(IllegalStateException.class, () -> new Stage21ETacticalMaterializationService().materialize(
                operation, forces, 50L, request -> 77L));
    }

    @Test
    void staleContactCannotMaterializeCombatEvenWhenFleetsAreCoLocated() {
        FleetId attackerId = new FleetId(10L);
        FleetId targetId = new FleetId(20L);
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(attackerId, 1, physicalEntity(1010L, 1, "hull.attacker")),
                entry(targetId, 2, physicalEntity(1020L, 2, "hull.target"))));
        OperationState operation = contactedOperation(attackerId, targetId, new StarSystemId(2L), 20L, 30L);

        assertThrows(IllegalStateException.class, () -> new Stage21ETacticalMaterializationService().materialize(
                operation, forces, 50L, request -> 77L));
    }

    private static OperationState contactedOperation(
            FleetId attackerId,
            FleetId targetId,
            StarSystemId systemId,
            long observedAt,
            long freshUntil) {
        ContactState contact = new ContactState(
                targetId, systemId, ObservationChannel.LOCAL_SENSOR_REPORT,
                "sensor:" + targetId.value(), observedAt, freshUntil);
        return new OperationState(
                1L, OperationType.INTERCEPTION, 1L, 1L, 1, List.of(attackerId),
                new StarSystemId(1L), systemId, "system:" + systemId.value(),
                RulesOfEngagement.IDENTIFIED_HOSTILES,
                new SupplyPolicy(2_000, 1_000, 100L),
                new WithdrawalPolicy(new StarSystemId(1L), 1_000, true, true),
                OperationStatus.CONTACT_CONFIRMED, 0L, observedAt, -1L, contact, null);
    }

    private static FleetForceRegistry.Entry entry(FleetId id, int factionId, EntityState entity) {
        return new FleetForceRegistry.Entry(
                id, factionId, FleetLocationKind.IN_SYSTEM, new StarSystemId(2L),
                null, null, entity, ready());
    }

    private static FleetReadinessState ready() {
        return new FleetReadinessState(9_000, 9_000, 9_000, 9_000, 9_000, 9_000, 9_000);
    }

    private static EntityState physicalEntity(long id, int factionId, String hullId) {
        EntityState.EngineeringState engineering = new EntityState.EngineeringState(
                hullId,
                List.of(),
                new EntityState.EngineeringConsumableState(0d, 0d, 0d, 0d, List.of()),
                0d,
                0d,
                List.of(),
                List.of(),
                0d,
                List.of(),
                null);
        return new EntityState(
                new EntityId(id),
                null, null, null, null, null, null, null,
                new EntityState.FactionState(factionId),
                null, null, null, null, null, null, null, engineering, null);
    }
}
