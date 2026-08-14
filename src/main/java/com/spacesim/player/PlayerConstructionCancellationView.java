package com.spacesim.player;

import com.spacesim.world.ConstructionProjectId;

import java.util.Objects;

/** Read-only Stage-16 cancellation decision for one player construction project. */
public record PlayerConstructionCancellationView(
        ConstructionProjectId projectId,
        boolean allowed,
        PlayerConstructionCancellationRejection rejection,
        long refundableMilliCredits) {

    /** Validates one immutable cancellation view. */
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
