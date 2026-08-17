package com.spacesim.ui;

import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.BeamWeaponRuntime;
import com.spacesim.ship.ElectronicWarfareState;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.HeavyImpactResolver;
import com.spacesim.ship.KineticProtectionRuntime;
import com.spacesim.ship.ProjectileBody;
import com.spacesim.ship.ShieldFieldRuntime;
import com.spacesim.ship.ShipDamageRuntime;
import com.spacesim.ship.ShipEngineeringState;
import com.spacesim.ship.ShipShieldEngineeringAdapter;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog;
import com.spacesim.ship.WeaponDefinition;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ITacticalVisualProjectionTest {
    @Test
    void projectsEveryMandatoryTacticalPrototypeVisualFamilyFromAuthoritativeState() {
        var engineering = Stage175ICombatTestContentPack.loadDoctrines();
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        var doctrine = Stage175IFleetDoctrineCatalog.get(Stage175IFleetDoctrineCatalog.DoctrineId.D_DEFENSIVE_EW);
        var fit = ShipEngineeringState.InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(doctrine.fitId()));
        var derived = new com.spacesim.ship.DerivedShipCalculator(engineering).derive(
                hull, fit, doctrine.initialConsumables(), ShipEngineeringState.DamageState.pristine());
        var fittedShield = new ShipShieldEngineeringAdapter().derive(derived).get(0);
        var shieldDefinition = fittedShield.definition();
        var shieldState = ShieldFieldRuntime.State.charged(shieldDefinition);

        String firstCompartment = hull.compartments().get(0).id();
        var damagedSnapshot = new ShipDamageRuntime.Snapshot(
                Map.of(firstCompartment, 0.45d),
                ShipEngineeringState.DamageState.pristine());

        ProjectileBody projectile = new ProjectileBody(
                101L,
                1L,
                5L,
                "material.test",
                WeaponDefinition.ProjectileShape.ROD,
                1.5d,
                0.08d,
                150d,
                80_000d,
                12_000d,
                2_500d,
                0d);

        var weaponCatalog = Stage175ICombatTestWeaponPack.loadAmmunition();
        var missileContent = weaponCatalog.findGuided("ammo.test_anti_ship_missile_2t_v1");
        var missile = GuidedWeaponBody.launch(
                201L,
                2L,
                99L,
                missileContent.toRuntimeWeapon(),
                missileContent.materialId(),
                missileContent.shape(),
                missileContent.lengthM(),
                missileContent.diameterM(),
                missileContent.impactPayloadId(),
                50_000d,
                -15_000d,
                900d,
                100d);
        var interceptorContent = weaponCatalog.findGuided("ammo.test_interceptor_750kg_v1");
        var interceptor = GuidedWeaponBody.launch(
                202L,
                3L,
                201L,
                interceptorContent.toRuntimeWeapon(),
                interceptorContent.materialId(),
                interceptorContent.shape(),
                interceptorContent.lengthM(),
                interceptorContent.diameterM(),
                interceptorContent.impactPayloadId(),
                200_000d,
                10_000d,
                -1_100d,
                -50d);

        var beam = new BeamWeaponRuntime.BeamSolution(
                true,
                BeamWeaponRuntime.Failure.NONE,
                99L,
                300_000d,
                4d,
                3d,
                2d,
                5.4d,
                1d,
                40_000_000d,
                500_000d,
                55_000_000d,
                15_000_000d);

        var deception = new ElectronicWarfareState.DeceptionSource(
                77L,
                "false-track-alpha",
                0.15d,
                12_000d,
                5_000d);

        var shieldInteraction = new ShieldFieldRuntime.Interaction(
                shieldState,
                8_000_000d,
                12_000_000d,
                1_000_000d,
                true);
        var armorImpact = new HeavyImpactResolver.ImpactResult(
                hull.structuralProtectionStackId(),
                projectile.kineticEnergyJ(),
                projectile.kineticEnergyJ() * 0.4d,
                projectile.kineticEnergyJ() * 0.6d,
                true,
                HeavyImpactResolver.Outcome.PERFORATED,
                List.of(),
                new HeavyImpactResolver.FragmentCloud(2d, 5_000d, true),
                projectile,
                2_000_000d);
        var damageEvent = new ShipDamageRuntime.DamageEvent(
                damagedSnapshot,
                firstCompartment,
                2_000_000d,
                400_000d,
                List.of());
        var protectionResult = new KineticProtectionRuntime.Result(
                shieldInteraction,
                projectile,
                armorImpact,
                projectile,
                damageEvent);

        TacticalPrototypeVisualSnapshot snapshot = new Stage175ITacticalVisualProjection()
                .addShip(10L, hull, damagedSnapshot, 300_000d, 0d, Math.PI, 0.65d, shieldDefinition, shieldState)
                .addKinetic(projectile)
                .addGuided(missile, false)
                .addGuided(interceptor, true)
                .addDeceptionHypothesis(303L, 0d, 0d, 0d, 250_000d, deception)
                .addBeam(401L, 0d, 0d, 300_000d, 0d, beam)
                .addImpact(501L, 300_000d, 0d, protectionResult)
                .snapshot();

        assertEquals(1, snapshot.ships().size());
        assertEquals(1, snapshot.shields().size());
        assertTrue(snapshot.damage().stream().anyMatch(value -> value.compartmentId().equals(firstCompartment)));
        assertTrue(snapshot.bodies().stream().anyMatch(value -> value.kind() == BodyKind.KINETIC_PROJECTILE));
        assertTrue(snapshot.bodies().stream().anyMatch(value -> value.kind() == BodyKind.GUIDED_MISSILE));
        assertTrue(snapshot.bodies().stream().anyMatch(value -> value.kind() == BodyKind.INTERCEPTOR));
        assertTrue(snapshot.bodies().stream().anyMatch(value -> value.kind() == BodyKind.DECOY));
        assertEquals(1, snapshot.beams().size());
        assertTrue(snapshot.impacts().stream().anyMatch(value -> value.kind() == ImpactKind.SHIELD));
        assertTrue(snapshot.impacts().stream().anyMatch(value -> value.kind() == ImpactKind.ARMOR));
        assertTrue(snapshot.impacts().stream().anyMatch(value -> value.kind() == ImpactKind.PENETRATION));
    }

    @Test
    void wreckAndDebrisArePurePresentationDerivedFromZeroCompartmentIntegrity() {
        var engineering = Stage175ICombatTestContentPack.loadDoctrines();
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        Map<String, Double> destroyedCompartments = hull.compartments().stream()
                .collect(java.util.stream.Collectors.toMap(value -> value.id(), value -> 0d));
        var destroyed = new ShipDamageRuntime.Snapshot(
                destroyedCompartments,
                ShipEngineeringState.DamageState.pristine());

        var first = new Stage175ITacticalVisualProjection()
                .addShip(700L, hull, destroyed, 10d, 20d, 0.4d, 0d, null, null)
                .snapshot();
        var second = new Stage175ITacticalVisualProjection()
                .addShip(700L, hull, destroyed, 10d, 20d, 0.4d, 0d, null, null)
                .snapshot();

        assertTrue(first.ships().get(0).wreck());
        assertEquals(6, first.bodies().stream().filter(value -> value.kind() == BodyKind.DEBRIS).count());
        assertEquals(first, second);
        assertEquals(destroyedCompartments, destroyed.compartmentIntegrityById());
    }

    @Test
    void projectionCannotMutateAuthoritativeProjectileGuidedOrDamageState() {
        ProjectileBody projectile = new ProjectileBody(
                901L, 9L, 1L, "material.test", WeaponDefinition.ProjectileShape.ROD,
                1d, 0.1d, 10d, 100d, 200d, 300d, 400d);
        var missileContent = Stage175ICombatTestWeaponPack.loadAmmunition()
                .findGuided("ammo.test_anti_ship_missile_2t_v1");
        GuidedWeaponBody missile = GuidedWeaponBody.launch(
                902L, 9L, 10L, missileContent.toRuntimeWeapon(), missileContent.materialId(),
                missileContent.shape(), missileContent.lengthM(), missileContent.diameterM(),
                missileContent.impactPayloadId(), 0d, 0d, 500d, 0d);
        var damage = new ShipDamageRuntime.Snapshot(
                Map.of("compartment.test", 0.7d),
                new ShipEngineeringState.DamageState(Map.of("mount.test", 0.8d)));

        ProjectileBody projectileBefore = projectile;
        GuidedWeaponBody missileBefore = missile;
        var damageBefore = damage;
        TacticalPrototypeVisualSnapshot snapshot = new Stage175ITacticalVisualProjection()
                .addKinetic(projectile)
                .addGuided(missile, false)
                .snapshot();

        assertEquals(projectileBefore, projectile);
        assertEquals(missileBefore, missile);
        assertEquals(damageBefore, damage);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.bodies().clear());
    }

    @Test
    void rejectedBeamProducesNoVisualAndCollapsedShieldRemainsVisuallyCollapsed() {
        var rejected = new BeamWeaponRuntime.BeamSolution(
                false,
                BeamWeaponRuntime.Failure.FIRE_CONTROL_INSUFFICIENT,
                42L,
                0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d, 0d);
        var engineering = Stage175ICombatTestContentPack.loadDoctrines();
        var hull = engineering.findHull("hull.test_doctrine_destroyer_v1");
        var pristine = new ShipDamageRuntime.Snapshot(
                hull.compartments().stream().collect(java.util.stream.Collectors.toMap(value -> value.id(), value -> 1d)),
                ShipEngineeringState.DamageState.pristine());
        ShieldFieldRuntime.Definition definition = new ShieldFieldRuntime.Definition(
                "shield-test", 10_000d, 20_000d, 5_000d, 0.8d, 0.1d, 2d, 0d, Math.PI);
        ShieldFieldRuntime.State collapsed = new ShieldFieldRuntime.State(0d, 0d, true, 1d, 1d);

        var snapshot = new Stage175ITacticalVisualProjection()
                .addShip(15L, hull, pristine, 0d, 0d, 0d, 0d, definition, collapsed)
                .addBeam(16L, 0d, 0d, 1d, 1d, rejected)
                .snapshot();

        assertTrue(snapshot.beams().isEmpty());
        assertEquals(1, snapshot.shields().size());
        assertTrue(snapshot.shields().get(0).collapsed());
        assertEquals(0d, snapshot.shields().get(0).reserveFraction(), 0d);
        assertFalse(snapshot.ships().get(0).wreck());
    }
}
