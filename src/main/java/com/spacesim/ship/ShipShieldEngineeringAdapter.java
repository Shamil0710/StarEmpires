package com.spacesim.ship;

import com.spacesim.content.ship.ShipEngineeringCatalog.ModuleFamily;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.ship.ShipEngineeringState.InstalledCapability;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Projects ordinary fitted {@code SHIELD_FIELD} modules into the Stage-17.5F shield runtime. */
public final class ShipShieldEngineeringAdapter {
    private static final double MIN_OPERATIONAL_INTEGRITY = 1e-6d;

    /**
     * One physical fitted emitter definition plus its current subsystem integrity.
     *
     * @param mountId physical fitted emitter mount
     * @param moduleId shield module content ID
     * @param definition authored physical shield definition
     * @param emitterIntegrity current emitter integrity in (0,1]
     */
    public record FittedShield(
            String mountId,
            String moduleId,
            ShieldFieldRuntime.Definition definition,
            double emitterIntegrity) {
        // Compact-constructor validation; record-level Javadoc owns the public parameter contract.
        public FittedShield {
            requireNonBlank(mountId, "mountId");
            requireNonBlank(moduleId, "moduleId");
            Objects.requireNonNull(definition, "definition");
            if (!Double.isFinite(emitterIntegrity) || emitterIntegrity <= 0d || emitterIntegrity > 1d) {
                throw new IllegalArgumentException("emitterIntegrity must be in (0,1]");
            }
        }

        /**
         * Creates a fully charged field constrained by current emitter damage.
         *
         * @param runtime shield runtime
         * @return damage-clamped charged state
         */
        public ShieldFieldRuntime.State chargedState(ShieldFieldRuntime runtime) {
            ShieldFieldRuntime checkedRuntime = Objects.requireNonNull(runtime, "runtime");
            return checkedRuntime.withEmitterIntegrity(
                    definition, ShieldFieldRuntime.State.charged(definition), emitterIntegrity);
        }
    }

    /**
     * Resolves all operational fitted shield emitters from the central derived state.
     *
     * @param derived common damage-aware derived ship state
     * @return deterministic mount-sorted shield projections
     */
    public List<FittedShield> derive(DerivedShipState derived) {
        DerivedShipState checked = Objects.requireNonNull(derived, "derived");
        List<FittedShield> result = new ArrayList<>();
        for (InstalledCapability capability : checked.installedCapabilities()) {
            FittedShield shield = fromCapability(capability);
            if (shield != null) {
                result.add(shield);
            }
        }
        result.sort(Comparator.comparing(FittedShield::mountId).thenComparing(FittedShield::moduleId));
        return List.copyOf(result);
    }

    /**
     * Projects one installed capability when it is an operational shield emitter.
     *
     * @param capability installed capability
     * @return fitted shield or {@code null} for another family/destroyed emitter
     */
    public FittedShield fromCapability(InstalledCapability capability) {
        InstalledCapability checked = Objects.requireNonNull(capability, "capability");
        if (checked.family() != ModuleFamily.SHIELD_FIELD) {
            return null;
        }
        Map<String, Double> parameters = checked.parameters();
        double integrity = parameters.getOrDefault(DerivedShipCalculator.RUNTIME_INTEGRITY, 1d);
        if (!Double.isFinite(integrity) || integrity < 0d || integrity > 1d) {
            throw new IllegalArgumentException("Invalid fitted shield runtime integrity");
        }
        if (integrity <= MIN_OPERATIONAL_INTEGRITY) {
            return null;
        }
        ShieldFieldRuntime.Definition definition = new ShieldFieldRuntime.Definition(
                checked.mountId(),
                positive(parameters, "field_reserve_j"),
                positive(parameters, "interaction_power_w"),
                nonNegative(parameters, "recharge_power_w"),
                unitInterval(parameters, "recharge_efficiency"),
                nonNegative(parameters, "heat_per_absorbed_j"),
                nonNegative(parameters, "restart_delay_s"),
                finite(parameters, "coverage_center_rad"),
                positive(parameters, "coverage_half_arc_rad"));
        return new FittedShield(checked.mountId(), checked.moduleId(), definition, integrity);
    }

    private static double positive(Map<String, Double> parameters, String key) {
        double value = finite(parameters, key);
        if (value <= 0d) {
            throw new IllegalArgumentException(key + " must be positive");
        }
        return value;
    }

    private static double nonNegative(Map<String, Double> parameters, String key) {
        double value = finite(parameters, key);
        if (value < 0d) {
            throw new IllegalArgumentException(key + " must be non-negative");
        }
        return value;
    }

    private static double unitInterval(Map<String, Double> parameters, String key) {
        double value = finite(parameters, key);
        if (value < 0d || value > 1d) {
            throw new IllegalArgumentException(key + " must be in [0,1]");
        }
        return value;
    }

    private static double finite(Map<String, Double> parameters, String key) {
        Double value = parameters.get(key);
        if (value == null || !Double.isFinite(value)) {
            throw new IllegalArgumentException("Missing/invalid fitted shield capability parameter: " + key);
        }
        return value;
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
    }
}
