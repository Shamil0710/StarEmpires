package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.ship.Stage228SmallCraftEngineeringCatalogLoader;
import com.spacesim.content.ship.Stage228SmallCraftProductionProjection;
import java.util.List;

/** Additive production vocabulary for paid small-craft construction; creates no assets or work. */
public final class Stage23SmallCraftProductionCatalogs {
    private Stage23SmallCraftProductionCatalogs() { }

    /** @return the seven already authored small-craft module identities */
    public static List<String> moduleIds() {
        return List.of(Stage228SmallCraftProductionProjection.REACTOR_ID,
                Stage228SmallCraftProductionProjection.DRIVE_ID,
                Stage228SmallCraftProductionProjection.SENSOR_ID,
                Stage228SmallCraftProductionProjection.RADIATOR_ID,
                Stage228SmallCraftProductionProjection.SHIELD_ID,
                Stage228SmallCraftProductionProjection.BEAM_ID,
                Stage228SmallCraftProductionProjection.KINETIC_ID);
    }

    /** @return historical civilian products plus exact authored small-craft module masses */
    public static Stage18ManufacturingProductRegistry products() { return Holder.PRODUCTS; }

    /** @return existing material-closed family recipes bound to small-craft modules */
    public static Stage18ManufacturingCatalog manufacturing() { return Holder.MANUFACTURING; }

    /** @return existing runtime service definitions plus the compact hull/module profiles */
    public static Stage18ShipyardCatalog shipyards() { return Holder.SHIPYARDS; }

    /** @return the six existing production fits; no provisional or caller-supplied fitting */
    public static List<String> designIds() {
        return List.of(Stage228SmallCraftProductionProjection.EMPIRE_INTERCEPTOR_FIT_ID,
                Stage228SmallCraftProductionProjection.EMPIRE_DEFENCE_FIT_ID,
                Stage228SmallCraftProductionProjection.EMPIRE_STRIKE_FIT_ID,
                Stage228SmallCraftProductionProjection.UNION_INTERCEPTOR_FIT_ID,
                Stage228SmallCraftProductionProjection.UNION_DEFENCE_FIT_ID,
                Stage228SmallCraftProductionProjection.UNION_STRIKE_FIT_ID);
    }

    /**
     * Resolves the authored fitting without accepting arbitrary installed hardware.
     * @param designId existing production craft fit
     * @return exact authored installed fit
     */
    public static com.spacesim.ship.ShipEngineeringState.InstalledFit fit(String designId) {
        if (!designIds().contains(designId)) throw new IllegalArgumentException("Unknown small-craft design");
        return Holder.FITS.get(designId);
    }

    /**
     * Uses the existing industrial requirements for the selected design family.
     * @param designId existing production craft fit
     * @return ordinary shipyard planner, without operating resource grants
     */
    public static com.spacesim.ship.ShipyardEngineeringService planner(String designId) {
        var target = fit(designId);
        return target.hullId().equals(Stage228SmallCraftProductionProjection.EMPIRE_HULL_ID)
                ? Holder.EMPIRE_PLANNER : Holder.UNION_PLANNER;
    }

    private static final class Holder {
        private static final com.spacesim.content.ship.ShipEngineeringCatalog ENGINEERING = Stage228SmallCraftEngineeringCatalogLoader.loadDefault();
        private static final com.spacesim.ship.ShipyardEngineeringService EMPIRE_PLANNER = new com.spacesim.ship.ShipyardEngineeringService(ENGINEERING,
                com.spacesim.content.ship.Stage228SmallCraftShipyardIndustrialCatalogLoader.loadEmpireDefault());
        private static final com.spacesim.ship.ShipyardEngineeringService UNION_PLANNER = new com.spacesim.ship.ShipyardEngineeringService(ENGINEERING,
                com.spacesim.content.ship.Stage228SmallCraftShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault());
        private static final java.util.Map<String, com.spacesim.ship.ShipEngineeringState.InstalledFit> FITS = designIds().stream().collect(
                java.util.stream.Collectors.toUnmodifiableMap(id -> id, id -> com.spacesim.ship.ShipEngineeringState.InstalledFit.fromDemonstrator(ENGINEERING.findDemonstratorFit(id))));
        private static final Stage18ManufacturingProductRegistry PRODUCTS =
                Stage22CivilianMiningProductionPath.loadProducts().withEngineeringModules(
                        Stage228SmallCraftEngineeringCatalogLoader.loadDefault(), moduleIds(),
                        Stage18ManufacturingProductRegistry.Provenance.STAGE22_AUTHORED);
        private static final Stage18ManufacturingCatalog MANUFACTURING = manufacturingCatalog();
        private static final Stage18ShipyardCatalog SHIPYARDS = shipyardCatalog();

        private static Stage18ManufacturingCatalog manufacturingCatalog() {
            var profiles = List.of("reactor", "drive", "sensor", "thermal_control", "shield",
                    "beam_launcher", "kinetic_launcher");
            var ids = moduleIds();
            var bindings = new java.util.ArrayList<ProductBindingDefinition>();
            for (int i = 0; i < ids.size(); i++) bindings.add(new ProductBindingDefinition(
                    ids.get(i), "manufacturing.profile." + profiles.get(i)));
            return Stage22AuthoredProductionBridge.withProductBindings(
                    Stage22CivilianMiningProductionPath.loadManufacturing(), bindings);
        }

        private static Stage18ShipyardCatalog shipyardCatalog() {
            var result = Stage22CivilianMiningProductionPath.loadRuntimeShipyards();
            for (var extension : List.of(Stage228SmallCraftShipyardCatalogLoader.loadEmpireDefault(),
                    Stage228SmallCraftShipyardCatalogLoader.loadIndustrialUnionDefault())) {
                var existing = result;
                result = Stage22AuthoredProductionBridge.withShipyardProfiles(existing, List.of(),
                        extension.getHullProfiles().stream()
                                .filter(h -> existing.findHullProfile(h.hullId()) == null).toList(),
                        extension.getModuleProfiles().stream()
                                .filter(m -> existing.findModuleProfile(m.moduleId()) == null).toList());
            }
            return result;
        }
    }
}
