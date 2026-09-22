package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ConstructionInputDefinition;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.CompartmentRepairProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * M22.8J Stage-17.5G manufacturing requirements for production small craft.
 *
 * <p>The physical craft modules are common, but each core faction pays through its already-authored
 * fabrication/tooling vocabulary. This projection adds no inventory, build completion or economic
 * discount and remains entirely inside the ordinary shipyard planner authority.</p>
 */
public final class Stage228SmallCraftIndustrialProjection {
    /** Semantic version of the J industrial projection. */
    public static final String VERSION = "m22.8j.small_craft_industrial_projection.v1";

    private Stage228SmallCraftIndustrialProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds Empire small-craft hull/module industrial requirements.
     *
     * @param source accepted Empire industrial catalog after common M22.6 projections
     * @param engineering M22.8J-completed common engineering catalog
     * @return immutable Empire catalog with small-craft production requirements
     */
    public static ShipyardIndustrialCatalog applyEmpire(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(
                source,
                engineering,
                empireHull(),
                empireModules());
    }

    /**
     * Adds Industrial Union small-craft hull/module industrial requirements.
     *
     * @param source accepted Union industrial catalog after common M22.6 projections
     * @param engineering M22.8J-completed common engineering catalog
     * @return immutable Union catalog with small-craft production requirements
     */
    public static ShipyardIndustrialCatalog applyIndustrialUnion(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(
                source,
                engineering,
                unionHull(),
                unionModules());
    }

    private static ShipyardIndustrialCatalog apply(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering,
            HullIndustrialProfile hull,
            List<ModuleIndustrialProfile> modules) {
        ShipyardIndustrialCatalog checked = Objects.requireNonNull(source, "source");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (checkedEngineering.findHull(hull.hullId()) == null) {
            throw new IllegalArgumentException("small-craft industrial hull absent from engineering: " + hull.hullId());
        }
        if (checked.findHullProfile(hull.hullId()) != null) {
            throw new IllegalArgumentException("small-craft industrial hull already exists: " + hull.hullId());
        }

        ArrayList<ModuleIndustrialProfile> resultModules =
                new ArrayList<>(checked.getModuleProfiles());
        for (ModuleIndustrialProfile profile : modules) {
            if (checkedEngineering.findModule(profile.moduleId()) == null) {
                throw new IllegalArgumentException(
                        "small-craft industrial module absent from engineering: " + profile.moduleId());
            }
            if (checked.findModuleProfile(profile.moduleId()) != null) {
                throw new IllegalArgumentException(
                        "small-craft industrial module already exists: " + profile.moduleId());
            }
            resultModules.add(profile);
        }
        ArrayList<HullIndustrialProfile> hulls = new ArrayList<>(checked.getHullProfiles());
        hulls.add(hull);
        return new ShipyardIndustrialCatalog(
                checked.getSchemaVersion(),
                hulls,
                resultModules);
    }

    private static HullIndustrialProfile empireHull() {
        return new HullIndustrialProfile(
                Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID,
                inputs(320d, 90d, 50d),
                Set.of("heavy_structure", "pressure_hull", "armor_integration"),
                Set.of("empire_axial_frame_fixture", "heavy_lift"),
                0.50d,
                180_000_000d,
                26,
                22,
                259_200d,
                List.of(
                        repair("engineering", 42d, 12d, 4d, 28_800d),
                        repair("crew", 36d, 10d, 8d, 25_200d),
                        repair("mission", 38d, 12d, 6d, 27_000d)));
    }

    private static HullIndustrialProfile unionHull() {
        return new HullIndustrialProfile(
                Stage228SmallCraftProductionProjection.UNION_HULL_ID,
                inputs(300d, 105d, 60d),
                Set.of("heavy_structure", "pressure_hull", "sectional_assembly"),
                Set.of("union_section_fixture", "common_bank_fixture", "heavy_lift"),
                0.50d,
                190_000_000d,
                22,
                34,
                230_400d,
                List.of(
                        repair("engineering", 38d, 15d, 5d, 25_200d),
                        repair("crew", 34d, 14d, 7d, 23_400d),
                        repair("mission", 36d, 15d, 6d, 24_300d)));
    }

