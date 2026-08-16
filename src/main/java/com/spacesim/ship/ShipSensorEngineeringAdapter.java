package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.SensorDefinition.Mode;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.SignatureState.Channel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Stage-17.5D/F adapter from the common fitted engineering state to sensor/signature runtime objects.
 *
 * <p>Stage 17.5F consumes the runtime integrity injected by {@link DerivedShipCalculator}: damaged
 * apertures become smaller/noisier and angular/range uncertainty worsens. A destroyed mount exposes
 * no sensor mode. Damage therefore cannot improve a sensor or create a parallel damage bonus.</p>
 */
public final class ShipSensorEngineeringAdapter {
    /** Shared physical aperture key for a multi-mode fitted sensor array. */
    public static final String APERTURE_AREA_M2 = "aperture_area_m2";

    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;
    private static final String THERMAL_W = "thermal_w";
    private static final String PLUME_W = "plume_w";
    private static final String RADAR_CROSS_SECTION_M2 = "radar_cross_section_m2";
    private static final String REFLECTED_OPTICAL_W = "reflected_optical_w";
    private static final String ACTIVE_RADIO_W = "active_radio_w";
    private static final String JAMMER_W = "jammer_w";
    private static final String LEGACY_STATIC_ACTIVE_RADAR_W = "active_radar_w";

    private static final List<ModeSpec> MODES = List.of(
            new ModeSpec("passive_thermal", Mode.PASSIVE_THERMAL, Channel.THERMAL, false),
            new ModeSpec("passive_plume", Mode.PASSIVE_PLUME, Channel.ENGINE_PLUME, false),
            new ModeSpec("passive_optical", Mode.PASSIVE_OPTICAL, Channel.REFLECTED_OPTICAL, false),
            new ModeSpec("passive_radio", Mode.PASSIVE_RADIO, Channel.RADAR, false),
            new ModeSpec("active_radar", Mode.ACTIVE_RADAR, Channel.RADAR, true));

    /**
     * Resolves the fitted sensors and current static ship signature from one central derived state.
     *
     * @param derivedState common fitted engineering state
     * @return immutable deterministic fitted sensor/signature projection
     */
    public FittedSensorSuite derive(DerivedShipState derivedState) {
        DerivedShipState state = Objects.requireNonNull(derivedState, "derivedState");
        SignatureState signature = signature(state.signatureContributions());
        List<FittedSensor> sensors = new ArrayList<>();
        for (InstalledCapability capability : state.installedCapabilities()) {
            if (capability.family() != ModuleFamily.SENSOR_EW_FIRE_CONTROL) {
                continue;
            }
            double integrity = integrity(capability.parameters());
            if (integrity <= MIN_OPERATIONAL_INTEGRITY) {
                continue;
            }
            for (ModeSpec mode : MODES) {
                if (hasMode(capability.parameters(), mode.prefix())) {
                    sensors.add(new FittedSensor(
                            capability.mountId(),
                            capability.moduleId(),
                            buildDefinition(capability, mode, integrity)));
                }
            }
        }
        sensors.sort(Comparator.comparing(FittedSensor::mountId)
                .thenComparing(value -> value.definition().mode().name())
                .thenComparing(FittedSensor::moduleId));
        return new FittedSensorSuite(sensors, signature);
    }

