package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalogLoader;
import com.spacesim.ship.HeavyImpactResolver.Outcome;
import com.spacesim.ship.HeavyImpactResolver.OutsideCalibrationDomainException;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeavyImpactAndShipDamageRuntimeTest {
    @Test
    void boundedResponseStopsRicochetsAndPerforatesWithoutExtrapolation() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.loadDefault(engineering);
        HeavyImpactResolver resolver = new HeavyImpactResolver(engineering, protection);

        ProjectileBody lowEnergy = projectile(1L, 1d, 1d, 1000d);
        HeavyImpactResolver.ImpactResult stopped = resolver.resolve(
                lowEnergy, "protection.escort_structural_v1", 0d);
        assertFalse(stopped.penetrated());
        assertEquals(Outcome.STOPPED, stopped.outcome());
        assertEquals(0d, stopped.residualProjectileEnergyJ(), 1e-9d);
        assertFalse(stopped.layerInteractions().isEmpty());

        ProjectileBody highEnergy = projectile(2L, 150d, 0.12d, 9000d);
        HeavyImpactResolver.ImpactResult penetrated = resolver.resolve(
                highEnergy, "protection.escort_structural_v1", 0d);
        assertTrue(penetrated.penetrated());
        assertEquals(Outcome.PERFORATED, penetrated.outcome());
        assertTrue(penetrated.residualProjectileEnergyJ() > 0d);
        assertTrue(penetrated.fragments().massKg() > 0d);
        assertTrue(penetrated.fragments().kineticEnergyJ() > 0d);
        assertTrue(penetrated.fragments().internal());
        assertTrue(penetrated.internalDamageEnergyJ() > penetrated.residualProjectileEnergyJ());

        HeavyImpactResolver.ImpactResult ricochet = resolver.resolve(
                highEnergy, "protection.escort_structural_v1", Math.toRadians(75d));
        assertEquals(Outcome.RICOCHET, ricochet.outcome());
        assertFalse(ricochet.penetrated());
        assertEquals(highEnergy.kineticEnergyJ() * 0.65d,
                ricochet.residualProjectileEnergyJ(), 1e-6d);
        assertEquals(0d, ricochet.internalDamageEnergyJ(), 0d);
        assertFalse(ricochet.fragments().internal());

        ProjectileBody outOfDomain = projectile(3L, 150d, 0.12d, 500d);
        assertThrows(OutsideCalibrationDomainException.class,
                () -> resolver.resolve(outOfDomain, "protection.escort_structural_v1", 0d));
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve(highEnergy, "protection.missing", 0d));
    }

    @Test
    void hitLocationRoutesDamageOnlyIntoLocalCompartmentAndInstalledMounts() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.loadDefault(engineering);
        ShipEngineeringCatalog.HullDefinition hull = engineering.findHull("hull.escort_destroyer_v1");
        ShipProtectionCatalog.HullDamageLayout layout = protection.findHullDamageLayout(hull.id());
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        HeavyImpactResolver impactResolver = new HeavyImpactResolver(engineering, protection);
        HeavyImpactResolver.ImpactResult impact = impactResolver.resolve(
                projectile(4L, 150d, 0.12d, 9000d), hull.structuralProtectionStackId(), 0d);

        ShipDamageRuntime runtime = new ShipDamageRuntime();
        ShipDamageRuntime.Snapshot pristine = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        ShipDamageRuntime.DamageEvent weaponHit = runtime.applyImpact(
                hull, fit, layout, pristine, impact, new Vector3d(0d, 72d, 0d));

        assertEquals("weapons", weaponHit.compartmentId());
        assertTrue(weaponHit.snapshot().compartmentIntegrityById().get("weapons") < 1d);
        assertEquals(1d, weaponHit.snapshot().compartmentIntegrityById().get("engineering"), 1e-12d);
        assertEquals(1, weaponHit.damagedMounts().size());
        assertEquals("weapon_spinal", weaponHit.damagedMounts().get(0));
        assertTrue(weaponHit.snapshot().moduleDamage().moduleIntegrityByMount().get("weapon_spinal") < 1d);

        ShipDamageRuntime.DamageEvent engineeringHit = runtime.applyImpact(
                hull, fit, layout, pristine, impact, new Vector3d(0d, -45d, 0d));
        assertEquals("engineering", engineeringHit.compartmentId());
        assertTrue(engineeringHit.damagedMounts().contains("core_reactor"));
        assertTrue(engineeringHit.damagedMounts().contains("core_drive"));
        assertTrue(engineeringHit.damagedMounts().contains("utility_thermal"));
        assertFalse(engineeringHit.damagedMounts().contains("utility_sensor"));
    }

    private static ProjectileBody projectile(
            long id, double massKg, double diameterM, double velocityMps) {
        return new ProjectileBody(
                id,
                99L,
                10L,
                "material.high_strength_steel_v1",
                ProjectileShape.DART,
                1d,
                diameterM,
                massKg,
                0d,
                0d,
                velocityMps,
                0d);
    }
}
