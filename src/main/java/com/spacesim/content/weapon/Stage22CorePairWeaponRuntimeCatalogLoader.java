package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Loads M22.6 core-faction weapon operating content through the existing Stage-17.5E authorities.
 *
 * <p>This class is a content bridge, not a combat system. Engineering module mass, power, thermal,
 * recoil and muzzle velocity remain owned by the Stage-22 engineering catalogs; launcher timing and
 * physical ammunition identity are validated by the common launcher/ammunition loaders and consumed
 * by {@code ShipWeaponEngineeringAdapter}.</p>
 */
public final class Stage22CorePairWeaponRuntimeCatalogLoader {
    /** Empire launcher resource. */
    public static final String EMPIRE_LAUNCHER_RESOURCE =
            "data/content/stage22-empire-weapon-launchers-v1.json";
    /** Empire ammunition resource. */
    public static final String EMPIRE_AMMUNITION_RESOURCE =
            "data/content/stage22-empire-weapon-ammunition-v1.json";
    /** Industrial Union launcher resource. */
    public static final String UNION_LAUNCHER_RESOURCE =
            "data/content/stage22-industrial-union-weapon-launchers-v1.json";
    /** Industrial Union ammunition resource. */
    public static final String UNION_AMMUNITION_RESOURCE =
            "data/content/stage22-industrial-union-weapon-ammunition-v1.json";

    private Stage22CorePairWeaponRuntimeCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /** @return validated Empire weapon runtime content */
    public static RuntimeContent loadEmpire() {
        return load(
                Stage22EmpireEngineeringCatalogLoader.loadDefault(),
                EMPIRE_LAUNCHER_RESOURCE,
                EMPIRE_AMMUNITION_RESOURCE);
    }

    /** @return validated Industrial Union weapon runtime content */
    public static RuntimeContent loadIndustrialUnion() {
        return load(
                Stage22IndustrialUnionEngineeringCatalogLoader.loadDefault(),
                UNION_LAUNCHER_RESOURCE,
                UNION_AMMUNITION_RESOURCE);
    }

    private static RuntimeContent load(
            ShipEngineeringCatalog engineering,
            String launcherResource,
            String ammunitionResource) {
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        WeaponLauncherCatalog launchers = WeaponLauncherCatalogLoader.parse(
                readResource(launcherResource), checkedEngineering);
        WeaponAmmunitionCatalog ammunition = WeaponAmmunitionCatalogLoader.parse(
                readResource(ammunitionResource), checkedEngineering);
        if (launchers.getProfiles().size() != 1 || ammunition.getKineticAmmunition().size() != 1
                || !ammunition.getGuidedAmmunition().isEmpty()) {
            throw new IllegalStateException("M22.6 core weapon bridge must expose one kinetic family per side");
        }
        return new RuntimeContent(checkedEngineering, launchers, ammunition);
    }

    private static String readResource(String path) {
        try (InputStream stream = Stage22CorePairWeaponRuntimeCatalogLoader.class
                .getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing M22.6 weapon runtime resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read M22.6 weapon runtime resource: " + path, exception);
        }
    }

    /**
     * One faction's common-runtime combat content.
     *
     * @param engineering accepted Stage-22 engineering catalog
     * @param launchers common Stage-17.5E launcher profiles
     * @param ammunition common Stage-17.5E physical ammunition catalog
     */
    public record RuntimeContent(
            ShipEngineeringCatalog engineering,
            WeaponLauncherCatalog launchers,
            WeaponAmmunitionCatalog ammunition) {
        /**
         * Validates immutable bridge content.
         *
         * @param engineering accepted Stage-22 engineering catalog
         * @param launchers common Stage-17.5E launcher profiles
         * @param ammunition common Stage-17.5E physical ammunition catalog
         */
        public RuntimeContent {
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(launchers, "launchers");
            Objects.requireNonNull(ammunition, "ammunition");
        }
    }
}
