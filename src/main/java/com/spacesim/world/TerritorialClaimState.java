package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent political claim of one faction to one star system.
 *
 * <p>The claimant is implicit in the enclosing {@link FactionStrategicState}. A claim never grants
 * sovereignty by itself. Stabilization progress is accumulated only by the ordinary territorial
 * process from qualifying physical evidence and the authoritative world tick.</p>
 *
 * @param systemId claimed star system
 * @param declaredTick authoritative world tick when the claim was declared
 * @param lastEvaluatedTick last authoritative world tick included in stabilization
 * @param stabilizationTicks accumulated qualifying stabilization time
 * @param status current deterministic claim state
 */
public record TerritorialClaimState(
        StarSystemId systemId,
        long declaredTick,
        long lastEvaluatedTick,
        long stabilizationTicks,
        Status status) implements Comparable<TerritorialClaimState> {

    /** Persistent lifecycle state of a territorial claim. */
    public enum Status {
        /** Political claim exists but currently lacks enough physical support to stabilize. */
        ACTIVE,
        /** Claim currently has sufficient uncontested physical evidence and is accumulating time. */
        STABILIZING,
        /** Another incompatible claim/controller has material evidence in the same system. */
        CONTESTED,
        /** The claimant has completed stabilization and currently controls the system. */
        ESTABLISHED
    }

    /**
     * Validates monotonic authoritative ticks and non-negative progress.
     *
     * @param systemId claimed star system
     * @param declaredTick authoritative world tick when the claim was declared
     * @param lastEvaluatedTick last authoritative world tick included in stabilization
     * @param stabilizationTicks accumulated qualifying stabilization time
     * @param status current deterministic claim state
     */
    public TerritorialClaimState {
        systemId = Objects.requireNonNull(systemId, "Territorial claim StarSystemId not set");
        status = Objects.requireNonNull(status, "Territorial claim status not set");
        if (declaredTick < 0L || lastEvaluatedTick < declaredTick || stabilizationTicks < 0L) {
            throw new IllegalArgumentException("Territorial claim ticks/progress are invalid");
        }
    }

    /** @param other another claim @return deterministic system ordering */
    @Override
    public int compareTo(TerritorialClaimState other) {
        return systemId.compareTo(Objects.requireNonNull(other, "Territorial claim not set").systemId);
    }
}
