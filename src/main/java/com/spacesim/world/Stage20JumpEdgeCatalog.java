package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20JumpEdgeState.ArrivalEndpoint;
import com.spacesim.world.Stage20JumpEdgeState.OperationalAccessState;

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
 * Immutable exact-coverage Stage-20D metadata catalog over an existing {@link GalaxyTopology}.
 *
 * <p>The catalog does not replace {@link GalaxyTopology}: every catalog row must correspond to
 * exactly one existing {@link JumpConnection}, and every topology connection must have exactly one
 * metadata row. This preserves the already-accepted neighbor graph while making physical edge state
 * explicit for routing, arrival and later persistence.</p>
 */
public final class Stage20JumpEdgeCatalog {
    /** Current Stage-20D catalog version. */
    public static final String CURRENT_VERSION = "stage20d.jump-edge-catalog.v1";

    private final String version;
    private final GalaxyTopology topology;
    private final List<Stage20JumpEdgeState> edges;
    private final Map<JumpConnection, Stage20JumpEdgeState> byConnection;
    private final Map<String, Stage20JumpEdgeState> byId;

    /**
     * Creates and validates one exact-coverage edge catalog.
     *
     * @param version stable catalog version
     * @param topology authoritative existing topology
     * @param edges one metadata row for every topology connection
     */
    public Stage20JumpEdgeCatalog(
            String version,
            GalaxyTopology topology,
            List<Stage20JumpEdgeState> edges) {
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("version must not be blank");
        }
        this.version = version;
        this.topology = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(edges, "edges");

        ArrayList<Stage20JumpEdgeState> copy = new ArrayList<>(edges);
        if (copy.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("edges cannot contain null");
        }
        copy.sort(Comparator.comparing(Stage20JumpEdgeState::connection));

        HashMap<JumpConnection, Stage20JumpEdgeState> connectionIndex = new HashMap<>();
        HashMap<String, Stage20JumpEdgeState> idIndex = new HashMap<>();
        for (Stage20JumpEdgeState edge : copy) {
            if (!Stage20JumpEdgeState.CURRENT_VERSION.equals(edge.version())) {
                throw new IllegalArgumentException("catalog requires current Stage-20D edge metadata version");
            }
            if (!topology.connections().contains(edge.connection())) {
                throw new IllegalArgumentException("metadata references non-topology edge: " + edge.connection());
            }
            if (connectionIndex.putIfAbsent(edge.connection(), edge) != null) {
                throw new IllegalArgumentException("duplicate metadata for topology edge: " + edge.connection());
            }
            if (idIndex.putIfAbsent(edge.edgeId(), edge) != null) {
                throw new IllegalArgumentException("duplicate stable edge ID: " + edge.edgeId());
            }
        }
        Set<JumpConnection> missing = new HashSet<>(topology.connections());
        missing.removeAll(connectionIndex.keySet());
        if (!missing.isEmpty() || connectionIndex.size() != topology.connections().size()) {
            throw new IllegalArgumentException("edge metadata must exactly cover topology connections; missing=" + missing);
        }

        this.edges = List.copyOf(copy);
        this.byConnection = Map.copyOf(connectionIndex);
        this.byId = Map.copyOf(idIndex);
    }

    /**
     * Returns the stable catalog version.
     *
     * @return stable catalog version
     */
    public String version() {
        return version;
    }

    /**
     * Returns the authoritative topology covered by this catalog.
     *
     * @return authoritative topology covered by this catalog
     */
    public GalaxyTopology topology() {
        return topology;
    }

    /**
     * Returns deterministic connection-ordered immutable metadata rows.
     *
     * @return deterministic connection-ordered immutable metadata rows
     */
    public List<Stage20JumpEdgeState> edges() {
        return edges;
    }

    /**
     * Finds metadata by canonical connection.
     *
     * @param connection topology connection
     * @return matching metadata or empty
     */
    public Optional<Stage20JumpEdgeState> find(JumpConnection connection) {
        return Optional.ofNullable(connection == null ? null : byConnection.get(connection));
    }

    /**
     * Requires metadata by canonical connection.
     *
     * @param connection topology connection
     * @return matching metadata
     */
    public Stage20JumpEdgeState require(JumpConnection connection) {
        JumpConnection checked = Objects.requireNonNull(connection, "connection");
        Stage20JumpEdgeState edge = byConnection.get(checked);
        if (edge == null) {
            throw new IllegalArgumentException("unknown topology edge: " + checked);
        }
        return edge;
    }

    /**
     * Finds metadata by stable edge ID.
     *
     * @param edgeId stable edge identity
     * @return matching metadata or empty
     */
    public Optional<Stage20JumpEdgeState> findById(String edgeId) {
        return Optional.ofNullable(edgeId == null ? null : byId.get(edgeId));
    }

    /**
     * Returns whether the edge is currently physically traversable.
     *
     * @param connection explicit topology edge
     * @return true only for an existing physically open edge
     */
    public boolean isPhysicallyTraversable(JumpConnection connection) {
        Stage20JumpEdgeState edge = require(connection);
        return edge.operationalAccessState() == OperationalAccessState.OPEN;
    }

    /**
     * Applies edge parameters to the live fitted edge-transit capability.
     *
     * @param connection explicit topology edge
     * @param fittedPlan current executable fitted jump plan
     * @return positive physical transit seconds for this edge
     */
    public double transitSeconds(JumpConnection connection, JumpPlan fittedPlan) {
        Stage20JumpEdgeState edge = require(connection);
        JumpPlan plan = Objects.requireNonNull(fittedPlan, "fittedPlan");
        if (!plan.allowed() || !Double.isFinite(plan.edgeTransitSeconds()) || plan.edgeTransitSeconds() <= 0d) {
            throw new IllegalArgumentException("edge transit requires an executable fitted jump plan");
        }
        double result = plan.edgeTransitSeconds() * edge.transitParameters().fittedTransitMultiplier();
        if (!Double.isFinite(result) || result <= 0d) {
            throw new IllegalStateException("edge transit parameter overflow for " + edge.edgeId());
        }
        return result;
    }

    /**
     * Returns destination-local physical arrival geometry for one edge.
     *
     * @param connection explicit topology edge
     * @param destination destination endpoint
     * @return authoritative Stage-20 physical arrival geometry
     */
    public ArrivalEndpoint arrivalIn(JumpConnection connection, StarSystemId destination) {
        return require(connection).arrivalIn(destination);
    }

    /**
     * Returns a new catalog with one edge's world-global physical availability updated.
     *
     * @param connection existing topology edge
     * @param state new physical access state
     * @return immutable exact-coverage catalog
     */
    public Stage20JumpEdgeCatalog withOperationalAccess(
            JumpConnection connection,
            OperationalAccessState state) {
        JumpConnection checked = Objects.requireNonNull(connection, "connection");
        require(checked);
        ArrayList<Stage20JumpEdgeState> updated = new ArrayList<>(edges.size());
        for (Stage20JumpEdgeState edge : edges) {
            updated.add(edge.connection().equals(checked)
                    ? edge.withOperationalAccess(Objects.requireNonNull(state, "state"))
                    : edge);
        }
        return new Stage20JumpEdgeCatalog(version, topology, updated);
    }
}
