package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidedBodyCollisionResolverTest {
    @Test
    void inelasticResidualsPreserveMassAndLinearMomentumWithoutAbstractDamage() {
        GuidedWeaponBody interceptor = body(198_001L, 10L, 20L, 500d, 250d, 300d, 40d);
        GuidedWeaponBody threat = body(195_001L, 30L, 40L, 1400d, 600d, -120d, -15d);
        double massBefore = interceptor.currentMassKg() + threat.currentMassKg();
        double momentumXBefore = interceptor.currentMassKg() * interceptor.velocityXMps()
                + threat.currentMassKg() * threat.velocityXMps();
        double momentumYBefore = interceptor.currentMassKg() * interceptor.velocityYMps()
                + threat.currentMassKg() * threat.velocityYMps();
        double kineticBefore = interceptor.kineticEnergyJ() + threat.kineticEnergyJ();

        var result = new GuidedBodyCollisionResolver().resolve(
                interceptor,
                threat,
                100d,
                200d,
                77L);

        double massAfter = result.residuals().stream().mapToDouble(ProjectileBody::massKg).sum();
        double momentumXAfter = result.residuals().stream().mapToDouble(ProjectileBody::momentumXNs).sum();
        double momentumYAfter = result.residuals().stream().mapToDouble(ProjectileBody::momentumYNs).sum();
        double kineticAfter = result.residuals().stream().mapToDouble(ProjectileBody::kineticEnergyJ).sum();

        assertEquals(massBefore, massAfter, 1e-9d);
        assertEquals(momentumXBefore, momentumXAfter, 1e-6d);
        assertEquals(momentumYBefore, momentumYAfter, 1e-6d);
        assertTrue(kineticAfter <= kineticBefore + 1e-6d);
        assertEquals(kineticBefore - kineticAfter, result.dissipatedKineticEnergyJ(), 1e-6d);
        assertEquals(interceptor.materialId(), result.interceptorResidual().materialId());
        assertEquals(threat.materialId(), result.threatResidual().materialId());
        assertEquals(interceptor.sourceEntityId(), result.interceptorResidual().sourceEntityId());
        assertEquals(threat.sourceEntityId(), result.threatResidual().sourceEntityId());
    }

    private static GuidedWeaponBody body(
            long bodyId,
            long sourceId,
            long targetId,
            double dryMassKg,
            double propellantKg,
            double velocityXMps,
            double velocityYMps) {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.test_body_" + bodyId,
                "seeker.test_v1",
                dryMassKg,
                propellantKg,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                0d);
        return GuidedWeaponBody.launch(
                bodyId,
                sourceId,
                targetId,
                definition,
                "material.stage17_5i_doctrine_alloy_v1",
                ProjectileShape.SHELL,
                3d,
                0.4d,
                null,
                0d,
                0d,
                velocityXMps,
                velocityYMps);
    }
}
