package com.spacesim.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.TreeSet;

/** Deterministic bootstrap for Stage-21A living actor lifecycle state. */
public final class FactionLivingActorBootstrap {
    private FactionLivingActorBootstrap() {
        throw new AssertionError("Utility class");
    }

    /**
     * Creates exactly one living-actor state per supplied autonomous faction and deterministically
     * staggers first review deadlines across the cadence window.
     *
     * @param autonomousFactionContentIds stable faction IDs explicitly authorized for autonomy
     * @param startTick earliest possible first review tick
     * @param cadenceTicks positive medium-review cadence
     * @return immutable states in stable faction-ID order
     */
    public static List<FactionLivingActorState> bootstrap(
            Collection<String> autonomousFactionContentIds,
            long startTick,
            long cadenceTicks) {
        Objects.requireNonNull(autonomousFactionContentIds, "Autonomous faction IDs not set");
        if (startTick < 0L) {
            throw new IllegalArgumentException("Bootstrap start tick cannot be negative");
        }
        if (cadenceTicks <= 0L) {
            throw new IllegalArgumentException("Review cadence must be positive");
        }
        TreeSet<String> ids = new TreeSet<>();
        for (String rawId : autonomousFactionContentIds) {
            String factionId = requireText(rawId, "Autonomous faction ID");
            ids.add(factionId);
        }
        ArrayList<FactionLivingActorState> result = new ArrayList<>(ids.size());
        for (String factionId : ids) {
            long offset = Long.remainderUnsigned(stableHash(factionId), cadenceTicks);
            result.add(FactionLivingActorState.initial(factionId, Math.addExact(startTick, offset)));
        }
        return List.copyOf(result);
    }

    private static long stableHash(String value) {
        long hash = 0xcbf29ce484222325L;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label + " not set").strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
