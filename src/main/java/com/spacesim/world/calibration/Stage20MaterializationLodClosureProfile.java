package com.spacesim.world.calibration;

import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandAuthority;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.DistanceBandClosure;
import com.spacesim.world.calibration.Stage20MaterializationLodCalibrationProfile.RepresentationLevel;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Superseding Stage-20A closure for materialization/LOD activation geometry.
 *
 * <p>The historical {@link Stage20MaterializationLodCalibrationProfile} remains unchanged and keeps
 * its original unresolved numeric bands. This profile closes those gaps from later accepted Stage-20A
 * physical evidence while preserving relevance-first scheduling, unbounded physical space and
 * render/simulation separation.</p>
 *
 * @param version stable closure-profile version
 * @param historicalProfileVersion exact historical calibration profile being superseded
 * @param majorInfrastructureProfileVersion accepted ACTIVE_LOCAL source profile
 * @param stationPhysicalProfileVersion accepted station physical geometry source
 * @param stationDefensiveProfileVersion accepted direct-interaction/exclusion source
 * @param wakeLatencySimulationSeconds production materialization wake latency
 * @param distanceBands explicit physical ACTIVE_LOCAL and TACTICAL promotion bands
 * @param authoritativeStateRetained whether every representation retains causal persistent authority
 * @param distanceCanSuppressDirectRelevance whether distance is allowed to override direct relevance
 * @param renderBoundary whether any band is a renderer boundary
 * @param worldBoundary whether any band is a physical world boundary
 */
