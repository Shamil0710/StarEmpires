package com.spacesim.world;

import com.spacesim.world.SmallCraftHangarCapacity.BayDefinition;
import com.spacesim.world.SmallCraftHangarCapacity.BayId;
import com.spacesim.world.SmallCraftHangarCapacity.CapacityStatus;
import com.spacesim.world.SmallCraftHangarCapacity.CraftFootprint;
import com.spacesim.world.SmallCraftHangarCapacity.OccupancyState;
import com.spacesim.world.SmallCraftHangarCapacity.Usage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * M22.8B deterministic occupancy registry for individual physical small craft.
 *
 * <p>The registry owns only which existing {@link SmallCraftId} occupies which physical bay and its
 * finite handling state. Craft engineering/resource state remains owned by {@link SmallCraftRegistry};
 * bay capacity remains a projection from real installed infrastructure. Launch/recovery clocks and
 * queues deliberately remain M22.8C.</p>
 */
public final class SmallCraftHangarRegistry {
    private final SmallCraftRegistry craftRegistry;
    private final TreeMap<SmallCraftId, Assignment> assignmentByCraft = new TreeMap<>();

    private SmallCraftHangarRegistry(
            SmallCraftRegistry craftRegistry,
            Collection<Assignment> assignments) {
        this.craftRegistry = Objects.requireNonNull(craftRegistry, "craftRegistry");
        Objects.requireNonNull(assignments, "assignments");
        TreeMap<BayId, SmallCraftHangarCapacity.HostKind> hostKindByBay = new TreeMap<>();
        for (Assignment assignment : assignments) {
            Assignment checked = Objects.requireNonNull(assignment, "assignment");
            requireCraftExists(checked.craftId());
            SmallCraftHangarCapacity.HostKind previousHostKind =
                    hostKindByBay.putIfAbsent(checked.bayId(), checked.hostKind());
            if (previousHostKind != null && previousHostKind != checked.hostKind()) {
                throw new IllegalArgumentException(
                        "Persisted bay has conflicting host families: " + checked.bayId());
            }
            if (assignmentByCraft.putIfAbsent(checked.craftId(), checked) != null) {
                throw new IllegalArgumentException(
                        "Duplicate hangar assignment for craft " + checked.craftId());
            }
        }
    }

    /**
     * Creates an empty non-granting occupancy registry.
     *
     * @param craftRegistry authoritative individual-craft registry
     * @return empty hangar registry
     */
    public static SmallCraftHangarRegistry empty(SmallCraftRegistry craftRegistry) {
        return new SmallCraftHangarRegistry(
                Objects.requireNonNull(craftRegistry, "craftRegistry"), List.of());
    }

    /**
     * Restores exact persisted occupancy without inventing craft or enforcing pristine capacity.
     *
     * <p>Over-capacity state may be lawful after physical bay damage, so restore validates identity
     * continuity but never deletes or relocates craft merely because a later bay projection is
     * degraded.</p>
     *
     * @param craftRegistry restored individual-craft authority
     * @param assignments persisted physical occupancy
     * @return restored hangar registry
     */
    public static SmallCraftHangarRegistry restore(
            SmallCraftRegistry craftRegistry,
            Collection<Assignment> assignments) {
        return new SmallCraftHangarRegistry(
                Objects.requireNonNull(craftRegistry, "craftRegistry"), assignments);
    }

    /**
     * Places one existing physical craft into a compatible bay.
     *
     * @param craftId existing individual craft identity
     * @param bay current physical bay capacity
     * @param state initial finite occupancy state
     */
    void assign(SmallCraftId craftId, BayDefinition bay, OccupancyState state) {
        SmallCraftId checkedId = Objects.requireNonNull(craftId, "craftId");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        Objects.requireNonNull(state, "state");
        requireCraftExists(checkedId);
        requireHostKindCompatible(checkedBay);
        if (assignmentByCraft.containsKey(checkedId)) {
            throw new IllegalArgumentException("Craft already occupies a bay: " + checkedId);
        }
        CraftFootprint footprint = craftRegistry.physicalFootprint(checkedId);
        Usage current = usage(checkedBay.id());
        if (!SmallCraftHangarCapacity.canAccept(checkedBay, current, footprint)) {
            throw new IllegalArgumentException(
                    "Craft does not fit current physical bay capacity: " + checkedId
                            + " -> " + checkedBay.id());
        }
        assignmentByCraft.put(
                checkedId,
                new Assignment(checkedId, checkedBay.id(), checkedBay.hostKind(), state));
    }

    /**
     * Changes only handling/readiness state; physical bay occupancy is retained.
     *
     * @param craftId assigned craft identity
     * @param state new finite occupancy state
     */
    void transition(SmallCraftId craftId, OccupancyState state) {
        SmallCraftId checkedId = Objects.requireNonNull(craftId, "craftId");
        Assignment current = requireAssignment(checkedId);
        assignmentByCraft.put(
                checkedId,
                new Assignment(
                        checkedId,
                        current.bayId(),
                        current.hostKind(),
                        Objects.requireNonNull(state, "state")));
    }

