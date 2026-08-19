package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Versioned Stage-20A descriptive major-infrastructure extent bands.
 *
 * <p>The profile derives its distances exclusively from the already accepted local-route semantic
 * bands. It therefore does not invent a system radius, a map edge, a renderer limit or a movement
 * clamp. The extents describe where major generated infrastructure is normally distributed and are
 * suitable as Stage-20B placement/diagnostic inputs only.</p>
 *
 * @param version stable calibration profile version
 * @param authority authority inherited from local-route distance authoring
 * @param stage22ReviewRequired whether playable content/balance review remains required
 * @param localRouteProfileVersion source local-route profile version
 * @param jumpArrivalProfileVersion source station jump-arrival profile version
 * @param bands deterministic descriptive infrastructure extents
 * @param maxClosedStationStandOffM largest accepted station jump-arrival stand-off
 * @param innerToOuterSystemMinDistanceM lower bound of the accepted inner-to-outer-system route band
 */
public record Stage20MajorInfrastructureExtentCalibrationProfile(
        String version,
        CalibrationAuthority authority,
        boolean stage22ReviewRequired,
        String localRouteProfileVersion,
        String jumpArrivalProfileVersion,
        List<ExtentBand> bands,
        double maxClosedStationStandOffM,
        double innerToOuterSystemMinDistanceM) {
    /** Current Stage-20A major-infrastructure extent profile version. */
    public static final String CURRENT_VERSION = "stage20a.major-infrastructure-extents.v1";

    /** Stable descriptive extent meanings. */
    public enum ExtentBandId {
        /** Dense major station/infrastructure cluster scale. */ CORE_STATION_CLUSTER,
        /** Industrial infrastructure plus associated resource-network scale. */ INDUSTRIAL_RESOURCE_NETWORK,
        /** Broad major-hub operational reach inside the local system. */ MAJOR_HUB_REACH
    }

    /**
     * One descriptive major-infrastructure extent interval.
     *
     * @param id stable descriptive extent meaning
     * @param sourceRouteBand exact accepted local-route meaning used as the distance source
     * @param minExtentM lower descriptive extent in meters
     * @param maxExtentM upper descriptive extent in meters
     * @param sourceEvidenceId exact source provenance
     * @param hardBoundary whether this extent may be interpreted as a physical system boundary
     * @param clampAllowed whether actors/content may be silently clamped to this extent
     */
    public record ExtentBand(
            ExtentBandId id,
            BandId sourceRouteBand,
            double minExtentM,
            double maxExtentM,
            String sourceEvidenceId,
            boolean hardBoundary,
            boolean clampAllowed) {
        /**
         * Validates one descriptive extent interval.
         *
         * @param id stable descriptive extent meaning
         * @param sourceRouteBand accepted source route meaning
         * @param minExtentM lower extent in meters
         * @param maxExtentM upper extent in meters
         * @param sourceEvidenceId exact provenance
         * @param hardBoundary must remain false for unbounded local physical space
         * @param clampAllowed must remain false for unbounded local physical space
         */
        public ExtentBand {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(sourceRouteBand, "sourceRouteBand");
            requirePositiveFinite(minExtentM, "minExtentM");
            requirePositiveFinite(maxExtentM, "maxExtentM");
            requireText(sourceEvidenceId, "sourceEvidenceId");
            if (minExtentM > maxExtentM) {
                throw new IllegalArgumentException("minExtentM cannot exceed maxExtentM");
            }
            if (hardBoundary || clampAllowed) {
                throw new IllegalArgumentException("major infrastructure extents are descriptive, never hard boundaries or clamps");
            }
        }
    }

    /**
     * Creates one immutable deterministic profile.
     *
     * @param version stable profile version
     * @param authority inherited distance authority
     * @param stage22ReviewRequired whether later review remains required
     * @param localRouteProfileVersion source route profile version
     * @param jumpArrivalProfileVersion source arrival profile version
     * @param bands descriptive extents
     * @param maxClosedStationStandOffM largest accepted station stand-off
     * @param innerToOuterSystemMinDistanceM accepted lower inner-to-outer-system distance
     */
    public Stage20MajorInfrastructureExtentCalibrationProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(localRouteProfileVersion, "localRouteProfileVersion");
        requireText(jumpArrivalProfileVersion, "jumpArrivalProfileVersion");
        Objects.requireNonNull(bands, "bands");
        requirePositiveFinite(maxClosedStationStandOffM, "maxClosedStationStandOffM");
        requirePositiveFinite(innerToOuterSystemMinDistanceM, "innerToOuterSystemMinDistanceM");
        ArrayList<ExtentBand> copy = new ArrayList<>(bands);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("bands must be non-empty and contain no null entries");
        }
        copy.sort(Comparator.comparing(value -> value.id().name()));
        bands = List.copyOf(copy);
    }

    /**
     * Derives the current profile from accepted local-route and station-arrival geometry.
     *
     * @return deterministic current Stage-20A descriptive extent profile
     */
    public static Stage20MajorInfrastructureExtentCalibrationProfile deriveCurrent() {
        Stage20LocalRouteSemanticCalibrationProfile localRoutes =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20JumpArrivalSpatialCalibrationProfile arrivals =
                Stage20JumpArrivalSpatialCalibrationCalculator.calibrate();
        Map<BandId, BandDefinition> routeById = localRoutes.bands().stream()
                .collect(Collectors.toMap(BandDefinition::id, value -> value));

        BandDefinition stationToStation = requireBand(routeById, BandId.STATION_TO_STATION);
        BandDefinition stationToResource = requireBand(routeById, BandId.STATION_TO_RESOURCE_FIELD);
        BandDefinition jumpToHub = requireBand(routeById, BandId.JUMP_ARRIVAL_TO_MAJOR_HUB);
        BandDefinition innerToOuter = requireBand(routeById, BandId.INNER_TO_OUTER_SYSTEM);

        List<ExtentBand> extents = List.of(
                fromRoute(ExtentBandId.CORE_STATION_CLUSTER, stationToStation),
                fromRoute(ExtentBandId.INDUSTRIAL_RESOURCE_NETWORK, stationToResource),
                fromRoute(ExtentBandId.MAJOR_HUB_REACH, jumpToHub));

        return new Stage20MajorInfrastructureExtentCalibrationProfile(
                CURRENT_VERSION,
                localRoutes.distanceAuthority(),
                localRoutes.stage22ReviewRequired(),
                localRoutes.version(),
                arrivals.version(),
                extents,
                localRoutes.maxClosedStationStandOffM(),
                innerToOuter.minDistanceM());
    }

    /**
     * Returns whether the current profile is sufficient for Stage-20B entry.
     *
     * @return true when all required descriptive extents exactly inherit accepted route geometry
     */
    public boolean closesStage20BEntryCoverage() {
        if (!CURRENT_VERSION.equals(version)
                || authority != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                || !stage22ReviewRequired
                || !Stage20LocalRouteSemanticCalibrationProfile.CURRENT_VERSION.equals(localRouteProfileVersion)
                || !Stage20JumpArrivalSpatialCalibrationProfile.CURRENT_VERSION.equals(jumpArrivalProfileVersion)) {
            return false;
        }
        Set<ExtentBandId> ids = bands.stream().map(ExtentBand::id).collect(Collectors.toSet());
        if (!ids.equals(EnumSet.allOf(ExtentBandId.class)) || bands.size() != ExtentBandId.values().length) {
            return false;
        }
        Map<ExtentBandId, ExtentBand> byId = new EnumMap<>(ExtentBandId.class);
        for (ExtentBand band : bands) {
            if (byId.put(band.id(), band) != null || band.hardBoundary() || band.clampAllowed()) {
                return false;
            }
        }
        ExtentBand core = byId.get(ExtentBandId.CORE_STATION_CLUSTER);
        ExtentBand industrial = byId.get(ExtentBandId.INDUSTRIAL_RESOURCE_NETWORK);
        ExtentBand hub = byId.get(ExtentBandId.MAJOR_HUB_REACH);
        if (core.sourceRouteBand() != BandId.STATION_TO_STATION
                || industrial.sourceRouteBand() != BandId.STATION_TO_RESOURCE_FIELD
                || hub.sourceRouteBand() != BandId.JUMP_ARRIVAL_TO_MAJOR_HUB) {
            return false;
        }
        if (!(core.minExtentM() > maxClosedStationStandOffM)) {
            return false;
        }
        if (industrial.maxExtentM() < core.maxExtentM()
                || hub.maxExtentM() < industrial.maxExtentM()) {
            return false;
        }
        return hub.maxExtentM() <= innerToOuterSystemMinDistanceM;
    }

    /**
     * Returns the largest descriptive major-infrastructure extent.
     *
     * @return largest current major-infrastructure extent in meters
     */
    public double maximumMajorInfrastructureExtentM() {
        return bands.stream().mapToDouble(ExtentBand::maxExtentM).max().orElseThrow();
    }

    private static ExtentBand fromRoute(ExtentBandId id, BandDefinition source) {
        return new ExtentBand(
                id,
                source.id(),
                source.minDistanceM(),
                source.maxDistanceM(),
                source.sourceEvidenceId() + "|derived_by=" + CURRENT_VERSION,
                false,
                false);
    }

    private static BandDefinition requireBand(Map<BandId, BandDefinition> byId, BandId id) {
        BandDefinition band = byId.get(id);
        if (band == null) {
            throw new IllegalStateException("Missing required local route band: " + id);
        }
        return band;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }
}
