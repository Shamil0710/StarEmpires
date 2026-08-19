package com.spacesim.world.calibration;

import com.spacesim.content.ship.ShipEngineeringCatalog;
import com.spacesim.content.ship.ShipEngineeringCatalog.InterfaceKind;
import com.spacesim.content.ship.ShipEngineeringCatalogLoader;
import com.spacesim.ship.DerivedShipCalculator;
import com.spacesim.ship.ShipEngineeringState.ConsumableLoad;
import com.spacesim.ship.ShipEngineeringState.ConsumableState;
import com.spacesim.ship.ShipEngineeringState.DamageState;
import com.spacesim.ship.ShipEngineeringState.DerivedShipState;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator.RouteTravelSample;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Versioned machine-readable Stage-20 scale-calibration output.
 *
 * <p>The profile mixes production engineering results with explicitly provisional accepted
 * references only when their authority and provenance remain machine-visible. Route samples use
 * inherited v0.9/v1.0 sensitivity distances as calibration probes, not as final generated-world
 * map constants.</p>
 *
 * @param version stable calibration-profile version
 * @param representativeShips deterministic representative propulsion envelopes
 * @param routeSamples deterministic per-ship route measurements
 * @param routeBands aggregate route-time, braking and delta-v bands across the representative set
 */
