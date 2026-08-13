package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent required/delivered amount of one construction material.
 *
 * @param itemContentId stable content item ID
 * @param requiredAmount strictly positive required amount
 * @param deliveredAmount delivered amount in range {@code [0, requiredAmount]}
 */
public record ConstructionMaterialState(
        String itemContentId,
        int requiredAmount,
        int deliveredAmount) implements Comparable<ConstructionMaterialState> {
    /** Validates and normalizes material state. */
    public ConstructionMaterialState {
        itemContentId = Objects.requireNonNull(itemContentId, "Construction item ID не задан").strip();
        if (itemContentId.isEmpty()) {
            throw new IllegalArgumentException("Construction item ID не должен быть пустым");
        }
        if (requiredAmount <= 0) {
            throw new IllegalArgumentException("Construction requiredAmount должен быть положительным");
        }
        if (deliveredAmount < 0 || deliveredAmount > requiredAmount) {
            throw new IllegalArgumentException("Construction deliveredAmount вне required range");
        }
    }

    /** @return remaining amount still required */
    public int remainingAmount() {
        return requiredAmount - deliveredAmount;
    }

    /** @return whether the requirement is fully delivered */
    public boolean fulfilled() {
        return deliveredAmount == requiredAmount;
    }

    @Override
    public int compareTo(ConstructionMaterialState other) {
        return itemContentId.compareTo(other.itemContentId);
    }
}
