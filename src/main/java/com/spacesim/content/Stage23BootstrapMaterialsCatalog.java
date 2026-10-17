package com.spacesim.content;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Additive player-built batch processing content; historical generated capital remains unchanged. */
public final class Stage23BootstrapMaterialsCatalog {
    /** Explicit immutable design identity; not a replacement for the existing advanced plant. */
    public static final String FACILITY_ID = "facility.processing.bootstrap_batch_materials_v1";
    /** Physical mass paid through the existing construction queue. */
    public static final double INSTALLED_MASS_KG = 25_000_000d;

    private Stage23BootstrapMaterialsCatalog() { }

    /** @return runtime facility vocabulary including the separately built low-rate batch workshop */
    public static Stage18FacilityCatalog facilities() { return Holder.FACILITIES; }

    /** @return unchanged historical bills plus the new basic-alloy construction bill */
    public static Stage18FacilityConstructionCatalog construction() { return Holder.CONSTRUCTION; }

    private static final class Holder {
        private static final Stage18FacilityCatalog FACILITIES = facilities();
        private static final Stage18FacilityConstructionCatalog CONSTRUCTION = construction();

        private static Stage18FacilityCatalog facilities() {
            var base = Stage18FacilityCatalogLoader.loadDefault();
            var definitions = new ArrayList<>(base.getFacilities());
            // Large batch vessels and simple local handling trade throughput for a basic-alloy bill.
            // They provide material processing only, never fabrication, extraction or yard equipment.
            definitions.add(new Stage18FacilityCatalog.FacilityDefinition(FACILITY_ID,
                    "Стартовая установка периодической переработки", Stage18FacilityCatalog.FacilityFamily.PROCESSING,
                    Set.of("capability.process.bulk_refining", "capability.process.chemical_processing", "capability.process.advanced_materials"),
                    10_000_000d, 1d, .1d, .8d, 10d, .2d,
                    Set.of("storage.dry_bulk", "storage.liquid_tank", "storage.general_container", "storage.high_value_controlled", "storage.hazardous_controlled"),
                    1000d, Set.of("location.orbital_station")));
            return new Stage18FacilityCatalog(base.getSchemaVersion(), definitions);
        }

        private static Stage18FacilityConstructionCatalog construction() {
            var base = Stage18FacilityConstructionCatalogLoader.loadDefault();
            String profileId = "construction.profile.bootstrap_batch_materials_v1";
            var profiles = new ArrayList<>(base.getProfiles());
            profiles.add(new Stage18FacilityConstructionCatalog.ConstructionProfileDefinition(profileId,
                    "Базовые сплавы для периодической переработки",
                    List.of(new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.structural_alloy", .6),
                            new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.light_alloy", .25),
                            new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.conductor_metal", .15)),
                    Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), .16));
            var bindings = new ArrayList<>(base.getFacilities());
            bindings.add(new Stage18FacilityConstructionCatalog.FacilityConstructionDefinition(FACILITY_ID, profileId, INSTALLED_MASS_KG));
            return new Stage18FacilityConstructionCatalog(base.getSchemaVersion(), profiles, bindings);
        }
    }
}
