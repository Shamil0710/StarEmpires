package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Completes Stage-22 core shield modules for the existing Stage-17.5F shield runtime.
 *
 * <p>M22.3/M22.4 already author package-specific field capacity and recharge power. This projection
 * preserves those values by mapping {@code field_capacity_j} to the common runtime's
 * {@code field_reserve_j} and retaining authored {@code recharge_power_w}. The remaining operating
 * terms are identical for both core packages, so no faction-name armor/shield scalar is introduced.</p>
 */
public final class Stage22CorePairShieldModeProjection {
    /** Semantic version of the common shield operating projection. */
    public static final String VERSION = "stage22.core_pair_shield_mode_projection.v1";

    private static final double COMMON_INTERACTION_POWER_W = 10_000_000_000d;
    private static final double COMMON_RECHARGE_EFFICIENCY = 0.80d;
    private static final double COMMON_HEAT_PER_ABSORBED_J = 0.08d;
    private static final double COMMON_RESTART_DELAY_S = 8d;
    private static final double COMMON_COVERAGE_CENTER_RAD = Math.PI / 2d;
    private static final double COMMON_COVERAGE_HALF_ARC_RAD = Math.PI;

    private Stage22CorePairShieldModeProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Returns the same engineering package with common Stage-17.5F shield operating fields completed.
     *
     * @param source accepted Stage-22 engineering catalog
     * @return immutable engineering catalog using the ordinary Stage-17.5 schema
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules().size());
        int shieldCount = 0;
        for (ModuleDefinition module : checked.getModules()) {
            if (module.family() != ModuleFamily.SHIELD_FIELD) {
                modules.add(module);
                continue;
            }
            shieldCount++;
            TreeMap<String, Double> parameters = new TreeMap<>(module.capabilityParameters());
            double authoredCapacity = requirePositive(parameters, "field_capacity_j", module.id());
            double authoredRecharge = requireNonNegative(parameters, "recharge_power_w", module.id());
            putSameOrAbsent(parameters, "field_reserve_j", authoredCapacity, module.id());
            putSameOrAbsent(parameters, "recharge_power_w", authoredRecharge, module.id());
            putSameOrAbsent(parameters, "interaction_power_w", COMMON_INTERACTION_POWER_W, module.id());
            putSameOrAbsent(parameters, "recharge_efficiency", COMMON_RECHARGE_EFFICIENCY, module.id());
            putSameOrAbsent(parameters, "heat_per_absorbed_j", COMMON_HEAT_PER_ABSORBED_J, module.id());
            putSameOrAbsent(parameters, "restart_delay_s", COMMON_RESTART_DELAY_S, module.id());
            putSameOrAbsent(parameters, "coverage_center_rad", COMMON_COVERAGE_CENTER_RAD, module.id());
            putSameOrAbsent(parameters, "coverage_half_arc_rad", COMMON_COVERAGE_HALF_ARC_RAD, module.id());
            modules.add(copyWithCapabilities(module, parameters));
        }
        if (shieldCount == 0) {
            throw new IllegalArgumentException("Stage-22 core engineering catalog contains no shield module");
        }
        return new ShipEngineeringCatalog(
                checked.getSchemaVersion(),
                checked.getMigrationVersion(),
                checked.getMaterials(),
                checked.getResponseSurfaces(),
                checked.getProtectionStacks(),
                checked.getHulls(),
                modules,
                checked.getDemonstratorFits());
    }

    /**
     * Returns the common non-capacity shield operating terms for diagnostics.
     *
     * @return immutable common parameter map
     */
    public static Map<String, Double> commonOperatingParameters() {
        return Map.of(
                "interaction_power_w", COMMON_INTERACTION_POWER_W,
                "recharge_efficiency", COMMON_RECHARGE_EFFICIENCY,
                "heat_per_absorbed_j", COMMON_HEAT_PER_ABSORBED_J,
                "restart_delay_s", COMMON_RESTART_DELAY_S,
                "coverage_center_rad", COMMON_COVERAGE_CENTER_RAD,
                "coverage_half_arc_rad", COMMON_COVERAGE_HALF_ARC_RAD);
    }

    private static ModuleDefinition copyWithCapabilities(
            ModuleDefinition module,
            Map<String, Double> capabilityParameters) {
        return new ModuleDefinition(
                module.id(), module.displayName(), module.family(),
                module.integrationCategories(), module.compatibleHardpointSizes(),
                module.physicalDimensionsM(), module.massKg(), module.occupiedVolumeM3(),
                module.requiredMountStrengthN(), module.continuousPowerSupplyW(), module.continuousPowerDemandW(),
                module.peakPowerDemandW(), module.storedEnergyCapacityJ(), module.wasteHeatW(),
                module.localThermalCapacityJ(), module.coolantTransferDemandW(), module.heatRejectionW(),
                module.crewRequirement(), module.automationRequirement(), module.interfaces(),
                module.signatureContributions(), module.constructionInputs(), module.maintenance(),
                Map.copyOf(capabilityParameters));
    }

    private static void putSameOrAbsent(
            Map<String, Double> parameters,
            String key,
            double expected,
            String moduleId) {
        Double existing = parameters.putIfAbsent(key, expected);
        if (existing != null && Double.compare(existing, expected) != 0) {
            throw new IllegalArgumentException(
                    "Stage-22 core shield conflicts with common operating envelope: " + moduleId + " -> " + key);
        }
    }

    private static double requirePositive(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("Stage-22 shield lacks positive " + key + ": " + moduleId);
        }
        return value;
    }

    private static double requireNonNegative(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("Stage-22 shield lacks non-negative " + key + ": " + moduleId);
        }
        return value;
    }
}
