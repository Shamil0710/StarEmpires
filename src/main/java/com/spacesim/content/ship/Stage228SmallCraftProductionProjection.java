package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.CompartmentDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.DemonstratorFitDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Dimensions3d;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.HardpointSize;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullArchitecture;
import com.spacesim.content.ship.ShipEngineeringCatalog.HullDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InstalledModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.IntegrationCategory;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalog.MaintenanceDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.content.ship.ShipEngineeringCatalog.SlotDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.Vector3d;
import com.spacesim.ship.ShipEngineeringRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * M22.8J production-content projection for persistent carrier small craft.
 *
 * <p>The projection adds only ordinary Stage-17.5 hull/module/fit definitions. Role labels do not
 * grant combat modifiers: interception, defence and strike differ only because their exact fitted
 * physical systems differ. The two core factions receive distinct hull identities and structural
 * protection ancestry while sharing a compact production module family that remains subject to the
 * same power, thermal, sensor, weapon, damage and fitting authorities as capital ships.</p>
 */
public final class Stage228SmallCraftProductionProjection {
    /** Semantic version of the production small-craft engineering projection. */
    public static final String VERSION = "m22.8j.small_craft_production_projection.v1";

    /** Empire production small-craft hull. */
    public static final String EMPIRE_HULL_ID = "hull.empire_small_craft_v1";
    /** Industrial Union production small-craft hull. */
    public static final String UNION_HULL_ID = "hull.industrial_union_small_craft_v1";

    /** Empire interception fit. */
    public static final String EMPIRE_INTERCEPTOR_FIT_ID = "fit.empire.small_craft.interceptor_v1";
    /** Empire defensive-screen fit. */
    public static final String EMPIRE_DEFENCE_FIT_ID = "fit.empire.small_craft.defence_v1";
    /** Empire strike fit. */
    public static final String EMPIRE_STRIKE_FIT_ID = "fit.empire.small_craft.strike_v1";
    /** Union interception fit. */
    public static final String UNION_INTERCEPTOR_FIT_ID = "fit.industrial_union.small_craft.interceptor_v1";
    /** Union defensive-screen fit. */
    public static final String UNION_DEFENCE_FIT_ID = "fit.industrial_union.small_craft.defence_v1";
    /** Union strike fit. */
    public static final String UNION_STRIKE_FIT_ID = "fit.industrial_union.small_craft.strike_v1";

    /** Compact reactor module used by all production small-craft fits. */
    public static final String REACTOR_ID = "module.small_craft_reactor_v1";
    /** Compact reaction-mass drive module used by all production small-craft fits. */
    public static final String DRIVE_ID = "module.small_craft_drive_v1";
    /** Compact multi-mode sensor/fire-control module. */
    public static final String SENSOR_ID = "module.small_craft_sensor_v1";
    /** Compact radiator/coolant module. */
    public static final String RADIATOR_ID = "module.small_craft_radiator_v1";
    /** Compact defensive field emitter. */
    public static final String SHIELD_ID = "module.small_craft_shield_v1";
    /** Compact beam mount used by interception/defence fits. */
    public static final String BEAM_ID = "module.small_craft_beam_v1";
    /** Compact kinetic mount used by strike fits. */
    public static final String KINETIC_ID = "module.small_craft_kinetic_v1";

    /** Stable production role label for interception fits. */
    public static final String ROLE_INTERCEPTION = "role.small_craft.interception";
    /** Stable production role label for defensive-screen fits. */
    public static final String ROLE_DEFENCE = "role.small_craft.defence";
    /** Stable production role label for strike fits. */
    public static final String ROLE_STRIKE = "role.small_craft.strike";

    private Stage228SmallCraftProductionProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds production small-craft hulls, modules and six core-faction fits.
     *
     * @param source accepted combined core engineering catalog
     * @return immutable catalog containing ordinary production small-craft definitions
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        requireAbsent(checked);

