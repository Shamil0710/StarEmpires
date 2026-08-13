package com.spacesim.components;

import com.badlogic.ashley.core.Component;
import com.spacesim.constants.Constants;

/**
 * Runtime-derived fixed purchase bids for a special physical market consumer.
 *
 * <p>The component affects only prices paid to incoming sellers. Construction sites derive every
 * bid from persistent requirements and project funding, so this runtime component does not require
 * an independent persistence schema.</p>
 */
public final class ProcurementPolicyComponent implements Component {
    private final float[] buyPrices = new float[Constants.MAX_ITEMS];

    /** Creates an initially unconfigured fixed-bid policy. */
    public ProcurementPolicyComponent() {
    }

    /**
     * Configures the fixed bid of one runtime item.
     *
     * @param itemId runtime item identifier
     * @param buyPrice positive finite purchase price per physical unit
     * @throws IllegalArgumentException if the item or price is invalid
     */
    public void configureBuyPrice(int itemId, float buyPrice) {
        if (itemId < 0 || itemId >= buyPrices.length) {
            throw new IllegalArgumentException("Procurement item id is outside runtime capacity");
        }
        if (!Float.isFinite(buyPrice) || buyPrice <= 0f) {
            throw new IllegalArgumentException("Procurement buy price must be finite and positive");
        }
        buyPrices[itemId] = buyPrice;
    }

    /**
     * Returns the configured fixed bid.
     *
     * @param itemId runtime item identifier
     * @return positive fixed bid, or zero for an unconfigured/invalid item
     */
    public float buyPrice(int itemId) {
        return itemId < 0 || itemId >= buyPrices.length ? 0f : buyPrices[itemId];
    }
}
