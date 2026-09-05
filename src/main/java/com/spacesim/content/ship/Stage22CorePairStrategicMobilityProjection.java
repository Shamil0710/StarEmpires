package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.6 common strategic-mobility completion for the two Stage-22 core engineering packages.
 *
 * <p>The raw M22.3/M22.4 combat/support catalogs deliberately remain stable. This versioned common
 * projection closes the cross-package B10 mobility seam by adding one ordinary Stage-17.5 FTL module
 * and explicit strategic variants. Every variant replaces the already-authored {@code utility_defense}
 * shield cassette/citadel with FTL; no free mount, translated-mass multiplier or faction-name movement
 * bonus is introduced. Combat-only base fits therefore remain correctly non-FTL.</p>
 *
 * <p>The same FTL physics is used by both factions. Their economic and fleet-level asymmetry comes
 * from the existing package hull/support burdens and the faction-specific Stage-22 industrial profile
 * that manufactures/integrates this common module.</p>
 */
public final class Stage22CorePairStrategicMobilityProjection {
    /** Semantic version pinned by the M22.6 freeze surface. */
    public static final String VERSION = "stage22.core_pair_strategic_mobility_projection.v1";
    /** Shared physical FTL module installed by every core strategic variant. */
    public static final String FTL_MODULE_ID = "module.stage22_core_strategic_ftl_v1";

    /** Empire remote-projection destroyer. */
    public static final String EMPIRE_DESTROYER_STRATEGIC_FIT = "fit.empire.destroyer.strategic_v1";
    /** Empire remote replenishment tanker. */
    public static final String EMPIRE_TANKER_STRATEGIC_FIT = "fit.empire.tanker.strategic_v1";
    /** Empire remote repair/support hull. */
    public static final String EMPIRE_SUPPORT_STRATEGIC_FIT = "fit.empire.fleet_support.strategic_v1";
    /** Industrial Union remote-projection destroyer. */
    public static final String UNION_DESTROYER_STRATEGIC_FIT = "fit.industrial_union.destroyer.strategic_v1";
    /** Industrial Union remote replenishment tanker. */
    public static final String UNION_TANKER_STRATEGIC_FIT = "fit.industrial_union.tanker.strategic_v1";
    /** Industrial Union remote repair/support hull. */
    public static final String UNION_SUPPORT_STRATEGIC_FIT = "fit.industrial_union.fleet_support.strategic_v1";

    private static final String MOBILITY_MOUNT = "utility_defense";
    private static final List<VariantSpec> VARIANTS = List.of(
            new VariantSpec("fit.empire.destroyer.screen_v1", EMPIRE_DESTROYER_STRATEGIC_FIT),
            new VariantSpec("fit.empire.tanker.armored_refit_v1", EMPIRE_TANKER_STRATEGIC_FIT),
            new VariantSpec("fit.empire.fleet_support.salvage_refit_v1", EMPIRE_SUPPORT_STRATEGIC_FIT),
            new VariantSpec("fit.industrial_union.destroyer.line_v1", UNION_DESTROYER_STRATEGIC_FIT),
            new VariantSpec("fit.industrial_union.tanker.escort_refit_v1", UNION_TANKER_STRATEGIC_FIT),
            new VariantSpec("fit.industrial_union.fleet_support.salvage_refit_v1", UNION_SUPPORT_STRATEGIC_FIT));

    private Stage22CorePairStrategicMobilityProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds the shared FTL hardware and exactly six paid-slot strategic variants.
     *
     * @param source combined runtime-completed core engineering catalog
     * @return immutable catalog with explicit strategic variants
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        if (checked.findModule(FTL_MODULE_ID) != null) {
            throw new IllegalArgumentException("Stage-22 core strategic FTL module already exists");
        }

        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules());
        modules.add(ftlModule());
        ArrayList<DemonstratorFitDefinition> fits = new ArrayList<>(checked.getDemonstratorFits());
        for (VariantSpec spec : VARIANTS) {
            if (checked.findDemonstratorFit(spec.strategicFitId()) != null) {
                throw new IllegalArgumentException("Stage-22 strategic fit already exists: " + spec.strategicFitId());
            }
            DemonstratorFitDefinition base = checked.findDemonstratorFit(spec.baseFitId());
            if (base == null) {
                throw new IllegalStateException("Missing Stage-22 strategic base fit: " + spec.baseFitId());
            }
            ArrayList<InstalledModuleDefinition> assignments = new ArrayList<>(base.installedModules());
            int replaced = 0;
            for (int index = 0; index < assignments.size(); index++) {
                InstalledModuleDefinition assignment = assignments.get(index);
                if (!MOBILITY_MOUNT.equals(assignment.mountId())) continue;
                ModuleDefinition displaced = checked.findModule(assignment.moduleId());
                if (displaced == null || displaced.family() != ModuleFamily.SHIELD_FIELD) {
                    throw new IllegalStateException(
                            "Strategic mobility must displace the authored defensive module: " + spec.baseFitId());
                }
                assignments.set(index, new InstalledModuleDefinition(MOBILITY_MOUNT, FTL_MODULE_ID));
                replaced++;
            }
            if (replaced != 1) {
                throw new IllegalStateException(
                        "Strategic mobility requires exactly one paid utility_defense tradeoff: " + spec.baseFitId());
            }
            fits.add(new DemonstratorFitDefinition(spec.strategicFitId(), base.hullId(), List.copyOf(assignments)));
        }

        return new ShipEngineeringCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                checked.getMaterials(),
                checked.getResponseSurfaces(),
                checked.getProtectionStacks(),
                checked.getHulls(),
                modules,
                fits);
    }

    private static ModuleDefinition ftlModule() {
        return new ModuleDefinition(
                FTL_MODULE_ID,
                "Core Strategic Translation Drive",
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
                500_000_000d,
                0d,
                4,
                4,
                List.of(),
                Map.of("thermal_w", 25_000_000d),
                List.of(
                        new ConstructionInputDefinition("component.heavy", 500d),
                        new ConstructionInputDefinition("component.electrical", 800d),
                        new ConstructionInputDefinition("component.precision", 1_200d)),
                new MaintenanceDefinition(345_600d, 18_000d, 0.75d),
                Map.of(
                        "translated_mass_max_kg", 130_000_000d,
                        "jump_energy_j", 150_000_000_000d,
                        "charge_power_w", 5_000_000_000d,
                        "spool_time_s", 30d,
                        "edge_transit_time_s", 30d,
                        "cooldown_s", 60d,
                        "jump_heat_j", 20_000_000_000d));
    }

    private record VariantSpec(String baseFitId, String strategicFitId) { }
}
