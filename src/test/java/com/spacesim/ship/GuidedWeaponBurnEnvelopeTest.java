package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.ProjectileShape;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GuidedWeaponBurnEnvelopeTest {
    @Test
    void poweredBurnCannotExceedAuthoredLifetimeEvenWhenPropellantRemains() {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.burn_envelope_test_v1",
                "seeker.test_v1",
                800d,
                200d,
                100_000d,
                10_000d,
                5d,
                0.0005d,
                0d);
        GuidedWeaponBody body = launch(definition);

        GuidedWeaponBody after = body.burn(1d, 0d, 10d);

        assertEquals(0d, after.remainingPoweredBurnSeconds(), 1e-12d);
        assertEquals(150d, after.remainingPropellantKg(), 1e-9d,
                "five authored powered seconds at 10 kg/s must leave unusable residual propellant physical mass");
        assertEquals(0d, after.remainingDeltaVMps(), 1e-12d,
                "no powered lifetime means no further physically deliverable delta-v");

        GuidedWeaponBody secondAttempt = after.burn(1d, 0d, 1d);
        assertEquals(after, secondAttempt,
                "expired propulsion lifetime must not create free delta-v on later simulation ticks");
    }

    @Test
    void fragmentedGuidanceBurnsConsumeTheSameFinitePoweredLifetime() {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.fragmented_burn_test_v1",
                "seeker.test_v1",
                500d,
                100d,
                50_000d,
                10_000d,
                4d,
                0.0005d,
                0d);
        GuidedWeaponBody body = launch(definition);

        GuidedWeaponBody first = body.burn(1d, 0d, 1.25d);
        GuidedWeaponBody second = first.burn(1d, 0d, 1.25d);
        GuidedWeaponBody third = second.burn(1d, 0d, 4d);

        assertEquals(0d, third.remainingPoweredBurnSeconds(), 1e-12d);
        assertEquals(80d, third.remainingPropellantKg(), 1e-9d,
                "four total powered seconds at 5 kg/s must consume exactly 20 kg regardless of tick partitioning");
        assertTrue(third.speedMps() > body.speedMps());
    }

    private static GuidedWeaponBody launch(GuidedWeapon definition) {
        return GuidedWeaponBody.launch(
                199_001L,
                199_101L,
                199_201L,
                definition,
                "material.stage17_5i_doctrine_alloy_v1",
                ProjectileShape.SHELL,
                3d,
                0.4d,
                null,
                0d,
                0d,
                0d,
                0d);
    }
}
