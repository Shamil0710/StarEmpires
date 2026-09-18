package com.spacesim.persistence;

import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarRegistry;
import com.spacesim.world.SmallCraftHangarRegistry.Assignment;
import com.spacesim.world.SmallCraftRegistry;

import java.util.List;
import java.util.Objects;

/** Maps the M22.8B physical hangar occupancy registry to deterministic persistent value state. */
public final class Stage228HangarPersistenceMapper {
    private Stage228HangarPersistenceMapper() {
        throw new AssertionError("utility class");
    }

    /**
     * Captures exact individual craft-to-bay occupancy.
     *
     * @param registry runtime hangar occupancy authority
     * @return deterministic persistent sidecar
     */
    public static Stage228HangarPersistentState capture(SmallCraftHangarRegistry registry) {
        SmallCraftHangarRegistry checked = Objects.requireNonNull(registry, "registry");
        List<Stage228HangarPersistentState.AssignmentState> assignments = checked.snapshot().stream()
                .map(value -> new Stage228HangarPersistentState.AssignmentState(
                        value.craftId(),
                        value.bayId().hostStableId(),
                        value.bayId().bayStableId(),
                        value.hostKind(),
                        value.state()))
                .toList();
        return new Stage228HangarPersistentState(
                Stage228HangarPersistentState.CURRENT_VERSION,
                Stage228HangarPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228HangarPersistentState.CURRENT_SEMANTIC_CONTRACT,
                assignments);
    }

    /**
     * Restores exact occupancy against already-restored craft identities.
     *
     * @param state validated hangar sidecar
     * @param craftRegistry authoritative restored individual craft registry
     * @return independent restored occupancy registry
     */
    public static SmallCraftHangarRegistry restore(
            Stage228HangarPersistentState state,
            SmallCraftRegistry craftRegistry) {
        Stage228HangarPersistentState checked = Objects.requireNonNull(state, "state");
        SmallCraftRegistry craft = Objects.requireNonNull(craftRegistry, "craftRegistry");
        List<Assignment> assignments = checked.assignments().stream()
                .map(value -> new Assignment(
                        value.craftId(),
                        new BayId(value.hostStableId(), value.bayStableId()),
                        value.hostKind(),
                        value.occupancyState()))
                .toList();
        return SmallCraftHangarRegistry.restore(craft, assignments);
    }


}
