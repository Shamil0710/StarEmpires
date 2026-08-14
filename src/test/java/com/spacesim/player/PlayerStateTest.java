package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerStateTest {
    @Test
    void canonicalizesPersistentCollections() {
        PlayerState state = new PlayerState(
                42_000L,
                " faction.trade_league ",
                List.of(
                        new PlayerReputationState("faction.trade_league", 4f),
                        new PlayerReputationState("faction.miners", -2f)),
                List.of(new FleetId(2L), new FleetId(1L)),
                new FleetId(2L),
                List.of(new StarSystemId(2L), new StarSystemId(1L)),
                List.of(
                        new DiscoveredObjectRef(new StarSystemId(2L), new EntityId(9L)),
                        new DiscoveredObjectRef(new StarSystemId(1L), new EntityId(7L))),
                new StarSystemId(1L));

        assertEquals("faction.trade_league", state.factionContentId());
        assertEquals(List.of(new FleetId(1L), new FleetId(2L)), state.ownedFleetIds());
        assertEquals(List.of(new StarSystemId(1L), new StarSystemId(2L)), state.discoveredSystemIds());
        assertEquals("faction.miners", state.reputations().get(0).factionContentId());
        assertEquals(new StarSystemId(1L), state.discoveredObjects().get(0).systemId());
        assertTrue(state.affiliated());
    }

    @Test
    void rejectsBrokenOwnershipAndDiscoveryInvariants() {
        assertThrows(IllegalArgumentException.class, () -> new PlayerState(
                0L,
                null,
                List.of(),
                List.of(new FleetId(1L)),
                new FleetId(2L),
                List.of(new StarSystemId(1L)),
                List.of(),
                new StarSystemId(1L)));

        assertThrows(IllegalArgumentException.class, () -> new PlayerState(
                0L,
                null,
                List.of(),
                List.of(),
                null,
                List.of(new StarSystemId(1L)),
                List.of(new DiscoveredObjectRef(new StarSystemId(2L), new EntityId(1L))),
                null));

        assertThrows(IllegalArgumentException.class, () -> new PlayerState(
                0L,
                null,
                List.of(
                        new PlayerReputationState("faction.miners", 1f),
                        new PlayerReputationState("faction.miners", 2f)),
                List.of(),
                null,
                List.of(),
                List.of(),
                null));
    }
}
