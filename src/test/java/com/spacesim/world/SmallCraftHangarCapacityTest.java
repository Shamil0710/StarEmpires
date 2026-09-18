package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.CapacityStatus;
import com.spacesim.world.SmallCraftHangarCapacity.CraftFootprint;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.Usage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCraftHangarCapacityTest {
    @Test
    void compatibilityUsesPhysicalEnvelopeMassAndVolumeRatherThanCraftClassCount() {
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:1", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(30d, 20d, 15d),
                9_000d,
                12_000_000d,
                1d);
        CraftFootprint first = new CraftFootprint(
                new SmallCraftId(1L), new Dimensions3d(18d, 12d, 6d), 4_000_000d);
        CraftFootprint rotated = new CraftFootprint(
                new SmallCraftId(2L), new Dimensions3d(6d, 18d, 12d), 4_000_000d);
        CraftFootprint tooLarge = new CraftFootprint(
                new SmallCraftId(3L), new Dimensions3d(31d, 12d, 6d), 1_000_000d);

        assertTrue(bay.acceptsEnvelope(first.envelopeM()));
        assertTrue(bay.acceptsEnvelope(rotated.envelopeM()));
        assertFalse(bay.acceptsEnvelope(tooLarge.envelopeM()));

        Usage one = Usage.empty().plus(first);
        assertTrue(SmallCraftHangarCapacity.canAccept(bay, one, rotated));
        assertEquals(CapacityStatus.WITHIN_CAPACITY,
                SmallCraftHangarCapacity.status(bay, one.plus(rotated)));
    }

    @Test
    void physicalDamageCanMakeExistingOccupancyOverCapacityWithoutRemovingCraft() {
        CraftFootprint craft = new CraftFootprint(
                new SmallCraftId(1L), new Dimensions3d(20d, 15d, 10d), 6_500_000d);
        Usage usage = Usage.empty().plus(craft);
        BayId id = new BayId("carrier:1", "mission_primary");
        BayDefinition pristine = new BayDefinition(
                id, HostKind.SHIP, new Dimensions3d(30d, 20d, 15d),
                9_000d, 12_000_000d, 1d);
        BayDefinition damaged = new BayDefinition(
                id, HostKind.SHIP, new Dimensions3d(30d, 20d, 15d),
                9_000d, 12_000_000d, 0.5d);

        assertEquals(CapacityStatus.WITHIN_CAPACITY,
                SmallCraftHangarCapacity.status(pristine, usage));
        assertEquals(CapacityStatus.OVER_MASS,
                SmallCraftHangarCapacity.status(damaged, usage));
        assertFalse(SmallCraftHangarCapacity.canAccept(damaged, Usage.empty(), craft));
        assertEquals(1, usage.craftCount());
    }

    @Test
    void zeroConditionBayIsInoperableForOccupiedCraftAndRejectsNewEntry() {
        BayDefinition destroyed = new BayDefinition(
                new BayId("station:1", "bay:a"),
                HostKind.STATION,
                new Dimensions3d(40d, 30d, 20d),
                20_000d,
                20_000_000d,
                0d);
        CraftFootprint craft = new CraftFootprint(
                new SmallCraftId(1L), new Dimensions3d(10d, 8d, 4d), 1_000_000d);

        assertFalse(SmallCraftHangarCapacity.canAccept(destroyed, Usage.empty(), craft));
        assertEquals(CapacityStatus.INOPERABLE,
                SmallCraftHangarCapacity.status(destroyed, Usage.empty().plus(craft)));
    }
}
