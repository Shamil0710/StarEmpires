package com.spacesim.world;

import com.spacesim.ship.ShipEngineeringRuntime.JumpPlan;
import com.spacesim.world.Stage20JumpEdgeState.ArrivalEndpoint;
import com.spacesim.world.Stage20JumpEdgeState.OperationalAccessState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Exact-coverage immutable Stage-20D physical metadata index over one {@link GalaxyTopology}.
 *
 * <p>The catalog does not replace topology. It augments every existing ordinary
 * {@link JumpConnection} with physical/access/arrival metadata and rejects missing or extra rows so
 * an unknown edge cannot accidentally become an execution shortcut.</p>
 */
public final class Stage20JumpEdgeCatalog {
    /** Current exact-coverage catalog version. */
    public static final String CURRENT_VERSION = "stage20d.jump-edge-catalog.v1";

    private final GalaxyTopology topology;
    private final Map<JumpConnection, Stage20JumpEdgeState> byConnection;
    private final Map<String, Stage20JumpEdgeState> byId;

    /**
     * Creates an exact one-to-one physical metadata catalog for the supplied topology.
     *
     * @param topology immutable ordinary graph authority
     * @param edgeStates one physical row for every ordinary edge and no extras
     */
    public Stage20JumpEdgeCatalog(GalaxyTopology topology, List<Stage20JumpEdgeState> edgeStates) {
        this.topology = Objects.requireNonNull(topology, "topology");
        Objects.requireNonNull(edgeStates, "edgeStates");

        TreeMap<JumpConnection, Stage20JumpEdgeState> connections = new TreeMap<>();
        TreeMap<String, Stage20JumpEdgeState> ids = new TreeMap<>();
        for (Stage20JumpEdgeState state : edgeStates) {
            Stage20JumpEdgeState checked = Objects.requireNonNull(state, "edge state");
            if (!Stage20JumpEdgeState.CURRENT_VERSION.equals(checked.version())) {
                throw new IllegalArgumentException("Unsupported Stage20 jump-edge state version: " + checked.version());
            }
            if (!topology.connections().contains(checked.connection())) {
                throw new IllegalArgumentException("Jump-edge metadata contains connection outside topology: "
                        + checked.connection());
            }
            if (connections.putIfAbsent(checked.connection(), checked) != null) {
                throw new IllegalArgumentException("Duplicate physical jump-edge row: " + checked.connection());
            }
            if (ids.putIfAbsent(checked.edgeId(), checked) != null) {
                throw new IllegalArgumentException("Duplicate stable physical jump-edge id: " + checked.edgeId());
            }
        }
        if (connections.size() != topology.connections().size()) {
            ArrayList<JumpConnection> missing = new ArrayList<>();
            for (JumpConnection connection : topology.connections()) {
                if (!connections.containsKey(connection)) {
                    missing.add(connection);
                }
            }
            throw new IllegalArgumentException("Stage20 jump-edge metadata must exactly cover topology; missing="
                    + missing);
        }
        this.byConnection = Collections.unmodifiableMap(new LinkedHashMap<>(connections));
        this.byId = Collections.unmodifiableMap(new LinkedHashMap<>(ids));
    }

    /** @return immutable topology augmented by this catalog */
    public GalaxyTopology topology() {
        return topology;
    }

    /** @return deterministic immutable edge rows */
    public List<Stage20JumpEdgeState> edgeStates() {
        return List.copyOf(byConnection.values());
    }

    /**
     * Finds physical metadata for an existing connection.
     *
     * @param connection canonical ordinary connection
     * @return matching physical row when present
     */
    public Optional<Stage20JumpEdgeState> find(JumpConnection connection) {
        return Optional.ofNullable(byConnection.get(Objects.requireNonNull(connection, "connection")));
    }

    /**
     * Requires physical metadata for an existing connection.
     *
     * @param connection canonical ordinary connection
     * @return exact physical row
     */
    public Stage20JumpEdgeState require(JumpConnection connection) {
        return find(connection).orElseThrow(() -> new IllegalArgumentException(
                "No Stage20 physical metadata for topology connection " + connection));
    }

    /**
     * Finds one row by stable edge ID.
     *
     * @param edgeId stable generated ID
     * @return matching physical row when present
     */
    public Optional<Stage20JumpEdgeState> findById(String edgeId) {
        if (edgeId == null || edgeId.isBlank()) {
            throw new IllegalArgumentException("edgeId must not be blank");
        }
        return Optional.ofNullable(byId.get(edgeId));
    }

    /**
     * Reports whether an ordinary topology edge is currently physically traversable.
     *
     * @param connection topology connection
     * @return true only when physical world state is open
     */
    public boolean isPhysicallyTraversable(JumpConnection connection) {
        return require(connection).operationalAccessState() == OperationalAccessState.OPEN;
    }

    /**
     * Computes fitted transit for one physical edge from the authoritative current jump plan.
     *
     * @param connection topology edge
     * @param fittedPlan current executable fitted FTL planning snapshot
     * @return edge transit seconds
     */
    public double transitSeconds(JumpConnection connection, JumpPlan fittedPlan) {
        return require(connection).transitParameters().transitSeconds(
                Objects.requireNonNull(fittedPlan, "fittedPlan"));
    }

    /**
     * Returns the destination-local physical arrival endpoint for traversing one connection.
     *
     * @param connection topology edge
     * @param destination direct-neighbor destination
     * @return generated authoritative destination-local endpoint
     */
    public ArrivalEndpoint arrivalIn(JumpConnection connection, StarSystemId destination) {
        return require(connection).arrivalIn(Objects.requireNonNull(destination, "destination"));
    }

    /**
     * Produces a new exact-coverage catalog with one physical edge access state changed.
     *
     * <p>This helper represents world-global physical closure/opening only. Faction law remains a
     * separate Stage-17 authorization layer.</p>
     *
     * @param connection edge to change
     * @param accessState new physical state
     * @return immutable updated catalog
     */
    public Stage20JumpEdgeCatalog withOperationalAccess(
            JumpConnection connection,
            OperationalAccessState accessState) {
        Objects.requireNonNull(connection, "connection");
        Objects.requireNonNull(accessState, "accessState");
        ArrayList<Stage20JumpEdgeState> rows = new ArrayList<>(edgeStates());
        boolean updated = false;
        for (int index = 0; index < rows.size(); index++) {
            Stage20JumpEdgeState state = rows.get(index);
            if (!state.connection().equals(connection)) {
                continue;
            }
            rows.set(index, state.withOperationalAccessState(accessState));
            updated = true;
            break;
        }
        if (!updated) {
            throw new IllegalArgumentException("Cannot update non-topology Stage20 edge " + connection);
        }
        return new Stage20JumpEdgeCatalog(topology, rows);
    }
}
