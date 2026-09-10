package com.spacesim.content.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Adds the common Stage-17.5D active-radar operating envelope to Stage-22 core sensor modules.
 *
 * <p>M22.3/M22.4 authored aperture area and package-specific mass/power/heat/crew burdens but stopped
 * before the common sensor adapter's explicit mode fields. M22.6 closes that integration seam using
 * one identical radar mode envelope for both core packages. Existing aperture area remains untouched,
 * so any resulting sensing difference is caused by authored physical aperture and damage, not a
 * faction-name multiplier.</p>
 */
public final class Stage22CorePairSensorModeProjection {
    /** Semantic version of the shared radar-mode projection. */
    public static final String VERSION = "stage22.core_pair_sensor_mode_projection.v1";

    private static final Map<String, Double> COMMON_ACTIVE_RADAR_PARAMETERS = Map.ofEntries(
            Map.entry("active_radar_receiver_noise_w", 0.000000000001d),
            Map.entry("active_radar_detection_snr", 10d),
            Map.entry("active_radar_classification_snr", 20d),
            Map.entry("active_radar_track_snr", 50d),
            Map.entry("active_radar_fire_control_snr", 100d),
            Map.entry("active_radar_bearing_sigma_floor_rad", 0.0001d),
            Map.entry("active_radar_range_sigma_fraction", 0.001d),
            Map.entry("active_radar_transmit_power_w", 45_000_000d),
            Map.entry("active_radar_transmit_gain_linear", 10d),
            Map.entry("active_radar_power_demand_w", 60_000_000d),
            Map.entry("active_radar_waste_heat_w", 15_000_000d),
            Map.entry("active_radar_eccm_processing_gain_linear", 180d),
            Map.entry("active_radar_eccm_power_demand_w", 10_000_000d),
            Map.entry("active_radar_eccm_waste_heat_w", 5_000_000d));

    private Stage22CorePairSensorModeProjection() {
        throw new AssertionError("utility class");
    }

    /**
     * Returns the same engineering content with the common active-radar mode authored on sensor modules.
     *
     * @param source accepted Stage-22 engineering catalog
     * @return immutable engineering catalog using the ordinary Stage-17.5 schema
     */
    public static ShipEngineeringCatalog apply(ShipEngineeringCatalog source) {
        ShipEngineeringCatalog checked = Objects.requireNonNull(source, "source");
        ArrayList<ModuleDefinition> modules = new ArrayList<>(checked.getModules().size());
        int sensorCount = 0;
        for (ModuleDefinition module : checked.getModules()) {
            if (module.family() != ModuleFamily.SENSOR_EW_FIRE_CONTROL) {
                modules.add(module);
                continue;
            }
            sensorCount++;
            TreeMap<String, Double> parameters = new TreeMap<>(module.capabilityParameters());
            if (!parameters.containsKey("aperture_area_m2")) {
                throw new IllegalArgumentException(
                        "Stage-22 core sensor lacks physical aperture before radar projection: " + module.id());
            }
            COMMON_ACTIVE_RADAR_PARAMETERS.forEach((key, value) -> {
                Double existing = parameters.putIfAbsent(key, value);
                if (existing != null && Double.compare(existing, value) != 0) {
                    throw new IllegalArgumentException(
                            "Stage-22 core sensor conflicts with common radar envelope: " + module.id() + " -> " + key);
                }
            });
            modules.add(copyWithCapabilities(module, parameters));
        }
        if (sensorCount == 0) {
            throw new IllegalArgumentException("Stage-22 core engineering catalog contains no sensor module");
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

    /** @return immutable common radar parameter surface for diagnostics/tests */
    public static Map<String, Double> commonActiveRadarParameters() {
        return COMMON_ACTIVE_RADAR_PARAMETERS;
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
