package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;

import java.util.List;

/**
 * Composes M22.3 Empire manufactured modules into the accepted Stage-18 manufacturing grammar.
 *
 * <p>This loader does not own manufacturing state. It extends the immutable Stage-18 catalog with
 * reviewed Stage-22 profiles/bindings, while the ordinary Stage-18 runtime remains the sole producer
 * of inventory and work outcomes.</p>
 */
public final class Stage22EmpireManufacturingCatalogLoader {
    private Stage22EmpireManufacturingCatalogLoader() {
        throw new AssertionError("utility class");
    }

    /**
     * @return Stage-18 manufacturing catalog plus reusable Stage-22 profiles and Empire bindings
     */
    public static Stage18ManufacturingCatalog loadDefault() {
        return Stage22AuthoredProductionBridge.withManufacturingContent(
                Stage18ManufacturingCatalogLoader.loadDefault(),
                Stage22CommonManufacturingProfiles.definitions(),
                List.of(
                        binding("module.empire_reactor_service_v1", "manufacturing.profile.reactor"),
                        binding("module.empire_drive_endurance_v1", "manufacturing.profile.drive"),
                        binding("module.empire_sensor_command_v1", "manufacturing.profile.sensor"),
                        binding("module.empire_radiator_recessed_v1", "manufacturing.profile.thermal_control"),
                        binding("module.empire_shield_citadel_v1", "manufacturing.profile.shield"),
                        binding("module.empire_kinetic_axial_v1", "manufacturing.profile.kinetic_launcher"),
                        binding("module.empire_cargo_secure_v1", Stage22CommonManufacturingProfiles.CARGO_TANK_STORES),
                        binding("module.empire_tanker_stores_v1", Stage22CommonManufacturingProfiles.CARGO_TANK_STORES),
                        binding("module.empire_hangar_fleet_v1", Stage22CommonManufacturingProfiles.HANGAR_SUPPORT),
                        binding("module.empire_repair_salvage_v1", Stage22CommonManufacturingProfiles.INDUSTRIAL_SUPPORT)));
    }

    private static ProductBindingDefinition binding(String productId, String profileId) {
        return new ProductBindingDefinition(productId, profileId);
    }
}
