package com.spacesim.components;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketComponentProvenanceTest {
    @Test
    void configuredTargetStartsAsBaselineAndSurvivesEffectivePolicyChange() {
        MarketComponent market = new MarketComponent();

        market.configureTradableItem(2, 25, 0.5f);
        assertEquals(25, market.configuredTargetStock[2]);
        assertEquals(25, market.targetStock[2]);
        assertTrue(market.isTradable(2));

        market.targetStock[2] = 80;
        assertEquals(25, market.configuredTargetStock[2],
                "Changing effective strategic demand must not rewrite station baseline");
        assertEquals(80, market.targetStock[2]);

        market.disableItemTrading(2);
        assertEquals(0, market.configuredTargetStock[2]);
        assertEquals(0, market.targetStock[2]);
        assertFalse(market.isTradable(2));
    }
}
