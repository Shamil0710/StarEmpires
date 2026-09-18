package com.spacesim.persistence;

import com.spacesim.world.SmallCraftHangarCapacity.HostKind;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned M22.8B persistence sidecar for exact individual hangar occupancy.
 *
 * <p>Capacity definitions are deliberately not duplicated here: ship capacity resolves from the
 * installed production hangar module and its current physical damage, while station capacity is
 * supplied by its physical infrastructure. This sidecar stores only durable craft-to-bay occupancy
 * and handling state.</p>
 *
 * @param schemaVersion exact sidecar schema
 * @param runtimeVersion exact runtime contract
 * @param semanticContract explicit physical-occupancy contract
 * @param assignments individual craft-to-bay assignments
 */
public record Stage228HangarPersistentState(
        int schemaVersion,
        String runtimeVersion,
        String semanticContract,
        List<AssignmentState> assignments) {

    /** Current M22.8B hangar sidecar schema. */
    public static final int CURRENT_VERSION = 1;
    /** Current M22.8B runtime identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8b.hangar-occupancy.v1";
    /** Stable semantic contract for persistent individual physical occupancy. */
    public static final String CURRENT_SEMANTIC_CONTRACT =
            "individual-craft|physical-bay|finite-occupancy|no-teleport|no-virtual-wing";

    /**
     * One exact persistent physical-bay assignment.
     *
     * @param craftId stable individual craft identity
     * @param hostStableId persistent bay-host identity
     * @param bayStableId host-local bay identity
     * @param hostKind physical host family
     * @param occupancyState finite current occupancy state
     */
    public record AssignmentState(
            SmallCraftId craftId,
            String hostStableId,
            String bayStableId,
            HostKind hostKind,
            OccupancyState occupancyState) implements Comparable<AssignmentState> {
        /** Validates one persistent assignment.
         * @param craftId stable craft identity
         * @param hostStableId persistent host identity
         * @param bayStableId host-local bay identity
         * @param hostKind physical host family
         * @param occupancyState finite occupancy state
         */
        public AssignmentState {
            Objects.requireNonNull(craftId, "craftId");
            hostStableId = requireText(hostStableId, "hostStableId");
            bayStableId = requireText(bayStableId, "bayStableId");
            Objects.requireNonNull(hostKind, "hostKind");
            Objects.requireNonNull(occupancyState, "occupancyState");
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(AssignmentState other) {
            return craftId.compareTo(Objects.requireNonNull(other, "other").craftId);
        }
    }

    /** Validates version, uniqueness and deterministic ordering.
     * @param schemaVersion exact sidecar schema
     * @param runtimeVersion exact runtime contract
     * @param semanticContract explicit physical-occupancy contract
     * @param assignments individual assignments
     */
    public Stage228HangarPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8B hangar schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8B hangar runtime: " + runtimeVersion);
        }
        semanticContract = requireText(semanticContract, "semanticContract");
        if (!CURRENT_SEMANTIC_CONTRACT.equals(semanticContract)) {
            throw new IllegalArgumentException(
                    "Unsupported M22.8B hangar semantic contract: " + semanticContract);
        }
        Objects.requireNonNull(assignments, "assignments");
        ArrayList<AssignmentState> copy = new ArrayList<>(assignments.size());
        Set<SmallCraftId> craftIds = new HashSet<>();
        for (AssignmentState assignment : assignments) {
            AssignmentState checked = Objects.requireNonNull(assignment, "assignment");
            if (!craftIds.add(checked.craftId())) {
                throw new IllegalArgumentException(
                        "Duplicate persisted hangar craft: " + checked.craftId());
            }
            copy.add(checked);
        }
        copy.sort(Comparator.naturalOrder());
        assignments = List.copyOf(copy);
    }

    /** @return non-granting empty hangar state */
    public static Stage228HangarPersistentState empty() {
        return new Stage228HangarPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                CURRENT_SEMANTIC_CONTRACT,
                List.of());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
