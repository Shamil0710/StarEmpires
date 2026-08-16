package com.spacesim.ship;

import com.spacesim.ship.WeaponDefinition.GuidedWeapon;
import com.spacesim.ship.WeaponDefinition.Launcher;
import com.spacesim.ship.WeaponLoadoutState.FeedBinding;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeaponLoadoutAndGuidedBodyTest {
    private static final Launcher LAUNCHER = new Launcher(
            "launcher.missile_cell_v1",
            "missile_feed",
            1d,
            6d,
            2);

    @Test
    void loadoutStoresOnlyAmmunitionIdentityAndRejectsTwoTypesInOneFeed() {
        WeaponLoadoutState state = new WeaponLoadoutState(List.of(
                new FeedBinding("weapon_port", "missile_feed", "ammo.interceptor_v1"),
                new FeedBinding("weapon_spinal", "kinetic_feed", "ammo.rail_dart_v1")));

        assertEquals("ammo.interceptor_v1", state.ammunitionContentId("weapon_port", "missile_feed").orElseThrow());
        assertTrue(state.ammunitionContentId("weapon_port", "kinetic_feed").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new WeaponLoadoutState(List.of(
                new FeedBinding("weapon_port", "missile_feed", "ammo.interceptor_v1"),
                new FeedBinding("weapon_port", "missile_feed", "ammo.torpedo_v1"))));
    }

    @Test
    void launcherCyclePreventsFreeImmediateRepeatShot() {
        WeaponMountRuntime runtime = new WeaponMountRuntime();
        WeaponMountRuntime.RuntimeState initial = WeaponMountRuntime.RuntimeState.empty();

        WeaponMountRuntime.RuntimeState cycling = runtime.commitShot(initial, "weapon_port", LAUNCHER);
        assertFalse(runtime.ready(cycling, "weapon_port"));
        assertThrows(IllegalStateException.class, () -> runtime.commitShot(cycling, "weapon_port", LAUNCHER));

        WeaponMountRuntime.RuntimeState almost = runtime.advance(cycling, 5.5d);
        assertFalse(runtime.ready(almost, "weapon_port"));
        WeaponMountRuntime.RuntimeState ready = runtime.advance(almost, 0.5d);
        assertTrue(runtime.ready(ready, "weapon_port"));
    }

    @Test
    void guidanceKillPreservesPhysicalBodyMomentumMassAndBallisticMotion() {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.interceptor_v1",
                "seeker.radar_v1",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                300d);
        GuidedWeaponBody launched = GuidedWeaponBody.launch(
                5001L,
                91L,
                777L,
                definition,
                10_000d,
                -5_000d,
                1_500d,
                300d);
        GuidedWeaponBody accelerated = launched.burn(1d, 0d, 5d);
        double massBeforeKill = accelerated.currentMassKg();
        double energyBeforeKill = accelerated.kineticEnergyJ();

        GuidedWeaponBody disabled = accelerated.disableGuidance();
        GuidedWeaponBody later = disabled.advanceBallistic(20d);

        assertFalse(disabled.guidanceAvailable());
        assertEquals(massBeforeKill, disabled.currentMassKg(), 1e-9d);
        assertEquals(energyBeforeKill, disabled.kineticEnergyJ(), 1e-6d);
        assertEquals(disabled.velocityXMps(), later.velocityXMps(), 1e-12d);
        assertEquals(disabled.velocityYMps(), later.velocityYMps(), 1e-12d);
        assertTrue(Math.hypot(later.xM() - disabled.xM(), later.yM() - disabled.yM()) > 20_000d);
    }

    @Test
    void guidedBurnConsumesRealPropellantAndReducesRemainingDeltaV() {
        GuidedWeapon definition = new GuidedWeapon(
                "ammo.interceptor_v1",
                "seeker.radar_v1",
                800d,
                200d,
                20_000d,
                5_000d,
                40d,
                0.0005d,
                300d);
        GuidedWeaponBody initial = GuidedWeaponBody.launch(
                5002L,
                91L,
                778L,
                definition,
                0d,
                0d,
                0d,
                0d);

        GuidedWeaponBody burned = initial.burn(0d, 1d, 10d);

        assertTrue(burned.remainingPropellantKg() < initial.remainingPropellantKg());
        assertTrue(burned.remainingDeltaVMps() < initial.remainingDeltaVMps());
        assertTrue(burned.velocityYMps() > 0d);
    }
}
