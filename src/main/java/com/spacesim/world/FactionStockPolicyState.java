package com.spacesim.world;

import java.util.Objects;

/**
 * Persistent floor целевого запаса одного товара для рынков faction.
 *
 * @param itemContentId stable item content ID
 * @param targetStockFloor минимальный target stock на подходящем market
 */
public record FactionStockPolicyState(
        String itemContentId,
        int targetStockFloor) implements Comparable<FactionStockPolicyState> {

    /**
     * Валидирует stable ID и положительный demand floor.
     *
     * @param itemContentId stable item content ID
     * @param targetStockFloor строго положительный target stock floor
     */
    public FactionStockPolicyState {
        itemContentId = Objects.requireNonNull(itemContentId, "Item content ID stock policy не задан").strip();
        if (itemContentId.isEmpty()) {
            throw new IllegalArgumentException("Item content ID stock policy не может быть пустым");
        }
        if (targetStockFloor <= 0) {
            throw new IllegalArgumentException("Target stock floor должен быть положительным");
        }
    }

    /** @param other другая policy @return lexical ordering по stable item ID */
    @Override
    public int compareTo(FactionStockPolicyState other) {
        return itemContentId.compareTo(
                Objects.requireNonNull(other, "FactionStockPolicyState не задан").itemContentId);
    }
}
