package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent state of one active jump operation.
 *
 * <p>Physical placement remains authoritative in {@link FleetPlacementState}: local phases use an
 * {@code IN_SYSTEM} placement, while {@link FleetJumpPhase#IN_TRANSIT} requires the fleet to be
 * detached into the world-owned transit payload. Absolute phase ticks make save/load continuation
 * independent from render-frame partitioning.</p>
 *
 * @param fleetId stable world fleet identity
 * @param phase current jump phase
 * @param originSystemId jump origin
 * @param destinationSystemId direct jump destination
 * @param phaseStartedTick authoritative world tick at which the phase began
 * @param phaseEndsTick exclusive phase completion boundary
 * @param arrivalX destination-local arrival X
 * @param arrivalY destination-local arrival Y
 */
public record FleetJumpState(
        FleetId fleetId,
        FleetJumpPhase phase,
        StarSystemId originSystemId,
        StarSystemId destinationSystemId,
        long phaseStartedTick,
        long phaseEndsTick,
        float arrivalX,
        float arrivalY) implements Comparable<FleetJumpState> {
    /**
     * Validates persistent jump state invariants.
     *
     * @param fleetId stable world fleet identity
     * @param phase current jump phase
     * @param originSystemId jump origin
     * @param destinationSystemId direct jump destination
     * @param phaseStartedTick authoritative world tick at which the phase began
     * @param phaseEndsTick exclusive phase completion boundary
     * @param arrivalX destination-local arrival X
     * @param arrivalY destination-local arrival Y
     */
    public FleetJumpState {
        Objects.requireNonNull(fleetId, "FleetId jump state не задан");
        Objects.requireNonNull(phase, "Jump phase не задана");
        Objects.requireNonNull(originSystemId, "Jump origin не задан");
        Objects.requireNonNull(destinationSystemId, "Jump destination не задан");
        if (originSystemId.equals(destinationSystemId)) {
            throw new IllegalArgumentException("Jump должен менять StarSystem");
        }
        if (phaseStartedTick < 0L || phaseEndsTick <= phaseStartedTick) {
            throw new IllegalArgumentException("Jump phase tick range некорректен");
        }
        if (!Float.isFinite(arrivalX) || !Float.isFinite(arrivalY)) {
            throw new IllegalArgumentException("Jump arrival coordinates должны быть конечными");
        }
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(FleetJumpState other) {
        return fleetId.compareTo(other.fleetId);
    }

    FleetJumpState next(FleetJumpPhase nextPhase, long startTick, long endTick) {
        return new FleetJumpState(
                fleetId,
                nextPhase,
                originSystemId,
                destinationSystemId,
                startTick,
                endTick,
                arrivalX,
                arrivalY);
    }
}
