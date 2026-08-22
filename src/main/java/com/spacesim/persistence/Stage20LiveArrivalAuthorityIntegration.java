package com.spacesim.persistence;

import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.CanonicalRow;
import com.spacesim.persistence.Stage20GeneratedCampaignPersistentState.OpenRuntimeBoundary;
import com.spacesim.simulation.Stage20MaterializationService;
import com.spacesim.world.FleetArrivalAuthority;
import com.spacesim.world.FleetId;
import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.JumpConnection;
import com.spacesim.world.LocalPhysicalKinematics;
import com.spacesim.world.LocalPhysicalPosition;
import com.spacesim.world.StarSystemId;
import com.spacesim.world.WorldSimulation;

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
 * <p>The adapter parses only the saved Stage-20K canonical rows. It does not regenerate edge
 * geometry, alter the topology, bypass intermediate systems, change fitted jump constraints or
 * grant discovery. The legacy float transform receives a non-authoritative local projection solely
 * so the existing Ashley representation can remain renderable; exact physical position and
 * velocity are installed unchanged in {@link Stage20MaterializationService}.</p>
 */
@SuppressWarnings("doclint:missing")
public final class Stage20LiveArrivalAuthorityIntegration implements FleetArrivalAuthority {
    /** Stable live-arrival integration contract version. */
    public static final String CURRENT_VERSION = "stage20_5.live-arrival-authority.v1";
    /** Current persisted scalar arrival velocity uses this stable local +X orientation convention. */
    public static final String VELOCITY_ORIENTATION_CONVENTION =
            "stage20d.persisted-arrival-speed.local-positive-x.v1";

    private static final String JUMP_EDGE_DOMAIN = "JUMP_EDGE";
    private static final int MINIMUM_JUMP_EDGE_VALUE_COUNT = 26;

    private final long rootSeed;
    private final String generatorVersion;
    private final String worldFingerprint;
    private final Map<EdgeKey, PersistedEdge> edges;
    private final Map<StarSystemId, Stage20MaterializationService> materializationBySystem;
    private final Set<FleetId> liveDepartures = new HashSet<>();
    private boolean bound;

    private Stage20LiveArrivalAuthorityIntegration(
            long rootSeed,
            String generatorVersion,
            String worldFingerprint,
            Map<EdgeKey, PersistedEdge> edges,
            Map<StarSystemId, Stage20MaterializationService> materializationBySystem) {
        this.rootSeed = rootSeed;
        this.generatorVersion = requireText(generatorVersion, "generatorVersion");
        this.worldFingerprint = requireText(worldFingerprint, "worldFingerprint");
        this.edges = Map.copyOf(edges);
        this.materializationBySystem = Map.copyOf(materializationBySystem);
        if (this.edges.isEmpty() || this.materializationBySystem.isEmpty()) {
            throw new IllegalArgumentException("live arrival integration requires edges and local runtimes");
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
        for (CanonicalRow row : state.materializedWorld().worldRows()) {
            if (!JUMP_EDGE_DOMAIN.equals(row.domain())) {
                continue;
            }
            PersistedEdge edge = parseEdge(row);
            if (edges.putIfAbsent(edge.key(), edge) != null) {
                throw malformed(row, "duplicate ordinary edge endpoints");
            }
        }
        return new Stage20LiveArrivalAuthorityIntegration(
                state.generationIdentity().worldSeed(),
                state.generationIdentity().generatorVersion(),
                state.materializedWorld().worldFingerprint(),
                edges,
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
        return new ResolvedArrival(
                from,
                to,
                endpoint.anchorId(),
                exact,
                exactFloat(endpoint.position().offsetXM(), "legacyProjectionX"),
                exactFloat(endpoint.position().offsetYM(), "legacyProjectionY"));
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
