package com.spacesim.simulation;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.spacesim.persistence.EntityId;
import com.spacesim.persistence.EntityRegistry;
import com.spacesim.persistence.EntityState;
import com.spacesim.persistence.EntityStateMapper;
import com.spacesim.world.LocalPhysicalKinematics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Reversible Stage-20 runtime materialization boundary for one local {@link SimulationSession}.
 *
 * <p>This service is deliberately separate from {@link EntityLifecycleService}: dematerialization
 * removes only the Ashley runtime representation and its live registry mapping. It does not
 * invalidate the persistent {@link EntityId}, consume inventory, repair damage, refill engineering
 * stores, relocate the physical body or allocate a replacement ID.</p>
 *
 * <p>Authoritative Stage-20 physical kinematics are retained in the accepted hierarchical/double
 * representation rather than being reconstructed from legacy global-float {@code TransformComponent}
 * values. This slice provides a lossless in-memory runtime round-trip and deterministic persistence
 * seams for both ECS and Stage-20 physical state.</p>
 */
public final class Stage20MaterializationService {
    /** Synchronous materialization completes within the calling simulation boundary. */
    public static final double SYNCHRONOUS_WAKE_LATENCY_SIMULATION_SECONDS = 0d;

    private final Engine engine;
    private final EntityRegistry registry;
    private final Map<EntityId, EntityState> dematerializedStates = new HashMap<>();
    private final Map<EntityId, LocalPhysicalKinematics> physicalStates = new HashMap<>();