    private static SensorDefinition buildDefinition(
            InstalledCapability capability, ModeSpec mode, double integrity) {
        Map<String, Double> parameters = capability.parameters();
        double authoredAperture = parameters.containsKey(mode.key("aperture_area_m2"))
                ? required(parameters, mode.key("aperture_area_m2"))
                : required(parameters, APERTURE_AREA_M2);
        double aperture = authoredAperture * integrity;
        double activeTransmitPowerW = mode.active()
                ? required(parameters, mode.key("transmit_power_w")) * integrity : 0d;
        double authoredGain = mode.active()
                ? required(parameters, mode.key("transmit_gain_linear")) : 1d;
        double transmitGain = 1d + (authoredGain - 1d) * integrity;
        double activePowerDemandW = mode.active()
                ? required(parameters, mode.key("power_demand_w")) * integrity : 0d;
        double activeWasteHeatW = mode.active()
                ? required(parameters, mode.key("waste_heat_w")) * integrity : 0d;
        double authoredEccm = required(parameters, mode.key("eccm_processing_gain_linear"));
        return new SensorDefinition(
                capability.moduleId() + "." + mode.prefix(),
                mode.mode(),
                mode.channel(),
                aperture,
                required(parameters, mode.key("receiver_noise_w")) / integrity,
                required(parameters, mode.key("detection_snr")),
                required(parameters, mode.key("classification_snr")),
                required(parameters, mode.key("track_snr")),
                required(parameters, mode.key("fire_control_snr")),
                required(parameters, mode.key("bearing_sigma_floor_rad")) / integrity,
                required(parameters, mode.key("range_sigma_fraction")) / integrity,
                activeTransmitPowerW,
                transmitGain,
                activePowerDemandW,
                activeWasteHeatW,
                1d + (authoredEccm - 1d) * integrity,
                required(parameters, mode.key("eccm_power_demand_w")) * integrity,
                required(parameters, mode.key("eccm_waste_heat_w")) * integrity);
    }

    private static SignatureState signature(Map<String, Double> contributions) {
        Map<String, Double> values = Objects.requireNonNull(contributions, "signatureContributions");
        if (values.containsKey(LEGACY_STATIC_ACTIVE_RADAR_W)) {
            throw new IllegalArgumentException(
                    "active_radar_w cannot be a static signature contribution; author transmitter capability instead");
        }
        return new SignatureState(
                values.getOrDefault(THERMAL_W, 0d),
                values.getOrDefault(PLUME_W, 0d),
                values.getOrDefault(RADAR_CROSS_SECTION_M2, 0d),
                values.getOrDefault(REFLECTED_OPTICAL_W, 0d),
                values.getOrDefault(ACTIVE_RADIO_W, 0d),
                values.getOrDefault(JAMMER_W, 0d));
    }

    private static boolean hasMode(Map<String, Double> parameters, String prefix) {
        return parameters.containsKey(prefix + "_receiver_noise_w");
    }

    private static double integrity(Map<String, Double> parameters) {
        double value = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid runtime sensor integrity");
        }
        return value;
    }

    private static double required(Map<String, Double> parameters, String key) {
        Double value = parameters.get(key);
        if (value == null) {
            throw new IllegalArgumentException("Missing fitted sensor capability parameter: " + key);
        }
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("Invalid fitted sensor capability parameter: " + key);
        }
        return value;
    }

    /**
     * One fitted sensor mode bound to its physical module mount.
     *
     * @param mountId physical fitted mount ID
     * @param moduleId sensor module content ID
     * @param definition damage-aware physical sensor definition
     */
    public record FittedSensor(String mountId, String moduleId, SensorDefinition definition) {
        // Compact-constructor validation; record-level Javadoc owns the public parameter contract.
        public FittedSensor {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(definition, "definition");
        }
    }

    /**
     * Deterministic fitted sensor/signature projection for one ship state.
     *
     * @param sensors damage-aware fitted sensor modes
     * @param staticSignature current static fitted signature projection
     */
    public record FittedSensorSuite(List<FittedSensor> sensors, SignatureState staticSignature) {
        // Compact-constructor validation; record-level Javadoc owns the public parameter contract.
        public FittedSensorSuite {
            Objects.requireNonNull(sensors, "sensors");
            if (sensors.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("sensors must not contain null");
            }
            sensors = List.copyOf(sensors);
            Objects.requireNonNull(staticSignature, "staticSignature");
        }
    }

    /** Internal explicit mode namespace; no numeric mode codes or module-ID inference. */
    private record ModeSpec(String prefix, Mode mode, Channel channel, boolean active) {
        private String key(String suffix) {
            return prefix + "_" + suffix;
        }
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
