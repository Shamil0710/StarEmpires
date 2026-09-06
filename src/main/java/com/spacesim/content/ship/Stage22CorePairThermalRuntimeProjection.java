package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Closes the common Stage-17.5 thermal-runtime seam for the two Stage-22 core engineering packages.
 *
 * <p>M22.3/M22.4 authored radiator rejection and per-module coolant-transfer demand, but predate the
 * runtime's explicit ship coolant-bus capability key. A radiator without that key rejects heat only
 * from an empty ship bus, so ordinary cooldown ticks can saturate otherwise healthy reactors. M22.6
 * maps the already-authored physical radiator rejection rate to the common coolant-bus ceiling. The
 * mapping is identical for both factions and introduces no extra rejection capacity, faction bonus,
 * or parallel thermal authority.</p>
 */
public final class Stage22CorePairThermalRuntimeProjection {
    /** Semantic version pinned by the M22.6 runtime-completion surface. */
    public static final String VERSION = "stage22.core_pair_thermal_runtime_projection.v1";

    private Stage22CorePairThermalRuntimeProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Adds the ordinary Stage-17.5 coolant-bus capability to every core thermal-control module.
     *
     * @param source accepted Stage-22 engineering catalog
     * @return immutable catalog whose radiator rejection can receive ordinary module coolant flow
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules().size());
        int thermalCount = 0;
        for (ModuleDefinition module : checked.getModules()) {
            if (module.family() != ModuleFamily.THERMAL_CONTROL) {
                modules.add(module);
                continue;
            }
            thermalCount++;
            if (!Double.isFinite(module.heatRejectionW()) || module.heatRejectionW() <= 0d) {
                throw new IllegalArgumentException(
                        "Stage-22 core thermal module requires positive authored heat rejection: " + module.id());
            }
            TreeMap<String, Double> parameters = new TreeMap<>(module.capabilityParameters());
            double physicalBusCeilingW = module.heatRejectionW();
            Double existing = parameters.putIfAbsent(
                    ShipEngineeringRuntime.COOLANT_BUS_CAPACITY_W,
                    physicalBusCeilingW);
            if (existing != null && Double.compare(existing, physicalBusCeilingW) != 0) {
                throw new IllegalArgumentException(
                        "Stage-22 core thermal module conflicts with authored rejection ceiling: " + module.id());
            }
            modules.add(copyWithCapabilities(module, parameters));
        }
        if (thermalCount == 0) {
            throw new IllegalArgumentException("Stage-22 core engineering catalog contains no thermal-control module");
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

    private static ModuleDefinition copyWithCapabilities(
            ModuleDefinition module,
            Map<String, Double> capabilityParameters) {
        return new ModuleDefinition(
                module.id(),
                module.displayName(),
                module.family(),
                module.integrationCategories(),
                module.compatibleHardpointSizes(),
                module.physicalDimensionsM(),
                module.massKg(),
                module.occupiedVolumeM3(),
                module.requiredMountStrengthN(),
                module.continuousPowerSupplyW(),
                module.continuousPowerDemandW(),
                module.peakPowerDemandW(),
                module.storedEnergyCapacityJ(),
                module.wasteHeatW(),
                module.localThermalCapacityJ(),
                module.coolantTransferDemandW(),
                module.heatRejectionW(),
                module.crewRequirement(),
                module.automationRequirement(),
                module.interfaces(),
                module.signatureContributions(),
                module.constructionInputs(),
                module.maintenance(),
                Map.copyOf(capabilityParameters));
    }
}