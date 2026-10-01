package com.spacesim.persistence;

import com.spacesim.player.PlayerState;

import java.util.Objects;

/**
 * Current M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all pre-M22.8 systems. Sidecars own
 * only newly introduced durable carrier/small-craft state and the existing player contract. Native
 * M22.8A/B/C/M and supported Stage-21
 * checkpoints migrate without synthesizing missions, craft, hangar occupancy, deck work, supplies,
 * pending replacements, carrier-wing membership or player assets. Version 5 adds exact optional
 * player persistence; historical checkpoints remain uninitialized.</p>
 *
 * @param schemaVersion exact current M22.8 envelope schema
 * @param runtimeVersion exact current M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 * @param hangars individual M22.8B physical hangar occupancy sidecar
 * @param flightDeck M22.8C deterministic launch/recovery sidecar
 * @param operations M22.8M D/G/H mission/logistics/carrier-wing sidecar
 * @param playerState existing durable player state, or null for an uninitialized campaign
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft,
        Stage228HangarPersistentState hangars,
        Stage228FlightDeckPersistentState flightDeck,
        Stage228OperationsPersistentState operations,
        PlayerState playerState) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 5;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v5";

    /**
     * Source-compatible composition without an initialized player; never invents player assets.
     *
     * @param schemaVersion current envelope schema
     * @param runtimeVersion current runtime identifier
     * @param stage21Runtime complete Stage-21 checkpoint
     * @param smallCraft exact craft state
     * @param hangars exact hangar state
     * @param flightDeck exact deck state
     * @param operations exact operations state
     */
    public Stage228GeneratedCampaignPersistentState(
            int schemaVersion, String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations) {
        this(schemaVersion, runtimeVersion, stage21Runtime, smallCraft, hangars,
                flightDeck, operations, null);
    }

    /**
     * Compatibility constructor for pre-M22.8M call sites; adds an empty non-granting operations sidecar.
     *
     * @param schemaVersion exact current schema
     * @param runtimeVersion exact current runtime identifier
     * @param stage21Runtime accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     */
    public Stage228GeneratedCampaignPersistentState(
            int schemaVersion,
            String runtimeVersion,
            Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        this(
                schemaVersion,
                runtimeVersion,
                stage21Runtime,
                smallCraft,
                hangars,
                flightDeck,
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Validates current envelope identity and embedded accepted authorities.
     *
     * @param schemaVersion exact current schema
     * @param runtimeVersion exact current runtime identifier
     * @param stage21Runtime accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @param operations exact M operations sidecar
     * @param playerState exact optional player checkpoint
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
        Objects.requireNonNull(operations, "operations");
        if (playerState != null) {
            GeneratedCampaignPlayerCheckpointValidator.validate(stage21Runtime, playerState);
        }
    }

    /**
     * Composes the existing authoritative checkpoint with an exact durable player state.
     *
     * @param stage21 complete Stage-21 checkpoint
     * @param smallCraft exact craft state
     * @param hangars exact hangar state
     * @param flightDeck exact deck state
     * @param operations exact operations state
     * @param playerState existing player state, or null before player initialization
     * @return current validated v5 envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations,
            PlayerState playerState) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION, CURRENT_RUNTIME_VERSION, stage21, smallCraft,
                hangars, flightDeck, operations, playerState);
    }

    /**
     * Composes current state from accepted Stage-21 and exact M22.8 sidecars.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @param operations exact D/G/H sidecar
     * @return current v5 envelope
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck,
            Stage228OperationsPersistentState operations) {
        return new Stage228GeneratedCampaignPersistentState(
                CURRENT_VERSION,
                CURRENT_RUNTIME_VERSION,
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Objects.requireNonNull(flightDeck, "flightDeck"),
                Objects.requireNonNull(operations, "operations"));
    }

    /**
     * Compatibility composition seam that adds empty non-granting M state.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @return current v5 envelope with empty operations
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        return compose(
                stage21,
                smallCraft,
                hangars,
                flightDeck,
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Compatibility composition seam that adds empty C/M state.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @return current v5 envelope with no deck or operations state
     */
    public static Stage228GeneratedCampaignPersistentState compose(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(
                stage21,
                smallCraft,
                hangars,
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Adopts Stage-21I without inventing any M22.8 assets/state.
     *
     * @param stage21 accepted Stage-21I checkpoint
     * @return current envelope with empty non-granting A/B/C/M sidecars
     */
    public static Stage228GeneratedCampaignPersistentState adoptStage21(
            Stage21IGeneratedWorldRuntimePersistentState stage21) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Stage228SmallCraftPersistentState.empty(),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8A into v5 without inventing later state.
     *
     * @param stage21 accepted Stage-21I checkpoint embedded by A
     * @param smallCraft exact A craft sidecar
     * @return current envelope preserving A and adding empty B/C/M state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8A(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Stage228HangarPersistentState.empty(),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8B into v5 without inventing later state.
     *
     * @param stage21 accepted Stage-21I checkpoint embedded by B
     * @param smallCraft exact A craft sidecar
     * @param hangars exact B occupancy sidecar
     * @return current envelope preserving A/B with empty C/M state
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8B(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Stage228FlightDeckPersistentState.empty(),
                Stage228OperationsPersistentState.empty());
    }

    /**
     * Migrates native M22.8C/v3 into v5 without inventing D/G/H state.
     *
     * @param stage21 accepted Stage-21I checkpoint
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @return current envelope preserving A/B/C with empty operations
     */
    public static Stage228GeneratedCampaignPersistentState adoptM22_8C(
            Stage21IGeneratedWorldRuntimePersistentState stage21,
            Stage228SmallCraftPersistentState smallCraft,
            Stage228HangarPersistentState hangars,
            Stage228FlightDeckPersistentState flightDeck) {
        return compose(
                Objects.requireNonNull(stage21, "stage21"),
                Objects.requireNonNull(smallCraft, "smallCraft"),
                Objects.requireNonNull(hangars, "hangars"),
                Objects.requireNonNull(flightDeck, "flightDeck"),
                Stage228OperationsPersistentState.empty());
    }

    private static String requireText(String value, String label) {
        String checked = Objects.requireNonNull(value, label).strip();
        if (checked.isEmpty()) {
            throw new IllegalArgumentException(label + " cannot be blank");
        }
        return checked;
    }
}
