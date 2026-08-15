package com.spacesim.trade;

import com.spacesim.constants.Constants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class Stage17DynamicFactionTradeProfileTest {
    @Test
    void acceptsDynamicRuntimeFactionSlotsAndFullReputationVector() {
        int dynamicFactionId = Constants.LEGACY_FACTION_COUNT;
        float[] reputation = new float[Constants.FACTION_RUNTIME_CAPACITY];
        reputation[dynamicFactionId] = 12.5f;
        reputation[Constants.FACTION_RUNTIME_CAPACITY - 1] = -7.25f;

        FleetTradeProfile profile = new FleetTradeProfile(
                0f, 0f, 10f, 1_000L, 10, 0, 10, -1, false, null,
                dynamicFactionId, new int[Constants.MAX_ITEMS], reputation);

        assertEquals(dynamicFactionId, profile.factionId());
        assertEquals(12.5f, profile.reputation(dynamicFactionId));
        assertEquals(-7.25f, profile.reputation(Constants.FACTION_RUNTIME_CAPACITY - 1));
    }

    @Test
    void rejectsFactionOutsideRuntimeCapacity() {
        float[] reputation = new float[Constants.FACTION_RUNTIME_CAPACITY];
        assertThrows(IllegalArgumentException.class, () -> new FleetTradeProfile(
                0f, 0f, 10f, 1_000L, 10, 0, 10, -1, false, null,
                Constants.FACTION_RUNTIME_CAPACITY, new int[Constants.MAX_ITEMS], reputation));
    }
}
