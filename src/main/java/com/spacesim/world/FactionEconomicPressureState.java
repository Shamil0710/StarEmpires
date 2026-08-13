package com.spacesim.world;

/**
 * Persistent hysteresis state for one faction observing one system-item bottleneck.
 *
 * @param factionContentId observing faction
 * @param systemId observed system
 * @param itemContentId observed item
 * @param bottleneckType last observed physical cause
 * @param firstObservedTick first tick of the current uninterrupted pressure episode
 * @param lastObservedTick most recent evaluation tick
 * @param consecutiveObservations current uninterrupted positive observations
 * @param peakUnmetDemandUnits peak unmet demand in the current episode
 * @param lastUnmetDemandUnits most recently observed unmet demand, or zero when clear
 * @param cooldownUntilTick earliest tick when another investment may be initiated
 */
public record FactionEconomicPressureState(
        String factionContentId,
        StarSystemId systemId,
        String itemContentId,
        EconomicBottleneckType bottleneckType,
        long firstObservedTick,
        long lastObservedTick,
        int consecutiveObservations,
        long peakUnmetDemandUnits,
        long lastUnmetDemandUnits,
        long cooldownUntilTick) implements Comparable<FactionEconomicPressureState> {

    /**
     * Validates non-negative counters and stable identifiers.
     *
     * @param factionContentId observing faction
     * @param systemId observed system
     * @param itemContentId observed item
     * @param bottleneckType last observed physical cause
     * @param firstObservedTick first tick of the current uninterrupted pressure episode
     * @param lastObservedTick most recent evaluation tick
     * @param consecutiveObservations current uninterrupted positive observations
     * @param peakUnmetDemandUnits peak unmet demand in the current episode
     * @param lastUnmetDemandUnits most recently observed unmet demand, or zero when clear
     * @param cooldownUntilTick earliest tick when another investment may be initiated
     */
    public FactionEconomicPressureState {
        if (factionContentId == null || factionContentId.isBlank()
                || itemContentId == null || itemContentId.isBlank()
                || systemId == null || bottleneckType == null) {
            throw new IllegalArgumentException("Economic pressure IDs/type должны быть заданы");
        }
        if (firstObservedTick < 0L || lastObservedTick < 0L || cooldownUntilTick < 0L
                || consecutiveObservations < 0 || peakUnmetDemandUnits < 0L || lastUnmetDemandUnits < 0L) {
            throw new IllegalArgumentException("Economic pressure counters/ticks не могут быть отрицательными");
        }
        if (firstObservedTick > lastObservedTick) {
            throw new IllegalArgumentException("Economic pressure first tick не может быть позже last tick");
        }
        factionContentId = factionContentId.strip();
        itemContentId = itemContentId.strip();
    }

    /** {@inheritDoc} */
    @Override
    public int compareTo(FactionEconomicPressureState other) {
        int faction = factionContentId.compareTo(other.factionContentId);
        if (faction != 0) {
            return faction;
        }
        int system = systemId.compareTo(other.systemId);
        return system != 0 ? system : itemContentId.compareTo(other.itemContentId);
    }
}
