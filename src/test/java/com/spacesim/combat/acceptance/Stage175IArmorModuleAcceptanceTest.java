package com.spacesim.combat.acceptance;

import com.spacesim.ship.ShipKineticProtectionService;
import com.spacesim.ship.ShipWeaponEngineeringAdapter;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponFireControl;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IArmorModuleAcceptanceTest {
    @Test
    void armorModulePaysMassAndAddsExternalPhysicalMaterialBeforeHullProtection() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var baseline = pack.manifest().findVariation("variation.ct_baseline_v1");
        var attacker = materializer.materialize("fit.ct_destroyer_kinetic_v1", baseline);
        var armored = materializer.materialize("fit.ct_cruiser_armor_gun_v1", baseline);
        var unarmored = materializer.materialize("fit.ct_cruiser_command_v1", baseline);
        assertTrue(armored.derived().installedDryMassKg() > unarmored.derived().installedDryMassKg());

        var mount = new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                attacker.derived(), pack.ammunition(), pack.launchers(),
                attacker.engineering().instanceState.weaponLoadout()).get(0);
        WeaponFireControl fireControl = new WeaponFireControl();
        KinematicState shooter = new KinematicState(-80_000d, 0d, 0d, 0d);
        TrackState track = new TrackState(
                2L, InformationState.FIRE_CONTROL, true, 80_000d, 0d,
                new TrackCovariance(100d, 1e-10d, 100d), 1d, 0d, 3, 6);
        var solution = fireControl.planKinetic(
                mount.round(), track, shooter, new TargetMotionEstimate(0d, 0d, 0d, 0d),
                mount.pointingJitterRad(), 0d);
        assertTrue(solution.allowed());
        var projectile = fireControl.materializeKineticProjectile(
                50_001L, 1L, 0L, mount.round(), shooter, solution);

        ShipKineticProtectionService protection = new ShipKineticProtectionService(
                pack.engineering(), pack.protection(), pack.armorModules());
        var hull = pack.engineering().findHull(armored.derived().hullId());
        var layout = pack.protection().findHullDamageLayout(hull.id());
        var hitPoint = hull.compartments().stream()
                .filter(value -> value.id().equals("weapons"))
                .findFirst().orElseThrow().centerM();

        assertEquals(2, protection.protectionStackIds(hull, armored.engineering().fit).size());
        assertEquals("protection.ct_light_v1",
                protection.protectionStackIds(hull, armored.engineering().fit).get(0));
        assertEquals(1, protection.protectionStackIds(hull, unarmored.engineering().fit).size());

        var armoredHit = protection.resolve(
                projectile, null, 0d, 1d, 0d,
                hull, armored.engineering().fit, layout,
                armored.engineering().instanceState.damage(), hitPoint);
        var unarmoredHit = protection.resolve(
                projectile, null, 0d, 1d, 0d,
                hull, unarmored.engineering().fit, layout,
                unarmored.engineering().instanceState.damage(), hitPoint);
        assertNotNull(armoredHit.damageEvent());
        assertNotNull(unarmoredHit.damageEvent());
        assertEquals(2, armoredHit.stackImpacts().size());
        assertEquals(1, unarmoredHit.stackImpacts().size());
        assertTrue(armoredHit.postProtectionProjectile().kineticEnergyJ()
                < unarmoredHit.postProtectionProjectile().kineticEnergyJ());
        assertTrue(armoredHit.damageEvent().compartmentDamageEnergyJ()
                < unarmoredHit.damageEvent().compartmentDamageEnergyJ());
        assertTrue(armoredHit.damageEvent().snapshot().compartmentIntegrityById().get("weapons")
                > unarmoredHit.damageEvent().snapshot().compartmentIntegrityById().get("weapons"));
    }
}