    /**
     * Releases one craft from physical bay occupancy.
     *
     * <p>M22.8C/E decide when launch/recovery semantics make release lawful. This B-layer operation
     * only changes the physical occupancy record and never destroys or mutates the craft.</p>
     *
     * @param craftId assigned craft identity
     * @return released assignment
     */
    Assignment release(SmallCraftId craftId) {
        Assignment removed = assignmentByCraft.remove(Objects.requireNonNull(craftId, "craftId"));
        if (removed == null) {
            throw new IllegalArgumentException("Craft is not assigned to a bay: " + craftId);
        }
        return removed;
    }

    /**
     * Calculates current real mass/envelope usage for one bay.
     *
     * @param bayId stable physical bay identity
     * @return aggregate usage of all individually assigned craft
     */
    public Usage usage(BayId bayId) {
        BayId checkedBayId = Objects.requireNonNull(bayId, "bayId");
        Usage usage = Usage.empty();
        for (Assignment assignment : assignmentByCraft.values()) {
            if (assignment.bayId().equals(checkedBayId)) {
                usage = usage.plus(craftRegistry.physicalFootprint(assignment.craftId()));
            }
        }
        return usage;
    }

    /**
     * Projects whether existing occupancy still fits a bay's current damaged capacity.
     *
     * @param bay current physical bay projection
     * @return capacity health without deleting or relocating existing craft
     */
    public CapacityStatus capacityStatus(BayDefinition bay) {
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        requireHostKindCompatible(checkedBay);
        return SmallCraftHangarCapacity.status(checkedBay, usage(checkedBay.id()));
    }

    /**
     * Checks whether another existing craft can enter one current bay.
     *
     * @param craftId candidate existing craft
     * @param bay current physical bay projection
     * @return whether current envelope, mass and volume capacity permit assignment
     */
    public boolean canAccept(SmallCraftId craftId, BayDefinition bay) {
        SmallCraftId checkedId = Objects.requireNonNull(craftId, "craftId");
        BayDefinition checkedBay = Objects.requireNonNull(bay, "bay");
        requireCraftExists(checkedId);
        requireHostKindCompatible(checkedBay);
        if (assignmentByCraft.containsKey(checkedId)) {
            return false;
        }
        return SmallCraftHangarCapacity.canAccept(
                checkedBay,
                usage(checkedBay.id()),
                craftRegistry.physicalFootprint(checkedId));
    }

    /**
     * @param craftId individual craft identity
     * @return current physical assignment when embarked
     */
    public Optional<Assignment> find(SmallCraftId craftId) {
        return Optional.ofNullable(
                assignmentByCraft.get(Objects.requireNonNull(craftId, "craftId")));
    }

    /** @return deterministic immutable assignment snapshot ordered by craft ID */
    public List<Assignment> snapshot() {
        return List.copyOf(new ArrayList<>(assignmentByCraft.values()));
    }

    /** @return number of individually embarked physical craft */
    public int size() {
        return assignmentByCraft.size();
    }

    /** @return immutable diagnostic count by finite occupancy state */
    public Map<OccupancyState, Long> occupancyCounts() {
        TreeMap<OccupancyState, Long> result = new TreeMap<>();
        for (Assignment assignment : assignmentByCraft.values()) {
            result.merge(assignment.state(), 1L, Long::sum);
        }
        return Collections.unmodifiableMap(result);
    }

    private void requireHostKindCompatible(BayDefinition bay) {
        for (Assignment assignment : assignmentByCraft.values()) {
            if (assignment.bayId().equals(bay.id())
                    && assignment.hostKind() != bay.hostKind()) {
                throw new IllegalArgumentException(
                        "Bay host kind disagrees with persisted occupancy: " + bay.id());
            }
        }
    }

    private Assignment requireAssignment(SmallCraftId craftId) {
        Assignment current = assignmentByCraft.get(craftId);
        if (current == null) {
            throw new IllegalArgumentException("Craft is not assigned to a bay: " + craftId);
        }
        return current;
    }

    private void requireCraftExists(SmallCraftId craftId) {
        if (craftRegistry.find(Objects.requireNonNull(craftId, "craftId")).isEmpty()) {
            throw new IllegalArgumentException(
                    "Hangar assignment references unknown craft: " + craftId);
        }
    }

    /**
     * Persistent physical occupancy of one individual craft.
     *
     * @param craftId stable craft identity
     * @param bayId stable host-local physical bay
     * @param hostKind physical host family
     * @param state finite occupancy/handling state
     */
    public record Assignment(
            SmallCraftId craftId,
            BayId bayId,
            SmallCraftHangarCapacity.HostKind hostKind,
            OccupancyState state) implements Comparable<Assignment> {
        /** Validates one assignment.
         * @param craftId stable craft identity
         * @param bayId stable physical bay identity
         * @param hostKind physical host family
         * @param state finite occupancy/handling state
         */
        public Assignment {
            Objects.requireNonNull(craftId, "craftId");
            Objects.requireNonNull(bayId, "bayId");
            Objects.requireNonNull(hostKind, "hostKind");
            Objects.requireNonNull(state, "state");
        }

        /** {@inheritDoc} */
        @Override
        public int compareTo(Assignment other) {
            return craftId.compareTo(Objects.requireNonNull(other, "other").craftId);
        }
    }
}
