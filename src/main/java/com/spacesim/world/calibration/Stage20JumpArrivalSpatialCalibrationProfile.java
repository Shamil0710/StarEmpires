package com.spacesim.world.calibration;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.OptionalDouble;

/**
 * Versioned Stage-20A.7 evidence for jump-arrival placement and infrastructure stand-off.
 *
 * <p>The profile separates accepted neighbor-edge FTL semantics from local materialization
 * coordinates. Legacy viewport anchors remain compatibility evidence only. Station-specific
 * stand-offs may be closed by explicit Stage-20 physical geometry and defensive exclusion references
 * without promoting either reference into final Stage-22 production content.</p>
 *
 * @param version stable profile version
 * @param runtimeArrivalPolicy current production/runtime materialization policy evidence
 * @param representativeArrivalSamples representative local arrival/braking evidence
 * @param stationStandOffSamples station-specific stand-off closure state
 * @param tacticalResponseEvidence Stage-20A.5 weapon/PD response probes retained as evidence only
 * @param unresolvedConstraints machine-visible unresolved physical closure
 */
public record Stage20JumpArrivalSpatialCalibrationProfile(
        String version,
        RuntimeArrivalPolicy runtimeArrivalPolicy,
        List<RepresentativeArrivalSample> representativeArrivalSamples,
        List<StationStandOffSample> stationStandOffSamples,
        TacticalResponseEvidence tacticalResponseEvidence,
        List<String> unresolvedConstraints) {
    /** Current Stage-20A.7 profile version. */
    public static final String CURRENT_VERSION = "stage20a.jump-arrival-spatial.v2";

    /**
     * Creates one immutable deterministically ordered profile.
     *
     * @param version stable profile version
     * @param runtimeArrivalPolicy current runtime materialization policy evidence
     * @param representativeArrivalSamples representative local arrival/braking evidence
     * @param stationStandOffSamples station-specific stand-off closure state
     * @param tacticalResponseEvidence Stage-20A.5 response probes retained as evidence only
     * @param unresolvedConstraints machine-visible unresolved physical closure
     */
    public Stage20JumpArrivalSpatialCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(runtimeArrivalPolicy, "runtimeArrivalPolicy");
        representativeArrivalSamples = sortedCopy(
                representativeArrivalSamples,
                Comparator.comparing(RepresentativeArrivalSample::representativeId),
                "representativeArrivalSamples");
        stationStandOffSamples = sortedCopy(
                stationStandOffSamples,
                Comparator.comparing(StationStandOffSample::stationArchetypeId),
                "stationStandOffSamples");
        Objects.requireNonNull(tacticalResponseEvidence, "tacticalResponseEvidence");
        unresolvedConstraints = sortedStrings(unresolvedConstraints, "unresolvedConstraints");
    }

    /** Spatial authority for an arrival/stand-off datum. */
    public enum ArrivalSpatialAuthority {
        /** Accepted/runtime physical behavior, but not necessarily final generated-world placement. */
        PRODUCTION_RUNTIME,
        /** Compatibility geometry tied to the old bounded local viewport. */
        LEGACY_BOUNDED_VIEWPORT_COMPATIBILITY,
        /** Accepted Stage-20 calibration probe retained only as scale evidence. */
        PROVISIONAL_CALIBRATION_PROBE,
        /** Explicit Stage-20 design reference derived from accepted physical inputs pending Stage-22 review. */
        PROVISIONAL_STAGE20_DESIGN_REFERENCE,
        /** Required generated-world physical closure is absent. */
        UNRESOLVED
    }

    /**
     * Current runtime jump materialization policy evidence.
     *
     * @param topologySemantics accepted ordinary inter-system transition semantics
     * @param explicitCoordinatesPreserved whether non-zero requested local coordinates remain exact
     * @param legacyZeroPairResolved whether the legacy {@code (0,0)} pair is remapped
     * @param legacyArrivalX current legacy compatibility anchor X
     * @param legacyArrivalY current legacy compatibility anchor Y
     * @param legacyAnchorAuthority authority of that compatibility anchor
     * @param arrivalVelocityMps current post-materialization speed
     * @param source exact runtime provenance
     */
    public record RuntimeArrivalPolicy(
            String topologySemantics,
            boolean explicitCoordinatesPreserved,
            boolean legacyZeroPairResolved,
            double legacyArrivalX,
            double legacyArrivalY,
            ArrivalSpatialAuthority legacyAnchorAuthority,
            double arrivalVelocityMps,
            String source) {
        /**
         * Validates current runtime materialization-policy evidence.
         *
         * @param topologySemantics accepted ordinary transition semantics
         * @param explicitCoordinatesPreserved whether non-zero requested coordinates remain exact
         * @param legacyZeroPairResolved whether the legacy zero pair is remapped
         * @param legacyArrivalX legacy compatibility anchor X
         * @param legacyArrivalY legacy compatibility anchor Y
         * @param legacyAnchorAuthority authority of the compatibility anchor
         * @param arrivalVelocityMps current post-materialization speed
         * @param source exact runtime provenance
         */
        public RuntimeArrivalPolicy {
            requireText(topologySemantics, "topologySemantics");
            requireFinite(legacyArrivalX, "legacyArrivalX");
            requireFinite(legacyArrivalY, "legacyArrivalY");
            Objects.requireNonNull(legacyAnchorAuthority, "legacyAnchorAuthority");
            requireNonNegative(arrivalVelocityMps, "arrivalVelocityMps");
            requireText(source, "source");
        }
    }

    /**
     * Representative physical response at the current runtime arrival speed.
     *
     * @param representativeId Stage-20 representative ship ID
     * @param authority production/provisional propulsion authority expressed as text provenance
     * @param propulsionProvenance exact representative propulsion provenance
     * @param arrivalSpeedMps current runtime post-materialization speed
     * @param accelerationMps2 representative departure acceleration used for sensitivity evidence
     * @param brakingDistanceM ideal constant-acceleration stopping distance at arrival speed
     */
    public record RepresentativeArrivalSample(
            String representativeId,
            String authority,
            String propulsionProvenance,
            double arrivalSpeedMps,
            double accelerationMps2,
            double brakingDistanceM) {
        /**
         * Validates one representative arrival-response sample.
         *
         * @param representativeId Stage-20 representative ship ID
         * @param authority propulsion authority text
         * @param propulsionProvenance exact representative propulsion provenance
         * @param arrivalSpeedMps current runtime post-materialization speed
         * @param accelerationMps2 representative physical acceleration
         * @param brakingDistanceM ideal stopping distance at the sampled speed
         */
        public RepresentativeArrivalSample {
            requireText(representativeId, "representativeId");
            requireText(authority, "authority");
            requireText(propulsionProvenance, "propulsionProvenance");
            requireNonNegative(arrivalSpeedMps, "arrivalSpeedMps");
            requirePositive(accelerationMps2, "accelerationMps2");
            requireNonNegative(brakingDistanceM, "brakingDistanceM");
        }
    }

    /**
     * Station-specific arrival stand-off closure state.
     *
     * @param stationArchetypeId Stage-18 station archetype ID
     * @param authority current stand-off authority
     * @param provenance exact geometry/defense/arrival provenance when closed
     * @param centerStandOffM center-to-arrival stand-off when physically closed
     * @param unresolvedReasons explicit reasons stand-off cannot yet be authored
     */
    public record StationStandOffSample(
            String stationArchetypeId,
            ArrivalSpatialAuthority authority,
            String provenance,
            OptionalDouble centerStandOffM,
            List<String> unresolvedReasons) {
        /**
         * Validates one station stand-off closure entry.
         *
         * @param stationArchetypeId Stage-18 station archetype ID
         * @param authority current stand-off authority
         * @param provenance exact physical/design provenance
         * @param centerStandOffM center-to-arrival stand-off when physically closed
         * @param unresolvedReasons explicit unresolved reasons
         */
        public StationStandOffSample {
            requireText(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(authority, "authority");
            requireText(provenance, "provenance");
            Objects.requireNonNull(centerStandOffM, "centerStandOffM");
            if (centerStandOffM.isPresent()) {
                requirePositive(centerStandOffM.getAsDouble(), "centerStandOffM");
                if (authority == ArrivalSpatialAuthority.UNRESOLVED) {
                    throw new IllegalArgumentException("closed station stand-off cannot have UNRESOLVED authority");
                }
            } else if (authority != ArrivalSpatialAuthority.UNRESOLVED) {
                throw new IllegalArgumentException("non-UNRESOLVED station stand-off must contain a distance");
            }
            unresolvedReasons = sortedStrings(unresolvedReasons, "unresolvedReasons");
            if (centerStandOffM.isPresent() && !unresolvedReasons.isEmpty()) {
                throw new IllegalArgumentException("closed station stand-off must not retain unresolved reasons");
            }
        }
    }

    /**
     * Stage-20A.5 tactical response evidence relevant to later arrival placement.
     *
     * <p>These are maximum observed/probed distances, not canonical station weapon radii.</p>
     *
     * @param maxDirectFireProbeRangeM largest direct-fire calibration probe distance
     * @param maxBeamProbeRangeM largest beam calibration probe distance
     * @param maxGuidedProbeRangeM largest guided calibration probe distance
     * @param maxAssignedDefenseInterceptDistanceM largest actually assigned PD/interceptor contact distance
     * @param authority explicit provisional probe authority
     * @param source exact Stage-20A.5 provenance
     */
    public record TacticalResponseEvidence(
            double maxDirectFireProbeRangeM,
            double maxBeamProbeRangeM,
            double maxGuidedProbeRangeM,
            double maxAssignedDefenseInterceptDistanceM,
            ArrivalSpatialAuthority authority,
            String source) {
        /**
         * Validates retained tactical response evidence.
         *
         * @param maxDirectFireProbeRangeM largest direct-fire calibration probe distance
         * @param maxBeamProbeRangeM largest beam calibration probe distance
         * @param maxGuidedProbeRangeM largest guided calibration probe distance
         * @param maxAssignedDefenseInterceptDistanceM largest assigned defense intercept distance
         * @param authority explicit provisional probe authority
         * @param source exact Stage-20A.5 provenance
         */
        public TacticalResponseEvidence {
            requirePositive(maxDirectFireProbeRangeM, "maxDirectFireProbeRangeM");
            requirePositive(maxBeamProbeRangeM, "maxBeamProbeRangeM");
            requirePositive(maxGuidedProbeRangeM, "maxGuidedProbeRangeM");
            requireNonNegative(maxAssignedDefenseInterceptDistanceM, "maxAssignedDefenseInterceptDistanceM");
            Objects.requireNonNull(authority, "authority");
            requireText(source, "source");
        }
    }

    /**
     * Explicit physical inputs required to derive one infrastructure-centered arrival stand-off.
     *
     * @param infrastructureId stable infrastructure/station ID
     * @param provenance accepted geometry/defense provenance
     * @param operationalRadiusM explicit infrastructure operational radius
     * @param trafficClearanceM explicit additional arrival/traffic clearance beyond the supplied operational radius
     * @param defensiveEnvelopeFromCenterM explicit accepted defensive/exclusion envelope from infrastructure center
     * @param arrivalSpeedMps explicit materialization/approach speed requiring stopping clearance
     * @param brakingAccelerationMps2 physical braking acceleration
     */
    public record StandOffGeometryInput(
            String infrastructureId,
            String provenance,
            double operationalRadiusM,
            double trafficClearanceM,
            double defensiveEnvelopeFromCenterM,
            double arrivalSpeedMps,
            double brakingAccelerationMps2) {
        /**
         * Validates explicit stand-off inputs.
         *
         * @param infrastructureId stable infrastructure/station ID
         * @param provenance accepted geometry/defense provenance
         * @param operationalRadiusM explicit operational radius
         * @param trafficClearanceM explicit additional traffic clearance beyond operational radius
         * @param defensiveEnvelopeFromCenterM explicit center-based defensive/exclusion envelope
         * @param arrivalSpeedMps explicit arrival/approach speed
         * @param brakingAccelerationMps2 physical braking acceleration
         */
        public StandOffGeometryInput {
            requireText(infrastructureId, "infrastructureId");
            requireText(provenance, "provenance");
            requirePositive(operationalRadiusM, "operationalRadiusM");
            requireNonNegative(trafficClearanceM, "trafficClearanceM");
            requireNonNegative(defensiveEnvelopeFromCenterM, "defensiveEnvelopeFromCenterM");
            requireNonNegative(arrivalSpeedMps, "arrivalSpeedMps");
            requirePositive(brakingAccelerationMps2, "brakingAccelerationMps2");
        }
    }

    /**
     * Conservative physical arrival stand-off derived from explicit inputs only.
     *
     * @param infrastructureId stable infrastructure/station ID
     * @param provenance exact accepted input provenance
     * @param brakingDistanceM stopping distance implied by sampled speed/acceleration
     * @param trafficLimitedCenterDistanceM infrastructure radius plus any additional traffic clearance
     * @param brakingLimitedCenterDistanceM infrastructure radius plus stopping distance
     * @param defensiveLimitedCenterDistanceM center-based defensive/exclusion envelope
     * @param requiredCenterStandOffM maximum of all physical constraints
     */
    public record DerivedStandOffEnvelope(
            String infrastructureId,
            String provenance,
            double brakingDistanceM,
            double trafficLimitedCenterDistanceM,
            double brakingLimitedCenterDistanceM,
            double defensiveLimitedCenterDistanceM,
            double requiredCenterStandOffM) {
        /**
         * Validates one derived stand-off envelope.
         *
         * @param infrastructureId stable infrastructure/station ID
         * @param provenance exact accepted input provenance
         * @param brakingDistanceM stopping distance implied by speed/acceleration
         * @param trafficLimitedCenterDistanceM radius plus any additional traffic clearance
         * @param brakingLimitedCenterDistanceM radius plus stopping distance
         * @param defensiveLimitedCenterDistanceM center-based defensive/exclusion envelope
         * @param requiredCenterStandOffM maximum physical constraint
         */
        public DerivedStandOffEnvelope {
            requireText(infrastructureId, "infrastructureId");
            requireText(provenance, "provenance");
            requireNonNegative(brakingDistanceM, "brakingDistanceM");
            requirePositive(trafficLimitedCenterDistanceM, "trafficLimitedCenterDistanceM");
            requirePositive(brakingLimitedCenterDistanceM, "brakingLimitedCenterDistanceM");
            requireNonNegative(defensiveLimitedCenterDistanceM, "defensiveLimitedCenterDistanceM");
            requirePositive(requiredCenterStandOffM, "requiredCenterStandOffM");
        }
    }

    private static <T> List<T> sortedCopy(List<T> values, Comparator<? super T> comparator, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<T> copy = new ArrayList<>(values);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException(field + " must not contain null");
        }
        copy.sort(comparator);
        return List.copyOf(copy);
    }

    private static List<String> sortedStrings(List<String> values, String field) {
        Objects.requireNonNull(values, field);
        ArrayList<String> copy = new ArrayList<>();
        for (String value : values) {
            copy.add(requireText(value, field + " entry"));
        }
        copy.sort(String::compareTo);
        return List.copyOf(copy);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegative(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }

    private static void requireFinite(double value, String field) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException(field + " must be finite");
        }
    }
}
