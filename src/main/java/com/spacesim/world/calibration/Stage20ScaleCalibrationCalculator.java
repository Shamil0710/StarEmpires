package com.spacesim.world.calibration;

import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.ReferenceDefinition;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.Objects;

/**
 * Converts authoritative or explicitly provisional ship capability into Stage-20 spatial measurements.
 *
 * <p>Production mass, thrust, mass flow and rocket-equation delta-v remain owned by the Stage-17.5
 * engineering pipeline and arrive through {@link DerivedShipState}. Explicitly accepted provisional
 * references are handled by a separate entry point and retain per-reference provenance in the result.
 * The equations owned here describe only the variable-mass calibration manoeuvre.</p>
 */
public final class Stage20ScaleCalibrationCalculator {
    private Stage20ScaleCalibrationCalculator() {
        throw new AssertionError("utility class");
    }

    /**
     * Derives one production local-propulsion calibration envelope.
     *
     * @param representativeId stable Stage-20 representative class/role ID
     * @param provenanceId production engineering fit/content provenance ID
     * @param loadCaseId stable Stage-20 calibration load-case ID
     * @param state authoritative derived ship state
     * @return immutable propulsion envelope in SI units
     */
    public static RepresentativeShipPropulsionEnvelope deriveProduction(
            String representativeId,
            String provenanceId,
            String loadCaseId,
            DerivedShipState state) {
        requireNonBlank(representativeId, "representativeId");
        requireNonBlank(provenanceId, "provenanceId");
        requireNonBlank(loadCaseId, "loadCaseId");
        DerivedShipState checked = Objects.requireNonNull(state, "state");
        if (!checked.validation().isValid()) {
            throw new IllegalArgumentException("calibration requires a valid authoritative ship state");
        }

        return derivePhysical(
                representativeId,
                CalibrationAuthority.PRODUCTION_ENGINEERING,
                provenanceId,
                loadCaseId,
                requirePositiveFinite(checked.totalMassKg(), "totalMassKg"),
                requirePositiveFinite(checked.reactionMassKg(), "reactionMassKg"),
                requirePositiveFinite(checked.availableThrustN(), "availableThrustN"),
                requirePositiveFinite(checked.massFlowKgPerS(), "massFlowKgPerS"),
                requirePositiveFinite(checked.accelerationMps2(), "accelerationMps2"),
                requirePositiveFinite(checked.effectiveExhaustVelocityMps(), "effectiveExhaustVelocityMps"),
                requirePositiveFinite(checked.deltaVMps(), "deltaVMps"));
    }

    /**
     * Derives a calibration envelope from one accepted but explicitly provisional reference design.
     *
     * <p>The loader has already closed the reference's mass, acceleration and delta-v against the
     * shared Stage-20 physical equations. This method consumes those accepted outputs, preserves the
     * reference's exact provenance and only derives Stage-20 manoeuvre geometry from them.</p>
     *
     * @param catalog owning versioned reference catalog
     * @param reference accepted reference definition contained by {@code catalog}
     * @return immutable provisional propulsion envelope in SI units
     */
    public static RepresentativeShipPropulsionEnvelope deriveReference(
            Stage20RepresentativePropulsionCatalog catalog,
            ReferenceDefinition reference) {
        Stage20RepresentativePropulsionCatalog checkedCatalog = Objects.requireNonNull(catalog, "catalog");
        ReferenceDefinition checked = Objects.requireNonNull(reference, "reference");
        if (checkedCatalog.status() != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE) {
            throw new IllegalArgumentException("reference calibration requires provisional accepted-reference authority");
        }
        if (!checkedCatalog.references().contains(checked)) {
            throw new IllegalArgumentException("reference is not owned by the supplied Stage-20 catalog");
        }

        double massFlowKgPerS = checked.thrustN() / checked.exhaustVelocityMps();
        return derivePhysical(
                checked.representativeClass(),
                checkedCatalog.status(),
                checked.sourceEvidenceId() + ":" + checked.id(),
                "load." + checked.id() + ".full_reaction_mass",
                checked.departureMassKg(),
                checked.reactionMassKg(),
                checked.thrustN(),
                massFlowKgPerS,
                checked.expectedAccelerationMps2(),
                checked.exhaustVelocityMps(),
                checked.expectedDeltaVMps());
    }

    private static RepresentativeShipPropulsionEnvelope derivePhysical(
            String representativeId,
            CalibrationAuthority authority,
            String provenanceId,
            String loadCaseId,
            double wetMassKg,
            double reactionMassKg,
            double thrustN,
            double massFlowKgPerS,
            double initialAccelerationMps2,
            double exhaustVelocityMps,
            double deltaVMps) {
        double dryMassAfterReactionKg = wetMassKg - reactionMassKg;
        if (!(dryMassAfterReactionKg > 0d) || !Double.isFinite(dryMassAfterReactionKg)) {
            throw new IllegalArgumentException("reaction mass must be smaller than total ship mass");
        }

        double reactionMassFraction = reactionMassKg / wetMassKg;
        double terminalAccelerationMps2 = thrustN / dryMassAfterReactionKg;
        double midpointMassKg = Math.sqrt(wetMassKg * dryMassAfterReactionKg);
        double accelerationBurnDurationS = (wetMassKg - midpointMassKg) / massFlowKgPerS;
        double brakingBurnDurationS = (midpointMassKg - dryMassAfterReactionKg) / massFlowKgPerS;
        double fullBurnDurationS = reactionMassKg / massFlowKgPerS;
        double symmetricPeakSpeedMps = deltaVMps / 2d;

        double accelerationDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (wetMassKg - midpointMassKg
                + midpointMassKg * Math.log(midpointMassKg / wetMassKg));
        double brakingDistanceM = exhaustVelocityMps / massFlowKgPerS
                * (midpointMassKg * Math.log(midpointMassKg / dryMassAfterReactionKg)
                - midpointMassKg + dryMassAfterReactionKg);
        double characteristicRestToRestDistanceM = accelerationDistanceM + brakingDistanceM;

        return new RepresentativeShipPropulsionEnvelope(
                representativeId,
                authority,
                provenanceId,
                loadCaseId,
                wetMassKg,
                dryMassAfterReactionKg,
                reactionMassKg,
                reactionMassFraction,
                thrustN,
                massFlowKgPerS,
                initialAccelerationMps2,
                terminalAccelerationMps2,
                exhaustVelocityMps,
                deltaVMps,
                fullBurnDurationS,
                symmetricPeakSpeedMps,
                accelerationBurnDurationS,
                brakingBurnDurationS,
                accelerationDistanceM,
                brakingDistanceM,
                characteristicRestToRestDistanceM);
    }

    private static void requireNonBlank(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static double requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
        return value;
    }
}
