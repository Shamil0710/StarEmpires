package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import java.util.List;

/** Composes M22.4 Union modules into the existing Stage-18 manufacturing authority. */
public final class Stage22IndustrialUnionManufacturingCatalogLoader {
    private Stage22IndustrialUnionManufacturingCatalogLoader(){throw new AssertionError("utility class");}

    /**
     * Loads the accepted Stage-18 manufacturing catalog plus reusable Stage-22 profiles and
     * Industrial Union product bindings.
     *
     * @return immutable manufacturing catalog using the ordinary Stage-18 authority
     */
    public static Stage18ManufacturingCatalog loadDefault(){
        return Stage22AuthoredProductionBridge.withManufacturingContent(
                Stage18ManufacturingCatalogLoader.loadDefault(), Stage22CommonManufacturingProfiles.definitions(), List.of(
                        binding("module.industrial_union_reactor_bank_v1","manufacturing.profile.reactor"),
                        binding("module.industrial_union_drive_bank_v1","manufacturing.profile.drive"),
                        binding("module.industrial_union_sensor_block_v1","manufacturing.profile.sensor"),
                        binding("module.industrial_union_radiator_panel_v1","manufacturing.profile.thermal_control"),
                        binding("module.industrial_union_defense_cassette_v1","manufacturing.profile.shield"),
                        binding("module.industrial_union_weapon_cassette_v1","manufacturing.profile.kinetic_launcher"),
                        binding("module.industrial_union_cargo_section_v1",Stage22CommonManufacturingProfiles.CARGO_TANK_STORES),
                        binding("module.industrial_union_tanker_section_v1",Stage22CommonManufacturingProfiles.CARGO_TANK_STORES),
                        binding("module.industrial_union_hangar_section_v1",Stage22CommonManufacturingProfiles.HANGAR_SUPPORT),
                        binding("module.industrial_union_workshop_section_v1",Stage22CommonManufacturingProfiles.INDUSTRIAL_SUPPORT)));
    }
    private static ProductBindingDefinition binding(String product,String profile){return new ProductBindingDefinition(product,profile);}
}
