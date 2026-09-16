package com.spacesim.world;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * M22.8 identity registry for individual physical small craft.
 *
 * <p>The registry owns identity continuity only. It neither manufactures craft nor replenishes any
 * physical resource. Future Stage-18 integration must authorize physical production before reserving
 * an ID and registering its supplied engineering state.</p>
 */
public final class SmallCraftRegistry {
    private final SmallCraftIdAllocator allocator;
    private final TreeMap<SmallCraftId, SmallCraftState> craftById = new TreeMap<>();

    private SmallCraftRegistry(SmallCraftIdAllocator allocator, Collection<SmallCraftState> states) {
        this.allocator = Objects.requireNonNull(allocator, "allocator");
        Objects.requireNonNull(states, "states");
        for (SmallCraftState state : states) {
            SmallCraftState checked = Objects.requireNonNull(state, "small-craft state");
            if (craftById.putIfAbsent(checked.id(), checked) != null) {
                throw new IllegalArgumentException("Duplicate small-craft ID: " + checked.id());
            }
        }
        long maxId = craftById.isEmpty() ? 0L : craftById.lastKey().value();
        if (allocator.nextValue() <= maxId) {
            throw new IllegalArgumentException("Small-craft allocator watermark must exceed every existing ID");
        }
    }

    /** @return new empty registry with no free or seeded craft */
    public static SmallCraftRegistry empty() {
        return new SmallCraftRegistry(SmallCraftIdAllocator.empty(), List.of());
    }

    /**
     * Restores exact persistent registry contents.
     *
     * @param nextId next unused allocator value
     * @param states individual physical craft states
     * @return independent restored registry
     */
    public static SmallCraftRegistry restore(long nextId, Collection<SmallCraftState> states) {
        return new SmallCraftRegistry(SmallCraftIdAllocator.restore(nextId), states);
    }

    /**
     * Reserves one stable identity after an external physical-production authority has completed a craft.
     *
     * <p>This method creates no craft state and grants no material. The returned ID must be paired with
     * the exact physically produced engineering state through {@link #registerProducedCraft(SmallCraftState)}.</p>
     *
     * @return newly reserved identity
     */
    public SmallCraftId reserveIdentityForCompletedProduction() {
        return allocator.allocate();
    }

    /**
     * Registers an externally authorized completed physical craft.
     *
     * @param state exact individual craft state using a previously reserved ID
     */
    public void registerProducedCraft(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        if (checked.id().value() >= allocator.nextValue()) {
            throw new IllegalArgumentException("Small-craft ID was not reserved by this registry");
        }
        if (craftById.putIfAbsent(checked.id(), checked) != null) {
            throw new IllegalArgumentException("Small-craft ID already registered: " + checked.id());
        }
    }

    /**
     * Replaces physical state for an existing craft without changing its identity or ownership/design identity.
     *
     * @param state updated physical state
     */
    public void replacePhysicalState(SmallCraftState state) {
        SmallCraftState checked = Objects.requireNonNull(state, "state");
        SmallCraftState current = craftById.get(checked.id());
        if (current == null) {
            throw new IllegalArgumentException("Unknown small-craft ID: " + checked.id());
        }
        if (!current.stableFactionId().equals(checked.stableFactionId())
                || !current.designId().equals(checked.designId())) {
            throw new IllegalArgumentException("Physical-state replacement cannot rewrite craft identity metadata");
        }
        craftById.put(checked.id(), checked);
    }

    /**
     * @param id stable craft identity
     * @return current individual craft state when present
     */
    public Optional<SmallCraftState> find(SmallCraftId id) {
        return Optional.ofNullable(craftById.get(Objects.requireNonNull(id, "id")));
    }

    /** @return immutable deterministic ascending-ID snapshot */
    public List<SmallCraftState> snapshot() {
        return List.copyOf(new ArrayList<>(craftById.values()));
    }

    /** @return number of individually registered physical craft */
    public int size() {
        return craftById.size();
    }

    /** @return exact next unused identity watermark for persistence */
    public long nextIdValue() {
        return allocator.nextValue();
    }
}
