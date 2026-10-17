package com.spacesim.content;

import java.util.*;

/** Separately authored paid storage package; historical bootstrap catalogs remain immutable. */
public final class Stage23StationStorageCatalog {
    /** Stable package and completed-facility identity. */
    public static final String FACILITY_ID = "facility.storage.industrial_liquid_extension_v1";
    /** Only this authored industrial host has the declared installation interface. */
    public static final String HOST_ARCHETYPE = "station.infrastructure.industrial_station";
    /** Authored containment capacity, not a production rate or existing stock. */
    public static final double CAPACITY_KG = 10_000_000d;
    /** Paid structural package mass. */
    public static final double INSTALLED_MASS_KG = 1_000_000d;
    /** Exact typed storage effect, including the bounded installation interface. */
    public static final String EFFECT_DECLARATION = "stage23b.storage-effect.v1|" + FACILITY_ID + "|" + HOST_ARCHETYPE
            + "|location.orbital_station|storage.liquid_tank|10000000kg|1000000kg|one-internal-package|no-rate-increase";
    private Stage23StationStorageCatalog() { }
    /** @return historical facility definitions plus the passive storage package */
    public static Stage18FacilityCatalog facilities() { return Holder.FACILITIES; }
    /** @return historical construction bills plus paid basic-alloy containment */
    public static Stage18FacilityConstructionCatalog construction() { return Holder.CONSTRUCTION; }
    /**
     * @param archetype existing host identity
     * @return whether this host has the authored package interface
     */
    public static boolean acceptsHost(String archetype) { return HOST_ARCHETYPE.equals(archetype); }
    /** @return exact SHA-256 of the typed structural effect */
    public static String effectFingerprint() {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(EFFECT_DECLARATION.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static final class Holder {
        private static final Stage18FacilityCatalog FACILITIES = facilities();
        private static final Stage18FacilityConstructionCatalog CONSTRUCTION = construction();
        private static Stage18FacilityCatalog facilities() {
            var base = Stage23BootstrapMaterialsCatalog.facilities();
            var rows = new ArrayList<>(base.getFacilities());
            rows.add(new Stage18FacilityCatalog.FacilityDefinition(FACILITY_ID, "Жидкостный склад промышленной станции",
                    Stage18FacilityCatalog.FacilityFamily.STORAGE, Set.of("capability.storage.liquid_tank"),
                    0d, 0d, 0d, 0d, 0d, 0d, Set.of("storage.liquid_tank"), 1d, Set.of("location.orbital_station")));
            return new Stage18FacilityCatalog(base.getSchemaVersion(), rows);
        }
        private static Stage18FacilityConstructionCatalog construction() {
            var base = Stage23BootstrapMaterialsCatalog.construction();
            var profiles = new ArrayList<>(base.getProfiles());
            String profile = "construction.profile.industrial_liquid_extension_v1";
            profiles.add(new Stage18FacilityConstructionCatalog.ConstructionProfileDefinition(profile,
                    "Оплачиваемая ёмкость и жидкостный интерфейс",
                    List.of(new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.structural_alloy", .7),
                            new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.light_alloy", .2),
                            new Stage18FacilityConstructionCatalog.ConstructionInputDefinition("commodity.material.conductor_metal", .1)),
                    Set.of("capability.fabrication.heavy", "capability.fabrication.assembly"), .16));
            var bindings = new ArrayList<>(base.getFacilities());
            bindings.add(new Stage18FacilityConstructionCatalog.FacilityConstructionDefinition(FACILITY_ID, profile, INSTALLED_MASS_KG));
            return PlayerConstructionScale.scale(new Stage18FacilityConstructionCatalog(base.getSchemaVersion(), profiles, bindings));
        }
    }
}
