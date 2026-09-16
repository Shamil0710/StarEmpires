package com.spacesim.persistence;

import com.spacesim.world.SmallCraftId;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Versioned M22.8A persistence sidecar for individually material small craft.
 *
 * <p>The sidecar intentionally stores no wing aggregate HP, virtual ammunition or replenishment
 * counters. Each row preserves one stable craft identity and the same Stage-17.5 engineering DTO
 * already used by ordinary ships.</p>
 *
 * @param schemaVersion exact M22.8A schema
 * @param runtimeVersion exact runtime contract identifier
 * @param semanticContract explicit identity/material-state semantic contract
 * @param nextCraftId next unused campaign-global small-craft ID
 * @param craft individual physical craft rows
 */
public record Stage228SmallCraftPersistentState(
        int schemaVersion,
        String runtimeVersion,
        String semanticContract,
        long nextCraftId,
        List<CraftState> craft) {

    /** Current M22.8A sidecar schema. */
    public static final int CURRENT_VERSION = 1;
    /** Current M22.8A runtime identifier. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8a.small-craft-persistence.v1";
    /** Stable semantic contract fingerprint for individual material craft identity. */
    public static final String CURRENT_SEMANTIC_CONTRACT =
            "individual-craft|stable-id|stage17.5-engineering|no-virtual-pool|no-free-replenishment";

    /**
     * One persistent individual craft.
     *
     * @param id stable campaign craft identity
     * @param stableFactionId stable owning faction identity
     * @param designId stable authored design/fit identity
     * @param engineering exact Stage-17.5 fitted physical state
     */
    public record CraftState(
            SmallCraftId id,
            String stableFactionId,
            String designId,
            EntityState.EngineeringState engineering) {
        /** Validates one physical craft row. */
        public CraftState {
            Objects.requireNonNull(id, "id");
            stableFactionId = requireText(stableFactionId, "stableFactionId");
            designId = requireText(designId, "designId");
            EntityState.EngineeringState checked = Objects.requireNonNull(engineering, "engineering");
            // Reuse the accepted Stage-17.5 persistence validator instead of inventing a fighter schema.
            EngineeringStatePersistenceMapper.restore(checked);
        }
    }

    /** Validates version, deterministic ordering, uniqueness and allocator continuity. */
    public Stage228SmallCraftPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported M22.8A small-craft schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported M22.8A runtime version: " + runtimeVersion);
        }
        semanticContract = requireText(semanticContract, "semanticContract");
        if (!CURRENT_SEMANTIC_CONTRACT.equals(semanticContract)) {
            throw new IllegalArgumentException("Unsupported M22.8A semantic contract: " + semanticContract);
        }
        if (nextCraftId <= 0L) {
            throw new IllegalArgumentException("nextCraftId must be positive");
        }
        Objects.requireNonNull(craft, "craft");
        ArrayList<CraftState> copy = new ArrayList<>(craft.size());
        Set<SmallCraftId> ids = new HashSet<>();
        long maxId = 0L;
        for (CraftState row : craft) {
            CraftState checked = Objects.requireNonNull(row, "craft row");
            if (!ids.add(checked.id())) {
                throw new IllegalArgumentException("Duplicate small-craft ID: " + checked.id());
            }
            maxId = Math.max(maxId, checked.id().value());
            copy.add(checked);
        }
        if (nextCraftId <= maxId) {
            throw new IllegalArgumentException("nextCraftId must exceed every persisted craft ID");
        }
        copy.sort(Comparator.comparing(CraftState::id));
        craft = List.copyOf(copy);
    }

    /** @return non-granting empty sidecar for a new or legacy-adopted campaign */
    public static Stage228SmallCraftPersistentState empty() {
        return new Stage228SmallCraftPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                CURRENT_SEMANTIC_CONTRACT,
                1L,
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
