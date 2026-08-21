package com.spacesim.navigation;

import java.util.Objects;

/**
 * Serializable-friendly domain object representing one ship jump operation.
 * No time is advanced internally; simulation time remains authoritative.
 */
public final class JumpTransitOperation {
    private final String operationId;
    private final String vesselId;
    private final JumpConnection connection;
    private final long startTimeSeconds;
    private final long completionTimeSeconds;

    private TravelState state;

    public JumpTransitOperation(
            String operationId,
            String vesselId,
            JumpConnection connection,
            long startTimeSeconds) {
        this.operationId = Objects.requireNonNull(operationId);
        this.vesselId = Objects.requireNonNull(vesselId);
        this.connection = Objects.requireNonNull(connection);
        if (startTimeSeconds < 0) {
            throw new IllegalArgumentException("start time cannot be negative");
        }
        this.startTimeSeconds = startTimeSeconds;
        this.completionTimeSeconds = startTimeSeconds + connection.transitTimeSeconds();
        this.state = TravelState.PREPARING_JUMP;
    }

    public String operationId() { return operationId; }
    public String vesselId() { return vesselId; }
    public JumpConnection connection() { return connection; }
    public long startTimeSeconds() { return startTimeSeconds; }
    public long completionTimeSeconds() { return completionTimeSeconds; }
    public TravelState state() { return state; }

    public void beginTransit() {
        requireState(TravelState.PREPARING_JUMP);
        state = TravelState.TRANSIT;
    }

    public void update(long simulationTimeSeconds) {
        if (state == TravelState.TRANSIT && simulationTimeSeconds >= completionTimeSeconds) {
            state = TravelState.COOLDOWN;
        }
    }

    public void arrive() {
        requireState(TravelState.COOLDOWN);
        state = TravelState.ARRIVED;
    }

    public void cancel() {
        if (state == TravelState.ARRIVED) {
            throw new IllegalStateException("arrived operation cannot be cancelled");
        }
        state = TravelState.CANCELLED;
    }

    private void requireState(TravelState expected) {
        if (state != expected) {
            throw new IllegalStateException("expected " + expected + " but was " + state);
        }
    }
}
