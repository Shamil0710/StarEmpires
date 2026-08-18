package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.content.ship.ShipProtectionCatalog;
import com.spacesim.content.ship.ShipProtectionCatalogLoader;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;
import com.spacesim.content.ship.Stage175ICombatTestProtectionPack;
import com.spacesim.ship.HeavyImpactResolver.OutsideCalibrationDomainException;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
                1100d,
                0d);

        OutsideCalibrationDomainException failure = assertThrows(
                OutsideCalibrationDomainException.class,
                () -> resolver.resolve(projectile, "protection.escort_structural_v1", 0d));

        assertEquals("response.synthetic_heavy_v1", failure.getResponseSurfaceId());
        assertTrue(failure.getMessage().contains("velocityMps="));
    }

    @Test
    void stage19PromotionContainsTheAuthoredTwoTonneStrikeBodyWithoutFurtherExtrapolation() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        ShipProtectionCatalog protection = Stage175ICombatTestProtectionPack.load();
        var response = engineering.findResponseSurface(Stage175ICombatTestContentPack.STAGE19_PROMOTED_RESPONSE_ID);
        assertEquals(Stage175ICombatTestContentPack.STAGE19_MAX_PROJECTILE_MASS_KG,
                response.calibrationDomain().maxProjectileMassKg(), 0d);
        assertEquals("stage19_strike_2t_provisional_test_only", response.calibrationDomain().confidenceLabel());

        HeavyImpactResolver resolver = new HeavyImpactResolver(engineering, protection);
        ProjectileBody twoTonneStrike = new ProjectileBody(
                502L,
                100L,
                11L,
                "material.stage17_5i_doctrine_alloy_v1",
                ProjectileShape.SHELL,
                4.0d,
                0.65d,
                2_000d,
                0d,
                0d,
                5_000d,
                0d);
        assertDoesNotThrow(() -> resolver.resolve(
                twoTonneStrike,
                "protection.stage17_5i_doctrine_v1",
                0d));

        ProjectileBody beyondPromotedEnvelope = new ProjectileBody(
                503L,
                100L,
                11L,
                "material.stage17_5i_doctrine_alloy_v1",
                ProjectileShape.SHELL,
                4.0d,
                0.65d,
                2_001d,
                0d,
                0d,
                5_000d,
                0d);
        assertThrows(OutsideCalibrationDomainException.class, () -> resolver.resolve(
                beyondPromotedEnvelope,
                "protection.stage17_5i_doctrine_v1",
                0d));
    }
}