        ArrayList<HullDefinition> hulls = new ArrayList<>(checked.getHulls());
        hulls.add(empireHull());
        hulls.add(unionHull());

        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules());
        modules.add(reactor());
        modules.add(drive());
        modules.add(sensor());
        modules.add(radiator());
        modules.add(shield());
        modules.add(beam());
        modules.add(kinetic());

        ArrayList<DemonstratorFitDefinition> fits =
                new ArrayList<>(checked.getDemonstratorFits());
        fits.add(fit(EMPIRE_INTERCEPTOR_FIT_ID, EMPIRE_HULL_ID, false, true));
        fits.add(fit(EMPIRE_DEFENCE_FIT_ID, EMPIRE_HULL_ID, true, true));
        fits.add(fit(EMPIRE_STRIKE_FIT_ID, EMPIRE_HULL_ID, false, false));
        fits.add(fit(UNION_INTERCEPTOR_FIT_ID, UNION_HULL_ID, false, true));
        fits.add(fit(UNION_DEFENCE_FIT_ID, UNION_HULL_ID, true, true));
        fits.add(fit(UNION_STRIKE_FIT_ID, UNION_HULL_ID, false, false));

        return new ShipEngineeringCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                checked.getMaterials(),
                checked.getResponseSurfaces(),
                checked.getProtectionStacks(),
                hulls,
                modules,
                fits);
    }

    /**
     * Returns the six production role bindings without introducing simulation behaviour.
     *
     * @return immutable design-to-role/faction bindings
     */
    public static List<DesignBinding> designBindings() {
        return List.of(
                new DesignBinding(EMPIRE_INTERCEPTOR_FIT_ID, "faction.imperial_directorate", ROLE_INTERCEPTION),
                new DesignBinding(EMPIRE_DEFENCE_FIT_ID, "faction.imperial_directorate", ROLE_DEFENCE),
                new DesignBinding(EMPIRE_STRIKE_FIT_ID, "faction.imperial_directorate", ROLE_STRIKE),
                new DesignBinding(UNION_INTERCEPTOR_FIT_ID, "faction.industrial_union", ROLE_INTERCEPTION),
                new DesignBinding(UNION_DEFENCE_FIT_ID, "faction.industrial_union", ROLE_DEFENCE),
                new DesignBinding(UNION_STRIKE_FIT_ID, "faction.industrial_union", ROLE_STRIKE));
    }

    private static HullDefinition empireHull() {
        return hull(
                EMPIRE_HULL_ID,
                "Imperial Embarked Combat Craft",
                32d, 12d, 6d,
                180_000d, 1_100d, 520_000d,
                2, 4, 150d,
                "protection.empire_citadel_v1");
    }

    private static HullDefinition unionHull() {
        return hull(
                UNION_HULL_ID,
                "Industrial Union Embarked Combat Craft",
                35d, 14d, 6.5d,
                200_000d, 1_300d, 560_000d,
                2, 4, 175d,
                "protection.industrial_union_sectional_v1");
    }

    private static HullDefinition hull(
            String id,
            String name,
            double lengthM,
            double widthM,
            double heightM,
            double bareMassKg,
            double volumeM3,
            double maxMassKg,
            int crew,
            int lifeSupport,
            double signatureAreaM2,
            String protectionStackId) {
        List<SlotDefinition> slots = List.of(
                new SlotDefinition("core_reactor", IntegrationCategory.CORE,
                        new Dimensions3d(5d, 4d, 3d), 70_000d),
                new SlotDefinition("core_drive", IntegrationCategory.CORE,
                        new Dimensions3d(9d, 5d, 4d), 115_000d),
                new SlotDefinition("utility_sensor", IntegrationCategory.UTILITY,
                        new Dimensions3d(3.5d, 3d, 2.5d), 30_000d),
                new SlotDefinition("utility_thermal", IntegrationCategory.UTILITY,
                        new Dimensions3d(6d, 3d, 1.8d), 35_000d),
                new SlotDefinition("utility_defense", IntegrationCategory.UTILITY,
                        new Dimensions3d(3.5d, 3d, 2.5d), 45_000d));
        List<HardpointDefinition> hardpoints = List.of(new HardpointDefinition(
                "weapon_primary",
                HardpointSize.SMALL,
                new Vector3d(0d, lengthM * 0.30d, 0d),
                new ShipEngineeringCatalog.ArcDefinition(Math.PI / 2d, 0.55d),
                new Dimensions3d(7d, 2.5d, 2.5d),
                60_000d,
                300_000d,
                List.of(ModuleFamily.WEAPON_AMMUNITION)));
        List<CompartmentDefinition> compartments = List.of(
                new CompartmentDefinition(
                        "engineering", volumeM3 * 0.38d,
                        new Vector3d(0d, -lengthM * 0.24d, 0d),
                        protectionStackId,
                        List.of("reactor", "drive")),
                new CompartmentDefinition(
                        "crew", volumeM3 * 0.24d,
                        new Vector3d(0d, 0d, 0d),
                        protectionStackId,
                        List.of("crew", "sensor")),
                new CompartmentDefinition(
                        "mission", volumeM3 * 0.38d,
                        new Vector3d(0d, lengthM * 0.24d, 0d),
                        protectionStackId,
                        List.of("weapon", "stores")));
        return new HullDefinition(
                id,
                name,
                HullArchitecture.FRAME,
                new Dimensions3d(lengthM, widthM, heightM),
                bareMassKg,
                volumeM3,
                slots,
                hardpoints,
                compartments,
                crew,
                lifeSupport,
                signatureAreaM2,
                protectionStackId,
                maxMassKg,
                List.of(ModuleFamily.MAIN_DRIVE, ModuleFamily.MANEUVER_THRUSTERS));
    }

    private static ModuleDefinition reactor() {
        return module(
                REACTOR_ID,
                "Compact Small-Craft Reactor",
                ModuleFamily.REACTOR_POWER,
                List.of(IntegrationCategory.CORE),
                List.of(),
                new Dimensions3d(4d, 3d, 2.5d),
                42_000d,
                30d,
                900_000d,
                90_000_000d,
                0d,
                0d,
                0d,
                7_000_000d,
                300_000_000d,
                7_000_000d,
                0d,
                0,
                8,
                List.of(),
                Map.of("thermal_w", 7_000_000d),
                inputs(24d, 18d, 8d),
                maintenance(86_400d, 3_600d, 0.45d),
                Map.of());
    }

    private static ModuleDefinition drive() {
        double thrust = 900_000d;
        double exhaust = 28_000d;
        return module(
                DRIVE_ID,
                "Compact High-Acceleration Drive",
                ModuleFamily.MAIN_DRIVE,
                List.of(IntegrationCategory.CORE),
                List.of(),
                new Dimensions3d(8d, 4d, 3.5d),
                82_000d,
                70d,
                1_600_000d,
                0d,
                12_000_000d,
                18_000_000d,
                0d,
                9_000_000d,
                420_000_000d,
                9_000_000d,
                0d,
                0,
                10,
                List.of(new InterfaceDefinition(
                        InterfaceKind.REACTION_MASS, "propellant_feed", 120_000d)),
                Map.of("thermal_w", 9_000_000d, "plume_w", 150_000_000d),
                inputs(42d, 18d, 10d),
                maintenance(43_200d, 4_800d, 0.58d),
                Map.of(
                        ShipEngineeringRuntime.THRUST_N, thrust,
                        ShipEngineeringRuntime.EXHAUST_VELOCITY_MPS, exhaust,
                        ShipEngineeringRuntime.JET_POWER_W, 0.5d * thrust * exhaust));
    }

    private static ModuleDefinition sensor() {
        return module(
                SENSOR_ID,
                "Compact Intercept Sensor/Fire-Control Array",
                ModuleFamily.SENSOR_EW_FIRE_CONTROL,
                List.of(IntegrationCategory.UTILITY),
                List.of(),
                new Dimensions3d(3d, 2.5d, 2d),
                22_000d,
                14d,
                500_000d,
                0d,
                8_000_000d,
                20_000_000d,
                0d,
                5_000_000d,
                180_000_000d,
                5_000_000d,
                0d,
                1,
                12,
                List.of(),
                Map.of("thermal_w", 5_000_000d, "active_radio_w", 2_000_000d),
                inputs(4d, 18d, 24d),
                maintenance(64_800d, 2_400d, 0.52d),
                Map.of("aperture_area_m2", 12d, "processing_channels", 18d));
    }

    private static ModuleDefinition radiator() {
        return module(
                RADIATOR_ID,
                "Compact Retractable Radiator",
                ModuleFamily.THERMAL_CONTROL,
                List.of(IntegrationCategory.UTILITY),
                List.of(),
                new Dimensions3d(5.5d, 2.5d, 1.5d),
                28_000d,
                26d,
                450_000d,
                0d,
                2_000_000d,
                4_000_000d,
                0d,
                1_000_000d,
                240_000_000d,
                0d,
                120_000_000d,
                0,
                6,
                List.of(),
                Map.of("thermal_w", 120_000_000d),
                inputs(8d, 14d, 6d),
                maintenance(86_400d, 2_800d, 0.40d),
                Map.of("radiator_area_m2", 180d));
    }

    private static ModuleDefinition shield() {
        return module(
                SHIELD_ID,
                "Compact Defensive Field Emitter",
                ModuleFamily.SHIELD_FIELD,
                List.of(IntegrationCategory.UTILITY),
                List.of(),
                new Dimensions3d(3d, 2.5d, 2d),
                34_000d,
                20d,
                600_000d,
                0d,
                18_000_000d,
                30_000_000d,
                500_000_000d,
                8_000_000d,
                220_000_000d,
                8_000_000d,
                0d,
                0,
                8,
                List.of(),
                Map.of("thermal_w", 8_000_000d),
                inputs(6d, 22d, 18d),
                maintenance(64_800d, 3_000d, 0.62d),
                Map.of("field_capacity_j", 2_000_000_000d, "recharge_power_w", 8_000_000d));
    }

    private static ModuleDefinition beam() {
        return module(
                BEAM_ID,
                "Compact Tracking Beam",
                ModuleFamily.WEAPON_AMMUNITION,
                List.of(IntegrationCategory.WEAPON),
                List.of(HardpointSize.SMALL),
                new Dimensions3d(6d, 2d, 2d),
                46_000d,
                34d,
                1_200_000d,
                0d,
                10_000_000d,
                32_000_000d,
                0d,
                14_000_000d,
                220_000_000d,
                14_000_000d,
                0d,
                1,
                8,
                List.of(),
                Map.of("thermal_w", 14_000_000d),
                inputs(12d, 22d, 18d),
                maintenance(43_200d, 3_600d, 0.68d),
                Map.of(
                        "beam_power_w", 18_000_000d,
                        "wavelength_m", 0.000001064d,
                        "aperture_diameter_m", 0.32d,
                        "pointing_jitter_rad", 0.00008d,
                        "max_continuous_dwell_s", 2.5d));
    }

    private static ModuleDefinition kinetic() {
        return module(
                KINETIC_ID,
                "Compact Kinetic Strike Mount",
                ModuleFamily.WEAPON_AMMUNITION,
                List.of(IntegrationCategory.WEAPON),
                List.of(HardpointSize.SMALL),
                new Dimensions3d(6.5d, 2.2d, 2.2d),
                52_000d,
                38d,
                1_400_000d,
                0d,
                3_000_000d,
                18_000_000d,
                120_000_000d,
                6_000_000d,
                180_000_000d,
                6_000_000d,
                0d,
                1,
                8,
                List.of(new InterfaceDefinition(
                        InterfaceKind.AMMUNITION, "kinetic_feed", 40d)),
                Map.of("thermal_w", 6_000_000d),
                inputs(18d, 14d, 12d),
                maintenance(43_200d, 3_800d, 0.70d),
                Map.of(
                        "projectile_mass_kg", 12d,
                        "muzzle_velocity_mps", 5_000d,
                        "recoil_impulse_ns", 60_000d));
    }

    private static DemonstratorFitDefinition fit(
            String id,
            String hullId,
            boolean shield,
            boolean beam) {
        ArrayList<InstalledModuleDefinition> modules = new ArrayList<>(List.of(
                new InstalledModuleDefinition("core_reactor", REACTOR_ID),
                new InstalledModuleDefinition("core_drive", DRIVE_ID),
                new InstalledModuleDefinition("utility_sensor", SENSOR_ID),
                new InstalledModuleDefinition("utility_thermal", RADIATOR_ID),
                new InstalledModuleDefinition("weapon_primary", beam ? BEAM_ID : KINETIC_ID)));
        if (shield) {
            modules.add(new InstalledModuleDefinition("utility_defense", SHIELD_ID));
        }
        return new DemonstratorFitDefinition(id, hullId, List.copyOf(modules));
    }

    private static ModuleDefinition module(
            String id,
            String name,
            ModuleFamily family,
            List<IntegrationCategory> categories,
            List<HardpointSize> hardpoints,
            Dimensions3d dimensions,
            double massKg,
            double volumeM3,
            double mountStrengthN,
            double supplyW,
            double demandW,
            double peakW,
            double storedEnergyJ,
            double wasteHeatW,
            double thermalCapacityJ,
            double coolantDemandW,
            double heatRejectionW,
            int crew,
            int automation,
            List<InterfaceDefinition> interfaces,
            Map<String, Double> signatures,
            List<ConstructionInputDefinition> construction,
            MaintenanceDefinition maintenance,
            Map<String, Double> capabilities) {
        return new ModuleDefinition(
                id, name, family, categories, hardpoints, dimensions, massKg, volumeM3,
                mountStrengthN, supplyW, demandW, peakW, storedEnergyJ, wasteHeatW,
                thermalCapacityJ, coolantDemandW, heatRejectionW, crew, automation,
                interfaces, signatures, construction, maintenance, capabilities);
    }

    private static List<ConstructionInputDefinition> inputs(
            double heavy,
            double electrical,
            double precision) {
        return List.of(
                new ConstructionInputDefinition("component.heavy", heavy),
                new ConstructionInputDefinition("component.electrical", electrical),
                new ConstructionInputDefinition("component.precision", precision));
    }

    private static MaintenanceDefinition maintenance(
            double serviceSeconds,
            double workSeconds,
            double complexity) {
        return new MaintenanceDefinition(serviceSeconds, workSeconds, complexity);
    }

    private static void requireAbsent(ShipEngineeringCatalog catalog) {
        for (String id : List.of(
                EMPIRE_HULL_ID,
                UNION_HULL_ID,
                REACTOR_ID,
                DRIVE_ID,
                SENSOR_ID,
                RADIATOR_ID,
                SHIELD_ID,
                BEAM_ID,
                KINETIC_ID,
                EMPIRE_INTERCEPTOR_FIT_ID,
                EMPIRE_DEFENCE_FIT_ID,
                EMPIRE_STRIKE_FIT_ID,
                UNION_INTERCEPTOR_FIT_ID,
                UNION_DEFENCE_FIT_ID,
                UNION_STRIKE_FIT_ID)) {
            if (catalog.findHull(id) != null
                    || catalog.findModule(id) != null
                    || catalog.findDemonstratorFit(id) != null) {
                throw new IllegalArgumentException(
                        "small-craft production content already exists: " + id);
            }
        }
    }

    /**
     * Presentation/content binding only; role never changes combat equations.
     *
     * @param fitId stable production fit identity
     * @param stableFactionId stable owning core faction
     * @param roleId authored production role label
     */
    public record DesignBinding(String fitId, String stableFactionId, String roleId) {
        /**
         * Validates one immutable design binding.
         *
         * @param fitId stable production fit identity
         * @param stableFactionId stable owning core faction
         * @param roleId authored production role label
         */
        public DesignBinding {
            fitId = requireText(fitId, "fitId");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            roleId = requireText(roleId, "roleId");
        }
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
