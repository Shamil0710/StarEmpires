package com.spacesim.ui;

import com.spacesim.content.weapon.Stage175ICombatTestWeaponPack;
import com.spacesim.ship.GuidedWeaponBody;
import com.spacesim.ship.ProjectileBody;
import com.spacesim.ship.WeaponDefinition;
import com.spacesim.ui.TacticalPrototypeVisualSnapshot.BodyKind;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class Stage175IPhysicalOrdnanceScaleTest {
    @Test
    void kineticProjectileKeepsExactPhysicalDimensionsInVisualProjection() {
        ProjectileBody projectile = new ProjectileBody(
                1001L,
                10L,
                11L,
                "material.test",
                WeaponDefinition.ProjectileShape.ROD,
                1.5d,
                0.08d,
                150d,
                80_000d,
                12_000d,
                2_500d,
                0d);

        var body = new Stage175ITacticalVisualProjection()
                .addKinetic(projectile)
                .snapshot()
                .bodies()
                .get(0);

        assertEquals(BodyKind.KINETIC_PROJECTILE, body.kind());
        assertEquals(projectile.lengthM(), body.lengthM(), 0d);
        assertEquals(projectile.diameterM(), body.widthM(), 0d);
    }

    @Test
    void guidedMissileKeepsExactPhysicalDimensionsInVisualProjection() {
        var content = Stage175ICombatTestWeaponPack.loadAmmunition()
                .findGuided("ammo.test_anti_ship_missile_2t_v1");
        GuidedWeaponBody missile = GuidedWeaponBody.launch(
                1002L,
                10L,
                99L,
                content.toRuntimeWeapon(),
                content.materialId(),
                content.shape(),
                content.lengthM(),
                content.diameterM(),
                content.impactPayloadId(),
                0d,
                0d,
                500d,
                0d);

        var body = new Stage175ITacticalVisualProjection()
                .addGuided(missile, false)
                .snapshot()
                .bodies()
                .get(0);

        assertEquals(BodyKind.GUIDED_MISSILE, body.kind());
        assertEquals(missile.lengthM(), body.lengthM(), 0d);
        assertEquals(missile.diameterM(), body.widthM(), 0d);
    }
}
