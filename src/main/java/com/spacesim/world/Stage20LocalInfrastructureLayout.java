package com.spacesim.world;

import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Immutable Stage-20C local-infrastructure placement result in authoritative SI coordinates.
 *
 * <p>The layout contains physical anchors and calibrated logistics connections. Route bands are
 * inherited from Stage-20A calibration; this type does not introduce a second strategic-distance
 * coordinate system. Resource fields and jump arrivals are deliberately represented as anchors only
 * until their own physical extents are authored. Station rows carry the already accepted physical
 * operational and defensive placement references.</p>
 *
 * @param version stable Stage-20C layout version
 * @param systemId owning star system
 * @param rootSeed root deterministic generation seed
 * @param majorHubId stable ID of the anchor major hub station
 * @param placements deterministic physical infrastructure placements
 * @param connections calibrated semantic logistics connections
 * @param systemGeometryVersion consumed Stage-20B system-geometry version
 * @param routeCalibrationVersion consumed Stage-20A local-route calibration version
 * @param stationGeometryVersion consumed Stage-20A station physical-geometry version
 * @param stationDefenseVersion consumed Stage-20A station defensive-geometry version
 */
public record Stage20LocalInfrastructureLayout(
        String version,
        StarSystemId systemId,
        long rootSeed,
        String majorHubId,
        List<InfrastructurePlacement> placements,
        List<CalibratedConnection> connections,
        String systemGeometryVersion,
        String routeCalibrationVersion,
        String stationGeometryVersion,
        String stationDefenseVersion) {
    /** Current Stage-20C local-infrastructure layout version. */
    public static final String CURRENT_VERSION = "stage20c.local-infrastructure-spacing.v1";

    /** Physical placement role without inventing resource-field or jump-zone footprint geometry. */
    public enum PlacementKind {
        /** Primary local economic/logistics hub used as the relative-placement anchor. */ MAJOR_HUB_STATION,
        /** Independent major station that should not default to point-blank mutual combat geometry. */ INDEPENDENT_STATION,
        /** Point anchor for a resource field whose full physical extent is not yet authored. */ RESOURCE_FIELD_ANCHOR,
        /** Point anchor for a jump-arrival region whose station-relative stand-off is calibrated separately. */ JUMP_ARRIVAL_ANCHOR
    }

    /**
     * One authoritative physical placement.
     *
     * @param id stable generated/content identity
     * @param kind infrastructure role
     * @param stationArchetypeId station archetype for station rows; empty for point anchors
     * @param position authoritative local SI position
     * @param operationalRadiusM station operational placement radius; zero for point anchors
     * @param defensiveExclusionReferenceM provisional station defensive exclusion reference; zero for point anchors
     */
    public record InfrastructurePlacement(
            String id,
            PlacementKind kind,
            Optional<String> stationArchetypeId,
            LocalPhysicalPosition position,
            double operationalRadiusM,
            double defensiveExclusionReferenceM) {
        /**
         * Validates one placement without promoting point anchors into invented physical footprints.
         *
         * @param id stable generated/content identity
         * @param kind infrastructure role
         * @param stationArchetypeId station archetype for station rows
         * @param position authoritative local SI position
         * @param operationalRadiusM station operational placement radius
         * @param defensiveExclusionReferenceM provisional station defensive exclusion reference
         */
        public InfrastructurePlacement {
            requireText(id, "id");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(stationArchetypeId, "stationArchetypeId");
            Objects.requireNonNull(position, "position");
            requireNonNegativeFinite(operationalRadiusM, "operationalRadiusM");
            requireNonNegativeFinite(defensiveExclusionReferenceM, "defensiveExclusionReferenceM");
            boolean station = kind == PlacementKind.MAJOR_HUB_STATION
                    || kind == PlacementKind.INDEPENDENT_STATION;
            if (station) {
                if (stationArchetypeId.isEmpty()
                        || stationArchetypeId.orElseThrow().isBlank()
                        || operationalRadiusM <= 0d
                        || defensiveExclusionReferenceM <= 0d) {
                    throw new IllegalArgumentException(
                            "station placements require archetype, operational radius and defensive reference");
                }
            } else if (stationArchetypeId.isPresent()
                    || operationalRadiusM != 0d
                    || defensiveExclusionReferenceM != 0d) {
                throw new IllegalArgumentException(
                        "resource/jump point anchors cannot invent station geometry");
            }
        }

        /**
         * Returns whether this placement carries station physical geometry.
         *
         * @return true for hub/independent station placements
         */
        public boolean isStation() {
            return kind == PlacementKind.MAJOR_HUB_STATION
                    || kind == PlacementKind.INDEPENDENT_STATION;
        }
    }

    /**
     * Derived physical consequence envelope behind one semantic route band.
     *
     * <p>Travel and response values are the accepted representative-population endpoint envelope.
     * Round-trip delta-v and transit-only cargo-cycle time are exact two-leg derivatives of the
     * one-way Stage-20A route samples. Cargo handling, docking service and market dwell time are not
     * silently invented here.</p>
     *
     * @param civilianRoutineTravelTimeMinS fastest accepted civilian/logistics routine endpoint consequence
     * @param civilianRoutineTravelTimeMaxS slowest accepted civilian/logistics routine endpoint consequence
     * @param militaryResponseTimeMinS fastest accepted military max-thrust endpoint consequence
     * @param militaryResponseTimeMaxS slowest accepted military max-thrust endpoint consequence
     * @param civilianRoundTripDeltaVMinMps minimum two-leg civilian/logistics delta-v consequence
     * @param civilianRoundTripDeltaVMaxMps maximum two-leg civilian/logistics delta-v consequence
     * @param civilianTransitOnlyCargoCycleMinS minimum two-leg transit-only cargo cycle
     * @param civilianTransitOnlyCargoCycleMaxS maximum two-leg transit-only cargo cycle
     * @param sourceProfileVersion exact Stage-20A calibration version
     */
    public record LogisticsConsequenceEnvelope(
            double civilianRoutineTravelTimeMinS,
            double civilianRoutineTravelTimeMaxS,
            double militaryResponseTimeMinS,
            double militaryResponseTimeMaxS,
            double civilianRoundTripDeltaVMinMps,
            double civilianRoundTripDeltaVMaxMps,
            double civilianTransitOnlyCargoCycleMinS,
            double civilianTransitOnlyCargoCycleMaxS,
            String sourceProfileVersion) {
        /**
         * Validates monotonic positive physical consequence ranges.
         *
         * @param civilianRoutineTravelTimeMinS lower civilian routine travel time
         * @param civilianRoutineTravelTimeMaxS upper civilian routine travel time
         * @param militaryResponseTimeMinS lower military response time
         * @param militaryResponseTimeMaxS upper military response time
         * @param civilianRoundTripDeltaVMinMps lower two-leg civilian delta-v
         * @param civilianRoundTripDeltaVMaxMps upper two-leg civilian delta-v
         * @param civilianTransitOnlyCargoCycleMinS lower two-leg transit time
         * @param civilianTransitOnlyCargoCycleMaxS upper two-leg transit time
         * @param sourceProfileVersion exact calibration source version
         */
        public LogisticsConsequenceEnvelope {
            requirePositiveRange(
                    civilianRoutineTravelTimeMinS,
                    civilianRoutineTravelTimeMaxS,
                    "civilianRoutineTravelTime");
            requirePositiveRange(militaryResponseTimeMinS, militaryResponseTimeMaxS, "militaryResponseTime");
            requirePositiveRange(
                    civilianRoundTripDeltaVMinMps,
                    civilianRoundTripDeltaVMaxMps,
                    "civilianRoundTripDeltaV");
            requirePositiveRange(
                    civilianTransitOnlyCargoCycleMinS,
                    civilianTransitOnlyCargoCycleMaxS,
                    "civilianTransitOnlyCargoCycle");
            requireText(sourceProfileVersion, "sourceProfileVersion");
        }
    }

    /**
     * One physical connection whose meaning resolves to an accepted Stage-20A semantic band.
     *
     * @param fromId source placement ID
     * @param toId destination placement ID
     * @param bandId accepted local-route semantic meaning
     * @param distanceM actual physical center/anchor separation in meters
     * @param minDistanceM accepted lower semantic distance
     * @param maxDistanceM accepted upper semantic distance
     * @param sourceEvidenceId accepted distance-band provenance
     * @param logisticsConsequences physically derived representative consequence envelope
     */
    public record CalibratedConnection(
            String fromId,
            String toId,
            BandId bandId,
            double distanceM,
            double minDistanceM,
            double maxDistanceM,
            String sourceEvidenceId,
            LogisticsConsequenceEnvelope logisticsConsequences) {
        /**
         * Validates one calibrated physical connection.
         *
         * @param fromId source placement ID
         * @param toId destination placement ID
         * @param bandId semantic band
         * @param distanceM actual separation
         * @param minDistanceM accepted minimum separation
         * @param maxDistanceM accepted maximum separation
         * @param sourceEvidenceId accepted authoring provenance
         * @param logisticsConsequences derived physical consequences
         */
        public CalibratedConnection {
            requireText(fromId, "fromId");
            requireText(toId, "toId");
            if (fromId.equals(toId)) {
                throw new IllegalArgumentException("connection endpoints must be distinct");
            }
            Objects.requireNonNull(bandId, "bandId");
            requirePositiveFinite(distanceM, "distanceM");
            requirePositiveRange(minDistanceM, maxDistanceM, "distance");
            requireText(sourceEvidenceId, "sourceEvidenceId");
            Objects.requireNonNull(logisticsConsequences, "logisticsConsequences");
            double tolerance = Math.max(1e-6d, maxDistanceM * 1e-12d);
            if (distanceM + tolerance < minDistanceM || distanceM - tolerance > maxDistanceM) {
                throw new IllegalArgumentException("physical connection lies outside its accepted semantic band");
            }
        }
    }

    /**
     * Validates and deterministically freezes one generated local-infrastructure layout.
     *
     * @param version stable Stage-20C layout version
     * @param systemId owning star system
     * @param rootSeed root generation seed
     * @param majorHubId stable major hub ID
     * @param placements physical placements
     * @param connections calibrated connections
     * @param systemGeometryVersion source Stage-20B geometry version
     * @param routeCalibrationVersion source route calibration version
     * @param stationGeometryVersion source station geometry version
     * @param stationDefenseVersion source station defense version
     */
    public Stage20LocalInfrastructureLayout {
        requireText(version, "version");
        Objects.requireNonNull(systemId, "systemId");
        requireText(majorHubId, "majorHubId");
        requireText(systemGeometryVersion, "systemGeometryVersion");
        requireText(routeCalibrationVersion, "routeCalibrationVersion");
        requireText(stationGeometryVersion, "stationGeometryVersion");
        requireText(stationDefenseVersion, "stationDefenseVersion");
        Objects.requireNonNull(placements, "placements");
        Objects.requireNonNull(connections, "connections");

        ArrayList<InfrastructurePlacement> placementCopy = new ArrayList<>(placements);
        ArrayList<CalibratedConnection> connectionCopy = new ArrayList<>(connections);
        if (placementCopy.isEmpty() || placementCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("placements must be non-empty and contain no nulls");
        }
        if (connectionCopy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("connections cannot contain nulls");
        }
        placementCopy.sort(Comparator.comparing(InfrastructurePlacement::id));
        connectionCopy.sort(Comparator.comparing(CalibratedConnection::fromId)
                .thenComparing(CalibratedConnection::toId)
                .thenComparing(value -> value.bandId().name()));
        placements = List.copyOf(placementCopy);
        connections = List.copyOf(connectionCopy);

        Map<String, InfrastructurePlacement> byId = new HashMap<>();
        for (InfrastructurePlacement placement : placements) {
            if (byId.put(placement.id(), placement) != null) {
                throw new IllegalArgumentException("placement IDs must be unique");
            }
        }
        InfrastructurePlacement hub = byId.get(majorHubId);
        if (hub == null || hub.kind() != PlacementKind.MAJOR_HUB_STATION) {
            throw new IllegalArgumentException("majorHubId must identify exactly one major hub station");
        }

        Set<String> connectionKeys = new HashSet<>();
        for (CalibratedConnection connection : connections) {
            InfrastructurePlacement from = byId.get(connection.fromId());
            InfrastructurePlacement to = byId.get(connection.toId());
            if (from == null || to == null) {
                throw new IllegalArgumentException("connection endpoints must reference generated placements");
            }
            String key = connection.fromId() + "\u0000" + connection.toId() + "\u0000" + connection.bandId();
            if (!connectionKeys.add(key)) {
                throw new IllegalArgumentException("duplicate calibrated connection");
            }
            double actualDistanceM = from.position().distanceTo(to.position());
            double tolerance = Math.max(1e-5d, actualDistanceM * 1e-12d);
            if (Math.abs(actualDistanceM - connection.distanceM()) > tolerance) {
                throw new IllegalArgumentException("connection distance must equal authoritative physical separation");
            }
        }
    }

    /**
     * Finds a generated placement by stable ID.
     *
     * @param id stable placement identity
     * @return matching placement
     */
    public InfrastructurePlacement placement(String id) {
        requireText(id, "id");
        return placements.stream()
                .filter(value -> value.id().equals(id))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown infrastructure placement: " + id));
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }

    private static void requirePositiveRange(double min, double max, String field) {
        requirePositiveFinite(min, field + "Min");
        requirePositiveFinite(max, field + "Max");
        if (min > max) {
            throw new IllegalArgumentException(field + " minimum cannot exceed maximum");
        }
    }

    private static void requirePositiveFinite(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static void requireNonNegativeFinite(double value, String field) {
        if (!Double.isFinite(value) || value < 0d) {
            throw new IllegalArgumentException(field + " must be non-negative and finite");
        }
    }
}
