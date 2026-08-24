package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CalibrationDomainDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HeavyImpactResponseSurfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Loads the production-valid but content-provisional Stage 17.5I engineering packs.
 *
 * <p>Both representative hull vocabulary and doctrine fits deliberately use
 * {@link ShipEngineeringCatalogLoader} rather than a parallel test schema. Stage 19 promotes only
 * the provisional doctrine heavy-impact mass domain needed to contain the already-authored 2,000 kg
 * strike round; Stage 21 additionally registers explicit strategic-mobility variants of the same
 * physical A-E fits. The original Stage-19 fit definitions remain unchanged and continue to be the
 * exact acceptance baseline. Stage 22 may replace or explicitly promote the wider provisional
 * definitions.</p>
 */
public final class Stage175ICombatTestContentPack {
    /** Classpath resource for the Stage 17.5I representative hull engineering pack. */
    public static final String RESOURCE = "data/content/stage17_5i-combat-test-engineering-v1.json";
    /** Classpath resource for the Stage 17.5I five-doctrine engineering pack. */
    public static final String DOCTRINE_RESOURCE = "data/content/stage17_5i-doctrine-engineering-v1.json";
    /** Response surface promoted narrowly for the authored Stage-19 2 t strike missile envelope. */
    public static final String STAGE19_PROMOTED_RESPONSE_ID = "response.stage17_5i_doctrine_v1";
    /** Maximum projectile mass admitted by the Stage-19 provisional promotion. */
    public static final double STAGE19_MAX_PROJECTILE_MASS_KG = 2_000d;
    /** Explicit provisional FTL module used only by Stage-21 strategic-mobility variants. */
    public static final String STAGE21_STRATEGIC_FTL_MODULE_ID = "module.test_stage21_strategic_ftl_v1";

    private static final String STRATEGIC_SUFFIX = ".stage21_strategic_v1";
    private static final String DATALINK_MOUNT_ID = "utility_datalink";
    private static final String DATALINK_MODULE_ID = "module.test_datalink_v1";

    private Stage175ICombatTestContentPack() {
        throw new AssertionError("utility class");
    }

    /**
     * Loads the representative hull pack through the ordinary production engineering parser.
     *
     * @return immutable validated representative engineering catalog
     */
    public static ShipEngineeringCatalog load() {
        return loadResource(RESOURCE);
    }

    /**
     * Loads the five-doctrine combat pack, applies the explicit Stage-19 strike-mass promotion and
     * registers explicit Stage-21 strategic-mobility variants.
     *
     * <p>The original five demonstrator fits are retained byte-for-semantic-field unchanged. Each
     * Stage-21 variant differs only by replacing the provisional datalink utility mount with one
     * ordinary physical FTL module. This is an explicit fitted tradeoff: the variants do not gain a
     * hidden movement statistic, free slot, duplicate energy store or doctrine/class bonus.</p>
     *
     * @return immutable validated doctrine engineering catalog containing base and strategic variants
     */
    public static ShipEngineeringCatalog loadDoctrines() {
        return addStage21StrategicMobility(promoteStage19StrikeMass(loadResource(DOCTRINE_RESOURCE)));
    }

    /**
     * Returns the explicit Stage-21 strategic-mobility fit ID corresponding to one original doctrine
     * demonstrator fit.
     *
     * @param baseFitId original Stage-17.5I/19 doctrine demonstrator fit ID
     * @return registered Stage-21 strategic-mobility variant fit ID
     */
    public static String stage21StrategicFitId(String baseFitId) {
        String id = baseFitId == null ? "" : baseFitId.strip();
        return switch (id) {
            case "fit.test_doctrine_a_kinetic_v1",
                    "fit.test_doctrine_b_missile_v1",
                    "fit.test_doctrine_c_beam_v1",
                    "fit.test_doctrine_d_defensive_ew_v1",
                    "fit.test_doctrine_e_balanced_v1" -> id + STRATEGIC_SUFFIX;
            default -> throw new IllegalArgumentException(
                    "not a Stage-17.5I A-E base doctrine fit: " + baseFitId);
        };
    }

    /**
     * Reports whether a fitted engineering state is one of the five exact Stage-21 strategic variants.
     *
     * @param fit installed fit to inspect
     * @return true only for an exact registered Stage-21 mobility fit
     */
    public static boolean isStage21StrategicFit(ShipEngineeringCatalog.DemonstratorFitDefinition fit) {
        if (fit == null) return false;
        return isStage21StrategicFitId(fit.id());
    }

    /**
     * Reports whether a demonstrator ID belongs to the explicit Stage-21 strategic variant set.
     *
     * @param fitId demonstrator fit ID
     * @return true only for an exact Stage-21 mobility variant ID
     */
    public static boolean isStage21StrategicFitId(String fitId) {
        if (fitId == null) return false;
        String id = fitId.strip();
        return id.endsWith(STRATEGIC_SUFFIX)
                && List.of(
                        "fit.test_doctrine_a_kinetic_v1" + STRATEGIC_SUFFIX,
                        "fit.test_doctrine_b_missile_v1" + STRATEGIC_SUFFIX,
                        "fit.test_doctrine_c_beam_v1" + STRATEGIC_SUFFIX,
                        "fit.test_doctrine_d_defensive_ew_v1" + STRATEGIC_SUFFIX,
                        "fit.test_doctrine_e_balanced_v1" + STRATEGIC_SUFFIX)
                .contains(id);
    }

