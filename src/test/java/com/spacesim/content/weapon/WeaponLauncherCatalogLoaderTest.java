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
import static org.junit.jupiter.api.Assertions.assertThrows;

class WeaponLauncherCatalogLoaderTest {
    @Test
    void defaultProfileLinksToExistingWeaponModuleAndPhysicalAmmoInterface() {
        WeaponLauncherCatalog catalog = WeaponLauncherCatalogLoader.loadDefault();

        assertEquals(1, catalog.getProfiles().size());
        assertEquals(64, catalog.getFingerprint().length());
        WeaponLauncherCatalog.LauncherProfile profile = catalog.findByModuleId("module.railgun_large_v1");
        assertNotNull(profile);
        assertEquals(Family.KINETIC, profile.family());
        assertEquals("kinetic_magazine_feed", profile.ammunitionInterfaceId());
        assertEquals(4d, profile.cycleTimeSeconds(), 1e-12d);
    }

    @Test
    void rejectsUnknownNonWeaponOrWrongFeedReferences() {
        String json = defaultJson();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();

        assertThrows(IllegalArgumentException.class, () -> WeaponLauncherCatalogLoader.parse(
                json.replace("module.railgun_large_v1", "module.missing_v1"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponLauncherCatalogLoader.parse(
                json.replace("module.railgun_large_v1", "module.main_drive_escort_v1"), engineering));
        assertThrows(IllegalArgumentException.class, () -> WeaponLauncherCatalogLoader.parse(
                json.replace("kinetic_magazine_feed", "missing_feed"), engineering));
    }

    @Test
    void repeatedParseHasStableSemanticFingerprint() {
        String json = defaultJson();
        ShipEngineeringCatalog engineering = ShipEngineeringCatalogLoader.loadDefault();
        assertEquals(
                WeaponLauncherCatalogLoader.parse(json, engineering).getFingerprint(),
                WeaponLauncherCatalogLoader.parse("\n" + json + "\n", engineering).getFingerprint());
    }

    private static String defaultJson() {
        try (InputStream stream = WeaponLauncherCatalogLoaderTest.class.getClassLoader()
                .getResourceAsStream(WeaponLauncherCatalogLoader.DEFAULT_RESOURCE)) {
            if (stream == null) {
                throw new IllegalStateException("Missing launcher test resource");
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
