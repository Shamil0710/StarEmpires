package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleDefinition;
import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;
import com.spacesim.ship.WeaponDefinition.BeamWeapon;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Projects fitted beam-capable engineering modules into the existing physical beam runtime.
 *
 * <p>Beam modules deliberately have no fake ammunition feed or launcher profile. Electrical demand,
 * local waste heat and continuous dwell derive from the same fitted module that participates in the
 * common power/thermal budgets. Destroyed mounts disappear; partial integrity worsens pointing
 * stability and available continuous dwell without silently increasing any lower-is-better value.</p>
 */
public final class ShipBeamEngineeringAdapter {
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;
    private final ShipEngineeringCatalog catalog;

    /**
     * Creates a fitted beam adapter over one production engineering catalog.
     *
     * @param catalog production engineering definitions
     */
    public ShipBeamEngineeringAdapter(ShipEngineeringCatalog catalog) {
        this.catalog = Objects.requireNonNull(catalog, "catalog");
    }

    /**
     * One fitted beam emitter ready for the common beam + engineering-grant services.
     *
     * @param mountId physical fitted emitter mount
     * @param moduleId engineering module content ID
     * @param weapon physical beam definition
     */
    public record FittedBeamMount(String mountId, String moduleId, BeamWeapon weapon) {
        /**
         * Validates one immutable fitted beam mount.
         *
         * @param mountId physical fitted emitter mount
         * @param moduleId engineering module content ID
         * @param weapon physical beam definition
         */
        public FittedBeamMount {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(weapon, "weapon");
        }
    }

    /**
     * Resolves all operational beam emitters from one current damage-aware derived ship.
     *
     * @param derived central derived ship state
     * @return deterministic immutable fitted beam mount list
     */
    public List<FittedBeamMount> deriveBeamMounts(DerivedShipState derived) {
        DerivedShipState checked = Objects.requireNonNull(derived, "derived");
        List<FittedBeamMount> result = new ArrayList<>();
        for (InstalledCapability capability : checked.installedCapabilities()) {
            if (capability.family() != ModuleFamily.WEAPON_AMMUNITION
                    || !isBeamCapability(capability.parameters())) {
                continue;
            }
            double integrity = runtimeIntegrity(capability.parameters());
            if (integrity <= MIN_OPERATIONAL_INTEGRITY) {
                continue;
            }
            ModuleDefinition module = catalog.findModule(capability.moduleId());
            if (module == null) {
                throw new IllegalArgumentException("Unknown fitted beam module: " + capability.moduleId());
            }
            double wavelengthM = positive(capability.parameters(), "wavelength_m", capability.moduleId());
            double apertureM = positive(capability.parameters(), "beam_aperture_m", capability.moduleId());
            double jitterRad = nonNegative(capability.parameters(), "pointing_jitter_rad", capability.moduleId())
                    / integrity;
            double beamPowerW = positive(capability.parameters(), "beam_power_w", capability.moduleId());
            double electricalPowerW = Math.max(module.continuousPowerDemandW(), module.peakPowerDemandW());
            if (!(electricalPowerW > 0d)) {
                throw new IllegalArgumentException("Beam module requires positive electrical demand: " + module.id());
            }
            double wasteHeatW = module.wasteHeatW();
            if (!(wasteHeatW > 0d)) {
                throw new IllegalArgumentException("Beam module requires positive waste heat: " + module.id());
            }
            double localCapacityJ = module.localThermalCapacityJ() * integrity;
            double maxDwellSeconds = localCapacityJ / wasteHeatW;
            result.add(new FittedBeamMount(
                    capability.mountId(),
                    capability.moduleId(),
                    new BeamWeapon(
                            "beam." + capability.moduleId(),
                            wavelengthM,
                            apertureM,
                            jitterRad,
                            beamPowerW,
                            electricalPowerW,
                            wasteHeatW,
                            maxDwellSeconds)));
        }
        result.sort(Comparator.comparing(FittedBeamMount::mountId).thenComparing(FittedBeamMount::moduleId));
        return List.copyOf(result);
    }

    private static boolean isBeamCapability(Map<String, Double> parameters) {
        return parameters.containsKey("beam_power_w")
                && parameters.containsKey("beam_aperture_m")
                && parameters.containsKey("wavelength_m");
    }

    private static double runtimeIntegrity(Map<String, Double> parameters) {
        double value = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(value) || value < 0d || value > 1d) {
            throw new IllegalArgumentException("Invalid runtime beam integrity");
        }
        return value;
    }

    private static double positive(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException("Beam module lacks positive " + key + ": " + moduleId);
        }
        return value;
    }

    private static double nonNegative(Map<String, Double> parameters, String key, String moduleId) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException("Beam module lacks non-negative " + key + ": " + moduleId);
        }
        return value;
    }

    private static void requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must be non-blank");
        }
    }
}
