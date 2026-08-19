package com.spacesim.simulation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EntityIdComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.simulation.Stage20RepresentationScheduler.RuntimeRepresentationAction;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RelevanceInput;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage20RepresentationSchedulerTest {
    @Test
    void noRelevanceDematerializesOnlyDormantAndDueEventPromotesSynchronously() {
        SimulationSession session = SimulationSession.createDemo(20_031L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Stage20RepresentationScheduler scheduler = new Stage20RepresentationScheduler(materialization);
        Entity live = firstPersistentEntity(session);
        EntityId id = live.getComponent(EntityIdComponent.class).id;
        EntityState before = EntityStateMapper.capture(live);
        LocalPhysicalKinematics physical = physicalState();
        materialization.registerPhysicalState(id, physical);

        var dormant = scheduler.synchronize(id, new RelevanceInput(false, false, false, false));

        assertEquals(RepresentationLevel.STRATEGIC, dormant.previousLevel());
        assertEquals(RepresentationLevel.DORMANT, dormant.requiredLevel());
        assertEquals(RuntimeRepresentationAction.DEMATERIALIZED_RUNTIME, dormant.runtimeAction());
        assertTrue(dormant.levelChanged());
        assertTrue(materialization.isDematerialized(id));
        assertFalse(session.getEntityRegistry().contains(id));
        assertEquals(before, materialization.dematerializedSnapshot(id).orElseThrow().entityState());
        assertEquals(physical, materialization.physicalState(id).orElseThrow());

        var strategic = scheduler.synchronize(id, new RelevanceInput(false, false, false, true));

        assertEquals(RepresentationLevel.DORMANT, strategic.previousLevel());
        assertEquals(RepresentationLevel.STRATEGIC, strategic.requiredLevel());
        assertEquals(RuntimeRepresentationAction.MATERIALIZED_RUNTIME, strategic.runtimeAction());
        assertEquals(0d, strategic.wakeLatencySimulationSeconds());
        assertFalse(materialization.isDematerialized(id));
        assertEquals(before, EntityStateMapper.capture(session.getEntityRegistry().require(id)));
        assertEquals(physical, materialization.physicalState(id).orElseThrow());
    }

    @Test
    void strategicActiveAndTacticalLevelsKeepOneLivePersistentEcsRepresentation() {
        SimulationSession session = SimulationSession.createDemo(20_032L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Stage20RepresentationScheduler scheduler = new Stage20RepresentationScheduler(materialization);
        Entity live = firstPersistentEntity(session);
        EntityId id = live.getComponent(EntityIdComponent.class).id;
        materialization.registerPhysicalState(id, physicalState());

        var strategic = scheduler.synchronize(id, new RelevanceInput(false, false, true, false));
        Entity afterStrategic = session.getEntityRegistry().require(id);
        var active = scheduler.synchronize(id, new RelevanceInput(false, true, true, false));
        Entity afterActive = session.getEntityRegistry().require(id);
        var tactical = scheduler.synchronize(id, new RelevanceInput(true, true, true, false));
        Entity afterTactical = session.getEntityRegistry().require(id);

        assertEquals(RuntimeRepresentationAction.NONE, strategic.runtimeAction());
        assertEquals(RepresentationLevel.STRATEGIC, strategic.requiredLevel());
        assertEquals(RuntimeRepresentationAction.NONE, active.runtimeAction());
        assertEquals(RepresentationLevel.ACTIVE_LOCAL, active.requiredLevel());
        assertEquals(RuntimeRepresentationAction.NONE, tactical.runtimeAction());
        assertEquals(RepresentationLevel.TACTICAL, tactical.requiredLevel());
        assertSame(live, afterStrategic);
        assertSame(live, afterActive);
        assertSame(live, afterTactical);
        assertFalse(materialization.isDematerialized(id));
    }

    @Test
    void synchronousSchedulerDerivesContextSpecificBandAtExplicitInteractionEnvelope() {
        SimulationSession session = SimulationSession.createDemo(20_033L);
        Stage20MaterializationService materialization = Stage20MaterializationService.forSession(session);
        Stage20RepresentationScheduler scheduler = new Stage20RepresentationScheduler(materialization);

        var band = scheduler.deriveActivationBand(
                25_000d,
                4_500d,
                "accepted.test.interaction_envelope");

        assertEquals(DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT, band.authority());
        assertEquals(25_000d, band.interactionEnvelopeRadiusM());
        assertEquals(0d, band.closingDuringWakeM());
        assertEquals(25_000d, band.activationDistanceM());
        assertEquals("accepted.test.interaction_envelope", band.provenance());
    }

    @Test
    void schedulerRefusesEntityWithoutStage20PhysicalAuthority() {
        SimulationSession session = SimulationSession.createDemo(20_034L);
        Stage20RepresentationScheduler scheduler = new Stage20RepresentationScheduler(
                Stage20MaterializationService.forSession(session));
        Entity live = firstPersistentEntity(session);
        EntityId id = live.getComponent(EntityIdComponent.class).id;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> scheduler.synchronize(id, new RelevanceInput(false, false, true, false)));

        assertTrue(error.getMessage().contains("physical authority"));
        assertTrue(session.getEntityRegistry().contains(id));
    }

    private static LocalPhysicalKinematics physicalState() {
        return new LocalPhysicalKinematics(
                new LocalPhysicalPosition(3_500_000_000L, -2_500_000_000L, 1_200d, -800d),
                950d,
                -325d);
    }

    private static Entity firstPersistentEntity(SimulationSession session) {
        for (Entity entity : session.getEngine().getEntities()) {
            if (entity.getComponent(EntityIdComponent.class) != null) {
                return entity;
            }
        }
        throw new AssertionError("Demo session has no persistent entity");
    }
}
