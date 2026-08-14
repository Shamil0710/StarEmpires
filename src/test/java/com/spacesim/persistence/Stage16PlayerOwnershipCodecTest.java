package com.spacesim.persistence;

import com.spacesim.DemoGalaxyFactory;
import com.spacesim.content.ContentCatalog;
import com.spacesim.content.ContentCatalogLoader;
import com.spacesim.player.OwnedStationRef;
import com.spacesim.player.PlayableWorldState;
import com.spacesim.player.PlayerState;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.WorldState;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage16PlayerOwnershipCodecTest {
    @Test
    void roundTripsConstructionProjectAndStationOwnership() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState world = DemoGalaxyFactory.createState(16001L, content);
        FleetPlacementState placement = world.fleets().get(0);
        PlayerState player = new PlayerState(
                250_000L,
                null,
                List.of(),
                List.of(placement.id()),
                placement.id(),
                List.of(placement.systemId()),
                List.of(),
                placement.systemId(),
                null,
                List.of(),
                List.of(),
                List.of(new ConstructionProjectId(41L), new ConstructionProjectId(7L)),
                List.of(new OwnedStationRef(placement.systemId(), new EntityId(9001L))));

        PlayableWorldState decoded = PlayableWorldStateCodec.decode(PlayableWorldStateCodec.encode(
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, world, player)));

        assertEquals(List.of(new ConstructionProjectId(7L), new ConstructionProjectId(41L)),
                decoded.playerState().ownedConstructionProjectIds());
        assertEquals(player.ownedStations(), decoded.playerState().ownedStations());
        assertEquals(null, decoded.playerState().factionContentId());
    }

    @Test
    void migratesSchemaV4WithEmptyStage16Ownership() {
        ContentCatalog content = ContentCatalogLoader.loadDefault();
        WorldState world = DemoGalaxyFactory.createState(16002L, content);
        FleetPlacementState placement = world.fleets().get(0);
        PlayerState player = new PlayerState(
                100_000L,
                null,
                List.of(),
                List.of(placement.id()),
                placement.id(),
                List.of(placement.systemId()),
                List.of(),
                placement.systemId());
        byte[] current = PlayableWorldStateCodec.encode(
                new PlayableWorldState(PlayableWorldState.CURRENT_VERSION, world, player));

        byte[] legacyV4 = Arrays.copyOf(current, current.length - 2 * Integer.BYTES);
        legacyV4[8] = 0;
        legacyV4[9] = 0;
        legacyV4[10] = 0;
        legacyV4[11] = (byte) PlayableWorldState.LEGACY_THREAT_INTEL_VERSION;

        PlayableWorldState migrated = PlayableWorldStateCodec.decode(legacyV4);

        assertEquals(PlayableWorldState.CURRENT_VERSION, migrated.schemaVersion());
        assertTrue(migrated.playerState().ownedConstructionProjectIds().isEmpty());
        assertTrue(migrated.playerState().ownedStations().isEmpty());
        assertEquals(player.walletMilliCredits(), migrated.playerState().walletMilliCredits());
        assertEquals(player.ownedFleetIds(), migrated.playerState().ownedFleetIds());
    }
}
