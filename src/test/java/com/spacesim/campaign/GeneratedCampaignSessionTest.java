package com.spacesim.campaign;

import com.spacesim.persistence.Stage20FreightPersistentState;
import com.spacesim.persistence.Stage20GeneratedWorldRuntimePersistentState;
import com.spacesim.world.generation.Stage20PlayableGeneratedWorldFactory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneratedCampaignSessionTest {
    @Test
    void highTimeScaleProducesSameAuthoritativeStateAcrossPresentationFramePartitions() {
        GeneratedCampaignSession bootstrap = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var checkpoint = bootstrap.captureState();
        GeneratedCampaignSession coarse = GeneratedCampaignSession.restore(checkpoint);
        GeneratedCampaignSession fine = GeneratedCampaignSession.restore(checkpoint);

        coarse.setTimeScale(8d);
        fine.setTimeScale(8d);

        var coarseReport = coarse.advanceFrame(0.1f);
        int fineAutonomousDecisions = 0;
        long fineTicks = 0L;
        for (int frame = 0; frame < 8; frame++) {
            var report = fine.advanceFrame(0.0125f);
            fineAutonomousDecisions += report.autonomousDecisions();
            fineTicks += report.fixedTicks();
        }

        assertEquals(8L, coarseReport.fixedTicks());
        assertEquals(2, coarseReport.autonomousDecisions());
        assertEquals(coarseReport.fixedTicks(), fineTicks);
        assertEquals(coarseReport.autonomousDecisions(), fineAutonomousDecisions);
        assertEquals(coarse.captureState(), fine.captureState());
    }

    @Test
    void supportedTimeScalesReachSameAuthoritativeStateAtSameSimulationTick() {
        GeneratedCampaignSession bootstrap = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        var checkpoint = bootstrap.captureState();
        List<Object> states = new ArrayList<>();

        for (double scale : List.of(1d, 2d, 4d, 8d)) {
            GeneratedCampaignSession session = GeneratedCampaignSession.restore(checkpoint);
            session.setTimeScale(scale);
            float realFrameSeconds = (float) (0.1d / scale);
            long ticks = 0L;
            int decisions = 0;
            for (int frame = 0; frame < 8; frame++) {
                var report = session.advanceFrame(realFrameSeconds);
                ticks += report.fixedTicks();
                decisions += report.autonomousDecisions();
            }
            assertEquals(8L, ticks, "supported speed must reach the same simulation tick");
            assertEquals(2, decisions, "autonomous cadence must depend on simulation ticks only");
            session.setTimeScale(1d);
            states.add(session.captureState());
        }

        for (int index = 1; index < states.size(); index++) {
            assertEquals(states.get(0), states.get(index),
                    "supported acceleration must not change authoritative campaign state");
        }
    }

    @Test
    void saveRestoreImmediatelyBeforeDecisionBoundaryPreservesAutonomousCadence() {
        GeneratedCampaignSession continuous = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        for (int tick = 0; tick < 3; tick++) {
            var report = continuous.advanceFrame(0.1f);
            assertEquals(1L, report.fixedTicks());
            assertEquals(0, report.autonomousDecisions());
        }
        var checkpoint = continuous.captureState();
        GeneratedCampaignSession resumed = GeneratedCampaignSession.restore(checkpoint);

        var continuousReport = continuous.advanceFrame(0.1f);
        var resumedReport = resumed.advanceFrame(0.1f);

        assertEquals(1, continuousReport.autonomousDecisions());
        assertEquals(continuousReport.autonomousDecisions(), resumedReport.autonomousDecisions());
        assertEquals(continuous.captureState(), resumed.captureState());
    }

    @Test
    void inTransitFreightCheckpointPreservesFleetOrderAndCargoProvenance() {
        GeneratedCampaignSession continuous = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        Stage20FreightPersistentState initialFreight = continuous.captureState().freight();
        Stage20FreightPersistentState.FreighterState initialFreighter = initialFreight.freighters().stream()
                .filter(freighter -> freighter.phase() == Stage20FreightPersistentState.FreightPhase.AT_SOURCE)
                .filter(freighter -> !freighter.activeOrderId().isBlank())
                .findFirst()
                .orElseThrow();
        var fleetId = initialFreighter.fleetId();
        String orderId = initialFreighter.activeOrderId();
        Stage20FreightPersistentState.TransportOrderState initialOrder = initialFreight.orders().stream()
                .filter(order -> order.orderId().equals(orderId))
                .findFirst()
                .orElseThrow();

        Stage20GeneratedWorldRuntimePersistentState checkpoint = null;
        Stage20FreightPersistentState.CargoLotState loadedLot = null;
        int guard = 0;
        while (guard++ < 40) {
            continuous.advanceFrame(0.1f);
            Stage20GeneratedWorldRuntimePersistentState candidate = continuous.captureState();
            loadedLot = candidate.freight().cargoLots().stream()
                    .filter(lot -> lot.fleetId().equals(fleetId))
                    .filter(lot -> lot.orderId().equals(orderId))
                    .findFirst()
                    .orElse(null);
            boolean hasAuthoritativeJump = candidate.worldState().fleetJumps().stream()
                    .anyMatch(jump -> jump.fleetId().equals(fleetId));
            if (loadedLot != null && hasAuthoritativeJump) {
                checkpoint = candidate;
                break;
            }
        }

        assertNotNull(checkpoint,
                "accepted generated freight must enter ordinary FleetJumpState with physical cargo");
        assertNotNull(loadedLot);
        assertEquals(initialOrder.sourceProvenanceId(), loadedLot.sourceProvenanceId());
        String loadedLotId = loadedLot.lotId();

        Stage20FreightPersistentState.TransportOrderState checkpointOrder = checkpoint.freight().orders().stream()
                .filter(order -> order.orderId().equals(orderId))
                .findFirst()
                .orElseThrow();
        Stage20FreightPersistentState.FreighterState checkpointFreighter = checkpoint.freight().freighters().stream()
                .filter(freighter -> freighter.fleetId().equals(fleetId))
                .findFirst()
                .orElseThrow();
        var jump = checkpoint.worldState().fleetJumps().stream()
                .filter(value -> value.fleetId().equals(fleetId))
                .findFirst()
                .orElseThrow();

        assertEquals(initialOrder.orderId(), checkpointOrder.orderId());
        assertEquals(initialOrder.fleetId(), checkpointOrder.fleetId());
        assertEquals(initialOrder.orderedSystems(), checkpointOrder.orderedSystems());
        assertEquals(initialOrder.sourceProvenanceId(), checkpointOrder.sourceProvenanceId());
        assertEquals(orderId, checkpointFreighter.activeOrderId());
        assertEquals(Stage20FreightPersistentState.FreightPhase.OUTBOUND, checkpointFreighter.phase());
        assertEquals(checkpointOrder.orderedSystems().get(0), jump.originSystemId());
        assertEquals(checkpointOrder.orderedSystems().get(1), jump.destinationSystemId());

        GeneratedCampaignSession resumed = GeneratedCampaignSession.restore(checkpoint);
        assertEquals(checkpoint, resumed.captureState(),
                "restore must not regenerate or rename an in-flight accepted freight route");
        Stage20FreightPersistentState.CargoLotState restoredLot = resumed.captureState().freight().cargoLots().stream()
                .filter(lot -> lot.lotId().equals(loadedLotId))
                .findFirst()
                .orElseThrow();
        assertEquals(loadedLot, restoredLot);
        assertEquals(fleetId, restoredLot.fleetId());
        assertEquals(orderId, restoredLot.orderId());
        assertEquals(initialOrder.sourceProvenanceId(), restoredLot.sourceProvenanceId());

        for (int frame = 0; frame < 40; frame++) {
            continuous.advanceFrame(0.1f);
            resumed.advanceFrame(0.1f);
        }

        assertEquals(continuous.captureState(), resumed.captureState(),
                "the same in-flight cargo must continue deterministically after checkpoint restore");
        Stage20FreightPersistentState continuedFreight = resumed.captureState().freight();
        assertTrue(continuedFreight.freighters().stream().anyMatch(freighter -> freighter.fleetId().equals(fleetId)));
        assertTrue(continuedFreight.orders().stream().anyMatch(order ->
                order.orderId().equals(orderId)
                        && order.fleetId().equals(fleetId)
                        && order.sourceProvenanceId().equals(initialOrder.sourceProvenanceId())));
    }

    @Test
    void pauseIsCampaignOrchestrationAndCannotAdvanceAuthoritativeState() {
        GeneratedCampaignSession session = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        session.setPaused(true);
        var paused = session.captureState();

        var report = session.advanceFrame(10f);

        assertTrue(session.isPaused());
        assertEquals(0L, report.fixedTicks());
        assertEquals(0, report.autonomousDecisions());
        assertFalse(report.lastAutonomousAction().changedState());
        assertEquals(paused, session.captureState());
    }

    @Test
    void saveRestoreContinuesClockFractionAndStableRuntimeStateWithoutRegeneration() {
        GeneratedCampaignSession continuous = GeneratedCampaignSession.create(
                Stage20PlayableGeneratedWorldFactory.DEFAULT_WORLD_SEED);
        continuous.advanceFrame(0.05f);
        var halfTickCheckpoint = continuous.captureState();

        GeneratedCampaignSession resumed = GeneratedCampaignSession.restore(halfTickCheckpoint);
        var resumedReport = resumed.advanceFrame(0.05f);
        var continuousReport = continuous.advanceFrame(0.05f);

        assertEquals(1L, resumedReport.fixedTicks());
        assertEquals(continuousReport.fixedTicks(), resumedReport.fixedTicks());
        assertEquals(continuous.captureState(), resumed.captureState());
        assertEquals(
                halfTickCheckpoint.worldState().nextFleetIdValue(),
                resumed.captureState().worldState().nextFleetIdValue());
    }
}
