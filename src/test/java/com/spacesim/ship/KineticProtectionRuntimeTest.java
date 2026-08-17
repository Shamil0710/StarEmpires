package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalogLoader;
import com.spacesim.ship.KineticProtectionRuntime.ShieldInput;
import com.spacesim.ship.ShipEngineeringState.InstalledFit;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KineticProtectionRuntimeTest {
    @Test
    void finiteShieldCanReduceButNotDeleteResidualArmorAndDamagePath() {
        Fixture fixture = fixture();
        ShieldFieldRuntime.Definition shieldDefinition = new ShieldFieldRuntime.Definition(
                "shield", 1_000_000_000d, 1_000_000_000d, 0d, 1d, 0.05d, 0d,
                0d, Math.PI);
        ShieldFieldRuntime.State shieldState = ShieldFieldRuntime.State.charged(shieldDefinition);

        KineticProtectionRuntime.Result result = fixture.runtime.resolve(
                projectile(1L), new ShieldInput(shieldDefinition, shieldState),
                0d, 1d, fixture.hull.structuralProtectionStackId(), 0d,
                fixture.hull, fixture.fit, fixture.layout, fixture.damage, new Vector3d(0d, 72d, 0d));

        assertNotNull(result.shieldInteraction());
        assertTrue(result.shieldInteraction().absorbedEnergyJ() > 0d);
        assertNotNull(result.armorEntryProjectile());
        assertTrue(result.armorEntryProjectile().kineticEnergyJ() < projectile(1L).kineticEnergyJ());
        assertTrue(result.armorReached());
        assertNotNull(result.armorImpact());
        assertTrue(result.internalDamageOccurred());
        assertNotNull(result.damageEvent());
    }

    @Test
    void sufficientlyStrongCoveredShieldStopsBodyBeforeArmorWithoutDeletingBookkeeping() {
        Fixture fixture = fixture();
        ShieldFieldRuntime.Definition shieldDefinition = new ShieldFieldRuntime.Definition(
                "shield", 10_000_000_000d, 10_000_000_000d, 0d, 1d, 0.05d, 0d,
                0d, Math.PI);
        ShieldFieldRuntime.State shieldState = ShieldFieldRuntime.State.charged(shieldDefinition);

        KineticProtectionRuntime.Result result = fixture.runtime.resolve(
                projectile(2L), new ShieldInput(shieldDefinition, shieldState),
                0d, 1d, fixture.hull.structuralProtectionStackId(), 0d,
                fixture.hull, fixture.fit, fixture.layout, fixture.damage, new Vector3d(0d, 72d, 0d));

        assertNotNull(result.shieldInteraction());
        assertTrue(result.shieldInteraction().absorbedEnergyJ() > 0d);
        assertFalse(result.armorReached());
        assertFalse(result.internalDamageOccurred());
        assertNull(result.armorEntryProjectile());
        assertNull(result.armorImpact());
        assertNull(result.postProtectionProjectile());
        assertNull(result.damageEvent());
    }

    private static Fixture fixture() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.loadDefault(engineering);
        ShipEngineeringCatalog.HullDefinition hull = engineering.findHull("hull.escort_destroyer_v1");
        ShipProtectionCatalog.HullDamageLayout layout = protection.findHullDamageLayout(hull.id());
        InstalledFit fit = InstalledFit.fromDemonstrator(
                engineering.findDemonstratorFit("fit.escort_destroyer_schema_v1"));
        ShipDamageRuntime damageRuntime = new ShipDamageRuntime();
        ShipDamageRuntime.Snapshot damage = ShipDamageRuntime.Snapshot.pristine(hull, layout);
        KineticProtectionRuntime runtime = new KineticProtectionRuntime(
                new ShieldFieldRuntime(),
                new HeavyImpactResolver(engineering, protection),
                damageRuntime);
        return new Fixture(runtime, hull, fit, layout, damage);
    }

    private static ProjectileBody projectile(long id) {
        return new ProjectileBody(
                id, 99L, 10L, "material.high_strength_steel_v1", ProjectileShape.DART,
                1d, 0.12d, 150d, 0d, 0d, 9000d, 0d);
    }

    private record Fixture(
            KineticProtectionRuntime runtime,
            ShipEngineeringCatalog.HullDefinition hull,
            InstalledFit fit,
            ShipProtectionCatalog.HullDamageLayout layout,
            ShipDamageRuntime.Snapshot damage) { }
}
