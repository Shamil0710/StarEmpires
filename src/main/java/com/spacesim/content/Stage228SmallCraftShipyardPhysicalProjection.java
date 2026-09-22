package com.spacesim.content;

import com.spacesim.content.Stage18ShipyardCatalog.CompartmentRepairProfile;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import com.spacesim.content.Stage18ShipyardCatalog.PhysicalInputDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * M22.8J Stage-18 physical build/repair projection for production small craft.
 *
 * <p>Every added hull profile closes exactly to the authored bare-hull mass. Module repair and
 * scheduled-service inputs are finite physical commodities below module replacement mass. The
 * projection appends no inventory and does not modify yard throughput or settlement authority.</p>
 */
public final class Stage228SmallCraftShipyardPhysicalProjection {
    /** Semantic version of the Stage-18 small-craft physical projection. */
    public static final String VERSION = "m22.8j.small_craft_stage18_physical_projection.v1";

    private Stage228SmallCraftShipyardPhysicalProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds Empire small-craft hull/module physical profiles.
     *
     * @param source accepted Empire Stage-18 shipyard catalog
     * @param engineering M22.8J-completed engineering catalog
     * @return immutable Stage-18 catalog including Empire small-craft physical profiles
     */
    public static Stage18ShipyardCatalog applyEmpire(
            Stage18ShipyardCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(
                source,
                engineering,
                empireHullProfile(),
                Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID);
    }

    /**
     * Adds Union small-craft hull/module physical profiles.
     *
     * @param source accepted Union Stage-18 shipyard catalog
     * @param engineering M22.8J-completed engineering catalog
     * @return immutable Stage-18 catalog including Union small-craft physical profiles
     */
    public static Stage18ShipyardCatalog applyIndustrialUnion(
            Stage18ShipyardCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(
                source,
                engineering,
                unionHullProfile(),
                Stage228SmallCraftProductionProjection.UNION_HULL_ID);
    }

    private static Stage18ShipyardCatalog apply(
            Stage18ShipyardCatalog source,
            ShipEngineeringCatalog engineering,
            HullPhysicalProfile hullProfile,
            String hullId) {
        Stage18ShipyardCatalog checked = Objects.requireNonNull(source, "source");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        var hull = checkedEngineering.findHull(hullId);
        if (hull == null) {
            throw new IllegalArgumentException("small-craft Stage-18 hull absent from engineering: " + hullId);
        }
        if (checked.findHullProfile(hullId) != null) {
            throw new IllegalArgumentException("small-craft Stage-18 hull profile already exists: " + hullId);
        }
        double buildMass = hullProfile.buildInputsKg().stream()
                .mapToDouble(PhysicalInputDefinition::massKg)
                .sum();
        if (Math.abs(buildMass - hull.bareHullMassKg()) > 1e-6d) {
            throw new IllegalArgumentException(
                    "small-craft Stage-18 hull mass does not close: "
                            + hullId + " inputs=" + buildMass + " hull=" + hull.bareHullMassKg());
        }

        ArrayList<HullPhysicalProfile> hulls = new ArrayList<>(checked.getHullProfiles());
        hulls.add(hullProfile);
        ArrayList<ModuleServiceProfile> modules =
                new ArrayList<>(checked.getModuleProfiles());
        for (String moduleId : moduleIds()) {
            if (checked.findModuleProfile(moduleId) != null) {
                throw new IllegalArgumentException(
                        "small-craft Stage-18 module profile already exists: " + moduleId);
            }
            ModuleDefinition module = checkedEngineering.findModule(moduleId);
            if (module == null) {
                throw new IllegalArgumentException(
                        "small-craft Stage-18 module absent from engineering: " + moduleId);
            }
            modules.add(moduleProfile(module));
        }
        return new Stage18ShipyardCatalog(
                checked.getSchemaVersion(),
                checked.getYards(),
                hulls,
                modules);
    }

    private static HullPhysicalProfile empireHullProfile() {
        return new HullPhysicalProfile(
                Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID,
                List.of(
                        input("commodity.material.structural_alloy", 110_000d),
                        input("commodity.material.light_alloy", 25_000d),
                        input("commodity.material.refractory_alloy", 10_000d),
                        input("commodity.material.ceramic_glass", 10_000d),
                        input("commodity.material.carbon_material", 5_000d),
                        input("commodity.material.electronic_grade_material", 4_000d),
                        input("commodity.component.heavy_components", 8_000d),
                        input("commodity.component.electrical_components", 5_000d),
                        input("commodity.component.precision_components", 3_000d)),
                List.of(
                        compartment("engineering", 8_000d, 2_000d),
                        compartment("crew", 6_000d, 1_500d),
                        compartment("mission", 7_000d, 2_000d)));
    }

    private static HullPhysicalProfile unionHullProfile() {
        return new HullPhysicalProfile(
                Stage228SmallCraftProductionProjection.UNION_HULL_ID,
                List.of(
                        input("commodity.material.structural_alloy", 115_000d),
                        input("commodity.material.light_alloy", 30_000d),
                        input("commodity.material.refractory_alloy", 10_000d),
                        input("commodity.material.ceramic_glass", 8_000d),
                        input("commodity.material.carbon_material", 10_000d),
                        input("commodity.material.electronic_grade_material", 5_000d),
                        input("commodity.component.heavy_components", 10_000d),
                        input("commodity.component.electrical_components", 7_000d),
                        input("commodity.component.precision_components", 5_000d)),
                List.of(
                        compartment("engineering", 8_500d, 2_200d),
                        compartment("crew", 6_500d, 1_700d),
                        compartment("mission", 7_500d, 2_200d)));
    }

    private static CompartmentRepairProfile compartment(
            String id,
            double structuralKg,
            double electricalKg) {
        return new CompartmentRepairProfile(
                id,
                List.of(
                        input("commodity.material.structural_alloy", structuralKg),
                        input("commodity.component.electrical_components", electricalKg)));
    }

    private static ModuleServiceProfile moduleProfile(ModuleDefinition module) {
        double repairStructural = module.massKg() * 0.18d;
        double repairElectrical = module.massKg() * 0.10d;
        double repairPrecision = module.massKg() * 0.06d;
        double serviceElectrical = module.massKg() * 0.006d;
        double servicePrecision = module.massKg() * 0.004d;
        return new ModuleServiceProfile(
                module.id(),
                List.of(
                        input("commodity.material.structural_alloy", repairStructural),
                        input("commodity.component.electrical_components", repairElectrical),
                        input("commodity.component.precision_components", repairPrecision)),
                List.of(
                        input("commodity.component.electrical_components", serviceElectrical),
                        input("commodity.component.precision_components", servicePrecision)));
    }

    private static PhysicalInputDefinition input(String commodityId, double massKg) {
        return new PhysicalInputDefinition(commodityId, massKg);
    }

    private static List<String> moduleIds() {
        return List.of(
                Stage228SmallCraftProductionProjection.REACTOR_ID,
                Stage228SmallCraftProductionProjection.DRIVE_ID,
                Stage228SmallCraftProductionProjection.SENSOR_ID,
                Stage228SmallCraftProductionProjection.RADIATOR_ID,
                Stage228SmallCraftProductionProjection.SHIELD_ID,
                Stage228SmallCraftProductionProjection.BEAM_ID,
                Stage228SmallCraftProductionProjection.KINETIC_ID);
    }
}
