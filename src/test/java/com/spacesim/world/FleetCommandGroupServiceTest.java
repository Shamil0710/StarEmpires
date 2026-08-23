package com.spacesim.world;

import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FleetCommandGroupServiceTest {
    private static final StarSystemId ALPHA = new StarSystemId(1L);
    private static final StarSystemId BETA = new StarSystemId(2L);
    private static final int FACTION = 7;

    @Test
    void formationAllocatesOnlyCommandIdentityAndRetainsOrdinaryFleetIds() {
        FleetId firstFleet = new FleetId(101L);
        FleetId secondFleet = new FleetId(102L);
        FleetId thirdFleet = new FleetId(103L);
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(firstFleet, FACTION),
                entry(secondFleet, FACTION),
                entry(thirdFleet, FACTION)));
        FleetCommandGroupService service = new FleetCommandGroupService(topology());

        var first = service.form(
                FleetCommandState.empty(), forces, FACTION, "First", List.of(secondFleet, firstFleet),
                ALPHA, false, true, 4_000);
        var second = service.form(
                first.state(), forces, FACTION, "Second", List.of(thirdFleet),
                BETA, false, false, 5_000);

        assertEquals(1L, first.group().id());
        assertEquals(List.of(firstFleet, secondFleet), first.group().memberFleetIds());
        assertEquals(2L, first.state().nextCommandGroupId());
        assertEquals(FACTION, first.group().factionId());
        assertEquals(firstFleet, forces.find(firstFleet).orElseThrow().fleetId());
        assertEquals(secondFleet, forces.find(secondFleet).orElseThrow().fleetId());
        assertEquals(thirdFleet, forces.find(thirdFleet).orElseThrow().fleetId());
        assertEquals(2L, second.group().id());
        assertEquals(List.of(thirdFleet), second.group().memberFleetIds());
        assertEquals(3L, second.state().nextCommandGroupId());
    }

    @Test
    void wrongOwnerUnknownFleetUnknownHomeAndDoubleAssignmentFailClosed() {
        FleetId owned = new FleetId(101L);
        FleetId foreign = new FleetId(202L);
        FleetForceRegistry forces = new FleetForceRegistry(List.of(
                entry(owned, FACTION),
                entry(foreign, FACTION + 1)));
        FleetCommandGroupService service = new FleetCommandGroupService(topology());

        assertThrows(IllegalStateException.class,
                () -> service.form(FleetCommandState.empty(), forces, FACTION, "Wrong owner",
                        List.of(foreign), ALPHA, false, false, 5_000));
        assertThrows(IllegalArgumentException.class,
                () -> service.form(FleetCommandState.empty(), forces, FACTION, "Unknown fleet",
                        List.of(new FleetId(999L)), ALPHA, false, false, 5_000));
        assertThrows(IllegalArgumentException.class,
                () -> service.form(FleetCommandState.empty(), forces, FACTION, "Unknown home",
                        List.of(owned), new StarSystemId(999L), false, false, 5_000));

        FleetCommandState assigned = service.form(FleetCommandState.empty(), forces, FACTION, "Assigned",
                List.of(owned), ALPHA, false, false, 5_000).state();
        assertThrows(IllegalStateException.class,
                () -> service.form(assigned, forces, FACTION, "Duplicate",
                        List.of(owned), ALPHA, false, false, 5_000));
    }

    private static FleetForceRegistry.Entry entry(FleetId fleetId, int factionId) {
        return new FleetForceRegistry.Entry(
                fleetId,
                factionId,
                FleetLocationKind.IN_SYSTEM,
                ALPHA,
                null,
                null,
                new EntityState(
                        new EntityId(fleetId.value()),
                        new EntityState.IdentityState("Fleet " + fleetId.value(), "FLEET"),
                        null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null),
                FleetReadinessState.unavailable());
    }

    private static GalaxyTopology topology() {
        StarSystemNode alpha = new StarSystemNode(ALPHA, "Alpha", 0d, 0d);
        StarSystemNode beta = new StarSystemNode(BETA, "Beta", 100d, 0d);
        return new GalaxyTopology(
                new GalaxyId(21L),
                "Stage 21D Command Group Test",
                List.of(new SectorNode(new SectorId(1L), "Core", List.of(alpha, beta))),
                List.of(new JumpConnection(ALPHA, BETA)));
    }
}
