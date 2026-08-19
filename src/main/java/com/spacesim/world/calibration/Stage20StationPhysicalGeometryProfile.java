package com.spacesim.world.calibration;

import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementGeometryInput;
import com.spacesim.world.calibration.Stage20RepresentativePropulsionCatalog.CalibrationAuthority;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Versioned Stage-20A design authority for station physical footprints and operational clearances.
 *
 * <p>Stage-18 station content intentionally contains industrial capacity/throughput but no physical
 * station dimensions. This profile therefore authors explicit provisional geometry rather than
 * deriving size from storage mass, facility count, transfer rate, berth capacity, UI scale or ship
 * mass. The values are world-generation design inputs and remain subject to Stage-22 content review.
 * Conservative placement envelopes are delegated to the existing Stage-20A.6 physical formula.</p>
 *
 * @param version stable geometry-profile version
 * @param authority explicit design-input authority
 * @param sourceDocument exact Stage-20 design source
 * @param stage22ReviewRequired whether provisional geometry must be reviewed during content promotion
 * @param stationDesigns explicit physical geometry for every required Stage-18 station archetype
 * @param placementEnvelopes conservative envelopes derived from the authored geometry
 */
public record Stage20StationPhysicalGeometryProfile(
        String version,
        CalibrationAuthority authority,
        String sourceDocument,
        boolean stage22ReviewRequired,
        List<StationGeometryDesign> stationDesigns,
        List<StationPlacementEnvelope> placementEnvelopes) {
    /** Current Stage-20A station-geometry profile version. */
    public static final String CURRENT_VERSION = "stage20a.station-physical-geometry.v1";
    /** Exact design-authority document for the authored physical values. */
    public static final String SOURCE_DOCUMENT = "docs/stage20a_station_physical_geometry.md";

    private static final Set<String> REQUIRED_STATION_IDS = Set.of(
            "station.infrastructure.mining_outpost",
            "station.infrastructure.volatile_depot",
            "station.infrastructure.refinery_complex",
            "station.infrastructure.industrial_station",
            "station.infrastructure.high_tech_hub",
            "station.infrastructure.trade_logistics_hub",
            "station.infrastructure.naval_ordnance_depot",
            "station.infrastructure.frontier_multipurpose");

    /**
     * Validates and deterministically freezes the station-geometry profile.
     *
     * @param version stable geometry-profile version
     * @param authority explicit design-input authority
     * @param sourceDocument exact Stage-20 design source
     * @param stage22ReviewRequired whether provisional geometry must be reviewed during content promotion
     * @param stationDesigns explicit physical geometry for every required Stage-18 station archetype
     * @param placementEnvelopes conservative envelopes derived from the authored geometry
     */
    public Stage20StationPhysicalGeometryProfile {
        requireText(version, "version");
        Objects.requireNonNull(authority, "authority");
        requireText(sourceDocument, "sourceDocument");
        stationDesigns = sortedDesigns(stationDesigns);
        placementEnvelopes = sortedEnvelopes(placementEnvelopes);
        if (authority == CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE && !stage22ReviewRequired) {
            throw new IllegalArgumentException("provisional station geometry requires Stage-22 review");
        }
    }

    /**
     * Derives the current physical station placement profile from explicitly authored design inputs.
     *
     * @return deterministic Stage-20A station physical-geometry profile
     */
    public static Stage20StationPhysicalGeometryProfile deriveCurrent() {
        List<StationGeometryDesign> designs = List.of(
                design("station.infrastructure.mining_outpost", 420d, 260d, 650d, 900d,
                        "compact_free_body_industrial_outpost"),
                design("station.infrastructure.volatile_depot", 620d, 420d, 850d, 1_400d,
                        "hazardous_tankage_requires_expanded_traffic_separation"),
                design("station.infrastructure.refinery_complex", 950d, 620d, 1_000d, 1_600d,
                        "distributed_processing_and_bulk_transfer_complex"),
                design("station.infrastructure.industrial_station", 1_200d, 780d, 1_250d, 1_800d,
                        "heavy_fabrication_and_assembly_complex"),
                design("station.infrastructure.high_tech_hub", 1_050d, 700d, 1_200d, 1_700d,
                        "precision_manufacturing_hub_with_controlled_approach"),
                design("station.infrastructure.trade_logistics_hub", 1_600d, 1_000d, 1_800d, 3_000d,
                        "high_traffic_multi_berth_logistics_hub"),
                design("station.infrastructure.naval_ordnance_depot", 1_100d, 750d, 1_400d, 2_800d,
                        "ordnance_security_and_hazard_separation"),
                design("station.infrastructure.frontier_multipurpose", 850d, 560d, 1_000d, 1_500d,
                        "self_contained_mixed_frontier_operations"));

        List<StationPlacementEnvelope> envelopes = designs.stream()
                .map(Stage20StationPhysicalGeometryProfile::deriveEnvelope)
                .toList();
        return new Stage20StationPhysicalGeometryProfile(
                CURRENT_VERSION,
                CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE,
                SOURCE_DOCUMENT,
                true,
                designs,
                envelopes);
    }

    /**
     * Returns whether all required Stage-18 station archetypes now have explicit physical placement geometry.
     *
     * <p>Closure is intentionally about physical placement readiness only. It does not claim that
     * station defensive sensors, jump-arrival stand-off, local route semantics, infrastructure
     * distribution extents or LOD activation bands are closed.</p>
     *
     * @return true when Stage 20B can consume station footprints without inventing dimensions
     */
    public boolean closesStage20BEntryCoverage() {
        if (authority != CalibrationAuthority.PROVISIONAL_ACCEPTED_REFERENCE
                || !stage22ReviewRequired
                || !SOURCE_DOCUMENT.equals(sourceDocument)) {
            return false;
        }
        Set<String> designIds = stationDesigns.stream()
                .map(StationGeometryDesign::stationArchetypeId)
                .collect(Collectors.toSet());
        Set<String> envelopeIds = placementEnvelopes.stream()
                .map(StationPlacementEnvelope::stationArchetypeId)
                .collect(Collectors.toSet());
        if (!designIds.equals(REQUIRED_STATION_IDS) || !envelopeIds.equals(REQUIRED_STATION_IDS)) {
            return false;
        }
        for (StationGeometryDesign design : stationDesigns) {
            StationPlacementEnvelope envelope = placementEnvelope(design.stationArchetypeId());
            StationPlacementEnvelope expected = deriveEnvelope(design);
            if (!envelope.equals(expected)
                    || !envelope.provenance().equals(design.provenanceId())
                    || envelope.operationalRadiusM() <= design.footprintLengthM() / 2d
                    || envelope.sameClassMinimumCenterSeparationM() != envelope.operationalRadiusM() * 2d) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns the authored geometry for one Stage-18 station archetype.
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @return matching explicit station geometry design
     */
    public StationGeometryDesign stationDesign(String stationArchetypeId) {
        requireText(stationArchetypeId, "stationArchetypeId");
        return stationDesigns.stream()
                .filter(value -> value.stationArchetypeId().equals(stationArchetypeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Stage-20 station geometry: " + stationArchetypeId));
    }

    /**
     * Returns the conservative placement envelope for one station archetype.
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @return matching conservative physical placement envelope
     */
    public StationPlacementEnvelope placementEnvelope(String stationArchetypeId) {
        requireText(stationArchetypeId, "stationArchetypeId");
        return placementEnvelopes.stream()
                .filter(value -> value.stationArchetypeId().equals(stationArchetypeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown Stage-20 station envelope: " + stationArchetypeId));
    }

    private static StationGeometryDesign design(
            String stationId,
            double footprintLengthM,
            double footprintWidthM,
            double dockingApproachClearanceM,
            double trafficClearanceM,
            String rationaleId) {
        return new StationGeometryDesign(
                stationId,
                SOURCE_DOCUMENT + "#" + stationId,
                rationaleId,
                footprintLengthM,
                footprintWidthM,
                dockingApproachClearanceM,
                trafficClearanceM);
    }

    private static StationPlacementEnvelope deriveEnvelope(StationGeometryDesign design) {
        return Stage20FormationStationSpatialCalibrationCalculator.deriveStationPlacementEnvelope(
                new StationPlacementGeometryInput(
                        design.stationArchetypeId(),
                        design.provenanceId(),
                        design.footprintLengthM(),
                        design.footprintWidthM(),
                        design.dockingApproachClearanceM(),
                        design.trafficClearanceM()));
    }

    /**
     * Explicit authored physical geometry for one station archetype.
     *
     * @param stationArchetypeId stable Stage-18 station archetype ID
     * @param provenanceId exact Stage-20 design provenance
     * @param rationaleId stable non-numeric rationale identifier
     * @param footprintLengthM physical top-down footprint length in meters
     * @param footprintWidthM physical top-down footprint width in meters
     * @param dockingApproachClearanceM clear approach distance beyond the structural footprint
     * @param trafficClearanceM conservative traffic-separation distance beyond the structural footprint
     */
    public record StationGeometryDesign(
            String stationArchetypeId,
            String provenanceId,
            String rationaleId,
            double footprintLengthM,
            double footprintWidthM,
            double dockingApproachClearanceM,
            double trafficClearanceM) {
        /**
         * Validates one explicit provisional station-geometry design.
         *
         * @param stationArchetypeId stable Stage-18 station archetype ID
         * @param provenanceId exact Stage-20 design provenance
         * @param rationaleId stable non-numeric rationale identifier
         * @param footprintLengthM physical top-down footprint length in meters
         * @param footprintWidthM physical top-down footprint width in meters
         * @param dockingApproachClearanceM clear approach distance beyond the structural footprint
         * @param trafficClearanceM conservative traffic-separation distance beyond the structural footprint
         */
        public StationGeometryDesign {
            requireText(stationArchetypeId, "stationArchetypeId");
            requireText(provenanceId, "provenanceId");
            requireText(rationaleId, "rationaleId");
            requirePositive(footprintLengthM, "footprintLengthM");
            requirePositive(footprintWidthM, "footprintWidthM");
            requireNonNegative(dockingApproachClearanceM, "dockingApproachClearanceM");
            requireNonNegative(trafficClearanceM, "trafficClearanceM");
            if (!provenanceId.startsWith(SOURCE_DOCUMENT + "#")) {
                throw new IllegalArgumentException("station geometry provenance must point to the Stage-20 design authority");
            }
        }
    }

    private static List<StationGeometryDesign> sortedDesigns(List<StationGeometryDesign> values) {
        Objects.requireNonNull(values, "stationDesigns");
        ArrayList<StationGeometryDesign> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("stationDesigns must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(StationGeometryDesign::stationArchetypeId));
        if (copy.stream().map(StationGeometryDesign::stationArchetypeId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("station geometry IDs must be unique");
        }
        return List.copyOf(copy);
    }

    private static List<StationPlacementEnvelope> sortedEnvelopes(List<StationPlacementEnvelope> values) {
        Objects.requireNonNull(values, "placementEnvelopes");
        ArrayList<StationPlacementEnvelope> copy = new ArrayList<>(values);
        if (copy.isEmpty() || copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("placementEnvelopes must be non-empty and contain no nulls");
        }
        copy.sort(Comparator.comparing(StationPlacementEnvelope::stationArchetypeId));
        if (copy.stream().map(StationPlacementEnvelope::stationArchetypeId).distinct().count() != copy.size()) {
            throw new IllegalArgumentException("station placement envelope IDs must be unique");
        }
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
}
