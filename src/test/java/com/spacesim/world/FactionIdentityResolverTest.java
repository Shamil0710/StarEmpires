package com.spacesim.world;

import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FactionIdentityResolverTest {
    @Test
    void resolvesAuthoredAndDynamicFactionsThroughOneDirectory() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldFactionIdentityState dynamic = new WorldFactionIdentityState(
                "faction.star_empire",
                7,
                "Star Empire",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);

        FactionIdentityResolver resolver = FactionIdentityResolver.createDefault(
                content,
                List.of(dynamic));

        ContentCatalog.FactionDefinition authored = content.getFactions().get(1);
        assertEquals(authored.runtimeId(), resolver.runtimeId(authored.id()).orElseThrow());
        assertEquals(authored.id(), resolver.stableId(authored.runtimeId()).orElseThrow());
        assertEquals(authored.displayName(), resolver.displayName(authored.id()).orElseThrow());
        assertEquals(7, resolver.runtimeId("faction.star_empire").orElseThrow());
        assertEquals("faction.star_empire", resolver.stableId(7).orElseThrow());
        assertEquals("Star Empire", resolver.displayName("faction.star_empire").orElseThrow());
    }

    @Test
    void allocationUsesLowestFreeSlotDeterministically() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldFactionIdentityState occupiedLaterSlot = new WorldFactionIdentityState(
                "faction.outer_watch",
                5,
                "Outer Watch",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
        FactionIdentityResolver resolver = new FactionIdentityResolver(
                content,
                List.of(occupiedLaterSlot),
                8);

        WorldFactionIdentityState allocated = resolver.allocatePlayerCreated(
                "faction.star_empire",
                " Star Empire ");

        assertEquals(3, allocated.runtimeFactionId());
        assertEquals("faction.star_empire", allocated.stableFactionId());
        assertEquals("Star Empire", allocated.displayName());
        assertEquals(WorldFactionIdentityState.Origin.PLAYER_CREATED, allocated.origin());
    }

    @Test
    void canonicalDynamicOrderDoesNotDependOnInputOrder() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldFactionIdentityState zeta = new WorldFactionIdentityState(
                "faction.zeta",
                6,
                "Zeta",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);
        WorldFactionIdentityState alpha = new WorldFactionIdentityState(
                "faction.alpha",
                5,
                "Alpha",
                WorldFactionIdentityState.Origin.PLAYER_CREATED);

        FactionIdentityResolver first = new FactionIdentityResolver(content, List.of(zeta, alpha), 8);
        FactionIdentityResolver second = new FactionIdentityResolver(content, List.of(alpha, zeta), 8);

        assertEquals(first.dynamicIdentities(), second.dynamicIdentities());
        assertEquals(List.of(alpha, zeta), first.dynamicIdentities());
    }

    @Test
    void rejectsStableAndRuntimeCollisions() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        ContentCatalog.FactionDefinition authored = content.getFactions().get(0);

        assertThrows(IllegalArgumentException.class, () -> new FactionIdentityResolver(
                content,
                List.of(new WorldFactionIdentityState(
                        authored.id(),
                        7,
                        "Collision",
                        WorldFactionIdentityState.Origin.PLAYER_CREATED)),
                8));

        assertThrows(IllegalArgumentException.class, () -> new FactionIdentityResolver(
                content,
                List.of(new WorldFactionIdentityState(
                        "faction.runtime_collision",
                        authored.runtimeId(),
                        "Collision",
                        WorldFactionIdentityState.Origin.PLAYER_CREATED)),
                8));

        assertThrows(IllegalArgumentException.class, () -> new FactionIdentityResolver(
                content,
                List.of(
                        new WorldFactionIdentityState(
                                "faction.one",
                                5,
                                "One",
                                WorldFactionIdentityState.Origin.PLAYER_CREATED),
                        new WorldFactionIdentityState(
                                "faction.one",
                                6,
                                "Duplicate",
                                WorldFactionIdentityState.Origin.PLAYER_CREATED)),
                8));
    }

    @Test
    void rejectsOutOfRangeIdentityAndExhaustedDirectory() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> new FactionIdentityResolver(
                content,
                List.of(new WorldFactionIdentityState(
                        "faction.out_of_range",
                        8,
                        "Out of Range",
                        WorldFactionIdentityState.Origin.PLAYER_CREATED)),
                8));

        FactionIdentityResolver full = new FactionIdentityResolver(content, List.of(), 3);
        assertThrows(IllegalStateException.class, () -> full.allocatePlayerCreated(
                "faction.no_slot",
                "No Slot"));
        assertTrue(full.runtimeId("faction.no_slot").isEmpty());
    }
}
