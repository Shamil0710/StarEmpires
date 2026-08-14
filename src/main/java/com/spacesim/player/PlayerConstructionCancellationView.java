package com.spacesim.player;

import com.spacesim.world.ConstructionProjectId;

import java.util.Objects;

/**
 * Read-only Stage-16 cancellation decision for one player construction project.
 *
 * @param projectId inspected construction project
 * @param allowed whether cancellation is currently authoritative and safe
 * @param rejection stable rejection code, or {@link PlayerConstructionCancellationRejection#NONE}
 * @param refundableMilliCredits complete live site liquidity that would return to the player
 */
public record PlayerConstructionCancellationView(
        ConstructionProjectId projectId,
        boolean allowed,
        PlayerConstructionCancellationRejection rejection,
        long refundableMilliCredits) {

    /**
     * Validates one immutable cancellation view.
     *
     * @param projectId inspected construction project
     * @param allowed whether cancellation is currently authoritative and safe
     * @param rejection stable rejection code
     * @param refundableMilliCredits non-negative complete live site liquidity
     */
    public PlayerConstructionCancellationView {
        Objects.requireNonNull(projectId, "ConstructionProjectId not set");
        Objects.requireNonNull(rejection, "Cancellation rejection not set");
        if (refundableMilliCredits < 0L) {
            throw new IllegalArgumentException("Refundable construction money cannot be negative");
        }
        if (allowed != (rejection == PlayerConstructionCancellationRejection.NONE)) {
            throw new IllegalArgumentException("Cancellation allowed flag must match rejection code");
        }
    }
}
