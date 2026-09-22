package com.spacesim.content;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.Stage22CorePairEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage22CorePairShipyardIndustrialCatalogLoader;
import com.spacesim.content.ship.ShipyardIndustrialCatalog;

/**
 * M22.8J production loader for physical small-craft shipyard support.
 *
 * <p>The accepted M22.3/M22.4 authored Stage-18 resources remain immutable. J starts from those
 * validated physical catalogs, then appends only the new compact hull/module profiles. Industrial
 * coverage is checked against the versioned core-pair J projection so a craft cannot exist in the
 * engineering catalog while being impossible to plan through the ordinary shipyard authority.</p>
 */
public final class Stage228SmallCraftShipyardCatalogLoader {
    private Stage228SmallCraftShipyardCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /** @return Empire Stage-18 catalog with M22.8J small-craft physical profiles */
    public static Stage18ShipyardCatalog loadEmpireDefault() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial =
                Stage22CorePairShipyardIndustrialCatalogLoader.loadEmpireDefault();
        Stage18ShipyardCatalog projected =
                Stage228SmallCraftShipyardPhysicalProjection.applyEmpire(
                        Stage22EmpireShipyardCatalogLoader.loadDefault(),
                        engineering);
        requireCoverage(projected, industrial, engineering,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID);
        return projected;
    }

    /** @return Industrial Union Stage-18 catalog with M22.8J small-craft physical profiles */
    public static Stage18ShipyardCatalog loadIndustrialUnionDefault() {
        ShipEngineeringCatalog engineering = Stage22CorePairEngineeringCatalogLoader.loadDefault();
        ShipyardIndustrialCatalog industrial =
                Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault();
        Stage18ShipyardCatalog projected =
                Stage228SmallCraftShipyardPhysicalProjection.applyIndustrialUnion(
                        Stage22IndustrialUnionShipyardCatalogLoader.loadDefault(),
                        engineering);
        requireCoverage(projected, industrial, engineering,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.UNION_HULL_ID);
        return projected;
    }

    private static void requireCoverage(
            Stage18ShipyardCatalog physical,
            ShipyardIndustrialCatalog industrial,
            ShipEngineeringCatalog engineering,
            String hullId) {
        if (engineering.findHull(hullId) == null
                || industrial.findHullProfile(hullId) == null
                || physical.findHullProfile(hullId) == null) {
            throw new IllegalStateException(
                    "M22.8J small-craft hull lacks complete engineering/industrial/physical coverage: "
                            + hullId);
        }
        for (String moduleId : java.util.List.of(
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.REACTOR_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.DRIVE_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.SENSOR_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.RADIATOR_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.SHIELD_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.BEAM_ID,
                com.spacesim.content.ship.Stage228SmallCraftProductionProjection.KINETIC_ID)) {
            if (engineering.findModule(moduleId) == null
                    || industrial.findModuleProfile(moduleId) == null
                    || physical.findModuleProfile(moduleId) == null) {
                throw new IllegalStateException(
                        "M22.8J small-craft module lacks complete production coverage: " + moduleId);
            }
        }
    }
}
