package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader;
import com.spacesim.content.weapon.Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M22.6 bridge acceptance: authored Stage22 kinetic weapons use the common Stage17.5E runtime. */
class Stage22CorePairWeaponRuntimeBridgeTest {
    @Test
    void bothCoreFactionPrimaryKineticsResolveThroughCommonEngineeringWeaponAdapter() {
        RuntimeContent empireContent = Stage22CorePairWeaponRuntimeCatalogLoader.loadEmpire();
        RuntimeContent unionContent = Stage22CorePairWeaponRuntimeCatalogLoader.loadIndustrialUnion();

        ShipWeaponEngineeringAdapter.FittedKineticMount empire = fittedMount(
                empireContent,
                "fit.empire.corvette.line_v1",
                "ammo.empire_axial_dart_150kg_v1",
                150d);
        ShipWeaponEngineeringAdapter.FittedKineticMount union = fittedMount(
                unionContent,
                "fit.industrial_union.corvette.line_v1",
                "ammo.industrial_union_dart_140kg_v1",
                140d);

        assertEquals("module.empire_kinetic_axial_v1", empire.moduleId());
        assertEquals("module.industrial_union_weapon_cassette_v1", union.moduleId());
        assertEquals(9_000d, empire.round().muzzleVelocityMps(), 1e-12d);
        assertEquals(8_800d, union.round().muzzleVelocityMps(), 1e-12d);
        assertEquals(1_350_000d, empire.recoilImpulseNs(), 1e-6d);
        assertEquals(1_232_000d, union.recoilImpulseNs(), 1e-6d);
        assertEquals(4d, empire.launcher().cycleTimeSeconds(), 1e-12d);
        assertEquals(empire.launcher().cycleTimeSeconds(), union.launcher().cycleTimeSeconds(), 1e-12d);
        assertEquals(empire.pointingJitterRad(), union.pointingJitterRad(), 1e-15d);
        assertTrue(empire.round().kineticEnergyJ() > union.round().kineticEnergyJ(),
                "Empire direct-shot strength must come from authored mass/velocity, not a faction scalar");
        assertNotEquals(empireContent.launchers().getFingerprint(), unionContent.launchers().getFingerprint());
        assertNotEquals(empireContent.ammunition().getFingerprint(), unionContent.ammunition().getFingerprint());
    }

    @Test
    void firingCoreFactionRoundReducesTheSameDerivedShipMassAndAmmoState() {
        RuntimeContent empireContent = Stage22CorePairWeaponRuntimeCatalogLoader.loadEmpire();
        DerivedShipCalculator calculator = new DerivedShipCalculator(empireContent.engineering());
        ConsumableState beforeLoads = ammunitionState(12L, 150d);
        DerivedShipState before = calculator.deriveDemonstrator(
                "fit.empire.corvette.line_v1", beforeLoads, DamageState.pristine());
        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(
                        before,
                        empireContent.ammunition(),
                        empireContent.launchers(),
                        loadout("ammo.empire_axial_dart_150kg_v1"))
                .get(0);

        ConsumableState afterLoads = new AmmunitionRuntime().consumeOne(
                beforeLoads,
                mount.mountId(),
                mount.launcher(),
                mount.round().massKg()).consumables();
        DerivedShipState after = calculator.deriveDemonstrator(
                "fit.empire.corvette.line_v1", afterLoads, DamageState.pristine());

        assertEquals(before.totalMassKg() - 150d, after.totalMassKg(), 1e-6d);
        assertEquals(before.ammunitionCount() - 1L, after.ammunitionCount());
        assertEquals(before.ammunitionMassKg() - 150d, after.ammunitionMassKg(), 1e-6d);
    }

    private static ShipWeaponEngineeringAdapter.FittedKineticMount fittedMount(
            RuntimeContent content,
            String fitId,
            String ammunitionId,
            double massKg) {
        DerivedShipState derived = new DerivedShipCalculator(content.engineering()).deriveDemonstrator(
                fitId, ammunitionState(12L, massKg), DamageState.pristine());
        return new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                derived, content.ammunition(), content.launchers(), loadout(ammunitionId)).get(0);
    }

    private static ConsumableState ammunitionState(long rounds, double massKg) {
        return new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "weapon_primary",
                        "kinetic_feed",
                        InterfaceKind.AMMUNITION,
                        rounds,
                        rounds * massKg,
                        rounds)));
    }

    private static WeaponLoadoutState loadout(String ammunitionId) {
        return new WeaponLoadoutState(List.of(new FeedBinding(
                "weapon_primary", "kinetic_feed", ammunitionId)));
    }
}
