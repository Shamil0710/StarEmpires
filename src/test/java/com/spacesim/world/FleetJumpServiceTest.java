package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.simulation.SimulationSession;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetJumpServiceTest {
    private static final ContentCatalog CONTENT = ContentCatalogLoader.loadDefault();
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final StarSystemId GAMMA = new StarSystemId(3L);

    @Test
    void directJumpRunsDeterministicPhasesAndKeepsStableFleetId() {
        WorldState state = worldState();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(state);
        FleetWorldService fleets = new FleetWorldService(
                sessions, state.nextFleetIdValue(), state.fleets());
        JumpTransitTiming timing = new JumpTransitTiming(2L, 3L, 2L, 10d);
        FleetJumpService jumps = new FleetJumpService(
                state.topology(), sessions, fleets, timing, List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM)
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst()
                .orElseThrow();

        FleetJumpState requested = jumps.requestJump(source.id(), BETA, 0L, 50f, -25f);
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, requested.phase());
        assertEquals(2L, requested.phaseEndsTick());
        assertTrue(sessions.get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        jumps.advance(1L);
        assertEquals(FleetJumpPhase.MOVING_TO_JUMP, jumps.find(source.id()).orElseThrow().phase());

        jumps.advance(2L);
        assertEquals(FleetJumpPhase.JUMP_PENDING, jumps.find(source.id()).orElseThrow().phase());
        assertEquals(5L, jumps.find(source.id()).orElseThrow().phaseEndsTick());
        assertTrue(sessions.get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        jumps.advance(5L);
        FleetJumpState transit = jumps.find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.IN_TRANSIT, transit.phase());
        assertEquals(105L, transit.phaseEndsTick());
        assertEquals(FleetLocationKind.IN_TRANSIT, fleets.find(source.id()).orElseThrow().locationKind());
        assertFalse(sessions.get(ALPHA).getEntityRegistry().contains(source.localEntityId()));

        jumps.advance(104L);
        assertEquals(FleetJumpPhase.IN_TRANSIT, jumps.find(source.id()).orElseThrow().phase());

        jumps.advance(105L);
        FleetJumpState arriving = jumps.find(source.id()).orElseThrow();
        assertEquals(FleetJumpPhase.ARRIVING, arriving.phase());
        assertEquals(107L, arriving.phaseEndsTick());
        FleetPlacementState destination = fleets.find(source.id()).orElseThrow();
        assertEquals(FleetLocationKind.IN_SYSTEM, destination.locationKind());
        assertEquals(BETA, destination.systemId());
        assertTrue(sessions.get(BETA).getEntityRegistry().contains(destination.localEntityId()));

        jumps.advance(107L);
        assertTrue(jumps.find(source.id()).isEmpty());
        assertEquals(source.id(), fleets.findByLocal(BETA, destination.localEntityId()).orElseThrow());
    }

    @Test
    void coarseAdvanceCrossesAllExpiredPhasesAtExactBoundaries() {
        WorldState state = worldState();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(state);
        FleetWorldService fleets = new FleetWorldService(
                sessions, state.nextFleetIdValue(), state.fleets());
        FleetJumpService jumps = new FleetJumpService(
                state.topology(), sessions, fleets,
                new JumpTransitTiming(1L, 1L, 1L, 1000d), List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst().orElseThrow();

        jumps.requestJump(source.id(), BETA, 10L, 1f, 2f);
        jumps.advance(100L);

        assertTrue(jumps.find(source.id()).isEmpty());
        FleetPlacementState arrived = fleets.find(source.id()).orElseThrow();
        assertEquals(BETA, arrived.systemId());
        assertEquals(FleetLocationKind.IN_SYSTEM, arrived.locationKind());
    }

    @Test
    void topologyRejectsJumpWithoutDirectConnection() {
        WorldState state = worldState();
        Map<StarSystemId, SimulationSession> sessions = restoreSessions(state);
        FleetWorldService fleets = new FleetWorldService(
                sessions, state.nextFleetIdValue(), state.fleets());
        FleetJumpService jumps = new FleetJumpService(
                state.topology(), sessions, fleets, JumpTransitTiming.DEFAULT, List.of());
        FleetPlacementState source = fleets.snapshots().stream()
                .filter(placement -> ALPHA.equals(placement.systemId()))
                .findFirst().orElseThrow();

        assertThrows(IllegalArgumentException.class,
                () -> jumps.requestJump(source.id(), G