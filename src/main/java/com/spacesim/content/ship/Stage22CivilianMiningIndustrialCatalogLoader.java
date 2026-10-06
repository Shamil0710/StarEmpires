package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;
import java.util.ArrayList;
import java.util.Objects;

/** Paid integration/service work for the authored mining section through ordinary shipyard planning. */
public final class Stage22CivilianMiningIndustrialCatalogLoader {
    private Stage22CivilianMiningIndustrialCatalogLoader() { }

    /** @return Union industrial requirements extended with the same physical workshop-envelope costs */
    public static ShipyardIndustrialCatalog loadDefault() {
        var base = Stage22CorePairShipyardIndustrialCatalogLoader.loadIndustrialUnionDefault();
        var profile = Objects.requireNonNull(base.findModuleProfile(Stage22CivilianMiningEngineeringCatalogLoader.BASE_WORKSHOP_MODULE_ID));
        var modules = new ArrayList<>(base.getModuleProfiles());
        modules.add(new ModuleIndustrialProfile(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID,
                profile.fabricationCapabilities(), profile.toolingTags(), profile.precisionRequirement(), profile.industrialPowerW(),
                profile.laborRequirement(), profile.automationRequirement(), profile.manufacturingWorkSeconds(),
                profile.installationWorkSeconds(), profile.removalWorkSeconds()));
        modules.add(new ModuleIndustrialProfile(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID,
                profile.fabricationCapabilities(), profile.toolingTags(), profile.precisionRequirement(), profile.industrialPowerW(),
                profile.laborRequirement(), profile.automationRequirement(), profile.manufacturingWorkSeconds(),
                profile.installationWorkSeconds(), profile.removalWorkSeconds()));
        return Stage22FreightStrategicProductionCatalogs.industrial(
                new ShipyardIndustrialCatalog(base.getSchemaVersion(), base.getHullProfiles(), modules));
    }
}
