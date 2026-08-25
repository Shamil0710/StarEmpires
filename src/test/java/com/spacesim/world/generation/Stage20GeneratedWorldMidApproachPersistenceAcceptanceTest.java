package com.spacesim.world.generation;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.TransformComponent;
import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20FreightPersistentState.FreighterState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimeBridge;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistenceCodec;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.simulation.SimulationSession;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetJumpPhase;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

        Stage20GeneratedWorldRuntimePersistentState checkpoint = original.captureState();
        FreighterState capturedFreight = checkpoint.freight().freighters().stream()
                .filter(value -> value.fleetId().equals(source.id()))
                .findFirst()
                .orElseThrow();
        var capturedExact = checkpoint.localFleetPhysicalStates().stream()
                .filter(value -> value.fleetId().equals(source.id()))
                .findFirst()
                .orElseThrow();
        assertEquals(midPhysical, capturedExact.physicalState());
        assertEquals(midPhysical, capturedFreight.physicalState(),
                "atomic capture must refresh the redundant freight mirror from exact local physics");
        assertRejectsTamperedFreightMirror(checkpoint, capturedFreight, midPhysical);

        byte[] encoded = Stage20GeneratedWorldRuntimePersistenceCodec.encode(checkpoint);
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

    private static void assertRejectsTamperedFreightMirror(
            Stage20GeneratedWorldRuntimePersistentState checkpoint,
            FreighterState capturedFreight,
            LocalPhysicalKinematics exact) {
        LocalPhysicalKinematics tamperedPhysical = new LocalPhysicalKinematics(
                exact.position().translated(1d, 0d),
                exact.velocityXMps(),
                exact.velocityYMps());
        FreighterState tamperedFleet = new FreighterState(
                capturedFreight.fleetId(),
                capturedFreight.stableFactionId(),
                capturedFreight.ownershipOrdinal(),
                capturedFreight.hullId(),
                capturedFreight.fitId(),
                capturedFreight.cargoCapacityKg(),
                capturedFreight.currentSystemId(),
                tamperedPhysical,
                capturedFreight.phase(),
                capturedFreight.activeOrderId(),
                capturedFreight.routeIndex(),
                capturedFreight.cargoStorage());
        ArrayList<FreighterState> tamperedFleets = new ArrayList<>(checkpoint.freight().freighters());
        int index = tamperedFleets.indexOf(capturedFreight);
        if (index < 0) {
            throw new AssertionError("captured freight disappeared before tamper acceptance");
        }
        tamperedFleets.set(index, tamperedFleet);
        Stage20FreightPersistentState freight = checkpoint.freight();
        Stage20FreightPersistentState tamperedFreight = new Stage20FreightPersistentState(
                freight.schemaVersion(),
                freight.rootSeed(),
                freight.generatorVersion(),
                freight.worldFingerprint(),
                freight.materializationVersion(),
                freight.compatibilityAuthorityVersion(),
                freight.nextFleetIdValue(),
                freight.nextCargoLotOrdinal(),
                tamperedFleets,
                freight.cargoLots(),
                freight.orders());

        assertThrows(IllegalArgumentException.class, () -> new Stage20GeneratedWorldRuntimePersistentState(
                checkpoint.schemaVersion(),
                checkpoint.bridgeVersion(),
                checkpoint.campaign(),
                checkpoint.worldState(),
                checkpoint.activeSystemId(),
                tamperedFreight,
                checkpoint.localFleetPhysicalStates()),
                "checkpoint construction must reject a freight mirror that differs from exact local physics");
    }

    private static RouteFixture routeWithPhysicalDistance(
            Stage20GeneratedWorldRuntimeBridge.LiveRuntime runtime) {
        for (FleetPlacementState placement : runtime.world().getFleetPlacements()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                    || runtime.freight().findFreighter(placement.id()).isEmpty()) {
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
        throw new AssertionError("generated acceptance world lacks a freight fleet away from an outgoing FTL endpoint");
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
