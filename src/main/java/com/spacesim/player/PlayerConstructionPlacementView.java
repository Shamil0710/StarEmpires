package com.spacesim.player;

import com.spacesim.world.StarSystemId;

import java.util.Objects;

/**
 * Read-only result of authoritative player construction placement validation.
 *
 * @param systemId target local system
 * @param x requested local X
 * @param y requested local Y
 * @param allowed whether project authoring is currently allowed
 * @param rejection stable rejection reason, {@link ConstructionPlacementRejection#NONE} when allowed
 */
public record PlayerConstructionPlacementView(
        StarSystemId systemId,
        float x,
        float y,
        boolean allowed,
        ConstructionPlacementRejection rejection) {

    /**
     * Validates one placement result.
     *
     * @param systemId target local system
     * @param x requested local X
     * @param y requested local Y
     * @param allowed whether authoring is allowed
     * @param rejection stable rejection reason
     */
    public PlayerConstructionPlacementView {
        Objects.requireNonNull(systemId, "Construction placement system not set");
        Objects.requireNonNull(rejection, "Construction placement rejection not set");
        if (allowed != (rejection == ConstructionPlacementRejection.NONE)) {
            throw new IllegalArgumentException("Construction placement allowed/rejection mismatch");
        }
    }
}
