package com.spacesim.ship;

import com.spacesim.content.Stage22CivilianMiningProductionPath;
import com.spacesim.content.ship.Stage22CivilianMiningEngineeringCatalogLoader;
import com.spacesim.economy.Stage18ExtractionRuntime.ExtractionCapability;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Read-only extraction capability from actual authored, damage-aware installed equipment. */
public final class ShipMiningEngineeringAdapter {
    /**
     * Projects the single civilian excavation section admitted by current content.
     * Does not infer mining equipment from a hull name, cargo capacity or repair workshop.
     * @param state common damage-aware fitted ship projection
     * @return installed capability, or empty without operating mining hardware
     */
    public Optional<MiningCapability> derive(DerivedShipState state) {
        var checked = Objects.requireNonNull(state, "state");
        var modules = checked.installedCapabilities().stream()
                .filter(m -> m.moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.MINING_MODULE_ID)
                        || m.moduleId().equals(Stage22CivilianMiningEngineeringCatalogLoader.FREIGHT_MINING_MODULE_ID)).toList();
        if (modules.isEmpty()) return Optional.empty();
        if (modules.size() != 1) throw new IllegalArgumentException("Mining fit requires exactly one excavation section");
        var module = modules.get(0);
        double integrity = module.parameters().getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(integrity) || integrity < 0 || integrity > 1)
            throw new IllegalArgumentException("Invalid excavation module integrity");
        if (integrity == 0 || !checked.validation().isValid()
                || checked.continuousPowerMarginW() < 0 || checked.continuousHeatMarginW() < 0)
            return Optional.empty();
        double power = parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_AVAILABLE_POWER_W) * integrity;
        double work = parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_WORK_RATE) * integrity;
        double maintenance = parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAINTENANCE_WORK_RATE) * integrity;
        double throughput = parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAX_SOURCE_KG_S) * integrity;
        return Optional.of(new MiningCapability(module.mountId(),
                new ExtractionCapability("installed-excavation:" + module.mountId(),
                        Set.of(Stage22CivilianMiningProductionPath.EXTRACTION_CAPABILITY_TAG), power, work, maintenance), throughput,
                parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_WORKING_RANGE_M),
                parameter(module.parameters(), Stage22CivilianMiningEngineeringCatalogLoader.PARAM_MAX_DRIFT_M_S)));
    }

    private static double parameter(java.util.Map<String, Double> values, String key) {
        Double value = values.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0)
            throw new IllegalArgumentException("Missing or invalid excavation capability: " + key);
        return value;
    }

    /**
     * One installed section's bounded extraction capability.
     * @param mountId actual installed mount
     * @param extraction existing Stage18 process capability
     * @param maximumSourceKgPerSecond damage-aware installed throughput
     * @param workingRangeM installed working range in metres
     * @param maximumDriftMps permitted drift in metres per second
     */
    public record MiningCapability(String mountId, ExtractionCapability extraction, double maximumSourceKgPerSecond,
            double workingRangeM, double maximumDriftMps) {
        /**
         * Validates the immutable projection.
         * @param mountId actual installed mount
         * @param extraction Stage18 process capability
         * @param maximumSourceKgPerSecond finite positive throughput
         * @param workingRangeM installed working range in metres
         * @param maximumDriftMps permitted drift in metres per second
         */
        public MiningCapability {
            Objects.requireNonNull(mountId); Objects.requireNonNull(extraction);
            if (mountId.isBlank() || !Double.isFinite(maximumSourceKgPerSecond) || maximumSourceKgPerSecond <= 0)
                throw new IllegalArgumentException("Invalid installed mining capability");
            if (!Double.isFinite(workingRangeM) || workingRangeM <= 0
                    || !Double.isFinite(maximumDriftMps) || maximumDriftMps <= 0)
                throw new IllegalArgumentException("Invalid mining contact envelope");
        }

        /**
         * Allocates damage-aware throughput alongside the ordinary finite work budget.
         * The caller must allocate each authoritative simulation interval only once.
         * @param durationSeconds actual completed simulation interval
         * @return shared bounded extraction budget
         */
        public com.spacesim.economy.Stage18ExtractionRuntime.IntervalBudget openInterval(double durationSeconds) {
            return extraction.openInterval(durationSeconds, maximumSourceKgPerSecond);
        }
    }
}
