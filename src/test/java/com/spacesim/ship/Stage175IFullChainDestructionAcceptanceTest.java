package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.KineticProtectionRuntime.ShieldInput;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.Stage175IFleetDoctrineCatalog.DoctrineId;
import com.spacesim.ui.Stage175ITacticalVisualProjection;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175IFullChainDestructionAcceptanceTest {
    private static final String PRIMARY_MOUNT = "weapon_primary";
    private static final String SHIELD_MOUNT = "utility_shield";
    private static final double SHIELD_INTERACTION_SECONDS = 1d;

    @Test
    void finitePhysicalMagazineCanDriveShieldArmorCompartmentsAndSubsystemsToWreckState() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
        var ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        var launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        var attackerDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.A_KINETIC_LINE);
        var targetDoctrine = Stage175IFleetDoctrineCatalog.get(DoctrineId.E_BALANCED_CONTROL);
        InstalledFit attackerFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(attackerDoctrine.fitId()));
        InstalledFit targetFit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit(targetDoctrine.fitId()));
        var hull = engineering.findHull(targetFit.hullId());
        var layout = protection.findHullDamageLayout(hull.id());
        var calculator = new DerivedShipCalculator(engineering);
        var pristineTarget = calculator.derive(
                hull, targetFit, targetDoctrine.initialConsumables(), DamageState.pristine());
        var attackerDerived = calculator.derive(
                engineering.findHull(attackerFit.hullId()),
                attackerFit,
                attackerDoctrine.initialConsumables(),
                DamageState.pristine());
        var primary = new ShipWeaponEngineeringAdapter().deriveKineticMounts(
                        attackerDerived,
                        ammunition,
                        launchers,
                        attackerDoctrine.weaponLoadout()).stream()
                .filter(value -> value.mountId().equals(PRIMARY_MOUNT))
                .findFirst()
                .orElseThrow();

        ShieldFieldRuntime shieldRuntime = new ShieldFieldRuntime();
        var fittedShield = new ShipShieldEngineeringAdapter().derive(pristineTarget).stream()
                .filter(value -> value.mountId().equals(SHIELD_MOUNT))
                .findFirst()
                .orElseThrow();
        ShieldFieldRuntime.State shieldState = fittedShield.chargedState(shieldRuntime);
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        KineticProtectionRuntime protectionRuntime = new KineticProtectionRuntime(
                shieldRuntime,
                new HeavyImpactResolver(engineering, protection),
                new ShipDamageRuntime());
        AmmunitionRuntime ammunitionRuntime = new AmmunitionRuntime();
        ConsumableState attackerStores = attackerDoctrine.initialConsumables();
        long initialPrimaryRounds = roundsOnMount(attackerStores, PRIMARY_MOUNT);
        long shots = 0L;
        double shieldAbsorbedJ = 0d;
        double internalDamageJ = 0d;
        KineticProtectionRuntime.Result lastImpact = null;

        while (!fullyDestroyed(hull.compartments(), layout, damage) && roundsOnMount(attackerStores, PRIMARY_MOUNT) > 0L) {
            CompartmentDefinition target = nextIncompleteCompartment(hull.compartments(), layout, damage);
            var spent = ammunitionRuntime.consumeOne(
                    attackerStores,
                    primary.mountId(),
                    primary.launcher(),
                    primary.round().massKg());
            attackerStores = spent.consumables();
            shots++;

            ProjectileBody projectile = new ProjectileBody(
                    50_000L + shots,
                    17_501L,
                    shots,
                    primary.round().materialId(),
                    primary.round().shape(),
                    primary.round().lengthM(),
                    primary.round().diameterM(),
                    primary.round().massKg(),
                    0d,
                    0d,
                    primary.round().muzzleVelocityMps(),
                    0d);
            ShieldInput shieldInput = shieldState.emitterIntegrity() > 0d
                    ? new ShieldInput(fittedShield.definition(), shieldState)
                    : null;
            lastImpact = protectionRuntime.resolve(
                    projectile,
                    shieldInput,
                    Math.PI,
                    SHIELD_INTERACTION_SECONDS,
                    hull.structuralProtectionStackId(),
                    0d,
                    hull,
                    targetFit,
                    layout,
                    damage,
                    new Vector3d(target.centerM().xM(), target.centerM().yM(), target.centerM().zM()));
            if (lastImpact.shieldInteraction() != null) {
                shieldAbsorbedJ += lastImpact.shieldInteraction().absorbedEnergyJ();
                shieldState = lastImpact.shieldInteraction().state();
            }
            if (lastImpact.damageEvent() != null) {
                internalDamageJ += lastImpact.damageEvent().compartmentDamageEnergyJ();
                damage = lastImpact.damageEvent().snapshot();
                double emitterIntegrity = damage.moduleDamage().moduleIntegrityByMount()
                        .getOrDefault(SHIELD_MOUNT, 1d);
                shieldState = shieldRuntime.withEmitterIntegrity(
                        fittedShield.definition(), shieldState, emitterIntegrity);
            }
        }

        assertNotNull(lastImpact);
        assertTrue(shots > 0L);
        assertTrue(shots <= initialPrimaryRounds, "destruction must remain bounded by the real fitted magazine");
        assertEquals(initialPrimaryRounds - shots, roundsOnMount(attackerStores, PRIMARY_MOUNT));
        assertTrue(shieldAbsorbedJ > 0d, "the charged fitted shield must absorb finite energy before collapse/damage");
        assertTrue(internalDamageJ > 0d, "residual penetrations must reach local compartment damage");
        assertTrue(fullyDestroyed(hull.compartments(), layout, damage),
                "finite physical doctrine-A ammunition must close the complete destruction path");

        var destroyedTarget = calculator.derive(
                hull, targetFit, targetDoctrine.initialConsumables(), damage.moduleDamage());
        assertTrue(destroyedTarget.accelerationMps2() < pristineTarget.accelerationMps2());
        assertEquals(0d, destroyedTarget.accelerationMps2(), 1e-12d,
                "destroyed propulsion must not retain acceleration capability");
        assertTrue(new ShipSensorEngineeringAdapter().derive(destroyedTarget).sensors().isEmpty(),
                "destroyed mission-core sensor must not survive as a usable capability");

        ShipDamageRuntime.Snapshot damageBeforeProjection = damage;
        var visual = new Stage175ITacticalVisualProjection()
                .addShip(
                        17_502L,
                        hull,
                        damage,
                        4_000d,
                        3_000d,
                        0d,
                        0d,
                        fittedShield.definition(),
                        shieldState)
                .addImpact(17_503L, 4_000d, 3_000d, lastImpact)
                .snapshot();

        assertTrue(visual.ships().get(0).wreck());
        assertEquals(6L, visual.bodies().stream().filter(value -> value.kind() == BodyKind.DEBRIS).count());
        assertTrue(visual.impacts().stream().anyMatch(value -> value.kind()
                == com.spacesim.ui.TacticalPrototypeVisualSnapshot.ImpactKind.ARMOR));
        assertEquals(damageBeforeProjection, damage,
                "presentation projection must not mutate the authoritative destruction snapshot");
    }

    private static CompartmentDefinition nextIncompleteCompartment(
            List<CompartmentDefinition> compartments,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        return compartments.stream()
                .filter(value -> !compartmentDestroyed(value.id(), layout, damage))
                .findFirst()
                .orElseThrow();
    }

    private static boolean fullyDestroyed(
            List<CompartmentDefinition> compartments,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        return compartments.stream().allMatch(value -> compartmentDestroyed(value.id(), layout, damage));
    }

    private static boolean compartmentDestroyed(
            String compartmentId,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) {
        if (damage.compartmentIntegrityById().getOrDefault(compartmentId, 1d) > 0d) {
            return false;
        }
        return layout.mounts().stream()
                .filter(value -> value.compartmentId().equals(compartmentId))
                .allMatch(value -> damage.moduleDamage().moduleIntegrityByMount()
                        .getOrDefault(value.mountId(), 1d) <= 0d);
    }

    private static long roundsOnMount(ConsumableState state, String mountId) {
        return state.interfaceLoads().stream()
                .filter(value -> value.kind() == InterfaceKind.AMMUNITION)
                .filter(value -> value.mountId().equals(mountId))
                .mapToLong(ShipEngineeringState.ConsumableLoad::itemCount)
                .sum();
    }
}
