package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalogLoader;
import com.spacesim.ship.HeavyImpactResolver.OutsideCalibrationDomainException;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HeavyImpactResidualCalibrationTest {
    @Test
    void eachLayerValidatesCurrentResidualVelocityNotOriginalImpactVelocity() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        ShipProtectionCatalog protection = ShipProtectionCatalogLoader.loadDefault(engineering);
        HeavyImpactResolver resolver = new HeavyImpactResolver(engineering, protection);
        ProjectileBody projectile = new ProjectileBody(
                501L,
                99L,
                10L,
                "material.high_strength_steel_v1",
                ProjectileShape.DART,
                0.2d,
                0.02d,
                1d,
                0d,
                0d,
                1500d,
                0d);

        OutsideCalibrationDomainException failure = assertThrows(
                OutsideCalibrationDomainException.class,
                () -> resolver.resolve(projectile, "protection.escort_structural_v1", 0d));

        assertEquals("response.synthetic_heavy_v1", failure.getResponseSurfaceId());
        assertTrue(failure.getMessage().contains("velocityMps="));
    }
}
