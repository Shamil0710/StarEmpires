package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.ship.AmmunitionRuntime.Failure;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.WeaponDefinition.Launcher;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmmunitionRuntimeTest {
    private static final AmmunitionRuntime RUNTIME = new AmmunitionRuntime();
    private static final Launcher LAUNCHER = new Launcher(
            "launcher.railgun_large_v1",
            "kinetic_magazine_feed",
            1d,
            4d,
            1);

    @Test
    void firingConsumesCountInterfaceAmountAndPhysicalMassFromCentralLoadState() {
        ConsumableState initial = state(3d, 450d, 3L);

        AmmunitionRuntime.ConsumptionResult result = RUNTIME.consumeOne(
                initial,
                "weapon_spinal",
                LAUNCHER,
                150d);

        ConsumableLoad original = initial.interfaceLoads().get(0);
        ConsumableLoad next = result.consumables().interfaceLoads().get(0);
        assertEquals(3L, original.itemCount());
        assertEquals(450d, original.massKg(), 1e-12d);
        assertEquals(2L, next.itemCount());
        assertEquals(2d, next.amount(), 1e-12d);
        assertEquals(300d, next.massKg(), 1e-12d);
        assertEquals(150d, result.consumedMassKg(), 1e-12d);
    }

    @Test
    void exhaustedPhysicalFeedCannotFireEvenIfOtherShipCargoExists() {
        ConsumableState exhausted = new ConsumableState(
                5_000d,
                1_000d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal",
                        "kinetic_magazine_feed",
                        InterfaceKind.AMMUNITION,
                        0d,
                        0d,
                        0L)));

        AmmunitionRuntime.ConsumptionPlan plan = RUNTIME.planOne(
                exhausted,
                "weapon_spinal",
                LAUNCHER,
                150d);

        assertEquals(Failure.ROUND_COUNT_EXHAUSTED, plan.failure());
        assertThrows(IllegalStateException.class, () -> RUNTIME.consumeOne(
                exhausted,
                "weapon_spinal",
                LAUNCHER,
                150d));
    }

    @Test
    void wrongMountCannotBorrowAmmunitionFromAnotherPhysicalFeed() {
        ConsumableState loaded = state(4d, 600d, 4L);

        AmmunitionRuntime.ConsumptionPlan plan = RUNTIME.planOne(
                loaded,
                "weapon_port",
                LAUNCHER,
                150d);

        assertEquals(Failure.FEED_NOT_FOUND, plan.failure());
        assertTrue(!plan.allowed());
    }

    @Test
    void physicalMassConstraintIsIndependentFromItemCounter() {
        ConsumableState inconsistent = state(3d, 100d, 3L);

        AmmunitionRuntime.ConsumptionPlan plan = RUNTIME.planOne(
                inconsistent,
                "weapon_spinal",
                LAUNCHER,
                150d);

        assertEquals(Failure.PHYSICAL_MASS_EXHAUSTED, plan.failure());
    }

    private static ConsumableState state(double amount, double massKg, long count) {
        return new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal",
                        "kinetic_magazine_feed",
                        InterfaceKind.AMMUNITION,
                        amount,
                        massKg,
                        count)));
    }
}
