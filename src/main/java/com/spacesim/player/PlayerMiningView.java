package com.spacesim.player;

import com.spacesim.components.MiningCommandComponent;
import com.spacesim.persistence.EntityId;

/**
 * Read-only manual-mining snapshot for HUD/test adapters.
 *
 * <p>Nullable target details mean that the selected persistent ID is absent or no longer resolves
 * to a live asteroid. This view never mutates simulation state.</p>
 *
 * @param status most recent readable command/equipment status
 * @param targetId selected asteroid persistent ID, or {@code null}
 * @param miningRequested whether continuous extraction is currently requested
 * @param resourceItem configured runtime item ID, or {@code -1} when no mining equipment exists
 * @param cargoUnits current units of the configured resource aboard the real ship
 * @param freeCargoCapacity current total free capacity of the real ship inventory
 * @param targetRemainingResource live finite target reserve, or {@code null} when unavailable
 * @param targetDistance physical distance to the selected target, or {@code null} when unavailable
 * @param extractionRange configured physical extraction range, or {@code 0} when unavailable
 * @param extractedLastTick whole units physically transferred during the most recent fixed tick
 */
public record PlayerMiningView(
        MiningCommandComponent.Status status,
        EntityId targetId,
        boolean miningRequested,
        int resourceItem,
        int cargoUnits,
        int freeCargoCapacity,
        Long targetRemainingResource,
        Float targetDistance,
        float extractionRange,
        int extractedLastTick) {
}
