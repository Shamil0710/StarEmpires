package com.spacesim.world;

import com.spacesim.simulation.SimulationRandom;
import com.spacesim.simulation.StatefulRandom;
import com.spacesim.world.Stage20LocalInfrastructureLayout.CalibratedConnection;
import com.spacesim.world.Stage20LocalInfrastructureLayout.InfrastructurePlacement;
import com.spacesim.world.Stage20LocalInfrastructureLayout.LogisticsConsequenceEnvelope;
import com.spacesim.world.Stage20LocalInfrastructureLayout.PlacementKind;
import com.spacesim.world.calibration.Stage20FormationStationSpatialCalibrationProfile.StationPlacementEnvelope;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandDefinition;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.RepresentativeGroup;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.SemanticRouteSample;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticCalibrationProfile.ThrustPolicy;
import com.spacesim.world.calibration.Stage20StationDefensiveSensorGeometryProfile;
import com.spacesim.world.calibration.Stage20StationPhysicalGeometryProfile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * Deterministic Stage-20C relative infrastructure placement calibrated by Stage-20A logistics.
 *
 * <p>The generator never accepts a raw caller-authored distance. A target role resolves to one
 * accepted semantic route band and the sampled SI separation remains inside that band. The band in
 * turn already carries representative civilian routine-travel and military response consequences,
 * so labels never become an independent strategic-distance system.</p>
 *
 * <p>Independent stations additionally respect accepted station operational/defensive geometry and
 * the lower {@link BandId#STATION_TO_STATION} logistics separation against every already generated
 * independent station. This prevents default generation from accidentally creating unavoidable
 * mutual point-blank station geometry. Special fortified overlaps require a later explicit design
 * rule rather than silently weakening this default.</p>
 */
public final class Stage20LocalInfrastructureLayoutGenerator {
    private static final String RNG_STREAM_PREFIX = "stage20c.local-infrastructure-spacing.v1.system.";
    private static final int MAX_PLACEMENT_ATTEMPTS = 128;

    /** Target roles supported by the first Stage-20C relative-placement contract. */
    public enum TargetKind {
        /** Independent major station relative to the major hub. */ INDEPENDENT_STATION,
        /** Resource-field point anchor relative to the hub. */ RESOURCE_FIELD_ANCHOR,
        /** Jump-arrival point anchor relative to the major hub. */ JUMP_ARRIVAL_ANCHOR
    }

    /**
     * One semantic target placement request; no physical distance is caller-authored.
     *
     * @param targetId stable generated/content identity
     * @param kind semantic target role
     * @param stationArchetypeId required only for independent station targets
     */
    public record PlacementRequest(String targetId, TargetKind kind, Optional<String> stationArchetypeId) {
        /**
         * Validates one semantic request without accepting a parallel distance value.
         *
         * @param targetId stable target identity
         * @param kind semantic target role
         * @param stationArchetypeId station archetype only for independent stations
         */
        public PlacementRequest {
            requireText(targetId, "targetId");
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(stationArchetypeId, "stationArchetypeId");
            if (kind == TargetKind.INDEPENDENT_STATION) {
                if (stationArchetypeId.isEmpty() || stationArchetypeId.orElseThrow().isBlank()) {
                    throw new IllegalArgumentException("independent station request requires a station archetype");
                }
            } else if (stationArchetypeId.isPresent()) {
                throw new IllegalArgumentException("resource/jump anchors cannot claim station geometry");
            }
        }

        /**
         * Creates an independent station request.
         *
         * @param targetId stable station identity
         * @param stationArchetypeId accepted Stage-18/20 station archetype
         * @return validated semantic station request
         */
        public static PlacementRequest independentStation(String targetId, String stationArchetypeId) {
            return new PlacementRequest(targetId, TargetKind.INDEPENDENT_STATION, Optional.of(stationArchetypeId));
        }

        /**
         * Creates a resource-field point-anchor request.
         *
         * @param targetId stable resource-field identity
         * @return validated semantic resource anchor request
         */
        public static PlacementRequest resourceFieldAnchor(String targetId) {
            return new PlacementRequest(targetId, TargetKind.RESOURCE_FIELD_ANCHOR, Optional.empty());
        }

        /**
         * Creates a jump-arrival point-anchor request.
         *
         * @param targetId stable jump-arrival identity
         * @return validated semantic jump anchor request
         */
        public static PlacementRequest jumpArrivalAnchor(String targetId) {
            return new PlacementRequest(targetId, TargetKind.JUMP_ARRIVAL_ANCHOR, Optional.empty());
        }
    }

    private Stage20LocalInfrastructureLayoutGenerator() {
        throw new AssertionError("No instances");
    }

    /**
     * Generates deterministic physical placements around an already resolved major-hub anchor.
     *
     * <p>The supplied hub position is authoritative physical geometry from the caller. It is not
     * snapped to the Stage-20B operational envelope because that envelope is descriptive rather than
     * a world boundary. Every generated relative displacement is expressed in SI meters through
     * {@link LocalPhysicalPosition}.</p>
     *
     * @param systemGeometry accepted Stage-20B system geometry
     * @param majorHubPosition authoritative physical position of the major hub
     * @param majorHubId stable major-hub identity
     * @param majorHubStationArchetypeId accepted station archetype of the hub
     * @param requests semantic targets to place relative to the hub
     * @return deterministic Stage-20C local-infrastructure layout
     */
    public static Stage20LocalInfrastructureLayout generate(
            Stage20SystemGeometry systemGeometry,
            LocalPhysicalPosition majorHubPosition,
            String majorHubId,
            String majorHubStationArchetypeId,
            List<PlacementRequest> requests) {
        Stage20SystemGeometry geometry = Objects.requireNonNull(systemGeometry, "systemGeometry");
        LocalPhysicalPosition hubPosition = Objects.requireNonNull(majorHubPosition, "majorHubPosition");
        requireText(majorHubId, "majorHubId");
        requireText(majorHubStationArchetypeId, "majorHubStationArchetypeId");
        Objects.requireNonNull(requests, "requests");
        if (!Stage20SystemGeometry.CURRENT_VERSION.equals(geometry.version())) {
            throw new IllegalArgumentException("Stage-20C requires current Stage-20B system geometry");
        }

        Stage20LocalRouteSemanticCalibrationProfile routes =
                Stage20LocalRouteSemanticCalibrationProfile.deriveCurrent();
        Stage20StationPhysicalGeometryProfile stationGeometry =
                Stage20StationPhysicalGeometryProfile.deriveCurrent();
        Stage20StationDefensiveSensorGeometryProfile stationDefense =
                Stage20StationDefensiveSensorGeometryProfile.deriveCurrent();
        if (!routes.closesStage20BEntryCoverage()
                || !stationGeometry.closesStage20BEntryCoverage()
                || !stationDefense.closesStage20BEntryCoverage()) {
            throw new IllegalStateException("Stage-20C requires closed Stage-20A route/station geometry");
        }

        List<PlacementRequest> orderedRequests = validatedOrderedRequests(majorHubId, requests);
        Map<BandId, BandDefinition> routeBands = routes.bands().stream()
                .collect(Collectors.toMap(BandDefinition::id, value -> value, (left, right) -> {
                    throw new IllegalStateException("Duplicate accepted route band: " + left.id());
                }, () -> new EnumMap<>(BandId.class)));

        InfrastructurePlacement hub = stationPlacement(
                majorHubId,
                PlacementKind.MAJOR_HUB_STATION,
                majorHubStationArchetypeId,
                hubPosition,
                stationGeometry,
                stationDefense);
        List<InfrastructurePlacement> placements = new ArrayList<>();
        placements.add(hub);
        List<InfrastructurePlacement> generatedStations = new ArrayList<>();
        generatedStations.add(hub);
        List<CalibratedConnection> connections = new ArrayList<>();

        SimulationRandom rootRandom = new SimulationRandom(geometry.rootSeed());
        for (PlacementRequest request : orderedRequests) {
            BandId bandId = bandFor(request.kind());
            BandDefinition band = requireBand(routeBands, bandId);
            InfrastructurePlacement template = placementTemplate(
                    request,
                    hubPosition,
                    stationGeometry,
                    stationDefense);
            double minimumDistanceM = minimumDistanceFromHub(
                    request.kind(),
                    band,
                    hub,
                    template,
                    routes.maxClosedStationStandOffM());
            if (minimumDistanceM > band.maxDistanceM()) {
                throw new IllegalStateException(
                        "Accepted geometry cannot satisfy semantic band for target " + request.targetId());
            }

            StatefulRandom random = rootRandom.createStream(streamName(geometry, majorHubId, request.targetId()));
            InfrastructurePlacement resolved = placeTarget(
                    request,
                    template,
                    hubPosition,
                    minimumDistanceM,
                    band.maxDistanceM(),
                    routeBands,
                    generatedStations,
                    random);
            placements.add(resolved);
            if (resolved.isStation()) {
                generatedStations.add(resolved);
            }

            double actualDistanceM = hubPosition.distanceTo(resolved.position());
            LogisticsConsequenceEnvelope consequenceEnvelope = logisticsEnvelope(routes, bandId);
            if (request.kind() == TargetKind.JUMP_ARRIVAL_ANCHOR) {
                connections.add(connection(
                        resolved.id(),
                        hub.id(),
                        band,
                        actualDistanceM,
                        consequenceEnvelope));
            } else {
                connections.add(connection(
                        hub.id(),
                        resolved.id(),
                        band,
                        actualDistanceM,
                        consequenceEnvelope));
            }
        }

        return new Stage20LocalInfrastructureLayout(
                Stage20LocalInfrastructureLayout.CURRENT_VERSION,
                geometry.systemId(),
                geometry.rootSeed(),
                majorHubId,
                placements,
                connections,
                geometry.version(),
                routes.version(),
                stationGeometry.version(),
                stationDefense.version());
    }

    private static InfrastructurePlacement placeTarget(
            PlacementRequest request,
            InfrastructurePlacement template,
            LocalPhysicalPosition hubPosition,
            double minimumDistanceM,
            double maximumDistanceM,
            Map<BandId, BandDefinition> routeBands,
            List<InfrastructurePlacement> generatedStations,
            StatefulRandom random) {
        BandDefinition stationBand = requireBand(routeBands, BandId.STATION_TO_STATION);
        for (int attempt = 0; attempt < MAX_PLACEMENT_ATTEMPTS; attempt++) {
            double distanceM = sampleRange(random.nextLong(), minimumDistanceM, maximumDistanceM);
            double angleRad = unitInterval(random.nextLong()) * Math.PI * 2d;
            LocalPhysicalPosition candidatePosition = hubPosition.translated(
                    Math.cos(angleRad) * distanceM,
                    Math.sin(angleRad) * distanceM);
            InfrastructurePlacement candidate = new InfrastructurePlacement(
                    template.id(),
                    template.kind(),
                    template.stationArchetypeId(),
                    candidatePosition,
                    template.operationalRadiusM(),
                    template.defensiveExclusionReferenceM());
            if (!candidate.isStation()
                    || respectsIndependentStationSpacing(candidate, generatedStations, stationBand.minDistanceM())) {
                return candidate;
            }
        }
        throw new IllegalStateException(
                "Cannot place target without violating accepted station/logistics spacing: " + request.targetId());
    }

    private static boolean respectsIndependentStationSpacing(
            InfrastructurePlacement candidate,
            List<InfrastructurePlacement> generatedStations,
            double stationRouteMinimumM) {
        for (InfrastructurePlacement existing : generatedStations) {
            double requiredM = Math.max(
                    stationRouteMinimumM,
                    Math.max(
                            existing.operationalRadiusM() + candidate.operationalRadiusM(),
                            Math.max(
                                    existing.defensiveExclusionReferenceM(),
                                    candidate.defensiveExclusionReferenceM())));
            if (existing.position().distanceTo(candidate.position()) < requiredM) {
                return false;
            }
        }
        return true;
    }

    private static double minimumDistanceFromHub(
            TargetKind kind,
            BandDefinition band,
            InfrastructurePlacement hub,
            InfrastructurePlacement target,
            double maxClosedStationStandOffM) {
        double minimum = band.minDistanceM();
        if (kind == TargetKind.INDEPENDENT_STATION) {
            minimum = Math.max(
                    minimum,
                    Math.max(
                            hub.operationalRadiusM() + target.operationalRadiusM(),
                            Math.max(
                                    hub.defensiveExclusionReferenceM(),
                                    target.defensiveExclusionReferenceM())));
        } else if (kind == TargetKind.JUMP_ARRIVAL_ANCHOR) {
            minimum = Math.max(minimum, maxClosedStationStandOffM);
        }
        return minimum;
    }

    private static InfrastructurePlacement placementTemplate(
            PlacementRequest request,
            LocalPhysicalPosition placeholder,
            Stage20StationPhysicalGeometryProfile stationGeometry,
            Stage20StationDefensiveSensorGeometryProfile stationDefense) {
        if (request.kind() == TargetKind.INDEPENDENT_STATION) {
            return stationPlacement(
                    request.targetId(),
                    PlacementKind.INDEPENDENT_STATION,
                    request.stationArchetypeId().orElseThrow(),
                    placeholder,
                    stationGeometry,
                    stationDefense);
        }
        PlacementKind kind = request.kind() == TargetKind.RESOURCE_FIELD_ANCHOR
                ? PlacementKind.RESOURCE_FIELD_ANCHOR
                : PlacementKind.JUMP_ARRIVAL_ANCHOR;
        return new InfrastructurePlacement(
                request.targetId(), kind, Optional.empty(), placeholder, 0d, 0d);
    }

    private static InfrastructurePlacement stationPlacement(
            String id,
            PlacementKind kind,
            String stationArchetypeId,
            LocalPhysicalPosition position,
            Stage20StationPhysicalGeometryProfile stationGeometry,
            Stage20StationDefensiveSensorGeometryProfile stationDefense) {
        StationPlacementEnvelope envelope = stationGeometry.placementEnvelope(stationArchetypeId);
        double defensiveReferenceM = stationDefense.station(stationArchetypeId).defensiveExclusionReferenceM();
        return new InfrastructurePlacement(
                id,
                kind,
                Optional.of(stationArchetypeId),
                position,
                envelope.operationalRadiusM(),
                defensiveReferenceM);
    }

    private static CalibratedConnection connection(
            String fromId,
            String toId,
            BandDefinition band,
            double actualDistanceM,
            LogisticsConsequenceEnvelope consequences) {
        return new CalibratedConnection(
                fromId,
                toId,
                band.id(),
                actualDistanceM,
                band.minDistanceM(),
                band.maxDistanceM(),
                band.sourceEvidenceId(),
                consequences);
    }

    private static LogisticsConsequenceEnvelope logisticsEnvelope(
            Stage20LocalRouteSemanticCalibrationProfile routes,
            BandId bandId) {
        List<SemanticRouteSample> civilianRoutine = routes.samples().stream()
                .filter(value -> value.bandId() == bandId)
                .filter(value -> value.representativeGroup() == RepresentativeGroup.CIVILIAN_LOGISTICS)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.ROUTINE_SUSTAINED)
                .toList();
        List<SemanticRouteSample> militaryResponse = routes.samples().stream()
                .filter(value -> value.bandId() == bandId)
                .filter(value -> value.representativeGroup() == RepresentativeGroup.MILITARY)
                .filter(value -> value.thrustPolicy() == ThrustPolicy.MAX_THRUST_RESPONSE)
                .toList();
        if (civilianRoutine.isEmpty() || militaryResponse.isEmpty()) {
            throw new IllegalStateException("Accepted semantic band lacks civilian/military physical consequences: " + bandId);
        }

        double civilianTravelMin = minimum(civilianRoutine, SemanticRouteSample::totalTravelTimeS);
        double civilianTravelMax = maximum(civilianRoutine, SemanticRouteSample::totalTravelTimeS);
        double militaryResponseMin = minimum(militaryResponse, SemanticRouteSample::totalTravelTimeS);
        double militaryResponseMax = maximum(militaryResponse, SemanticRouteSample::totalTravelTimeS);
        double civilianDeltaVMin = minimum(civilianRoutine, SemanticRouteSample::requiredDeltaVMps) * 2d;
        double civilianDeltaVMax = maximum(civilianRoutine, SemanticRouteSample::requiredDeltaVMps) * 2d;

        return new LogisticsConsequenceEnvelope(
                civilianTravelMin,
                civilianTravelMax,
                militaryResponseMin,
                militaryResponseMax,
                civilianDeltaVMin,
                civilianDeltaVMax,
                civilianTravelMin * 2d,
                civilianTravelMax * 2d,
                routes.version());
    }

    private static double minimum(List<SemanticRouteSample> samples, ToDoubleFunction<SemanticRouteSample> metric) {
        return samples.stream().mapToDouble(metric).min().orElseThrow();
    }

    private static double maximum(List<SemanticRouteSample> samples, ToDoubleFunction<SemanticRouteSample> metric) {
        return samples.stream().mapToDouble(metric).max().orElseThrow();
    }

    private static List<PlacementRequest> validatedOrderedRequests(String majorHubId, List<PlacementRequest> requests) {
        ArrayList<PlacementRequest> ordered = new ArrayList<>(requests);
        if (ordered.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("requests cannot contain nulls");
        }
        ordered.sort(Comparator.comparing(PlacementRequest::targetId));
        Set<String> ids = new HashSet<>();
        ids.add(majorHubId);
        for (PlacementRequest request : ordered) {
            if (!ids.add(request.targetId())) {
                throw new IllegalArgumentException("infrastructure IDs must be unique: " + request.targetId());
            }
        }
        return List.copyOf(ordered);
    }

    private static BandId bandFor(TargetKind kind) {
        return switch (kind) {
            case INDEPENDENT_STATION -> BandId.STATION_TO_STATION;
            case RESOURCE_FIELD_ANCHOR -> BandId.STATION_TO_RESOURCE_FIELD;
            case JUMP_ARRIVAL_ANCHOR -> BandId.JUMP_ARRIVAL_TO_MAJOR_HUB;
        };
    }

    private static BandDefinition requireBand(Map<BandId, BandDefinition> bands, BandId id) {
        BandDefinition band = bands.get(id);
        if (band == null) {
            throw new IllegalStateException("Missing accepted Stage-20 local route band: " + id);
        }
        return band;
    }

    private static String streamName(Stage20SystemGeometry geometry, String hubId, String targetId) {
        return RNG_STREAM_PREFIX + geometry.systemId().value() + ".hub." + hubId + ".target." + targetId;
    }

    private static double sampleRange(long bits, double min, double max) {
        if (min == max) {
            return min;
        }
        return Math.fma(max - min, unitInterval(bits), min);
    }

    private static double unitInterval(long bits) {
        return (bits >>> 11) * 0x1.0p-53;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
