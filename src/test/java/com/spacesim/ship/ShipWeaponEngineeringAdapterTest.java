package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.weapon.WeaponAmmunitionCatalog;
import com.spacesim.content.weapon.WeaponAmmunitionCatalogLoader;
import com.spacesim.content.weapon.WeaponLauncherCatalog;
import com.spacesim.content.weapon.WeaponLauncherCatalogLoader;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShipWeaponEngineeringAdapterTest {
    @Test
    void productionRailgunCombinesEngineeringLauncherAndPhysicalAmmoWithoutHardRangeStats() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.loadDefault();
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.loadDefault();
        DerivedShipState derived = new DerivedShipCalculator(engineering).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1",
                ammunitionState(10L),
                DamageState.pristine());
        WeaponLoadoutState loadout = new WeaponLoadoutState(List.of(new FeedBinding(
                "weapon_spinal",
                "kinetic_magazine_feed",
                "ammo.rail_dart_150kg_v1")));

        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(derived, ammunition, launchers, loadout)
                .get(0);

        assertEquals("weapon_spinal", mount.mountId());
        assertEquals("module.railgun_large_v1", mount.moduleId());
        assertEquals(150d, mount.round().massKg(), 1e-12d);
        assertEquals(9_000d, mount.round().muzzleVelocityMps(), 1e-12d);
        assertEquals(1_350_000d, mount.recoilImpulseNs(), 1e-6d);
        assertEquals(4d, mount.launcher().cycleTimeSeconds(), 1e-12d);
        assertEquals(0.00002d, mount.pointingJitterRad(), 1e-15d);
        assertTrue(!mount.round().id().contains("range"));
    }

    @Test
    void firingLoadedRoundReducesSameCentralMassUsedByDerivedShipCalculator() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.loadDefault();
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(engineering);
        ConsumableState beforeLoads = ammunitionState(10L);
        DerivedShipState before = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", beforeLoads, DamageState.pristine());
        WeaponLoadoutState loadout = new WeaponLoadoutState(List.of(new FeedBinding(
                "weapon_spinal", "kinetic_magazine_feed", "ammo.rail_dart_150kg_v1")));
        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(before, ammunition, launchers, loadout).get(0);

        ConsumableState afterLoads = new AmmunitionRuntime().consumeOne(
                beforeLoads,
                mount.mountId(),
                mount.launcher(),
                mount.round().massKg()).consumables();
        DerivedShipState after = calculator.deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", afterLoads, DamageState.pristine());

        assertEquals(before.totalMassKg() - 150d, after.totalMassKg(), 1e-6d);
        assertEquals(before.ammunitionCount() - 1L, after.ammunitionCount());
        assertEquals(before.ammunitionMassKg() - 150d, after.ammunitionMassKg(), 1e-6d);
    }

    @Test
    void missingOrWrongAmmunitionIdentityDoesNotBecomeFreeDefaultRound() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipState derived = new DerivedShipCalculator(engineering).deriveDemonstrator(
                "fit.escort_destroyer_schema_v1", ammunitionState(2L), DamageState.pristine());
        ShipWeaponEngineeringAdapter adapter = new ShipWeaponEngineeringAdapter();

        assertThrows(IllegalArgumentException.class, () -> adapter.deriveKineticMounts(
                derived,
                WeaponAmmunitionCatalogLoader.loadDefault(),
                WeaponLauncherCatalogLoader.loadDefault(),
                WeaponLoadoutState.empty()));
        assertThrows(IllegalArgumentException.class, () -> adapter.deriveKineticMounts(
                derived,
                WeaponAmmunitionCatalogLoader.loadDefault(),
                WeaponLauncherCatalogLoader.loadDefault(),
                new WeaponLoadoutState(List.of(new FeedBinding(
                        "weapon_spinal", "kinetic_magazine_feed", "ammo.interceptor_1t_v1")))));
    }

    private static ConsumableState ammunitionState(long rounds) {
        double mass = rounds * 150d;
        return new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "weapon_spinal",
                        "kinetic_magazine_feed",
                        InterfaceKind.AMMUNITION,
                        rounds,
                        mass,
                        rounds)));
    }
}
