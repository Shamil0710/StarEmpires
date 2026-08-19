package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20RepresentativeEnduranceProfile.EnduranceSample;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;
import com.spacesim.world.calibration.Stage20RouteCalibrationCalculator.RouteTravelSample;
import com.spacesim.world.calibration.Stage20ScaleCalibrationProfile.RepresentativeShipPropulsionEnvelope;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Machine-readable Stage-20A local-route semantic bands with physically derived travel consequences.
 *
 * <p>Distance intervals are explicit provisional world-generation authoring. Travel time, delta-v,
 * reaction-mass use and braking geometry are always derived from the current representative
 * propulsion/endurance profiles through the shared variable-mass route solver.</p>
 *
 * @param version stable profile version
 * @param distanceAuthority authority of the semantic distance authoring
 * @param stage22ReviewRequired whether balance/content review remains required
 * @param bands authored semantic distance bands
 * @param samples deterministic civilian/military endpoint consequences
 */
public record Stage20LocalRouteSemanticCalibrationProfile(
        String version,
        CalibrationAuthority distanceAuthority,
        boolean stage22ReviewRequired,
        List<BandDefinition> bands,
        List<SemanticRouteSample> samples) {
    /** Current Stage-20A local-route semantic calibration version. */
    public static final String CURRENT_VERSION = "stage20a.local-route-semantic-bands.v1";

    private static final Set<String> CIVILIAN_LOGISTICS = Set.of(
            "EARLY_CIVILIAN_FREIGHTER",
            "BULK_FREIGHTER_LOADED",
            "MINING_SHIP",
            "FLEET_TANKER_LOADED");

    /** High-level representative population used by the Stage-20A civilian/military requirement. */
    public enum RepresentativeGroup {
        /** Civilian freight, extraction and fleet-logistics representatives. */ CIVILIAN_LOGISTICS,
        /** Patrol, escort, cruiser and capital combat representatives. */ MILITARY
    }

    /** Physical thrust policy used to derive route consequences. */
    public enum ThrustPolicy {
        /** Nominal routine travel at the accepted sustained-thrust policy. */ ROUTINE_SUSTAINED,
        /** Urgent response at the current max/reference thrust. */ MAX_THRUST_RESPONSE
    }

    /** Endpoint of an authored semantic distance interval. */
    public enum BandEndpoint {
        /** Lower authored operational distance. */ MIN,
        /** Upper authored operational distance. */ MAX
    }

    /**
     * Creates one immutable deterministic local-route profile.
     *
     * @param version stable profile version
     * @param distanceAuthority authority of semantic distance authoring
     * @param stage22ReviewRequired whether later balance/content review remains required
     * @param bands authored semantic bands
     * @param samples derived endpoint consequences
     */
    public Stage20LocalRouteSemanticCalibrationProfile {
        requireNonBlank(version, "version");
        Objects.requireNonNull(distanceAuthority, "distanceAuthority");
        Objects.requireNonNull(bands, "bands");
        Objects.requireNonNull(samples, "samples");
        ArrayList<BandDefinition> bandCopy = new ArrayList<>(bands);
        ArrayList<SemanticRouteSample> sampleCopy = new ArrayList<>(samples);
        if (bandCopy.isEmpty() || bandCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("bands must be non-empty and contain no null entries");
        }
        if (sampleCopy.isEmpty() || sampleCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("samples must be non-empty and contain no null entries");
        }
        bandCopy.sort(Comparator.comparing(value -> value.id().name()));
        sampleCopy.sort(Comparator.comparing((SemanticRouteSample value) -> value.bandId().name())
                .thenComparing(value -> value.representativeId())
                .thenComparing(value -> value.thrustPolicy().name())
                .thenComparing(value -> value.endpoint().name()));
        bands = List.copyOf(bandCopy);
        samples = List.copyOf(sampleCopy);
    }

    /**
     * Derives the current four-band, nine-role, two-thrust-policy calibration matrix.
     *
     * @return deterministic current Stage-20A local-route semantic calibration profile
     */
    public static Stage20LocalRouteSemanticCalibrationProfile deriveCurrent() {
        Stage20LocalRouteSemanticBandCatalog distanceCatalog =
                Stage20LocalRouteSemanticBandCatalogLoader.loadDefault();
        Stage20ScaleCalibrationProfile scale = Stage20ScaleCalibrationProfile.deriveCurrent();
        Stage20RepresentativeEnduranceProfile endurance = Stage20RepresentativeEnduranceProfile.deriveCurrent();
        Map<String, EnduranceSample> enduranceById = endurance.samples().stream()
                .collect(Collectors.toMap(EnduranceSample::representativeId, Function.identity()));

        List<SemanticRouteSample> samples = new ArrayList<>();
        for (RepresentativeShipPropulsionEnvelope baseline : scale.representativeShips()) {
            EnduranceSample enduranceSample = enduranceById.get(baseline.representativeId());
            if (enduranceSample == null) {
                throw new IllegalStateException("Missing endurance profile for " + baseline.representativeId());
            }
            RepresentativeShipPropulsionEnvelope sustained = Stage20ScaleCalibrationCalculator.deriveAtThrust(
                    baseline,
                    CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                    enduranceSample.sustainedThrustSourceEvidenceId(),
                    "route.sustained." + baseline.representativeId(),
                    enduranceSample.sustainedThrustN());
            for (BandDefinition band : distanceCatalog.bands()) {
                samples.add(sample(band, BandEndpoint.MIN, baseline, baseline, ThrustPolicy.MAX_THRUST_RESPONSE));
                samples.add(sample(band, BandEndpoint.MAX, baseline, baseline, ThrustPolicy.MAX_THRUST_RESPONSE));
                samples.add(sample(band, BandEndpoint.MIN, baseline, sustained, ThrustPolicy.ROUTINE_SUSTAINED));
                samples.add(sample(band, BandEndpoint.MAX, baseline, sustained, ThrustPolicy.ROUTINE_SUSTAINED));
            }
        }
        return new Stage20LocalRouteSemanticCalibrationProfile(
                CURRENT_VERSION,
                distanceCatalog.status(),
                distanceCatalog.stage22ReviewRequired(),
                distanceCatalog.bands(),
                samples);
    }

    private static SemanticRouteSample sample(
            BandDefinition band,
            BandEndpoint endpoint,
            RepresentativeShipPropulsionEnvelope baseline,
            RepresentativeShipPropulsionEnvelope routeEnvelope,
            ThrustPolicy policy) {
        double distanceM = endpoint == BandEndpoint.MIN ? band.minDistanceM() : band.maxDistanceM();
        RouteTravelSample route = Stage20RouteCalibrationCalculator.derive(
                baseline.representativeId(), routeEnvelope, distanceM);
        return new SemanticRouteSample(
                band.id(),
                endpoint,
                groupFor(baseline.representativeId()),
                baseline.representativeId(),
                policy,
                distanceM,
                band.sourceEvidenceId(),
                baseline.authority(),
                baseline.provenanceId(),
                routeEnvelope.thrustN(),
                routeEnvelope.provenanceId(),
                route.regime(),
                route.totalTravelTimeS(),
                route.requiredDeltaVMps(),
                route.reactionMassConsumedKg(),
                route.reactionMassFractionConsumed(),
                route.brakingDistanceM(),
                route.peakSpeedMps());
    }

    private static RepresentativeGroup groupFor(String representativeId) {
        return CIVILIAN_LOGISTICS.contains(representativeId)
                ? RepresentativeGroup.CIVILIAN_LOGISTICS
                : RepresentativeGroup.MILITARY;
    }

    /**
     * One physical route consequence at an authored semantic band endpoint.
     *
     * @param bandId semantic route band
     * @param endpoint band endpoint
     * @param representativeGroup civilian/logistics or military population
     * @param representativeId stable representative ID
     * @param thrustPolicy physical thrust policy
     * @param distanceM authored endpoint distance
     * @param distanceSourceEvidenceId distance authoring provenance
     * @param propulsionAuthority baseline propulsion authority
     * @param propulsionProvenanceId baseline propulsion provenance
     * @param appliedThrustN physical thrust used by the route solver
     * @param thrustPolicyProvenanceId applied-thrust provenance
     * @param regime resulting route regime
     * @param totalTravelTimeS physical rest-to-rest travel time
     * @param requiredDeltaVMps physical route delta-v
     * @param reactionMassConsumedKg physical reaction mass consumed
     * @param reactionMassFractionConsumed fraction of represented reaction mass consumed
     * @param brakingDistanceM physical braking distance
     * @param peakSpeedMps route peak speed
     */
    public record SemanticRouteSample(
            BandId bandId,
            BandEndpoint endpoint,
            RepresentativeGroup representativeGroup,
            String representativeId,
            ThrustPolicy thrustPolicy,
            double distanceM,
            String distanceSourceEvidenceId,
            CalibrationAuthority propulsionAuthority,
            String propulsionProvenanceId,
            double appliedThrustN,
            String thrustPolicyProvenanceId,
            Stage20RouteCalibrationCalculator.TravelRegime regime,
            double totalTravelTimeS,
            double requiredDeltaVMps,
            double reactionMassConsumedKg,
            double reactionMassFractionConsumed,
            double brakingDistanceM,
            double peakSpeedMps) {
        /**
         * Validates one derived semantic route consequence.
         *
         * @param bandId semantic route band
         * @param endpoint band endpoint
         * @param representativeGroup civilian/logistics or military population
         * @param representativeId stable representative ID
         * @param thrustPolicy physical thrust policy
         * @param distanceM authored endpoint distance
         * @param distanceSourceEvidenceId distance authoring provenance
         * @param propulsionAuthority baseline propulsion authority
         * @param propulsionProvenanceId baseline propulsion provenance
         * @param appliedThrustN physical thrust used by the route solver
         * @param thrustPolicyProvenanceId applied-thrust provenance
         * @param regime resulting route regime
         * @param totalTravelTimeS physical rest-to-rest travel time
         * @param requiredDeltaVMps physical route delta-v
         * @param reactionMassConsumedKg physical reaction mass consumed
         * @param reactionMassFractionConsumed fraction of represented reaction mass consumed
         * @param brakingDistanceM physical braking distance
         * @param peakSpeedMps route peak speed
         */
        public SemanticRouteSample {
            Objects.requireNonNull(bandId, "bandId");
            Objects.requireNonNull(endpoint, "endpoint");
            Objects.requireNonNull(representativeGroup, "representativeGroup");
            requireNonBlank(representativeId, "representativeId");
            Objects.requireNonNull(thrustPolicy, "thrustPolicy");
            requirePositiveFinite(distanceM, "distanceM");
            requireNonBlank(distanceSourceEvidenceId, "distanceSourceEvidenceId");
            Objects.requireNonNull(propulsionAuthority, "propulsionAuthority");
            requireNonBlank(propulsionProvenanceId, "propulsionProvenanceId");
            requirePositiveFinite(appliedThrustN, "appliedThrustN");
            requireNonBlank(thrustPolicyProvenanceId, "thrustPolicyProvenanceId");
            Objects.requireNonNull(regime, "regime");
            requirePositiveFinite(totalTravelTimeS, "totalTravelTimeS");
            requirePositiveFinite(requiredDeltaVMps, "requiredDeltaVMps");
            requirePositiveFinite(reactionMassConsumedKg, "reactionMassConsumedKg");
            requirePositiveFinite(reactionMassFractionConsumed, "reactionMassFractionConsumed");
            requirePositiveFinite(brakingDistanceM, "brakingDistanceM");
            requirePositiveFinite(peakSpeedMps, "peakSpeedMps");
            if (reactionMassFractionConsumed > 1d + 1e-12d) {
                throw new IllegalArgumentException("reactionMassFractionConsumed cannot exceed one");
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
