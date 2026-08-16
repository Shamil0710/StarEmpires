package com.spacesim.player;

import com.spacesim.world.GalaxyTopology;
import com.spacesim.world.StarSystemId;

import java.util.List;
import java.util.Objects;

/**
 * Pure presentation model for choosing one immediate inter-system jump destination.
 *
 * <p>The model never plans or executes a long-range teleport. It exposes only the immutable,
 * deterministic direct neighbors already present in {@link GalaxyTopology}. Authoritative travel
 * remains in {@link PlayerRuntime} / world jump services, which independently reject non-neighbor
 * destinations.</p>
 */
public final class PlayerJumpNavigationModel {
    private PlayerJumpNavigationModel() {
        throw new AssertionError("PlayerJumpNavigationModel does not create instances");
    }

    /**
     * Returns direct jump neighbors in the topology's deterministic StarSystemId order.
     *
     * @param topology authoritative galaxy topology
     * @param currentSystemId current system
     * @return immutable direct-neighbor list; empty when the current system has no jump edge
     */
    public static List<StarSystemId> neighbors(GalaxyTopology topology, StarSystemId currentSystemId) {
        GalaxyTopology checkedTopology = Objects.requireNonNull(topology, "GalaxyTopology not set");
        StarSystemId current = Objects.requireNonNull(currentSystemId, "Current StarSystemId not set");
        if (checkedTopology.findSystem(current).isEmpty()) {
            throw new IllegalArgumentException("Current StarSystemId is not present in topology: " + current);
        }
        return checkedTopology.neighbors(current);
    }

    /**
     * Normalizes a UI selection index against only the current system's direct neighbors.
     *
     * @param topology authoritative galaxy topology
     * @param currentSystemId current system
     * @param requestedIndex arbitrary current/cycled UI index
     * @return normalized index, or {@code -1} when there are no direct neighbors
     */
    public static int normalizeSelectionIndex(
            GalaxyTopology topology,
            StarSystemId currentSystemId,
            int requestedIndex) {
        List<StarSystemId> neighbors = neighbors(topology, currentSystemId);
        return neighbors.isEmpty() ? -1 : Math.floorMod(requestedIndex, neighbors.size());
    }

    /**
     * Resolves the selected immediate jump destination.
     *
     * @param topology authoritative galaxy topology
     * @param currentSystemId current system
     * @param requestedIndex arbitrary current/cycled UI index
     * @return one directly connected destination, or {@code null} when no jump edge exists
     */
    public static StarSystemId selectedDestination(
            GalaxyTopology topology,
            StarSystemId currentSystemId,
            int requestedIndex) {
        List<StarSystemId> neighbors = neighbors(topology, currentSystemId);
        if (neighbors.isEmpty()) {
            return null;
        }
        return neighbors.get(Math.floorMod(requestedIndex, neighbors.size()));
    }
}
