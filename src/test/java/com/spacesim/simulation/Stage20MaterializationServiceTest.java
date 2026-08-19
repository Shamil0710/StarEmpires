package com.spacesim.simulation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20MaterializationServiceTest {
    @Test
    void dematerializeAndMaterializeRoundTripPreservesPersistentAndFarPhysicalStateExactly() {
        SimulationSession session = SimulationSession.createDemo(20_011L);
        Stage20MaterializationService service = Stage20MaterializationService.forSession(session);
        Entity original = firstPersistentEntity(session);
        EntityId id = original.getComponent(EntityIdComponent.class).id;
        EntityState before = EntityStateMapper.capture(original);
        LocalPhysicalKinematics physical = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(8_000_000_000L, -7_000_000_000L, 12_345.25d, -9_876.5d),
                1_234.5d,
                -987.25d);
        int persistentCountBefore = session.snapshot().entities().size();

        service.registerPhysicalState(id, physical);
        Stage20MaterializationService.DematerializedEntitySnapshot dormant = service.dematerialize(id);

        assertFalse(session.getEntityRegistry().contains(id));
        assertTrue(service.isDematerialized(id));
        assertEquals(before, dormant.entityState());
        assertEquals(physical, dormant.physicalState());
        assertEquals(physical, service.physicalState(id).orElseThrow());
        assertEquals(persistentCountBefore, service.snapshotAllPersistentEntities().size());
        assertEquals(before, state(service.snapshotAllPersistentEntities(), id));

        Entity restored = service.materialize(id);

        assertNotSame(original, restored);
        assertEquals(restored, session.getEntityRegistry().require(id));
        assertFalse(service.isDematerialized(id));
        assertEquals(before, EntityStateMapper.capture(restored));
        assertEquals(physical, service.physicalState(id).orElseThrow());
    }

    @Test
    void strategicPhysicalStateCanAdvanceWhileRuntimeEntityIsDematerialized() {
        SimulationSession session = SimulationSession.createDemo(20_012L);
        Stage20MaterializationService service = Stage20MaterializationService.forSession(session);
        Entity original = firstPersistentEntity(session);
        EntityId id = original.getComponent(EntityIdComponent.class).id;
        LocalPhysicalKinematics initial = new LocalPhysicalKinematics(
                new LocalPhysicalPosition(4_000_000_000L, 5_000_000_000L, 100d, -200d),
                500d,
                -250d);
        LocalPhysicalKinematics advanced = new LocalPhysicalKinematics(
                initial.position().translated(25_000d, -12_500d),
                520d,
                -245d);

        service.registerPhysicalState(id, initial);
        service.dematerialize(id);
        service.updatePhysicalState(id, advanced);

        assertFalse(session.getEntityRegistry().contains(id));
        assertEquals(advanced, service.dematerializedSnapshot(id).orElseThrow().physicalState());
        Entity restored = service.materialize(id);
        assertEquals(id, restored.getComponent(EntityIdComponent.class).id);
        assertEquals(advanced, service.physicalState(id).orElseThrow());
    }

    @Test
    void stage20DematerializationRefusesToFallBackToLegacyFloatTransform() {
        SimulationSession session = SimulationSession.createDemo(20_013L);
        Stage20MaterializationService service = Stage20MaterializationService.forSession(session);
        Entity live = firstPersistentEntity(session);
        EntityId id = live.getComponent(EntityIdComponent.class).id;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> service.dematerialize(id));

        assertTrue(error.getMessage().contains("authoritative physical kinematics"));
        assertTrue(session.getEntityRegistry().contains(id));
    }

    @Test
    void synchronousMaterializationHasZeroSimulationTimeWakeLatencyByContract() {
        assertEquals(0d, Stage20MaterializationService.SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS);
    }

    private static Entity firstPersistentEntity(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Demo session has no persistent entity");
    }

    private static EntityState state(List<EntityState> states, EntityId id) {
        return states.stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