    /**
     * Creates a materialization boundary over one existing local simulation runtime.
     *
     * @param engine Ashley engine owning current local runtime representations
     * @param registry live EntityId-to-Ashley registry tracking the same engine
     */
    public Stage20MaterializationService(Engine engine, EntityRegistry registry) {
        this.engine = Objects.requireNonNull(engine, "engine");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    /**
     * Creates the materialization service for an existing headless simulation session.
     *
     * @param session local authoritative simulation session
     * @return independent materialization service using the session's engine/registry
     */
    public static Stage20MaterializationService forSession(SimulationSession session) {
        SimulationSession checked = Objects.requireNonNull(session, "session");
        return new Stage20MaterializationService(checked.getEngine(), checked.getEntityRegistry());
    }

    /**
     * Registers accepted Stage-20 physical authority for one currently materialized entity.
     *
     * @param id stable persistent entity ID
     * @param physicalState authoritative local physical kinematics
     */
    public void registerPhysicalState(EntityId id, LocalPhysicalKinematics physicalState) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicalState, "physicalState");
        if (!registry.contains(checkedId)) {
            throw new IllegalStateException("Physical authority can only be registered for a live persistent entity: " + checkedId);
        }
        if (dematerializedStates.containsKey(checkedId)) {
            throw new IllegalStateException("Entity cannot be live and dematerialized simultaneously: " + checkedId);
        }
        LocalPhysicalKinematics previous = physicalStates.putIfAbsent(checkedId, physicalState);
        if (previous != null) {
            throw new IllegalStateException("Physical authority already registered for entity: " + checkedId);
        }
    }

    /**
     * Updates physical kinematics for a known Stage-20 entity in either live or dematerialized form.
     *
     * <p>This allows future STRATEGIC/ACTIVE_LOCAL representations to advance authoritative physical
     * state without materializing a full Ashley entity merely to move it.</p>
     *
     * @param id stable persistent entity ID
     * @param physicalState replacement authoritative kinematics
     */
    public void updatePhysicalState(EntityId id, LocalPhysicalKinematics physicalState) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        Objects.requireNonNull(physicalState, "physicalState");
        if (!physicalStates.containsKey(checkedId)) {
            throw new IllegalStateException("Stage-20 physical state is not registered: " + checkedId);
        }
        if (!registry.contains(checkedId) && !dematerializedStates.containsKey(checkedId)) {
            throw new IllegalStateException("Physical entity has no live or dematerialized persistent representation: " + checkedId);
        }
        physicalStates.put(checkedId, physicalState);
    }

    /**
     * Releases exact physical authority after the ordinary world fleet service has detached an
     * entity for inter-system transit.
     *
     * <p>The persistent entity continues to exist in the world-owned transit payload, but it no
     * longer belongs to this local session. The caller must install destination authority under the
     * freshly allocated destination-local {@link EntityId} when the same FleetId arrives.</p>
     *
     * @param id former origin-local persistent entity ID
     * @return exact released hierarchical/double kinematics
     */
    public LocalPhysicalKinematics releasePhysicalStateForWorldTransfer(EntityId id) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        if (registry.contains(checkedId) || dematerializedStates.containsKey(checkedId)) {
            throw new IllegalStateException(
                    "World-transfer physical release requires an already detached local entity: "
                            + checkedId);
        }
        LocalPhysicalKinematics released = physicalStates.remove(checkedId);
        if (released == null) {
            throw new IllegalStateException(
                    "World-transfer entity lacks Stage-20 physical authority: " + checkedId);
        }
        return released;
    }

    /**
     * Captures all supported persistent ECS state and removes only the runtime representation.
     *
     * @param id stable persistent entity ID
     * @return immutable snapshot retained while the entity is dematerialized
     */
    public DematerializedEntitySnapshot dematerialize(EntityId id) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        if (dematerializedStates.containsKey(checkedId)) {
            throw new IllegalStateException("Entity is already dematerialized: " + checkedId);
        }
        LocalPhysicalKinematics physical = physicalStates.get(checkedId);
        if (physical == null) {
            throw new IllegalStateException(
                    "Refusing Stage-20 dematerialization without authoritative physical kinematics: " + checkedId);
        }
        Entity live = registry.require(checkedId);
        EntityState snapshot = EntityStateMapper.capture(live);

        engine.removeEntity(live);
        if (registry.contains(checkedId)) {
            throw new IllegalStateException("EntityRegistry still contains removed runtime representation: " + checkedId);
        }
        EntityState duplicate = dematerializedStates.putIfAbsent(checkedId, snapshot);
        if (duplicate != null) {
            throw new IllegalStateException("Duplicate dematerialized snapshot: " + checkedId);
        }
        return new DematerializedEntitySnapshot(snapshot, physical);
    }

    /**
     * Restores a dematerialized entity synchronously with the same persistent ID and ECS values.
     *
     * @param id stable persistent entity ID
     * @return newly materialized Ashley entity
     */
    public Entity materialize(EntityId id) {
        EntityId checkedId = Objects.requireNonNull(id, "id");
        if (registry.contains(checkedId)) {
            throw new IllegalStateException("Entity is already materialized: " + checkedId);
        }
        EntityState snapshot = dematerializedStates.get(checkedId);
        if (snapshot == null) {
            throw new IllegalStateException("No dematerialized snapshot exists for entity: " + checkedId);
        }
        if (!physicalStates.containsKey(checkedId)) {
            throw new IllegalStateException("Dematerialized entity lost Stage-20 physical authority: " + checkedId);
        }

        Entity restored = EntityStateMapper.restore(snapshot);
        engine.addEntity(restored);
        if (registry.find(checkedId) != restored) {
            engine.removeEntity(restored);
            throw new IllegalStateException("Materialized entity was not registered under its persistent ID: " + checkedId);
        }
        dematerializedStates.remove(checkedId, snapshot);
        return restored;
    }

    /**
     * Returns current Stage-20 physical authority without materializing the ECS entity.
     *
     * @param id persistent entity ID
     * @return registered physical state or empty for non-Stage-20 entities
     */
    public Optional<LocalPhysicalKinematics> physicalState(EntityId id) {
        return Optional.ofNullable(id == null ? null : physicalStates.get(id));
    }

    /**
     * Returns the retained dematerialized snapshot, if any.
     *
     * @param id persistent entity ID
     * @return immutable dematerialized snapshot or empty when currently live/unknown
     */
    public Optional<DematerializedEntitySnapshot> dematerializedSnapshot(EntityId id) {
        if (id == null) {
            return Optional.empty();
        }
        EntityState entityState = dematerializedStates.get(id);
        LocalPhysicalKinematics physicalState = physicalStates.get(id);
        return entityState == null
                ? Optional.empty()
                : Optional.of(new DematerializedEntitySnapshot(
                        entityState,
                        Objects.requireNonNull(physicalState, "Dematerialized physical state missing")));
    }

    /**
     * Returns whether an entity currently has no Ashley runtime representation but still exists.
     *
     * @param id persistent entity ID
     * @return true only for retained dematerialized state
     */
    public boolean isDematerialized(EntityId id) {
        return id != null && dematerializedStates.containsKey(id);
    }

    /**
     * Returns every persistent ECS EntityState, combining live runtime entities and retained dormant snapshots.
     *
     * @return deterministic EntityId-sorted full persistent entity set
     */
    public List<EntityState> snapshotAllPersistentEntities() {
        Map<EntityId, EntityState> all = new HashMap<>(dematerializedStates);
        for (Entity entity : engine.getEntities()) {
            EntityState state = EntityStateMapper.capture(entity);
            EntityState previous = all.putIfAbsent(state.id(), state);
            if (previous != null) {
                throw new IllegalStateException("Entity exists in both live and dematerialized snapshot sets: " + state.id());
            }
        }
        List<EntityState> result = new ArrayList<>(all.values());
        result.sort(Comparator.comparingLong(value -> value.id().value()));
        return List.copyOf(result);
    }

    /**
     * Returns every registered Stage-20 physical state in deterministic persistent-ID order.
     *
     * <p>The snapshot contains live and dematerialized physical entities alike. Representation level
     * is intentionally absent because it is computational relevance, not causal persistent state.</p>
     *
     * @return immutable sorted physical-state snapshot list
     */
    public List<PhysicalStateSnapshot> snapshotPhysicalStates() {
        List<PhysicalStateSnapshot> result = new ArrayList<>();
        for (Map.Entry<EntityId, LocalPhysicalKinematics> entry : physicalStates.entrySet()) {
            EntityId id = entry.getKey();
            if (!registry.contains(id) && !dematerializedStates.containsKey(id)) {
                throw new IllegalStateException("Physical state has no persistent entity representation: " + id);
            }
            result.add(new PhysicalStateSnapshot(id, entry.getValue()));
        }
        result.sort(Comparator.comparing(PhysicalStateSnapshot::id));
        return List.copyOf(result);
    }

    /**
     * Immutable pair retained while one persistent entity has no local Ashley representation.
     *
     * @param entityState complete supported persistent ECS component state
     * @param physicalState authoritative Stage-20 hierarchical/double physical kinematics
     */
    public record DematerializedEntitySnapshot(
            EntityState entityState,
            LocalPhysicalKinematics physicalState) {
        /**
         * Validates a retained dematerialized snapshot.
         *
         * @param entityState persistent ECS state
         * @param physicalState authoritative physical kinematics
         */
        public DematerializedEntitySnapshot {
            Objects.requireNonNull(entityState, "entityState");
            Objects.requireNonNull(entityState.id(), "entityState.id");
            Objects.requireNonNull(physicalState, "physicalState");
        }
    }

    /**
     * Persistent-ID keyed Stage-20 physical kinematic snapshot.
     *
     * @param id stable persistent entity ID
     * @param physicalState authoritative Stage-20 physical kinematics
     */
    public record PhysicalStateSnapshot(EntityId id, LocalPhysicalKinematics physicalState) {
        /**
         * Validates one physical snapshot.
         *
         * @param id stable persistent entity ID
         * @param physicalState authoritative physical kinematics
         */
        public PhysicalStateSnapshot {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(physicalState, "physicalState");
        }
    }
}
