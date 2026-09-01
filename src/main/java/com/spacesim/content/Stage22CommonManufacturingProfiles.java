package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ManufacturingInputDefinition;
import com.spacesim.content.Stage18ManufacturingCatalog.ProductProfileDefinition;

import java.util.List;
import java.util.Set;

/** Shared Stage-22 manufacturing profiles required by both current and future faction packages. */
public final class Stage22CommonManufacturingProfiles {
    /** Cargo/tank/stores structural integration profile. */
    public static final String CARGO_TANK_STORES = "manufacturing.profile.stage22_cargo_tank_stores";
    /** Hangar and embarked-craft support profile. */
    public static final String HANGAR_SUPPORT = "manufacturing.profile.stage22_hangar_support";
    /** Mobile repair/salvage/industrial support profile. */
    public static final String INDUSTRIAL_SUPPORT = "manufacturing.profile.stage22_industrial_support";

    private Stage22CommonManufacturingProfiles() {
        throw new AssertionError("utility class");
    }

    /**
     * @return deterministic reusable profiles expressed entirely in Stage-18 commodity/capability grammar
     */
    public static List<ProductProfileDefinition> definitions() {
        return List.of(
                new ProductProfileDefinition(
                        CARGO_TANK_STORES,
                        "Stage-22 Cargo/Tank/Stores Module",
                        List.of(
                                input("commodity.component.heavy_components", 0.34d),
                                input("commodity.component.electrical_components", 0.12d),
                                input("commodity.component.precision_components", 0.04d),
                                input("commodity.material.structural_alloy", 0.25d),
                                input("commodity.material.light_alloy", 0.12d),
                                input("commodity.material.conductor_metal", 0.04d),
                                input("commodity.material.industrial_chemicals", 0.05d),
                                input("commodity.material.carbon_material", 0.04d)),
                        Set.of(
                                "capability.fabrication.assembly",
                                "capability.fabrication.heavy",
                                "capability.fabrication.electrical"),
                        9_000_000d,
                        0.045d,
                        0.0025d),
                new ProductProfileDefinition(
                        HANGAR_SUPPORT,
                        "Stage-22 Hangar Support Module",
                        List.of(
                                input("commodity.component.heavy_components", 0.26d),
                                input("commodity.component.electrical_components", 0.18d),
                                input("commodity.component.precision_components", 0.12d),
                                input("commodity.material.structural_alloy", 0.18d),
                                input("commodity.material.light_alloy", 0.10d),
                                input("commodity.material.conductor_metal", 0.05d),
                                input("commodity.material.electronic_grade_material", 0.05d),
                                input("commodity.material.industrial_chemicals", 0.03d),
                                input("commodity.material.carbon_material", 0.03d)),
                        Set.of(
                                "capability.fabrication.assembly",
                                "capability.fabrication.heavy",
                                "capability.fabrication.electrical",
                                "capability.fabrication.precision"),
                        16_000_000d,
                        0.080d,
                        0.0040d),
                new ProductProfileDefinition(
                        INDUSTRIAL_SUPPORT,
                        "Stage-22 Mobile Industrial Support Module",
                        List.of(
                                input("commodity.component.heavy_components", 0.30d),
                                input("commodity.component.electrical_components", 0.16d),
                                input("commodity.component.precision_components", 0.14d),
                                input("commodity.material.structural_alloy", 0.16d),
                                input("commodity.material.refractory_alloy", 0.06d),
                                input("commodity.material.light_alloy", 0.06d),
                                input("commodity.material.conductor_metal", 0.04d),
                                input("commodity.material.electronic_grade_material", 0.04d),
                                input("commodity.material.industrial_chemicals", 0.04d)),
                        Set.of(
                                "capability.fabrication.assembly",
                                "capability.fabrication.heavy",
                                "capability.fabrication.electrical",
                                "capability.fabrication.precision"),
                        19_000_000d,
                        0.095d,
                        0.0050d));
    }

    private static ManufacturingInputDefinition input(String commodityId, double fraction) {
        return new ManufacturingInputDefinition(commodityId, fraction);
    }
}
