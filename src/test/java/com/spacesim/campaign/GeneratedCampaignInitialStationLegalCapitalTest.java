package com.spacesim.campaign;

import com.spacesim.world.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedCampaignInitialStationLegalCapitalTest {
    @Test void distanceThenStableIdentitySelectsBoundedAssetsWithoutOverridingOwners() {
        var one = new StarSystemId(1); var two = new StarSystemId(2); var three = new StarSystemId(3);
        var disconnected = new StarSystemId(4);
        var topology = new GalaxyTopology(new GalaxyId(1), "test", List.of(new SectorNode(new SectorId(1), "sector",
                List.of(new StarSystemNode(one, "one", 0, 0), new StarSystemNode(two, "two", 1, 0),
                        new StarSystemNode(three, "three", 2, 0), new StarSystemNode(disconnected, "isolated", 3, 0)))),
                List.of(new JumpConnection(one, two), new JumpConnection(two, three)));
        String refinery = "station.infrastructure.refinery_complex", frontier = "station.infrastructure.frontier_multipurpose";
        var assets = List.of(asset("existing", one, frontier, "alpha"), asset("r.a", two, refinery, null),
                asset("r.b", three, refinery, null), asset("f.a", two, frontier, null),
                asset("other", two, "station.infrastructure.logistics_hub", null), asset("r.0", disconnected, refinery, null));
        var starts = Map.of("alpha", one, "beta", three);
        var plan = GeneratedCampaignInitialStationLegalCapital.select(topology, starts, assets);
        // beta's local refinery wins first; equal-distance remaining slots use stable IDs.
        assertEquals(Map.of("r.b", "beta", "r.a", "alpha", "f.a", "beta"), plan.assignments());
        assertTrue(plan.unfilledSlots().isEmpty());
        var reversed = new ArrayList<>(assets); Collections.reverse(reversed);
        assertEquals(plan, GeneratedCampaignInitialStationLegalCapital.select(topology, starts, reversed));
        var shortage = GeneratedCampaignInitialStationLegalCapital.select(topology, starts, List.of());
        assertTrue(shortage.assignments().isEmpty()); assertEquals(4, shortage.unfilledSlots().size());
        assertThrows(UnsupportedOperationException.class, () -> plan.assignments().put("new", "alpha"));
    }

    private static GeneratedCampaignInitialStationLegalCapital.Asset asset(String id, StarSystemId system, String role, String owner) {
        return new GeneratedCampaignInitialStationLegalCapital.Asset(id, system, role, Optional.ofNullable(owner));
    }
}
