package com.spacesim.persistence;

import java.util.Objects;

/**
 * Current M22.8 campaign persistence envelope extending the accepted Stage-21I checkpoint.
 *
 * <p>The embedded Stage-21 checkpoint remains authoritative for all pre-M22.8 systems. Sidecars own
 * only newly introduced durable carrier/small-craft state. Native M22.8A/B/C and supported Stage-21
 * checkpoints migrate without synthesizing missions, craft, hangar occupancy, deck work, supplies,
 * pending replacements or carrier-wing membership.</p>
 *
 * @param schemaVersion exact current M22.8 envelope schema
 * @param runtimeVersion exact current M22.8 runtime identifier
 * @param stage21Runtime complete accepted Stage-21I checkpoint
 * @param smallCraft individual M22.8A craft persistence sidecar
 * @param hangars individual M22.8B physical hangar occupancy sidecar
 * @param flightDeck M22.8C deterministic launch/recovery sidecar
 * @param operations M22.8M D/G/H mission/logistics/carrier-wing sidecar
 */
public record Stage228GeneratedCampaignPersistentState(
        int schemaVersion,
        String runtimeVersion,
        Stage21IGeneratedWorldRuntimePersistentState stage21Runtime,
        Stage228SmallCraftPersistentState smallCraft,
        Stage228HangarPersistentState hangars,
        Stage228FlightDeckPersistentState flightDeck,
        Stage228OperationsPersistentState operations) {

    /** Current M22.8 campaign envelope schema. */
    public static final int CURRENT_VERSION = 4;
    /** Current M22.8 campaign runtime contract. */
    public static final String CURRENT_RUNTIME_VERSION = "m22.8.generated-campaign.v4";

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
    }

    /**
     * Composes current state from accepted Stage-21 and exact M22.8 sidecars.
     *
     * @param stage21 accepted Stage-21I state
     * @param smallCraft exact A sidecar
     * @param hangars exact B sidecar
     * @param flightDeck exact C sidecar
     * @param operations exact D/G/H sidecar
     * @return current v4 envelope
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
     * @return current v4 envelope with empty operations
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
     * @return current v4 envelope with no deck or operations state
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
     * Migrates native M22.8A into v4 without inventing later state.
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
     * Migrates native M22.8B into v4 without inventing later state.
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
     * Migrates native M22.8C/v3 into v4 without inventing D/G/H state.
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
