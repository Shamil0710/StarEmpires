package com.spacesim.content.weapon;

import com.spacesim.content.weapon.WeaponAmmunitionCatalog.GuidedEngagementRole;
import com.spacesim.ship.WeaponDefinition.Family;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Stage175ICombatTestWeaponPackTest {
    @Test
    void physicalAmmunitionLoadsThroughTheOrdinaryStage175ELoader() {
        WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();

        assertEquals(2, ammunition.getKineticAmmunition().size());
        assertEquals(3, ammunition.getGuidedAmmunition().size());
        assertNotNull(ammunition.findKinetic("ammo.test_kinetic_dart_150kg_v1"));
        assertNotNull(ammunition.findKinetic("ammo.test_pd_slug_5kg_v1"));
        assertNotNull(ammunition.findGuided("ammo.test_anti_ship_missile_2t_v1"));
        assertNotNull(ammunition.findGuided("ammo.test_interceptor_750kg_v1"));
        var decoy = ammunition.findGuided("ammo.test_radar_repeater_decoy_300kg_v1");
        assertNotNull(decoy);
        assertEquals(GuidedEngagementRole.DECOY, decoy.engagementRole());
        assertEquals(300d, decoy.wetMassKg(), 1e-9d);
        assertTrue(decoy.signature().radarCrossSectionM2() > 0d);
        assertEquals(64, ammunition.getFingerprint().length());
    }

    @Test
    void guidedBodiesCloseTheirOwnPropellantAndDeltaVBudgets() {
        WeaponAmmunitionCatalog ammunition = Stage175ICombatTestWeaponPack.loadAmmunition();
        var missile = ammunition.findGuided("ammo.test_anti_ship_missile_2t_v1").toRuntimeWeapon();
        var interceptor = ammunition.findGuided("ammo.test_interceptor_750kg_v1").toRuntimeWeapon();
        var decoy = ammunition.findGuided("ammo.test_radar_repeater_decoy_300kg_v1").toRuntimeWeapon();

        assertTrue(missile.massFlowKgPerS() * missile.burnTimeSeconds() <= missile.propellantMassKg());
        assertTrue(interceptor.massFlowKgPerS() * interceptor.burnTimeSeconds() <= interceptor.propellantMassKg());
        assertTrue(decoy.massFlowKgPerS() * decoy.burnTimeSeconds() <= decoy.propellantMassKg());
        assertTrue(missile.idealDeltaVMps() > missile.terminalReserveMps());
        assertTrue(interceptor.idealDeltaVMps() > interceptor.terminalReserveMps());
        assertTrue(decoy.idealDeltaVMps() > decoy.terminalReserveMps());
    }

    @Test
    void launcherProfilesUseRealFittedAmmunitionInterfaces() {
        WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();

        assertEquals(3, launchers.getProfiles().size());
        assertEquals(Family.KINETIC, launchers.findByModuleId("module.test_weapon_kinetic_v1").family());
        assertEquals(Family.GUIDED, launchers.findByModuleId("module.test_weapon_missile_v1").family());
        assertEquals(Family.KINETIC, launchers.findByModuleId("module.test_weapon_pd_v1").family());
        assertEquals("guided_feed",
                launchers.findByModuleId("module.test_weapon_missile_v1").ammunitionInterfaceId());
        assertEquals(64, launchers.getFingerprint().length());
    }

    @Test
    void beamModuleIsIntentionallyNotGivenAFakeAmmunitionLauncherProfile() {
        WeaponLauncherCatalog launchers = Stage175ICombatTestWeaponPack.loadLaunchers();
        assertEquals(null, launchers.findByModuleId("module.test_weapon_beam_v1"));
    }
}
