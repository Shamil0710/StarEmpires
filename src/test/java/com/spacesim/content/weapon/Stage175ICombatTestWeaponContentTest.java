package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.WeaponDefinition.Family;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class Stage175ICombatTestWeaponContentTest {
    private static final String ENGINEERING = "data/content/stage17_5i-combat-test-engineering-v1.json";
    private static final String AMMUNITION = "data/content/stage17_5i-combat-test-ammunition-v1.json";
    private static final String LAUNCHERS = "data/content/stage17_5i-combat-test-launchers-v1.json";

    @Test
    void physicalAmmunitionAndLauncherProfilesUseOrdinaryProductionLoaders() {
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.parse(read(ENGINEERING));
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.parse(read(AMMUNITION), engineering);
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.parse(read(LAUNCHERS), engineering);

        assertEquals(2, ammunition.getKineticAmmunition().size());
        assertEquals(2, ammunition.getGuidedAmmunition().size());
        assertNotNull(ammunition.findKinetic("ammo.ct_rail_dart_50kg_v1"));
        assertNotNull(ammunition.findKinetic("ammo.ct_rail_dart_200kg_v1"));
        assertNotNull(ammunition.findGuided("ammo.ct_antiship_missile_v1"));
        assertNotNull(ammunition.findGuided("ammo.ct_interceptor_v1"));

        assertEquals(4, launchers.getProfiles().size());
        assertEquals(Family.KINETIC, launchers.findByModuleId("module.ct_kinetic_medium_v1").family());
        assertEquals(Family.KINETIC, launchers.findByModuleId("module.ct_kinetic_large_v1").family());
        assertEquals(Family.GUIDED, launchers.findByModuleId("module.ct_missile_medium_v1").family());
        assertEquals(Family.GUIDED, launchers.findByModuleId("module.ct_pd_v1").family());
    }

    private static String read(String resource) {
        ClassLoader classLoader = Stage175ICombatTestWeaponContentTest.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            assertNotNull(stream, resource);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read " + resource, exception);
        }
    }
}