public record Stage20MaterializationLodClosureProfile(
        String version,
        String historicalProfileVersion,
        String majorInfrastructureProfileVersion,
        String stationPhysicalProfileVersion,
        String stationDefensiveProfileVersion,
        double wakeLatencySimulationSeconds,
        List<DistanceBandClosure> distanceBands,
        boolean authoritativeStateRetained,
        boolean distanceCanSuppressDirectRelevance,
        boolean renderBoundary,
        boolean worldBoundary) {
    /** Current superseding Stage-20A materialization/LOD closure profile version. */
    public static final String CURRENT_VERSION = "stage20a.materialization-lod-closure.v1";

    /**
     * Creates one immutable deterministic closure profile.
     *
     * @param version stable closure-profile version
     * @param historicalProfileVersion exact historical profile version
     * @param majorInfrastructureProfileVersion accepted infrastructure-extent source
     * @param stationPhysicalProfileVersion accepted station physical-geometry source
     * @param stationDefensiveProfileVersion accepted station defensive-geometry source
     * @param wakeLatencySimulationSeconds production materialization wake latency
     * @param distanceBands explicit physical activation bands
     * @param authoritativeStateRetained whether authoritative state is retained at all levels
     * @param distanceCanSuppressDirectRelevance whether distance can suppress direct relevance
     * @param renderBoundary whether the bands define a render boundary
     * @param worldBoundary whether the bands define a physical world boundary
     */
    public Stage20MaterializationLodClosureProfile {
        requireText(version, "version");
        requireText(historicalProfileVersion, "historicalProfileVersion");
        requireText(majorInfrastructureProfileVersion, "majorInfrastructureProfileVersion");
        requireText(stationPhysicalProfileVersion, "stationPhysicalProfileVersion");
        requireText(stationDefensiveProfileVersion, "stationDefensiveProfileVersion");
        requireNonNegativeFinite(wakeLatencySimulationSeconds, "wakeLatencySimulationSeconds");
        Objects.requireNonNull(distanceBands, "distanceBands");
        ArrayList<DistanceBandClosure> copy = new ArrayList<>(distanceBands);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("distanceBands must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparing(value -> value.level().name()));
        distanceBands = List.copyOf(copy);
        if (!authoritativeStateRetained || distanceCanSuppressDirectRelevance || renderBoundary || worldBoundary) {
            throw new IllegalArgumentException(
                    "Stage-20 LOD closure must retain authority and cannot create relevance, render or world boundaries");
        }
    }

    /**
     * Derives the current closure exclusively from already accepted Stage-20A physical profiles and
     * the production synchronous materialization boundary.
     *
     * @return deterministic current materialization/LOD closure
     */
    public static Stage20MaterializationLodClosureProfile deriveCurrent() {
        Stage20MaterializationLodCalibrationProfile historical =
                Stage20MaterializationLodCalibrationCalculator.calibrate();
        Stage20MajorInfrastructureExtentCalibrationProfile infrastructure =
                Stage20MajorInfrastructureExtentCalibrationProfile.deriveCurrent();
        Stage20StationPhysicalGeometryProfile stationPhysical =
                Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefensive =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();

        double activeLocalEnvelopeM = infrastructure.maximumMajorInfrastructureExtentM();
        double maximumStationOperationalRadiusM = stationPhysical.placementEnvelopes().stream()
                .mapToDouble(StationPlacementEnvelope::operationalRadiusM)
                .max()
                .orElseThrow();
        double maximumDefensiveExclusionM = stationDefensive.stations().stream()
                .mapToDouble(Stage20StationDefensiveSensorGeometryProfile.StationDefensiveSensorGeometry::defensiveExclusionReferenceM)
                .max()
                .orElseThrow();
        double tacticalEnvelopeM = Math.max(maximumStationOperationalRadiusM, maximumDefensiveExclusionM);

        List<DistanceBandClosure> bands = List.of(
                new DistanceBandClosure(
                        RepresentationLevel.ACTIVE_LOCAL,
                        DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT,
                        java.util.OptionalDouble.of(activeLocalEnvelopeM),
                        "Stage20MajorInfrastructureExtentCalibrationProfile:" + infrastructure.version()
                                + ":maximumMajorInfrastructureExtentM;relevance_first=true;not_world_boundary"),
                new DistanceBandClosure(
                        RepresentationLevel.TACTICAL,
                        DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT,
                        java.util.OptionalDouble.of(tacticalEnvelopeM),
                        "max(Stage20StationPhysicalGeometryProfile:" + stationPhysical.version()
                                + ":operationalRadiusM,Stage20StationDefensiveSensorGeometryProfile:"
                                + stationDefensive.version()
                                + ":defensiveExclusionReferenceM);relevance_first=true;not_sensor_detection_wall"));

        return new Stage20MaterializationLodClosureProfile(
                CURRENT_VERSION,
                historical.version(),
                infrastructure.version(),
                stationPhysical.version(),
                stationDefensive.version(),
                Stage20MaterializationService.SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS,
                bands,
                true,
                false,
                false,
                false);
    }

    /**
     * Returns whether this superseding profile closes the Stage-20B materialization/LOD entry gap.
     *
     * @return true when both required numeric bands have explicit physical authority and preserve
     *         relevance-first, non-boundary semantics
     */
    public boolean closesStage20BEntryCoverage() {
        if (!CURRENT_VERSION.equals(version)
                || !Stage20MaterializationLodCalibrationProfile.CURRENT_VERSION.equals(historicalProfileVersion)
                || !Stage20MajorInfrastructureExtentCalibrationProfile.CURRENT_VERSION.equals(majorInfrastructureProfileVersion)
                || !Stage20StationPhysicalGeometryProfile.CURRENT_VERSION.equals(stationPhysicalProfileVersion)
                || !Stage20StationDefensiveSensorGeometryProfile.CURRENT_VERSION.equals(stationDefensiveProfileVersion)
                || wakeLatencySimulationSeconds != Stage20MaterializationService.SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS
                || !authoritativeStateRetained
                || distanceCanSuppressDirectRelevance
                || renderBoundary
                || worldBoundary) {
            return false;
        }
        Set<RepresentationLevel> required = EnumSet.of(RepresentationLevel.ACTIVE_LOCAL, RepresentationLevel.TACTICAL);
        Set<RepresentationLevel> actual = distanceBands.stream()
                .map(DistanceBandClosure::level)
                .collect(Collectors.toSet());
        if (!actual.equals(required) || distanceBands.size() != required.size()) {
            return false;
        }
        if (distanceBands.stream().anyMatch(value ->
                value.authority() != DistanceBandAuthority.EXPLICIT_PHYSICAL_INPUT
                        || value.activationDistanceM().isEmpty()
                        || value.activationDistanceM().orElseThrow() <= 0d)) {
            return false;
        }
        double active = activationDistanceM(RepresentationLevel.ACTIVE_LOCAL);
        double tactical = activationDistanceM(RepresentationLevel.TACTICAL);
        return active >= tactical;
    }

    /**
     * Returns the explicit activation distance for one closed representation level.
     *
     * @param level ACTIVE_LOCAL or TACTICAL
     * @return physical activation distance in meters
     */
    public double activationDistanceM(RepresentationLevel level) {
        Objects.requireNonNull(level, "level");
        return distanceBands.stream()
                .filter(value -> value.level() == level)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No closed Stage-20 LOD band for " + level))
                .activationDistanceM()
                .orElseThrow();
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