    private static ShipEngineeringCatalog promoteStage19StrikeMass(ShipEngineeringCatalog catalog) {
        boolean found = false;
        List<HeavyImpactResponseSurfaceDefinition> promoted = catalog.getResponseSurfaces().stream()
                .map(surface -> {
                    if (!surface.id().equals(STAGE19_PROMOTED_RESPONSE_ID)) {
                        return surface;
                    }
                    CalibrationDomainDefinition domain = surface.calibrationDomain();
                    if (domain.maxProjectileMassKg() > STAGE19_MAX_PROJECTILE_MASS_KG) {
                        throw new IllegalStateException("Stage-19 promotion would narrow an already wider domain");
                    }
                    return new HeavyImpactResponseSurfaceDefinition(
                            surface.id(),
                            new CalibrationDomainDefinition(
                                    domain.minImpactVelocityMps(),
                                    domain.maxImpactVelocityMps(),
                                    domain.minProjectileMassKg(),
                                    STAGE19_MAX_PROJECTILE_MASS_KG,
                                    "stage19_strike_2t_provisional_test_only"));
                })
                .toList();
        for (HeavyImpactResponseSurfaceDefinition surface : promoted) {
            if (surface.id().equals(STAGE19_PROMOTED_RESPONSE_ID)) {
                found = true;
                break;
            }
        }
        if (!found) {
            throw new IllegalStateException("Missing Stage-19 promoted response surface");
        }
        return new ShipEngineeringCatalog(
                catalog.getSchemaVersion(),
                catalog.getMigrationVersion(),
                catalog.getMaterials(),
                promoted,
                catalog.getProtectionStacks(),
                catalog.getHulls(),
                catalog.getModules(),
                catalog.getDemonstratorFits());
    }

    private static ShipEngineeringCatalog addStage21StrategicMobility(ShipEngineeringCatalog catalog) {
        if (catalog.findModule(STAGE21_STRATEGIC_FTL_MODULE_ID) != null) {
            throw new IllegalStateException("Stage-21 strategic FTL module already exists in base content");
        }
        ArrayList<ModuleDefinition> modules = new ArrayList<>(catalog.getModules());
        modules.add(stage21StrategicFtlModule());
        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(catalog.getDemonstratorFits());
        List<DemonstratorFitDefinition> baseFits = List.copyOf(catalog.getDemonstratorFits());
        for (DemonstratorFitDefinition base : baseFits) {
            String strategicId;
            try {
                strategicId = stage21StrategicFitId(base.id());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ArrayList<InstalledModuleDefinition> assignments = new ArrayList<>(base.installedModules());
            int replaced = 0;
            for (int index = 0; index < assignments.size(); index++) {
                InstalledModuleDefinition assignment = assignments.get(index);
                if (assignment.mountId().equals(DATALINK_MOUNT_ID)
                        && assignment.moduleId().equals(DATALINK_MODULE_ID)) {
                    assignments.set(index, new InstalledModuleDefinition(
                            DATALINK_MOUNT_ID, STAGE21_STRATEGIC_FTL_MODULE_ID));
                    replaced++;
                }
            }
            if (replaced != 1) {
                throw new IllegalStateException(
                        "Stage-21 strategic variant requires exactly one provisional datalink mount: "
                                + base.id());
            }
            fits.add(new DemonstratorFitDefinition(strategicId, base.hullId(), List.copyOf(assignments)));
        }
        if (fits.size() != baseFits.size() + 5) {
            throw new IllegalStateException("Stage-21 strategic mobility must register exactly five variants");
        }
        return new ShipEngineeringCatalog(
                catalog.getSchemaVersion(),
                catalog.getMigrationVersion(),
                catalog.getMaterials(),
                catalog.getResponseSurfaces(),
                catalog.getProtectionStacks(),
                catalog.getHulls(),
                modules,
                fits);
    }

    private static ModuleDefinition stage21StrategicFtlModule() {
        return new ModuleDefinition(
                STAGE21_STRATEGIC_FTL_MODULE_ID,
                "Stage 21 Provisional Strategic FTL",
                ModuleFamily.FTL_JUMP,
                List.of(IntegrationCategory.UTILITY),
                List.of(),
                new Dimensions3d(12d, 8d, 6d),
                450_000d,
                450d,
                3_000_000d,
                0d,
                50_000_000d,
                5_000_000_000d,
                0d,
                25_000_000d,
                100_000_000_000d,
                25_000_000d,
                0d,
                4,
                4,
                List.of(),
                Map.of("thermal_w", 25_000_000d),
                List.of(),
                new MaintenanceDefinition(345_600d, 18_000d, 0.75d),
                Map.of(
                        "translated_mass_max_kg", 32_000_000d,
                        "jump_energy_j", 150_000_000_000d,
                        "charge_power_w", 5_000_000_000d,
                        "spool_time_s", 30d,
                        "edge_transit_time_s", 30d,
                        "cooldown_s", 60d,
                        "jump_heat_j", 20_000_000_000d));
    }

    private static ShipEngineeringCatalog loadResource(String resource) {
        ClassLoader classLoader = Stage175ICombatTestContentPack.class.getClassLoader();
        try (InputStream stream = classLoader.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing Stage 17.5I combat-test content: " + resource);
            }
            return ShipEngineeringCatalogLoader.parse(
                    new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot read Stage 17.5I combat-test content: " + resource, exception);
        }
    }
}