    private static List<ModuleIndustrialProfile> empireModules() {
        return List.of(
                empireModule(Stage228SmallCraftProductionProjection.REACTOR_ID,
                        Set.of("heavy_machinery", "power_system_integration"),
                        Set.of("reactor_service_fixture"), 0.70d, 110_000_000d, 18, 18),
                empireModule(Stage228SmallCraftProductionProjection.DRIVE_ID,
                        Set.of("heavy_machinery", "propulsion_integration"),
                        Set.of("drive_alignment_fixture"), 0.74d, 120_000_000d, 20, 18),
                empireModule(Stage228SmallCraftProductionProjection.SENSOR_ID,
                        Set.of("electronics_integration", "precision_alignment"),
                        Set.of("sensor_calibration_rig"), 0.88d, 70_000_000d, 14, 22),
                empireModule(Stage228SmallCraftProductionProjection.RADIATOR_ID,
                        Set.of("thermal_system_integration", "light_structure"),
                        Set.of("coolant_pressure_rig"), 0.58d, 60_000_000d, 12, 14),
                empireModule(Stage228SmallCraftProductionProjection.SHIELD_ID,
                        Set.of("electronics_integration", "precision_alignment", "power_system_integration"),
                        Set.of("field_emitter_alignment_rig"), 0.82d, 100_000_000d, 16, 20),
                empireModule(Stage228SmallCraftProductionProjection.BEAM_ID,
                        Set.of("weapon_integration", "precision_alignment", "electronics_integration"),
                        Set.of("weapon_bore_alignment_rig"), 0.84d, 110_000_000d, 16, 20),
                empireModule(Stage228SmallCraftProductionProjection.KINETIC_ID,
                        Set.of("weapon_integration", "precision_alignment", "heavy_machinery"),
                        Set.of("weapon_bore_alignment_rig"), 0.78d, 100_000_000d, 16, 18));
    }

    private static List<ModuleIndustrialProfile> unionModules() {
        ArrayList<ModuleIndustrialProfile> result = new ArrayList<>();
        for (String id : List.of(
                Stage228SmallCraftProductionProjection.REACTOR_ID,
                Stage228SmallCraftProductionProjection.DRIVE_ID,
                Stage228SmallCraftProductionProjection.SENSOR_ID,
                Stage228SmallCraftProductionProjection.RADIATOR_ID,
                Stage228SmallCraftProductionProjection.SHIELD_ID,
                Stage228SmallCraftProductionProjection.BEAM_ID,
                Stage228SmallCraftProductionProjection.KINETIC_ID)) {
            result.add(new ModuleIndustrialProfile(
                    id,
                    Set.of("common_module_assembly", "electrical_integration"),
                    Set.of("common_bank_fixture", "union_module_jig"),
                    0.62d,
                    90_000_000d,
                    12,
                    26,
                    86_400d,
                    10_800d,
                    7_200d));
        }
        return List.copyOf(result);
    }

    private static ModuleIndustrialProfile empireModule(
            String id,
            Set<String> capabilities,
            Set<String> tooling,
            double precision,
            double powerW,
            int labor,
            int automation) {
        return new ModuleIndustrialProfile(
                id,
                capabilities,
                tooling,
                precision,
                powerW,
                labor,
                automation,
                103_680d,
                12_960d,
                8_640d);
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

    private static CompartmentRepairProfile repair(
            String compartment,
            double heavy,
            double electrical,
            double precision,
            double workSeconds) {
        return new CompartmentRepairProfile(
                compartment,
                inputs(heavy, electrical, precision),
                workSeconds);
    }
}
