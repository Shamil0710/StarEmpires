package com.spacesim.world;

import com.spacesim.components.FactionMarketAccessComponent;
import com.spacesim.components.ReputationComponent;
import com.spacesim.constants.Constants;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage17FactionRuntimeCapacityTest {
    @Test
    void runtimeCapacityIsIndependentFromLegacyAuthoredFactionCount() {
        assertEquals(3, Constants.LEGACY_FACTION_COUNT);
        assertEquals(Constants.LEGACY_FACTION_COUNT, Constants.MAX_FACTIONS);
        assertEquals(32, Constants.FACTION_RUNTIME_CAPACITY);
        assertTrue(Constants.FACTION_RUNTIME_CAPACITY > Constants.MAX_FACTIONS);
    }

    @Test
    void reputationAndMarketAccessSupportHighestRuntimeSlot() {
        int dynamicSlot = Constants.FACTION_RUNTIME_CAPACITY - 1;

        ReputationComponent reputation = new ReputationComponent();
        assertEquals(0f, reputation.getReputation(dynamicSlot), 0f);
        reputation.addReputation(dynamicSlot, 12.5f);
        assertEquals(12.5f, reputation.getReputation(dynamicSlot), 0f);

        FactionMarketAccessComponent access = new FactionMarketAccessComponent();
        assertFalse(access.canTrade(dynamicSlot));
        access.setFactionAllowed(dynamicSlot, true);
        assertTrue(access.canTrade(dynamicSlot));
        assertEquals(Constants.FACTION_RUNTIME_CAPACITY, access.copyAllowedFactionIds().length);
    }

    @Test
    void defaultResolverAllocatesFirstSlotAfterAuthoredCoreFactions() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(content, List.of());

        assertEquals(Constants.FACTION_RUNTIME_CAPACITY, resolver.runtimeSlotCapacity());
        WorldFactionIdentityState created = resolver.allocatePlayerCreated(
                "faction.player.runtime-capacity-test",
                "Runtime Capacity Test");

        assertEquals(Constants.LEGACY_FACTION_COUNT, created.runtimeFactionId());
        assertEquals(WorldFactionIdentityState.Origin.PLAYER_CREATED, created.origin());
    }
}
