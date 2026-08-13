package com.spacesim.world;

import com.spacesim.DemoGalaxyFactory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FleetIdentityBootstrapTest {
    @Test
    void bootstrapIsStableForSameWorld() {
        WorldState state = DemoGalaxyFactory.create(0x10A5EEDL).snapshot();
        FleetBootstrap.Result first = FleetBootstrap.create(state.systems());
        FleetBootstrap.Result second = FleetBootstrap.create(state.systems());

        assertEquals(first, second);
        assertFalse(first.placements().isEmpty());
        assertEquals(first.placements().size(),
                new HashSet<>(first.placements().stream().map(FleetPlacementState::id).toList()).size());
        assertEquals(first.placements().size() + 1L, first.nextId());
        assertTrue(first.placements().stream()
                .allMatch(placement -> placement.locationKind() == FleetLocationKind.IN_SYSTEM));
    }
}
