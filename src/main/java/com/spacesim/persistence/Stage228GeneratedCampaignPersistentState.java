package com.spacesim.persistence;

import java.util.Objects;

/**
 * M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint without mutating it.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all previously accepted systems.
 * M22.8 sidecars own only newly introduced physical state. Legacy Stage-21 plus native M22.8A/B
 * saves migrate without synthesizing craft, hangar occupancy, deck operations, supplies or capacity.</p>
 *
 * @param schemaVersion exact current M22.8 envelope schema
 * @param runtimeVersion exact current M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 * @param hangars individual M22.8B physical hangar occupancy sidecar
 * @param flightDeck M22.8C deterministic launch/recovery sidecar
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft,
        Stage228HangarPersistentState hangars,
        Stage228FlightDeckPersistentState flightDeck) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 3;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v3";

    /** Validates current envelope identity and embedded accepted authorities.
     * @param schemaVersion exact current schema
     * @param runtimeVersion exact current runtime identifier
     * @param stage21Runtime accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
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
        Objects.requireNonNull(flightDeck, "flightDeck");
    }

    /** Composes current state from accepted Stage-21 and exact A/B/C sidecars.
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @return current v3 envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Objects.requireNonNull(flightDeck, "flightDeck"));
    }

    /** Compatibility composition seam that adds empty non-granting C state.
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @return current v3 envelope with no deck operations
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(stage21, smallCraft, hangars, Stage228FlightDeckPersistentState.empty());
    }

    /** Adopts Stage-21I without inventing any M22.8 assets/state.
     * @param stage21 accepted Stage-21I checkpoint
     * @return current envelope with empty non-granting A/B/C sidecars
     */
    public static Stage228GeneratedCampaignPersistentState adoptStage21(
            Stage21IGeneratedWorldRuntimePersistentState stage21) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Stage228SmallCraftPersistentState.empty(),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty());
    }

    /** Migrates native M22.8A into v3 without inventing occupancy or deck operations.
     * @param stage21 accepted Stage-21I checkpoint embedded by A
     * @param smallCraft exact A craft sidecar
     * @return current envelope preserving craft and adding empty B/C state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8A(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty());
    }

    /** Migrates native M22.8B into v3 without inventing deck operations.
     * @param stage21 accepted Stage-21I checkpoint embedded by B
     * @param smallCraft exact A craft sidecar
     * @param hangars exact B occupancy sidecar
     * @return current envelope preserving A/B state with empty C state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8B(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Stage228FlightDeckPersistentState.empty());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
