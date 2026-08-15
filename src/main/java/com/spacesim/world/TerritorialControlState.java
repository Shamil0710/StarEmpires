package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent maintenance clock for established territorial control.
 *
 * <p>The controlling faction is implicit in the enclosing {@link FactionStrategicState}. The state
 * stores only temporal evidence needed for deterministic loss of control; the authoritative list of
 * controlled systems remains {@link FactionStrategicState#controlledSystems()} for compatibility
 * with existing fiscal, routing and policy code.</p>
 *
 * @param systemId controlled star system
 * @param establishedTick authoritative world tick when this control period began
 * @param lastEvaluatedTick last authoritative world tick included in maintenance evaluation
 * @param unsupportedTicks accumulated time without sufficient uncontested control evidence
 */
public record TerritorialControlState(
        StarSystemId systemId,
        long establishedTick,
        long lastEvaluatedTick,
        long unsupportedTicks) implements Comparable<TerritorialControlState> {

    /**
     * Validates monotonic control-maintenance clocks.
     *
     * @param systemId controlled star system
     * @param establishedTick authoritative world tick when this control period began
     * @param lastEvaluatedTick last authoritative world tick included in maintenance evaluation
     * @param unsupportedTicks accumulated time without sufficient uncontested control evidence
     */
    public TerritorialControlState {
        systemId = Objects.requireNonNull(systemId, "Territorial control StarSystemId not set");
        if (establishedTick < 0L || lastEvaluatedTick < establishedTick || unsupportedTicks < 0L) {
            throw new IllegalArgumentException("Territorial control ticks are invalid");
        }
    }

    /** @param other another control state @return deterministic system ordering */
    @Override
    public int compareTo(TerritorialControlState other) {
        return systemId.compareTo(Objects.requireNonNull(other, "Territorial control state not set").systemId);
    }
}
