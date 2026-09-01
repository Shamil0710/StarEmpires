package com.spacesim.content;

import com.spacesim.content.Stage18ManufacturingCatalog.ProductBindingDefinition;
import com.spacesim.content.Stage18ShipyardCatalog.HullPhysicalProfile;
import com.spacesim.content.Stage18ShipyardCatalog.ModuleServiceProfile;
import com.spacesim.content.Stage18ShipyardCatalog.YardDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Common Stage-22 composition seam for later authored content that must remain inside Stage-18
 * manufacturing and shipyard authorities.
 *
 * <p>The bridge only combines immutable catalog definitions. It creates no inventory, work,
 * treasury value, repair progress or faction state. Empire and Industrial Union packages use the
 * same methods, preventing faction-specific production authorities.</p>
 */
public final class Stage22AuthoredProductionBridge {
    private Stage22AuthoredProductionBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds reviewed product-to-profile bindings to the accepted Stage-18 manufacturing grammar.
     *
     * @param base accepted Stage-18 manufacturing catalog
     * @param additions later-stage product bindings
     * @return combined immutable Stage-18 manufacturing catalog
     */
    public static Stage18ManufacturingCatalog withProductBindings(
            Stage18ManufacturingCatalog base,
            List<ProductBindingDefinition> additions) {
        Stage18ManufacturingCatalog checked = Objects.requireNonNull(base, "base");
        List<ProductBindingDefinition> combined = new ArrayList<>(checked.getProductBindings());
        for (ProductBindingDefinition addition : Objects.requireNonNull(additions, "additions")) {
            ProductBindingDefinition value = Objects.requireNonNull(addition, "product binding");
            if (checked.findProductProfile(value.profileId()) == null) {
                throw new IllegalArgumentException(
                        "Stage-22 product binding references unknown Stage-18 profile: " + value.profileId());
            }
            combined.add(value);
        }
        return new Stage18ManufacturingCatalog(
                checked.getSchemaVersion(),
                checked.getComponentRecipes(),
                checked.getProductProfiles(),
                combined);
    }

    /**
     * Adds later-stage physical yard/hull/module profiles to the accepted Stage-18 shipyard catalog.
     *
     * <p>All duplicate identities fail closed through the ordinary Stage-18 catalog constructor.
     * Callers must validate added profiles against engineering, resource ontology, facilities and
     * Stage-17.5G industrial requirements before composition.</p>
     *
     * @param base accepted Stage-18 shipyard catalog
     * @param yards validated additional yards
     * @param hulls validated additional physical hull profiles
     * @param modules validated additional module service profiles
     * @return combined immutable Stage-18 shipyard catalog
     */
    public static Stage18ShipyardCatalog withShipyardProfiles(
            Stage18ShipyardCatalog base,
            List<YardDefinition> yards,
            List<HullPhysicalProfile> hulls,
            List<ModuleServiceProfile> modules) {
        Stage18ShipyardCatalog checked = Objects.requireNonNull(base, "base");
        List<YardDefinition> combinedYards = new ArrayList<>(checked.getYards());
        List<HullPhysicalProfile> combinedHulls = new ArrayList<>(checked.getHullProfiles());
        List<ModuleServiceProfile> combinedModules = new ArrayList<>(checked.getModuleProfiles());
        combinedYards.addAll(nonNullCopy(yards, "yards"));
        combinedHulls.addAll(nonNullCopy(hulls, "hulls"));
        combinedModules.addAll(nonNullCopy(modules, "modules"));
        return new Stage18ShipyardCatalog(
                checked.getSchemaVersion(), combinedYards, combinedHulls, combinedModules);
    }

    private static <T> List<T> nonNullCopy(List<T> values, String name) {
        List<T> result = new ArrayList<>(Objects.requireNonNull(values, name));
        result.replaceAll(value -> Objects.requireNonNull(value, name + " entry"));
        return result;
    }
}
