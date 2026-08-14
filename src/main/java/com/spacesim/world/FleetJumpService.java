package com.spacesim.world;

import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative Stage-10B controller for direct jump phase transitions.
 *
 * <p>The service owns only active jump state. Physical fleet ownership remains in
 * {@link FleetWorldService}; entering {@link FleetJumpPhase#IN_TRANSIT} delegates to the Stage-10A
 * detach boundary and leaving it delegates to the matching attach boundary.</p>
 *
 * <p>Stage-10 callers historically used {@code (0,0)} as a placeholder arrival coordinate. The
 * current bounded local map treats that point as a viewport corner, so that legacy placeholder is
 * canonicalized to {@link LocalSystemCoordinates}' interior arrival anchor. Explicit non-zero
 * coordinates remain exact.</p>
 */
final class FleetJumpService {
    private final GalaxyTopology topology;
    private final Map<StarSystemId, SimulationSession> sessionsById;
    private final FleetWorldService fleetWorldService;
    private final JumpTransitTiming timing;
    private final Map<FleetId, FleetJumpState> jumpsByFleetId = new HashMap<>();

    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            List<FleetJumpState> initialStates) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        this.sessionsById = Map.copyOf(Objects.requireNonNull(sessionsById, "Simulation sessions не заданы"));
        this.fleetWorldService = Objects.requireNonNull(fleetWorldService, "FleetWorldService не задан");
        this.timing = Objects.requireNonNull(timing, "JumpTransitTiming не задан");
        for (FleetJumpState state : Objects.requireNonNull(initialStates, "Jump states не заданы")) {
            FleetJumpState checked = Objects.requireNonNull(state, "FleetJumpState не задан");
            if (jumpsByFleetId.putIfAbsent(checked.fleetId(), checked) != null) {
                throw new IllegalArgumentException("Duplicate active jump FleetId: " + checked.fleetId());
            }
            validateStateAgainstPlacement(checked);
        }
    }

    FleetJumpState requestJump(
            FleetId fleetId,
            StarSystemId destinationSystemId,
            long worldTick,
            float arrivalX,
            float arrivalY) {
        FleetId id = Objects.requireNonNull(fleetId, "FleetId jump request не задан");
        StarSystemId destination = Objects.requireNonNull(destinationSystemId, "Jump destination не задан");
        if (worldTick < 0L) {
            throw new IllegalArgumentException("World tick не может быть отрицательным");
        }
        if (!Float.isFinite(arrivalX) || !Float.isFinite(arrivalY)) {
            throw new IllegalArgumentException("Jump arrival coordinates должны быть конечными");
        }
        if (jumpsByFleetId.containsKey(id)) {
            throw new IllegalStateException("Fleet уже выполняет jump: " + id);
        }
        FleetPlacementState placement = fleetWorldService.find(id).orElseThrow(
                () -> new IllegalArgumentException("Unknown FleetId: " + id));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            throw new IllegalStateException("Jump request требует fleet в local StarSystem: " + id);
        }
        StarSystemId origin = placement.systemId();
        requireDirectConnection(origin, destination);

        float resolvedArrivalX = LocalSystemCoordinates.resolveArrivalX(arrivalX, arrivalY);
        float resolvedArrivalY = LocalSystemCoordinates.resolveArrivalY(arrivalX, arrivalY);
        long endTick = addTicks(worldTick, timing.approachTicks());
        FleetJumpState state = new FleetJumpState(
                id,
                FleetJumpPhase.MOVING_TO_JUMP,
                origin,
                destination,
                worldTick,
                endTick,
                resolvedArrivalX,
                resolvedArrivalY);
        jumpsByFleetId.put(id, state);
        return state;
    }

    void advance(long worldTick) {
        if (worldTick < 0L) {
            throw new IllegalArgumentException("World tick не может быть отрицательным");
        }
        List<FleetId> order = new ArrayList<>(jumpsByFleetId.keySet());
        order.sort(FleetId::compareTo);
        for (FleetId fleetId : order) {
            FleetJumpState state = jumpsByFleetId.get(fleetId);
            while (state != null && worldTick >= state.phaseEndsTick()) {
                state = transition(state);
            }
        }
    }

    Optional<FleetJumpState> find(FleetId fleetId) {
        return Optional.ofNullable(fleetId == null ? null : jumpsByFleetId.get(fleetId));
    }

    List<FleetJumpState> snapshots() {
        List<FleetJumpState> states = new ArrayList<>(jumpsByFleetId.values());
        states.sort(FleetJumpState::compareTo);
        return List.copyOf(states);
    }

    boolean remove(FleetId fleetId) {
        return fleetId != null && jumpsByFleetId.remove(fleetId) != null;
    }

    private FleetJumpState transition(FleetJumpState state) {
        long boundary = state.phaseEndsTick();
        return switch (state.phase()) {
            case MOVING_TO_JUMP -> replace(
                    state,
                    state.next(
                            FleetJumpPhase.JUMP_PENDING,
                            boundary,
                            addTicks(boundary, timing.pendingTicks())));
            case JUMP_PENDING -> {
                FleetPlacementState transit = fleetWorldService.beginTransfer(
                        state.fleetId(), state.destinationSystemId());
                if (transit.locationKind() != FleetLocationKind.IN_TRANSIT) {
                    throw new IllegalStateException("Fleet detach не создал IN_TRANSIT placement");
                }
                long transitTicks = timing.transitTicks(
                        topology,
                        state.originSystemId(),
                        state.destinationSystemId(),
                        requireSession(state.originSystemId()).getClock().getFixedStepSeconds());
                yield replace(
                        state,
                        state.next(
                                FleetJumpPhase.IN_TRANSIT,
                                boundary,
                                addTicks(boundary, transitTicks)));
            }
            case IN_TRANSIT -> {
                FleetPlacementState arrived = fleetWorldService.completeTransfer(
                        state.fleetId(), state.arrivalX(), state.arrivalY());
                if (arrived.locationKind() != FleetLocationKind.IN_SYSTEM
                        || !state.destinationSystemId().equals(arrived.systemId())) {
                    throw new IllegalStateException("Fleet arrival materialized in wrong system");
                }
                yield replace(
                        state,
                        state.next(
                                FleetJumpPhase.ARRIVING,
                                boundary,
                                addTicks(boundary, timing.arrivalTicks())));
            }
            case ARRIVING -> {
                jumpsByFleetId.remove(state.fleetId());
                yield null;
            }
        };
    }

    private FleetJumpState replace(FleetJumpState previous, FleetJumpState next) {
        if (!jumpsByFleetId.replace(previous.fleetId(), previous, next)) {
            throw new IllegalStateException("Jump state changed during deterministic transition: " + previous.fleetId());
        }
        return next;
    }

    private void validateStateAgainstPlacement(FleetJumpState state) {
        requireDirectConnection(state.originSystemId(), state.destinationSystemId());
        FleetPlacementState placement = fleetWorldService.find(state.fleetId()).orElseThrow(
                () -> new IllegalArgumentException("Jump state references unknown fleet: " + state.fleetId()));
        switch (state.phase()) {
            case MOVING_TO_JUMP, JUMP_PENDING -> {
                if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                        || !state.originSystemId().equals(placement.systemId())) {
                    throw new IllegalArgumentException("Pre-jump state requires fleet in origin system");
                }
            }
            case IN_TRANSIT -> {
                if (placement.locationKind() != FleetLocationKind.IN_TRANSIT
                        || placement.transitState() == null
                        || !state.originSystemId().equals(placement.transitState().originSystemId())
                        || !state.destinationSystemId().equals(placement.transitState().destinationSystemId())) {
                    throw new IllegalArgumentException("Transit jump state does not match physical placement");
                }
            }
            case ARRIVING -> {
                if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                        || !state.destinationSystemId().equals(placement.systemId())) {
                    throw new IllegalArgumentException("Arrival state requires fleet in destination system");
                }
            }
        }
    }

    private void requireDirectConnection(StarSystemId origin, StarSystemId destination) {
        if (topology.findSystem(origin).isEmpty() || topology.findSystem(destination).isEmpty()) {
            throw new IllegalArgumentException("Jump references unknown StarSystem");
        }
        if (!topology.neighbors(origin).contains(destination)) {
            throw new IllegalArgumentException("No direct jump connection: " + origin + " -> " + destination);
        }
    }

    private SimulationSession requireSession(StarSystemId systemId) {
        SimulationSession session = sessionsById.get(systemId);
        if (session == null) {
            throw new IllegalStateException("Missing SimulationSession for jump system: " + systemId);
        }
        return session;
    }

    private static long addTicks(long start, long duration) {
        try {
            return Math.addExact(start, duration);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Jump phase tick overflow", exception);
        }
    }
}
