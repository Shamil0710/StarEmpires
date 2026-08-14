package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.player.DiscoveredObjectRef;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerReputationState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayableWorldStateCodecTest {
    @Test
    void roundTripsPlayerAndWorldState() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState world = DemoGalaxyFactory.createState(12_345L, content);
        FleetPlacementState placement = world.fleets().get(0);
        PlayerState player = new PlayerState(
                125_000L,
                "faction.trade_league",
                List.of(new PlayerReputationState("faction.miners", 12.5f)),
                List.of(placement.id()),
                placement.id(),
                List.of(placement.systemId()),
                List.of(new DiscoveredObjectRef(placement.systemId(), placement.localEntityId())),
                placement.systemId());
        PlayableWorldState state = new PlayableWorldState(
                PlayableWorldState.CURRENT_VERSION,
                world,
                player);

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(
                PlayableWorldStateCodec.encode(state));

        assertEquals(state, decoded);
        assertTrue(decoded.hasPlayer());
    }

    @Test
    void migratesLegacyWorldSaveWithoutInventingPlayer() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState legacyWorld = DemoGalaxyFactory.createState(9876L, content);

        PlayableWorldState migrated = PlayableWorldStateCodec.decode(
                WorldStateCodec.encode(legacyWorld));

        assertEquals(PlayableWorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertEquals(legacyWorld, migrated.worldState());
        assertNull(migrated.playerState());
        assertFalse(migrated.hasPlayer());
    }
}
