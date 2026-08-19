package com.spacesim.persistence;

import com.spacesim.world.LocalPhysicalKinematics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned Stage-20 persistence envelope over the unchanged core {@link GameState} v4.
 *
 * <p>The ordinary GameState remains the complete authoritative ECS/economy snapshot. Stage-20
 * hierarchical physical kinematics are stored as a separate deterministic sidecar so existing
 * save compatibility and legacy migration remain untouched. Runtime materialization level is not
 * persisted because it is recomputable computational relevance rather than gameplay authority.</p>
 *
 * @param envelopeVersion Stage-20 envelope schema version
 * @param gameState complete ordinary GameState including live and dematerialized persistent entities
 * @param physicalEntities deterministic Stage-20 physical-state sidecar
 */
public record Stage20MaterializationPersistentState(
        int envelopeVersion,
        GameState gameState,
        List<PhysicalEntityState> physicalEntities) {
    /** Current Stage-20 materialization persistence envelope version. */
    public static final int CURRENT_VERSION = 1;

    /**
     * Validates and deterministically orders one persistence envelope.
     *
     * @param envelopeVersion Stage-20 envelope schema version
     * @param gameState complete ordinary GameState
     * @param physicalEntities Stage-20 physical-state sidecar
     */
    public Stage20MaterializationPersistentState {
        if (envelopeVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported Stage-20 materialization envelope version: " + envelopeVersion);
        }
        Objects.requireNonNull(gameState, "gameState");
        if (gameState.schemaVersion() != GameState.CURRENT_VERSION) {
            throw new IllegalArgumentException("Stage-20 envelope requires current GameState schema");
        }
        Objects.requireNonNull(gameState.entities(), "gameState.entities");
        Objects.requireNonNull(physicalEntities, "physicalEntities");

        Set<EntityId> persistentIds = new HashSet<>();
        for (EntityState entity : gameState.entities()) {
            EntityState checked = Objects.requireNonNull(entity, "gameState entity");
            if (!persistentIds.add(checked.id())) {
                throw new IllegalArgumentException("Duplicate persistent EntityId in GameState: " + checked.id());
            }
        }

        ArrayList<PhysicalEntityState> copy = new ArrayList<>();
        Set<EntityId> physicalIds = new HashSet<>();
        for (PhysicalEntityState physical : physicalEntities) {
            PhysicalEntityState checked = Objects.requireNonNull(physical, "physical entity");
            if (!physicalIds.add(checked.id())) {
                throw new IllegalArgumentException("Duplicate Stage-20 physical EntityId: " + checked.id());
            }
            if (!persistentIds.contains(checked.id())) {
                throw new IllegalArgumentException(
                        "Stage-20 physical state references EntityId absent from GameState: " + checked.id());
            }
            copy.add(checked);
        }
        copy.sort(Comparator.comparing(PhysicalEntityState::id));
        physicalEntities = List.copyOf(copy);
    }

    /**
     * Stage-20 physical sidecar state for one persistent entity.
     *
     * @param id stable persistent entity ID
     * @param physicalState exact hierarchical/double physical kinematics
     */
    public record PhysicalEntityState(EntityId id, LocalPhysicalKinematics physicalState) {
        /**
         * Validates one physical sidecar row.
         *
         * @param id stable persistent entity ID
         * @param physicalState authoritative Stage-20 physical kinematics
         */
        public PhysicalEntityState {
            Objects.requireNonNull(id, "id");
            Objects.requireNonNull(physicalState, "physicalState");
        }
    }
}
