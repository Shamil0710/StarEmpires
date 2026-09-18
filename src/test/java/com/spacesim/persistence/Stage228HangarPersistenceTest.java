package com.spacesim.persistence;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.world.ProductionSmallCraftFixture;
import com.spacesim.world.SmallCraftFitAuthority;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftId;
import com.spacesim.world.SmallCraftRegistry;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage228HangarPersistenceTest {
    @Test
    void sidecarRoundTripPreservesIndividualBayAndHandlingStateDeterministically() {
        SmallCraftRegistry craft = oneCraftRegistry();
        SmallCraftId id = craft.snapshot().get(0).id();
        var footprint = craft.physicalFootprint(id);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:7", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 2d,
                footprint.currentMassKg() * 2d,
                1d);
        SmallCraftHangarRegistry hangar = SmallCraftHangarRegistry.empty(craft);
        hangar.assign(id, bay, OccupancyState.SERVICING);

        Stage228HangarPersistentState captured =
                Stage228HangarPersistenceMapper.capture(hangar);
        byte[] first = Stage228HangarPersistenceCodec.encode(captured);
        Stage228HangarPersistentState decoded =
                Stage228HangarPersistenceCodec.decode(first);
        byte[] second = Stage228HangarPersistenceCodec.encode(decoded);

        assertEquals(captured, decoded);
        assertTrue(Arrays.equals(first, second));
        SmallCraftHangarRegistry restored =
                Stage228HangarPersistenceMapper.restore(decoded, craft);
        assertEquals(OccupancyState.SERVICING,
                restored.find(id).orElseThrow().state());
        assertEquals(HostKind.SHIP,
                restored.find(id).orElseThrow().hostKind());
        assertEquals("carrier:7",
                restored.find(id).orElseThrow().bayId().hostStableId());
    }

    @Test
    void restoreFailsClosedWhenOccupancyReferencesUnknownCraft() {
        SmallCraftRegistry craft = oneCraftRegistry();
        Stage228HangarPersistentState malformed = new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                List.of(new Stage228HangarPersistentState.AssignmentState(
                        new SmallCraftId(999L),
                        "station:1",
                        "bay:a",
                        HostKind.STATION,
                        OccupancyState.PARKED)));

        assertThrows(IllegalArgumentException.class,
                () -> Stage228HangarPersistenceMapper.restore(malformed, craft));
    }

    @Test
    void persistentStateRejectsDuplicateIndividualCraftAssignment() {
        SmallCraftId id = new SmallCraftId(1L);
        var first = new Stage228HangarPersistentState.AssignmentState(
                id, "carrier:1", "bay:a", HostKind.SHIP, OccupancyState.PARKED);
        var second = new Stage228HangarPersistentState.AssignmentState(
                id, "carrier:2", "bay:b", HostKind.SHIP, OccupancyState.READY);

        assertThrows(IllegalArgumentException.class, () -> new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                List.of(first, second)));
    }

    private static SmallCraftRegistry oneCraftRegistry() {
        SmallCraftFitAuthority authority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(authority);
        SmallCraftId id = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                id, 8L, 80d, 4_000d, 1d, 100d));
        return registry;
    }
}
