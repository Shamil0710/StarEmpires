package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * M22.6 Stage-18/shipyard integration for the common core strategic FTL module.
 *
 * <p>The physical module is common, but each faction manufactures and integrates it through the
 * already-authored industrial language of its package. Empire uses its precision/power-system line;
 * Industrial Union uses its common-module/electrical line. These profiles add work/capability cost
 * only and never alter FTL physics or grant inventory.</p>
 */
public final class Stage22CorePairStrategicMobilityIndustrialProjection {
    /** Semantic version retained by balance evidence/freeze diagnostics. */
    public static final String VERSION = "stage22.core_pair_strategic_mobility_industrial_projection.v1";

    private Stage22CorePairStrategicMobilityIndustrialProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds the common FTL module to the Empire's existing shipyard-industrial vocabulary.
     *
     * @param source accepted M22.3 Empire industrial catalog
     * @param engineering runtime-completed core engineering catalog containing the FTL module
     * @return immutable industrial catalog with one additional paid module profile
     */
    public static ShipyardIndustrialCatalog applyEmpire(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(source, engineering, empireProfile());
    }

    /**
     * Adds the common FTL module to the Industrial Union's existing shipyard-industrial vocabulary.
     *
     * @param source accepted M22.4 Union industrial catalog
     * @param engineering runtime-completed core engineering catalog containing the FTL module
     * @return immutable industrial catalog with one additional paid module profile
     */
    public static ShipyardIndustrialCatalog applyIndustrialUnion(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(source, engineering, unionProfile());
    }

    private static ShipyardIndustrialCatalog apply(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering,
            ModuleIndustrialProfile profile) {
        ShipyardIndustrialCatalog checked = Objects.requireNonNull(source, "source");
        ShipEngineeringCatalog checkedEngineering = Objects.requireNonNull(engineering, "engineering");
        if (checkedEngineering.findModule(Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID) == null) {
            throw new IllegalArgumentException("Core strategic FTL module is absent from engineering catalog");
        }
        if (checked.findModuleProfile(profile.moduleId()) != null) {
            throw new IllegalArgumentException("Core strategic FTL industrial profile already exists");
        }
        ArrayList<ModuleIndustrialProfile> modules = new ArrayList<>(checked.getModuleProfiles());
        modules.add(profile);
        return new ShipyardIndustrialCatalog(
                checked.getSchemaVersion(),
                checked.getHullProfiles(),
                modules);
    }

    private static ModuleIndustrialProfile empireProfile() {
        return new ModuleIndustrialProfile(
                Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID,
                Set.of("electronics_integration", "precision_alignment", "power_system_integration"),
                Set.of("field_emitter_alignment_rig"),
                0.90d,
                420_000_000d,
                36,
                34,
                604_800d,
                86_400d,
                57_600d);
    }

    private static ModuleIndustrialProfile unionProfile() {
        return new ModuleIndustrialProfile(
                Stage22CorePairStrategicMobilityProjection.FTL_MODULE_ID,
                Set.of("common_module_assembly", "electrical_integration"),
                Set.of("common_bank_fixture", "union_module_jig"),
                0.65d,
                250_000_000d,
                32,
                48,
                504_000d,
                64_800d,
                43_200d);
    }
}
