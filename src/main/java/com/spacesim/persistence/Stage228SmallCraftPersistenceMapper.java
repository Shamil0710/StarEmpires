package com.spacesim.persistence;

import com.spacesim.components.EngineeringComponent;
import com.spacesim.world.SmallCraftRegistry;
import com.spacesim.world.SmallCraftState;

import java.util.List;
import java.util.Objects;

/** Maps the M22.8A runtime registry to and from deterministic persistent value state. */
public final class Stage228SmallCraftPersistenceMapper {
    private Stage228SmallCraftPersistenceMapper() {
        throw new AssertionError("Utility class");
    }

    /**
     * Captures the exact individual material state of every registered small craft.
     *
     * @param registry runtime identity registry
     * @return deterministic persistent sidecar
     */
    public static Stage228SmallCraftPersistentState capture(SmallCraftRegistry registry) {
        SmallCraftRegistry checked = Objects.requireNonNull(registry, "registry");
        List<Stage228SmallCraftPersistentState.CraftState> rows = checked.snapshot().stream()
                .map(Stage228SmallCraftPersistenceMapper::captureCraft)
                .toList();
        return new Stage228SmallCraftPersistentState(
                Stage228SmallCraftPersistentState.CURRENT_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_RUNTIME_VERSION,
                Stage228SmallCraftPersistentState.CURRENT_SEMANTIC_CONTRACT,
                checked.nextIdValue(),
                rows);
    }

    /**
     * Restores an independent registry without manufacturing or replenishing any craft.
     *
     * @param state validated M22.8A sidecar
     * @return independent runtime registry
     */
    public static SmallCraftRegistry restore(Stage228SmallCraftPersistentState state) {
        Stage228SmallCraftPersistentState checked = Objects.requireNonNull(state, "state");
        List<SmallCraftState> rows = checked.craft().stream()
                .map(Stage228SmallCraftPersistenceMapper::restoreCraft)
                .toList();
        return SmallCraftRegistry.restore(checked.nextCraftId(), rows);
    }

    private static Stage228SmallCraftPersistentState.CraftState captureCraft(SmallCraftState state) {
        EngineeringComponent engineering = new EngineeringComponent(
                state.fit(), state.runtimeState(), state.instanceState());
        return new Stage228SmallCraftPersistentState.CraftState(
                state.id(),
                state.stableFactionId(),
                state.designId(),
                Objects.requireNonNull(
                        EngineeringStatePersistenceMapper.capture(engineering),
                        "captured engineering state"));
    }

    private static SmallCraftState restoreCraft(Stage228SmallCraftPersistentState.CraftState row) {
        EngineeringComponent engineering = EngineeringStatePersistenceMapper.restore(row.engineering());
        return new SmallCraftState(
                row.id(),
                row.stableFactionId(),
                row.designId(),
                engineering.fit,
                engineering.runtimeState,
                engineering.instanceState);
    }
}
