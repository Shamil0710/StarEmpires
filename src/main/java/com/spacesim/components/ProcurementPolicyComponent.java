package com.spacesim.components;

import com.badlogic.ashley.core.Component;

/**
 * Runtime-derived policy for a market that posts a fixed purchase bid relative to item base price.
 *
 * <p>The component deliberately affects only the price paid by the market to incoming sellers.
 * Construction sites derive it from persistent construction requirements and project funding, so
 * the component itself does not need an independent persistence schema.</p>
 */
public final class ProcurementPolicyComponent implements Component {
    private final float buyPriceMultiplier;

    /**
     * Creates a fixed procurement bid policy.
     *
     * @param buyPriceMultiplier positive finite multiplier applied to the catalog item base price
     * @throws IllegalArgumentException if the multiplier is not finite and positive
     */
    public ProcurementPolicyComponent(float buyPriceMultiplier) {
        if (!Float.isFinite(buyPriceMultiplier) || buyPriceMultiplier <= 0f) {
            throw new IllegalArgumentException("Procurement buy-price multiplier must be finite and positive");
        }
        this.buyPriceMultiplier = buyPriceMultiplier;
    }

    /**
     * @return positive multiplier used for the market buy offer
     */
    public float buyPriceMultiplier() {
        return buyPriceMultiplier;
    }
}
