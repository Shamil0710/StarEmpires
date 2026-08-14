package com.spacesim.player;

import com.spacesim.persistence.EntityId;
import com.spacesim.world.ConstructionProjectId;
import com.spacesim.world.FleetId;
import com.spacesim.world.StarSystemId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage16PlayerOwnershipCopyTest {
    @Test
    void walletAndFleetOwnershipChangesPreserveConstructionAssets() {
        FleetId fleet = new FleetId(1L);
        StarSystemId system = new StarSystemId(2L);
        ConstructionProjectId project = new ConstructionProjectId(3L);
        OwnedStationRef station = new OwnedStationRef(system, new EntityId(4L));
        PlayerState source = new PlayerState(
                100L,
                null,
                List.of(),
                List.of(fleet),
                fleet,
                List.of(system),
                List.of(),
                system,
                null,
                List.of(),
                List.of(),
                List.of(project),
                List.of(station));

        PlayerState changed = PlayerRuntime.copyWithOwnershipAndWallet(
                source, 75L, List.of(fleet), fleet);

        assertEquals(List.of(project), changed.ownedConstructionProjectIds());
        assertEquals(List.of(station), changed.ownedStations());
    }
}
