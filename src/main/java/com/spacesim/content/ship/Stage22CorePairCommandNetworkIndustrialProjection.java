package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipyardIndustrialCatalog.ModuleIndustrialProfile;

import java.util.ArrayList;
import java.util.Objects;
import java.util.Set;

/**
 * M22.6 Stage-18/shipyard integration for the common core command-network datalink.
 *
 * <p>The datalink's physics is common to both core factions. Manufacturing and installation use each
 * package's already-authored industrial vocabulary, which creates paid economic asymmetry without
 * changing support-channel physics or granting inventory.</p>
 */
public final class Stage22CorePairCommandNetworkIndustrialProjection {
    /** Semantic version retained by balance evidence/freeze diagnostics. */
    public static final String VERSION = "stage22.core_pair_command_network_industrial_projection.v1";

    private Stage22CorePairCommandNetworkIndustrialProjection() {
        throw new AssertionError("utility class");
    }

    /** Adds paid Empire manufacturing/integration requirements for the common datalink. */
    public static ShipyardIndustrialCatalog applyEmpire(
            ShipyardIndustrialCatalog source,
            ShipEngineeringCatalog engineering) {
        return apply(source, engineering, empireProfile());
    }

    /** Adds paid Industrial Union manufacturing/integration requirements for the common datalink. */
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
        if (checkedEngineering.findModule(Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID) == null) {
            throw new IllegalArgumentException("Core fleet datalink is absent from engineering catalog");
        }
        if (checked.findModuleProfile(profile.moduleId()) != null) {
            throw new IllegalArgumentException("Core fleet datalink industrial profile already exists");
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
                Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID,
                Set.of("electronics_integration", "precision_alignment", "power_system_integration"),
                Set.of("command_network_alignment_rig"),
                0.72d,
                160_000_000d,
                18,
                24,
                172_800d,
                28_800d,
                18_000d);
    }

    private static ModuleIndustrialProfile unionProfile() {
        return new ModuleIndustrialProfile(
                Stage22CorePairCommandNetworkProjection.DATALINK_MODULE_ID,
                Set.of("common_module_assembly", "electrical_integration"),
                Set.of("common_bank_fixture", "union_module_jig"),
                0.50d,
                120_000_000d,
                14,
                32,
                129_600d,
                21_600d,
                14_400d);
    }
}
