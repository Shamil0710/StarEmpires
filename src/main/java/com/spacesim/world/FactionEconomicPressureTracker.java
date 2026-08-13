package com.spacesim.world;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class FactionEconomicPressureTracker {
    private final Map<Key, FactionEconomicPressureState> states = new HashMap<>();

    FactionEconomicPressureTracker(List<FactionEconomicPressureState> initialStates) {
        for (FactionEconomicPressureState state : Objects.requireNonNull(initialStates, "Pressure state list не задан")) {
            Key key = Key.of(state);
            if (states.put(key, state) != null) {
                throw new IllegalArgumentException("Duplicate pressure state: " + key);
            }
        }
    }

    void observe(
            List<FactionStrategicState> strategies,
            EconomicBottleneckReport report,
            long tick) {
        Objects.requireNonNull(strategies, "Faction strategies не заданы");
        Objects.requireNonNull(report, "Bottleneck report не задан");
        if (tick < 0L) {
            throw new IllegalArgumentException("Pressure observation tick не может быть отрицательным");
        }

        Map<StarSystemId, List<EconomicBottleneck>> bySystem = new HashMap<>();
        for (EconomicBottleneck bottleneck : report.bottlenecks()) {
            bySystem.computeIfAbsent(bottleneck.systemId(), ignored -> new ArrayList<>()).add(bottleneck);
        }

        Set<Key> observed = new HashSet<>();
        for (FactionStrategicState strategy : strategies) {
            for (StarSystemId systemId : strategy.controlledSystems()) {
                for (EconomicBottleneck bottleneck : bySystem.getOrDefault(systemId, List.of())) {
                    Key key = new Key(strategy.factionContentId(), systemId, bottleneck.itemContentId());
                    observed.add(key);
                    states.put(key, positiveObservation(states.get(key), strategy.factionContentId(), bottleneck, tick));
                }
            }
        }

        for (Map.Entry<Key, FactionEconomicPressureState> entry : new ArrayList<>(states.entrySet())) {
            Key key = entry.getKey();
            FactionEconomicPressureState previous = entry.getValue();
            if (!observed.contains(key) && isStillControlled(strategies, key.factionId(), key.systemId())) {
                states.put(key, clearObservation(previous, tick));
            }
        }
    }

    void markInvestment(String factionId, StarSystemId systemId, String itemId, long tick, long cooldownTicks) {
        if (tick < 0L || cooldownTicks <= 0L) {
            throw new IllegalArgumentException("Investment tick/cooldown некорректны");
        }
        Key key = new Key(factionId, systemId, itemId);
        FactionEconomicPressureState current = states.get(key);
        if (current == null) {
            throw new IllegalArgumentException("Investment pressure state отсутствует: " + key);
        }
        long cooldownUntil = tick > Long.MAX_VALUE - cooldownTicks ? Long.MAX_VALUE : tick + cooldownTicks;
        states.put(key, new FactionEconomicPressureState(
                current.factionContentId(), current.systemId(), current.itemContentId(), current.bottleneckType(),
                current.firstObservedTick(), current.lastObservedTick(), current.consecutiveObservations(),
                current.peakUnmetDemandUnits(), current.lastUnmetDemandUnits(), cooldownUntil));
    }

    FactionEconomicPressureState find(String factionId, StarSystemId systemId, String itemId) {
        return states.get(new Key(factionId, systemId, itemId));
    }

    List<FactionEconomicPressureState> snapshots() {
        List<FactionEconomicPressureState> result = new ArrayList<>(states.values());
        result.sort(FactionEconomicPressureState::compareTo);
        return List.copyOf(result);
    }

    private static FactionEconomicPressureState positiveObservation(
            FactionEconomicPressureState previous,
            String factionId,
            EconomicBottleneck bottleneck,
            long tick) {
        boolean continuation = previous != null
                && previous.consecutiveObservations() > 0
                && previous.bottleneckType() == bottleneck.type()
                && tick >= previous.lastObservedTick();
        long firstTick = continuation ? previous.firstObservedTick() : tick;
        int consecutive = continuation && previous.consecutiveObservations() < Integer.MAX_VALUE
                ? previous.consecutiveObservations() + 1
                : 1;
        long peak = continuation
                ? Math.max(previous.peakUnmetDemandUnits(), bottleneck.unmetDemandUnits())
                : bottleneck.unmetDemandUnits();
        long cooldown = previous == null ? 0L : previous.cooldownUntilTick();
        return new FactionEconomicPressureState(
                factionId,
                bottleneck.systemId(),
                bottleneck.itemContentId(),
                bottleneck.type(),
                firstTick,
                tick,
                consecutive,
                peak,
                bottleneck.unmetDemandUnits(),
                cooldown);
    }

    private static FactionEconomicPressureState clearObservation(
            FactionEconomicPressureState previous, long tick) {
        return new FactionEconomicPressureState(
                previous.factionContentId(), previous.systemId(), previous.itemContentId(), previous.bottleneckType(),
                tick, tick, 0, 0L, 0L, previous.cooldownUntilTick());
    }

    private static boolean isStillControlled(
            List<FactionStrategicState> strategies, String factionId, StarSystemId systemId) {
        for (FactionStrategicState strategy : strategies) {
            if (strategy.factionContentId().equals(factionId) && strategy.controlledSystems().contains(systemId)) {
                return true;
            }
        }
        return false;
    }

    private record Key(String factionId, StarSystemId systemId, String itemId) {
        private Key {
            if (factionId == null || factionId.isBlank() || systemId == null || itemId == null || itemId.isBlank()) {
                throw new IllegalArgumentException("Pressure key должен быть полностью задан");
            }
            factionId = factionId.strip();
            itemId = itemId.strip();
        }

        static Key of(FactionEconomicPressureState state) {
            return new Key(state.factionContentId(), state.systemId(), state.itemContentId());
        }
    }
}
