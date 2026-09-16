package com.spacesim.persistence;

import java.util.Objects;

/**
 * M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint without mutating it.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all previously accepted systems. The
 * adjacent small-craft sidecar owns only M22.8 individual craft identity/material continuity. Legacy
 * Stage-21 saves are adopted with an empty sidecar and therefore receive no free craft.</p>
 *
 * @param schemaVersion exact M22.8 envelope schema
 * @param runtimeVersion exact M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 1;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v1";

    /**
     * Validates envelope identity and embedded accepted authorities.
     *
     * @param schemaVersion exact M22.8 envelope schema
     * @param runtimeVersion exact M22.8 runtime identifier
     * @param stage21Runtime complete accepted Stage-21I checkpoint
     * @param smallCraft individual M22.8A craft persistence sidecar
     */
    public Stage228GeneratedCampaignPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException("Unsupported M22.8 campaign schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException("Unsupported M22.8 campaign runtime: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21Runtime, "stage21Runtime");
        Objects.requireNonNull(smallCraft, "smallCraft");
    }

    /**
     * Composes a current M22.8 save from accepted Stage-21 state and exact individual small-craft state.
     *
     * @param stage21 complete accepted Stage-21I checkpoint
     * @param smallCraft exact M22.8A sidecar
     * @return current M22.8 campaign envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"));
    }

    /**
     * Adopts a supported Stage-21I save without inventing any small craft or material.
     *
     * @param stage21 existing accepted Stage-21I checkpoint
     * @return M22.8 envelope with an empty non-granting sidecar
     */
    public static Stage228GeneratedCampaignPersistentState adoptStage21(
            Stage21IGeneratedWorldRuntimePersistentState stage21) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Stage228SmallCraftPersistentState.empty());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
