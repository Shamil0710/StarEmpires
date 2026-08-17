package com.spacesim.combat.acceptance;

import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.ship.AmmunitionRuntime;
import com.spacesim.ship.HeavyImpactResolver;
import com.spacesim.ship.KineticProtectionRuntime;
import com.spacesim.ship.KineticProtectionRuntime.ShieldInput;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringRuntime.RuntimeState;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.ShipWeaponEngineeringAdapter;
import com.spacesim.ship.TrackCovariance;
import com.spacesim.ship.TrackState;
import com.spacesim.ship.TrackState.InformationState;
import com.spacesim.ship.WeaponFireControl;
import com.spacesim.ship.WeaponFireControl.KinematicState;
import com.spacesim.ship.WeaponFireControl.TargetMotionEstimate;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IKineticImpactAcceptanceTest {
    @Test
    void physicalRailShotConsumesAmmoCrossesShieldAndArmorAndDamagesLocalSubsystems() {
        Stage175ICombatTestContentPack pack = Stage175ICombatTestContentPack.loadDefault();
        Stage175IShipMaterializer materializer = new Stage175IShipMaterializer(pack);
        var variation = pack.manifest().findVariation("variation.ct_baseline_v1");
        var attacker = materializer.materialize("fit.ct_destroyer_kinetic_v1", variation);
        var target = materializer.materialize("fit.ct_destroyer_defense_v1", variation);

        ShipWeaponEngineeringAdapter.FittedKineticMount mount = new ShipWeaponEngineeringAdapter()
                .deriveKineticMounts(
                        attacker.derived(),
                        pack.ammunition(),
                        pack.launchers(),
                        attacker.engineering().instanceState.weaponLoadout())
                .get(0);
        long ammunitionBefore = attacker.engineering().runtimeState.consumables().ammunitionCount();
        var consumed = new AmmunitionRuntime().consumeOne(
                attacker.engineering().runtimeState.consumables(),
                mount.mountId(),
                mount.launcher(),
                mount.round().massKg());
        RuntimeState current = attacker.engineering().runtimeState;
        attacker.engineering().setRuntimeState(new RuntimeState(
                consumed.consumables(),
                current.sharedBusEnergyJ(),
                current.shipHeatStoredJ(),
                current.localHeatJByMount(),
                current.thrustLimitNByMount(),
                current.coolantBusCapacityW(),
                current.ftlCooldownSecondsByMount()));

        WeaponFireControl fireControl = new WeaponFireControl();
        KinematicState shooter = new KinematicState(-125_000d, 0d, 0d, 0d);
        TrackState track = new TrackState(
                2L,
                InformationState.FIRE_CONTROL,
                true,
                125_000d,
                0d,
                new TrackCovariance(100d, 1e-10d, 100d),
                0.99d,
                0d,
                2,
                4);
        var solution = fireControl.planKinetic(
                mount.round(), track, shooter, new TargetMotionEstimate(0d, 0d, 0d, 0d),
                mount.pointingJitterRad(), 0d);
        assertTrue(solution.allowed());
        var projectile = fireControl.materializeKineticProjectile(
                10_001L, 1L, 0L, mount.round(), shooter, solution);

        var targetHull = pack.engineering().findHull(target.derived().hullId());
        var targetLayout = pack.protection().findHullDamageLayout(targetHull.id());
        var fittedShield = new ShipShieldEngineeringAdapter().derive(target.derived()).get(0);
        ShieldFieldRuntime.State shieldState = target.engineering().instanceState.shieldStatesByMount()
                .get(fittedShield.mountId());
        assertNotNull(shieldState);

        KineticProtectionRuntime protection = new KineticProtectionRuntime(
                new ShieldFieldRuntime(),
                new HeavyImpactResolver(pack.engineering(), pack.protection()),
                new ShipDamageRuntime());
        Vector3d hitPoint = targetHull.compartments().stream()
                .filter(value -> value.id().equals("weapons"))
                .findFirst().orElseThrow().centerM();
        var impact = protection.resolve(
                projectile,
                new ShieldInput(fittedShield.definition(), shieldState),
                0d,
                1d,
                targetHull.structuralProtectionStackId(),
                0d,
                targetHull,
                target.engineering().fit,
                targetLayout,
                target.engineering().instanceState.damage(),
                hitPoint);

        assertTrue(attacker.engineering().runtimeState.consumables().ammunitionCount() == ammunitionBefore - 1L);
        assertNotNull(impact.shieldInteraction());
        assertTrue(impact.shieldInteraction().absorbedEnergyJ() > 0d);
        assertTrue(impact.shieldInteraction().state().reserveJ() < shieldState.reserveJ());
        assertTrue(impact.armorReached());
        assertTrue(impact.internalDamageOccurred());
        assertNotNull(impact.damageEvent());
        assertTrue(impact.damageEvent().snapshot().compartmentIntegrityById().get("weapons") < 1d);
        assertTrue(impact.damageEvent().snapshot().moduleDamage().moduleIntegrityByMount().values().stream()
                .anyMatch(value -> value < 1d));

        TreeMap<String, ShieldFieldRuntime.State> shields = new TreeMap<>(
                target.engineering().instanceState.shieldStatesByMount());
        shields.put(fittedShield.mountId(), impact.shieldInteraction().state());
        target.engineering().setInstanceState(new com.spacesim.ship.ShipInstanceRuntimeState(
                impact.damageEvent().snapshot(),
                Map.copyOf(shields),
                target.engineering().instanceState.maintenance(),
                target.engineering().instanceState.weaponLoadout(),
                target.engineering().instanceState.weaponMountRuntime()));
        assertTrue(target.engineering().instanceState.damage().compartmentIntegrityById().get("weapons") < 1d);
    }
}
