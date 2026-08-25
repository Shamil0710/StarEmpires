package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Acceptance for exact local FTL approach continuation through the existing Stage-20 checkpoint. */
class Stage20GeneratedWorldMidApproachPersistenceAcceptanceTest {
    @Test
    void movingToJumpExactPhysicalStateRoundTripsAndContinuesTowardPersistedEndpoint() {
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime original = Stage20PlayableGeneratedWorldFactory.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED).runtime();
        RouteFixture route = routeWithPhysicalDistance(original);
        FleetPlacementState source = route.placement();
        LocalPhysicalKinematics initial = original.arrival().materialization(route.origin())
                .physicalState(source.localEntityId()).orElseThrow();

        var requested = original.world().requestFleetJump(source.id(), route.destination());
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, requested.phase());
        assertTrue(requested.phaseEndsTick() - requested.phaseStartedTick() > 2L,
                "acceptance fixture must exercise a real multi-tick local FTL approach");

        original.advanceFrame(SimulationSession.DEFAULT_FIXED_STEP_SECONDS);
        var midJump = original.world().findFleetJump(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, midJump.phase());
        LocalPhysicalKinematics midPhysical = original.arrival().materialization(route.origin())
                .physicalState(source.localEntityId()).orElseThrow();
        assertNotEquals(initial.position(), midPhysical.position());
        assertTrue(midPhysical.position().distanceTo(route.outgoingEndpoint())
                < initial.position().distanceTo(route.outgoingEndpoint()));
        assertLiveProjection(original, source, midPhysical);

        byte[] encoded = Stage20GeneratedWorldRuntimePersistenceCodec.encode(original.captureState());
        Stage20GeneratedWorldRuntimeBridge.LiveRuntime restored = Stage20GeneratedWorldRuntimeBridge.restore(
                Stage20GeneratedWorldRuntimePersistenceCodec.decode(encoded));

        FleetPlacementState restoredPlacement = restored.world().findFleet(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, restoredPlacement.locationKind());
        assertEquals(route.origin(), restoredPlacement.systemId());
        assertEquals(midJump, restored.world().findFleetJump(source.id()).orElseThrow());
        LocalPhysicalKinematics restoredPhysical = restored.arrival().materialization(route.origin())
                .physicalState(restoredPlacement.localEntityId()).orElseThrow();
        assertEquals(midPhysical, restoredPhysical,
                "checkpoint must restore exact hierarchical position and local approach velocity");
        assertLiveProjection(restored, restoredPlacement, restoredPhysical);

        restored.advanceFrame(SimulationSession.DEFAULT_FIXED_STEP_SECONDS);
        LocalPhysicalKinematics continued = restored.arrival().materialization(route.origin())
                .physicalState(restoredPlacement.localEntityId()).orElseThrow();
        assertNotEquals(restoredPhysical.position(), continued.position(),
                "restored MOVING_TO_JUMP must continue without process-local hidden approach state");
        assertTrue(continued.position().distanceTo(route.outgoingEndpoint())
                < restoredPhysical.position().distanceTo(route.outgoingEndpoint()));
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP,
                restored.world().findFleetJump(source.id()).orElseThrow().phase());
        assertEquals(FleetLocationKind.IN_SYSTEM,
                restored.world().findFleet(source.id()).orElseThrow().locationKind());
        assertLiveProjection(restored, restoredPlacement, continued);
    }

    private static RouteFixture routeWithPhysicalDistance(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            for (StarSystemId destination : runtime.world().getTopology().neighbors(placement.systemId())) {
                LocalPhysicalKinematics current = runtime.arrival().materialization(placement.systemId())
                        .physicalState(placement.localEntityId()).orElse(null);
                if (current == null) {
                    continue;
                }
                LocalPhysicalPosition outgoing = runtime.arrival()
                        .resolve(destination, placement.systemId())
                        .physicalState()
                        .position();
                if (current.position().distanceTo(outgoing) > 0d) {
                    return new RouteFixture(placement, placement.systemId(), destination, outgoing);
                }
            }
        }
        throw new AssertionError("generated acceptance world lacks a local fleet away from an outgoing FTL endpoint");
    }

    private static void assertLiveProjection(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime,
            FleetPlacementState placement,
            LocalPhysicalKinematics physical) {
        Entity entity = runtime.world().findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().require(placement.localEntityId());
        TransformComponent transform = entity.getComponent(TransformComponent.class);
        assertEquals((float) physical.position().offsetXM(), transform.position.x, 0f);
        assertEquals((float) physical.position().offsetYM(), transform.position.y, 0f);
        assertEquals((float) physical.velocityXMps(), transform.velocity.x, 0f);
        assertEquals((float) physical.velocityYMps(), transform.velocity.y, 0f);
    }

    private record RouteFixture(
            FleetPlacementState placement,
            StarSystemId origin,
            StarSystemId destination,
            LocalPhysicalPosition outgoingEndpoint) { }
}