public record Stage20ScaleCalibrationProfile(
        String version,
        List<RepresentativeShipPropulsionEnvelope> representativeShips,
        List<RouteTravelSample> routeSamples,
        List<RouteCalibrationBand> routeBands) {
    /** Current Stage-20A representative-route calibration profile version. */
    public static final String CURRENT_VERSION = "stage20a.representative-routes.v3";

    private static final String ESCORT_REPRESENTATIVE_ID = "ESCORT_DESTROYER";
    private static final String ESCORT_FIT_ID = "fit.escort_destroyer_schema_v1";
    private static final String ESCORT_LOAD_CASE_ID = "load.escort_destroyer.full_reaction_mass_v1";
    private static final double ESCORT_REACTION_MASS_KG = 1_800_000d;
    private static final List<Double> ROUTE_PROBE_DISTANCES_M = List.of(
            10_000_000d,
            100_000_000d,
            1_000_000_000d,
            10_000_000_000d);

    /**
     * Creates an immutable deterministically ordered calibration profile.
     *
     * @param version stable calibration-profile version
     * @param representativeShips representative propulsion envelopes
     * @param routeSamples per-ship route measurements
     * @param routeBands aggregate route bands
     */
    public Stage20ScaleCalibrationProfile {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        representativeShips = immutableSorted(
                representativeShips,
                Comparator.comparing(RepresentativeShipPropulsionEnvelope::representativeId)
                        .thenComparing(RepresentativeShipPropulsionEnvelope::loadCaseId),
                "representativeShips");
        routeSamples = immutableSorted(
                routeSamples,
                Comparator.comparing(RouteTravelSample::representativeId)
                        .thenComparingDouble(RouteTravelSample::distanceM),
                "routeSamples");
        routeBands = immutableSorted(
                routeBands,
                Comparator.comparingDouble(RouteCalibrationBand::distanceM),
                "routeBands");
    }

    /**
     * Derives the current Stage-20A profile from production engineering plus accepted references.
     *
     * <p>The current production escort-destroyer supersedes the matching provisional escort reference.
     * All other required representative roles currently use explicitly provisional calibration evidence
     * with per-reference provenance and mandatory Stage-22 content review.</p>
     *
     * @return deterministic current calibration profile
     */
    public static Stage20ScaleCalibrationProfile deriveCurrent() {
        List<RepresentativeShipPropulsionEnvelope> representatives = new ArrayList<>();
        representatives.add(deriveProductionEscort());

        Stage20RepresentativePropulsionCatalog referenceCatalog =
                Stage20RepresentativePropulsionCatalogLoader.loadDefault();
        for (Stage20RepresentativePropulsionCatalog.ReferenceDefinition reference : referenceCatalog.references()) {
            if (!ESCORT_REPRESENTATIVE_ID.equals(reference.representativeClass())) {
                representatives.add(Stage20ScaleCalibrationCalculator.deriveReference(referenceCatalog, reference));
            }
        }

        List<RouteTravelSample> samples = new ArrayList<>();
        for (RepresentativeShipPropulsionEnvelope representative : representatives) {
            for (double distanceM : ROUTE_PROBE_DISTANCES_M) {
                samples.add(Stage20RouteCalibrationCalculator.derive(
                        representative.representativeId(), representative, distanceM));
            }
        }

        List<RouteCalibrationBand> bands = new ArrayList<>();
        for (double distanceM : ROUTE_PROBE_DISTANCES_M) {
            bands.add(deriveBand(distanceM, samples));
        }
        return new Stage20ScaleCalibrationProfile(CURRENT_VERSION, representatives, samples, bands);
    }

    private static RepresentativeShipPropulsionEnvelope deriveProductionEscort() {
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
        return Stage20ScaleCalibrationCalculator.deriveProduction(
                ESCORT_REPRESENTATIVE_ID,
                ESCORT_FIT_ID,
                ESCORT_LOAD_CASE_ID,
                escort);
    }

    private static RouteCalibrationBand deriveBand(double distanceM, List<RouteTravelSample> samples) {
        double minTime = Double.POSITIVE_INFINITY;
        double maxTime = Double.NEGATIVE_INFINITY;
        double minDeltaV = Double.POSITIVE_INFINITY;
        double maxDeltaV = Double.NEGATIVE_INFINITY;
        double minBrakingDistance = Double.POSITIVE_INFINITY;
        double maxBrakingDistance = Double.NEGATIVE_INFINITY;
        double minReactionFraction = Double.POSITIVE_INFINITY;
        double maxReactionFraction = Double.NEGATIVE_INFINITY;
        int matched = 0;
        for (RouteTravelSample sample : samples) {
            if (Double.compare(sample.distanceM(), distanceM) != 0) {
                continue;
            }
            matched++;
            minTime = Math.min(minTime, sample.totalTravelTimeS());
            maxTime = Math.max(maxTime, sample.totalTravelTimeS());
            minDeltaV = Math.min(minDeltaV, sample.requiredDeltaVMps());
            maxDeltaV = Math.max(maxDeltaV, sample.requiredDeltaVMps());
            minBrakingDistance = Math.min(minBrakingDistance, sample.brakingDistanceM());
            maxBrakingDistance = Math.max(maxBrakingDistance, sample.brakingDistanceM());
            minReactionFraction = Math.min(minReactionFraction, sample.reactionMassFractionConsumed());
            maxReactionFraction = Math.max(maxReactionFraction, sample.reactionMassFractionConsumed());
        }
        if (matched == 0) {
            throw new IllegalStateException("No route samples for calibration distance " + distanceM);
        }
        return new RouteCalibrationBand(
                distanceM,
                minTime,
                maxTime,
                minDeltaV,
                maxDeltaV,
                minBrakingDistance,
                maxBrakingDistance,
                minReactionFraction,
                maxReactionFraction);
    }

    private static <T> List<T> immutableSorted(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    /**
     * One representative local-propulsion measurement with explicit authority and provenance.
     *
     * @param representativeId stable Stage-20 representative class/role ID
     * @param authority production or provisional accepted-reference authority
     * @param provenanceId exact fit/content/baseline provenance for the physical capability values
     * @param loadCaseId versioned calibration load case
     * @param wetMassKg departure mass including reaction mass
     * @param dryMassAfterReactionKg mass after exhausting the represented reaction mass
     * @param reactionMassKg represented reaction-mass load
     * @param reactionMassFraction reaction mass divided by wet mass
     * @param thrustN available/reference thrust
     * @param massFlowKgPerS propulsion mass flow
     * @param initialAccelerationMps2 departure acceleration
     * @param terminalAccelerationMps2 acceleration at the end of the represented reaction-mass burn
     * @param effectiveExhaustVelocityMps effective exhaust velocity
     * @param deltaVMps idealized available/accepted delta-v
     * @param fullBurnDurationS duration required to consume the represented reaction mass
     * @param symmetricPeakSpeedMps peak speed of the full-burn equal-delta-v rest-to-rest manoeuvre
     * @param accelerationBurnDurationS duration of the acceleration leg
     * @param brakingBurnDurationS duration of the braking leg
     * @param accelerationDistanceM distance covered by the acceleration leg
     * @param brakingDistanceM distance covered by the braking leg
     * @param characteristicRestToRestDistanceM total distance covered by both full-burn legs without coast
     */
    public record RepresentativeShipPropulsionEnvelope(
            String representativeId,
            CalibrationAuthority authority,
            String provenanceId,
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
        /**
         * Creates one validated immutable representative measurement.
         *
         * @param representativeId stable representative class/role ID
         * @param authority production or provisional authority
         * @param provenanceId exact physical-capability provenance
         * @param loadCaseId versioned calibration load case
         * @param wetMassKg departure mass including reaction mass
         * @param dryMassAfterReactionKg mass after exhausting represented reaction mass
         * @param reactionMassKg represented reaction-mass load
         * @param reactionMassFraction reaction mass divided by wet mass
         * @param thrustN available/reference thrust
         * @param massFlowKgPerS propulsion mass flow
         * @param initialAccelerationMps2 departure acceleration
         * @param terminalAccelerationMps2 terminal acceleration
         * @param effectiveExhaustVelocityMps effective exhaust velocity
         * @param deltaVMps idealized available/accepted delta-v
         * @param fullBurnDurationS full represented burn duration
         * @param symmetricPeakSpeedMps symmetric full-burn peak speed
         * @param accelerationBurnDurationS acceleration burn duration
         * @param brakingBurnDurationS braking burn duration
         * @param accelerationDistanceM acceleration-leg distance
         * @param brakingDistanceM braking-leg distance
         * @param characteristicRestToRestDistanceM full-burn no-coast rest-to-rest distance
         */
        public RepresentativeShipPropulsionEnvelope {
            requireNonBlank(representativeId, "representativeId");
            Objects.requireNonNull(authority, "authority");
            requireNonBlank(provenanceId, "provenanceId");
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
    }

    /**
     * Aggregate route band across all current representative propulsion profiles at one probe distance.
     *
     * @param distanceM route probe distance
     * @param minTravelTimeS fastest representative travel time
     * @param maxTravelTimeS slowest representative travel time
     * @param minRequiredDeltaVMps lowest route delta-v requirement
     * @param maxRequiredDeltaVMps highest route delta-v requirement
     * @param minBrakingDistanceM shortest braking leg
     * @param maxBrakingDistanceM longest braking leg
     * @param minReactionMassFractionConsumed lowest fraction of represented reaction mass consumed
     * @param maxReactionMassFractionConsumed highest fraction of represented reaction mass consumed
     */
    public record RouteCalibrationBand(
            double distanceM,
            double minTravelTimeS,
            double maxTravelTimeS,
            double minRequiredDeltaVMps,
            double maxRequiredDeltaVMps,
            double minBrakingDistanceM,
            double maxBrakingDistanceM,
            double minReactionMassFractionConsumed,
            double maxReactionMassFractionConsumed) {
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
