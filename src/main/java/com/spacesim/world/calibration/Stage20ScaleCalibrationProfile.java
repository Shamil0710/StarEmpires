package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned machine-readable Stage-20 scale-calibration output.
 *
 * <p>The first profile version intentionally contains only the production escort-destroyer fit that
 * is already backed by the current engineering catalog. Later Stage-20A slices may expand the
 * representative set, but must not silently invent production hulls that do not yet exist in
 * authored content.</p>
 *
 * @param version stable calibration-profile version
 * @param representativeShips deterministic representative propulsion envelopes
 */
public record Stage20ScaleCalibrationProfile(
        String version,
        List<RepresentativeShipPropulsionEnvelope> representativeShips) {
    /** Current Stage-20A local-propulsion calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.local-propulsion.v1";

    private static final String ESCORT_FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final String ESCORT_LOAD_CASE_ID = "load.escort_destroyer.full_reaction_mass_v1";
    private static final double ESCORT_REACTION_MASS_KG = 1_800_000d;

    /**
     * Creates an immutable deterministically ordered calibration profile.
     *
     * @param version stable calibration-profile version
     * @param representativeShips representative propulsion envelopes
     */
    public Stage20ScaleCalibrationProfile {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        Objects.requireNonNull(representativeShips, "representativeShips");
        ArrayList<RepresentativeShipPropulsionEnvelope> copy = new ArrayList<>(representativeShips);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("representativeShips must not contain null");
        }
        copy.sort(Comparator.comparing(RepresentativeShipPropulsionEnvelope::sourceFitId)
                .thenComparing(RepresentativeShipPropulsionEnvelope::loadCaseId));
        representativeShips = List.copyOf(copy);
    }

    /**
     * Derives the current profile from production engineering content.
     *
     * <p>The reaction-mass load is the accepted full load already exercised by the production
     * escort-destroyer engineering tests. No mass, thrust, exhaust velocity or delta-v value is
     * duplicated here: all are resolved by {@link DerivedShipCalculator}.</p>
     *
     * @return deterministic current calibration profile
     */
    public static Stage20ScaleCalibrationProfile deriveCurrent() {
        ShipEngineeringCatalog catalog = ShipEngineeringCatalogLoader.loadDefault();
        DerivedShipCalculator calculator = new DerivedShipCalculator(catalog);
        ConsumableState escortLoaded = new ConsumableState(
                0d,
                0d,
                0d,
                0d,
                List.of(new ConsumableLoad(
                        "core_drive",
                        "propellant_feed",
                        InterfaceKind.REACTION_MASS,
                        ESCORT_REACTION_MASS_KG,
                        ESCORT_REACTION_MASS_KG,
                        0L)));
        DerivedShipState escort = calculator.deriveDemonstrator(
                ESCORT_FIT_ID, escortLoaded, DamageState.pristine());

        RepresentativeShipPropulsionEnvelope envelope = Stage20ScaleCalibrationCalculator.derive(
                ESCORT_FIT_ID, ESCORT_LOAD_CASE_ID, escort);
        return new Stage20ScaleCalibrationProfile(CURRENT_VERSION, List.of(envelope));
    }

    /**
     * One representative local-propulsion measurement derived from authoritative ship state.
     *
     * @param sourceFitId production engineering fit ID
     * @param loadCaseId versioned calibration load case
     * @param wetMassKg departure mass including reaction mass
     * @param dryMassAfterReactionKg mass after exhausting the represented reaction mass
     * @param reactionMassKg represented reaction-mass load
     * @param reactionMassFraction reaction mass divided by wet mass
     * @param thrustN authoritative available thrust
     * @param massFlowKgPerS authoritative propulsion mass flow
     * @param initialAccelerationMps2 authoritative departure acceleration
     * @param terminalAccelerationMps2 acceleration at the end of the represented reaction-mass burn
     * @param effectiveExhaustVelocityMps authoritative effective exhaust velocity
     * @param deltaVMps authoritative idealized available delta-v
     * @param fullBurnDurationS duration required to consume the represented reaction mass
     * @param symmetricPeakSpeedMps peak speed of the full-burn equal-delta-v rest-to-rest manoeuvre
     * @param accelerationBurnDurationS duration of the acceleration leg
     * @param brakingBurnDurationS duration of the braking leg
     * @param accelerationDistanceM distance covered by the acceleration leg
     * @param brakingDistanceM distance covered by the braking leg
     * @param characteristicRestToRestDistanceM total distance covered by both full-burn legs without coast
     */
    public record RepresentativeShipPropulsionEnvelope(
            String sourceFitId,
            String loadCaseId,
            double wetMassKg,
            double dryMassAfterReactionKg,
            double reactionMassKg,
            double reactionMassFraction,
            double thrustN,
            double massFlowKgPerS,
            double initialAccelerationMps2,
            double terminalAccelerationMps2,
            double effectiveExhaustVelocityMps,
            double deltaVMps,
            double fullBurnDurationS,
            double symmetricPeakSpeedMps,
            double accelerationBurnDurationS,
            double brakingBurnDurationS,
            double accelerationDistanceM,
            double brakingDistanceM,
            double characteristicRestToRestDistanceM) {
        /** Creates one validated immutable representative measurement. */
        public RepresentativeShipPropulsionEnvelope {
            requireNonBlank(sourceFitId, "sourceFitId");
            requireNonBlank(loadCaseId, "loadCaseId");
            requirePositiveFinite(wetMassKg, "wetMassKg");
            requirePositiveFinite(dryMassAfterReactionKg, "dryMassAfterReactionKg");
            requirePositiveFinite(reactionMassKg, "reactionMassKg");
            requirePositiveFinite(reactionMassFraction, "reactionMassFraction");
            requirePositiveFinite(thrustN, "thrustN");
            requirePositiveFinite(massFlowKgPerS, "massFlowKgPerS");
            requirePositiveFinite(initialAccelerationMps2, "initialAccelerationMps2");
            requirePositiveFinite(terminalAccelerationMps2, "terminalAccelerationMps2");
            requirePositiveFinite(effectiveExhaustVelocityMps, "effectiveExhaustVelocityMps");
            requirePositiveFinite(deltaVMps, "deltaVMps");
            requirePositiveFinite(fullBurnDurationS, "fullBurnDurationS");
            requirePositiveFinite(symmetricPeakSpeedMps, "symmetricPeakSpeedMps");
            requirePositiveFinite(accelerationBurnDurationS, "accelerationBurnDurationS");
            requirePositiveFinite(brakingBurnDurationS, "brakingBurnDurationS");
            requirePositiveFinite(accelerationDistanceM, "accelerationDistanceM");
            requirePositiveFinite(brakingDistanceM, "brakingDistanceM");
            requirePositiveFinite(characteristicRestToRestDistanceM, "characteristicRestToRestDistanceM");
            if (!(dryMassAfterReactionKg < wetMassKg)) {
                throw new IllegalArgumentException("dryMassAfterReactionKg must be smaller than wetMassKg");
            }
            if (!(reactionMassFraction < 1d)) {
                throw new IllegalArgumentException("reactionMassFraction must be smaller than one");
            }
        }

        private static void requireNonBlank(String value, String field) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(field + " must not be blank");
            }
        }

        private static void requirePositiveFinite(double value, String field) {
            if (!Double.isFinite(value) || value <= 0d) {
                throw new IllegalArgumentException(field + " must be positive and finite");
            }
        }
    }
}
