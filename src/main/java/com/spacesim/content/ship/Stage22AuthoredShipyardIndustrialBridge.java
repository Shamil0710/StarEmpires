package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Reusable Stage-22 composition seam for authored shipyard industrial requirements.
 *
 * <p>The bridge extends the accepted Stage-17.5G requirement vocabulary without owning work state,
 * inventory or shipyard runtime authority. Later faction packages provide validated definitions and
 * both core factions use this same composition path.</p>
 */
public final class Stage22AuthoredShipyardIndustrialBridge {
    private Stage22AuthoredShipyardIndustrialBridge() {
        throw new AssertionError("utility class");
    }

    /**
     * Combines validated authored hull/module requirements with an existing industrial catalog.
     * Duplicate IDs remain fail-closed through the resulting catalog index.
     *
     * @param base existing industrial requirement catalog
     * @param hulls validated authored hull requirements
     * @param modules validated authored module requirements
     * @return combined immutable requirement catalog
     */
    public static ShipyardIndustrialCatalog withProfiles(
            ShipyardIndustrialCatalog base,
            List<HullIndustrialProfile> hulls,
            List<ModuleIndustrialProfile> modules) {
        ShipyardIndustrialCatalog checked = Objects.requireNonNull(base, "base");
        List<HullIndustrialProfile> combinedHulls = new ArrayList<>(checked.getHullProfiles());
        List<ModuleIndustrialProfile> combinedModules = new ArrayList<>(checked.getModuleProfiles());
        combinedHulls.addAll(nonNullCopy(hulls, "hulls"));
        combinedModules.addAll(nonNullCopy(modules, "modules"));
        return new ShipyardIndustrialCatalog(
                checked.getSchemaVersion(), combinedHulls, combinedModules);
    }

    private static <T> List<T> nonNullCopy(List<T> values, String name) {
        List<T> result = new ArrayList<>(Objects.requireNonNull(values, name));
        result.replaceAll(value -> Objects.requireNonNull(value, name + " entry"));
        return result;
    }
}
