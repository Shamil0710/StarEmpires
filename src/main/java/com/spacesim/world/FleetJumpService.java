package com.spacesim.world;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.EngineeringComponent;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.ship.ShipEngineeringRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.ship.ShipEngineeringRuntime.OperatingCommand;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.simulation.SimulationSession;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Authoritative Stage-10B/17.5C controller for direct jump phase transitions.
 *
 * <p>The service remains the single ordinary inter-system jump FSM. Fleets without a fitted
 * {@link EngineeringComponent} keep the historical Stage-10 timing path strictly as a migration
 * seam. Once a fleet has authoritative engineering state, FTL availability, spool time, translated
 * mass, shared-bus energy, jump heat, cooldown and edge-transit time come only from the fitted
 * Stage-17.5 capability.</p>
 *
 * <p>When an exact {@link FleetArrivalAuthority} supplies generated-world local geometry, the
 * existing {@link FleetJumpPhase#MOVING_TO_JUMP} phase also becomes the physical in-system approach
 * to the origin endpoint of the requested edge. This is not a second movement lifecycle: the same
 * persisted jump state owns approach timing and the same authority advances exact local kinematics.
 * A route such as A→B→C therefore arrives at B's A-facing endpoint and must physically cross B to
 * B's C-facing endpoint before spool and detached FTL transit can begin. The authority receives a
 * final fail-closed readiness check before physical FTL consequences are committed.</p>
 *
 * <p>Physical fleet ownership remains in {@link FleetWorldService}; entering
 * {@link FleetJumpPhase#IN_TRANSIT} delegates to the Stage-10A detach boundary and leaving it
 * delegates to the matching attach boundary. Physical FTL consequences are committed exactly once,
 * immediately before detach. Cancelling or failing before that boundary spends no jump energy.
 * Active fitted cooldowns progress through the same {@link ShipEngineeringRuntime} on authoritative
 * world time. A detached transit payload is caught up through that runtime before ordinary attach,
 * so route time cools hardware without creating a second timer or mutable transit authority.</p>
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
    private final FittedJumpResolver fittedJumpResolver;
    private final Map<FleetId, FleetJumpState> jumpsByFleetId = new HashMap<>();
    private long lastEngineeringWorldTick;
    private FleetArrivalAuthority arrivalAuthority;

    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            List<FleetJumpState> initialStates) {
        this(
                topology,
                sessionsById,
                fleetWorldService,
                timing,
                new ProductionFittedJumpResolver(),
                initialStates);
    }

    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            ShipEngineeringRuntime engineeringRuntime,
            List<FleetJumpState> initialStates) {
        this(
                topology,
                sessionsById,
                fleetWorldService,
                timing,
                runtimeResolver(Objects.requireNonNull(engineeringRuntime, "ShipEngineeringRuntime не задан")),
                initialStates);
    }

    FleetJumpService(
            GalaxyTopology topology,
            Map<StarSystemId, SimulationSession> sessionsById,
            FleetWorldService fleetWorldService,
            JumpTransitTiming timing,
            FittedJumpResolver fittedJumpResolver,
            List<FleetJumpState> initialStates) {
        this.topology = Objects.requireNonNull(topology, "GalaxyTopology не задан");
        this.sessionsById = Map.copyOf(Objects.requireNonNull(sessionsById, "Simulation sessions не заданы"));
        this.fleetWorldService = Objects.requireNonNull(fleetWorldService, "FleetWorldService не задан");
        this.timing = Objects.requireNonNull(timing, "JumpTransitTiming не задан");
        this.fittedJumpResolver = Objects.requireNonNull(fittedJumpResolver, "FittedJumpResolver не задан");
        this.lastEngineeringWorldTick = this.sessionsById.values().stream()
                .mapToLong(session -> session.getClock().getTick())
                .max()
                .orElse(0L);
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
        FleetArrivalAuthority.ResolvedArrival exactArrival = arrivalAuthority == null
                ? null : requireExactArrival(origin, destination);

        Optional<FittedJump> fitted = fittedJump(id);
        if (fitted.isPresent() && !fitted.orElseThrow().plan().allowed()) {
            throw fittedJumpUnavailable(id, fitted.orElseThrow().plan());
        }

        float resolvedArrivalX = exactArrival == null
                ? LocalSystemCoordinates.resolveArrivalX(arrivalX, arrivalY)
                : exactArrival.legacyProjectionX();
        float resolvedArrivalY = exactArrival == null
                ? LocalSystemCoordinates.resolveArrivalY(arrivalX, arrivalY)
                : exactArrival.legacyProjectionY();
        long approachTicks = timing.approachTicks();
        if (arrivalAuthority != null) {
            float fixedStep = requireSession(origin).getClock().getFixedStepSeconds();
            long exactApproachTicks = arrivalAuthority.beginDepartureApproach(
                    id,
                    origin,
                    destination,
                    placement.localEntityId(),
                    worldTick,
                    fixedStep);
            if (exactApproachTicks < 0L) {
                throw new IllegalStateException("FleetArrivalAuthority returned negative departure approach ticks");
            }
            if (exactApproachTicks > 0L) {
                approachTicks = exactApproachTicks;
            }
        }
        long endTick = addTicks(worldTick, approachTicks);
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
        long previousWorldTick = lastEngineeringWorldTick;
        advanceActiveFittedCooldowns(worldTick);
        List<FleetId> order = new ArrayList<>(jumpsByFleetId.keySet());
        order.sort(FleetId::compareTo);
        for (FleetId fleetId : order) {
            FleetJumpState state = jumpsByFleetId.get(fleetId);
            if (state != null
                    && state.phase() == FleetJumpPhase.MOVING_TO_JUMP
                    && arrivalAuthority != null) {
                advanceExactDeparture(state, previousWorldTick, worldTick);
            }
            while (state != null && worldTick >= state.phaseEndsTick()) {
                state = transition(state);
            }
        }
    }

    Optional<FleetJumpState> find(FleetId fleetId) {
        return Optional.ofNullable(fleetId == null ? null : jumpsByFleetId.get(fleetId));
    }

    void bindArrivalAuthority(FleetArrivalAuthority authority) {
        FleetArrivalAuthority checked = Objects.requireNonNull(authority, "arrivalAuthority");
        if (arrivalAuthority != null) {
            throw new IllegalStateException("Fleet arrival authority is already bound");
        }
        arrivalAuthority = checked;
    }

    List<FleetJumpState> snapshots() {
        List<FleetJumpState> states = new ArrayList<>(jumpsByFleetId.values());
        states.sort(FleetJumpState::compareTo);
        return List.copyOf(states);
    }

    boolean remove(FleetId fleetId) {
        return fleetId != null && jumpsByFleetId.remove(fleetId) != null;
    }

    private void advanceExactDeparture(
            FleetJumpState state,
            long previousWorldTick,
            long worldTick) {
        FleetPlacementState placement = fleetWorldService.find(state.fleetId()).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !placement.systemId().equals(state.originSystemId())) {
            throw new IllegalStateException("Exact departure approach requires fleet in origin system");
        }
        arrivalAuthority.advanceDepartureApproach(
                state.fleetId(),
                state.originSystemId(),
                state.destinationSystemId(),
                placement.localEntityId(),
                previousWorldTick,
                worldTick,
                state.phaseEndsTick(),
                requireSession(state.originSystemId()).getClock().getFixedStepSeconds());
    }

    private FleetJumpState transition(FleetJumpState state) {
        long boundary = state.phaseEndsTick();
        return switch (state.phase()) {
            case MOVING_TO_JUMP -> {
                Optional<FittedJump> fitted = fittedJump(state.fleetId());
                if (fitted.isPresent() && !fitted.orElseThrow().plan().allowed()) {
                    yield cancel(state);
                }
                long pendingTicks = fitted.isPresent()
                        ? secondsToTicks(
                                fitted.orElseThrow().plan().spoolSeconds(),
                                fitted.orElseThrow().fixedStepSeconds())
                        : timing.pendingTicks();
                yield replace(
                        state,
                        state.next(
                                FleetJumpPhase.JUMP_PENDING,
                                boundary,
                                addTicks(boundary, pendingTicks)));
            }
            case JUMP_PENDING -> {
                FleetPlacementState readyPlacement = fleetWorldService.find(state.fleetId()).orElseThrow();
                if (arrivalAuthority != null) {
                    if (readyPlacement.locationKind() != FleetLocationKind.IN_SYSTEM
                            || !state.originSystemId().equals(readyPlacement.systemId())) {
                        throw new IllegalStateException("FTL readiness check requires fleet in origin system");
                    }
                    arrivalAuthority.validateDepartureReady(
                            state.fleetId(),
                            state.originSystemId(),
                            state.destinationSystemId(),
                            readyPlacement.localEntityId());
                }

                Optional<FittedJump> fitted = fittedJump(state.fleetId());
                if (fitted.isPresent() && !fitted.orElseThrow().plan().allowed()) {
                    yield cancel(state);
                }

                RuntimeState previousEngineeringState = null;
                if (fitted.isPresent()) {
                    FittedJump context = fitted.orElseThrow();
                    previousEngineeringState = context.component().runtimeState;
                    context.component().setRuntimeState(
                            fittedJumpResolver.commit(context.component(), context.plan()));
                }

                FleetPlacementState transit;
                try {
                    transit = fleetWorldService.beginTransfer(
                            state.fleetId(), state.destinationSystemId());
                } catch (RuntimeException | Error exception) {
                    if (fitted.isPresent()) {
                        fitted.orElseThrow().component().setRuntimeState(previousEngineeringState);
                    }
                    throw exception;
                }
                if (transit.locationKind() != FleetLocationKind.IN_TRANSIT) {
                    throw new IllegalStateException("Fleet detach не создал IN_TRANSIT placement");
                }
                if (arrivalAuthority != null) {
                    EntityId formerLocalEntityId = transit.transitState().entityState().id();
                    arrivalAuthority.onDeparted(
                            state.fleetId(), state.originSystemId(), formerLocalEntityId);
                }

                long transitTicks;
                if (fitted.isPresent()) {
                    FittedJump context = fitted.orElseThrow();
                    transitTicks = secondsToTicks(
                            context.plan().edgeTransitSeconds(),
                            context.fixedStepSeconds());
                } else {
                    transitTicks = timing.transitTicks(
                            topology,
                            state.originSystemId(),
                            state.destinationSystemId(),
                            requireSession(state.originSystemId()).getClock().getFixedStepSeconds());
                }
                yield replace(
                        state,
                        state.next(
                                FleetJumpPhase.IN_TRANSIT,
                                boundary,
                                addTicks(boundary, transitTicks)));
            }
            case IN_TRANSIT -> {
                RuntimeState transitAdvancedState = advanceDetachedTransitEngineering(state);
                FleetArrivalAuthority.ResolvedArrival exactArrival = arrivalAuthority == null
                        ? null
                        : requireExactArrival(
                                state.originSystemId(), state.destinationSystemId());
                FleetPlacementState arrived = fleetWorldService.completeTransfer(
                        state.fleetId(),
                        exactArrival == null ? state.arrivalX() : exactArrival.legacyProjectionX(),
                        exactArrival == null ? state.arrivalY() : exactArrival.legacyProjectionY());
                if (arrived.locationKind() != FleetLocationKind.IN_SYSTEM
                        || !state.destinationSystemId().equals(arrived.systemId())) {
                    throw new IllegalStateException("Fleet arrival materialized in wrong system");
                }
                if (transitAdvancedState != null) {
                    requireEngineeringComponent(arrived).setRuntimeState(transitAdvancedState);
                }
                if (exactArrival != null) {
                    arrivalAuthority.onArrived(
                            state.fleetId(), exactArrival, arrived.localEntityId());
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

    private void advanceActiveFittedCooldowns(long worldTick) {
        if (worldTick < lastEngineeringWorldTick) {
            throw new IllegalStateException("Engineering world tick moved backwards");
        }
        long elapsedTicks = worldTick - lastEngineeringWorldTick;
        if (elapsedTicks == 0L) {
            return;
        }
        double deltaSeconds = elapsedTicks * (double) fixedStepSeconds();
        if (!Double.isFinite(deltaSeconds) || deltaSeconds <= 0d) {
            throw new IllegalStateException("Engineering elapsed world time is invalid");
        }
        ArrayList<EngineeringAdvance> pending = new ArrayList<>();
        for (FleetPlacementState placement : fleetWorldService.snapshots()) {
            if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
                continue;
            }
            SimulationSession session = requireSession(placement.systemId());
            Entity entity = session.getEntityRegistry().find(placement.localEntityId());
            if (entity == null) {
                throw new IllegalStateException(
                        "Fleet placement references missing local entity: " + placement.id());
            }
            EngineeringComponent component = entity.getComponent(EngineeringComponent.class);
            if (!hasActiveFittedCooldown(component)) {
                continue;
            }
            RuntimeState next = Objects.requireNonNull(
                    fittedJumpResolver.advanceIdle(component, deltaSeconds),
                    "FittedJumpResolver returned null idle state");
            pending.add(new EngineeringAdvance(component, next));
        }
        for (EngineeringAdvance advance : pending) {
            advance.component().setRuntimeState(advance.nextState());
        }
        lastEngineeringWorldTick = worldTick;
    }

    private RuntimeState advanceDetachedTransitEngineering(FleetJumpState state) {
        FleetPlacementState placement = fleetWorldService.find(state.fleetId()).orElseThrow();
        if (placement.locationKind() != FleetLocationKind.IN_TRANSIT || placement.transitState() == null) {
            throw new IllegalStateException("Transit engineering catch-up requires detached fleet");
        }
        Entity detached = EntityStateMapper.restore(placement.transitState().entityState());
        EngineeringComponent component = detached.getComponent(EngineeringComponent.class);
        if (!hasActiveFittedCooldown(component)) {
            return null;
        }
        long elapsedTicks = state.phaseEndsTick() - state.phaseStartedTick();
        if (elapsedTicks <= 0L) {
            throw new IllegalStateException("Transit engineering interval must be positive");
        }
        double deltaSeconds = elapsedTicks * (double) fixedStepSeconds();
        return Objects.requireNonNull(
                fittedJumpResolver.advanceIdle(component, deltaSeconds),
                "FittedJumpResolver returned null transit idle state");
    }

    private EngineeringComponent requireEngineeringComponent(FleetPlacementState placement) {
        SimulationSession session = requireSession(placement.systemId());
        Entity entity = session.getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            throw new IllegalStateException("Arrived FleetId references missing local entity: " + placement.id());
        }
        EngineeringComponent component = entity.getComponent(EngineeringComponent.class);
        if (component == null) {
            throw new IllegalStateException("Arrived fitted FleetId lost EngineeringComponent: " + placement.id());
        }
        return component;
    }

    private static boolean hasActiveFittedCooldown(EngineeringComponent component) {
        if (component == null) {
            return false;
        }
        if (component.runtimeState == null) {
            throw new IllegalStateException("Fleet EngineeringComponent is missing runtime state");
        }
        return component.runtimeState.ftlCooldownSecondsByMount().values().stream()
                .anyMatch(value -> value > 0d);
    }

    private float fixedStepSeconds() {
        SimulationSession session = sessionsById.values().stream().findFirst().orElseThrow(
                () -> new IllegalStateException("Fleet jump service has no simulation sessions"));
        float step = session.getClock().getFixedStepSeconds();
        if (!Float.isFinite(step) || step <= 0f) {
            throw new IllegalStateException("Fleet jump fixed step must be positive and finite");
        }
        return step;
    }

    private Optional<FittedJump> fittedJump(FleetId fleetId) {
        FleetPlacementState placement = fleetWorldService.find(fleetId).orElseThrow(
                () -> new IllegalArgumentException("Unknown FleetId: " + fleetId));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM) {
            return Optional.empty();
        }
        SimulationSession session = requireSession(placement.systemId());
        Entity entity = session.getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            throw new IllegalStateException("Fleet placement references missing local entity: " + fleetId);
        }
        EngineeringComponent component = entity.getComponent(EngineeringComponent.class);
        if (component == null) {
            return Optional.empty();
        }
        if (component.fit == null || component.runtimeState == null || component.instanceState == null) {
            throw new IllegalStateException("Fleet EngineeringComponent is incomplete: " + fleetId);
        }
        JumpPlan plan = Objects.requireNonNull(
                fittedJumpResolver.plan(component), "FittedJumpResolver returned null plan");
        return Optional.of(new FittedJump(
                component,
                plan,
                session.getClock().getFixedStepSeconds()));
    }

    private FleetJumpState replace(FleetJumpState previous, FleetJumpState next) {
        if (!jumpsByFleetId.replace(previous.fleetId(), previous, next)) {
            throw new IllegalStateException("Jump state changed during deterministic transition: " + previous.fleetId());
        }
        return next;
    }

    private FleetArrivalAuthority.ResolvedArrival requireExactArrival(
            StarSystemId origin,
            StarSystemId destination) {
        FleetArrivalAuthority.ResolvedArrival result = Objects.requireNonNull(
                arrivalAuthority.resolve(origin, destination),
                "FleetArrivalAuthority returned null");
        if (!result.originSystemId().equals(origin)
                || !result.destinationSystemId().equals(destination)) {
            throw new IllegalArgumentException(
                    "fleet arrival authority resolved a different direct edge");
        }
        return result;
    }

    private FleetJumpState cancel(FleetJumpState state) {
        if (!jumpsByFleetId.remove(state.fleetId(), state)) {
            throw new IllegalStateException("Jump state changed during deterministic cancellation: " + state.fleetId());
        }
        return null;
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

    private static FittedJumpResolver runtimeResolver(ShipEngineeringRuntime runtime) {
        return new FittedJumpResolver() {
            @Override
            public JumpPlan plan(EngineeringComponent component) {
                if (component.instanceState == null) {
                    throw new IllegalStateException("Fleet EngineeringComponent is missing instance state");
                }
                return runtime.planJump(
                        component.fit,
                        component.runtimeState,
                        component.instanceState.damage().moduleDamage());
            }

            @Override
            public RuntimeState commit(EngineeringComponent component, JumpPlan plan) {
                return runtime.commitJump(component.runtimeState, plan);
            }

            @Override
            public RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds) {
                if (component.instanceState == null) {
                    throw new IllegalStateException("Fleet EngineeringComponent is missing instance state");
                }
                return runtime.advance(
                        component.fit,
                        component.runtimeState,
                        component.instanceState.damage().moduleDamage(),
                        OperatingCommand.idle(),
                        deltaSeconds).state();
            }
        };
    }

    private static IllegalStateException fittedJumpUnavailable(FleetId fleetId, JumpPlan plan) {
        return new IllegalStateException(
                "Fitted FTL jump unavailable for " + fleetId + ": " + plan.failure());
    }

    private static long secondsToTicks(double seconds, float fixedStepSeconds) {
        if (!Double.isFinite(seconds) || seconds <= 0d) {
            throw new IllegalArgumentException("Fitted jump duration должна быть положительной и конечной");
        }
        if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
            throw new IllegalArgumentException("Fixed step должен быть положительным и конечным");
        }
        double ticks = StrictMath.ceil(seconds / fixedStepSeconds);
        if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
            throw new IllegalArgumentException("Fitted jump duration не представима в long ticks");
        }
        return Math.max(1L, (long) ticks);
    }

    private static long addTicks(long start, long duration) {
        try {
            return Math.addExact(start, duration);
        } catch (ArithmeticException exception) {
            throw new IllegalStateException("Jump phase tick overflow", exception);
        }
    }

    /** Narrow Stage-17.5C dependency used to compose the already-tested engineering runtime with the world FSM. */
    interface FittedJumpResolver {
        JumpPlan plan(EngineeringComponent component);

        RuntimeState commit(EngineeringComponent component, JumpPlan plan);

        RuntimeState advanceIdle(EngineeringComponent component, double deltaSeconds);
    }

    private record FittedJump(
            EngineeringComponent component,
            JumpPlan plan,
            float fixedStepSeconds) {
        private FittedJump {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(plan, "plan");
            if (!Float.isFinite(fixedStepSeconds) || fixedStepSeconds <= 0f) {
                throw new IllegalArgumentException("fixedStepSeconds must be positive and finite");
            }
        }
    }

    private record EngineeringAdvance(EngineeringComponent component, RuntimeState nextState) {
        private EngineeringAdvance {
            Objects.requireNonNull(component, "component");
            Objects.requireNonNull(nextState, "nextState");
        }
    }
}
