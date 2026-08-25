package com.spacesim.persistence;

import com.badlogic.ashley.core.Entity;
import com.spacesim.components.ShipComponent;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.FleetArrivalAuthority;
import com.spacesim.world.FleetId;
import com.spacesim.world.FleetLocationKind;
import com.spacesim.world.FleetPlacementState;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;
import com.spacesim.world.calibration.Stage20LocalRouteSemanticBandCatalog.BandId;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * Stage-20.5D adapter from persisted Stage-20D jump-edge endpoints to the live ordinary fleet jump
 * FSM and Stage-20 hierarchical/double materialization authority.
 *
 * <p>The adapter parses only saved Stage-20K canonical rows. It does not regenerate edge geometry,
 * alter topology, bypass intermediate systems, change fitted jump constraints or grant discovery.
 * Current resolved generated worlds also persist the accepted jump-anchor-to-hub local-route
 * calibration. That evidence gives the existing {@code MOVING_TO_JUMP} phase deterministic physical
 * duration and exact local motion to the outgoing endpoint instead of the historical one-tick
 * placeholder.</p>
 *
 * <p>Local approach speed is not a new arbitrary balance constant. For commercial/mining hulls the
 * adapter uses the persisted conservative civilian-routine upper travel-time envelope; combat hulls
 * use the persisted conservative military-response upper envelope. The saved reference time is
 * scaled by actual exact distance relative to that anchor's calibrated hub distance. Exact position
 * remains hierarchical/double authority in {@link Stage20MaterializationService}; legacy float
 * transforms are only a render projection.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20LiveArrivalAuthorityIntegration implements FleetArrivalAuthority {
    /** Stable live-arrival integration contract version. */
    public static final String CURRENT_VERSION = "stage20_5.live-arrival-authority.v2";
    /** Current persisted scalar arrival velocity uses this stable local +X orientation convention. */
    public static final String VELOCITY_ORIENTATION_CONVENTION =
            "stage20d.persisted-arrival-speed.local-positive-x.v1";

    private static final String JUMP_EDGE_DOMAIN = "JUMP_EDGE";
    private static final String LOCAL_CONNECTION_DOMAIN = "LOCAL_CONNECTION";
    private static final int MINIMUM_JUMP_EDGE_VALUE_COUNT = 26;
    private static final int MINIMUM_LOCAL_CONNECTION_VALUE_COUNT = 17;

    private final long rootSeed;
    private final String generatorVersion;
    private final String worldFingerprint;
    private final Map<EdgeKey, PersistedEdge> edges;
    private final Map<ApproachKey, LocalApproachCalibration> approaches;
    private final Map<StarSystemId, Stage20MaterializationService> materializationBySystem;
    private final Set<FleetId> liveDepartures = new HashSet<>();
    private boolean bound;
    private WorldSimulation boundWorld;

    private Stage20LiveArrivalAuthorityIntegration(
            long rootSeed,
            String generatorVersion,
            String worldFingerprint,
            Map<EdgeKey, PersistedEdge> edges,
            Map<ApproachKey, LocalApproachCalibration> approaches,
            Map<StarSystemId, Stage20MaterializationService> materializationBySystem) {
        this.rootSeed = rootSeed;
        this.generatorVersion = requireText(generatorVersion, "generatorVersion");
        this.worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
        this.edges = Map.copyOf(edges);
        this.approaches = Map.copyOf(approaches);
        this.materializationBySystem = Map.copyOf(materializationBySystem);
        if (this.edges.isEmpty() || this.approaches.isEmpty() || this.materializationBySystem.isEmpty()) {
            throw new IllegalArgumentException(
                    "live arrival integration requires edges, local approach calibration and runtimes");
        }
        for (PersistedEdge edge : this.edges.values()) {
            requireApproach(edge.firstEndpoint());
            requireApproach(edge.secondEndpoint());
        }
    }

    /**
     * Restores exact endpoint authority from one saved campaign and caller-owned local runtimes.
     *
     * @param saved exact Stage-20K saved campaign
     * @param materializationBySystem Stage-20 physical sidecar for every live system session
     * @return unbound exact arrival integration
     */
    public static Stage20LiveArrivalAuthorityIntegration restore(
            Stage20GeneratedCampaignPersistentState saved,
            Map<StarSystemId, Stage20MaterializationService> materializationBySystem) {
        Stage20GeneratedCampaignPersistentState state = Objects.requireNonNull(saved, "saved");
        if (!state.openRuntimeBoundaries().contains(
                OpenRuntimeBoundary.LIVE_ARRIVAL_AUTHORITY_INTEGRATION)) {
            throw new IllegalArgumentException("saved campaign lacks live arrival authority boundary");
        }
        TreeMap<EdgeKey, PersistedEdge> edges = new TreeMap<>();
        TreeMap<ApproachKey, LocalApproachCalibration> approaches = new TreeMap<>();
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (JUMP_EDGE_DOMAIN.equals(row.domain())) {
                PersistedEdge edge = parseEdge(row);
                if (edges.putIfAbsent(edge.key(), edge) != null) {
                    throw malformed(row, "duplicate ordinary edge endpoints");
                }
            } else if (LOCAL_CONNECTION_DOMAIN.equals(row.domain())) {
                LocalApproachCalibration approach = parseApproach(row);
                if (approach != null
                        && approaches.putIfAbsent(approach.key(), approach) != null) {
                    throw malformed(row, "duplicate jump-anchor local approach calibration");
                }
            }
        }
        return new Stage20LiveArrivalAuthorityIntegration(
                state.generationIdentity().worldSeed(),
                state.generationIdentity().generatorVersion(),
                state.materializedWorld().worldFingerprint(),
                edges,
                approaches,
                Objects.requireNonNull(materializationBySystem, "materializationBySystem"));
    }

    /**
     * Creates one Stage-20 physical service per existing world session, restores saved endpoints and
     * binds the adapter to the ordinary jump FSM.
     *
     * @param saved exact Stage-20K saved campaign
     * @param world ordinary live world simulation
     * @return bound live exact-arrival integration
     */
    public static Stage20LiveArrivalAuthorityIntegration restoreAndBind(
            Stage20GeneratedCampaignPersistentState saved,
            WorldSimulation world) {
        WorldSimulation runtime = Objects.requireNonNull(world, "world");
        TreeMap<StarSystemId, Stage20MaterializationService> services = new TreeMap<>();
        runtime.getTopology().systems().forEach(system -> services.put(
                system.id(),
                Stage20MaterializationService.forSession(
                        runtime.findSession(system.id()).orElseThrow())));
        Stage20LiveArrivalAuthorityIntegration result = restore(saved, services);
        result.bind(runtime);
        return result;
    }

    /**
     * Validates exact topology coverage and installs this one-shot authority into the live FSM.
     *
     * @param world ordinary live world simulation
     */
    public void bind(WorldSimulation world) {
        WorldSimulation runtime = Objects.requireNonNull(world, "world");
        if (bound) {
            throw new IllegalStateException("live arrival authority is already bound");
        }
        validateTopology(runtime.getTopology());
        for (var system : runtime.getTopology().systems()) {
            if (!materializationBySystem.containsKey(system.id())
                    || runtime.findSession(system.id()).isEmpty()) {
                throw new IllegalArgumentException(
                        "live arrival integration lacks a system materialization runtime");
            }
        }
        runtime.bindFleetArrivalAuthority(this);
        boundWorld = runtime;
        bound = true;
    }

    /** @return exact saved root seed */
    public long rootSeed() {
        return rootSeed;
    }

    /** @return exact saved generator version */
    public String generatorVersion() {
        return generatorVersion;
    }

    /** @return exact saved world fingerprint */
    public String worldFingerprint() {
        return worldFingerprint;
    }

    /**
     * Returns the Stage-20 physical service for explicit entity registration/inspection.
     *
     * @param systemId live system identity
     * @return exact physical sidecar owned by that system session
     */
    public Stage20MaterializationService materialization(StarSystemId systemId) {
        Stage20MaterializationService result = materializationBySystem.get(
                Objects.requireNonNull(systemId, "systemId"));
        if (result == null) {
            throw new IllegalArgumentException("unknown materialized arrival system: " + systemId);
        }
        return result;
    }

    /**
     * Resolves only the exact persisted ordinary edge and explicit destination endpoint.
     *
     * @param origin explicit direct-edge origin
     * @param destination explicit direct-edge destination
     * @return exact saved destination authority
     */
    @Override
    public ResolvedArrival resolve(StarSystemId origin, StarSystemId destination) {
        StarSystemId from = Objects.requireNonNull(origin, "origin");
        StarSystemId to = Objects.requireNonNull(destination, "destination");
        PersistedEdge edge = edges.get(new EdgeKey(from, to));
        if (edge == null) {
            throw new IllegalArgumentException("saved world has no ordinary direct edge: " + from + " -> " + to);
        }
        PersistedEndpoint endpoint = edge.endpoint(to);
        LocalPhysicalKinematics exact = new LocalPhysicalKinematics(
                endpoint.position(), endpoint.arrivalVelocityMps(), 0d);
        return resolved(from, to, endpoint, exact);
    }

    @Override
    public long beginDepartureApproach(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            EntityId localEntityId,
            long worldTick,
            float fixedStepSeconds) {
        requireNonNegativeTick(worldTick, "worldTick");
        requirePositiveFixedStep(fixedStepSeconds);
        FleetPlacementState placement = requireLocalPlacement(
                fleetId, originSystemId, localEntityId);
        PersistedEndpoint departure = requireEdge(originSystemId, destinationSystemId)
                .endpoint(originSystemId);
        LocalApproachCalibration calibration = requireApproach(departure);
        LocalPhysicalKinematics current = materialization(originSystemId)
                .physicalState(localEntityId)
                .orElseThrow(() -> new IllegalStateException(
                        "generated-world fleet lacks exact local physical state before FTL approach: " + fleetId));
        double distanceM = current.position().distanceTo(departure.position());
        boolean military = isCombatShip(placement);
        double referenceSeconds = military
                ? calibration.militaryResponseTimeMaxS()
                : calibration.civilianRoutineTravelTimeMaxS();
        double travelSeconds = referenceSeconds * distanceM / calibration.referenceDistanceM();
        long ticks = travelSeconds <= 0d ? 1L : secondsToTicks(travelSeconds, fixedStepSeconds);
        double durationSeconds = ticks * (double) fixedStepSeconds;
        var displacement = current.position().displacementTo(departure.position());
        materialization(originSystemId).updatePhysicalState(
                localEntityId,
                new LocalPhysicalKinematics(
                        current.position(),
                        displacement.deltaXM() / durationSeconds,
                        displacement.deltaYM() / durationSeconds));
        return ticks;
    }

    @Override
    public void advanceDepartureApproach(
            FleetId fleetId,
            StarSystemId originSystemId,
            StarSystemId destinationSystemId,
            EntityId localEntityId,
            long previousWorldTick,
            long worldTick,
            long phaseEndsTick,
            float fixedStepSeconds) {
        requireNonNegativeTick(previousWorldTick, "previousWorldTick");
        requireNonNegativeTick(worldTick, "worldTick");
        requireNonNegativeTick(phaseEndsTick, "phaseEndsTick");
        requirePositiveFixedStep(fixedStepSeconds);
        if (worldTick < previousWorldTick || phaseEndsTick < previousWorldTick) {
            throw new IllegalStateException("generated-world FTL approach tick order is invalid");
        }
        requireLocalPlacement(fleetId, originSystemId, localEntityId);
        PersistedEndpoint departure = requireEdge(originSystemId, destinationSystemId)
                .endpoint(originSystemId);
        LocalPhysicalKinematics current = materialization(originSystemId)
                .physicalState(localEntityId)
                .orElseThrow(() -> new IllegalStateException(
                        "generated-world fleet lost exact local state during FTL approach: " + fleetId));

        long fromTick = Math.min(previousWorldTick, phaseEndsTick);
        long toTick = Math.min(worldTick, phaseEndsTick);
        if (toTick <= fromTick) {
            return;
        }
        long remainingBefore = phaseEndsTick - fromTick;
        if (remainingBefore <= 0L) {
            materialization(originSystemId).updatePhysicalState(
                    localEntityId, LocalPhysicalKinematics.stationary(departure.position()));
            return;
        }
        long elapsedTicks = toTick - fromTick;
        double fraction = Math.min(1d, elapsedTicks / (double) remainingBefore);
        var displacement = current.position().displacementTo(departure.position());
        LocalPhysicalPosition nextPosition = fraction >= 1d
                ? departure.position()
                : current.position().translated(
                        displacement.deltaXM() * fraction,
                        displacement.deltaYM() * fraction);
        long remainingAfter = phaseEndsTick - toTick;
        LocalPhysicalKinematics next;
        if (remainingAfter <= 0L) {
            next = LocalPhysicalKinematics.stationary(departure.position());
        } else {
            var remaining = nextPosition.displacementTo(departure.position());
            double remainingSeconds = remainingAfter * (double) fixedStepSeconds;
            next = new LocalPhysicalKinematics(
                    nextPosition,
                    remaining.deltaXM() / remainingSeconds,
                    remaining.deltaYM() / remainingSeconds);
        }
        materialization(originSystemId).updatePhysicalState(localEntityId, next);
    }

    /**
     * Releases the detached entity's exact origin-local physical sidecar.
     *
     * @param fleetId stable world fleet identity
     * @param originSystemId exact edge origin
     * @param formerLocalEntityId detached origin-local entity identity
     */
    @Override
    public void onDeparted(
            FleetId fleetId,
            StarSystemId originSystemId,
            EntityId formerLocalEntityId) {
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        Stage20MaterializationService origin = materialization(originSystemId);
        if (!liveDepartures.add(id)) {
            throw new IllegalStateException("fleet physical authority detached more than once: " + id);
        }
        origin.releasePhysicalStateForWorldTransfer(formerLocalEntityId);
    }

    /**
     * Registers the exact saved endpoint under the newly allocated destination-local identity.
     *
     * <p>An integration restored while a fleet is already in transit has no in-memory departure
     * marker. Arrival remains valid because the ordinary persisted FSM and transit payload are the
     * authority for that lifecycle; the marker only detects duplicate departures in one process.</p>
     *
     * @param fleetId unchanged stable world fleet identity
     * @param arrival exact resolved saved endpoint
     * @param destinationLocalEntityId freshly allocated destination-local identity
     */
    @Override
    public void onArrived(
            FleetId fleetId,
            ResolvedArrival arrival,
            EntityId destinationLocalEntityId) {
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        ResolvedArrival exact = Objects.requireNonNull(arrival, "arrival");
        liveDepartures.remove(id);
        materialization(exact.destinationSystemId()).registerPhysicalState(
                destinationLocalEntityId,
                exact.physicalState());
    }

    private FleetPlacementState requireLocalPlacement(
            FleetId fleetId,
            StarSystemId originSystemId,
            EntityId localEntityId) {
        if (!bound || boundWorld == null) {
            throw new IllegalStateException("generated-world FTL approach requires a bound world");
        }
        FleetId id = Objects.requireNonNull(fleetId, "fleetId");
        StarSystemId origin = Objects.requireNonNull(originSystemId, "originSystemId");
        EntityId local = Objects.requireNonNull(localEntityId, "localEntityId");
        FleetPlacementState placement = boundWorld.findFleet(id).orElseThrow(
                () -> new IllegalArgumentException("unknown generated-world FleetId: " + id));
        if (placement.locationKind() != FleetLocationKind.IN_SYSTEM
                || !origin.equals(placement.systemId())
                || !local.equals(placement.localEntityId())) {
            throw new IllegalStateException("generated-world FTL approach requires matching local fleet placement");
        }
        return placement;
    }

    private boolean isCombatShip(FleetPlacementState placement) {
        Entity entity = boundWorld.findSession(placement.systemId()).orElseThrow()
                .getEntityRegistry().find(placement.localEntityId());
        if (entity == null) {
            throw new IllegalStateException("generated-world fleet placement references missing local entity");
        }
        ShipComponent ship = entity.getComponent(ShipComponent.class);
        return ship != null && ship.type != null && ship.type.isCombat();
    }

    private PersistedEdge requireEdge(StarSystemId first, StarSystemId second) {
        PersistedEdge edge = edges.get(new EdgeKey(
                Objects.requireNonNull(first, "first"),
                Objects.requireNonNull(second, "second")));
        if (edge == null) {
            throw new IllegalArgumentException("saved world has no ordinary direct edge: " + first + " -> " + second);
        }
        return edge;
    }

    private LocalApproachCalibration requireApproach(PersistedEndpoint endpoint) {
        LocalApproachCalibration result = approaches.get(new ApproachKey(
                endpoint.systemId(), endpoint.anchorId()));
        if (result == null) {
            throw new IllegalArgumentException(
                    "saved jump endpoint lacks accepted local approach calibration: "
                            + endpoint.systemId() + ':' + endpoint.anchorId());
        }
        return result;
    }

    private static ResolvedArrival resolved(
            StarSystemId origin,
            StarSystemId destination,
            PersistedEndpoint endpoint,
            LocalPhysicalKinematics exact) {
        return new ResolvedArrival(
                origin,
                destination,
                endpoint.anchorId(),
                exact,
                exactFloat(endpoint.position().offsetXM(), "legacyProjectionX"),
                exactFloat(endpoint.position().offsetYM(), "legacyProjectionY"));
    }

    private void validateTopology(GalaxyTopology topology) {
        GalaxyTopology world = Objects.requireNonNull(topology, "topology");
        Set<EdgeKey> actual = new HashSet<>();
        for (JumpConnection connection : world.connections()) {
            actual.add(new EdgeKey(connection.first(), connection.second()));
        }
        if (!actual.equals(edges.keySet())) {
            throw new IllegalArgumentException(
                    "live world topology differs from saved Stage-20D edge authority");
        }
    }

    private static PersistedEdge parseEdge(CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() < MINIMUM_JUMP_EDGE_VALUE_COUNT) {
            throw malformed(row, "jump edge row lacks both persisted endpoints");
        }
        StarSystemId first = new StarSystemId(parsePositiveLong(values.get(1), row, "firstSystemId"));
        StarSystemId second = new StarSystemId(parsePositiveLong(values.get(2), row, "secondSystemId"));
        EdgeKey key = new EdgeKey(first, second);
        String expectedId = "ordinary:" + key.first().value() + ':' + key.second().value();
        if (!row.stableId().equals(expectedId)) {
            throw malformed(row, "edge stable ID differs from canonical endpoints");
        }
        PersistedEndpoint firstEndpoint = parseEndpoint(row, values, 8);
        PersistedEndpoint secondEndpoint = parseEndpoint(row, values, 17);
        if (!firstEndpoint.systemId().equals(first)
                || !secondEndpoint.systemId().equals(second)) {
            throw malformed(row, "arrival endpoints differ from canonical edge systems");
        }
        return new PersistedEdge(key, firstEndpoint, secondEndpoint);
    }

    private static PersistedEndpoint parseEndpoint(
            CanonicalRow row,
            List<String> values,
            int offset) {
        double speed = parseDouble(values.get(offset + 6), row, "arrivalVelocityMps");
        if (speed < 0d) {
            throw malformed(row, "arrivalVelocityMps must be non-negative");
        }
        return new PersistedEndpoint(
                new StarSystemId(parsePositiveLong(values.get(offset), row, "endpointSystemId")),
                requireText(values.get(offset + 1), "anchorId"),
                new LocalPhysicalPosition(
                        parseLong(values.get(offset + 2), row, "cellX"),
                        parseLong(values.get(offset + 3), row, "cellY"),
                        parseDouble(values.get(offset + 4), row, "offsetXM"),
                        parseDouble(values.get(offset + 5), row, "offsetYM")),
                speed,
                requireText(values.get(offset + 7), "localInfrastructureVersion"),
                requireText(values.get(offset + 8), "jumpArrivalCalibrationVersion"));
    }

    private static LocalApproachCalibration parseApproach(CanonicalRow row) {
        List<String> values = row.values();
        if (values.size() < MINIMUM_LOCAL_CONNECTION_VALUE_COUNT) {
            throw malformed(row, "local connection row is truncated");
        }
        if (!BandId.JUMP_ARRIVAL_TO_MAJOR_HUB.name().equals(values.get(3))) {
            return null;
        }
        StarSystemId systemId = new StarSystemId(parsePositiveLong(values.get(0), row, "systemId"));
        String anchorId = requireText(values.get(1), "fromId");
        double referenceDistanceM = parsePositiveDouble(values.get(4), row, "distanceM");
        double civilianRoutineTravelTimeMaxS = parsePositiveDouble(
                values.get(9), row, "civilianRoutineTravelTimeMaxS");
        double militaryResponseTimeMaxS = parsePositiveDouble(
                values.get(11), row, "militaryResponseTimeMaxS");
        return new LocalApproachCalibration(
                new ApproachKey(systemId, anchorId),
                referenceDistanceM,
                civilianRoutineTravelTimeMaxS,
                militaryResponseTimeMaxS,
                requireText(values.get(16), "sourceProfileVersion"));
    }

    private record PersistedEdge(
            EdgeKey key,
            PersistedEndpoint firstEndpoint,
            PersistedEndpoint secondEndpoint) {
        private PersistedEdge {
            Objects.requireNonNull(key, "key");
            Objects.requireNonNull(firstEndpoint, "firstEndpoint");
            Objects.requireNonNull(secondEndpoint, "secondEndpoint");
        }

        PersistedEndpoint endpoint(StarSystemId destination) {
            if (firstEndpoint.systemId().equals(destination)) {
                return firstEndpoint;
            }
            if (secondEndpoint.systemId().equals(destination)) {
                return secondEndpoint;
            }
            throw new IllegalArgumentException("destination is not part of persisted edge");
        }
    }

    private record PersistedEndpoint(
            StarSystemId systemId,
            String anchorId,
            LocalPhysicalPosition position,
            double arrivalVelocityMps,
            String localInfrastructureVersion,
            String jumpArrivalCalibrationVersion) { }

    private record LocalApproachCalibration(
            ApproachKey key,
            double referenceDistanceM,
            double civilianRoutineTravelTimeMaxS,
            double militaryResponseTimeMaxS,
            String sourceProfileVersion) {
        private LocalApproachCalibration {
            Objects.requireNonNull(key, "key");
            requirePositive(referenceDistanceM, "referenceDistanceM");
            requirePositive(civilianRoutineTravelTimeMaxS, "civilianRoutineTravelTimeMaxS");
            requirePositive(militaryResponseTimeMaxS, "militaryResponseTimeMaxS");
            requireText(sourceProfileVersion, "sourceProfileVersion");
        }
    }

    private record ApproachKey(StarSystemId systemId, String anchorId)
            implements Comparable<ApproachKey> {
        private ApproachKey {
            Objects.requireNonNull(systemId, "systemId");
            anchorId = requireText(anchorId, "anchorId");
        }

        @Override
        public int compareTo(ApproachKey other) {
            int result = systemId.compareTo(other.systemId);
            return result != 0 ? result : anchorId.compareTo(other.anchorId);
        }
    }

    private record EdgeKey(StarSystemId first, StarSystemId second)
            implements Comparable<EdgeKey> {
        private EdgeKey {
            Objects.requireNonNull(first, "first");
            Objects.requireNonNull(second, "second");
            if (first.equals(second)) {
                throw new IllegalArgumentException("ordinary edge endpoints must differ");
            }
            if (first.compareTo(second) > 0) {
                StarSystemId swap = first;
                first = second;
                second = swap;
            }
        }

        @Override
        public int compareTo(EdgeKey other) {
            int result = first.compareTo(other.first);
            return result != 0 ? result : second.compareTo(other.second);
        }
    }

    private static float exactFloat(double value, String field) {
        float projection = (float) value;
        if (!Float.isFinite(projection)) {
            throw new IllegalArgumentException(field + " is outside legacy projection range");
        }
        return projection;
    }

    private static long secondsToTicks(double seconds, float fixedStepSeconds) {
        requirePositive(seconds, "travelSeconds");
        requirePositiveFixedStep(fixedStepSeconds);
        double ticks = StrictMath.ceil(seconds / fixedStepSeconds);
        if (!Double.isFinite(ticks) || ticks > Long.MAX_VALUE) {
            throw new IllegalArgumentException("generated-world local FTL approach duration is not representable");
        }
        return Math.max(1L, (long) ticks);
    }

    private static long parsePositiveLong(String value, CanonicalRow row, String field) {
        long parsed = parseLong(value, row, field);
        if (parsed <= 0L) {
            throw malformed(row, field + " must be positive");
        }
        return parsed;
    }

    private static long parseLong(String value, CanonicalRow row, String field) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            throw malformed(row, field + " is invalid", exception);
        }
    }

    private static double parsePositiveDouble(String value, CanonicalRow row, String field) {
        double parsed = parseDouble(value, row, field);
        if (parsed <= 0d) {
            throw malformed(row, field + " must be positive");
        }
        return parsed;
    }

    private static double parseDouble(String value, CanonicalRow row, String field) {
        try {
            double parsed = Double.parseDouble(value);
            if (!Double.isFinite(parsed)) {
                throw new NumberFormatException("non-finite");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw malformed(row, field + " is invalid", exception);
        }
    }

    private static void requireNonNegativeTick(long value, String field) {
        if (value < 0L) {
            throw new IllegalArgumentException(field + " must be non-negative");
        }
    }

    private static void requirePositiveFixedStep(float value) {
        if (!Float.isFinite(value) || value <= 0f) {
            throw new IllegalArgumentException("fixedStepSeconds must be positive and finite");
        }
    }

    private static void requirePositive(double value, String field) {
        if (!Double.isFinite(value) || value <= 0d) {
            throw new IllegalArgumentException(field + " must be positive and finite");
        }
    }

    private static IllegalArgumentException malformed(CanonicalRow row, String message) {
        return new IllegalArgumentException(
                "malformed " + row.domain() + ':' + row.stableId() + ": " + message);
    }

    private static IllegalArgumentException malformed(
            CanonicalRow row,
            String message,
            RuntimeException cause) {
        return new IllegalArgumentException(
                "malformed " + row.domain() + ':' + row.stableId() + ": " + message,
                cause);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must be non-blank");
        }
        return value.strip();
    }
}
