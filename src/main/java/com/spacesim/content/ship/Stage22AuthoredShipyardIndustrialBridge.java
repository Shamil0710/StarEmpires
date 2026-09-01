package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipyardIndustrialCatalog.HullIndustrialProfile;
import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

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
     * Duplicate IDs fail closed before construction.
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
        List<HullIndustrialProfile> additionsHulls = nonNullCopy(hulls, "hulls");
        List<ModuleIndustrialProfile> additionsModules = nonNullCopy(modules, "modules");
        rejectDuplicateHullIds(checked, additionsHulls);
        rejectDuplicateModuleIds(checked, additionsModules);

        List<HullIndustrialProfile> combinedHulls = new ArrayList<>(checked.getHullProfiles());
        List<ModuleIndustrialProfile> combinedModules = new ArrayList<>(checked.getModuleProfiles());
        combinedHulls.addAll(additionsHulls);
        combinedModules.addAll(additionsModules);
        return new ShipyardIndustrialCatalog(
                checked.getSchemaVersion(), combinedHulls, combinedModules);
    }

    private static void rejectDuplicateHullIds(
            ShipyardIndustrialCatalog base,
            List<HullIndustrialProfile> additions) {
        Set<String> seen = new HashSet<>();
        for (HullIndustrialProfile value : additions) {
            if (base.findHullProfile(value.hullId()) != null || !seen.add(value.hullId())) {
                throw new IllegalArgumentException("Duplicate authored hull industrial profile: " + value.hullId());
            }
        }
    }

    private static void rejectDuplicateModuleIds(
            ShipyardIndustrialCatalog base,
            List<ModuleIndustrialProfile> additions) {
        Set<String> seen = new HashSet<>();
        for (ModuleIndustrialProfile value : additions) {
            if (base.findModuleProfile(value.moduleId()) != null || !seen.add(value.moduleId())) {
                throw new IllegalArgumentException("Duplicate authored module industrial profile: " + value.moduleId());
            }
        }
    }

    private static <T> List<T> nonNullCopy(List<T> values, String name) {
        List<T> result = new ArrayList<>(Objects.requireNonNull(values, name));
        result.replaceAll(value -> Objects.requireNonNull(value, name + " entry"));
        return result;
    }
}
