package com.spacesim.content;

import java.util.ArrayList;

/**
 * Balance scale of everything built during a campaign: facility and yard construction bills and
 * their engineering work. Authored bills (8–500 million kg) made the first ordinary industrial chain
 * take thousands of game hours for a pilot mining about 5 kg/s; campaign construction uses 1/100 of
 * them. World generation keeps the authored catalogues unchanged.
 */
public final class PlayerConstructionScale {
    /** Fraction of the authored installed mass, material bill and engineering work. */
    public static final double FACTOR = 0.01d;

    private PlayerConstructionScale() { }

    /**
     * @param authored authored construction catalogue
     * @return the same profiles with every facility's installed mass scaled by {@link #FACTOR}
     */
    public static Stage18FacilityConstructionCatalog scale(Stage18FacilityConstructionCatalog authored) {
        var bindings = new ArrayList<Stage18FacilityConstructionCatalog.FacilityConstructionDefinition>();
        for (var binding : authored.getFacilities())
            bindings.add(new Stage18FacilityConstructionCatalog.FacilityConstructionDefinition(
                    binding.facilityDefinitionId(), binding.profileId(), binding.installedMassKg() * FACTOR));
        return new Stage18FacilityConstructionCatalog(authored.getSchemaVersion(), authored.getProfiles(), bindings);
    }
}
