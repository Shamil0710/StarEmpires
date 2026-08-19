package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20RepresentativeEnduranceReferenceCatalog.ReferenceDefinition;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Machine-readable Stage-20 representative sustained-thrust and mission-endurance calibration.
 *
 * <p>Maximum thrust, mass, reaction mass and effective exhaust velocity come from the current
 * accepted propulsion profile. This profile adds provisional sustained-thrust and operational
 * mission-stores policy only, then derives their physical consequences with the same equations.</p>
 *
 * @param version stable calibration-profile version
 * @param authority authority of the added endurance policy
 * @param stage22ReviewRequired whether content review remains required
 * @param samples deterministic nine-role derived samples
 */
public record Stage20RepresentativeEnduranceProfile(
        String version,
        CalibrationAuthority authority,
        boolean stage22ReviewRequired,
        List<EnduranceSample> samples) {
    /** Current Stage-20A endurance calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.representative-endurance.v1";

    /**
     * Creates one immutable deterministic profile.
     *
     * @param version stable calibration-profile version
     * @param authority authority of the added endurance policy
     * @param stage22ReviewRequired whether content review remains required
     * @param samples deterministic derived samples
     */
    public Stage20RepresentativeEnduranceProfile {
        requireNonBlank(version, "version");
        Objects.requireNonNull(authority, "authority");
        Objects.requireNonNull(samples, "samples");
        ArrayList<EnduranceSample> copy = new ArrayList<>(samples);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparing(EnduranceSample::representativeId));
        samples = List.copyOf(copy);
    }

    /**
     * Derives the current nine-role endurance matrix.
     *
     * @return current deterministic Stage-20A endurance profile
     */
    public static Stage20RepresentativeEnduranceProfile deriveCurrent() {
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20RepresentativeEnduranceReferenceCatalog policy =
                Stage20RepresentativeEnduranceReferenceCatalogLoader.loadDefault();
        Map<String, ReferenceDefinition> byRole = policy.references().stream()
                .collect(Collectors.toMap(ReferenceDefinition::representativeClass, Function.identity()));

        List<EnduranceSample> samples = new ArrayList<>();
        for (RepresentativeShipPropulsionEnvelope representative : scale.representativeShips()) {
            ReferenceDefinition reference = byRole.get(representative.representativeId());
            if (reference == null) {
                throw new IllegalStateException(
                        "Missing endurance reference for " + representative.representativeId());
            }
            samples.add(derive(representative, reference));
        }
        if (samples.size() != policy.references().size()) {
            throw new IllegalStateException(
                    "Endurance policy contains roles outside the current Stage-20 representative set");
        }
        return new Stage20RepresentativeEnduranceProfile(
                CURRENT_VERSION,
                policy.status(),
                policy.stage22ReviewRequired(),
                samples);
    }

    private static EnduranceSample derive(
            RepresentativeShipPropulsionEnvelope propulsion,
            ReferenceDefinition reference) {
        if (reference.sustainedThrustN() > propulsion.thrustN()) {
            throw new IllegalArgumentException(
                    "sustained thrust exceeds current max thrust for " + propulsion.representativeId());
        }
        double sustainedRatio = reference.sustainedThrustN() / propulsion.thrustN();
        double sustainedAcceleration = reference.sustainedThrustN() / propulsion.wetMassKg();
        double maxMassFlow = propulsion.thrustN() / propulsion.effectiveExhaustVelocityMps();
        double sustainedMassFlow = reference.sustainedThrustN() / propulsion.effectiveExhaustVelocityMps();
        double fullBurnAtMax = propulsion.reactionMassKg() / maxMassFlow;
        double fullBurnAtSustained = propulsion.reactionMassKg() / sustainedMassFlow;
        return new EnduranceSample(
                propulsion.representativeId(),
                propulsion.authority(),
                propulsion.provenanceId(),
                propulsion.wetMassKg(),
                propulsion.reactionMassKg(),
                propulsion.effectiveExhaustVelocityMps(),
                propulsion.thrustN(),
                reference.sustainedThrustN(),
                reference.sustainedThrustSourceEvidenceId(),
                sustainedRatio,
                propulsion.initialAccelerationMps2(),
                sustainedAcceleration,
                maxMassFlow,
                sustainedMassFlow,
                fullBurnAtMax,
                fullBurnAtSustained,
                reference.missionStoresEnduranceS(),
                reference.missionStoresSourceEvidenceId());
    }

    /**
     * One representative endurance/thrust consequence row.
     *
     * @param representativeId stable representative role ID
     * @param propulsionAuthority authority of current mass/max-thrust/exhaust inputs
     * @param propulsionProvenanceId exact current propulsion provenance
     * @param wetMassKg current representative wet mass
     * @param reactionMassKg represented reaction-mass load
     * @param effectiveExhaustVelocityMps current representative effective exhaust velocity
     * @param maxThrustN current representative max/reference thrust
     * @param sustainedThrustN provisional sustained-thrust seed
     * @param sustainedThrustSourceEvidenceId exact sustained-thrust provenance
     * @param sustainedToMaxThrustRatio sustained thrust divided by max thrust
     * @param maxAccelerationMps2 current max-thrust departure acceleration
     * @param sustainedAccelerationMps2 derived sustained departure acceleration
     * @param maxMassFlowKgPerS derived max-thrust mass flow
     * @param sustainedMassFlowKgPerS derived sustained-thrust mass flow
     * @param fullReactionMassBurnAtMaxS duration to consume represented reaction mass at max thrust
     * @param fullReactionMassBurnAtSustainedS duration at sustained thrust
     * @param missionStoresEnduranceS nominal operational stores-endurance policy
     * @param missionStoresSourceEvidenceId exact operational-policy provenance
     */
    public record EnduranceSample(
            String representativeId,
            CalibrationAuthority propulsionAuthority,
            String propulsionProvenanceId,
            double wetMassKg,
            double reactionMassKg,
            double effectiveExhaustVelocityMps,
            double maxThrustN,
            double sustainedThrustN,
            String sustainedThrustSourceEvidenceId,
            double sustainedToMaxThrustRatio,
            double maxAccelerationMps2,
            double sustainedAccelerationMps2,
            double maxMassFlowKgPerS,
            double sustainedMassFlowKgPerS,
            double fullReactionMassBurnAtMaxS,
            double fullReactionMassBurnAtSustainedS,
            double missionStoresEnduranceS,
            String missionStoresSourceEvidenceId) {
        /**
         * Validates one derived endurance sample.
         *
         * @param representativeId stable representative role ID
         * @param propulsionAuthority current propulsion authority
         * @param propulsionProvenanceId current propulsion provenance
         * @param wetMassKg current representative wet mass
         * @param reactionMassKg represented reaction-mass load
         * @param effectiveExhaustVelocityMps effective exhaust velocity
         * @param maxThrustN current max/reference thrust
         * @param sustainedThrustN provisional sustained thrust
         * @param sustainedThrustSourceEvidenceId sustained-thrust provenance
         * @param sustainedToMaxThrustRatio sustained-to-max thrust ratio
         * @param maxAccelerationMps2 max-thrust acceleration
         * @param sustainedAccelerationMps2 sustained-thrust acceleration
         * @param maxMassFlowKgPerS max-thrust mass flow
         * @param sustainedMassFlowKgPerS sustained-thrust mass flow
         * @param fullReactionMassBurnAtMaxS max-thrust full-load burn duration
         * @param fullReactionMassBurnAtSustainedS sustained full-load burn duration
         * @param missionStoresEnduranceS nominal operational stores endurance
         * @param missionStoresSourceEvidenceId mission-policy provenance
         */
        public EnduranceSample {
            requireNonBlank(representativeId, "representativeId");
            Objects.requireNonNull(propulsionAuthority, "propulsionAuthority");
            requireNonBlank(propulsionProvenanceId, "propulsionProvenanceId");
            requirePositiveFinite(wetMassKg, "wetMassKg");
            requirePositiveFinite(reactionMassKg, "reactionMassKg");
            requirePositiveFinite(effectiveExhaustVelocityMps, "effectiveExhaustVelocityMps");
            requirePositiveFinite(maxThrustN, "maxThrustN");
            requirePositiveFinite(sustainedThrustN, "sustainedThrustN");
            requireNonBlank(sustainedThrustSourceEvidenceId, "sustainedThrustSourceEvidenceId");
            requirePositiveFinite(sustainedToMaxThrustRatio, "sustainedToMaxThrustRatio");
            requirePositiveFinite(maxAccelerationMps2, "maxAccelerationMps2");
            requirePositiveFinite(sustainedAccelerationMps2, "sustainedAccelerationMps2");
            requirePositiveFinite(maxMassFlowKgPerS, "maxMassFlowKgPerS");
            requirePositiveFinite(sustainedMassFlowKgPerS, "sustainedMassFlowKgPerS");
            requirePositiveFinite(fullReactionMassBurnAtMaxS, "fullReactionMassBurnAtMaxS");
            requirePositiveFinite(fullReactionMassBurnAtSustainedS, "fullReactionMassBurnAtSustainedS");
            requirePositiveFinite(missionStoresEnduranceS, "missionStoresEnduranceS");
            requireNonBlank(missionStoresSourceEvidenceId, "missionStoresSourceEvidenceId");
            if (sustainedThrustN > maxThrustN || sustainedToMaxThrustRatio > 1d) {
                throw new IllegalArgumentException("sustained thrust cannot exceed max thrust");
            }
            if (sustainedAccelerationMps2 > maxAccelerationMps2) {
                throw new IllegalArgumentException("sustained acceleration cannot exceed max acceleration");
            }
            if (sustainedMassFlowKgPerS > maxMassFlowKgPerS) {
                throw new IllegalArgumentException("sustained mass flow cannot exceed max mass flow");
            }
            if (fullReactionMassBurnAtSustainedS < fullReactionMassBurnAtMaxS) {
                throw new IllegalArgumentException("sustained burn duration cannot be shorter than max-thrust burn");
            }
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
