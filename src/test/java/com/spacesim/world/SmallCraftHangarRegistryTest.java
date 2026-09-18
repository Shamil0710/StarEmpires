package com.spacesim.world;

import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.CapacityStatus;
import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallCraftHangarRegistryTest {
    @Test
    void assignmentsRemainIndividualAndAllOccupancyStatesConsumeSamePhysicalCapacity() {
        SmallCraftRegistry craft = twoCraftRegistry();
        SmallCraftId first = craft.snapshot().get(0).id();
        SmallCraftId second = craft.snapshot().get(1).id();
        var firstFootprint = craft.physicalFootprint(first);
        BayDefinition bay = new BayDefinition(
                new BayId("carrier:1", "mission_primary"),
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                firstFootprint.envelopeVolumeM3() * 1.5d,
                firstFootprint.currentMassKg() * 1.5d,
                1d);
        SmallCraftHangarRegistry hangar = SmallCraftHangarRegistry.empty(craft);

        hangar.assign(first, bay, OccupancyState.PARKED);
        assertEquals(1, hangar.size());
        assertEquals(firstFootprint.currentMassKg(),
                hangar.usage(bay.id()).occupiedMassKg(), 1e-6);

        hangar.transition(first, OccupancyState.SERVICING);
        hangar.transition(first, OccupancyState.READY);
        hangar.transition(first, OccupancyState.LAUNCHING);
        hangar.transition(first, OccupancyState.RECOVERING);
        assertEquals(1, hangar.usage(bay.id()).craftCount());
        assertEquals(OccupancyState.RECOVERING,
                hangar.find(first).orElseThrow().state());

        assertFalse(hangar.canAccept(second, bay));
        assertThrows(IllegalArgumentException.class,
                () -> hangar.assign(second, bay, OccupancyState.PARKED));
    }

    @Test
    void damagedBayCanBecomeOverCapacityWithoutTeleportingAssignedCraft() {
        SmallCraftRegistry craft = twoCraftRegistry();
        SmallCraftId first = craft.snapshot().get(0).id();
        var footprint = craft.physicalFootprint(first);
        BayId id = new BayId("carrier:1", "mission_primary");
        BayDefinition pristine = new BayDefinition(
                id,
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 2d,
                footprint.currentMassKg() * 2d,
                1d);
        BayDefinition damaged = new BayDefinition(
                id,
                HostKind.SHIP,
                pristine.singleCraftEnvelopeM(),
                pristine.pristineUsableVolumeM3(),
                pristine.pristineSupportedMassKg(),
                0.4d);
        SmallCraftHangarRegistry hangar = SmallCraftHangarRegistry.empty(craft);
        hangar.assign(first, pristine, OccupancyState.READY);

        assertEquals(CapacityStatus.WITHIN_CAPACITY, hangar.capacityStatus(pristine));
        assertTrue(hangar.capacityStatus(damaged) != CapacityStatus.WITHIN_CAPACITY);
        assertEquals(first, hangar.snapshot().get(0).craftId());
    }

    @Test
    void restoreRejectsUnknownOrDuplicateCraftButPreservesHandlingState() {
        SmallCraftRegistry craft = twoCraftRegistry();
        SmallCraftId first = craft.snapshot().get(0).id();
        BayId bay = new BayId("station:1", "bay:a");
        var restored = SmallCraftHangarRegistry.restore(
                craft,
                List.of(new SmallCraftHangarRegistry.Assignment(
                        first, bay, HostKind.STATION, OccupancyState.SERVICING)));

        assertEquals(OccupancyState.SERVICING,
                restored.find(first).orElseThrow().state());
        assertThrows(IllegalArgumentException.class, () -> SmallCraftHangarRegistry.restore(
                craft,
                List.of(
                        new SmallCraftHangarRegistry.Assignment(
                                first, bay, HostKind.STATION, OccupancyState.PARKED),
                        new SmallCraftHangarRegistry.Assignment(
                                first, bay, HostKind.STATION, OccupancyState.READY))));
        assertThrows(IllegalArgumentException.class, () -> SmallCraftHangarRegistry.restore(
                craft,
                List.of(new SmallCraftHangarRegistry.Assignment(
                        new SmallCraftId(999L), bay, HostKind.STATION, OccupancyState.PARKED))));
    }

    @Test
    void restoreRejectsOneBayIdentityWithConflictingHostFamilies() {
        SmallCraftRegistry craft = twoCraftRegistry();
        SmallCraftId first = craft.snapshot().get(0).id();
        SmallCraftId second = craft.snapshot().get(1).id();
        BayId bay = new BayId("shared:host", "bay:a");

        assertThrows(IllegalArgumentException.class, () -> SmallCraftHangarRegistry.restore(
                craft,
                List.of(
                        new SmallCraftHangarRegistry.Assignment(
                                first, bay, HostKind.SHIP, OccupancyState.PARKED),
                        new SmallCraftHangarRegistry.Assignment(
                                second, bay, HostKind.STATION, OccupancyState.PARKED))));
    }

    @Test
    void currentBayProjectionCannotSilentlyChangePersistedHostFamily() {
        SmallCraftRegistry craft = twoCraftRegistry();
        SmallCraftId first = craft.snapshot().get(0).id();
        BayId id = new BayId("shared:host", "bay:a");
        var footprint = craft.physicalFootprint(first);
        BayDefinition shipBay = new BayDefinition(
                id,
                HostKind.SHIP,
                new Dimensions3d(1_000d, 1_000d, 1_000d),
                footprint.envelopeVolumeM3() * 2d,
                footprint.currentMassKg() * 2d,
                1d);
        SmallCraftHangarRegistry hangar = SmallCraftHangarRegistry.empty(craft);
        hangar.assign(first, shipBay, OccupancyState.PARKED);
        BayDefinition conflictingStationBay = new BayDefinition(
                id,
                HostKind.STATION,
                shipBay.singleCraftEnvelopeM(),
                shipBay.pristineUsableVolumeM3(),
                shipBay.pristineSupportedMassKg(),
                1d);

        assertThrows(IllegalArgumentException.class,
                () -> hangar.capacityStatus(conflictingStationBay));
        assertThrows(IllegalArgumentException.class,
                () -> hangar.canAccept(craft.snapshot().get(1).id(), conflictingStationBay));
    }

    private static SmallCraftRegistry twoCraftRegistry() {
        SmallCraftFitAuthority authority = ProductionSmallCraftFixture.fitAuthority();
        SmallCraftRegistry registry = SmallCraftRegistry.empty(authority);
        SmallCraftId first = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                first, 8L, 80d, 4_000d, 1d, 100d));
        SmallCraftId second = registry.reserveIdentityForCompletedProduction();
        registry.registerProducedCraft(ProductionSmallCraftFixture.craft(
                second, 8L, 80d, 4_000d, 1d, 100d));
        return registry;
    }
}
