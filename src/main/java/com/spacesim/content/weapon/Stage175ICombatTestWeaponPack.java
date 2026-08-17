package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage175ICombatTestContentPack;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads production-schema Stage 17.5I ammunition and launcher sidecars against the doctrine catalog.
 *
 * <p>The sidecars are content-provisional acceptance vocabulary. Kinetic and guided bodies are
 * validated by the ordinary Stage-17.5E loaders; beam weapons intentionally remain energy-only and
 * are therefore not given a fake ammunition interface.</p>
 */
public final class Stage175ICombatTestWeaponPack {
    /** Physical ammunition resource. */
    public static final String AMMUNITION_RESOURCE = "data/content/stage17_5i-weapon-ammunition-v1.json";
    /** Physical launcher-profile resource. */
    public static final String LAUNCHER_RESOURCE = "data/content/stage17_5i-weapon-launchers-v1.json";

    private Stage175ICombatTestWeaponPack() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads physical kinetic/guided ammunition against the Stage-17.5I engineering materials.
     *
     * @return validated immutable ammunition catalog
     */
    public static WeaponAmmunitionCatalog loadAmmunition() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        return WeaponAmmunitionCatalogLoader.parse(readResource(AMMUNITION_RESOURCE), engineering);
    }

    /**
     * Loads launcher/feed profiles against the Stage-17.5I fitted weapon modules.
     *
     * @return validated immutable launcher catalog
     */
    public static WeaponLauncherCatalog loadLaunchers() {
        ShipEngineeringCatalog engineering = Stage175ICombatTestContentPack.loadDoctrines();
        return WeaponLauncherCatalogLoader.parse(readResource(LAUNCHER_RESOURCE), engineering);
    }

    private static String readResource(String resource) {
        ClassLoader classLoader = Stage175ICombatTestWeaponPack.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage 17.5I weapon content: " + resource);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage 17.5I weapon content: " + resource, exception);
        }
    }
}
