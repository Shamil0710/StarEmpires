package com.spacesim.content.weapon;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22EmpireEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22IndustrialUnionEngineeringCatalogLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    /**
     * Loads the exact two-faction weapon content used by M22.6 paired Stage-19 scenarios.
     *
     * @return one immutable engineering/launcher/ammunition universe containing both core packages
     */
    public static RuntimeContent loadCombined() {
        RuntimeContent empire = loadEmpire();
        RuntimeContent union = loadIndustrialUnion();
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        if (empire.launchers().getSchemaVersion() != union.launchers().getSchemaVersion()
                || empire.launchers().getMigrationVersion() != union.launchers().getMigrationVersion()
                || empire.ammunition().getSchemaVersion() != union.ammunition().getSchemaVersion()
                || empire.ammunition().getMigrationVersion() != union.ammunition().getMigrationVersion()) {
            throw new IllegalStateException("Core weapon packages disagree on schema/migration versions");
        }

        ArrayList<WeaponLauncherCatalog.LauncherProfile> launchers = new ArrayList<>();
        launchers.addAll(empire.launchers().getProfiles());
        launchers.addAll(union.launchers().getProfiles());
        WeaponLauncherCatalog combinedLaunchers = new WeaponLauncherCatalog(
                empire.launchers().getSchemaVersion(),
                empire.launchers().getMigrationVersion(),
                launchers);

        ArrayList<WeaponAmmunitionCatalog.KineticAmmunitionDefinition> kinetic = new ArrayList<>();
        kinetic.addAll(empire.ammunition().getKineticAmmunition());
        kinetic.addAll(union.ammunition().getKineticAmmunition());
        ArrayList<WeaponAmmunitionCatalog.GuidedAmmunitionDefinition> guided = new ArrayList<>();
        guided.addAll(empire.ammunition().getGuidedAmmunition());
        guided.addAll(union.ammunition().getGuidedAmmunition());
        WeaponAmmunitionCatalog combinedAmmunition = new WeaponAmmunitionCatalog(
                empire.ammunition().getSchemaVersion(),
                empire.ammunition().getMigrationVersion(),
                kinetic,
                guided);

        for (WeaponLauncherCatalog.LauncherProfile profile : combinedLaunchers.getProfiles()) {
            if (engineering.findModule(profile.moduleId()) == null) {
                throw new IllegalStateException("Combined launcher references absent core module: " + profile.moduleId());
            }
        }
        combinedAmmunition.getKineticAmmunition().forEach(ammo -> {
            if (engineering.findMaterial(ammo.materialId()) == null) {
                throw new IllegalStateException("Combined ammunition references absent core material: " + ammo.id());
            }
        });
        return new RuntimeContent(engineering, combinedLaunchers, combinedAmmunition);
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
