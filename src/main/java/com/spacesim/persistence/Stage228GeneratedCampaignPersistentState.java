package com.spacesim.persistence;

import java.util.Objects;

/**
 * M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint without mutating it.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all previously accepted systems.
 * M22.8 sidecars own only their newly introduced physical state. Legacy Stage-21 and native M22.8A
 * saves migrate without synthesizing craft, hangar occupancy, supplies or capacity.</p>
 *
 * @param schemaVersion exact current M22.8 envelope schema
 * @param runtimeVersion exact current M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 * @param hangars individual M22.8B physical hangar occupancy sidecar
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft,
        Stage228HangarPersistentState hangars) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 2;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v2";

    /**
     * Validates current envelope identity and embedded accepted authorities.
     *
     * @param schemaVersion exact current M22.8 envelope schema
     * @param runtimeVersion exact current M22.8 runtime identifier
     * @param stage21Runtime complete accepted Stage-21I checkpoint
     * @param smallCraft individual M22.8A craft persistence sidecar
     * @param hangars individual M22.8B physical hangar occupancy sidecar
     */
    public Stage228GeneratedCampaignPersistentState {
        if (schemaVersion != CURRENT_VERSION) {
            throw new IllegalArgumentException(
                    "Unsupported current M22.8 campaign schema: " + schemaVersion);
        }
        runtimeVersion = requireText(runtimeVersion, "runtimeVersion");
        if (!CURRENT_RUNTIME_VERSION.equals(runtimeVersion)) {
            throw new IllegalArgumentException(
                    "Unsupported current M22.8 campaign runtime: " + runtimeVersion);
        }
        Objects.requireNonNull(stage21Runtime, "stage21Runtime");
        Objects.requireNonNull(smallCraft, "smallCraft");
        Objects.requireNonNull(hangars, "hangars");
    }

    /**
     * Composes a current M22.8 save from accepted Stage-21 and exact physical M22.8 sidecars.
     *
     * @param stage21 complete accepted Stage-21I checkpoint
     * @param smallCraft exact M22.8A individual-craft sidecar
     * @param hangars exact M22.8B physical occupancy sidecar
     * @return current M22.8 campaign envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"));
    }

    /**
     * Adopts a supported Stage-21I save without inventing small craft or hangar occupancy.
     *
     * @param stage21 existing accepted Stage-21I checkpoint
     * @return current M22.8 envelope with empty non-granting sidecars
     */
    public static Stage228GeneratedCampaignPersistentState adoptStage21(
            Stage21IGeneratedWorldRuntimePersistentState stage21) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Stage228SmallCraftPersistentState.empty(),
                Stage228HangarPersistentState.empty());
    }

    /**
     * Migrates a native M22.8A envelope into B without inventing physical occupancy.
     *
     * @param stage21 accepted Stage-21I checkpoint embedded by M22.8A
     * @param smallCraft exact M22.8A individual-craft sidecar
     * @return current envelope preserving every craft with empty hangar assignments
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8A(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Stage228HangarPersistentState.empty());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
