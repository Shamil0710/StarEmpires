package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22CarrierEngineeringCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Objects;

/**
 * M22.8J tactical-content composition for production small craft.
 *
 * <p>The loader reuses every accepted core-pair launcher/ammunition definition unchanged and appends
 * only small-craft launcher profiles and physical ammunition bodies. The resulting engineering
 * catalogue is the same superset used by the persistent small-craft authority, so Stage-19 receives
 * no role-name or fighter-only combat rules.</p>
 */
public final class Stage22CarrierWeaponRuntimeCatalogLoader {
    /** Small-craft launcher-profile resource. */
    public static final String LAUNCHER_RESOURCE =
            "data/content/stage22-small-craft-weapon-launchers-v1.json";
    /** Small-craft physical ammunition resource. */
    public static final String AMMUNITION_RESOURCE =
            "data/content/stage22-small-craft-weapon-ammunition-v1.json";

    private Stage22CarrierWeaponRuntimeCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads core-pair and small-craft weapon content against one engineering superset.
     *
     * @return immutable Stage-19-ready production content
     */
    public static RuntimeContent loadCombined() {
        ShipEngineeringCatalog engineering = Stage22CarrierEngineeringCatalogLoader.loadDefault();
        Stage22CorePairWeaponRuntimeCatalogLoader.RuntimeContent core =
                Stage22CorePairWeaponRuntimeCatalogLoader.loadCombined();
        WeaponLauncherCatalog smallLaunchers = WeaponLauncherCatalogLoader.parse(
                readResource(LAUNCHER_RESOURCE), engineering);
        WeaponAmmunitionCatalog smallAmmunition = WeaponAmmunitionCatalogLoader.parse(
                readResource(AMMUNITION_RESOURCE), engineering);

        ArrayList<WeaponLauncherCatalog.LauncherProfile> launchers = new ArrayList<>();
        launchers.addAll(core.launchers().getProfiles());
        launchers.addAll(smallLaunchers.getProfiles());
        WeaponLauncherCatalog combinedLaunchers = new WeaponLauncherCatalog(
                core.launchers().getSchemaVersion(),
                core.launchers().getMigrationVersion(),
                launchers);

        ArrayList<WeaponAmmunitionCatalog.KineticAmmunitionDefinition> kinetic =
                new ArrayList<>();
        kinetic.addAll(core.ammunition().getKineticAmmunition());
        kinetic.addAll(smallAmmunition.getKineticAmmunition());
        ArrayList<WeaponAmmunitionCatalog.GuidedAmmunitionDefinition> guided =
                new ArrayList<>();
        guided.addAll(core.ammunition().getGuidedAmmunition());
        guided.addAll(smallAmmunition.getGuidedAmmunition());
        WeaponAmmunitionCatalog combinedAmmunition = new WeaponAmmunitionCatalog(
                core.ammunition().getSchemaVersion(),
                core.ammunition().getMigrationVersion(),
                kinetic,
                guided);

        for (WeaponLauncherCatalog.LauncherProfile profile : combinedLaunchers.getProfiles()) {
            if (engineering.findModule(profile.moduleId()) == null) {
                throw new IllegalStateException(
                        "Carrier weapon runtime references absent engineering module: "
                                + profile.moduleId());
            }
        }
        return new RuntimeContent(engineering, combinedLaunchers, combinedAmmunition);
    }

    private static String readResource(String path) {
        try (InputStream stream = Stage22CarrierWeaponRuntimeCatalogLoader.class
                .getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing M22.8J weapon resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read M22.8J weapon resource: " + path, exception);
        }
    }

    /**
     * Exact shared tactical content for ordinary ships and production small craft.
     *
     * @param engineering common physical engineering catalogue
     * @param launchers common physical launcher definitions
     * @param ammunition common physical ammunition definitions
     */
    public record RuntimeContent(
            ShipEngineeringCatalog engineering,
            WeaponLauncherCatalog launchers,
            WeaponAmmunitionCatalog ammunition) {
        /**
         * Validates one immutable runtime content bundle.
         *
         * @param engineering common physical engineering catalogue
         * @param launchers common physical launcher definitions
         * @param ammunition common physical ammunition definitions
         */
        public RuntimeContent {
            Objects.requireNonNull(engineering, "engineering");
            Objects.requireNonNull(launchers, "launchers");
            Objects.requireNonNull(ammunition, "ammunition");
        }
    }
}
