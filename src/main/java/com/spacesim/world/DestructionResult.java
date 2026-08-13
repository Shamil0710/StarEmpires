package com.spacesim.world;

import com.spacesim.persistence.EntityId;

/**
 * Immutable diagnostics of one completed destruction operation.
 *
 * @param destroyedEntityId removed target entity
 * @param salvageEntityId created salvage entity or {@code null}
 * @param destroyedResourceUnits units explicitly sunk
 * @param transferredResourceUnits units moved to salvage/recipient
 * @param sunkMoneyMilliCredits wallet money explicitly destroyed
 * @param transferredMoneyMilliCredits wallet money moved to faction treasury
 * @param removedMarket whether target provided a market
 * @param removedProduction whether target provided production capacity
 * @param failedConstructionProject project failed by site destruction or {@code null}
 */
public record DestructionResult(
        EntityId destroyedEntityId,
        EntityId salvageEntityId,
        long destroyedResourceUnits,
        long transferredResourceUnits,
        long sunkMoneyMilliCredits,
        long transferredMoneyMilliCredits,
        boolean removedMarket,
        boolean removedProduction,
        ConstructionProjectId failedConstructionProject) {
}
